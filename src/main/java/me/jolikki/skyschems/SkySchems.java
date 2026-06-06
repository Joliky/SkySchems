package me.jolikki.skyschems;

import me.jolikki.skyschems.schematic.SchematicManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class SkySchems extends JavaPlugin {

    private ConfigManager configManager;
    private SchematicManager schematicManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        schematicManager = new SchematicManager(this, configManager);

        registerCommands();
        registerListeners();

        getLogger().info("SkySchems has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SkySchems has been disabled.");
    }

    private void registerCommands() {
        PluginCommand command = getCommand("skyschems");
        if (command == null) {
            getLogger().severe("Command /skyschems is missing in plugin.yml");
            return;
        }

        SkySchemsCommand skySchemsCommand = new SkySchemsCommand(configManager, schematicManager);
        command.setExecutor(skySchemsCommand);
        command.setTabCompleter(skySchemsCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new SchematicPreviewListener(schematicManager),
                this
        );
    }
}
