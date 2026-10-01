package com.jruk8.jmanhunt.tutorial.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.Header;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Interactive setup tutorial: dialogue format, sounds, fixed messages, and
 * the node graph (questions plus answers with branching). Field names stay
 * single lowercase words so the yaml keys never depend on a naming
 * strategy. Dialogue content lives in Core/tutorial.yml; only the short
 * format, sound, and message defaults are duplicated here so a damaged
 * file still renders safe fallbacks.
 */
@SuppressWarnings("FieldMayBeFinal")
@Header({
        "JManhunt interactive setup tutorial (/mh setup).",
        "",
        "Nodes form a graph: every answer names the next node id, EXIT to",
        "close the tutorial, or HELP_EXIT to run mh help and then close.",
        "Going back is automatic from the visit stack; 'b' on the first",
        "dialogue quits like 'q'."
})
@Getter
@Setter
public class TutorialConfig extends OkaeriConfig {

    @Comment("Dialogue line templates and the recommendation tag.")
    private TutorialFormat format = new TutorialFormat();

    @Comment("Path-completion feedback sound. Neutral and angry feedback use sounds.yml ui sounds.")
    private TutorialSounds sounds = new TutorialSounds();

    @Comment("Fixed engine messages.")
    private TutorialMessages messages = new TutorialMessages();

    @Comment("Dialogue nodes by id. The tutorial starts at 'start'.")
    private Map<String, TutorialNode> nodes = new LinkedHashMap<>();

    /** Dialogue line templates. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class TutorialFormat extends OkaeriConfig {

        @Comment("First-dialogue header. {logo} is the prefix minus brackets.")
        private List<String> header = new ArrayList<>(List.of(
                "<white>Welcome to the interactive setup for {logo}.",
                "This system aims to help you quickly start using the plugin."));

        @Comment("First question line. {step} is the dialogue position.")
        private String question = "<#de7766>{step}.</#de7766> {question}";

        @Comment("One answer line per option.")
        private String answer = "{number} <green>»</green> {recommendation}{notrecommended}{answer}";

        @Comment("Line drawn between the question and the answers.")
        private String separator = "<gray>--**--**--**--**--**--**--**--**--</gray>";

        @Comment("Footer shown under every dialogue.")
        private String footer = "<gray>(type answer number to continue, b to go back, or q to quit)";

        @Comment("Inserted as {recommendation} for recommended answers.")
        private String recommendation = "<yellow>(recommended)</yellow> ";

        @Comment("Inserted as {notrecommended} for discouraged answers.")
        private String notrecommended = "<red>(not recommended)</red> ";

    }

    /** One feedback sound. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class TutorialSound extends OkaeriConfig {

        private boolean enabled = true;
        private String id = "block.note_block.pling";
        private float volume = 1.0f;
        private float pitch = 1.0f;

        public static TutorialSound of(String id) {
            return of(id, 1.0f, 1.0f);
        }

        public static TutorialSound of(String id, float volume, float pitch) {
            TutorialSound sound = new TutorialSound();
            sound.setId(id);
            sound.setVolume(volume);
            sound.setPitch(pitch);
            return sound;
        }

    }

    /** Path-completion feedback sound. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class TutorialSounds extends OkaeriConfig {

        @Comment("Heard when a path completes.")
        private TutorialSound congratulations =
                TutorialSound.of("entity.player.levelup", 1.0f, 0.8f);

    }

    /** Fixed engine messages. {prefix} resolves in the adapter. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class TutorialMessages extends OkaeriConfig {

        @Comment("Sent when the player types anything but an answer, b, or q.")
        private String invalid = "{prefix}<gray>Type a number or type 'q' to quit the setup.";

        @Comment("Sent when the session times out.")
        private String timeout = "{prefix}<yellow>Setup timed out after 5 minutes of no answers.";

        @Comment("Sent on quit and on plain EXIT answers.")
        private String quit = "{prefix}<gray>Setup closed. Run <white>/mh setup<gray> any time to restart.";

        @Comment("Sent when the node graph is broken (missing node).")
        private String broken = "{prefix}<red>Setup is broken: tell an admin to check Core/tutorial.yml.";

    }

    /** One dialogue: question lines plus numbered answers. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class TutorialNode extends OkaeriConfig {

        private List<String> question = new ArrayList<>();
        private List<TutorialAnswer> answers = new ArrayList<>();
        private boolean celebrate = false;

    }

    /** One answer: label, optional commands, and where it leads. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class TutorialAnswer extends OkaeriConfig {

        private String text = "";
        private boolean recommended = false;
        private boolean notrecommended = false;
        private List<String> commands = new ArrayList<>();
        private String next = "EXIT";

    }
}
