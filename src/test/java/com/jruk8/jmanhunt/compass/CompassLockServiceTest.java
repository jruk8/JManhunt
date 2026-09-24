package com.jruk8.jmanhunt.compass;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.GameMode;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class CompassLockServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void rapidClicksRefreshOncePerCooldown() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.compass.left-click.enabled", true);
        ConfigPathMapper.set(root, "settings.compass.left-click.scroll-cooldown", 10.0);
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService configService = new ConfigService(root,
                new ModifierStore(new ModifiersConfig(), log));
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.configService()).thenReturn(configService);
        UUID holderId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(holderId);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        PlayerStateStore playerStates = new PlayerStateStore();
        playerStates.setRole(player, Role.HUNTER);
        GameInstance instance = mock(GameInstance.class);
        GameManager game = mock(GameManager.class);
        when(game.instanceOf(holderId)).thenReturn(Optional.of(instance));
        CompassTargetService targets = mock(CompassTargetService.class);
        when(targets.collectOpponents(any(), any(), any())).thenReturn(List.of());
        when(targets.collectSightings(any(), any(), any())).thenReturn(List.of());
        Consumer<Player> refresher = mock(Consumer.class);
        CompassLockService locks = new CompassLockService(plugin, playerStates, null, null,
                targets, mock(CompassSignalService.class), new HashMap<>(), refresher);
        locks.setGameManager(game);

        locks.handleLeftClick(player);
        locks.handleLeftClick(player);

        verify(refresher, times(1)).accept(player);
    }
}
