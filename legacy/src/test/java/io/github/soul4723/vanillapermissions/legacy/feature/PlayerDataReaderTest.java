package io.github.soul4723.vanillapermissions.legacy.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerDataReaderTest {

    @FunctionalInterface
    interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    /** Gzipped NBT: a root compound named "" holding what {@code body} writes, then the end tag. */
    private static byte[] nbt(Body body) throws IOException {
        return gzip(out -> {
            out.writeByte(10);
            out.writeUTF("");
            body.write(out);
            out.writeByte(0);
        });
    }

    private static byte[] gzip(Body body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            body.write(out);
        }
        return bytes.toByteArray();
    }

    private static Integer read(byte[] data) throws IOException {
        return PlayerDataReader.readRootInt(new ByteArrayInputStream(data), "playerGameType");
    }

    private static void tag(DataOutputStream out, int type, String name) throws IOException {
        out.writeByte(type);
        out.writeUTF(name);
    }

    private static void intTag(DataOutputStream out, String name, int value) throws IOException {
        tag(out, 3, name);
        out.writeInt(value);
    }

    @Test
    void findsTheTagAfterOneTagOfEveryType() throws IOException {
        byte[] data = nbt(out -> {
            tag(out, 1, "byte");
            out.writeByte(5);
            tag(out, 2, "short");
            out.writeShort(6);
            intTag(out, "int", 7);
            tag(out, 4, "long");
            out.writeLong(8);
            tag(out, 5, "float");
            out.writeFloat(1.5f);
            tag(out, 6, "double");
            out.writeDouble(2.5);
            tag(out, 7, "bytes");
            out.writeInt(3);
            out.write(new byte[]{1, 2, 3});
            tag(out, 8, "string");
            out.writeUTF("hello");
            tag(out, 9, "listOfCompounds");
            out.writeByte(10);
            out.writeInt(2);
            for (int i = 0; i < 2; i++) {
                intTag(out, "playerGameType", 9);
                out.writeByte(0);
            }
            tag(out, 9, "listOfInts");
            out.writeByte(3);
            out.writeInt(2);
            out.writeInt(1);
            out.writeInt(2);
            tag(out, 10, "nested");
            intTag(out, "playerGameType", 3);
            tag(out, 10, "deeper");
            intTag(out, "playerGameType", 2);
            out.writeByte(0);
            out.writeByte(0);
            tag(out, 11, "ints");
            out.writeInt(2);
            out.writeInt(10);
            out.writeInt(11);
            tag(out, 12, "longs");
            out.writeInt(2);
            out.writeLong(10);
            out.writeLong(11);
            intTag(out, "playerGameType", 1);
        });
        assertEquals(1, read(data));
    }

    @Test
    void emptyListsAndArraysAreSkipped() throws IOException {
        byte[] data = nbt(out -> {
            tag(out, 9, "empty");
            out.writeByte(0);
            out.writeInt(0);
            tag(out, 7, "bytes");
            out.writeInt(0);
            tag(out, 11, "ints");
            out.writeInt(0);
            intTag(out, "playerGameType", 2);
        });
        assertEquals(2, read(data));
    }

    @Test
    void ignoresTheTagInANestedCompound() throws IOException {
        byte[] data = nbt(out -> {
            tag(out, 10, "abilities");
            intTag(out, "playerGameType", 3);
            out.writeByte(0);
        });
        assertNull(read(data));
    }

    @Test
    void missingTagIsNull() throws IOException {
        assertNull(read(nbt(out -> intTag(out, "other", 1))));
        assertNull(read(nbt(out -> { })));
    }

    @Test
    void sameNameWithAnotherTypeIsNull() throws IOException {
        byte[] data = nbt(out -> {
            tag(out, 8, "playerGameType");
            out.writeUTF("1");
        });
        assertNull(read(data));
    }

    @Test
    void missingFileIsNull(@TempDir File dir) throws IOException {
        assertNull(PlayerDataReader.readRootInt(new File(dir, "absent.dat"), "playerGameType"));
        assertNull(PlayerDataReader.readRootInt(dir, "playerGameType"));
    }

    @Test
    void readsARealFile(@TempDir File dir) throws IOException {
        File file = new File(dir, "player.dat");
        Files.write(file.toPath(), nbt(out -> {
            tag(out, 8, "name");
            out.writeUTF("Steve");
            intTag(out, "playerGameType", 2);
        }));
        assertEquals(2, PlayerDataReader.readRootInt(file, "playerGameType"));
        assertNull(PlayerDataReader.readRootInt(file, "absent"));
    }

    @Test
    void truncatedNbtIsAnIOException() throws IOException {
        byte[] data = gzip(out -> {
            out.writeByte(10);
            out.writeUTF("");
            tag(out, 4, "long");
            out.writeInt(0); // half a long
        });
        assertThrows(IOException.class, () -> read(data));
    }

    @Test
    void nbtWithoutEndTagIsAnIOException() throws IOException {
        byte[] data = gzip(out -> {
            out.writeByte(10);
            out.writeUTF("");
            intTag(out, "other", 1);
        });
        assertThrows(IOException.class, () -> read(data));
    }

    @Test
    void truncatedGzipIsAnIOException() throws IOException {
        byte[] data = nbt(out -> intTag(out, "other", 1));
        byte[] cut = Arrays.copyOf(data, data.length / 2);
        assertThrows(IOException.class, () -> read(cut));
    }

    @Test
    void notGzipIsAnIOException() {
        assertThrows(IOException.class, () -> read(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}));
    }

    @Test
    void unknownTagTypeIsAnIOException() throws IOException {
        byte[] data = nbt(out -> {
            tag(out, 13, "weird");
            out.writeInt(0);
        });
        assertThrows(IOException.class, () -> read(data));
    }

    @Test
    void negativeLengthsAreIOExceptions() throws IOException {
        byte[] bytes = nbt(out -> {
            tag(out, 7, "bytes");
            out.writeInt(-1);
        });
        assertThrows(IOException.class, () -> read(bytes));
        byte[] list = nbt(out -> {
            tag(out, 9, "list");
            out.writeByte(1);
            out.writeInt(-5);
        });
        assertThrows(IOException.class, () -> read(list));
        byte[] ints = nbt(out -> {
            tag(out, 11, "ints");
            out.writeInt(-1);
        });
        assertThrows(IOException.class, () -> read(ints));
    }

    @Test
    void lengthPastTheEndOfDataIsAnIOException() throws IOException {
        byte[] data = nbt(out -> {
            tag(out, 7, "bytes");
            out.writeInt(Integer.MAX_VALUE);
        });
        assertThrows(IOException.class, () -> read(data));
    }

    @Test
    void nonCompoundRootIsAnIOException() throws IOException {
        byte[] data = gzip(out -> {
            out.writeByte(9);
            out.writeUTF("");
            out.writeByte(0);
            out.writeInt(0);
        });
        assertThrows(IOException.class, () -> read(data));
    }

    @Test
    void nestingDeeperThanTheLimitIsAnIOException() throws IOException {
        byte[] data = gzip(out -> {
            out.writeByte(10);
            out.writeUTF("");
            writeNested(out, 600);
            out.writeByte(0);
        });
        assertThrows(IOException.class, () -> read(data));
    }

    @Test
    void nestingWithinTheLimitIsAccepted() throws IOException {
        byte[] data = gzip(out -> {
            out.writeByte(10);
            out.writeUTF("");
            writeNested(out, 100);
            intTag(out, "playerGameType", 1);
            out.writeByte(0);
        });
        assertEquals(1, read(data));
    }

    /** {@code depth} compounds named "n", each inside the previous one, then all of them closed. */
    private static void writeNested(DataOutputStream out, int depth) throws IOException {
        for (int i = 0; i < depth; i++) tag(out, 10, "n");
        for (int i = 0; i < depth; i++) out.writeByte(0);
    }
}
