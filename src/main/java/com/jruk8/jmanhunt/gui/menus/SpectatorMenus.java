package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.MenuLayout;
import com.jruk8.jmanhunt.gui.PagedList;
import com.jruk8.jmanhunt.gui.ScalingLayout;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SpectatorMessages;
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
    private final SpectatorMessages texts;
    private final GuiService gui;
    private final SpectatorToolbarService toolbar;

    public SpectatorMenus(MessageService messages, SpectatorMessages texts, GuiService gui,
            SpectatorToolbarService toolbar) {
        this.messages = messages;
        this.texts = texts;
        this.gui = gui;
        this.toolbar = toolbar;
    }

    /** Opens the match browser, or says so when nothing runs. */
    public void openLobbiesMenu(Player spectator) {
        List<SpectatorToolbarService.MatchEntry> entries = toolbar.matchEntries(spectator);
        if (entries.isEmpty()) {
            messages.messageRaw(spectator, texts.getNoMatches());
            return;
        }
        gui.open(spectator, lobbiesMenu(spectator));
    }

    /** Opens the player browser, or says so when nobody qualifies. */
    public void openPlayersMenu(Player spectator) {
        List<SpectatorToolbarService.PlayerEntry> entries = toolbar.playerEntries(spectator);
        if (entries.isEmpty()) {
            messages.messageRaw(spectator, texts.getNoPlayers());
            return;
        }
        gui.open(spectator, playersMenu(spectator));
    }

    /** Browse Matches: the shared modifiers-pattern scrollable list. */
    Menu lobbiesMenu(Player spectator) {
        return PagedList.menu(
                GuiTexts.title(messages, texts.getLobbiesTitle()),
                columns -> lobbyButtons(spectator), null, gui,
                GuiTexts.name(messages, texts.getScrollUp(), "Scroll up"),
                GuiTexts.name(messages, texts.getScrollDown(), "Scroll down"),
                GuiTexts.name(messages, texts.getBack(), "Back"),
                self -> PagedList.Chrome.none());
    }

    /** Spectate Player: a scaling menu like the settings drills. */
    Menu playersMenu(Player spectator) {
        Supplier<List<MenuButton>> content = () -> playerButtons(spectator);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(messages, texts.getPlayersTitle()),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        new MenuButton(Material.PAPER,
                                GuiTexts.name(messages, texts.getBack(), "Back"),
                                null, false, false,
                                player -> gui.back(player, self[0]))),
                content::get, null);
        return self[0];
    }

    private List<MenuButton> lobbyButtons(Player spectator) {
        List<MenuButton> buttons = new ArrayList<>();
        for (SpectatorToolbarService.MatchEntry entry : toolbar.matchEntries(spectator)) {
            String info = texts.getLobbiesEntryInfo()
                    .replace("{runners}", String.valueOf(entry.runners()))
                    .replace("{hunters}", String.valueOf(entry.hunters()));
            String hint = entry.current()
                    ? texts.getLobbiesCurrentHint()
                    : texts.getLobbiesEntryHint();
            String lore = texts.getLobbiesEntryLore()
                    .replace("{info}", info).replace("{hint}", hint);
            buttons.add(new MenuButton(Material.ENDER_EYE,
                    GuiTexts.name(messages,
                            texts.getLobbiesEntryName()
                                    .replace("{label}", entry.label()),
                            entry.label()),
                    GuiTexts.lore(messages, lore), entry.subLobby(), false,
                    player -> toolbar.swapSpectator(player, entry.matchId())).silent());
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
                            texts.getPlayersEntryHint())),
                    entry.locked(), false,
                    player -> toolbar.teleportAndLock(player, entry.id()))
                    .silent()
                    .withMeta(meta -> {
                        if (meta instanceof SkullMeta skull) {
                            skull.setOwnerProfile(Bukkit.createPlayerProfile(entry.id()));
                        }
                    }));
        }
        return buttons;
    }


}
