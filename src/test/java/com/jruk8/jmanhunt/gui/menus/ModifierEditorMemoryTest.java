package com.jruk8.jmanhunt.gui.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.gui.dialog.ModifierDialog;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Test-a-Command memory: remembered prefills, defaults, failure fallbacks. */
class ModifierEditorMemoryTest {

    @Test
    void initialForPrefillsRememberedRows() throws Exception {
        EngineStateRepository repository = mock(EngineStateRepository.class);
        UUID id = UUID.randomUUID();
        when(repository.getEditorMemory(id)).thenReturn(Optional.of(
                new EngineStateRepository.EditorMemory("hunter", true,
                        List.of("say one", "", "", "", ""))));
        ModifierEditorMemory memory = new ModifierEditorMemory(repository,
                mock(JManhuntLogger.class), () -> false);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);

        assertEquals(new ModifierDialog.TestSubmission(true, "HUNTER",
                List.of("say one", "", "", "", "")), memory.initialFor(player));
    }

    @Test
    void initialForSkipsUnrememberedRows() throws Exception {
        EngineStateRepository repository = mock(EngineStateRepository.class);
        UUID id = UUID.randomUUID();
        when(repository.getEditorMemory(id)).thenReturn(Optional.of(
                new EngineStateRepository.EditorMemory("HUNTER", false,
                        List.of("say one", "", "", "", ""))));
        ModifierEditorMemory memory = new ModifierEditorMemory(repository,
                mock(JManhuntLogger.class), () -> true);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);

        assertEquals(new ModifierDialog.TestSubmission(true, "SPEEDRUNNER",
                List.of("", "", "", "", "")), memory.initialFor(player));
    }

    @Test
    void initialForFallsBackOnMissingRowsAndFailures() throws Exception {
        EngineStateRepository repository = mock(EngineStateRepository.class);
        UUID missing = UUID.randomUUID();
        UUID broken = UUID.randomUUID();
        when(repository.getEditorMemory(missing)).thenReturn(Optional.empty());
        when(repository.getEditorMemory(broken)).thenThrow(new SQLException("down"));
        JManhuntLogger logger = mock(JManhuntLogger.class);
        ModifierEditorMemory memory = new ModifierEditorMemory(repository, logger, () -> false);
        Player absent = mock(Player.class);
        when(absent.getUniqueId()).thenReturn(missing);
        Player failing = mock(Player.class);
        when(failing.getUniqueId()).thenReturn(broken);

        ModifierDialog.TestSubmission defaults = new ModifierDialog.TestSubmission(false,
                "SPEEDRUNNER", List.of("", "", "", "", ""));
        assertEquals(defaults, memory.initialFor(absent));
        assertEquals(defaults, memory.initialFor(failing));
        verify(logger).warning(anyString());
    }

    @Test
    void initialForToleratesNullRepository() {
        ModifierEditorMemory memory = new ModifierEditorMemory(null,
                mock(JManhuntLogger.class), () -> false);

        assertEquals(new ModifierDialog.TestSubmission(false, "SPEEDRUNNER",
                List.of("", "", "", "", "")), memory.initialFor(mock(Player.class)));
    }

    @Test
    void storeWritesLatestSubmit() throws Exception {
        EngineStateRepository repository = mock(EngineStateRepository.class);
        ModifierEditorMemory memory = new ModifierEditorMemory(repository,
                mock(JManhuntLogger.class), () -> false);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        ModifierDialog.TestSubmission submission = new ModifierDialog.TestSubmission(true,
                "HUNTER", List.of("say one", "", "", "", ""));

        memory.store(player, submission);

        verify(repository).putEditorMemory(eq(id),
                eq(new EngineStateRepository.EditorMemory("HUNTER", true,
                        List.of("say one", "", "", "", ""))));
    }

    @Test
    void storeLogsAndSkipsOnFailure() throws Exception {
        EngineStateRepository repository = mock(EngineStateRepository.class);
        JManhuntLogger logger = mock(JManhuntLogger.class);
        doThrow(new SQLException("down")).when(repository).putEditorMemory(any(), any());
        ModifierEditorMemory memory = new ModifierEditorMemory(repository, logger, () -> false);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        memory.store(player, new ModifierDialog.TestSubmission(false, "SPEEDRUNNER",
                List.of("", "", "", "", "")));

        verify(logger).warning(anyString());
    }
}
