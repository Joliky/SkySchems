package me.jolikki.skyschems;

import me.jolikki.skyschems.schematic.SchematicManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SkySchemsCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager config;
    private final SchematicManager schematicManager;

    public SkySchemsCommand(ConfigManager config, SchematicManager schematicManager) {
        this.config = config;
        this.schematicManager = schematicManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendUsage(sender, label);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("skyschems.command.reload")) {
                sender.sendMessage(config.getString("messages.no-permission"));
                return true;
            }

            config.reload();
            sender.sendMessage(config.getString("messages.config-reloaded"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getString("messages.only-player"));
            return true;
        }

        if (!sender.hasPermission("skyschems.command.use")) {
            sender.sendMessage(config.getString("messages.no-permission"));
            return true;
        }

        if (args[0].equalsIgnoreCase("preview")) {
            handlePreview(player, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("paste")) {
            handlePaste(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("clear")) {
            handleClear(player);
            return true;
        }

        sender.sendMessage(config.getString("messages.unknown-command"));
        sendUsage(sender, label);
        return true;
    }

    private void handlePreview(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(config.getString("messages.usage"));
            return;
        }

        try {
            SchematicManager.PreviewResult result;
            if (args.length >= 5) {
                double x = parseCoordinate(args[2], player.getLocation().getX());
                double y = parseCoordinate(args[3], player.getLocation().getY());
                double z = parseCoordinate(args[4], player.getLocation().getZ());
                int rotation = args.length >= 6 ? parseRotation(args[5]) : 0;
                result = schematicManager.preview(player, args[1], new Location(player.getWorld(), x, y, z), rotation);
            } else {
                result = schematicManager.preview(player, args[1]);
            }

            if (result.tooLarge()) {
                player.sendMessage(config.getString("messages.schem-too-large")
                        .replace("{blocks}", String.valueOf(result.blocks()))
                        .replace("{max}", String.valueOf(result.maxBlocks())));
                return;
            }

            player.sendMessage(config.getString("messages.schem-preview")
                    .replace("{schem}", args[1])
                    .replace("{width}", String.valueOf(result.width()))
                    .replace("{height}", String.valueOf(result.height()))
                    .replace("{length}", String.valueOf(result.length()))
                    .replace("{blocks}", String.valueOf(result.blocks())));
        } catch (IOException | IllegalArgumentException exception) {
            player.sendMessage(config.getString("messages.schem-load-error")
                    .replace("{schem}", args[1])
                    .replace("{error}", exception.getMessage()));
        }
    }

    private void handlePaste(Player player) {
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
    }

    private void handleClear(Player player) {
        if (schematicManager.clearPreview(player)) {
            player.sendMessage(config.getString("messages.schem-cleared"));
            return;
        }

        player.sendMessage(config.getString("messages.schem-no-preview"));
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(config.getString("messages.usage"));
        sender.sendMessage(ChatColor.GRAY + "/" + label + " preview <name> [x] [y] [z] [rotation]");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " paste");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " clear");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " reload");
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("help", "preview", "paste", "clear", "reload"));
        }

        if (args[0].equalsIgnoreCase("preview")) {
            if (args.length == 2) {
                List<String> names = Arrays.stream(schematicManager.listSchematicFiles())
                        .map(File::getName)
                        .map(name -> name.endsWith(".schem") ? name.substring(0, name.length() - 6) : name)
                        .collect(Collectors.toList());
                return filter(args[1], names);
            }

            if (sender instanceof Player player) {
                Location location = player.getLocation();
                if (args.length == 3) {
                    return filter(args[2], List.of(String.valueOf(location.getBlockX()), "~"));
                }
                if (args.length == 4) {
                    return filter(args[3], List.of(String.valueOf(location.getBlockY()), "~"));
                }
                if (args.length == 5) {
                    return filter(args[4], List.of(String.valueOf(location.getBlockZ()), "~"));
                }
            }

            if (args.length == 6) {
                return filter(args[5], Arrays.asList("0", "90", "180", "270"));
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
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
