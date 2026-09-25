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
    private final SoundPolicy soundPolicy;

    /**
     * @param material icon material, never air
     * @param name display name, may be null for no custom name
     * @param lore lore lines, null means none
     * @param glow true to force the enchantment glint
     * @param hideTooltip true to hide the hover tooltip
     * @param action click action, null for display-only buttons
     */
    public MenuButton(Material material, Component name, List<Component> lore,
            boolean glow, boolean hideTooltip, Consumer<Player> action) {
        this(material, name, lore, glow, hideTooltip, action, null);
    }

    /**
     * @param material icon material, never air
     * @param name display name, may be null for no custom name
     * @param lore lore lines, null means none
     * @param glow true to force the enchantment glint
     * @param hideTooltip true to hide the hover tooltip
     * @param action click action, null for display-only buttons
     * @param rightAction right-click action, null to reuse the main action
     */
    public MenuButton(Material material, Component name, List<Component> lore,
            boolean glow, boolean hideTooltip, Consumer<Player> action,
            Consumer<Player> rightAction) {
        this(material, name, lore, glow, hideTooltip, action, rightAction,
                SoundPolicy.CLICK);
    }

    /**
     * @param material icon material, never air
     * @param name display name, may be null for no custom name
     * @param lore lore lines, null means none
     * @param glow true to force the enchantment glint
     * @param hideTooltip true to hide the hover tooltip
     * @param action click action, null for display-only buttons
     * @param rightAction right-click action, null to reuse the main action
     * @param soundPolicy central click sound policy, never null
     */
    public MenuButton(Material material, Component name, List<Component> lore,
            boolean glow, boolean hideTooltip, Consumer<Player> action,
            Consumer<Player> rightAction, SoundPolicy soundPolicy) {
        this.material = material;
        this.name = name;
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        this.glow = glow;
        this.hideTooltip = hideTooltip;
        this.action = action;
        this.rightAction = rightAction;
        this.soundPolicy = soundPolicy;
    }

    /** Blank, tooltip-less filler pane with no action. */
    public static MenuButton filler() {
        return new MenuButton(Material.GRAY_STAINED_GLASS_PANE,
                Component.text(" "), null, false, true, null);
    }

    /** Copy of this button with the central click suppressed. */
    public MenuButton silent() {
        if (soundPolicy == SoundPolicy.SILENT) {
            return this;
        }
        return new MenuButton(material, name, lore, glow, hideTooltip,
                action, rightAction, SoundPolicy.SILENT);
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

    public SoundPolicy soundPolicy() {
        return soundPolicy;
    }
}
