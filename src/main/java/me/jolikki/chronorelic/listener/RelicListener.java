package me.jolikki.chronorelic.listener;

import me.jolikki.chronorelic.manager.ConfigManager;
import me.jolikki.chronorelic.manager.CooldownManager;
import me.jolikki.chronorelic.manager.PermissionManager;
import me.jolikki.chronorelic.relic.RelicMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RelicListener implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, RelicMode> playerModes = new HashMap<>();
    private final CooldownManager cooldownManager;
    private final ConfigManager config;
    private final PermissionManager permissionManager;

    public RelicListener(JavaPlugin plugin, CooldownManager cooldownManager, ConfigManager config, PermissionManager permissionManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.config = config;
        this.permissionManager = permissionManager;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.WITHER_SKELETON_SKULL) {
            return;
        }

        Block block = event.getBlockPlaced();
        World world = block.getWorld();
        Block blockBelow = world.getBlockAt(block.getX(), block.getY() - 1, block.getZ());

        if (blockBelow.getType() == Material.DIAMOND_BLOCK) {
            event.getPlayer().sendMessage(config.getString("messages.ritual-lightning"));
            event.getPlayer().getWorld().strikeLightning(event.getPlayer().getLocation());
        }
    }

    @EventHandler
    public void onHero(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        if (!isHerobrineStructure(event.getBlock())) {
            return;
        }

        String permission = config.getString("permissions.herobrine");
        if (permissionManager.hasPermission(player, permission)) {
            player.sendMessage(config.getString("messages.hero_already_summoned"));
            return;
        }

        if (cooldownManager.hasCooldown(player.getUniqueId(), "herobrine")) {
            long remaining = cooldownManager.getRemaining(player.getUniqueId(), "herobrine") / 1000;
            player.sendMessage(config.getString("messages.hero_spawn") + remaining + " сек.");
            return;
        }

        long cooldown = config.getLong("cooldowns.hero_cooldown");
        if (!cooldownManager.tryUse(player.getUniqueId(), "herobrine", cooldown)) {
            return;
        }

        player.getWorld().strikeLightning(player.getLocation());
        event.getBlock().getWorld().strikeLightning(event.getBlock().getLocation());

        player.sendMessage(config.getString("messages.hero_spawn_chat"));
        player.sendTitle(
                config.getString("titles.summon.title"),
                config.getString("titles.summon.subtitle"),
                config.getInt("titles.summon.fadeIn"),
                config.getInt("titles.summon.stay"),
                config.getInt("titles.summon.fadeOut")
        );

        player.playSound(
                player.getLocation(),
                config.getString("sounds.summon.sound"),
                config.getFloat("sounds.summon.volume"),
                config.getFloat("sounds.summon.pitch")
        );

        permissionManager.addPermission(player.getUniqueId(), permission);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!event.getAction().toString().contains("RIGHT_CLICK")) {
            return;
        }

        ItemStack item = event.getItem();
        if (!isRelic(item)) {
            return;
        }

        Player player = event.getPlayer();
        event.setCancelled(true);

        if (player.isSneaking()) {
            RelicMode next = getNextMode(player.getUniqueId());
            playerModes.put(player.getUniqueId(), next);
            player.sendMessage(config.getString("messages.relic-mode").replace("{mode}", next.name()));
            return;
        }

        long cooldown = plugin.getConfig().getLong("cooldowns.relic_cooldown");
        if (!cooldownManager.tryUse(player.getUniqueId(), "relic", cooldown)) {
            long remaining = cooldownManager.getRemaining(player.getUniqueId(), "relic") / 1000;
            player.sendMessage(config.getString("messages.relic-cooldown")
                    .replace("{seconds}", String.valueOf(remaining)));
            return;
        }

        player.sendMessage(config.getString("messages.relic-used"));
        RelicMode mode = playerModes.getOrDefault(player.getUniqueId(), RelicMode.JUMP);

        switch (mode) {
            case JUMP -> player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 100, 10));
            case SPEED -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2));
            case TIME_BLINK -> teleportToTargetBlock(player);
        }

        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);

        if (config.getBoolean("relic.consume-on-use")) {
            consumeOneRelic(player, item);
        }
    }

    private boolean isHerobrineStructure(Block fireBlock) {
        return fireBlock.getRelative(0, -1, 0).getType() == Material.NETHERRACK
                && fireBlock.getRelative(0, -2, 0).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(1, -2, 0).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(-1, -2, 0).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(1, -2, 1).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(-1, -2, 1).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(0, -2, 1).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(0, -2, -1).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(-1, -2, -1).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(1, -2, -1).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(1, -1, -1).getType() == Material.REDSTONE_TORCH
                && fireBlock.getRelative(1, -1, 1).getType() == Material.REDSTONE_TORCH
                && fireBlock.getRelative(-1, -1, -1).getType() == Material.REDSTONE_TORCH
                && fireBlock.getRelative(-1, -1, 1).getType() == Material.REDSTONE_TORCH;
    }

    private boolean isRelic(ItemStack item) {
        if (item == null) {
            return false;
        }

        Material material = Material.matchMaterial(config.getString("relic.material"));
        if (material == null) {
            material = Material.DIAMOND;
        }

        if (item.getType() != material) {
            return false;
        }

        if (!item.hasItemMeta() || item.getItemMeta() == null || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        return item.getItemMeta().getDisplayName().equals(config.getString("relic.name"));
    }

    private RelicMode getNextMode(UUID uuid) {
        RelicMode current = playerModes.getOrDefault(uuid, RelicMode.JUMP);

        return switch (current) {
            case JUMP -> RelicMode.SPEED;
            case SPEED -> RelicMode.TIME_BLINK;
            case TIME_BLINK -> RelicMode.JUMP;
        };
    }

    private void teleportToTargetBlock(Player player) {
        var result = player.rayTraceBlocks(50);

        if (result == null || result.getHitBlock() == null) {
            player.sendMessage(config.getString("messages.teleport-no-target"));
            return;
        }

        var location = result.getHitBlock().getLocation().add(0.5, 1, 0.5);
        player.teleport(location);
    }

    private void consumeOneRelic(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            return;
        }

        player.getInventory().setItemInMainHand(null);
    }
}
