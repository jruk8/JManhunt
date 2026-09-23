package com.jruk8.jmanhunt.tutorial;

import com.jruk8.jmanhunt.tutorial.config.TutorialConfig;
import com.jruk8.jmanhunt.tutorial.ports.TutorialCommandRunner;
import com.jruk8.jmanhunt.tutorial.ports.TutorialLogger;
import com.jruk8.jmanhunt.tutorial.ports.TutorialMessenger;
import com.jruk8.jmanhunt.tutorial.ports.TutorialSoundPlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TutorialServiceTest {

    @Mock
    private Player player;

    private final UUID playerId = UUID.randomUUID();
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final List<List<String>> sent = new ArrayList<>();
    private final List<String> ran = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private int neutralPlays;
    private int angryPlays;
    private int congratulationsPlays;
    private TutorialConfig config;
    private TutorialService tutorial;

    @BeforeEach
    void setUp() {
        lenient().when(player.getUniqueId()).thenReturn(playerId);
        config = new TutorialConfig();
        config.getNodes().put("start", node(
                List.of("First?"),
                List.of(answer("Go on.", "second", List.of("say hi"), false),
                        answer("Leave.", "EXIT", List.of(), false))));
        config.getNodes().put("second", node(
                List.of("Second?"),
                List.of(answer("Back out.", "EXIT", List.of(), true))));
        TutorialMessenger messenger = (target, lines) -> sent.add(new ArrayList<>(lines));
        TutorialSoundPlayer sounds = new TutorialSoundPlayer() {
            @Override
            public void playNeutral(Player target) {
                neutralPlays++;
            }

            @Override
            public void playAngry(Player target) {
                angryPlays++;
            }

            @Override
            public void playCongratulations(Player target) {
                congratulationsPlays++;
            }
        };
        TutorialCommandRunner commands = (target, command) -> ran.add(command);
        TutorialLogger logger = new TutorialLogger() {
            @Override
            public void info(String message) {
            }

            @Override
            public void warning(String message) {
                warnings.add(message);
            }
        };
        tutorial = new TutorialService(config, messenger, sounds, commands, logger,
                clock::get, Map.of(playerId, player)::get);
    }

    @Test
    void startRendersFirstDialogue() {
        tutorial.start(player);

        assertTrue(tutorial.isInTutorial(playerId));
        assertEquals(1, sent.size());
        assertEquals(1, neutralPlays);
        List<String> lines = sent.get(0);
        assertEquals("", lines.get(0));
        assertTrue(lines.get(1).contains("Welcome to the interactive setup"));
        assertTrue(lines.contains(stepLine(1, "First?")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("1 <green>")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("2 <green>")));
        assertTrue(lines.contains(config.getFormat().getSeparator()));
        assertEquals(config.getFormat().getFooter(), lines.get(lines.size() - 2));
        assertEquals("", lines.get(lines.size() - 1));
    }

    @Test
    void validAnswerRunsCommandsAndAdvances() {
        tutorial.start(player);

        assertEquals(TutorialService.InputResult.CONSUMED, tutorial.handleInput(player, "1"));

        assertEquals(List.of("say hi"), ran);
        assertEquals(2, neutralPlays);
        assertTrue(tutorial.isInTutorial(playerId));
        assertEquals(2, sent.size());
        assertTrue(sent.get(1).contains(stepLine(2, "Second?")));
        assertTrue(sent.get(1).stream().anyMatch(line -> line.contains("(recommended)")));
    }

    @Test
    void invalidInputSendsReminderAndPassesThrough() {
        tutorial.start(player);

        assertEquals(TutorialService.InputResult.PASS_THROUGH, tutorial.handleInput(player, "hello"));

        assertEquals(1, angryPlays);
        assertTrue(tutorial.isInTutorial(playerId));
        assertEquals(List.of(config.getMessages().getInvalid()), sent.get(sent.size() - 1));
    }

    @Test
    void outOfRangeNumberPassesThrough() {
        tutorial.start(player);

        assertFalse(tutorial.isConsumableInput(playerId, "9"));
        assertEquals(TutorialService.InputResult.PASS_THROUGH, tutorial.handleInput(player, "9"));
        assertTrue(tutorial.isInTutorial(playerId));
    }

    @Test
    void backGoesToPreviousNode() {
        tutorial.start(player);
        tutorial.handleInput(player, "1");

        assertEquals(TutorialService.InputResult.CONSUMED, tutorial.handleInput(player, "b"));

        assertTrue(tutorial.isInTutorial(playerId));
        assertTrue(sent.get(sent.size() - 1).contains(stepLine(1, "First?")));
    }

    @Test
    void revisitingStartRewindsStepAndWipesHistory() {
        config.getNodes().get("second").getAnswers()
                .add(answer("Menu.", "start", List.of(), false));
        tutorial.start(player);
        tutorial.handleInput(player, "1");

        tutorial.handleInput(player, "2");

        assertTrue(sent.get(sent.size() - 1).contains(stepLine(1, "First?")));
        tutorial.handleInput(player, "b");
        assertFalse(tutorial.isInTutorial(playerId));
    }

    @Test
    void revisitingMiddleNodeKeepsEarlierHistory() {
        config.getNodes().put("third", node(List.of("Third?"),
                List.of(answer("To second.", "second", List.of(), false))));
        config.getNodes().get("second").getAnswers()
                .add(answer("Deeper.", "third", List.of(), false));
        tutorial.start(player);
        tutorial.handleInput(player, "1");
        tutorial.handleInput(player, "2");

        tutorial.handleInput(player, "1");

        assertTrue(sent.get(sent.size() - 1).contains(stepLine(2, "Second?")));
        tutorial.handleInput(player, "b");
        assertTrue(tutorial.isInTutorial(playerId));
        assertTrue(sent.get(sent.size() - 1).contains(stepLine(1, "First?")));
    }

    @Test
    void sameNodeLoopKeepsStepNumber() {
        config.getNodes().put("loop", node(List.of("Loop?"),
                List.of(answer("Again.", "loop", List.of(), false),
                        answer("Out.", "EXIT", List.of(), false))));
        config.getNodes().get("start").getAnswers()
                .add(answer("Loop.", "loop", List.of(), false));
        tutorial.start(player);
        tutorial.handleInput(player, "3");

        tutorial.handleInput(player, "1");

        assertTrue(sent.get(sent.size() - 1).contains(stepLine(2, "Loop?")));
    }

    @Test
    void celebrateNodePlaysCongratulations() {
        config.getNodes().get("second").setCelebrate(true);
        tutorial.start(player);

        assertEquals(TutorialService.InputResult.CONSUMED, tutorial.handleInput(player, "1"));

        assertEquals(1, congratulationsPlays);
        assertEquals(1, neutralPlays);
        assertTrue(tutorial.isInTutorial(playerId));
    }

    @Test
    void notrecommendedTagRendersOnAnswer() {
        config.getNodes().put("start", node(List.of("First?"),
                List.of(answer("Risky.", "EXIT", List.of(), false))));
        config.getNodes().get("start").getAnswers().get(0).setNotrecommended(true);
        tutorial.start(player);

        assertTrue(sent.get(0).stream().anyMatch(line -> line.contains("(not recommended)")));
    }

    @Test
    void backAtRootQuits() {
        tutorial.start(player);

        assertEquals(TutorialService.InputResult.CONSUMED, tutorial.handleInput(player, "b"));

        assertFalse(tutorial.isInTutorial(playerId));
        assertEquals(List.of(config.getMessages().getQuit()), sent.get(sent.size() - 1));
    }

    @Test
    void quitClosesSession() {
        tutorial.start(player);

        assertEquals(TutorialService.InputResult.CONSUMED, tutorial.handleInput(player, "Q"));

        assertFalse(tutorial.isInTutorial(playerId));
        assertEquals(List.of(config.getMessages().getQuit()), sent.get(sent.size() - 1));
    }

    @Test
    void exitAnswerClosesSession() {
        tutorial.start(player);

        assertEquals(TutorialService.InputResult.CONSUMED, tutorial.handleInput(player, "2"));

        assertFalse(tutorial.isInTutorial(playerId));
        assertEquals(List.of(config.getMessages().getQuit()), sent.get(sent.size() - 1));
    }

    @Test
    void helpExitRunsHelpAndCloses() {
        config.getNodes().put("start", node(List.of("First?"),
                List.of(answer("Learn.", "HELP_EXIT", List.of(), false))));
        tutorial.start(player);

        assertEquals(TutorialService.InputResult.CONSUMED, tutorial.handleInput(player, "1"));

        assertEquals(List.of("mh help"), ran);
        assertFalse(tutorial.isInTutorial(playerId));
    }

    @Test
    void unknownTargetClosesSessionWithWarning() {
        config.getNodes().put("start", node(List.of("First?"),
                List.of(answer("Broken.", "nope", List.of(), false))));
        tutorial.start(player);

        assertEquals(TutorialService.InputResult.CONSUMED, tutorial.handleInput(player, "1"));

        assertFalse(tutorial.isInTutorial(playerId));
        assertEquals(1, warnings.size());
    }

    @Test
    void timeoutQuitsIdleSession() {
        tutorial.start(player);
        clock.addAndGet(301_000L);

        tutorial.checkTimeouts();

        assertFalse(tutorial.isInTutorial(playerId));
        assertEquals(List.of(config.getMessages().getTimeout()), sent.get(sent.size() - 1));
        assertEquals(1, angryPlays);
    }

    @Test
    void recentAnswerSurvivesTimeoutCheck() {
        tutorial.start(player);
        clock.addAndGet(200_000L);
        tutorial.handleInput(player, "1");
        clock.addAndGet(200_000L);

        tutorial.checkTimeouts();

        assertTrue(tutorial.isInTutorial(playerId));
    }

    @Test
    void missingStartNodeSendsBroken() {
        config.getNodes().clear();

        tutorial.start(player);

        assertFalse(tutorial.isInTutorial(playerId));
        assertEquals(List.of(config.getMessages().getBroken()), sent.get(0));
    }

    @Test
    void consumableInputMatchesListedNumbersAndKeys() {
        tutorial.start(player);

        assertTrue(tutorial.isConsumableInput(playerId, "1"));
        assertTrue(tutorial.isConsumableInput(playerId, "2"));
        assertTrue(tutorial.isConsumableInput(playerId, "b"));
        assertTrue(tutorial.isConsumableInput(playerId, "Q"));
        assertFalse(tutorial.isConsumableInput(playerId, "3"));
        assertFalse(tutorial.isConsumableInput(playerId, "1 please"));
        assertFalse(tutorial.isConsumableInput(UUID.randomUUID(), "1"));
    }

    @Test
    void parseAnswerNumberAcceptsDigitsOnly() {
        assertEquals(OptionalInt.of(1), TutorialService.parseAnswerNumber("1"));
        assertEquals(OptionalInt.of(12), TutorialService.parseAnswerNumber("12"));
        assertEquals(OptionalInt.empty(), TutorialService.parseAnswerNumber("1a"));
        assertEquals(OptionalInt.empty(), TutorialService.parseAnswerNumber(""));
        assertEquals(OptionalInt.empty(), TutorialService.parseAnswerNumber("9999999999999999999999"));
    }

    private static TutorialConfig.TutorialNode node(List<String> question,
            List<TutorialConfig.TutorialAnswer> answers) {
        TutorialConfig.TutorialNode node = new TutorialConfig.TutorialNode();
        node.setQuestion(new ArrayList<>(question));
        node.setAnswers(new ArrayList<>(answers));
        return node;
    }

    private String stepLine(int step, String question) {
        return config.getFormat().getQuestion()
                .replace("{step}", String.valueOf(step))
                .replace("{question}", question);
    }

    private static TutorialConfig.TutorialAnswer answer(String text, String next, List<String> commands,
            boolean recommended) {
        TutorialConfig.TutorialAnswer answer = new TutorialConfig.TutorialAnswer();
        answer.setText(text);
        answer.setNext(next);
        answer.setCommands(new ArrayList<>(commands));
        answer.setRecommended(recommended);
        return answer;
    }
}
