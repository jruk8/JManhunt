package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Match context for modifier tag evaluation: who counts as a participant,
 * which random source to draw from, and where tag warnings go. Executor-only
 * scopes (console commands outside a match, compass debuffs) carry no
 * participants, so {@code <all-players>} and {@code <random-player>} fall
 * back to the executor instead of leaking across matches.
 */
public final class ModifierTagScope {
    /** One in-match player: console-safe name plus upper-case team name. */
    public record Participant(String name, String team) {
    }

    private final String executorName;
    private final List<Participant> participants;
    private final Random random;
    private final Consumer<String> warnings;

    private ModifierTagScope(String executorName, List<Participant> participants,
            Random random, Consumer<String> warnings) {
        this.executorName = executorName;
        this.participants = List.copyOf(participants);
        this.random = random;
        this.warnings = warnings;
    }

    /** Scope without participants: player tags fall back to the executor. */
    public static ModifierTagScope executor(String executorName, Consumer<String> warnings) {
        return new ModifierTagScope(executorName, List.of(), new Random(), warnings);
    }

    /** Scope for one match activation covering the given participants. */
    public static ModifierTagScope match(String executorName, List<Participant> participants,
            Random random, Consumer<String> warnings) {
        return new ModifierTagScope(executorName, participants, random, warnings);
    }

    /** Executor name, or null for console dispatch. */
    public String executorName() {
        return executorName;
    }

    public List<Participant> participants() {
        return participants;
    }

    public Random random() {
        return random;
    }

    public void warn(String message) {
        warnings.accept(message);
    }

    /** Random participant name, or empty when the scope holds nobody. */
    public Optional<String> randomParticipant() {
        if (participants.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(participants.get(random.nextInt(participants.size())).name());
    }

    /**
     * Participant names, narrowed to one team when {@code team} names a
     * role (case-insensitive). A null or blank team returns everyone.
     */
    public List<String> participantNames(String team) {
        if (team == null || team.isBlank()) {
            return participants.stream().map(Participant::name).toList();
        }
        String wanted = team.trim();
        return participants.stream()
                .filter(candidate -> candidate.team().equalsIgnoreCase(wanted))
                .map(Participant::name)
                .toList();
    }
}
