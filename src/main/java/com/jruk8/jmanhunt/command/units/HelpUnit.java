package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.SubcommandUnit;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The help, support, and challenges verbs: static info output. */
public final class HelpUnit implements SubcommandUnit {
    /**
     * Readonly announcement printed by the challenges subcommand. This text is
     * intentionally not loaded from messages.yml so it cannot be edited or
     * removed by server owners; change it here instead. It is always parsed as
     * MiniMessage; the {link} token becomes a clickable link to
     * {@link #CHALLENGES_URL} and {status} becomes the companion plugin status.
     */
    static final String CHALLENGES_MESSAGE = """
            
            <gray>[<gradient:#5e42f4:#b742f4>JMHChallenges</gradient>]</gray>
            <#de7766>JManhunt</#de7766> is a free plugin for configurable manhunts. \
            For lucky blocks and other fun challenges, you can find the optional addon {link}.
            
            <gray> » Challenges status: [{status}<gray>]</gray>
            
            <gray>Looking for modifiers instead? Try \
            <white>/mh modifiers</white>.</gray>
            """;
    public static final String CHALLENGES_URL = "https://builtbybit.com/resources/jmanhunt-challenges.121574/";
    private static final String CHALLENGES_LINK_TOKEN = "{link}";
    private static final String CHALLENGES_LINK_TEXT = "here";
    private static final String CHALLENGES_STATUS_TOKEN = "{status}";
    private static final String CHALLENGES_STATUS_ACTIVE = "ACTIVE";
    private static final String CHALLENGES_STATUS_INACTIVE = "INACTIVE";
    // Candidate plugin.yml names of the JManhunt-Challenges companion plugin;
    // the status line shows ACTIVE when any of these is loaded and enabled.
    private static final Set<String> CHALLENGES_PLUGIN_NAMES =
            Set.of("JManhunt-Challenges", "JManhuntChallenges", "JMHChallenges");

    /** Community links shared by the help suffix and /mh support. */
    private static final String DOCS_URL = "https://jruk8.github.io/JManhunt/";
    private static final String DISCORD_URL = "https://discord.gg/hkWmCVmWDC";
    private static final String GITHUB_URL = "https://github.com/jruk8/JManhunt";
    private static final String KOFI_URL = "https://ko-fi.com/jruk";

    private final ManhuntMessages texts;
    private final CommandSupport support;

    public HelpUnit(ManhuntMessages texts, CommandSupport support) {
        this.texts = texts;
        this.support = support;
    }

    /** Info verbs take no args; the verb selects the output. */
    public record HelpArgs(String verb) {
    }

    public static HelpArgs parse(String[] args) {
        return new HelpArgs(args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT));
    }

    @Override public String primaryName() {
        return "help";
    }

    @Override public Set<String> aliases() {
        return Set.of("support", "challenges");
    }

    @Override public boolean execute(CommandSender sender, String[] args) {
        return executeParsed(sender, parse(args));
    }

    public boolean executeParsed(CommandSender sender, HelpArgs args) {
        return switch (args.verb()) {
            case "help" -> help(sender);
            case "support" -> support(sender);
            case "challenges" -> challenges(sender);
            default -> throw new IllegalArgumentException("HelpUnit cannot handle " + args.verb());
        };
    }

    @Override public List<String> complete(CommandSender sender, String[] args) {
        return null;
    }

    private boolean help(CommandSender sender) {
        support.message(sender, texts.getHelpHeader());
        String[][] lines = {{"/manhunt help", "show commands"}, {"/manhunt setup", "interactive setup guide"},
                {"/manhunt", "open the GUI or show match status"},
                {"/manhunt setplayer <selector> <hunter|speedrunner|spectator|afk|none>", "assign roles"},
                {"/manhunt lobby join <selector> <lobby-id> [role] [-notp]", "move players to a lobby"},
                {"/manhunt lobby leave [selector]", "remove players from their lobby"},
                {"/manhunt start [lobby-id]", "start a match"}, {"/manhunt end [id] [-i|-immediate]", "cancel a match"},
                {"/manhunt game join <id> [role] [selector]", "add players to a running match"},
                {"/manhunt game leave [id] [selector]", "remove players from a running match"},
                {"/manhunt status [id|all]", "show match status"},
                {"/manhunt quickstart [percentage]", "assign teams and start immediately"},
                {"/manhunt config <category> <key...> <value>", "view or change a setting"},
                {"/manhunt modifiers [setmod|setpreset]", "browse or toggle gameplay modifiers"},
                {"/manhunt override <lobby> <settings|modifiers|clear> ...", "view or change per-lobby overrides"},
                {"/manhunt worldengine", "manage lobbies or teleport players"},
                {"/manhunt debug [INFO|WARN|SEVERE]", "toggle or set debug level"},
                {"/manhunt challenges", "show Challenges addon info"},
                {"/manhunt dev schem <pos1|pos2|save|load|list>", "dev schematic tools"},
                {"/manhunt reload", "reload files"}};
        for (String[] line : lines) {
            support.message(sender, texts.getHelpLine(), Map.of("command", line[0], "description", line[1]));
        }
        // Clickable links use hardcoded MiniMessage instead of living in
        // messages.yml.
        sender.sendMessage(support.miniMessage(
                "\n<green>Still need help? Check <#de7766><click:open_url:'" + DOCS_URL + "'>"
                        + "<underlined>Docs</underlined></click></#de7766> or join our "
                        + "<#de7766><click:open_url:'" + DISCORD_URL + "'>"
                        + "<underlined>Discord server</underlined></click></#de7766>!</green>"));
        sender.sendMessage(support.miniMessage(
                "<green>Support our development on <#de7766><click:open_url:'" + KOFI_URL + "'>"
                        + "<underlined>Ko-fi</underlined></click></#de7766>.</green>"));
        support.neutralSound(sender);
        return true;
    }

    /** Support links: Discord invite, GitHub, Ko-fi. Players and console alike. */
    private boolean support(CommandSender sender) {
        for (Component line : supportMessages(support)) {
            sender.sendMessage(line);
        }
        support.neutralSound(sender);
        return true;
    }

    /**
     * Support output lines: blank, parsed prefix, then the Discord,
     * GitHub, and Ko-fi lines. Static for tests.
     */
    public static List<Component> supportMessages(CommandSupport support) {
        return List.of(
                Component.empty(),
                support.componentRaw(support.prefix(), Map.of()),
                support.miniMessage(
                        "<green>Need help? Join our <#de7766><click:open_url:'" + DISCORD_URL + "'>"
                                + "<underlined>Discord server</underlined></click></#de7766>!</green>"),
                support.miniMessage(
                        "<green>Star us on <#de7766><click:open_url:'" + GITHUB_URL + "'>"
                                + "<underlined>GitHub</underlined></click></#de7766>.</green>"),
                support.miniMessage(
                        "<green>Support our development on <#de7766><click:open_url:'" + KOFI_URL + "'>"
                                + "<underlined>Ko-fi</underlined></click></#de7766>.</green>"));
    }

    private boolean challenges(CommandSender sender) {
        for (Component line : challengesComponents(support, isCompanionEnabled())) {
            sender.sendMessage(line);
        }
        support.neutralSound(sender);
        return true;
    }

    /**
     * The announcement lines from {@link #CHALLENGES_MESSAGE}, with the {link}
     * token turned into a clickable link and the {status} token turned into the
     * companion plugin status.
     */
    public static List<Component> challengesComponents(CommandSupport support, boolean companionEnabled) {
        List<Component> lines = new ArrayList<>();
        for (String line : CHALLENGES_MESSAGE.split("\n")) {
            lines.add(renderChallengesLine(support, line, companionEnabled));
        }
        return lines;
    }

    /**
     * Renders one line of {@link #CHALLENGES_MESSAGE}, replacing every
     * braced {token} with its dynamic component and parsing the rest of the
     * text as MiniMessage.
     */
    private static Component renderChallengesLine(CommandSupport support, String line, boolean companionEnabled) {
        Component rendered = Component.empty();
        int cursor = 0;
        while (cursor < line.length()) {
            int open = line.indexOf('{', cursor);
            int close = open < 0 ? -1 : line.indexOf('}', open);
            if (open < 0 || close < 0) {
                rendered = rendered.append(support.miniMessage(line.substring(cursor)));
                break;
            }
            if (open > cursor) {
                rendered = rendered.append(support.miniMessage(line.substring(cursor, open)));
            }
            rendered = rendered.append(tokenComponent(support, line.substring(open, close + 1), companionEnabled));
            cursor = close + 1;
        }
        return rendered;
    }

    private static Component tokenComponent(CommandSupport support, String token, boolean companionEnabled) {
        return switch (token) {
            case CHALLENGES_LINK_TOKEN -> challengesLink();
            case CHALLENGES_STATUS_TOKEN -> challengesStatus(companionEnabled);
            default -> support.miniMessage(token);
        };
    }

    private static Component challengesLink() {
        return Component.text(CHALLENGES_LINK_TEXT, NamedTextColor.GOLD, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(CHALLENGES_URL))
                .hoverEvent(HoverEvent.showText(Component.text(CHALLENGES_URL, NamedTextColor.GRAY)));
    }

    private static Component challengesStatus(boolean companionEnabled) {
        String status = companionEnabled ? CHALLENGES_STATUS_ACTIVE : CHALLENGES_STATUS_INACTIVE;
        NamedTextColor color = companionEnabled ? NamedTextColor.GREEN : NamedTextColor.RED;
        return Component.text(status, color);
    }

    private boolean isCompanionEnabled() {
        for (String name : CHALLENGES_PLUGIN_NAMES) {
            if (Bukkit.getPluginManager().isPluginEnabled(name)) {
                return true;
            }
        }
        return false;
    }
}
