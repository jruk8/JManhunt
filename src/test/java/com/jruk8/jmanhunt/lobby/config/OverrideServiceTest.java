package com.jruk8.jmanhunt.lobby.config;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Override storage, validation parity, effective resolution, pruning,
 * and modifier flags over bare configs. Map-backed and loaded
 * section-backed entries behave the same.
 */
class OverrideServiceTest {

    private static final String BOOL = "settings.match.autostart.enabled";
    private static final String INT = "settings.match.autostart.countdown-seconds";
    private static final String FLOAT = "match.end-delay";
    private static final String OPTION = "settings.match.game-leave.destination";
    private static final String LIST = "match.end-statistics";

    private ConfigService config;
    private LobbyConfig lobbies;
    private AtomicInteger saves;
    private OverrideService overrides;

    @BeforeEach
    void setUp() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ModifierStore store = new ModifierStore(new ModifiersConfig(), log);
        ModifierEntry beef = new ModifierEntry();
        beef.setEnabled(true);
        store.addModifier("beef", beef);
        ModifierEntry gapple = new ModifierEntry();
        gapple.setEnabled(false);
        store.addModifier("gapple", gapple);
        ModifierPreset chaos = new ModifierPreset();
        chaos.setModifiers(List.of("beef", "gapple"));
        store.addPreset("chaos", chaos);
        config = new ConfigService(new JManhuntConfig(), store);
        lobbies = new LobbyConfig();
        saves = new AtomicInteger();
        overrides = new OverrideService(config, lobbies, saves::incrementAndGet);
    }

    @Test
    void parseLobbyIdAcceptsNonNegativeIntegersOnly() {
        assertEquals(0, OverrideService.parseLobbyId("0").orElseThrow());
        assertEquals(12, OverrideService.parseLobbyId(" 12 ").orElseThrow());
        assertTrue(OverrideService.parseLobbyId("-1").isEmpty());
        assertTrue(OverrideService.parseLobbyId("GLOBAL").isEmpty());
        assertTrue(OverrideService.parseLobbyId("3x").isEmpty());
        assertTrue(OverrideService.parseLobbyId("").isEmpty());
        assertTrue(OverrideService.parseLobbyId(null).isEmpty());
    }

    @Test
    void setScalarStoresNestedCanonicalValues() {
        assertTrue(overrides.setSettingOverride(0, BOOL, "false").ok());
        assertTrue(overrides.setSettingOverride(0, INT, "30").ok());
        assertTrue(overrides.setSettingOverride(0, FLOAT, "12.5").ok());
        assertTrue(overrides.setSettingOverride(0, OPTION, "lobby").ok());

        assertEquals(Optional.of(false), overrides.rawOverride(0, BOOL));
        assertEquals(Optional.of(30), overrides.rawOverride(0, INT));
        assertEquals(Optional.of(12.5), overrides.rawOverride(0, FLOAT));
        assertEquals(Optional.of("LOBBY"), overrides.rawOverride(0, OPTION));
        assertEquals(4, saves.get());
    }

    @Test
    void setRejectsUnknownListAndBadValues() {
        assertEquals("manhunt.setting-invalid",
                overrides.setSettingOverride(0, "bogus.path", "1").errorKey());
        assertEquals("manhunt.setting-invalid",
                overrides.setSettingOverride(0, LIST, "a").errorKey());
        assertEquals("manhunt.setting-invalid-value",
                overrides.setSettingOverride(0, BOOL, "maybe").errorKey());
        assertEquals("manhunt.setting-out-of-range",
                overrides.setSettingOverride(0, INT, "-5").errorKey());
        assertEquals("manhunt.setting-invalid-option",
                overrides.setSettingOverride(0, OPTION, "moon").errorKey());
        assertEquals(0, saves.get());
    }

    @Test
    void unknownLobbyCreatedOnWrite() {
        assertTrue(overrides.setSettingOverride(9, BOOL, "true").ok());
        assertTrue(lobbies.getLobbies().containsKey("9"));
        assertEquals(Optional.of(true), overrides.rawOverride(9, BOOL));
    }

    @Test
    void effectiveReadsPreferOverrideElseGlobal() {
        overrides.setSettingOverride(0, BOOL, "false");
        overrides.setSettingOverride(0, INT, "30");
        overrides.setSettingOverride(0, FLOAT, "12.5");
        overrides.setSettingOverride(0, OPTION, "lobby");

        assertEquals(false, overrides.getBoolean(0, BOOL, true));
        assertEquals(30, overrides.getInt(0, INT, 1));
        assertEquals(12.5, overrides.getDouble(0, FLOAT, 1.0));
        assertEquals("LOBBY", overrides.getString(0, OPTION, "x"));
        assertEquals(config.getBoolean(BOOL, true),
                overrides.getBoolean(null, BOOL, true));
        assertEquals(config.getInt(INT, 1), overrides.getInt(7, INT, 1));
    }

    @Test
    void corruptOverrideFallsBackToGlobal() {
        Map<String, Object> settings = lobbies.getLobbies().get("0")
                .getOverrides().getSettings();
        settings.put("bogus-leaf", "not-a-map");
        overrides.setSettingOverride(0, BOOL, "false");
        @SuppressWarnings("unchecked")
        Map<String, Object> deep = (Map<String, Object>) settings.get("settings");
        deep.put("match", "corrupt");

        assertEquals(Optional.empty(), overrides.rawOverride(0, BOOL));
        assertEquals(config.getBoolean(BOOL, true),
                overrides.getBoolean(0, BOOL, true));
    }

    @Test
    void presenceHelpersCoverScalarsListsAndSections() {
        overrides.setSettingOverride(0, BOOL, "false");
        overrides.setListOverride(0, LIST, List.of("kills"));

        assertTrue(overrides.hasSettingOverride(0, BOOL));
        assertFalse(overrides.hasSettingOverride(0, INT));
        assertTrue(overrides.hasListOverride(0, LIST));
        assertFalse(overrides.hasListOverride(0, BOOL));
        assertTrue(overrides.hasOverridesBeneath(0, "settings.match.autostart"));
        assertTrue(overrides.hasOverridesBeneath(0, "settings"));
        assertTrue(overrides.hasOverridesBeneath(0, "match"));
        assertFalse(overrides.hasOverridesBeneath(0, "settings.compass"));
        assertFalse(overrides.hasOverridesBeneath(7, "settings"));
    }

    @Test
    void clearScalarPrunesEmptiedParents() {
        overrides.setSettingOverride(0, BOOL, "false");
        overrides.setSettingOverride(0, INT, "30");

        assertEquals(1, overrides.clearOverrides(0, BOOL));
        assertTrue(overrides.hasSettingOverride(0, INT));

        assertEquals(1, overrides.clearOverrides(0, INT));
        assertTrue(lobbies.getLobbies().get("0").getOverrides().getSettings().isEmpty());
        assertTrue(lobbies.getLobbies().containsKey("0"));
    }

    @Test
    void clearSubtreeCountsLeaves() {
        overrides.setSettingOverride(0, BOOL, "false");
        overrides.setSettingOverride(0, INT, "30");

        assertEquals(2, overrides.clearOverrides(0, "settings.match.autostart"));
        assertTrue(lobbies.getLobbies().get("0").getOverrides().getSettings().isEmpty());
    }

    @Test
    void clearMissingCountsZeroWithoutSaving() {
        assertEquals(0, overrides.clearOverrides(0, BOOL));
        assertEquals(0, overrides.clearOverrides(7, "settings"));
        assertEquals(0, saves.get());
    }

    @Test
    void clearLobbyRemovesHuskEntries() {
        overrides.setSettingOverride(9, BOOL, "true");
        overrides.setModifierOverride(9, "beef", false);

        assertEquals(2, overrides.clearLobby(9));
        assertFalse(lobbies.getLobbies().containsKey("9"));
        assertEquals(0, overrides.clearLobby(9));
    }

    @Test
    void modifierFlagsResolveEffective() {
        assertEquals(Optional.empty(), overrides.modifierOverride(0, "beef"));
        assertTrue(overrides.modifierEnabled(0, "beef"));
        assertFalse(overrides.presetEnabled(0, "chaos"));
        assertFalse(overrides.presetEnabled(null, "chaos"));

        assertTrue(overrides.setModifierOverride(0, "gapple", true));
        assertTrue(overrides.hasModifierOverride(0, "gapple"));
        assertTrue(overrides.modifierEnabled(0, "gapple"));
        assertTrue(overrides.presetEnabled(0, "chaos"));

        assertTrue(overrides.clearModifierOverride(0, "gapple"));
        assertFalse(overrides.presetEnabled(0, "chaos"));
        assertFalse(overrides.clearModifierOverride(0, "gapple"));
        assertFalse(overrides.setModifierOverride(0, "nope", true));
    }

    @Test
    void listOpsEditTheEffectiveList() {
        List<String> global = config.getStringList(LIST);

        ConfigService.SetOutcome added = overrides.listAddOverride(0, LIST, " wins ");
        assertTrue(added.ok());
        List<String> stored = overrides.getStringList(0, LIST);
        assertEquals(global.size() + 1, stored.size());
        assertEquals("wins", stored.get(stored.size() - 1));

        int last = stored.size() - 1;
        assertTrue(overrides.listSetOverride(0, LIST, last, "kills").ok());
        assertEquals("kills", overrides.getStringList(0, LIST).get(last));
        assertTrue(overrides.listSetOverride(0, LIST, 999, "x").errorKey()
                .equals("manhunt.setting-index-invalid"));

        assertTrue(overrides.listRemoveOverride(0, LIST, last).ok());
        assertEquals(global, overrides.getStringList(0, LIST));
        assertTrue(overrides.hasListOverride(0, LIST));

        assertEquals(1, overrides.clearOverrides(0, LIST));
        assertEquals(global, overrides.getStringList(0, LIST));
    }

    @Test
    void sectionBackedEntriesBehaveLikeMaps() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("settings.match.autostart.enabled", false);
        lobbies.getLobbies().get("0").getOverrides().setSettings(yaml.getValues(false));

        assertEquals(Optional.of(false), overrides.rawOverride(0, BOOL));
        assertTrue(overrides.hasOverridesBeneath(0, "settings.match"));
        assertEquals(false, overrides.getBoolean(0, BOOL, true));

        assertTrue(overrides.setSettingOverride(0, INT, "30").ok());
        assertEquals(Optional.of(30), overrides.rawOverride(0, INT));

        assertEquals(1, overrides.clearOverrides(0, BOOL));
        assertTrue(overrides.hasSettingOverride(0, INT));
        assertEquals(1, overrides.clearOverrides(0, "settings"));
        assertTrue(lobbies.getLobbies().get("0").getOverrides().getSettings().isEmpty());
    }

    @Test
    void overrideLobbyIdsListsHolders() {
        overrides.setSettingOverride(9, BOOL, "true");
        overrides.setModifierOverride(3, "beef", false);

        assertEquals(Set.of(9, 3), overrides.overrideLobbyIds());
    }
}
