package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.MenuLayout;
import com.jruk8.jmanhunt.gui.PagedList;
import com.jruk8.jmanhunt.gui.ScalingLayout;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.SpectatorToolbarService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;

/** Spectator toolbar browsers: running matches and spectateable players. */
public final class SpectatorMenus {
    private final MessageService messages;
    private final GuiService gui;
    private final SpectatorToolbarService toolbar;

    public SpectatorMenus(MessageService messages, GuiService gui,
            SpectatorToolbarService toolbar) {
        this.messages = messages;
        this.gui = gui;
        this.toolbar = toolbar;
    }

    /** Opens the match browser, or says so when nothing runs. */
    public void openLobbiesMenu(Player spectator) {
        List<SpectatorToolbarService.MatchEntry> entries = toolbar.matchEntries(spectator);
        if (entries.isEmpty()) {
            messages.message(spectator, "spectator.no-matches");
            return;
        }
        gui.open(spectator, lobbiesMenu(spectator));
    }

    /** Opens the player browser, or says so when nobody qualifies. */
    public void openPlayersMenu(Player spectator) {
        List<SpectatorToolbarService.PlayerEntry> entries = toolbar.playerEntries(spectator);
        if (entries.isEmpty()) {
            messages.message(spectator, "spectator.no-players");
            return;
        }
        gui.open(spectator, playersMenu(spectator));
    }

    /** Browse Matches: the shared modifiers-pattern scrollable list. */
    Menu lobbiesMenu(Player spectator) {
        return PagedList.menu(
                GuiTexts.title(messages, text("lobbies-title", "Running Matches")),
                columns -> lobbyButtons(spectator), null, gui,
                GuiTexts.name(messages, text("scroll-up", "Scroll up"), "Scroll up"),
                GuiTexts.name(messages, text("scroll-down", "Scroll down"), "Scroll down"),
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                self -> PagedList.Chrome.none());
    }

    /** Spectate Player: a scaling menu like the settings drills. */
    Menu playersMenu(Player spectator) {
        Supplier<List<MenuButton>> content = () -> playerButtons(spectator);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(messages, text("players-title", "Spectate Player")),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        new MenuButton(Material.PAPER,
                                GuiTexts.name(messages, text("back", "Back"), "Back"),
                                null, false, false,
                                player -> gui.back(player, self[0]))),
                content::get, null);
        return self[0];
    }

    private List<MenuButton> lobbyButtons(Player spectator) {
        List<MenuButton> buttons = new ArrayList<>();
        for (SpectatorToolbarService.MatchEntry entry : toolbar.matchEntries(spectator)) {
            String info = text("lobbies-entry-info", "{runners} runners, {hunters} hunters")
                    .replace("{runners}", String.valueOf(entry.runners()))
                    .replace("{hunters}", String.valueOf(entry.hunters()));
            String hint = entry.current()
                    ? text("lobbies-current-hint", "You are here")
                    : text("lobbies-entry-hint", "Click to spectate");
            String lore = text("lobbies-entry-lore", "{info}\\n{hint}")
                    .replace("{info}", info).replace("{hint}", hint);
            buttons.add(new MenuButton(Material.ENDER_EYE,
                    GuiTexts.name(messages,
                            text("lobbies-entry-name", "{label}")
                                    .replace("{label}", entry.label()),
                            entry.label()),
                    GuiTexts.lore(messages, lore), entry.subLobby(), false,
                    entry.current()
                            ? Player::closeInventory
                            : player -> toolbar.swapSpectator(player, entry.matchId())));
        }
        return buttons;
    }

    private List<MenuButton> playerButtons(Player spectator) {
        List<MenuButton> buttons = new ArrayList<>();
        for (SpectatorToolbarService.PlayerEntry entry : toolbar.playerEntries(spectator)) {
            buttons.add(new MenuButton(Material.PLAYER_HEAD,
                    GuiTexts.name(messages, entry.name(), entry.name()),
                    GuiTexts.lore(messages, List.of(
                            messages.roleName(entry.role()),
                            text("players-entry-hint", "Click to teleport and follow"))),
                    entry.locked(), false,
                    player -> toolbar.teleportAndLock(player, entry.id()))
                    .withMeta(meta -> {
                        if (meta instanceof SkullMeta skull) {
                            skull.setOwnerProfile(Bukkit.createPlayerProfile(entry.id()));
                        }
                    }));
        }
        return buttons;
    }

    private String text(String key, String fallback) {
        return messages.string("spectator." + key, fallback);
    }
}
