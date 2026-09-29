package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.gui.dialog.DialogInputs;
import com.jruk8.jmanhunt.gui.dialog.ModifierDialog;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;

/**
 * Test-a-Command dialog memory: per-player role, remember toggle,
 * and command boxes persisted in engine.db. Prefills read the stored
 * row only when its remember toggle is on, falling back to the
 * configured default plus blanks; database failures fall back the
 * same way and log once. Writes always store the latest submit.
 */
public final class ModifierEditorMemory {

    private final EngineStateRepository repository;
    private final JManhuntLogger logger;
    private final BooleanSupplier rememberDefault;

    public ModifierEditorMemory(EngineStateRepository repository, JManhuntLogger logger,
            BooleanSupplier rememberDefault) {
        this.repository = repository;
        this.logger = logger;
        this.rememberDefault = rememberDefault;
    }

    /**
     * Dialog initials for one player: the remembered row when its
     * toggle is on, else the configured default with blanks.
     */
    public ModifierDialog.TestSubmission initialFor(Player player) {
        Optional<EngineStateRepository.EditorMemory> stored = read(player);
        if (stored.isPresent() && stored.get().remember()) {
            return new ModifierDialog.TestSubmission(true,
                    DialogInputs.parseTestRole(stored.get().role()),
                    List.copyOf(stored.get().commands()));
        }
        return new ModifierDialog.TestSubmission(rememberDefault.getAsBoolean(), "SPEEDRUNNER",
                List.of("", "", "", "", ""));
    }

    /** Stores the latest submit for one player; failures log and skip. */
    public void store(Player player, ModifierDialog.TestSubmission submission) {
        if (repository == null) {
            return;
        }
        try {
            repository.putEditorMemory(player.getUniqueId(),
                    new EngineStateRepository.EditorMemory(submission.role(),
                            submission.remember(), submission.commands()));
        } catch (SQLException failed) {
            logger.warning("Could not store the Test-a-Command memory: " + failed.getMessage());
        }
    }

    private Optional<EngineStateRepository.EditorMemory> read(Player player) {
        if (repository == null) {
            return Optional.empty();
        }
        try {
            return repository.getEditorMemory(player.getUniqueId());
        } catch (SQLException failed) {
            logger.warning("Could not read the Test-a-Command memory: " + failed.getMessage());
            return Optional.empty();
        }
    }
}
