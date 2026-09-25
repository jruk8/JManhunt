package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** Modifiers GUI labels. */
@SuppressWarnings("FieldMayBeFinal")
public class ModifiersGuiMessages extends OkaeriConfig {

    @CustomKey("title-main")
    private String titleMain = "Modifiers";

    @CustomKey("title-modifiers")
    private String titleModifiers = "Modifiers";

    @CustomKey("title-presets")
    private String titlePresets = "Presets";

    @CustomKey("to-modifiers")
    private String toModifiers = "Modifiers";

    @CustomKey("to-modifiers-lore")
    private String toModifiersLore = "{enabled}/{total} enabled";

    @CustomKey("to-presets")
    private String toPresets = "Presets";

    @CustomKey("to-presets-lore")
    private String toPresetsLore = "{enabled}/{total} enabled";

    @CustomKey("scroll-up")
    private String scrollUp = "Scroll up";

    @CustomKey("scroll-down")
    private String scrollDown = "Scroll down";

    @CustomKey("back")
    private String back = "Back";

    @CustomKey("toggle-all")
    private String toggleAll = "Toggle all";

    @CustomKey("toggle-all-modifiers-lore")
    private String toggleAllModifiersLore = "{total} modifiers";

    @CustomKey("toggle-all-presets-lore")
    private String toggleAllPresetsLore = "{total} presets";

    @CustomKey("preset-more")
    private String presetMore = "and {count} more";

    @CustomKey("state-on")
    private String stateOn = "<green>Enabled";

    @CustomKey("state-off")
    private String stateOff = "<red>Disabled";

    @CustomKey("import-modifier")
    private String importModifier = "Import Modifier";

    @CustomKey("import-modifier-lore")
    private String importModifierLore = "Get modifiers by exporting them or\n" +
            "community modifiers through our Discord";

    @CustomKey("import-preset")
    private String importPreset = "Import Preset";

    @CustomKey("import-preset-lore")
    private String importPresetLore = "Get presets by exporting them or\n" +
            "community presets through our Discord";

    @CustomKey("import-modifier-title")
    private String importModifierTitle = "Import Modifier";

    @CustomKey("import-preset-title")
    private String importPresetTitle = "Import Preset";

    @CustomKey("import-prompt")
    private String importPrompt = "Paste an exported string.";

    @CustomKey("create-modifier")
    private String createModifier = "Create Modifier";

    @CustomKey("create-modifier-lore")
    private String createModifierLore = "Start a new modifier";

    @CustomKey("create-preset")
    private String createPreset = "Create Preset";

    @CustomKey("create-preset-lore")
    private String createPresetLore = "Start a new preset";

    @CustomKey("create-name-title")
    private String createNameTitle = "Name your modifier";

    @CustomKey("create-preset-name-title")
    private String createPresetNameTitle = "Name your preset";

    @CustomKey("create-name-prompt")
    private String createNamePrompt = "Type a display name.";

    @CustomKey("edit-hint")
    private String editHint = "Right-click to edit";

    @CustomKey("editor-title-modifier")
    private String editorTitleModifier = "Edit Modifier";

    @CustomKey("editor-title-preset")
    private String editorTitlePreset = "Edit Preset";

    @CustomKey("editor-current")
    private String editorCurrent = "Current: {value}";

    @CustomKey("editor-unset")
    private String editorUnset = "Not set";

    @CustomKey("editor-click-edit")
    private String editorClickEdit = "Click to edit";

    @CustomKey("editor-click-toggle")
    private String editorClickToggle = "Click to toggle";

    @CustomKey("editor-click-cycle")
    private String editorClickCycle = "Click to change";

    @CustomKey("editor-click-open")
    private String editorClickOpen = "Click to open";

    @CustomKey("editor-click-copy")
    private String editorClickCopy = "Click to copy";

    @CustomKey("editor-click-delete")
    private String editorClickDelete = "Click to delete";

    @CustomKey("editor-prompt-title")
    private String editorPromptTitle = "Edit {label}";

    @CustomKey("editor-prompt-current")
    private String editorPromptCurrent = "Current value: {value}";

    @CustomKey("editor-commands")
    private String editorCommands = "Commands";

    @CustomKey("editor-commands-lore")
    private String editorCommandsLore = "{total} lines";

    @CustomKey("editor-export")
    private String editorExport = "Export";

    @CustomKey("editor-export-lore")
    private String editorExportLore = "Copy a share string";

    @CustomKey("editor-rename")
    private String editorRename = "Rename Id";

    @CustomKey("editor-rename-lore")
    private String editorRenameLore = "Current id: {value}";

    @CustomKey("editor-rename-title")
    private String editorRenameTitle = "Rename Id";

    @CustomKey("editor-rename-prompt")
    private String editorRenamePrompt = "Type the new id.";

    @CustomKey("editor-delete")
    private String editorDelete = "Delete";

    @CustomKey("editor-delete-lore")
    private String editorDeleteLore = "Removes this modifier forever";

    @CustomKey("editor-delete-preset-lore")
    private String editorDeletePresetLore = "Removes this preset forever";

    @CustomKey("editor-delete-title")
    private String editorDeleteTitle = "Delete {name}?";

    @CustomKey("editor-delete-confirm")
    private String editorDeleteConfirm = "This cannot be undone.";

    @CustomKey("triggers-title")
    private String triggersTitle = "Run On";

    @CustomKey("commands-title")
    private String commandsTitle = "Command Lists";

    @CustomKey("lines-title")
    private String linesTitle = "Commands: {list}";

    @CustomKey("lines-add")
    private String linesAdd = "Add Line";

    @CustomKey("lines-add-title")
    private String linesAddTitle = "Add Command";

    @CustomKey("lines-edit-title")
    private String linesEditTitle = "Edit Command";

    @CustomKey("lines-prompt")
    private String linesPrompt = "Tags like <p> and <random-num:1,6> resolve.";

    @CustomKey("lines-delete-hint")
    private String linesDeleteHint = "Right-click to delete";

    @CustomKey("lines-delete-title")
    private String linesDeleteTitle = "Delete this line?";

    @CustomKey("members-title")
    private String membersTitle = "Members";

    @CustomKey("members-lore")
    private String membersLore = "{total} members";

    @CustomKey("meta-title")
    private String metaTitle = "Meta";

    @CustomKey("meta-lore")
    private String metaLore = "Name, description, icon, author";

    @CustomKey("behavior-title")
    private String behaviorTitle = "Behavior";

    @CustomKey("behavior-lore")
    private String behaviorLore = "Triggers, options, commands";

    @CustomKey("modifiers-title")
    private String modifiersTitle = "Modifiers";

    @CustomKey("editor-delete-preset")
    private String editorDeletePreset = "Delete Preset";

    @CustomKey("editor-delete-modifier")
    private String editorDeleteModifier = "Delete Modifier";

    @CustomKey("editor-rename-hint")
    private String editorRenameHint = "Right-click to rename id";

    @CustomKey("cancel")
    private String cancel = "Cancel";

    @CustomKey("confirm")
    private String confirm = "Confirm";
}
