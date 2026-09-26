package com.jruk8.jmanhunt.lobby.schem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle.BoundEntry;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle.Offset;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle.TeleportEntry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Pure .jmhlobby zip codec: schem.nbt bytes plus a lobby.json manifest
 * of relative bounds and teleports. Extra zip entries are ignored on
 * read; anything malformed fails with an IOException. No Bukkit.
 */
public final class JmhLobbyCodec {

    /** Zip entry holding the raw structure bytes. */
    public static final String SCHEM_ENTRY = "schem.nbt";

    /** Zip entry holding the relative lobby manifest. */
    public static final String LOBBY_ENTRY = "lobby.json";

    /** Manifest format version this codec reads and writes. */
    public static final int FORMAT = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JmhLobbyCodec() {
    }

    /** Serializes a bundle to .jmhlobby zip bytes. */
    public static byte[] write(JmhLobbyBundle bundle) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(raw, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(SCHEM_ENTRY));
            zip.write(bundle.nbt());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(LOBBY_ENTRY));
            byte[] manifest = GSON.toJson(manifest(bundle))
                    .getBytes(StandardCharsets.UTF_8);
            zip.write(manifest);
            zip.closeEntry();
        }
        return raw.toByteArray();
    }

    /** Parses .jmhlobby zip bytes back into a bundle. */
    public static JmhLobbyBundle read(byte[] data) throws IOException {
        byte[] nbt = null;
        byte[] manifest = null;
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(SCHEM_ENTRY)) {
                    nbt = zip.readAllBytes();
                } else if (entry.getName().equals(LOBBY_ENTRY)) {
                    manifest = zip.readAllBytes();
                }
                zip.closeEntry();
            }
        } catch (IllegalArgumentException corrupt) {
            throw new IOException("Not a .jmhlobby zip: " + corrupt.getMessage(), corrupt);
        }
        if (nbt == null) {
            throw new IOException("Missing " + SCHEM_ENTRY + " in .jmhlobby bundle.");
        }
        if (manifest == null) {
            throw new IOException("Missing " + LOBBY_ENTRY + " in .jmhlobby bundle.");
        }
        return parseManifest(nbt, new String(manifest, StandardCharsets.UTF_8));
    }

    private static JsonObject manifest(JmhLobbyBundle bundle) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.add("origin", offset(bundle.origin()));
        JsonArray bounds = new JsonArray();
        for (BoundEntry entry : bundle.bounds()) {
            JsonObject bound = new JsonObject();
            bound.addProperty("lobby", entry.lobby());
            bound.add("min", offset(entry.min()));
            bound.add("max", offset(entry.max()));
            bounds.add(bound);
        }
        root.add("bounds", bounds);
        JsonArray teleports = new JsonArray();
        for (TeleportEntry entry : bundle.teleports()) {
            JsonObject teleport = new JsonObject();
            teleport.addProperty("lobby", entry.lobby());
            teleport.addProperty("x", entry.x());
            teleport.addProperty("y", entry.y());
            teleport.addProperty("z", entry.z());
            teleport.addProperty("yaw", entry.yaw());
            teleport.addProperty("pitch", entry.pitch());
            teleports.add(teleport);
        }
        root.add("teleports", teleports);
        return root;
    }

    private static JsonObject offset(Offset offset) {
        JsonObject point = new JsonObject();
        point.addProperty("x", offset.x());
        point.addProperty("y", offset.y());
        point.addProperty("z", offset.z());
        return point;
    }

    private static JmhLobbyBundle parseManifest(byte[] nbt, String raw) throws IOException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(raw);
        } catch (JsonSyntaxException corrupt) {
            throw new IOException("Malformed lobby.json: " + corrupt.getMessage(), corrupt);
        }
        if (!parsed.isJsonObject()) {
            throw new IOException("Malformed lobby.json: root must be an object.");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (!root.has("format") || !root.get("format").isJsonPrimitive()
                || !root.get("format").getAsJsonPrimitive().isNumber()
                || root.get("format").getAsInt() != FORMAT) {
            throw new IOException("Unsupported lobby.json format: expected " + FORMAT + ".");
        }
        Offset origin = parseOffset(root, "origin");
        List<BoundEntry> bounds = new ArrayList<>();
        for (JsonElement element : parseArray(root, "bounds")) {
            bounds.add(parseBound(element));
        }
        List<TeleportEntry> teleports = new ArrayList<>();
        for (JsonElement element : parseArray(root, "teleports")) {
            teleports.add(parseTeleport(element));
        }
        return new JmhLobbyBundle(nbt, origin, List.copyOf(bounds), List.copyOf(teleports));
    }

    private static List<JsonElement> parseArray(JsonObject root, String key) throws IOException {
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            throw new IOException("Malformed lobby.json: '" + key + "' must be an array.");
        }
        List<JsonElement> elements = new ArrayList<>();
        root.getAsJsonArray(key).forEach(elements::add);
        return elements;
    }

    private static BoundEntry parseBound(JsonElement element) throws IOException {
        JsonObject bound = asObject(element, "bounds entry");
        return new BoundEntry(parseInt(bound, "lobby"),
                parseOffset(bound, "min"), parseOffset(bound, "max"));
    }

    private static TeleportEntry parseTeleport(JsonElement element) throws IOException {
        JsonObject teleport = asObject(element, "teleports entry");
        return new TeleportEntry(parseInt(teleport, "lobby"),
                parseDouble(teleport, "x"), parseDouble(teleport, "y"), parseDouble(teleport, "z"),
                parseFloat(teleport, "yaw"), parseFloat(teleport, "pitch"));
    }

    private static Offset parseOffset(JsonObject parent, String key) throws IOException {
        JsonObject point = asObject(parent.get(key), "'" + key + "' point");
        return new Offset(parseInt(point, "x"), parseInt(point, "y"), parseInt(point, "z"));
    }

    private static JsonObject asObject(JsonElement element, String what) throws IOException {
        if (element == null || !element.isJsonObject()) {
            throw new IOException("Malformed lobby.json: " + what + " must be an object.");
        }
        return element.getAsJsonObject();
    }

    private static int parseInt(JsonObject parent, String key) throws IOException {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive()
                || !parent.get(key).getAsJsonPrimitive().isNumber()) {
            throw new IOException("Malformed lobby.json: '" + key + "' must be a number.");
        }
        try {
            return parent.get(key).getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException corrupt) {
            throw new IOException("Malformed lobby.json: '" + key + "' must be a number.");
        }
    }

    private static double parseDouble(JsonObject parent, String key) throws IOException {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive()
                || !parent.get(key).getAsJsonPrimitive().isNumber()) {
            throw new IOException("Malformed lobby.json: '" + key + "' must be a number.");
        }
        try {
            return parent.get(key).getAsDouble();
        } catch (NumberFormatException | UnsupportedOperationException corrupt) {
            throw new IOException("Malformed lobby.json: '" + key + "' must be a number.");
        }
    }

    private static float parseFloat(JsonObject parent, String key) throws IOException {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive()
                || !parent.get(key).getAsJsonPrimitive().isNumber()) {
            throw new IOException("Malformed lobby.json: '" + key + "' must be a number.");
        }
        try {
            return parent.get(key).getAsFloat();
        } catch (NumberFormatException | UnsupportedOperationException corrupt) {
            throw new IOException("Malformed lobby.json: '" + key + "' must be a number.");
        }
    }
}
