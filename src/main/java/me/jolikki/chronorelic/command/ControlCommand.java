package me.jolikki.chronorelic.command;

import me.jolikki.chronorelic.manager.ConfigManager;
import me.jolikki.chronorelic.schematic.SchematicManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ControlCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final SchematicManager schematicManager;

    public ControlCommand(JavaPlugin plugin, ConfigManager config, SchematicManager schematicManager) {
        this.plugin = plugin;
        this.config = config;
        this.schematicManager = schematicManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("chronorelic.command.reload")) {
                sender.sendMessage(config.getString("messages.no-permission"));
                return true;
            }

            config.reload();
            sender.sendMessage(config.getString("messages.config-reloaded"));
            return true;
        }

        if (args[0].equalsIgnoreCase("schem")) {
            handleSchematicCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            if (!sender.hasPermission("chronorelic.command.give")) {
                sender.sendMessage(config.getString("messages.no-permission"));
                return true;
            }

            sendItemList(sender);
            return true;
        }

        if (!args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(config.getString("messages.unknown-command"));
            return true;
        }

        if (!sender.hasPermission("chronorelic.command.give")) {
            sender.sendMessage(config.getString("messages.no-permission"));
            return true;
        }

        if (args.length < 3) {
            sendUsage(sender);
            return true;
        }

        Player player = Bukkit.getPlayer(args[1]);
        if (player == null) {
            sender.sendMessage(config.getString("messages.player-not-found"));
            return true;
        }

        String itemId = args[2].toLowerCase();
        ConfigurationSection itemSection = config.getConfig().getConfigurationSection("items." + itemId);
        if (itemSection == null) {
            sender.sendMessage(config.getString("messages.item-not-found")
                    .replace("{item}", itemId));
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }

        Material material = Material.matchMaterial(itemSection.getString("material", "DIAMOND"));
        if (material == null) {
            material = Material.DIAMOND;
        }

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            sender.sendMessage(config.getString("messages.item-create-error"));
            return true;
        }

        meta.setDisplayName(config.getString("items." + itemId + ".name"));
        List<String> lore = config.getStringList("items." + itemId + ".lore");
        meta.setLore(lore);
        meta.setUnbreakable(itemSection.getBoolean("unbreakable", false));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "chrono_item_id"),
                PersistentDataType.STRING,
                itemId
        );
        item.setItemMeta(meta);

        applyEnchantments(item, itemSection.getConfigurationSection("enchantments"));

        player.getInventory().addItem(item);
        player.sendMessage(config.getString("messages.item-given")
                .replace("{item}", itemId)
                .replace("{amount}", String.valueOf(amount)));
        sender.sendMessage(config.getString("messages.item-given-sender")
                .replace("{player}", player.getName())
                .replace("{item}", itemId)
                .replace("{amount}", String.valueOf(amount)));

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(config.getString("messages.usage"));
        sender.sendMessage(ChatColor.GRAY + "/cr give <player> <item_id> [amount]");
        sender.sendMessage(ChatColor.GRAY + "/cr list");
        sender.sendMessage(ChatColor.GRAY + "/cr schem preview <name> [x] [y] [z] [rotation]");
        sender.sendMessage(ChatColor.GRAY + "/cr schem paste");
        sender.sendMessage(ChatColor.GRAY + "/cr schem clear");
        sender.sendMessage(ChatColor.GRAY + "/cr reload");
    }

    private void handleSchematicCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chronorelic.command.schem")) {
            sender.sendMessage(config.getString("messages.no-permission"));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getString("messages.only-player"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(config.getString("messages.schem-usage"));
            return;
        }

        if (args[1].equalsIgnoreCase("preview")) {
            if (args.length < 3) {
                sender.sendMessage(config.getString("messages.schem-usage"));
                return;
            }

            try {
                SchematicManager.PreviewResult result;
                if (args.length >= 6) {
                    double x = parseCoordinate(args[3], player.getLocation().getX());
                    double y = parseCoordinate(args[4], player.getLocation().getY());
                    double z = parseCoordinate(args[5], player.getLocation().getZ());
                    int rotation = args.length >= 7 ? parseRotation(args[6]) : 0;
                    result = schematicManager.preview(player, args[2], new Location(player.getWorld(), x, y, z), rotation);
                } else {
                    result = schematicManager.preview(player, args[2]);
                }

                if (result.tooLarge()) {
                    player.sendMessage(config.getString("messages.schem-too-large")
                            .replace("{blocks}", String.valueOf(result.blocks()))
                            .replace("{max}", String.valueOf(result.maxBlocks())));
                    return;
                }

                player.sendMessage(config.getString("messages.schem-preview")
                        .replace("{schem}", args[2])
                        .replace("{width}", String.valueOf(result.width()))
                        .replace("{height}", String.valueOf(result.height()))
                        .replace("{length}", String.valueOf(result.length()))
                        .replace("{blocks}", String.valueOf(result.blocks())));
            } catch (IOException | IllegalArgumentException exception) {
                player.sendMessage(config.getString("messages.schem-load-error")
                        .replace("{schem}", args[2])
                        .replace("{error}", exception.getMessage()));
            }
            return;
        }

        if (args[1].equalsIgnoreCase("paste")) {
            SchematicManager.PasteResult result = schematicManager.paste(player);
            if (result.noPreview()) {
                player.sendMessage(config.getString("messages.schem-no-preview"));
                return;
            }

            if (result.wrongWorld()) {
                player.sendMessage(config.getString("messages.schem-wrong-world"));
                return;
            }

            player.sendMessage(config.getString("messages.schem-pasted")
                    .replace("{schem}", result.schematicName())
                    .replace("{blocks}", String.valueOf(result.blocks())));
            return;
        }

        if (args[1].equalsIgnoreCase("clear")) {
            if (schematicManager.clearPreview(player)) {
                player.sendMessage(config.getString("messages.schem-cleared"));
            } else {
                player.sendMessage(config.getString("messages.schem-no-preview"));
            }
            return;
        }

        sender.sendMessage(config.getString("messages.schem-usage"));
    }

    private double parseCoordinate(String raw, double current) {
        if (raw.startsWith("~")) {
            if (raw.length() == 1) {
                return current;
            }

            return current + Double.parseDouble(raw.substring(1));
        }

        return Double.parseDouble(raw);
    }

    private int parseRotation(String raw) {
        int rotation = Integer.parseInt(raw);
        if (rotation % 90 != 0) {
            throw new IllegalArgumentException("rotation must be 0, 90, 180 or 270");
        }

        return rotation;
    }

    private void sendItemList(CommandSender sender) {
        ConfigurationSection items = config.getConfig().getConfigurationSection("items");
        if (items == null || items.getKeys(false).isEmpty()) {
            sender.sendMessage(config.getString("messages.item-list-empty"));
            return;
        }

        Set<String> itemIds = items.getKeys(false);
        sender.sendMessage(config.getString("messages.item-list")
                .replace("{items}", String.join(", ", itemIds)));
    }

    private void applyEnchantments(ItemStack item, ConfigurationSection enchantments) {
        if (enchantments == null) {
            return;
        }

        for (String enchantmentName : enchantments.getKeys(false)) {
            Enchantment enchantment = Enchantment.getByName(enchantmentName.toUpperCase());
            if (enchantment == null) {
                continue;
            }

            int level = Math.max(1, enchantments.getInt(enchantmentName));
            item.addUnsafeEnchantment(enchantment, level);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("help", "reload", "list", "give", "schem"));
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                return filter(args[1], Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList()));
            }

            if (args.length == 3) {
                ConfigurationSection items = config.getConfig().getConfigurationSection("items");
                if (items == null) {
                    return Collections.emptyList();
                }

                return filter(args[2], new ArrayList<>(items.getKeys(false)));
            }

            if (args.length == 4) {
                return filter(args[3], Arrays.asList("1", "8", "16", "32", "64"));
            }
        }

        if (args[0].equalsIgnoreCase("schem")) {
            if (args.length == 2) {
                return filter(args[1], Arrays.asList("preview", "paste", "clear"));
            }

            if (args[1].equalsIgnoreCase("preview")) {
                if (args.length == 3) {
                    List<String> names = Arrays.stream(schematicManager.listSchematicFiles())
                            .map(File::getName)
                            .map(name -> name.endsWith(".schem") ? name.substring(0, name.length() - 6) : name)
                            .collect(Collectors.toList());
                    return filter(args[2], names);
                }

                if (sender instanceof Player player) {
                    Location location = player.getLocation();
                    if (args.length == 4) {
                        return filter(args[3], List.of(String.valueOf(location.getBlockX()), "~"));
                    }
                    if (args.length == 5) {
                        return filter(args[4], List.of(String.valueOf(location.getBlockY()), "~"));
                    }
                    if (args.length == 6) {
                        return filter(args[5], List.of(String.valueOf(location.getBlockZ()), "~"));
                    }
                }

                if (args.length == 7) {
                    return filter(args[6], Arrays.asList("0", "90", "180", "270"));
                }
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(String prefix, List<String> values) {
        String lowerPrefix = prefix.toLowerCase();
        return values.stream()
                .filter(value -> value.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }
}
