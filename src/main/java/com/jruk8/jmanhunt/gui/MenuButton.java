package com.jruk8.jmanhunt.gui;

import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * One clickable menu icon.
 *
 * <p>A button carries its rendered text plus the action to run when clicked.
 * A null action means the button is display-only; filler buttons use
 * {@link #filler()} and never appear in click routing.
 *
 * <p>Sound convention: {@link GuiService} plays the compass click after
 * every executed action unless the button is {@link SoundPolicy#SILENT}.
 * Silent buttons are explicitly constrained (toggles, dialog openers,
 * confirm commits) and their actions play exactly the sound the commit
 * needs, usually neutral on success and angry on failure.
 */
public final class MenuButton {

    /** Central click sound policy for one button. */
    public enum SoundPolicy {
        /** GuiService plays the compass click after the action. */
        CLICK,
        /** No central sound; the action plays its own sounds. */
        SILENT
    }

    private final Material material;
    private final Component name;
    private final List<Component> lore;
    private final boolean glow;
    private final boolean hideTooltip;
    private final Consumer<Player> action;
    private final Consumer<Player> rightAction;
    private final Consumer<Player> shiftAction;
    private final SoundPolicy soundPolicy;
    private final Consumer<ItemMeta> metaTweak;

    /**
     * Full button spec: icon, text, glint, tooltip, click actions,
     * central sound policy, and an optional item-meta tweak.
     *
     * @param material icon material, never air
     * @param name display name, may be null for no custom name
     * @param lore lore lines, null means none
     * @param glow true to force the enchantment glint
     * @param hideTooltip true to hide the hover tooltip
     * @param action click action, null for display-only buttons
     * @param rightAction right-click action, null to reuse the main action
     * @param shiftAction shift-left-click action, null to reuse the main action
     * @param soundPolicy central click sound policy, never null
     * @param metaTweak item-meta tweak, null for none
     */
    public record Spec(Material material, Component name, List<Component> lore, boolean glow,
            boolean hideTooltip, Consumer<Player> action, Consumer<Player> rightAction,
            Consumer<Player> shiftAction, SoundPolicy soundPolicy,
            Consumer<ItemMeta> metaTweak) {
    }

    public MenuButton(Spec spec) {
        this.material = spec.material();
        this.name = spec.name();
        this.lore = spec.lore() == null ? List.of() : List.copyOf(spec.lore());
        this.glow = spec.glow();
        this.hideTooltip = spec.hideTooltip();
        this.action = spec.action();
        this.rightAction = spec.rightAction();
        this.shiftAction = spec.shiftAction();
        this.soundPolicy = spec.soundPolicy();
        this.metaTweak = spec.metaTweak();
    }

    /** Blank, tooltip-less filler pane with no action. */
    public static MenuButton filler() {
        return new MenuButton(new Spec(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "),
                null, false, true, null, null, null, SoundPolicy.CLICK, null));
    }

    /** Copy of this button with the central click suppressed. */
    public MenuButton silent() {
        if (soundPolicy == SoundPolicy.SILENT) {
            return this;
        }
        return new MenuButton(new Spec(material, name, lore, glow, hideTooltip, action,
                rightAction, shiftAction, SoundPolicy.SILENT, metaTweak));
    }

    /** Copy of this button running the shift action on shift-left-click. */
    public MenuButton shiftAction(Consumer<Player> shiftAction) {
        return new MenuButton(new Spec(material, name, lore, glow, hideTooltip, action,
                rightAction, shiftAction, soundPolicy, metaTweak));
    }

    /**
     * Copy of this button with an item-meta tweak applied at build time,
     * for per-button meta such as skull profiles. Null clears the tweak.
     */
    public MenuButton withMeta(Consumer<ItemMeta> metaTweak) {
        return new MenuButton(new Spec(material, name, lore, glow, hideTooltip, action,
                rightAction, shiftAction, soundPolicy, metaTweak));
    }

    /** Builds the displayed item. */
    public ItemStack buildItem() {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (name != null) {
            meta.displayName(name);
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_PLACED_ON,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ARMOR_TRIM,
                ItemFlag.HIDE_STORED_ENCHANTS);
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (hideTooltip) {
            meta.setHideTooltip(true);
        }
        if (metaTweak != null) {
            metaTweak.accept(meta);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public Material material() {
        return material;
    }

    public Component name() {
        return name;
    }

    public List<Component> lore() {
        return lore;
    }

    public boolean glow() {
        return glow;
    }

    public boolean hideTooltip() {
        return hideTooltip;
    }

    public Consumer<Player> action() {
        return action;
    }

    public Consumer<Player> rightAction() {
        return rightAction;
    }

    public Consumer<Player> shiftAction() {
        return shiftAction;
    }

    public SoundPolicy soundPolicy() {
        return soundPolicy;
    }

    public Consumer<ItemMeta> metaTweak() {
        return metaTweak;
    }
}
