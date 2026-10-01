package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifierCreateArgsTest {
    private static final Set<String> KNOWN = Set.of("beef", "swords");

    private static ModifiersMessages texts() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "modifiers.create-usage", "usage tpl");
        ConfigPathMapper.set(config, "modifiers.create-unknown-flag", "flag tpl");
        ConfigPathMapper.set(config, "modifiers.create-missing-value", "missing tpl");
        ConfigPathMapper.set(config, "modifiers.create-unknown-trigger", "trigger tpl");
        ConfigPathMapper.set(config, "modifiers.create-unknown-member", "member tpl");
        ConfigPathMapper.set(config, "modifiers.create-bad-command", "command tpl");
        ConfigPathMapper.set(config, "modifiers.create-bad-item", "item tpl");
        ConfigPathMapper.set(config, "modifiers.create-deviation-range", "deviation tpl");
        ConfigPathMapper.set(config, "modifiers.create-bad-enum", "enum tpl");
        ConfigPathMapper.set(config, "modifiers.create-bad-number", "number tpl");
        return config.getModifiers();
    }

    @Test
    void missingOrBadTypeShowsUsage() {
        assertEquals("usage tpl", ModifierCreateArgs.parse(new String[]{}, KNOWN, texts()).messageTemplate());
        assertEquals("usage tpl",
                ModifierCreateArgs.parse(new String[]{"thing", "Foo"}, KNOWN, texts()).messageTemplate());
    }

    @Test
    void missingNameShowsUsage() {
        assertEquals("usage tpl",
                ModifierCreateArgs.parse(new String[]{"modifier"}, KNOWN, texts()).messageTemplate());
        assertEquals("usage tpl",
                ModifierCreateArgs.parse(new String[]{"modifier", "--chance", "0.5"}, KNOWN,
                        texts()).messageTemplate());
    }

    @Test
    void minimalModifierHasNoSections() {
        ModifierCreateArgs.Result result =
                ModifierCreateArgs.parse(new String[]{"modifier", "Beef", "Party"}, KNOWN, texts());
        assertTrue(result.success());
        assertEquals("Beef Party", result.plan().name());
        assertFalse(result.plan().preset());
        ModifierEntry entry = result.plan().toEntry("Bea");
        assertFalse(entry.isEnabled());
        assertEquals("Beef Party", entry.getMeta().getName());
        assertEquals("Bea", entry.getMeta().getAuthor());
        assertNull(entry.getBehavior());
    }

    @Test
    void unknownFlagFails() {
        ModifierCreateArgs.Result result =
                ModifierCreateArgs.parse(new String[]{"modifier", "Foo", "--bogus", "x"}, KNOWN, texts());
        assertFalse(result.success());
        assertEquals("flag tpl", result.messageTemplate());
        assertEquals("--bogus", result.params().get("flag"));
    }

    @Test
    void modifierFlagOnPresetFails() {
        ModifierCreateArgs.Result result =
                ModifierCreateArgs.parse(new String[]{"preset", "Foo", "--trigger", "ON_START"}, KNOWN, texts());
        assertFalse(result.success());
        assertEquals("flag tpl", result.messageTemplate());
    }

    @Test
    void missingValueFails() {
        ModifierCreateArgs.Result result =
                ModifierCreateArgs.parse(new String[]{"modifier", "Foo", "--chance"}, KNOWN, texts());
        assertFalse(result.success());
        assertEquals("missing tpl", result.messageTemplate());
    }

    @Test
    void fullModifierPlanBuildsEverySection() {
        String[] args = {"modifier", "Full", "--desc", "Does things", "--item", "cooked beef",
                "--author", "Me", "--trigger", "on_start", "--trigger", "INTERVAL",
                "--on-start", "pick_random", "--interval", "30", "--deviation", "5",
                "--interval-scope", "per_executor", "--chance", "0.5", "--chance-scope", "per_invoke",
                "--selection", "pick_random", "--pick-count", "2", "--pick-scope", "per_executor",
                "--delay", "100", "--console", "say hi <p>", "--player", "give <p> apple"};
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(args, KNOWN, texts());
        assertTrue(result.success());
        assertTrue(result.warnings().isEmpty());
        ModifierCreateArgs.Plan plan = result.plan();
        assertEquals("Does things", plan.description());
        assertEquals("COOKED_BEEF", plan.item());
        assertEquals("Me", plan.author());
        assertEquals(java.util.List.of("ON_START", "INTERVAL"), plan.triggers());
        assertEquals("PICK_RANDOM", plan.onStartOrder());
        assertEquals(30.0, plan.interval());
        assertEquals(5.0, plan.deviation());
        assertEquals("PER_EXECUTOR", plan.intervalBehavior());
        assertEquals(0.5, plan.chance());
        assertEquals("PER_INVOKE", plan.chanceBehavior());
        assertEquals("PICK_RANDOM", plan.selection());
        assertEquals(2, plan.pickCount());
        assertEquals("PER_EXECUTOR", plan.pickBehavior());
        assertEquals(100L, plan.delay());

        assertEntryMatchesPlan(plan.toEntry(plan.author()));
        assertEquals("Me", plan.toEntry(plan.author()).getMeta().getAuthor());
    }

    private static void assertEntryMatchesPlan(ModifierEntry entry) {
        assertNotNull(entry.getBehavior());
        assertEquals(java.util.List.of("ON_START", "INTERVAL"), entry.getBehavior().get("0").getRunsOn());
        assertEquals("PICK_RANDOM", entry.getBehavior().get("0").getOnStart().getPreStartOrder());
        assertEquals(30.0, entry.getBehavior().get("0").getOptions().getIntervalSettings().getInterval());
        assertEquals(5.0, entry.getBehavior().get("0").getOptions().getIntervalSettings().getDeviation());
        assertEquals("PER_EXECUTOR",
                entry.getBehavior().get("0").getOptions().getIntervalSettings().getBehavior());
        assertEquals(0.5, entry.getBehavior().get("0").getOptions().getSuccessChance().getChance());
        assertEquals("PER_INVOKE",
                entry.getBehavior().get("0").getOptions().getSuccessChance().getBehavior());
        assertEquals("PICK_RANDOM", entry.getBehavior().get("0").getOptions().getExecution().getSelection());
        assertEquals(2, entry.getBehavior().get("0").getOptions().getExecution().getPickRandom().getCount());
        assertEquals("PER_EXECUTOR",
                entry.getBehavior().get("0").getOptions().getExecution().getPickRandom().getBehavior());
        assertEquals(100L, entry.getBehavior().get("0").getOptions().getDelay());
        assertEquals(java.util.List.of("say hi <p>"),
                entry.getBehavior().get("0").getCommands().getLists().get("console"));
        assertEquals(java.util.List.of("give <p> apple"),
                entry.getBehavior().get("0").getCommands().getLists().get("player"));
    }

    @Test
    void badNumbersFail() {
        assertEquals("number tpl", failTemplate("modifier", "Foo", "--chance", "nope"));
        assertEquals("number tpl", failTemplate("modifier", "Foo", "--chance", "2"));
        assertEquals("number tpl", failTemplate("modifier", "Foo", "--interval", "-1"));
        assertEquals("number tpl", failTemplate("modifier", "Foo", "--pick-count", "0"));
        assertEquals("number tpl",
                failTemplate("modifier", "Foo", "--pick-count", "99999999999"));
        assertEquals("number tpl", failTemplate("modifier", "Foo", "--delay", "-5"));
    }

    @Test
    void badEnumsFail() {
        assertEquals("enum tpl", failTemplate("modifier", "Foo", "--selection", "maybe"));
        assertEquals("enum tpl", failTemplate("modifier", "Foo", "--chance-scope", "all"));
        assertEquals("enum tpl", failTemplate("modifier", "Foo", "--on-start", "now"));
    }

    @Test
    void unknownTriggerFails() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(
                new String[]{"modifier", "Foo", "--trigger", "ON_TUESDAY"}, KNOWN, texts());
        assertFalse(result.success());
        assertEquals("trigger tpl", result.messageTemplate());
        assertTrue(result.params().get("valid").contains("ON_START"));
    }

    @Test
    void unknownMemberFails() {
        ModifierCreateArgs.Result result =
                ModifierCreateArgs.parse(new String[]{"preset", "Foo", "--member", "nope"}, KNOWN, texts());
        assertFalse(result.success());
        assertEquals("member tpl", result.messageTemplate());
    }

    @Test
    void presetPlanBuildsMembers() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(
                new String[]{"preset", "Pack", "--desc", "Both", "--member", "beef", "--member", "swords"},
                KNOWN, texts());
        assertTrue(result.success());
        assertTrue(result.plan().preset());
        ModifierPreset preset = result.plan().toPreset("Bea");
        assertEquals("Pack", preset.getMeta().getName());
        assertEquals("Both", preset.getMeta().getDescription());
        assertEquals("Bea", preset.getMeta().getAuthor());
        assertEquals(java.util.List.of("beef", "swords"), preset.getModifiers());
    }

    @Test
    void presetAcceptsAuthorFlag() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(
                new String[] {"preset", "Pack", "--author", "Bea", "--member", "beef"}, KNOWN, texts());
        assertTrue(result.success());
        assertEquals("Bea", result.plan().author());
        assertEquals("Bea", result.plan().toPreset(result.plan().author()).getMeta().getAuthor());
    }

    @Test
    void triggersAndMembersDedupe() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(new String[]{"modifier", "Foo",
                "--trigger", "ON_START", "--trigger", "on_start"}, KNOWN, texts());
        assertTrue(result.success());
        assertEquals(java.util.List.of("ON_START"), result.plan().triggers());
    }

    @Test
    void badItemFails() {
        assertEquals("item tpl", failTemplate("modifier", "Foo", "--item", "not_a_mat"));
        assertEquals("item tpl", failTemplate("modifier", "Foo", "--item", "air"));
    }

    @Test
    void itemPrefixesNormalize() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(
                new String[]{"modifier", "Foo", "--item", "minecraft:stone"}, KNOWN, texts());
        assertTrue(result.success());
        assertEquals("STONE", result.plan().item());
    }

    @Test
    void badCommandFailsWithList() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(
                new String[]{"modifier", "Foo", "--player", "give <p apple"}, KNOWN, texts());
        assertFalse(result.success());
        assertEquals("command tpl", result.messageTemplate());
        assertEquals("player", result.params().get("list"));
    }

    @Test
    void commandWarningsPassThrough() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(
                new String[]{"modifier", "Foo", "--console", "say <bogus>"}, KNOWN, texts());
        assertTrue(result.success());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void definedCallsWarnNowhereInScope() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(new String[]{"modifier", "Foo",
                "--player", "say <fact:5>", "--hunter", "<def:fact,<fact:x>,x>"}, KNOWN, texts());
        assertTrue(result.success());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test
    void consoleDefsStayOutOfPlayerScope() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(new String[]{"modifier", "Foo",
                "--player", "say <fact:5>", "--console", "<def:fact,<fact:x>,x>"}, KNOWN, texts());
        assertTrue(result.success());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void greedyValuesKeepSpacesUntilNextFlag() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(new String[]{"modifier", "Foo",
                "--console", "say hello brave world", "--chance", "0.5"}, KNOWN, texts());
        assertTrue(result.success());
        assertEquals(java.util.List.of("say hello brave world"), result.plan().commands().get("console"));
        assertEquals(0.5, result.plan().chance());
    }

    @Test
    void repeatListFlagsAccumulate() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(new String[]{"modifier", "Foo",
                "--player", "give <p> apple", "--player", "give <p> bread"}, KNOWN, texts());
        assertTrue(result.success());
        assertEquals(java.util.List.of("give <p> apple", "give <p> bread"),
                result.plan().commands().get("player"));
    }

    @Test
    void deviationNeedsIntervalAndCap() {
        assertEquals("deviation tpl",
                failTemplate("modifier", "Foo", "--deviation", "5"));
        assertEquals("deviation tpl",
                failTemplate("modifier", "Foo", "--interval", "5", "--deviation", "9"));
    }

    @Test
    void flagsForListsVocabulary() {
        assertTrue(ModifierCreateArgs.flagsFor(false).contains("--pick-count"));
        assertTrue(ModifierCreateArgs.flagsFor(true).contains("--member"));
        assertFalse(ModifierCreateArgs.flagsFor(true).contains("--chance"));
    }

    private static String failTemplate(String... args) {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(args, KNOWN, texts());
        assertFalse(result.success());
        return result.messageTemplate();
    }
}
