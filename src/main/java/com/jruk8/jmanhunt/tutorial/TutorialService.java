package com.jruk8.jmanhunt.tutorial;

import com.jruk8.jmanhunt.tutorial.config.TutorialConfig;
import com.jruk8.jmanhunt.tutorial.ports.TutorialCommandRunner;
import com.jruk8.jmanhunt.tutorial.ports.TutorialLogger;
import com.jruk8.jmanhunt.tutorial.ports.TutorialMessenger;
import com.jruk8.jmanhunt.tutorial.ports.TutorialSoundPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Generic dialogue runner for the interactive setup. All text, branching,
 * and commands come from {@link TutorialConfig}; everything host-specific
 * (sending, sounds, commands, logging) goes through the ports. Only Bukkit
 * and Okaeri types are referenced, never host plugin classes, so this
 * package extracts to other plugins with new adapters.
 *
 * <p>Sessions are keyed by player and mutated on the main thread only. The
 * chat listener pre-check ({@link #isConsumableInput}) is the single async
 * reader; every other entry point runs sync.
 */
public final class TutorialService {

    /** Idle seconds before a session quits itself. */
    public static final float TIMEOUT_SECONDS = 300.0f;
    /** Node id the tutorial starts at. */
    public static final String START_NODE = "start";
    /** Answer target that closes the tutorial. */
    public static final String EXIT = "EXIT";
    /** Answer target that runs mh help, then closes the tutorial. */
    public static final String HELP_EXIT = "HELP_EXIT";
    private static final String HELP_COMMAND = "mh help";

    private final TutorialConfig config;
    private final TutorialMessenger messenger;
    private final TutorialSoundPlayer sounds;
    private final TutorialCommandRunner commands;
    private final TutorialLogger logger;
    private final Map<UUID, TutorialSession> sessions = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final Function<UUID, Player> players;

    public TutorialService(TutorialConfig config, TutorialMessenger messenger, TutorialSoundPlayer sounds,
                           TutorialCommandRunner commands, TutorialLogger logger) {
        this(config, messenger, sounds, commands, logger, System::currentTimeMillis, Bukkit::getPlayer);
    }

    TutorialService(TutorialConfig config, TutorialMessenger messenger, TutorialSoundPlayer sounds,
                    TutorialCommandRunner commands, TutorialLogger logger,
                    LongSupplier clock, Function<UUID, Player> players) {
        this.config = config;
        this.messenger = messenger;
        this.sounds = sounds;
        this.commands = commands;
        this.logger = logger;
        this.clock = clock;
        this.players = players;
    }

    public boolean isInTutorial(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /** Starts or restarts the tutorial for one player. */
    public void start(Player player) {
        TutorialSession session = new TutorialSession(player.getUniqueId(), START_NODE, clock.getAsLong());
        sessions.put(player.getUniqueId(), session);
        session.shown();
        if (!render(player, session)) {
            sessions.remove(player.getUniqueId());
            return;
        }
        sounds.playNeutral(player);
    }

    /** Drops a session without output, for disconnects. */
    public void removeOnQuit(UUID playerId) {
        sessions.remove(playerId);
    }

    /** How one chat line was handled. */
    public enum InputResult {
        /** Valid tutorial input; the chat line must not broadcast. */
        CONSUMED,
        /** Not tutorial input; broadcast it, the reminder is already sent. */
        PASS_THROUGH,
        /** Sender has no session; the line is untouched. */
        NOT_IN_TUTORIAL
    }

    /**
     * Async-safe pre-check for the chat listener: true when the trimmed
     * line is consumable tutorial input (a listed number, b, or q).
     */
    public boolean isConsumableInput(UUID playerId, String trimmed) {
        TutorialSession session = sessions.get(playerId);
        if (session == null) {
            return false;
        }
        if (trimmed.equalsIgnoreCase("q") || trimmed.equalsIgnoreCase("b")) {
            return true;
        }
        OptionalInt number = parseAnswerNumber(trimmed);
        if (number.isEmpty()) {
            return false;
        }
        TutorialConfig.TutorialNode node = node(session.current());
        return node != null && number.getAsInt() >= 1 && number.getAsInt() <= node.getAnswers().size();
    }

    /**
     * Handles one trimmed chat line from a player, on the main thread.
     * Valid input advances, goes back, or quits; anything else sends the
     * invalid-input reminder and passes through to chat.
     */
    public InputResult handleInput(Player player, String trimmed) {
        TutorialSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return InputResult.NOT_IN_TUTORIAL;
        }
        TutorialConfig.TutorialNode node = node(session.current());
        if (node == null) {
            return quitBroken(player, session);
        }
        if (trimmed.equalsIgnoreCase("q")) {
            quitWithMessage(player, session);
            return InputResult.CONSUMED;
        }
        if (trimmed.equalsIgnoreCase("b")) {
            if (session.depth() <= 1) {
                quitWithMessage(player, session);
            } else {
                session.pop();
                session.answered(clock.getAsLong());
                session.backed();
                render(player, session);
                sounds.playNeutral(player);
            }
            return InputResult.CONSUMED;
        }
        OptionalInt number = parseAnswerNumber(trimmed);
        if (number.isPresent() && number.getAsInt() >= 1 && number.getAsInt() <= node.getAnswers().size()) {
            answer(player, session, node.getAnswers().get(number.getAsInt() - 1));
            return InputResult.CONSUMED;
        }
        messenger.send(player, List.of(config.getMessages().getInvalid()));
        sounds.playAngry(player);
        return InputResult.PASS_THROUGH;
    }

    /** Quits sessions idle past the timeout. Runs on the main thread. */
    public void checkTimeouts() {
        long now = clock.getAsLong();
        long timeoutMillis = (long) (TIMEOUT_SECONDS * 1000.0f);
        for (TutorialSession session : sessions.values()) {
            if (now - session.lastAnswerMillis() < timeoutMillis) {
                continue;
            }
            sessions.remove(session.playerId());
            Player player = players.apply(session.playerId());
            if (player == null) {
                continue;
            }
            messenger.send(player, List.of(config.getMessages().getTimeout()));
            sounds.playAngry(player);
        }
    }

    /** Runs an answer's commands, then follows its target. */
    private void answer(Player player, TutorialSession session, TutorialConfig.TutorialAnswer answer) {
        for (String command : answer.getCommands()) {
            commands.runAsPlayer(player, command);
        }
        session.answered(clock.getAsLong());
        String next = answer.getNext();
        TutorialConfig.TutorialNode target = node(next);
        if (target != null && target.isCelebrate()) {
            sounds.playCongratulations(player);
        } else {
            sounds.playNeutral(player);
        }
        if (next == null || next.isBlank() || EXIT.equalsIgnoreCase(next)) {
            quitWithMessage(player, session);
        } else if (HELP_EXIT.equalsIgnoreCase(next)) {
            commands.runAsPlayer(player, HELP_COMMAND);
            sessions.remove(session.playerId());
        } else if (target == null) {
            logger.warning("Unknown tutorial target '" + next + "'; closing the setup.");
            quitWithMessage(player, session);
        } else if (session.revisit(next)) {
            render(player, session);
        } else {
            session.push(next);
            session.shown();
            render(player, session);
        }
    }

    private void quitWithMessage(Player player, TutorialSession session) {
        sessions.remove(session.playerId());
        messenger.send(player, List.of(config.getMessages().getQuit()));
        sounds.playNeutral(player);
    }

    private InputResult quitBroken(Player player, TutorialSession session) {
        logger.warning("Tutorial node '" + session.current() + "' is missing from tutorial.yml.");
        sessions.remove(session.playerId());
        messenger.send(player, List.of(config.getMessages().getBroken()));
        return InputResult.CONSUMED;
    }

    /**
     * Renders the session's current dialogue: a blank line, the header on
     * the first dialogue, the step-prefixed question, a separator,
     * numbered answers, the footer, and a trailing blank line. False when
     * the node is missing (broken message sent).
     */
    private boolean render(Player player, TutorialSession session) {
        TutorialConfig.TutorialNode node = node(session.current());
        if (node == null) {
            logger.warning("Tutorial node '" + session.current() + "' is missing from tutorial.yml.");
            messenger.send(player, List.of(config.getMessages().getBroken()));
            return false;
        }
        TutorialConfig.TutorialFormat format = config.getFormat();
        List<String> lines = new ArrayList<>();
        lines.add("");
        if (session.shownCount() == 1) {
            lines.addAll(format.getHeader());
            lines.add("");
        }
        List<String> question = node.getQuestion();
        for (int index = 0; index < question.size(); index++) {
            if (index == 0) {
                lines.add(format.getQuestion()
                        .replace("{step}", String.valueOf(session.shownCount()))
                        .replace("{question}", question.get(index)));
            } else {
                lines.add(question.get(index));
            }
        }
        lines.add(format.getSeparator());
        List<TutorialConfig.TutorialAnswer> answers = node.getAnswers();
        for (int index = 0; index < answers.size(); index++) {
            TutorialConfig.TutorialAnswer answer = answers.get(index);
            String recommendation = answer.isRecommended() ? format.getRecommendation() : "";
            String notrecommended = answer.isNotrecommended() ? format.getNotrecommended() : "";
            lines.add(format.getAnswer()
                    .replace("{number}", String.valueOf(index + 1))
                    .replace("{recommendation}", recommendation)
                    .replace("{notrecommended}", notrecommended)
                    .replace("{answer}", answer.getText()));
        }
        lines.add(format.getFooter());
        lines.add("");
        messenger.send(player, lines);
        return true;
    }

    private TutorialConfig.TutorialNode node(String id) {
        return id == null ? null : config.getNodes().get(id);
    }

    /** Digits-only answer number; empty for anything else. Pure for tests. */
    static OptionalInt parseAnswerNumber(String trimmed) {
        if (trimmed == null || trimmed.isEmpty()) {
            return OptionalInt.empty();
        }
        for (int index = 0; index < trimmed.length(); index++) {
            if (!Character.isDigit(trimmed.charAt(index))) {
                return OptionalInt.empty();
            }
        }
        try {
            return OptionalInt.of(Integer.parseInt(trimmed));
        } catch (NumberFormatException overflow) {
            return OptionalInt.empty();
        }
    }
}
