package com.jruk8.jmanhunt.command;

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

    @Test
    void missingOrBadTypeShowsUsage() {
        assertEquals("modifiers.create-usage", ModifierCreateArgs.parse(new String[]{}, KNOWN).messageKey());
        assertEquals("modifiers.create-usage",
                ModifierCreateArgs.parse(new String[]{"thing", "Foo"}, KNOWN).messageKey());
    }

    @Test
    void missingNameShowsUsage() {
        assertEquals("modifiers.create-usage",
                ModifierCreateArgs.parse(new String[]{"modifier"}, KNOWN).messageKey());
        assertEquals("modifiers.create-usage",
                ModifierCreateArgs.parse(new String[]{"modifier", "--chance", "0.5"}, KNOWN).messageKey());
    }

    @Test
    void minimalModifierHasNoSections() {
        ModifierCreateArgs.Result result =
                ModifierCreateArgs.parse(new String[]{"modifier", "Beef", "Party"}, KNOWN);
        assertTrue(result.success());
        assertEquals("Beef Party", result.plan().name());
        assertFalse(result.plan().preset());
        ModifierEntry entry = result.plan().toEntry();
        assertFalse(entry.isEnabled());
        assertEquals("Beef Party", entry.getMeta().getName());
        assertNull(entry.getBehavior());
    }

    @Test
    void unknownFlagFails() {
        ModifierCreateArgs.Result result =
                ModifierCreateArgs.parse(new String[]{"modifier", "Foo", "--bogus", "x"}, KNOWN);
        assertFalse(result.success());
        assertEquals("modifiers.create-unknown-flag", result.messageKey());
        assertEquals("--bogus", result.params().get("flag"));
    }

    @Test
    void modifierFlagOnPresetFails() {
        ModifierCreateArgs.Result result =
                ModifierCreateArgs.parse(new String[]{"preset", "Foo", "--trigger", "ON_START"}, KNOWN);
        assertFalse(result.success());
        assertEquals("modifiers.create-unknown-flag", result.messageKey());
    }

    @Test
    void missingValueFails() {
        ModifierCreateArgs.Result result =
                ModifierCreateArgs.parse(new String[]{"modifier", "Foo", "--chance"}, KNOWN);
        assertFalse(result.success());
        assertEquals("modifiers.create-missing-value", result.messageKey());
    }

    @Test
    void fullModifierPlanBuildsEverySection() {
        String[] args = {"modifier", "Full", "--desc", "Does things", "--item", "cooked beef",
                "--author", "Me", "--trigger", "on_start", "--trigger", "INTERVAL",
                "--on-start", "pick_random", "--interval", "30", "--deviation", "5",
                "--interval-scope", "per_executor", "--chance", "0.5", "--chance-scope", "per_invoke",
                "--selection", "pick_random", "--pick-count", "2", "--pick-scope", "per_executor",
                "--delay", "100", "--console", "say hi <p>", "--player", "give <p> apple"};
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(args, KNOWN);
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

        assertEntryMatchesPlan(plan.toEntry());
    }

    private static void assertEntryMatchesPlan(ModifierEntry entry) {
        assertNotNull(entry.getBehavior());
        assertEquals(java.util.List.of("ON_START", "INTERVAL"), entry.getBehavior().getRunsOn());
        assertEquals("PICK_RANDOM", entry.getBehavior().getOnStart().getPreStartOrder());
        assertEquals(30.0, entry.getBehavior().getOptions().getIntervalSettings().getInterval());
        assertEquals(5.0, entry.getBehavior().getOptions().getIntervalSettings().getDeviation());
        assertEquals("PER_EXECUTOR",
                entry.getBehavior().getOptions().getIntervalSettings().getBehavior());
        assertEquals(0.5, entry.getBehavior().getOptions().getSuccessChance().getChance());
        assertEquals("PER_INVOKE",
                entry.getBehavior().getOptions().getSuccessChance().getBehavior());
        assertEquals("PICK_RANDOM", entry.getBehavior().getOptions().getExecution().getSelection());
        assertEquals(2, entry.getBehavior().getOptions().getExecution().getPickRandom().getCount());
        assertEquals("PER_EXECUTOR",
                entry.getBehavior().getOptions().getExecution().getPickRandom().getBehavior());
        assertEquals(100L, entry.getBehavior().getOptions().getDelay());
        assertEquals(java.util.List.of("say hi <p>"),
                entry.getBehavior().getCommands().getLists().get("console"));
        assertEquals(java.util.List.of("give <p> apple"),
                entry.getBehavior().getCommands().getLists().get("player"));
    }

    @Test
    void badNumbersFail() {
        assertEquals("modifiers.create-bad-number", failKey("modifier", "Foo", "--chance", "nope"));
        assertEquals("modifiers.create-bad-number", failKey("modifier", "Foo", "--chance", "2"));
        assertEquals("modifiers.create-bad-number", failKey("modifier", "Foo", "--interval", "-1"));
        assertEquals("modifiers.create-bad-number", failKey("modifier", "Foo", "--pick-count", "0"));
        assertEquals("modifiers.create-bad-number",
                failKey("modifier", "Foo", "--pick-count", "99999999999"));
        assertEquals("modifiers.create-bad-number", failKey("modifier", "Foo", "--delay", "-5"));
    }

    @Test
    void badEnumsFail() {
        assertEquals("modifiers.create-bad-enum", failKey("modifier", "Foo", "--selection", "maybe"));
        assertEquals("modifiers.create-bad-enum", failKey("modifier", "Foo", "--chance-scope", "all"));
        assertEquals("modifiers.create-bad-enum", failKey("modifier", "Foo", "--on-start", "now"));
    }

    @Test
    void unknownTriggerFails() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(
                new String[]{"modifier", "Foo", "--trigger", "ON_TUESDAY"}, KNOWN);
        assertFalse(result.success());
        assertEquals("modifiers.create-unknown-trigger", result.messageKey());
        assertTrue(result.params().get("valid").contains("ON_START"));
    }

    @Test
    void unknownMemberFails() {
        ModifierCreateArgs.Result result =
                ModifierCreateArgs.parse(new String[]{"preset", "Foo", "--member", "nope"}, KNOWN);
        assertFalse(result.success());
        assertEquals("modifiers.create-unknown-member", result.messageKey());
    }

    @Test
    void presetPlanBuildsMembers() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(
                new String[]{"preset", "Pack", "--desc", "Both", "--member", "beef", "--member", "swords"},
                KNOWN);
        assertTrue(result.success());
        assertTrue(result.plan().preset());
        ModifierPreset preset = result.plan().toPreset();
        assertEquals("Pack", preset.getMeta().getName());
        assertEquals("Both", preset.getMeta().getDescription());
        assertEquals(java.util.List.of("beef", "swords"), preset.getModifiers());
    }

    @Test
    void triggersAndMembersDedupe() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(new String[]{"modifier", "Foo",
                "--trigger", "ON_START", "--trigger", "on_start"}, KNOWN);
        assertTrue(result.success());
        assertEquals(java.util.List.of("ON_START"), result.plan().triggers());
    }

    @Test
    void badItemFails() {
        assertEquals("modifiers.create-bad-item", failKey("modifier", "Foo", "--item", "not_a_mat"));
        assertEquals("modifiers.create-bad-item", failKey("modifier", "Foo", "--item", "air"));
    }

    @Test
    void itemPrefixesNormalize() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(
                new String[]{"modifier", "Foo", "--item", "minecraft:stone"}, KNOWN);
        assertTrue(result.success());
        assertEquals("STONE", result.plan().item());
    }

    @Test
    void badCommandFailsWithList() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(
                new String[]{"modifier", "Foo", "--player", "give <p apple"}, KNOWN);
        assertFalse(result.success());
        assertEquals("modifiers.create-bad-command", result.messageKey());
        assertEquals("player", result.params().get("list"));
    }

    @Test
    void commandWarningsPassThrough() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(
                new String[]{"modifier", "Foo", "--console", "say <bogus>"}, KNOWN);
        assertTrue(result.success());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void greedyValuesKeepSpacesUntilNextFlag() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(new String[]{"modifier", "Foo",
                "--console", "say hello brave world", "--chance", "0.5"}, KNOWN);
        assertTrue(result.success());
        assertEquals(java.util.List.of("say hello brave world"), result.plan().commands().get("console"));
        assertEquals(0.5, result.plan().chance());
    }

    @Test
    void repeatListFlagsAccumulate() {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(new String[]{"modifier", "Foo",
                "--player", "give <p> apple", "--player", "give <p> bread"}, KNOWN);
        assertTrue(result.success());
        assertEquals(java.util.List.of("give <p> apple", "give <p> bread"),
                result.plan().commands().get("player"));
    }

    @Test
    void deviationNeedsIntervalAndCap() {
        assertEquals("modifiers.create-deviation-range",
                failKey("modifier", "Foo", "--deviation", "5"));
        assertEquals("modifiers.create-deviation-range",
                failKey("modifier", "Foo", "--interval", "5", "--deviation", "9"));
    }

    @Test
    void flagsForListsVocabulary() {
        assertTrue(ModifierCreateArgs.flagsFor(false).contains("--pick-count"));
        assertTrue(ModifierCreateArgs.flagsFor(true).contains("--member"));
        assertFalse(ModifierCreateArgs.flagsFor(true).contains("--chance"));
    }

    private static String failKey(String... args) {
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(args, KNOWN);
        assertFalse(result.success());
        return result.messageKey();
    }
}
