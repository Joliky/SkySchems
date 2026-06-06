package me.jolikki.chronorelic.schematic;

import me.jolikki.chronorelic.manager.ConfigManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SchematicManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final SchematicLoader loader = new SchematicLoader();
    private final Map<UUID, PreviewSession> previews = new HashMap<>();

    public SchematicManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        getSchematicsFolder().mkdirs();
        saveBundledSchematic("ad_generator_spawn.schem");
    }

    public PreviewResult preview(Player player, String schematicName) throws IOException {
        return preview(player, schematicName, getPreviewOrigin(player), getPlayerRotation(player));
    }

    public PreviewResult preview(Player player, String schematicName, Location origin, int rotation) throws IOException {
        clearPreview(player);

        LoadedSchematic schematic = loadSchematic(schematicName);
        int maxBlocks = config.getConfig().getInt("schematics.max-preview-blocks", 20000);
        if (schematic.getBlocks().size() > maxBlocks) {
            return PreviewResult.tooLarge(schematic.getBlocks().size(), maxBlocks);
        }

        PreviewSession session = new PreviewSession(schematicName, player.getWorld(), origin, schematic, normalizeRotation(rotation));
        session.task = startFakeBlockTask(player, session);
        previews.put(player.getUniqueId(), session);
        return PreviewResult.success(schematic.getWidth(), schematic.getHeight(), schematic.getLength(), schematic.getBlocks().size());
    }

    public PasteResult paste(Player player) {
        PreviewSession session = previews.get(player.getUniqueId());
        if (session == null) {
            return PasteResult.withoutPreview();
        }

        if (!session.world().equals(player.getWorld())) {
            return PasteResult.inWrongWorld();
        }

        session.cancelTask();
        restoreVisibleFakeBlocks(player, session);
        for (LoadedSchematic.SchematicBlock schematicBlock : session.schematic().getBlocks()) {
            Location location = session.toWorldLocation(schematicBlock);
            Block block = location.getBlock();
            block.setBlockData(session.rotateBlockData(schematicBlock.blockData()), false);
        }

        previews.remove(player.getUniqueId());
        return PasteResult.success(session.schematicName(), session.schematic().getBlocks().size());
    }

    public boolean clearPreview(Player player) {
        PreviewSession session = previews.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }

        restoreVisibleFakeBlocks(player, session);
        session.cancelTask();
        return true;
    }

    public File[] listSchematicFiles() {
        File[] files = getSchematicsFolder().listFiles((dir, name) -> name.toLowerCase().endsWith(".schem"));
        return files == null ? new File[0] : files;
    }

    public boolean hasPreviewBlock(Player player, Location location) {
        PreviewSession session = previews.get(player.getUniqueId());
        return session != null && session.isProtected(location);
    }

    private LoadedSchematic loadSchematic(String schematicName) throws IOException {
        File file = getSchematicFile(schematicName);
        if (!file.isFile()) {
            throw new IOException("Schematic file not found: " + file.getName());
        }

        boolean ignoreAir = config.getConfig().getBoolean("schematics.ignore-air", true);
        return loader.load(file, ignoreAir);
    }

    private File getSchematicFile(String schematicName) throws IOException {
        String safeName = schematicName;
        if (!safeName.endsWith(".schem")) {
            safeName += ".schem";
        }

        File folder = getSchematicsFolder().getCanonicalFile();
        File file = new File(folder, safeName).getCanonicalFile();
        if (!file.toPath().startsWith(folder.toPath())) {
            throw new IOException("Invalid schematic name");
        }

        return file;
    }

    private File getSchematicsFolder() {
        return new File(plugin.getDataFolder(), "schematics");
    }

    private void saveBundledSchematic(String fileName) {
        String resourcePath = "schematics/" + fileName;
        if (plugin.getResource(resourcePath) == null) {
            return;
        }

        File file = new File(getSchematicsFolder(), fileName);
        if (!file.isFile()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    private Location getPreviewOrigin(Player player) {
        int distance = config.getConfig().getInt("schematics.preview-distance", 20);
        int offsetX = config.getConfig().getInt("schematics.default-offset-x", 1);
        int offsetZ = config.getConfig().getInt("schematics.default-offset-z", 1);
        Block target = player.getTargetBlockExact(distance);

        if (target == null) {
            return player.getLocation().getBlock().getLocation().add(offsetX, 0, offsetZ);
        }

        return target.getLocation().add(offsetX, 1, offsetZ);
    }

    private BukkitTask startFakeBlockTask(Player player, PreviewSession session) {
        long period = Math.max(2, config.getConfig().getLong("schematics.fake-refresh-ticks", 8));
        int maxBlocks = Math.max(1, config.getConfig().getInt("schematics.max-fake-blocks-per-pulse", 20000));

        return plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !player.getWorld().equals(session.world())) {
                session.cancelTask();
                previews.remove(player.getUniqueId());
                return;
            }

            int sent = 0;
            for (LoadedSchematic.SchematicBlock schematicBlock : session.schematic().getBlocks()) {
                Location location = session.toWorldLocation(schematicBlock);
                BlockKey key = BlockKey.from(location);
                player.sendBlockChange(location, session.rotateBlockData(schematicBlock.blockData()));
                session.visibleFakeBlocks.add(key);

                sent++;
                if (sent >= maxBlocks) {
                    break;
                }
            }
        }, 0L, period);
    }

    private void restoreVisibleFakeBlocks(Player player, PreviewSession session) {
        for (BlockKey key : new HashSet<>(session.visibleFakeBlocks)) {
            Location location = key.toLocation(session.world());
            player.sendBlockChange(location, location.getBlock().getBlockData());
        }
    }

    private int getPlayerRotation(Player player) {
        float yaw = player.getLocation().getYaw();
        int quarterTurns = Math.round(yaw / 90.0F);
        return normalizeRotation(quarterTurns * 90);
    }

    private int normalizeRotation(int rotation) {
        int normalized = rotation % 360;
        if (normalized < 0) {
            normalized += 360;
        }

        if (normalized < 45 || normalized >= 315) {
            return 0;
        }
        if (normalized < 135) {
            return 90;
        }
        if (normalized < 225) {
            return 180;
        }
        return 270;
    }

    public static class PreviewSession {
        private final String schematicName;
        private final World world;
        private final Location origin;
        private final LoadedSchematic schematic;
        private final int rotation;
        private BukkitTask task;
        private final Set<BlockKey> protectedBlocks = new HashSet<>();

        public PreviewSession(String schematicName, World world, Location origin, LoadedSchematic schematic, int rotation) {
            this.schematicName = schematicName;
            this.world = world;
            this.origin = origin;
            this.schematic = schematic;
            this.rotation = rotation;
            buildProtectedBlocks();
        }

        public String schematicName() {
            return schematicName;
        }

        public World world() {
            return world;
        }

        public LoadedSchematic schematic() {
            return schematic;
        }

        public Location toWorldLocation(LoadedSchematic.SchematicBlock block) {
            int x = block.x();
            int z = block.z();

            return switch (rotation) {
                case 90 -> origin.clone().add(schematic.getLength() - 1 - z, block.y(), x);
                case 180 -> origin.clone().add(schematic.getWidth() - 1 - x, block.y(), schematic.getLength() - 1 - z);
                case 270 -> origin.clone().add(z, block.y(), schematic.getWidth() - 1 - x);
                default -> origin.clone().add(x, block.y(), z);
            };
        }

        public void cancelTask() {
            if (task != null) {
                task.cancel();
                task = null;
            }

            visibleFakeBlocks.clear();
        }

        private final Set<BlockKey> visibleFakeBlocks = new HashSet<>();

        public boolean isProtected(Location location) {
            if (location.getWorld() == null || !location.getWorld().equals(world)) {
                return false;
            }

            return protectedBlocks.contains(BlockKey.from(location));
        }

        private void buildProtectedBlocks() {
            for (LoadedSchematic.SchematicBlock block : schematic.getBlocks()) {
                Location location = toWorldLocation(block);
                BlockKey key = BlockKey.from(location);
                protectedBlocks.add(key);
                protectedBlocks.add(new BlockKey(key.x(), key.y() - 1, key.z()));
            }
        }

        public BlockData rotateBlockData(BlockData original) {
            BlockData data = original.clone();
            rotateDirectional(data);
            rotateRotatable(data);
            rotateOrientable(data);
            rotateMultipleFacing(data);
            rotateRedstoneWire(data);
            rotateRail(data);
            return data;
        }

        private void rotateDirectional(BlockData data) {
            if (!(data instanceof Directional directional)) {
                return;
            }

            BlockFace rotated = rotateFace(directional.getFacing());
            if (directional.getFaces().contains(rotated)) {
                directional.setFacing(rotated);
            }
        }

        private void rotateRotatable(BlockData data) {
            if (data instanceof Rotatable rotatable) {
                rotatable.setRotation(rotateFace(rotatable.getRotation()));
            }
        }

        private void rotateOrientable(BlockData data) {
            if (!(data instanceof Orientable orientable) || rotation == 0 || rotation == 180) {
                return;
            }

            if (orientable.getAxis() == org.bukkit.Axis.X && orientable.getAxes().contains(org.bukkit.Axis.Z)) {
                orientable.setAxis(org.bukkit.Axis.Z);
            } else if (orientable.getAxis() == org.bukkit.Axis.Z && orientable.getAxes().contains(org.bukkit.Axis.X)) {
                orientable.setAxis(org.bukkit.Axis.X);
            }
        }

        private void rotateMultipleFacing(BlockData data) {
            if (!(data instanceof MultipleFacing multipleFacing)) {
                return;
            }

            Map<BlockFace, Boolean> faces = new HashMap<>();
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                if (multipleFacing.getAllowedFaces().contains(face)) {
                    faces.put(face, multipleFacing.hasFace(face));
                    multipleFacing.setFace(face, false);
                }
            }

            for (Map.Entry<BlockFace, Boolean> entry : faces.entrySet()) {
                BlockFace rotated = rotateFace(entry.getKey());
                if (multipleFacing.getAllowedFaces().contains(rotated)) {
                    multipleFacing.setFace(rotated, entry.getValue());
                }
            }
        }

        private void rotateRedstoneWire(BlockData data) {
            if (!(data instanceof RedstoneWire redstoneWire)) {
                return;
            }

            Map<BlockFace, RedstoneWire.Connection> connections = new HashMap<>();
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                connections.put(face, redstoneWire.getFace(face));
                redstoneWire.setFace(face, RedstoneWire.Connection.NONE);
            }

            for (Map.Entry<BlockFace, RedstoneWire.Connection> entry : connections.entrySet()) {
                redstoneWire.setFace(rotateFace(entry.getKey()), entry.getValue());
            }
        }

        private void rotateRail(BlockData data) {
            if (!(data instanceof Rail rail)) {
                return;
            }

            for (int i = 0; i < rotation / 90; i++) {
                rail.setShape(rotateRailShapeOnce(rail.getShape()));
            }
        }

        private Rail.Shape rotateRailShapeOnce(Rail.Shape shape) {
            return switch (shape) {
                case NORTH_SOUTH -> Rail.Shape.EAST_WEST;
                case EAST_WEST -> Rail.Shape.NORTH_SOUTH;
                case ASCENDING_EAST -> Rail.Shape.ASCENDING_SOUTH;
                case ASCENDING_SOUTH -> Rail.Shape.ASCENDING_WEST;
                case ASCENDING_WEST -> Rail.Shape.ASCENDING_NORTH;
                case ASCENDING_NORTH -> Rail.Shape.ASCENDING_EAST;
                case SOUTH_EAST -> Rail.Shape.SOUTH_WEST;
                case SOUTH_WEST -> Rail.Shape.NORTH_WEST;
                case NORTH_WEST -> Rail.Shape.NORTH_EAST;
                case NORTH_EAST -> Rail.Shape.SOUTH_EAST;
            };
        }

        private BlockFace rotateFace(BlockFace face) {
            BlockFace rotated = face;
            for (int i = 0; i < rotation / 90; i++) {
                rotated = rotateFaceOnce(rotated);
            }
            return rotated;
        }

        private BlockFace rotateFaceOnce(BlockFace face) {
            return switch (face) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                case WEST -> BlockFace.NORTH;
                case NORTH_EAST -> BlockFace.SOUTH_EAST;
                case SOUTH_EAST -> BlockFace.SOUTH_WEST;
                case SOUTH_WEST -> BlockFace.NORTH_WEST;
                case NORTH_WEST -> BlockFace.NORTH_EAST;
                default -> face;
            };
        }
    }

    private record BlockKey(int x, int y, int z) {
        public static BlockKey from(Location location) {
            return new BlockKey(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        public Location toLocation(World world) {
            return new Location(world, x, y, z);
        }
    }

    public record PreviewResult(boolean success, boolean tooLarge, int width, int height, int length, int blocks, int maxBlocks) {
        public static PreviewResult success(int width, int height, int length, int blocks) {
            return new PreviewResult(true, false, width, height, length, blocks, 0);
        }

        public static PreviewResult tooLarge(int blocks, int maxBlocks) {
            return new PreviewResult(false, true, 0, 0, 0, blocks, maxBlocks);
        }
    }

    public record PasteResult(boolean success, boolean noPreview, boolean wrongWorld, String schematicName, int blocks) {
        public static PasteResult success(String schematicName, int blocks) {
            return new PasteResult(true, false, false, schematicName, blocks);
        }

        public static PasteResult withoutPreview() {
            return new PasteResult(false, true, false, "", 0);
        }

        public static PasteResult inWrongWorld() {
            return new PasteResult(false, false, true, "", 0);
        }
    }
}
