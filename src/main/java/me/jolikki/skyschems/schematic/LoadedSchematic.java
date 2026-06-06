package me.jolikki.skyschems.schematic;

import org.bukkit.block.data.BlockData;

import java.util.List;

public class LoadedSchematic {

    private final int width;
    private final int height;
    private final int length;
    private final List<SchematicBlock> blocks;

    public LoadedSchematic(int width, int height, int length, List<SchematicBlock> blocks) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.blocks = blocks;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLength() {
        return length;
    }

    public List<SchematicBlock> getBlocks() {
        return blocks;
    }

    public record SchematicBlock(int x, int y, int z, BlockData blockData) {
    }
}
