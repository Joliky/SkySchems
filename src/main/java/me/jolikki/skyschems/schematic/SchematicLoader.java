package me.jolikki.skyschems.schematic;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class SchematicLoader {

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    public LoadedSchematic load(File file, boolean ignoreAir) throws IOException {
        Map<String, Object> root = readRoot(file);
        int width = getNumber(root, "Width").intValue();
        int height = getNumber(root, "Height").intValue();
        int length = getNumber(root, "Length").intValue();
        byte[] blockData = (byte[]) root.get("BlockData");

        @SuppressWarnings("unchecked")
        Map<String, Object> palette = (Map<String, Object>) root.get("Palette");

        if (blockData == null || palette == null) {
            throw new IOException("Unsupported schematic: missing Palette or BlockData");
        }

        Map<Integer, BlockData> blocksById = new HashMap<>();
        for (Map.Entry<String, Object> entry : palette.entrySet()) {
            blocksById.put(((Number) entry.getValue()).intValue(), Bukkit.createBlockData(entry.getKey()));
        }

        int volume = width * height * length;
        int[] paletteIndexes = readVarInts(blockData, volume);
        List<LoadedSchematic.SchematicBlock> blocks = new ArrayList<>();

        for (int index = 0; index < paletteIndexes.length; index++) {
            BlockData data = blocksById.get(paletteIndexes[index]);
            if (data == null || (ignoreAir && data.getMaterial().isAir())) {
                continue;
            }

            int x = index % width;
            int y = index / (width * length);
            int z = (index / width) % length;
            blocks.add(new LoadedSchematic.SchematicBlock(x, y, z, data));
        }

        return new LoadedSchematic(width, height, length, blocks);
    }

    private Map<String, Object> readRoot(File file) throws IOException {
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(
                new BufferedInputStream(new FileInputStream(file))))) {
            int type = input.readUnsignedByte();
            if (type != TAG_COMPOUND) {
                throw new IOException("Unsupported schematic: root tag is not a compound");
            }

            readString(input);
            return readCompound(input);
        }
    }

    private Map<String, Object> readCompound(DataInputStream input) throws IOException {
        Map<String, Object> compound = new HashMap<>();

        while (true) {
            int type = input.readUnsignedByte();
            if (type == TAG_END) {
                return compound;
            }

            String name = readString(input);
            compound.put(name, readPayload(input, type));
        }
    }

    private Object readPayload(DataInputStream input, int type) throws IOException {
        return switch (type) {
            case TAG_BYTE -> input.readByte();
            case TAG_SHORT -> input.readShort();
            case TAG_INT -> input.readInt();
            case TAG_LONG -> input.readLong();
            case TAG_FLOAT -> input.readFloat();
            case TAG_DOUBLE -> input.readDouble();
            case TAG_BYTE_ARRAY -> readByteArray(input);
            case TAG_STRING -> readString(input);
            case TAG_LIST -> readList(input);
            case TAG_COMPOUND -> readCompound(input);
            case TAG_INT_ARRAY -> readIntArray(input);
            case TAG_LONG_ARRAY -> readLongArray(input);
            default -> throw new IOException("Unsupported NBT tag type: " + type);
        };
    }

    private List<Object> readList(DataInputStream input) throws IOException {
        int elementType = input.readUnsignedByte();
        int size = input.readInt();
        List<Object> list = new ArrayList<>(Math.max(size, 0));

        for (int i = 0; i < size; i++) {
            list.add(readPayload(input, elementType));
        }

        return list;
    }

    private byte[] readByteArray(DataInputStream input) throws IOException {
        int size = input.readInt();
        byte[] value = new byte[size];
        input.readFully(value);
        return value;
    }

    private int[] readIntArray(DataInputStream input) throws IOException {
        int size = input.readInt();
        int[] value = new int[size];
        for (int i = 0; i < size; i++) {
            value[i] = input.readInt();
        }
        return value;
    }

    private long[] readLongArray(DataInputStream input) throws IOException {
        int size = input.readInt();
        long[] value = new long[size];
        for (int i = 0; i < size; i++) {
            value[i] = input.readLong();
        }
        return value;
    }

    private String readString(DataInputStream input) throws IOException {
        int size = input.readUnsignedShort();
        byte[] bytes = new byte[size];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Number getNumber(Map<String, Object> root, String key) throws IOException {
        Object value = root.get(key);
        if (!(value instanceof Number number)) {
            throw new IOException("Unsupported schematic: missing numeric " + key);
        }

        return number;
    }

    private int[] readVarInts(byte[] data, int expectedAmount) throws IOException {
        int[] values = new int[expectedAmount];
        int valueIndex = 0;
        int dataIndex = 0;

        while (valueIndex < expectedAmount && dataIndex < data.length) {
            int value = 0;
            int position = 0;

            while (true) {
                if (dataIndex >= data.length) {
                    throw new EOFException("Unexpected end of schematic block data");
                }

                int currentByte = data[dataIndex++] & 0xFF;
                value |= (currentByte & 0x7F) << position;

                if ((currentByte & 0x80) == 0) {
                    values[valueIndex++] = value;
                    break;
                }

                position += 7;
                if (position > 28) {
                    throw new IOException("Invalid schematic varint");
                }
            }
        }

        if (valueIndex != expectedAmount) {
            throw new IOException("Schematic block data is shorter than expected");
        }

        return values;
    }
}
