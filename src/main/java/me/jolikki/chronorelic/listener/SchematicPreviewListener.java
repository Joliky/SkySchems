package me.jolikki.chronorelic.listener;

import me.jolikki.chronorelic.schematic.SchematicManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;

public class SchematicPreviewListener implements Listener {

    private final SchematicManager schematicManager;

    public SchematicPreviewListener(SchematicManager schematicManager) {
        this.schematicManager = schematicManager;
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        if (schematicManager.hasPreviewBlock(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (schematicManager.hasPreviewBlock(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}
