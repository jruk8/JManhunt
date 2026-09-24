package com.jruk8.jmanhunt.modifiers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierChance;
import com.jruk8.jmanhunt.modifiers.config.ModifierCommands;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierExecution;
import com.jruk8.jmanhunt.modifiers.config.ModifierInterval;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierOnStart;
import com.jruk8.jmanhunt.modifiers.config.ModifierOptions;
import com.jruk8.jmanhunt.modifiers.config.ModifierPickRandom;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.bukkit.Material;

/**
 * Share strings for modifiers and presets: minified JSON, a 4-byte
 * CRC32 of the JSON, raw Deflate when it shrinks the payload, and
 * unpadded URL-safe Base64 under a versioned tag. Decoding enforces
 * a 1 MB inflation cap and strict schema validation; anything
 * corrupt or off-schema decodes to empty.
 */
public final class ModifierCodec {

    /** Tag for deflated payloads. */
    public static final String TAG_DEFLATED = "JMH1D:";
    /** Tag for raw payloads. */
    public static final String TAG_RAW = "JMH1R:";
    /** Hard ceiling on decompressed JSON bytes. */
    public static final int MAX_JSON_BYTES = 1024 * 1024;

    private static final Gson GSON = new Gson();
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Pattern TRIGGER_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

    private ModifierCodec() {
    }

    /** True when an id fits the shared modifier/preset id shape. */
    public static boolean validId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    /** Decoded share string: the kind, id, and validated entry. */
    public enum Kind {
        MODIFIER,
        PRESET
    }

    /** One decoded payload; exactly one of entry or preset is set. */
    public record Imported(Kind kind, String id, ModifierEntry entry, ModifierPreset preset) {
    }

    /** Share string for one modifier. */
    public static String exportModifier(String id, ModifierEntry entry) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("type", "modifier");
        envelope.addProperty("id", id);
        envelope.add("data", modifierData(entry));
        return encode(GSON.toJson(envelope));
    }

    /** Share string for one preset. */
    public static String exportPreset(String id, ModifierPreset preset) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("type", "preset");
        envelope.addProperty("id", id);
        envelope.add("data", presetData(preset));
        return encode(GSON.toJson(envelope));
    }

    /**
     * Decodes and validates a share string. Empty when the tag,
     * checksum, JSON, or schema is wrong.
     */
    public static Optional<Imported> decode(String payload) {
        if (payload == null) {
            return Optional.empty();
        }
        Optional<String> json = decodeJson(payload.trim());
        if (json.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonElement root = JsonParser.parseString(json.get());
            if (!root.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject envelope = root.getAsJsonObject();
            String type = requiredString(envelope, "type");
            String id = requiredString(envelope, "id");
            if (!validId(id)) {
                return Optional.empty();
            }
            JsonObject data = optionalObject(envelope, "data");
            if (data == null) {
                return Optional.empty();
            }
            return switch (type) {
                case "modifier" -> Optional.of(new Imported(Kind.MODIFIER, id, readModifier(data), null));
                case "preset" -> Optional.of(new Imported(Kind.PRESET, id, null, readPreset(data)));
                default -> Optional.empty();
            };
        } catch (JsonSyntaxException | Invalid exception) {
            return Optional.empty();
        }
    }

    private static JsonObject modifierData(ModifierEntry entry) {
        JsonObject data = new JsonObject();
        data.addProperty("enabled", entry.isEnabled());
        ModifierMeta meta = entry.getMeta();
        JsonObject written = new JsonObject();
        written.addProperty("name",
                meta == null || meta.getName() == null || meta.getName().isBlank()
                        ? ModifierStore.DEFAULT_NAME : meta.getName());
        if (meta != null && meta.getDescription() != null) {
            written.addProperty("description", meta.getDescription());
        }
        written.addProperty("item",
                meta == null || meta.getItem() == null || meta.getItem().isBlank()
                        ? Material.STONE.name() : meta.getItem());
        if (meta != null && meta.getAuthor() != null) {
            written.addProperty("author", meta.getAuthor());
        }
        data.add("meta", written);
        ModifierBehavior behavior = entry.getBehavior();
        if (behavior != null) {
            data.add("behavior", behaviorData(behavior));
        }
        return data;
    }

    private static JsonObject behaviorData(ModifierBehavior behavior) {
        JsonObject data = new JsonObject();
        if (behavior.getRunsOn() != null) {
            JsonArray runs = new JsonArray();
            for (String trigger : behavior.getRunsOn()) {
                runs.add(trigger);
            }
            data.add("runs-on", runs);
        }
        if (behavior.getOnStart() != null && behavior.getOnStart().getPreStartOrder() != null) {
            JsonObject onStart = new JsonObject();
            onStart.addProperty("pre-start-order", behavior.getOnStart().getPreStartOrder());
            data.add("on-start", onStart);
        }
        if (behavior.getOptions() != null) {
            data.add("options", optionsData(behavior.getOptions()));
        }
        if (behavior.getCommands() != null) {
            JsonObject commands = new JsonObject();
            for (Map.Entry<String, List<String>> list : behavior.getCommands().getLists().entrySet()) {
                JsonArray lines = new JsonArray();
                for (String line : list.getValue()) {
                    lines.add(line);
                }
                commands.add(list.getKey(), lines);
            }
            data.add("commands", commands);
        }
        return data;
    }

    private static JsonObject optionsData(ModifierOptions options) {
        JsonObject data = new JsonObject();
        ModifierInterval interval = options.getIntervalSettings();
        if (interval != null) {
            JsonObject written = new JsonObject();
            if (interval.getInterval() != null) {
                written.addProperty("interval", interval.getInterval());
            }
            if (interval.getDeviation() != null) {
                written.addProperty("deviation", interval.getDeviation());
            }
            if (interval.getBehavior() != null) {
                written.addProperty("behavior", interval.getBehavior());
            }
            data.add("interval-settings", written);
        }
        ModifierChance chance = options.getSuccessChance();
        if (chance != null) {
            JsonObject written = new JsonObject();
            if (chance.getChance() != null) {
                written.addProperty("chance", chance.getChance());
            }
            if (chance.getBehavior() != null) {
                written.addProperty("behavior", chance.getBehavior());
            }
            data.add("success-chance", written);
        }
        ModifierExecution execution = options.getExecution();
        if (execution != null) {
            JsonObject written = new JsonObject();
            if (execution.getSelection() != null) {
                written.addProperty("selection", execution.getSelection());
            }
            ModifierPickRandom pick = execution.getPickRandom();
            if (pick != null) {
                JsonObject pickWritten = new JsonObject();
                if (pick.getCount() != null) {
                    pickWritten.addProperty("count", pick.getCount());
                }
                if (pick.getBehavior() != null) {
                    pickWritten.addProperty("behavior", pick.getBehavior());
                }
                written.add("pick-random", pickWritten);
            }
            data.add("execution", written);
        }
        if (options.getDelay() != null) {
            data.addProperty("delay", options.getDelay());
        }
        return data;
    }

    private static JsonObject presetData(ModifierPreset preset) {
        JsonObject data = new JsonObject();
        data.addProperty("name",
                preset.getName() == null || preset.getName().isBlank()
                        ? ModifierStore.DEFAULT_PRESET_NAME : preset.getName());
        if (preset.getDescription() != null) {
            data.addProperty("description", preset.getDescription());
        }
        data.addProperty("item",
                preset.getItem() == null || preset.getItem().isBlank()
                        ? Material.STONE.name() : preset.getItem());
        if (preset.getModifiers() != null) {
            JsonArray members = new JsonArray();
            for (String member : preset.getModifiers()) {
                members.add(member);
            }
            data.add("modifiers", members);
        }
        return data;
    }

    private static ModifierEntry readModifier(JsonObject data) {
        ModifierEntry entry = new ModifierEntry();
        entry.setEnabled(optionalBoolean(data, "enabled", false));
        JsonObject meta = optionalObject(data, "meta");
        if (meta == null) {
            throw new Invalid();
        }
        String name = requiredString(meta, "name");
        if (name.isBlank()) {
            throw new Invalid();
        }
        String item = optionalString(meta, "item", Material.STONE.name());
        Material material = ModifierStore.parseMaterial(item);
        if (material == null || material == Material.AIR) {
            throw new Invalid();
        }
        ModifierMeta written = new ModifierMeta();
        written.setName(name);
        written.setDescription(optionalString(meta, "description", ""));
        written.setItem(item);
        written.setAuthor(optionalString(meta, "author", ""));
        entry.setMeta(written);
        JsonObject behavior = optionalObject(data, "behavior");
        if (behavior != null) {
            entry.setBehavior(readBehavior(behavior));
        }
        return entry;
    }

    private static ModifierBehavior readBehavior(JsonObject data) {
        ModifierBehavior behavior = new ModifierBehavior();
        JsonArray runs = optionalArray(data, "runs-on");
        if (runs != null) {
            List<String> triggers = new ArrayList<>();
            for (JsonElement trigger : runs) {
                if (!trigger.isJsonPrimitive() || !trigger.getAsJsonPrimitive().isString()
                        || !TRIGGER_PATTERN.matcher(trigger.getAsString()).matches()) {
                    throw new Invalid();
                }
                triggers.add(trigger.getAsString());
            }
            behavior.setRunsOn(triggers);
        }
        JsonObject onStart = optionalObject(data, "on-start");
        if (onStart != null) {
            String order = requiredString(onStart, "pre-start-order");
            if (!order.equals("BEFORE") && !order.equals("AFTER")) {
                throw new Invalid();
            }
            ModifierOnStart written = new ModifierOnStart();
            written.setPreStartOrder(order);
            behavior.setOnStart(written);
        }
        JsonObject options = optionalObject(data, "options");
        if (options != null) {
            behavior.setOptions(readOptions(options));
        }
        JsonObject commands = optionalObject(data, "commands");
        if (commands != null) {
            ModifierCommands written = new ModifierCommands();
            for (Map.Entry<String, JsonElement> list : commands.entrySet()) {
                if (list.getKey().isBlank() || !list.getValue().isJsonArray()) {
                    throw new Invalid();
                }
                List<String> lines = new ArrayList<>();
                for (JsonElement line : list.getValue().getAsJsonArray()) {
                    if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString()) {
                        throw new Invalid();
                    }
                    lines.add(line.getAsString());
                }
                written.getLists().put(list.getKey(), lines);
            }
            behavior.setCommands(written);
        }
        return behavior;
    }

    private static ModifierOptions readOptions(JsonObject data) {
        ModifierOptions options = new ModifierOptions();
        JsonObject interval = optionalObject(data, "interval-settings");
        if (interval != null) {
            ModifierInterval written = new ModifierInterval();
            Double seconds = optionalDouble(interval, "interval", null, null, null);
            if (seconds != null) {
                written.setInterval(seconds);
            }
            Double deviation = optionalDouble(interval, "deviation", 0.0, null, null);
            if (deviation != null) {
                written.setDeviation(deviation);
            }
            String behavior = optionalString(interval, "behavior", null);
            if (behavior != null) {
                requireExecutorBehavior(behavior);
                written.setBehavior(behavior);
            }
            options.setIntervalSettings(written);
        }
        JsonObject chance = optionalObject(data, "success-chance");
        if (chance != null) {
            ModifierChance written = new ModifierChance();
            Double fraction = optionalDouble(chance, "chance", 0.0, 1.0, null);
            if (fraction != null) {
                written.setChance(fraction);
            }
            String behavior = optionalString(chance, "behavior", null);
            if (behavior != null) {
                requireExecutorBehavior(behavior);
                written.setBehavior(behavior);
            }
            options.setSuccessChance(written);
        }
        JsonObject execution = optionalObject(data, "execution");
        if (execution != null) {
            ModifierExecution written = new ModifierExecution();
            String selection = optionalString(execution, "selection", null);
            if (selection != null) {
                if (!selection.equals("IN_ORDER") && !selection.equals("PICK_RANDOM")) {
                    throw new Invalid();
                }
                written.setSelection(selection);
            }
            JsonObject pick = optionalObject(execution, "pick-random");
            if (pick != null) {
                ModifierPickRandom pickWritten = new ModifierPickRandom();
                Integer count = optionalInteger(pick, "count", 1, null, null);
                if (count != null) {
                    pickWritten.setCount(count);
                }
                String behavior = optionalString(pick, "behavior", null);
                if (behavior != null) {
                    requireExecutorBehavior(behavior);
                    pickWritten.setBehavior(behavior);
                }
                written.setPickRandom(pickWritten);
            }
            options.setExecution(written);
        }
        Long delay = optionalLong(data, "delay", 0L, null, null);
        if (delay != null) {
            options.setDelay(delay);
        }
        return options;
    }

    private static ModifierPreset readPreset(JsonObject data) {
        String name = requiredString(data, "name");
        if (name.isBlank()) {
            throw new Invalid();
        }
        String item = optionalString(data, "item", Material.STONE.name());
        Material material = ModifierStore.parseMaterial(item);
        if (material == null || material == Material.AIR) {
            throw new Invalid();
        }
        ModifierPreset preset = new ModifierPreset();
        preset.setName(name);
        preset.setDescription(optionalString(data, "description", ""));
        preset.setItem(item);
        JsonArray members = optionalArray(data, "modifiers");
        if (members != null) {
            List<String> ids = new ArrayList<>();
            for (JsonElement member : members) {
                if (!member.isJsonPrimitive() || !member.getAsJsonPrimitive().isString()
                        || member.getAsString().isBlank()) {
                    throw new Invalid();
                }
                ids.add(member.getAsString());
            }
            preset.setModifiers(ids);
        }
        return preset;
    }

    private static void requireExecutorBehavior(String behavior) {
        if (!behavior.equals("PER_INVOKE") && !behavior.equals("PER_EXECUTOR")) {
            throw new Invalid();
        }
    }

    private static String requiredString(JsonObject data, String key) {
        JsonElement element = data.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new Invalid();
        }
        return element.getAsString();
    }

    private static String optionalString(JsonObject data, String key, String fallback) {
        JsonElement element = data.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new Invalid();
        }
        return element.getAsString();
    }

    private static boolean optionalBoolean(JsonObject data, String key, boolean fallback) {
        JsonElement element = data.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new Invalid();
        }
        return element.getAsBoolean();
    }

    private static Double optionalDouble(JsonObject data, String key,
            Double minimum, Double maximum, Double fallback) {
        JsonElement element = data.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new Invalid();
        }
        double value = element.getAsDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)
                || (minimum != null && value < minimum)
                || (maximum != null && value > maximum)) {
            throw new Invalid();
        }
        return value;
    }

    private static Integer optionalInteger(JsonObject data, String key,
            Integer minimum, Integer maximum, Integer fallback) {
        JsonElement element = data.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new Invalid();
        }
        double value = element.getAsDouble();
        if (value % 1.0 != 0.0 || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                || (minimum != null && value < minimum)
                || (maximum != null && value > maximum)) {
            throw new Invalid();
        }
        return (int) value;
    }

    private static Long optionalLong(JsonObject data, String key,
            Long minimum, Long maximum, Long fallback) {
        JsonElement element = data.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new Invalid();
        }
        double value = element.getAsDouble();
        if (value % 1.0 != 0.0 || value < Long.MIN_VALUE || value > Long.MAX_VALUE
                || (minimum != null && value < minimum)
                || (maximum != null && value > maximum)) {
            throw new Invalid();
        }
        return (long) value;
    }

    private static JsonObject optionalObject(JsonObject data, String key) {
        JsonElement element = data.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            throw new Invalid();
        }
        return element.getAsJsonObject();
    }

    private static JsonArray optionalArray(JsonObject data, String key) {
        JsonElement element = data.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonArray()) {
            throw new Invalid();
        }
        return element.getAsJsonArray();
    }

    private static String encode(String json) {
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        CRC32 checksum = new CRC32();
        checksum.update(raw);
        byte[] deflated = deflate(raw);
        boolean compressed = deflated.length < raw.length;
        byte[] body = compressed ? deflated : raw;
        byte[] framed = new byte[4 + body.length];
        int value = (int) checksum.getValue();
        framed[0] = (byte) (value >>> 24);
        framed[1] = (byte) (value >>> 16);
        framed[2] = (byte) (value >>> 8);
        framed[3] = (byte) value;
        System.arraycopy(body, 0, framed, 4, body.length);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(framed);
        return (compressed ? TAG_DEFLATED : TAG_RAW) + encoded;
    }

    private static Optional<String> decodeJson(String payload) {
        boolean compressed;
        if (payload.startsWith(TAG_DEFLATED)) {
            compressed = true;
        } else if (payload.startsWith(TAG_RAW)) {
            compressed = false;
        } else {
            return Optional.empty();
        }
        byte[] framed;
        try {
            framed = Base64.getUrlDecoder().decode(payload.substring(TAG_DEFLATED.length()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        if (framed.length < 4) {
            return Optional.empty();
        }
        int expected = ((framed[0] & 0xFF) << 24) | ((framed[1] & 0xFF) << 16)
                | ((framed[2] & 0xFF) << 8) | (framed[3] & 0xFF);
        byte[] body = Arrays.copyOfRange(framed, 4, framed.length);
        byte[] json;
        try {
            json = compressed ? inflateCapped(body) : body;
        } catch (DataFormatException exception) {
            return Optional.empty();
        }
        if (json.length > MAX_JSON_BYTES) {
            return Optional.empty();
        }
        CRC32 checksum = new CRC32();
        checksum.update(json);
        if ((int) checksum.getValue() != expected) {
            return Optional.empty();
        }
        return Optional.of(new String(json, StandardCharsets.UTF_8));
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length);
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /**
     * Inflates with the size cap enforced during inflation, so a
     * decompression bomb aborts before its full output exists.
     */
    private static byte[] inflateCapped(byte[] body) throws DataFormatException {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(body);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(body.length * 2, 8192));
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    if (inflater.needsInput()) {
                        break;
                    }
                    throw new DataFormatException("Invalid deflated payload");
                }
                out.write(buffer, 0, count);
                if (out.size() > MAX_JSON_BYTES) {
                    throw new DataFormatException("Payload exceeds size cap");
                }
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }

    /** Schema violation during decoding; always caught into empty. */
    private static final class Invalid extends RuntimeException {
        Invalid() {
            super(null, null, false, false);
        }
    }
}
