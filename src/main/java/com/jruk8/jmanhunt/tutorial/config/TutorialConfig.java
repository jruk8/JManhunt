package com.jruk8.jmanhunt.tutorial.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.Header;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
public class TutorialConfig extends OkaeriConfig {

    @Comment("Dialogue line templates and the recommendation tag.")
    private TutorialFormat format = new TutorialFormat();

    @Comment("Path-completion feedback sound. Neutral and angry feedback use sounds.yml ui sounds.")
    private TutorialSounds sounds = new TutorialSounds();

    @Comment("Fixed engine messages.")
    private TutorialMessages messages = new TutorialMessages();

    @Comment("Dialogue nodes by id. The tutorial starts at 'start'.")
    private Map<String, TutorialNode> nodes = new LinkedHashMap<>();

    public TutorialFormat getFormat() {
        return format;
    }

    public void setFormat(TutorialFormat format) {
        this.format = format;
    }

    public TutorialSounds getSounds() {
        return sounds;
    }

    public void setSounds(TutorialSounds sounds) {
        this.sounds = sounds;
    }

    public TutorialMessages getMessages() {
        return messages;
    }

    public void setMessages(TutorialMessages messages) {
        this.messages = messages;
    }

    public Map<String, TutorialNode> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, TutorialNode> nodes) {
        this.nodes = nodes;
    }

    /** Dialogue line templates. */
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

        public List<String> getHeader() {
            return header;
        }

        public void setHeader(List<String> header) {
            this.header = header;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }

        public String getSeparator() {
            return separator;
        }

        public void setSeparator(String separator) {
            this.separator = separator;
        }

        public String getFooter() {
            return footer;
        }

        public void setFooter(String footer) {
            this.footer = footer;
        }

        public String getRecommendation() {
            return recommendation;
        }

        public void setRecommendation(String recommendation) {
            this.recommendation = recommendation;
        }

        public String getNotrecommended() {
            return notrecommended;
        }

        public void setNotrecommended(String notrecommended) {
            this.notrecommended = notrecommended;
        }
    }

    /** One feedback sound. */
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public float getVolume() {
            return volume;
        }

        public void setVolume(float volume) {
            this.volume = volume;
        }

        public float getPitch() {
            return pitch;
        }

        public void setPitch(float pitch) {
            this.pitch = pitch;
        }
    }

    /** Path-completion feedback sound. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class TutorialSounds extends OkaeriConfig {

        @Comment("Heard when a path completes.")
        private TutorialSound congratulations =
                TutorialSound.of("entity.player.levelup", 1.0f, 0.8f);

        public TutorialSound getCongratulations() {
            return congratulations;
        }

        public void setCongratulations(TutorialSound congratulations) {
            this.congratulations = congratulations;
        }
    }

    /** Fixed engine messages. {prefix} resolves in the adapter. */
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

        public String getInvalid() {
            return invalid;
        }

        public void setInvalid(String invalid) {
            this.invalid = invalid;
        }

        public String getTimeout() {
            return timeout;
        }

        public void setTimeout(String timeout) {
            this.timeout = timeout;
        }

        public String getQuit() {
            return quit;
        }

        public void setQuit(String quit) {
            this.quit = quit;
        }

        public String getBroken() {
            return broken;
        }

        public void setBroken(String broken) {
            this.broken = broken;
        }
    }

    /** One dialogue: question lines plus numbered answers. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class TutorialNode extends OkaeriConfig {

        private List<String> question = new ArrayList<>();
        private List<TutorialAnswer> answers = new ArrayList<>();
        private boolean celebrate = false;

        public List<String> getQuestion() {
            return question;
        }

        public void setQuestion(List<String> question) {
            this.question = question;
        }

        public List<TutorialAnswer> getAnswers() {
            return answers;
        }

        public void setAnswers(List<TutorialAnswer> answers) {
            this.answers = answers;
        }

        public boolean isCelebrate() {
            return celebrate;
        }

        public void setCelebrate(boolean celebrate) {
            this.celebrate = celebrate;
        }
    }

    /** One answer: label, optional commands, and where it leads. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class TutorialAnswer extends OkaeriConfig {

        private String text = "";
        private boolean recommended = false;
        private boolean notrecommended = false;
        private List<String> commands = new ArrayList<>();
        private String next = "EXIT";

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public boolean isRecommended() {
            return recommended;
        }

        public void setRecommended(boolean recommended) {
            this.recommended = recommended;
        }

        public boolean isNotrecommended() {
            return notrecommended;
        }

        public void setNotrecommended(boolean notrecommended) {
            this.notrecommended = notrecommended;
        }

        public List<String> getCommands() {
            return commands;
        }

        public void setCommands(List<String> commands) {
            this.commands = commands;
        }

        public String getNext() {
            return next;
        }

        public void setNext(String next) {
            this.next = next;
        }
    }
}
