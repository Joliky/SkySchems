package me.jolikki.chronorelic;

import me.jolikki.chronorelic.command.ControlCommand;
import me.jolikki.chronorelic.listener.RelicListener;
import me.jolikki.chronorelic.listener.SchematicPreviewListener;
import me.jolikki.chronorelic.manager.ConfigManager;
import me.jolikki.chronorelic.manager.CooldownManager;
import me.jolikki.chronorelic.manager.PermissionManager;
import me.jolikki.chronorelic.schematic.SchematicManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ChronoRelic extends JavaPlugin {

    private ConfigManager configManager;
    private PermissionManager permissionManager;
    private SchematicManager schematicManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        permissionManager = new PermissionManager(this);
        schematicManager = new SchematicManager(this, configManager);

        registerCommands();
        registerListeners();

        getLogger().info("ChronoRelic has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ChronoRelic has been disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    private void registerCommands() {
        PluginCommand command = getCommand("cr");
        if (command == null) {
            getLogger().severe("Command /cr is missing in plugin.yml");
            return;
        }

        ControlCommand controlCommand = new ControlCommand(this, configManager, schematicManager);
        command.setExecutor(controlCommand);
        command.setTabCompleter(controlCommand);
    }

    private void registerListeners() {
        CooldownManager cooldownManager = new CooldownManager();
        getServer().getPluginManager().registerEvents(
                new RelicListener(this, cooldownManager, configManager, permissionManager),
                this
        );
        getServer().getPluginManager().registerEvents(
                new SchematicPreviewListener(schematicManager),
                this
        );
    }
}
