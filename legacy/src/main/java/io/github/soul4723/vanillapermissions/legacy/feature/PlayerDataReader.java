package io.github.soul4723.vanillapermissions.legacy.feature;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Reads one top-level int tag from a gzipped NBT file such as {@code <world>/playerdata/<uuid>.dat}, without NMS.
 * It checks lengths and nesting so a damaged file fails with an {@link IOException} instead of looping or
 * overflowing the stack.
 */
final class PlayerDataReader {

    /** Vanilla's {@code NBTTagCompound} refuses deeper nesting than this too. */
    private static final int MAX_DEPTH = 512;

    private PlayerDataReader() {}

    /** The tag's value, or {@code null} if the file does not exist or has no int tag of that name at the top level. */
    static Integer readRootInt(File file, String name) throws IOException {
        if (!file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) {
            return readRootInt(in, name);
        }
    }

    /** As {@link #readRootInt(File, String)}, from a gzip stream; the caller closes it. */
    static Integer readRootInt(InputStream gzip, String name) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(gzip)));
        if (in.readByte() != 10) throw new IOException("Not an NBT compound");
        in.readUTF();
        while (true) {
            byte type = in.readByte();
            if (type == 0) return null;
            String tag = in.readUTF();
            if (type == 3 && tag.equals(name)) return in.readInt();
            skip(in, type, 1);
        }
    }

    private static void skip(DataInputStream in, byte type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nested deeper than " + MAX_DEPTH);
        switch (type) {
            case 1: skipFully(in, 1); break;
            case 2: skipFully(in, 2); break;
            case 3: case 5: skipFully(in, 4); break;
            case 4: case 6: skipFully(in, 8); break;
            case 7: skipFully(in, length(in)); break;
            case 8: skipFully(in, in.readUnsignedShort()); break;
            case 9: {
                byte element = in.readByte();
                int length = length(in);
                for (int i = 0; i < length; i++) skip(in, element, depth + 1);
                break;
            }
            case 10: {
                byte inner;
                while ((inner = in.readByte()) != 0) {
                    in.readUTF();
                    skip(in, inner, depth + 1);
                }
                break;
            }
            case 11: skipFully(in, 4L * length(in)); break;
            case 12: skipFully(in, 8L * length(in)); break;
            default: throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private static int length(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0) throw new IOException("Negative NBT length " + length);
        return length;
    }

    private static void skipFully(DataInputStream in, long bytes) throws IOException {
        while (bytes > 0) {
            int step = (int) Math.min(bytes, Integer.MAX_VALUE);
            int skipped = in.skipBytes(step);
            if (skipped <= 0) throw new EOFException("NBT data ends early");
            bytes -= skipped;
        }
    }
}
