package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.List;

/** Power-user toggles that rarely need changing. */
@SuppressWarnings("FieldMayBeFinal")
public class MiscConfig extends OkaeriConfig {

    @Comment("Advanced server interop toggles.")
    private Interop interop = new Interop();

    public Interop getInterop() {
        return interop;
    }

    public void setInterop(Interop interop) {
        this.interop = interop;
    }

    @CustomKey("modifier-editor")
    @Comment("Modifier editor toggles.")
    private ModifierEditor modifierEditor = new ModifierEditor();

    public ModifierEditor getModifierEditor() {
        return modifierEditor;
    }

    public void setModifierEditor(ModifierEditor modifierEditor) {
        this.modifierEditor = modifierEditor;
    }

    /** Advanced server interop toggles. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Interop extends OkaeriConfig {

        @CustomKey("disable-worldedit-navwand")
        @Comment({
                "When true, compass clicks never trigger WorldEdit's navwand",
                "teleport. The navwand is a conflicting feature with the compass",
                "affecting OP'd users. This option fixes it.",
                "Purely passive, requires no dependency. If no WorldEdit is installed,",
                "feel free to leave off.",
                "Note: this also blocks breaking blocks with a compass in hand.",
                "To avoid that, configure the WorldEdit plugin navwand item to",
                "something other than the compass and turn this setting off.",
                "",
                "Performance impact: none",
                "Default: true"
        })
        private boolean disableWorldeditNavwand = true;

        public boolean isDisableWorldeditNavwand() {
            return disableWorldeditNavwand;
        }

        public void setDisableWorldeditNavwand(boolean disableWorldeditNavwand) {
            this.disableWorldeditNavwand = disableWorldeditNavwand;
        }

        @CustomKey("blacklisted-modifier-commands")
        @Comment({
                "Command roots modifiers may never dispatch, matched against the",
                "first token with slashes and namespace prefixes stripped,",
                "case-insensitively. A hit logs an error and aborts the rest",
                "of the command list; the match keeps running.",
                "Default: op, deop, stop, restart, reload, luckperms, lp,",
                "permissions, ban, kick, whitelist"
        })
        private List<String> blacklistedModifierCommands = new ArrayList<>(List.of("op", "deop",
                "stop", "restart", "reload", "luckperms", "lp", "permissions", "ban", "kick",
                "whitelist"));

        public List<String> getBlacklistedModifierCommands() {
            return blacklistedModifierCommands;
        }

        public void setBlacklistedModifierCommands(List<String> blacklistedModifierCommands) {
            this.blacklistedModifierCommands = blacklistedModifierCommands;
        }
    }

    /** Modifier editor toggles. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class ModifierEditor extends OkaeriConfig {

        @CustomKey("validate-commands")
        @Comment({
                "When true, the modifier editor rejects command lines with",
                "unknown root commands or unknown give items. Placeholder",
                "checks always run either way.",
                "Default: true"
        })
        private boolean validateCommands = true;

        public boolean isValidateCommands() {
            return validateCommands;
        }

        public void setValidateCommands(boolean validateCommands) {
            this.validateCommands = validateCommands;
        }

        @CustomKey("remember-gui-commands")
        @Comment({
                "When true, the Test-a-Command dialog starts with its",
                "remember checkbox ticked for players with no memory yet.",
                "Ticked players keep their last role and commands.",
                "Default: false"
        })
        private boolean rememberGuiCommands = false;

        public boolean isRememberGuiCommands() {
            return rememberGuiCommands;
        }

        public void setRememberGuiCommands(boolean rememberGuiCommands) {
            this.rememberGuiCommands = rememberGuiCommands;
        }
    }

}
