package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.modifiers.ModifierOptionDescriptors;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierOptions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Shared behavior rows, prompts, and patches behind the option menus. */
final class BehaviorRows {
    private final ModifierStore store;
    private final BehaviorOptionsMenus.BehaviorTexts texts;
    private final BehaviorOptionsMenus.BehaviorDeps deps;

    BehaviorRows(ModifierStore store, BehaviorOptionsMenus.BehaviorTexts texts,
            BehaviorOptionsMenus.BehaviorDeps deps) {
        this.store = store;
        this.texts = texts;
        this.deps = deps;
    }

    /**
     * One option leaf in the shared settings schema. A null label
     * keeps the descriptor label; the Runs On row overrides it with
     * its configured title.
     */
    MenuButton leafRow(String id, int index, Supplier<Menu> self, String optionKey, String label,
            String value, List<String> options, Set<String> marked, boolean glow,
            Consumer<Player> click, Runnable clear) {
        ModifierOptionDescriptors.Descriptor descriptor =
                ModifierOptionDescriptors.byKey(optionKey);
        FieldLore.Field field = new FieldLore.Field(
                descriptor.description(), value,
                behaviorPath(id, index, descriptor.pathSuffix()),
                ModifierOptionDescriptors.typeName(descriptor.kind()),
                descriptor.allowed(), options, marked,
                descriptor.defaultText(), hintFor(descriptor.kind()), false);
        return FieldButtons.field(texts.messages(), descriptor.icon(),
                label == null ? descriptor.label() : label,
                FieldLore.lines(texts.manhuntGui(), field), glow,
                click, player -> resetLeaf(player, id, index, self, optionKey, value, clear));
    }

    MenuButton choiceRow(String id, int index, Supplier<Menu> self, String optionKey,
            String raw, String fallback, boolean glow,
            Consumer<Player> click, Runnable clear) {
        ModifierOptionDescriptors.Descriptor descriptor =
                ModifierOptionDescriptors.byKey(optionKey);
        String effective = effectiveOption(raw, descriptor.options(), fallback);
        return leafRow(id, index, self, optionKey, null,
                raw == null ? "Default (" + fallback + ")" : raw,
                descriptor.options(), Set.of(effective), glow, click, clear);
    }

    String hintFor(ModifierOptionDescriptors.Kind kind) {
        return switch (kind) {
            case NUMBER, INTEGER ->
                    texts.manhuntGui().getSettingHintEdit();
            case CHOICE ->
                    texts.manhuntGui().getSettingHintCycle();
            case TRIGGERS -> texts.modifiersGui().getEditorClickOpen();
        };
    }

    /**
     * Right-click reset behind a confirm panel, mirroring the settings
     * flow: already-default rows refuse in chat instead of opening
     * a panel for nothing.
     */
    void resetLeaf(Player player, String id, int index, Supplier<Menu> self, String optionKey,
            String value, Runnable clear) {
        if (denied(player)) {
            return;
        }
        if (!leafModified(id, index, optionKey)) {
            texts.messages().messageRaw(player, texts.modifiersGui().getEditorAlreadyDefault());
            return;
        }
        ModifierOptionDescriptors.Descriptor descriptor =
                ModifierOptionDescriptors.byKey(optionKey);
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(texts.messages(), texts.modifiersGui().getEditorResetTitle()
                        .replace("{name}", descriptor.label())),
                Material.PAPER, null,
                GuiTexts.lore(texts.messages(), List.of(value + " -> " + descriptor.defaultText())),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getCancel(), "Cancel"),
                back -> deps.gui().navigate(back, self.get()),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getConfirm(), "Confirm"),
                done -> {
                    clear.run();
                    if (texts.sounds() != null) {
                        texts.sounds().playNeutralSound(done);
                    }
                    deps.gui().navigate(done, self.get());
                },
                self);
        deps.gui().navigate(player, confirm);
        if (texts.sounds() != null) {
            texts.sounds().playSound(player, "compass.left-click");
        }
    }

    boolean leafModified(String id, int index, String optionKey) {
        return switch (optionKey) {
            case "delay" -> ModifiedGlow.behaviorDelay(store, id, index);
            case "interval" -> ModifiedGlow.behaviorInterval(store, id, index);
            case "deviation" -> ModifiedGlow.behaviorDeviation(store, id, index);
            case "interval-scope" -> ModifiedGlow.behaviorIntervalScope(store, id, index);
            case "selection" -> ModifiedGlow.behaviorSelection(store, id, index);
            case "pick-count" -> ModifiedGlow.behaviorPickCount(store, id, index);
            case "pick-scope" -> ModifiedGlow.behaviorPickScope(store, id, index);
            case "pre-start" -> ModifiedGlow.behaviorPreStart(store, id, index);
            case "chance" -> ModifiedGlow.behaviorChance(store, id, index);
            case "chance-scope" -> ModifiedGlow.behaviorChanceScope(store, id, index);
            case "runs-on" -> ModifiedGlow.behaviorRunsOn(store, id, index);
            default -> false;
        };
    }

    static String behaviorPath(String id, int index, String pathSuffix) {
        String scoped = pathSuffix.startsWith("behavior.")
                ? "behavior." + index + "." + pathSuffix.substring("behavior.".length())
                : pathSuffix;
        return "modifiers." + id + "." + scoped;
    }

    static String effectiveOption(String raw, List<String> options, String fallback) {
        if (raw != null) {
            for (String option : options) {
                if (option.equalsIgnoreCase(raw)) {
                    return option;
                }
            }
        }
        return fallback;
    }

    void fieldPrompt(Player player, Supplier<Menu> reopen, String label, String current,
            boolean clearable, FieldPrompts.Submit submit) {
        FieldPrompts.prompt(deps.dialogs(), deps.gui(), texts.messages(), texts.modifiersGui(), texts.modifiers(),
                texts.sounds(), player, reopen,
                texts.modifiersGui().getEditorPromptTitle().replace("{label}", label),
                current, clearable, submit);
    }

    void patch(String id, Consumer<ModifierEntry> patch) {
        store.updateModifier(id, patch);
    }

    static String cycle(String current, String first, String second) {
        if (current == null) {
            return first;
        }
        if (first.equalsIgnoreCase(current)) {
            return second;
        }
        return null;
    }

    String orUnset(String value) {
        return value == null || value.isBlank()
                ? texts.modifiersGui().getEditorUnset() : value;
    }

    ModifierOptions rawOptions(String id, int index) {
        ModifierEntry entry = store.modifierEntry(id);
        return entry == null ? null : rawOptions(entry, index);
    }

    static ModifierOptions rawOptions(ModifierEntry entry, int index) {
        if (entry.getBehavior() == null) {
            return null;
        }
        ModifierBehavior behavior = entry.getBehavior().get(String.valueOf(index));
        return behavior == null ? null : behavior.getOptions();
    }

    Component backName() {
        return GuiTexts.name(texts.messages(), texts.modifiersGui().getBack(), "Back");
    }

    boolean denied(Player player) {
        if (player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
            return false;
        }
        texts.messages().messageRaw(player, texts.command().getNoPermission());
        return true;
    }

    void invalid(Player player, String error) {
        texts.messages().messageRaw(player, texts.modifiers().getEditInvalid(), Map.of("error", error));
        texts.sounds().playAngrySound(player);
    }
}
