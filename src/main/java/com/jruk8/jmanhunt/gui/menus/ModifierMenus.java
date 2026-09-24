package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.MenuLayout;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The three modifiers menus: main page plus the modifiers and presets
 * scroll lists. Each builder returns a fresh menu, so counts, order, and
 * glow are always read live. Buttons capture ids only; toggles run the
 * chat command as the clicking player, so permission, chat text, and
 * announces behave exactly like chat.
 */
public final class ModifierMenus {

    /** Member lines shown in preset lore before collapsing to "and n more". */
    public static final int MAX_PRESET_LORE_LINES = 8;

    private final ModifierStore store;
    private final MessageService messages;
    private final SoundService sounds;
    private final GuiService gui;
    private final ModifiersCommand toggles;
    private final SettingDialogs dialogs;

    /**
     * @param store modifier and preset reads
     * @param messages GUI text; sounds, gui, toggles, and dialogs are only
     *        touched inside click actions, so builders tolerate them as null
     */
    public ModifierMenus(ModifierStore store, MessageService messages, SoundService sounds,
            GuiService gui, ModifiersCommand toggles, SettingDialogs dialogs) {
        this.store = store;
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.toggles = toggles;
        this.dialogs = dialogs;
    }

    /** 27-slot root with links to both lists. */
    public Menu mainMenu() {
        return mainMenu(null);
    }

    /**
     * Root with an optional parent. An embedded root (parent supplied)
     * gains a back button so Back returns to the embedding menu.
     */
    public Menu mainMenu(Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(messages, text("title-main", "Modifiers")),
                MenuLayout.parse("#########", "###m#p###", "#########"),
                () -> mainStatic(self[0], parent), List::of, parent);
        return self[0];
    }

    private Map<Integer, MenuButton> mainStatic(Menu self, Supplier<Menu> parent) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        fixed.put(12, linkButton(Material.DIAMOND, "to-modifiers", "to-modifiers-lore",
                "Modifiers", enabledModifiers(), store.modifierNames().size(),
                () -> modifiersMenu(parent)));
        fixed.put(14, linkButton(Material.FILLED_MAP, "to-presets", "to-presets-lore",
                "Presets", enabledPresets(), store.presetNames().size(),
                () -> presetsMenu(parent)));
        if (parent != null) {
            fixed.put(13, new MenuButton(Material.PAPER,
                    GuiTexts.name(messages, text("back", "Back"), "Back"),
                    null, false, false,
                    player -> {
                        gui.back(player, self);
                        sounds.playNeutralSound(player);
                    }));
        }
        return fixed;
    }

    /** 45-slot modifiers scroll list. */
    public Menu modifiersMenu() {
        return modifiersMenu(null);
    }

    /** Modifiers scroll list with an explicit root parent. */
    public Menu modifiersMenu(Supplier<Menu> parent) {
        return listMenu("title-modifiers", this::modifierButtons,
                () -> mainMenu(parent), this::toggleAllModifiersButton,
                self -> importButton(self, "modifier", "import-modifier",
                        "import-modifier-lore", "import-modifier-title"));
    }

    /** 45-slot presets scroll list. */
    public Menu presetsMenu() {
        return presetsMenu(null);
    }

    /** Presets scroll list with an explicit root parent. */
    public Menu presetsMenu(Supplier<Menu> parent) {
        return listMenu("title-presets", this::presetButtons,
                () -> mainMenu(parent), this::toggleAllPresetsButton,
                self -> importButton(self, "preset", "import-preset",
                        "import-preset-lore", "import-preset-title"));
    }

    private Menu listMenu(String titleKey, Function<Integer, List<MenuButton>> content,
            Supplier<Menu> parent, Supplier<MenuButton> toggleAll,
            Function<Menu[], MenuButton> importButton) {
        MenuLayout layout = MenuLayout.parse(
                "##xxxxxx#", "u#xxxxxx#", "b#xxxxxxt", "d#xxxxxx#", "##xxxxxx#");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(messages, text(titleKey, "Modifiers")),
                layout, () -> listStatic(self, toggleAll, importButton),
                () -> content.apply(layout.contentColumns()), parent);
        return self[0];
    }

    private Map<Integer, MenuButton> listStatic(
            Menu[] self, Supplier<MenuButton> toggleAll, Function<Menu[], MenuButton> importButton) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        fixed.put(9, scrollButton(Material.ARROW, "scroll-up", "Scroll up", self, -1));
        fixed.put(18, new MenuButton(Material.PAPER,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                null, false, false,
                player -> {
                    gui.back(player, self[0]);
                    sounds.playNeutralSound(player);
                }));
        fixed.put(26, toggleAll.get());
        fixed.put(27, scrollButton(Material.ARROW, "scroll-down", "Scroll down", self, 1));
        fixed.put(36, importButton.apply(self));
        return fixed;
    }

    /** Bottom-left import loom: prompts for a share string, then refreshes. */
    private MenuButton importButton(Menu[] self, String type, String nameKey,
            String loreKey, String titleKey) {
        return new MenuButton(Material.LOOM,
                GuiTexts.name(messages,
                        text(nameKey, type.equals("preset") ? "Import Preset" : "Import Modifier"),
                        "Import"),
                GuiTexts.lore(messages, text(loreKey, "Paste an exported string.")),
                false, false,
                player -> {
                    if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                        messages.message(player, "command.no-permission");
                        return;
                    }
                    dialogs.prompt(player,
                            text(titleKey,
                                    type.equals("preset") ? "Import Preset" : "Import Modifier"),
                            List.of(text("import-prompt", "Paste an exported string.")),
                            payload -> {
                                if (!toggles.importEntry(player, type, payload)) {
                                    sounds.playAngrySound(player);
                                }
                                gui.navigate(player, self[0]);
                            },
                            () -> gui.navigate(player, self[0]));
                });
    }

    private MenuButton toggleAllModifiersButton() {
        boolean allOn = allModifiersOn();
        String loreText = text("toggle-all-modifiers-lore", "{total} modifiers")
                .replace("{total}", String.valueOf(store.modifierNames().size()));
        return new MenuButton(Material.STRUCTURE_VOID,
                GuiTexts.name(messages, text("toggle-all", "Toggle all"), "Toggle all"),
                GuiTexts.lore(messages, loreText), allOn, false,
                player -> {
                    if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                        messages.message(player, "command.no-permission");
                        return;
                    }
                    toggles.toggleAllModifiers(player,
                            new ArrayList<>(store.modifierNames()), !allModifiersOn());
                    sounds.playNeutralSound(player);
                });
    }

    private MenuButton toggleAllPresetsButton() {
        boolean allOn = allPresetsOn();
        String loreText = text("toggle-all-presets-lore", "{total} presets")
                .replace("{total}", String.valueOf(store.presetNames().size()));
        return new MenuButton(Material.STRUCTURE_VOID,
                GuiTexts.name(messages, text("toggle-all", "Toggle all"), "Toggle all"),
                GuiTexts.lore(messages, loreText), allOn, false,
                player -> {
                    if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                        messages.message(player, "command.no-permission");
                        return;
                    }
                    toggles.toggleAllPresets(player,
                            new ArrayList<>(store.presetNames()), !allPresetsOn());
                    sounds.playNeutralSound(player);
                });
    }

    private MenuButton linkButton(Material material, String nameKey, String loreKey,
            String fallback, int enabled, int total, Supplier<Menu> target) {
        String loreText = text(loreKey, "{enabled}/{total} enabled")
                .replace("{enabled}", String.valueOf(enabled))
                .replace("{total}", String.valueOf(total));
        return new MenuButton(material,
                GuiTexts.name(messages, text(nameKey, fallback), fallback),
                GuiTexts.lore(messages, loreText), false, false,
                player -> {
                    gui.navigate(player, target.get());
                    sounds.playNeutralSound(player);
                });
    }

    private MenuButton scrollButton(Material material, String nameKey, String fallback,
            Menu[] self, int delta) {
        return new MenuButton(material,
                GuiTexts.name(messages, text(nameKey, fallback), fallback),
                null, false, false,
                player -> {
                    if (self[0].window().scrollLine(delta)) {
                        sounds.playSound(player, "compass.left-click");
                    }
                });
    }

    private List<MenuButton> modifierButtons(int columns) {
        Map<String, Integer> order = fileOrder(store.modifierNames());
        List<String> ids = new ArrayList<>(store.modifierNames());
        ids.sort(MenuOrder.modifiers(store::metaName, store::isEnabled, order::get));
        int enabled = 0;
        while (enabled < ids.size() && store.isEnabled(ids.get(enabled))) {
            enabled++;
        }
        List<MenuButton> buttons = new ArrayList<>();
        for (int index = 0; index < enabled; index++) {
            buttons.add(modifierButton(ids.get(index), true));
        }
        padGroup(buttons, enabled, columns);
        for (int index = enabled; index < ids.size(); index++) {
            buttons.add(modifierButton(ids.get(index), false));
        }
        return buttons;
    }

    private MenuButton modifierButton(String id, boolean enabled) {
        return new MenuButton(store.metaItem(id),
                GuiTexts.name(messages, store.metaName(id), ModifierStore.DEFAULT_NAME),
                modifierLore(id, enabled), enabled, false, toggleModifier(id));
    }

    /**
     * Pads the leading group with nulls so the next group starts on a
     * fresh content row. Nulls render as empty slots.
     */
    private static void padGroup(List<MenuButton> buttons, int groupSize, int columns) {
        int pad = (columns - groupSize % columns) % columns;
        for (int index = 0; index < pad; index++) {
            buttons.add(null);
        }
    }

    private static Map<String, Integer> fileOrder(Set<String> ids) {
        Map<String, Integer> order = new HashMap<>();
        int index = 0;
        for (String id : ids) {
            order.put(id, index++);
        }
        return order;
    }

    private List<Component> modifierLore(String id, boolean enabled) {
        List<Component> lore = new ArrayList<>(GuiTexts.lore(messages, store.metaDescription(id)));
        if (!lore.isEmpty()) {
            lore.add(Component.text(" "));
        }
        lore.addAll(GuiTexts.lore(messages, text(stateKey(enabled), enabled ? "Enabled" : "Disabled")));
        String author = store.metaAuthor(id);
        if (author != null) {
            lore.add(Component.text(" "));
            lore.addAll(GuiTexts.lore(messages, "by " + author));
        }
        return lore;
    }

    private List<MenuButton> presetButtons(int columns) {
        Map<String, Integer> order = fileOrder(store.presetNames());
        List<String> ids = new ArrayList<>(store.presetNames());
        ids.sort(MenuOrder.presets(store::presetName, store::presetEnabled, order::get));
        int allOn = 0;
        while (allOn < ids.size() && store.presetEnabled(ids.get(allOn))) {
            allOn++;
        }
        List<MenuButton> buttons = new ArrayList<>();
        for (int index = 0; index < allOn; index++) {
            buttons.add(presetButton(ids.get(index), true));
        }
        padGroup(buttons, allOn, columns);
        for (int index = allOn; index < ids.size(); index++) {
            buttons.add(presetButton(ids.get(index), false));
        }
        return buttons;
    }

    private MenuButton presetButton(String id, boolean allOn) {
        return new MenuButton(store.presetItem(id),
                GuiTexts.name(messages, store.presetName(id), ModifierStore.DEFAULT_NAME),
                presetLore(id, allOn), allOn, false, togglePreset(id));
    }

    private List<Component> presetLore(String id, boolean allOn) {
        List<Component> lore = new ArrayList<>();
        List<String> members = store.presetMembers(id);
        int shown = Math.min(members.size(), MAX_PRESET_LORE_LINES);
        for (int index = 0; index < shown; index++) {
            String member = members.get(index);
            String color = store.isEnabled(member) ? "<green>" : "<red>";
            lore.addAll(GuiTexts.lore(messages, color + "» " + store.metaName(member)));
        }
        if (members.size() > shown) {
            String more = text("preset-more", "and {count} more")
                    .replace("{count}", String.valueOf(members.size() - shown));
            lore.addAll(GuiTexts.lore(messages, more));
        }
        if (!lore.isEmpty()) {
            lore.add(Component.text(" "));
        }
        lore.addAll(GuiTexts.lore(messages, text(stateKey(allOn), allOn ? "Enabled" : "Disabled")));
        return lore;
    }

    private Consumer<Player> toggleModifier(String id) {
        return player -> {
            if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                messages.message(player, "command.no-permission");
                return;
            }
            boolean next = !store.isEnabled(id);
            toggles.execute(player, new String[]{"setmod", id, String.valueOf(next)});
            sounds.playNeutralSound(player);
        };
    }

    private Consumer<Player> togglePreset(String id) {
        return player -> {
            if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                messages.message(player, "command.no-permission");
                return;
            }
            boolean next = !store.presetEnabled(id);
            toggles.execute(player, new String[]{"setpreset", id, String.valueOf(next)});
            sounds.playNeutralSound(player);
        };
    }

    private int enabledModifiers() {
        int enabled = 0;
        for (String id : store.modifierNames()) {
            if (store.isEnabled(id)) {
                enabled++;
            }
        }
        return enabled;
    }

    private int enabledPresets() {
        int enabled = 0;
        for (String id : store.presetNames()) {
            if (store.presetEnabled(id)) {
                enabled++;
            }
        }
        return enabled;
    }

    private boolean allModifiersOn() {
        return !store.modifierNames().isEmpty()
                && enabledModifiers() == store.modifierNames().size();
    }

    private boolean allPresetsOn() {
        return !store.presetNames().isEmpty()
                && enabledPresets() == store.presetNames().size();
    }

    private static String stateKey(boolean on) {
        return on ? "state-on" : "state-off";
    }

    private String text(String key, String fallback) {
        return messages.string("modifiers-gui." + key, fallback);
    }
}
