package com.jruk8.jmanhunt.command;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.Setter;

/**
 * Evaluation context for extended tags: the match scope plus the
 * container id ({@code <id>}), the message/sound sinks, and the
 * stat/flag backends. Managers build one per dispatch run with
 * {@link #run}; the parser stays pure behind it.
 */
public final class TagContext {

    /** Match id for runs outside any match: stats miss, flags gate. */
    public static final long NO_MATCH = -1L;

    /** Plays one sound for its audience. */
    public interface SoundSink {
        void play(String soundId, float pitch, float volume);
    }

    /** Plays one sound for the members of the named upper-case role. */
    public interface RoleSoundSink {
        void play(String role, String soundId, float pitch, float volume);
    }

    /**
     * Where one tag line comes from: the modifier id, the behavior
     * index in that modifier, the command list name, and the 0-based
     * line index in that list. Managers stamp the current line before
     * each evaluation; loop-limit and null-line diagnostics read it.
     * Non-modifier runs (compass debuffs) use a negative behavior
     * index and the dispatch name for both id and list.
     */
    public record Provenance(String modifierId, int behaviorIndex, String listName, int lineIndex) {
        /** Base provenance before any line runs: line unknown. */
        public static Provenance of(String modifierId, int behaviorIndex, String listName) {
            return new Provenance(modifierId, behaviorIndex, listName, -1);
        }

        /** Same source with the 0-based line index filled in. */
        public Provenance withLine(int lineIndex) {
            return new Provenance(modifierId, behaviorIndex, listName, lineIndex);
        }

        /**
         * One-line source for severe logs: the behavior part drops
         * out for non-modifier runs.
         */
        public String describe() {
            if (behaviorIndex < 0) {
                return "modifier '" + modifierId + "', list '" + listName
                        + "', line " + lineIndex + " (0-based)";
            }
            return "modifier '" + modifierId + "', behavior " + behaviorIndex
                    + ", list '" + listName + "', line " + lineIndex + " (0-based)";
        }
    }

    private final ModifierTagScope scope;
    private final String containerId;
    private final Consumer<String> globalMessage;
    private final Consumer<String> playerMessage;
    private final SoundSink globalSound;
    private final SoundSink playerSound;
    private final BiConsumer<String, String> losePlayer;
    private final BiConsumer<String, String> winMatch;
    private final BiConsumer<String, String> switchRole;
    private final long matchId;
    private final TagBackends backends;
    private final List<String> eventArgs;
    private final Map<String, String> localFlags;
    private final Map<String, TagFunctions.Definition> functions = new HashMap<>();
    private final Deque<String> loopItems;
    private final Consumer<String> loopLimit;
    private final BiConsumer<String, String> roleMessage;
    private final RoleSoundSink roleSound;
    private final BiConsumer<String, String> commandRun;
    /** Stamps the source line; managers call this per line. */
    @Setter
    private Provenance provenance;
    /**
     * Points this run at the shared match store; managers call this
     * per run. Fresh contexts start with a private empty store.
     */
    @Setter
    private TagCooldownStore cooldowns = new TagCooldownStore();
    private int stepBudget = TagLoops.LOOP_LIMIT;
    private boolean limitFired;

    /** Who evaluates plus the container id ({@code <id>}). */
    public record TagIdentity(ModifierTagScope scope, String containerId) {
    }

    /** Message, sound, and command sinks behind the tag lines. */
    public record TagSinks(Consumer<String> globalMessage, Consumer<String> playerMessage,
            SoundSink globalSound, SoundSink playerSound,
            BiConsumer<String, String> commandRun) {
        /** Sinks with the warn-only command fallback for the scope. */
        public static TagSinks simple(Consumer<String> globalMessage,
                Consumer<String> playerMessage, SoundSink globalSound,
                SoundSink playerSound, ModifierTagScope scope) {
            return new TagSinks(globalMessage, playerMessage, globalSound, playerSound,
                    defaultCommandRun(scope));
        }
    }

    /** Role sinks behind {@code <rmessage>} and {@code <rsound>}. */
    public record TagRole(BiConsumer<String, String> roleMessage, RoleSoundSink roleSound) {
        /** Silent role sinks. */
        public static TagRole silent() {
            return new TagRole((role, text) -> { }, (role, id, pitch, volume) -> { });
        }
    }

    /** Match id, backends, event args, limits, and outcomes. */
    public record TagMatch(long matchId, TagBackends backends, List<String> eventArgs,
            Consumer<String> loopLimit, BiConsumer<String, String> losePlayer,
            BiConsumer<String, String> winMatch, BiConsumer<String, String> switchRole) {
        /** Match with empty args and a silent loop sink. */
        public static TagMatch simple(long matchId, TagBackends backends,
                BiConsumer<String, String> losePlayer,
                BiConsumer<String, String> winMatch) {
            return simple(matchId, backends, losePlayer, winMatch, (player, role) -> { });
        }

        /** Match with empty args, a silent loop sink, and a role switch sink. */
        public static TagMatch simple(long matchId, TagBackends backends,
                BiConsumer<String, String> losePlayer,
                BiConsumer<String, String> winMatch,
                BiConsumer<String, String> switchRole) {
            return new TagMatch(matchId, backends, List.of(), detail -> { }, losePlayer,
                    winMatch, switchRole);
        }
    }

    private TagContext(TagIdentity identity, TagSinks sinks, TagRole role, TagMatch match) {
        this.scope = identity.scope();
        this.containerId = identity.containerId();
        this.globalMessage = sinks.globalMessage();
        this.playerMessage = sinks.playerMessage();
        this.globalSound = sinks.globalSound();
        this.playerSound = sinks.playerSound();
        this.losePlayer = match.losePlayer();
        this.winMatch = match.winMatch();
        this.switchRole = match.switchRole();
        this.matchId = match.matchId();
        this.backends = match.backends();
        this.eventArgs = List.copyOf(match.eventArgs());
        this.localFlags = new HashMap<>();
        this.loopItems = new ArrayDeque<>();
        this.loopLimit = match.loopLimit();
        this.roleMessage = role.roleMessage();
        this.roleSound = role.roleSound();
        this.commandRun = sinks.commandRun();
        this.provenance = Provenance.of(identity.containerId(), -1, "");
    }

    /**
     * Full run context for one modifier or debuff dispatch: identity,
     * sinks, role sinks, and match. Event args copy and freeze; the
     * {@code <lflag>} map is fresh per run.
     */
    public static TagContext run(TagIdentity identity, TagSinks sinks, TagRole role,
            TagMatch match) {
        return new TagContext(identity, sinks, role, match);
    }

    /** Warns that {@code <run>} only dispatches inside wired runs. */
    private static BiConsumer<String, String> defaultCommandRun(ModifierTagScope scope) {
        return (line, provenance) -> scope.warn("Tag <run> only dispatches inside modifier runs: "
                + line);
    }

    /** Full context for one modifier or debuff dispatch outside any match. */
    public static TagContext of(TagIdentity identity, Consumer<String> globalMessage,
            Consumer<String> playerMessage, SoundSink globalSound, SoundSink playerSound) {
        return new TagContext(identity,
                TagSinks.simple(globalMessage, playerMessage, globalSound, playerSound,
                        identity.scope()),
                TagRole.silent(),
                TagMatch.simple(NO_MATCH, TagBackends.inert(), (player, reason) -> { },
                        (role, reason) -> { }));
    }

    /** Inert context for scope-only callers: empty id, silent sinks. */
    public static TagContext inert(ModifierTagScope scope) {
        return new TagContext(new TagIdentity(scope, ""),
                TagSinks.simple(text -> { }, text -> { },
                        (id, pitch, volume) -> { }, (id, pitch, volume) -> { }, scope),
                TagRole.silent(),
                TagMatch.simple(NO_MATCH, TagBackends.inert(), (player, reason) -> { },
                        (role, reason) -> { }));
    }

    public ModifierTagScope scope() {
        return scope;
    }

    /** Cooldown stamps behind the {@code pcooldown} and {@code gcooldown} tags. */
    public TagCooldownStore cooldowns() {
        return cooldowns;
    }

    public String containerId() {
        return containerId;
    }

    /** Match backing stats and flags, or {@link #NO_MATCH}. */
    public long matchId() {
        return matchId;
    }

    /** Stat values behind {@code <pstat>} and {@code <gstat>}. */
    public StatValues statValues() {
        return backends.stats();
    }

    /** Shared store behind {@code <gflag>} and {@code <pflag>}. */
    public FlagStore flagStore() {
        return backends.flags();
    }

    /** Placeholder expansion behind raw spans and {@code <placeholder>}. */
    public PlaceholderResolver placeholders() {
        return backends.placeholders();
    }

    /** This run's {@code <lflag>} map, discarded after dispatch. */
    public Map<String, String> localFlags() {
        return localFlags;
    }

    /** This run's {@code <def>} table, discarded after dispatch like lflag. */
    public Map<String, TagFunctions.Definition> functions() {
        return functions;
    }

    /** Restores the per-line step budget; evaluation entry calls this per line. */
    public void resetStepBudget() {
        stepBudget = TagLoops.LOOP_LIMIT;
        limitFired = false;
    }

    /**
     * Consumes one shared line step (a loop iteration or a function
     * call); false once exhausted. Monotonic within the line: loop
     * exits and returns never refund, so recursion cannot launder
     * the budget.
     */
    public boolean tryConsumeStep() {
        if (stepBudget <= 0) {
            return false;
        }
        stepBudget--;
        return true;
    }

    /** Match roster reads for roster tags. */
    public RosterValues roster() {
        return backends.roster();
    }

    /** Named-player delivery behind {@code <pmessage>} and {@code <psound>}. */
    public PlayerSinks playerSinks() {
        return backends.players();
    }

    /** This trigger's event args behind {@code <args:index>}. */
    public List<String> eventArgs() {
        return eventArgs;
    }

    /** Source line stamped by the manager before each evaluation. */
    public Provenance provenance() {
        return provenance;
    }

    /** Pushes one for-loop item; nested loops see the innermost. */
    public void pushLoopItem(String item) {
        loopItems.push(item);
    }

    /** Pops one for-loop item; callers pair pushes with pops. */
    public void popLoopItem() {
        loopItems.pop();
    }

    /** Innermost for-loop item, or empty outside any for loop. */
    public Optional<String> loopItem() {
        return loopItems.isEmpty() ? Optional.empty() : Optional.of(loopItems.peek());
    }

    /**
     * Fires the loop-limit response behind an over-step loop or call.
     * Once per line: cascading failures still resolve "null" each, but
     * the match hears about the line only once.
     */
    public void loopLimitExceeded(String detail) {
        if (limitFired) {
            return;
        }
        limitFired = true;
        loopLimit.accept(detail);
    }

    public void sendGlobalMessage(String text) {
        globalMessage.accept(text);
    }

    public void sendPlayerMessage(String text) {
        playerMessage.accept(text);
    }

    /**
     * Sends one message to the named role members. The role is the
     * canonical upper-case name.
     */
    public void sendRoleMessage(String role, String text) {
        roleMessage.accept(role, text);
    }

    /**
     * Dispatches one {@code <run>} line through the wired command
     * sink, which reports under the given provenance.
     */
    public void runCommand(String line, String provenance) {
        commandRun.accept(line, provenance);
    }

    public void playGlobalSound(String soundId, float pitch, float volume) {
        globalSound.play(soundId, pitch, volume);
    }

    public void playPlayerSound(String soundId, float pitch, float volume) {
        playerSound.play(soundId, pitch, volume);
    }

    /**
     * Plays one sound for the named role members. The role is the
     * canonical upper-case name.
     */
    public void playRoleSound(String role, String soundId, float pitch, float volume) {
        roleSound.play(role, soundId, pitch, volume);
    }

    /** Eliminates one player by name, behind {@code <loseplayer>}. */
    public void losePlayer(String playerName, String reason) {
        losePlayer.accept(playerName, reason);
    }

    /**
     * Ends the match for one role, behind {@code <win:ROLE,reason>}.
     * The role is the canonical upper-case name.
     */
    public void winMatch(String role, String reason) {
        winMatch.accept(role, reason);
    }

    /**
     * Switches one player to the named role, behind
     * {@code <pswitch:player,ROLE>}. The role is the canonical
     * upper-case name.
     */
    public void switchPlayerRole(String playerName, String role) {
        switchRole.accept(playerName, role);
    }
}
