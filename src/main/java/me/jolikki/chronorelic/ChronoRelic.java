package me.jolikki.chronorelic;

import me.jolikki.chronorelic.command.ControlCommand;
import me.jolikki.chronorelic.listener.RelicListener;
import me.jolikki.chronorelic.manager.ConfigManager;
import me.jolikki.chronorelic.manager.CooldownManager;
import me.jolikki.chronorelic.manager.PermissionManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ChronoRelic extends JavaPlugin {

    private ConfigManager configManager;
    private PermissionManager permissionManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        permissionManager = new PermissionManager(this);

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

        command.setExecutor(new ControlCommand(configManager));
    }

    private void registerListeners() {
        CooldownManager cooldownManager = new CooldownManager();
        getServer().getPluginManager().registerEvents(
                new RelicListener(this, cooldownManager, configManager, permissionManager),
                this
        );
    }
}
