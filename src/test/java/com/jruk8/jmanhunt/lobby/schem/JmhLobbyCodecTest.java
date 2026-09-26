package com.jruk8.jmanhunt.lobby.schem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle.BoundEntry;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle.Offset;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle.TeleportEntry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/** .jmhlobby zip codec round-trips and failure modes. */
class JmhLobbyCodecTest {

    private static JmhLobbyBundle bundle() {
        return new JmhLobbyBundle(new byte[]{1, 2, 3, 4},
                new Offset(100, 64, -200),
                List.of(new BoundEntry(0, new Offset(0, 0, 0), new Offset(9, 9, 9)),
                        new BoundEntry(1, new Offset(-5, 0, 3), new Offset(4, 9, 12))),
                List.of(new TeleportEntry(0, 5.5, 1.0, 5.5, 90.0f, 0.0f)));
    }

    @Test
    void roundTrip() throws IOException {
        JmhLobbyBundle bundle = bundle();

        JmhLobbyBundle parsed = JmhLobbyCodec.read(JmhLobbyCodec.write(bundle));

        assertArrayEquals(bundle.nbt(), parsed.nbt());
        assertEquals(bundle.origin(), parsed.origin());
        assertEquals(bundle.bounds(), parsed.bounds());
        assertEquals(bundle.teleports(), parsed.teleports());
    }

    @Test
    void roundTripEmptyLobbyData() throws IOException {
        JmhLobbyBundle bundle = new JmhLobbyBundle(new byte[]{9},
                new Offset(0, 64, 0), List.of(), List.of());

        JmhLobbyBundle parsed = JmhLobbyCodec.read(JmhLobbyCodec.write(bundle));

        assertArrayEquals(bundle.nbt(), parsed.nbt());
        assertEquals(bundle.origin(), parsed.origin());
        assertEquals(bundle.bounds(), parsed.bounds());
        assertEquals(bundle.teleports(), parsed.teleports());
    }

    @Test
    void missingEntriesFail() throws IOException {
        byte[] schemOnly = zip(new String[]{JmhLobbyCodec.SCHEM_ENTRY},
                new byte[][]{{1, 2}});
        byte[] lobbyOnly = zip(new String[]{JmhLobbyCodec.LOBBY_ENTRY},
                new byte[][]{"{}".getBytes(StandardCharsets.UTF_8)});

        IOException schemMissing = assertThrows(IOException.class,
                () -> JmhLobbyCodec.read(lobbyOnly));
        assertTrue(schemMissing.getMessage().contains(JmhLobbyCodec.SCHEM_ENTRY));
        assertThrows(IOException.class, () -> JmhLobbyCodec.read(schemOnly));
    }

    @Test
    void malformedManifestFails() {
        byte[] garbage = zip(new String[]{JmhLobbyCodec.SCHEM_ENTRY, JmhLobbyCodec.LOBBY_ENTRY},
                new byte[][]{{1}, "not json".getBytes(StandardCharsets.UTF_8)});
        byte[] wrongFormat = zip(new String[]{JmhLobbyCodec.SCHEM_ENTRY, JmhLobbyCodec.LOBBY_ENTRY},
                new byte[][]{{1},
                        "{\"format\": 99, \"origin\": {\"x\": 0, \"y\": 0, \"z\": 0},"
                                .getBytes(StandardCharsets.UTF_8)});
        byte[] missingOrigin = zip(new String[]{JmhLobbyCodec.SCHEM_ENTRY, JmhLobbyCodec.LOBBY_ENTRY},
                new byte[][]{{1},
                        "{\"format\": 1, \"bounds\": [], \"teleports\": []}"
                                .getBytes(StandardCharsets.UTF_8)});

        assertThrows(IOException.class, () -> JmhLobbyCodec.read(garbage));
        assertThrows(IOException.class, () -> JmhLobbyCodec.read(wrongFormat));
        assertThrows(IOException.class, () -> JmhLobbyCodec.read(missingOrigin));
        assertThrows(IOException.class, () -> JmhLobbyCodec.read(new byte[]{1, 2, 3}));
    }

    @Test
    void extraEntriesAreIgnored() throws IOException {
        JmhLobbyBundle bundle = bundle();
        byte[] manifest = manifestBytes(JmhLobbyCodec.write(bundle));
        byte[] zip = zip(
                new String[]{JmhLobbyCodec.SCHEM_ENTRY, "__MACOSX/._schem.nbt",
                        JmhLobbyCodec.LOBBY_ENTRY},
                new byte[][]{bundle.nbt(), {0}, manifest});

        JmhLobbyBundle parsed = JmhLobbyCodec.read(zip);

        assertArrayEquals(bundle.nbt(), parsed.nbt());
        assertEquals(bundle.bounds(), parsed.bounds());
        assertEquals(bundle.teleports(), parsed.teleports());
    }

    private static byte[] manifestBytes(byte[] zip) throws IOException {
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().equals(JmhLobbyCodec.LOBBY_ENTRY)) {
                    return in.readAllBytes();
                }
            }
        }
        throw new IOException("No manifest in test bundle.");
    }

    private static byte[] zip(String[] names, byte[][] bodies) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(raw, StandardCharsets.UTF_8)) {
                for (int index = 0; index < names.length; index++) {
                    zip.putNextEntry(new ZipEntry(names[index]));
                    zip.write(bodies[index]);
                    zip.closeEntry();
                }
            }
            return raw.toByteArray();
        } catch (IOException failed) {
            throw new AssertionError(failed);
        }
    }
}
