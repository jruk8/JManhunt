package com.jruk8.jmanhunt.modifiers;

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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Share-string codec: exact bytes out, strict schema back in. */
class ModifierCodecTest {

    @Test
    void tinyModifierEncodesWithExactJson() throws Exception {
        ModifierEntry entry = new ModifierEntry();
        entry.setEnabled(false);
        ModifierMeta meta = new ModifierMeta();
        meta.setName("Beef");
        entry.setMeta(meta);

        String payload = ModifierCodec.exportModifier("beef", entry);

        assertEquals("{\"type\":\"modifier\",\"id\":\"beef\",\"data\":{\"enabled\":false,"
                + "\"meta\":{\"name\":\"Beef\",\"item\":\"STONE\"}}}",
                framedJson(payload));
        assertChecksumMatches(payload);
    }

    @Test
    void rawEnvelopeDecodes() {
        String json = "{\"type\":\"preset\",\"id\":\"pack\",\"data\":{\"name\":\"Pack\","
                + "\"item\":\"CHEST\",\"modifiers\":[\"a\"]}}";

        Optional<ModifierCodec.Imported> decoded = ModifierCodec.decode(envelope(json));

        assertTrue(decoded.isPresent());
        assertEquals(ModifierCodec.Kind.PRESET, decoded.get().kind());
        assertEquals("pack", decoded.get().id());
        assertEquals("Pack", decoded.get().preset().getName());
    }

    @Test
    void fullModifierRoundTripsExactly() {
        ModifierEntry entry = fullEntry();

        String payload = ModifierCodec.exportModifier("gear-dice", entry);
        Optional<ModifierCodec.Imported> decoded = ModifierCodec.decode(payload);

        assertTrue(decoded.isPresent());
        assertEquals(ModifierCodec.Kind.MODIFIER, decoded.get().kind());
        assertEquals("gear-dice", decoded.get().id());
        ModifierEntry back = decoded.get().entry();
        assertTrue(back.isEnabled());
        assertEquals("Gear Dice", back.getMeta().getName());
        assertEquals("Roll for gear", back.getMeta().getDescription());
        assertEquals("TNT", back.getMeta().getItem());
        assertEquals("jruk", back.getMeta().getAuthor());
        assertEquals(List.of("INTERVAL", "ON_START"), back.getBehavior().getRunsOn());
        assertEquals("AFTER", back.getBehavior().getOnStart().getPreStartOrder());
        assertEquals(15.0, back.getBehavior().getOptions().getIntervalSettings().getInterval());
        assertEquals(5.0, back.getBehavior().getOptions().getIntervalSettings().getDeviation());
        assertEquals("PER_EXECUTOR",
                back.getBehavior().getOptions().getIntervalSettings().getBehavior());
        assertEquals(0.5, back.getBehavior().getOptions().getSuccessChance().getChance());
        assertEquals("PICK_RANDOM", back.getBehavior().getOptions().getExecution().getSelection());
        assertEquals(2, back.getBehavior().getOptions().getExecution().getPickRandom().getCount());
        assertEquals(100L, back.getBehavior().getOptions().getDelay());
        assertEquals(List.of("give <p> beef 8", "say hi"),
                back.getBehavior().getCommands().getLists().get("player"));
        assertEquals(List.of("say custom"),
                back.getBehavior().getCommands().getLists().get("custom-list"));
        assertEquals(payload, ModifierCodec.exportModifier(decoded.get().id(), back));
    }

    @Test
    void presetRoundTripsExactly() {
        ModifierPreset preset = new ModifierPreset();
        preset.setName("Chaos");
        preset.setDescription("Everything on");
        preset.setAuthor("JManhunt");
        preset.setItem("TNT");
        preset.setModifiers(List.of("gear-dice", "beef"));

        String payload = ModifierCodec.exportPreset("chaos", preset);
        Optional<ModifierCodec.Imported> decoded = ModifierCodec.decode(payload);

        assertTrue(decoded.isPresent());
        assertEquals(ModifierCodec.Kind.PRESET, decoded.get().kind());
        assertEquals("chaos", decoded.get().id());
        assertEquals("Chaos", decoded.get().preset().getName());
        assertEquals("JManhunt", decoded.get().preset().getAuthor());
        assertEquals(List.of("gear-dice", "beef"), decoded.get().preset().getModifiers());
        assertEquals(payload, ModifierCodec.exportPreset(decoded.get().id(), decoded.get().preset()));
    }

    @Test
    void thinModifierImportsWithDefaults() {
        String payload = ModifierCodec.exportModifier("thin", new ModifierEntry());

        Optional<ModifierCodec.Imported> decoded = ModifierCodec.decode(payload);

        assertTrue(decoded.isPresent());
        assertEquals(ModifierStore.DEFAULT_NAME, decoded.get().entry().getMeta().getName());
    }

    @Test
    void largePayloadUsesDeflate() {
        ModifierEntry entry = new ModifierEntry();
        ModifierMeta meta = new ModifierMeta();
        meta.setName("Big");
        entry.setMeta(meta);
        ModifierBehavior behavior = new ModifierBehavior();
        ModifierCommands commands = new ModifierCommands();
        commands.getLists().put("player",
                List.of("say " + "x".repeat(2000), "say " + "y".repeat(2000)));
        behavior.setCommands(commands);
        entry.setBehavior(behavior);

        String payload = ModifierCodec.exportModifier("big", entry);

        assertTrue(payload.startsWith(ModifierCodec.TAG_DEFLATED));
        assertTrue(ModifierCodec.decode(payload).isPresent());
    }

    @Test
    void corruptedPayloadsDecodeEmpty() {
        String payload = ModifierCodec.exportModifier("beef", namedEntry("Beef"));

        assertTrue(ModifierCodec.decode(null).isEmpty());
        assertTrue(ModifierCodec.decode("").isEmpty());
        assertTrue(ModifierCodec.decode("JMH1X:" + payload.substring(6)).isEmpty());
        assertTrue(ModifierCodec.decode(payload.substring(0, payload.length() - 4)).isEmpty());
        assertTrue(ModifierCodec.decode("JMH1R:!!!not-base64!!!").isEmpty());
        assertTrue(ModifierCodec.decode(flip(payload, 6)).isEmpty());
        assertTrue(ModifierCodec.decode(flip(payload, 10)).isEmpty());
        assertTrue(ModifierCodec.decode(flip(payload, payload.length() - 5)).isEmpty());
    }

    @Test
    void oversizedPayloadDecodesEmpty() {
        ModifierEntry entry = namedEntry("Huge");
        ModifierBehavior behavior = new ModifierBehavior();
        ModifierCommands commands = new ModifierCommands();
        commands.getLists().put("player", List.of("say " + "z".repeat(1_200_000)));
        behavior.setCommands(commands);
        entry.setBehavior(behavior);

        String payload = ModifierCodec.exportModifier("huge", entry);

        assertTrue(payload.startsWith(ModifierCodec.TAG_DEFLATED));
        assertTrue(ModifierCodec.decode(payload).isEmpty());
    }

    @Test
    void offSchemaPayloadsDecodeEmpty() {
        assertTrue(ModifierCodec.decode(envelope("{\"type\":\"mod\",\"id\":\"a\",\"data\":{}}")).isEmpty());
        assertTrue(ModifierCodec.decode(envelope("{\"type\":\"modifier\",\"id\":\"has space\",\"data\":{}}"))
                .isEmpty());
        assertTrue(ModifierCodec.decode(envelope("{\"type\":\"modifier\",\"id\":\"a\"}")).isEmpty());
        assertTrue(ModifierCodec.decode(envelope("not json")).isEmpty());
        assertTrue(ModifierCodec.decode(envelope("[1,2]")).isEmpty());

        String noMeta = "{\"type\":\"modifier\",\"id\":\"a\",\"data\":{\"enabled\":false}}";
        assertTrue(ModifierCodec.decode(envelope(noMeta)).isEmpty());

        String blankName = "{\"type\":\"modifier\",\"id\":\"a\","
                + "\"data\":{\"meta\":{\"name\":\"  \",\"item\":\"STONE\"}}}";
        assertTrue(ModifierCodec.decode(envelope(blankName)).isEmpty());

        String badItem = "{\"type\":\"modifier\",\"id\":\"a\","
                + "\"data\":{\"meta\":{\"name\":\"A\",\"item\":\"NOPE\"}}}";
        assertTrue(ModifierCodec.decode(envelope(badItem)).isEmpty());

        String badTrigger = "{\"type\":\"modifier\",\"id\":\"a\",\"data\":{\"meta\":{\"name\":\"A\","
                + "\"item\":\"STONE\"},\"behavior\":{\"runs-on\":[\"nope\"]}}}";
        assertTrue(ModifierCodec.decode(envelope(badTrigger)).isEmpty());

        String badChance = "{\"type\":\"modifier\",\"id\":\"a\",\"data\":{\"meta\":{\"name\":\"A\","
                + "\"item\":\"STONE\"},\"behavior\":{\"options\":{\"success-chance\":{\"chance\":2.0}}}}}";
        assertTrue(ModifierCodec.decode(envelope(badChance)).isEmpty());

        String badSelection = "{\"type\":\"modifier\",\"id\":\"a\",\"data\":{\"meta\":{\"name\":\"A\","
                + "\"item\":\"STONE\"},\"behavior\":{\"options\":{\"execution\":{\"selection\":\"MAYBE\"}}}}}";
        assertTrue(ModifierCodec.decode(envelope(badSelection)).isEmpty());

        String badCount = "{\"type\":\"modifier\",\"id\":\"a\",\"data\":{\"meta\":{\"name\":\"A\","
                + "\"item\":\"STONE\"},\"behavior\":{\"options\":{\"execution\":"
                + "{\"pick-random\":{\"count\":0}}}}}";
        assertTrue(ModifierCodec.decode(envelope(badCount)).isEmpty());

        String badDelay = "{\"type\":\"modifier\",\"id\":\"a\",\"data\":{\"meta\":{\"name\":\"A\","
                + "\"item\":\"STONE\"},\"behavior\":{\"options\":{\"delay\":-5}}}";
        assertTrue(ModifierCodec.decode(envelope(badDelay)).isEmpty());

        String badPreset = "{\"type\":\"preset\",\"id\":\"a\",\"data\":{\"name\":\"  \"}}";
        assertTrue(ModifierCodec.decode(envelope(badPreset)).isEmpty());
    }

    @Test
    void oversizedRawPayloadDecodesEmpty() {
        String json = "{\"type\":\"modifier\",\"id\":\"a\",\"data\":{\"meta\":{\"name\":\"A\","
                + "\"item\":\"STONE\",\"description\":\"" + "q".repeat(1_200_000) + "\"}}}";

        assertTrue(ModifierCodec.decode(envelope(json)).isEmpty());
    }

    @Test
    void decodeTrimsSurroundingWhitespace() {
        String payload = ModifierCodec.exportModifier("beef", namedEntry("Beef"));

        assertTrue(ModifierCodec.decode("  " + payload + "\n").isPresent());
    }

    private static ModifierEntry namedEntry(String name) {
        ModifierEntry entry = new ModifierEntry();
        ModifierMeta meta = new ModifierMeta();
        meta.setName(name);
        entry.setMeta(meta);
        return entry;
    }

    private static ModifierEntry fullEntry() {
        ModifierEntry entry = new ModifierEntry();
        entry.setEnabled(true);
        ModifierMeta meta = new ModifierMeta();
        meta.setName("Gear Dice");
        meta.setDescription("Roll for gear");
        meta.setItem("TNT");
        meta.setAuthor("jruk");
        entry.setMeta(meta);
        ModifierBehavior behavior = new ModifierBehavior();
        behavior.setRunsOn(List.of("INTERVAL", "ON_START"));
        ModifierOnStart onStart = new ModifierOnStart();
        onStart.setPreStartOrder("AFTER");
        behavior.setOnStart(onStart);
        ModifierOptions options = new ModifierOptions();
        ModifierInterval interval = new ModifierInterval();
        interval.setInterval(15.0);
        interval.setDeviation(5.0);
        interval.setBehavior("PER_EXECUTOR");
        options.setIntervalSettings(interval);
        ModifierChance chance = new ModifierChance();
        chance.setChance(0.5);
        options.setSuccessChance(chance);
        ModifierExecution execution = new ModifierExecution();
        execution.setSelection("PICK_RANDOM");
        ModifierPickRandom pick = new ModifierPickRandom();
        pick.setCount(2);
        execution.setPickRandom(pick);
        options.setExecution(execution);
        options.setDelay(100L);
        behavior.setOptions(options);
        ModifierCommands commands = new ModifierCommands();
        commands.getLists().put("player", List.of("give <p> beef 8", "say hi"));
        commands.getLists().put("custom-list", List.of("say custom"));
        behavior.setCommands(commands);
        entry.setBehavior(behavior);
        return entry;
    }

    private static String flip(String payload, int index) {
        char replacement = payload.charAt(index) == 'A' ? 'B' : 'A';
        return payload.substring(0, index) + replacement + payload.substring(index + 1);
    }

    /** Wraps hand-built JSON in a raw envelope for negative tests. */
    private static String envelope(String json) {
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        CRC32 checksum = new CRC32();
        checksum.update(raw);
        int value = (int) checksum.getValue();
        byte[] framed = new byte[4 + raw.length];
        framed[0] = (byte) (value >>> 24);
        framed[1] = (byte) (value >>> 16);
        framed[2] = (byte) (value >>> 8);
        framed[3] = (byte) value;
        System.arraycopy(raw, 0, framed, 4, raw.length);
        return ModifierCodec.TAG_RAW
                + Base64.getUrlEncoder().withoutPadding().encodeToString(framed);
    }

    private static String framedJson(String payload) throws Exception {
        return new String(unframed(payload), StandardCharsets.UTF_8);
    }

    private static void assertChecksumMatches(String payload) throws Exception {
        String tag = payload.startsWith(ModifierCodec.TAG_DEFLATED)
                ? ModifierCodec.TAG_DEFLATED : ModifierCodec.TAG_RAW;
        byte[] framed = Base64.getUrlDecoder().decode(payload.substring(tag.length()));
        int expected = ((framed[0] & 0xFF) << 24) | ((framed[1] & 0xFF) << 16)
                | ((framed[2] & 0xFF) << 8) | (framed[3] & 0xFF);
        CRC32 checksum = new CRC32();
        checksum.update(unframed(payload));
        assertEquals(expected, (int) checksum.getValue());
    }

    private static byte[] unframed(String payload) throws Exception {
        boolean compressed = payload.startsWith(ModifierCodec.TAG_DEFLATED);
        String tag = compressed ? ModifierCodec.TAG_DEFLATED : ModifierCodec.TAG_RAW;
        assertTrue(payload.startsWith(tag));
        byte[] framed = Base64.getUrlDecoder().decode(payload.substring(tag.length()));
        byte[] body = new byte[framed.length - 4];
        System.arraycopy(framed, 4, body, 0, body.length);
        if (!compressed) {
            return body;
        }
        java.util.zip.Inflater inflater = new java.util.zip.Inflater(true);
        try {
            inflater.setInput(body);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    break;
                }
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }

    @Test
    void validIdMatchesCodecShape() {
        assertTrue(ModifierCodec.validId("good-id_1"));
        assertTrue(ModifierCodec.validId("a"));
        assertFalse(ModifierCodec.validId("-lead"));
        assertFalse(ModifierCodec.validId("has space"));
        assertFalse(ModifierCodec.validId(null));
    }
}
