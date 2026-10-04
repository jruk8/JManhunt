package com.jruk8.jmanhunt.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.MiscConfig;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.lobby.config.PlayersSettingsFacade;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * End-to-end dispatch routing for test-a-command: tildes resolve
 * against the tester and every player-context line dispatches pinned
 * to the tester's world and facing, so position-dependent commands
 * land where the tester stands instead of the console world.
 */
final class ModifierTestDispatchTest {

    @Test
    void testRunDispatchesTildesPinnedToTester() {
        UUID senderId = UUID.randomUUID();
        List<String> dispatched = runTestLine(senderId, "summon pig ~ ~2 ~");
        assertEquals(List.of("execute as " + senderId + " at @s run summon pig 100 66 200"),
                dispatched);
    }

    @Test
    void testRunPassesCaretsThroughPinnedToTester() {
        UUID senderId = UUID.randomUUID();
        List<String> dispatched = runTestLine(senderId, "summon pig ^ ^ ^2");
        assertEquals(List.of("execute as " + senderId + " at @s run summon pig ^ ^ ^2"),
                dispatched);
    }

    private static List<String> runTestLine(UUID senderId, String line) {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.logger()).thenReturn(mock(JManhuntLogger.class));
        GameStateCommandManager commands = new GameStateCommandManager(
                new GameStateCommandManager.CommandReads(new PlayerStateStore(),
                        mock(ConfigService.class), new MiscConfig.Interop(),
                        mock(PlayersSettingsFacade.class)),
                new GameStateCommandManager.CommandEdge(
                        plugin.engineStates(), plugin.fakeSpectators(),
                        plugin.logger(), plugin.overrides(),
                        plugin.placeholderValues(), plugin),
                mock(MessageService.class), mock(SoundService.class), mock(GameManager.class));
        ModifierTestService test = new ModifierTestService(commands, mock(PlayerStateStore.class),
                mock(MessageService.class), mock(ModifiersMessages.class), mock(SoundService.class));
        Player sender = mock(Player.class);
        when(sender.getName()).thenReturn("Steve");
        when(sender.getUniqueId()).thenReturn(senderId);
        when(sender.getLocation()).thenReturn(new Location(mock(World.class), 100.0, 64.0, 200.0));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
            ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
            bukkit.when(() -> Bukkit.dispatchCommand(any(), lines.capture())).thenReturn(true);

            test.run(sender, "HUNTER", List.of(line));

            return lines.getAllValues();
        }
    }
}
