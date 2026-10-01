package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selector expansion behind {@link CommandPlaceholders}. */
public final class SelectorExpansion {
    private static final Pattern SELECTOR_ALL = Pattern.compile("@a(?![A-Za-z0-9_])(\\[[^\\]]*\\])?");
    private static final Pattern SELECTOR_RANDOM = Pattern.compile("@r(?![A-Za-z0-9_])(\\[[^\\]]*\\])?");
    private static final Pattern SELECTOR_SELF = Pattern.compile("@[ps](?![A-Za-z0-9_])(\\[[^\\]]*\\])?");
    private static final Pattern TEAM_ARGUMENT = Pattern.compile("(?i)(?:^|[,\\[])\\s*team\\s*=\\s*([^,\\]]+)");
    private static final Pattern FANOUT_TOKEN = Pattern.compile("<all-fanout(?::([A-Za-z]+))?>");

    private SelectorExpansion() {
    }

    static String convertSelectorRun(String run) {
        String converted = replaceSelector(run, SELECTOR_ALL, true);
        converted = replaceSelector(converted, SELECTOR_RANDOM, false);
        return replaceSelfSelector(converted);
    }

    static String replaceSelfSelector(String command) {
        Matcher matcher = SELECTOR_SELF.matcher(command);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement("<p>"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static String replaceSelector(String command, Pattern pattern, boolean keepTeam) {
        Matcher matcher = pattern.matcher(command);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = "<all-fanout>";
            if (!keepTeam) {
                replacement = "<random-player>";
            } else if (matcher.group(1) != null) {
                Matcher team = TEAM_ARGUMENT.matcher(matcher.group(1));
                if (team.find()) {
                    replacement = "<all-fanout:" + team.group(1).trim().toUpperCase(Locale.ROOT) + ">";
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Fans a command out to one copy per in-match participant when it holds
     * the hidden {@code @a} fan-out marker. With nobody to cover, warns and
     * returns no commands so nothing leaks outside the match. The
     * {@code <all-players>} tag is NOT expanded here: it resolves to a name
     * list during tag evaluation instead. Pure for tests.
     */
    public static List<String> expandAllPlayers(String command, ModifierTagScope scope) {
        String substituted = EngineEscapes.substitute(command);
        String converted = CommandPlaceholders.convertSelectors(substituted);
        Matcher matcher = FANOUT_TOKEN.matcher(converted);
        if (!matcher.find()) {
            return List.of(substituted);
        }
        String team = matcher.group(1);
        List<String> names = scope.participantNames(team);
        if (names.isEmpty()) {
            scope.warn("Skipping command with @a"
                    + (team == null ? "" : "[team=" + team + "]")
                    + " because the match has no covered players: " + command);
            return List.of();
        }
        List<String> expanded = new ArrayList<>(names.size());
        for (String name : names) {
            expanded.add(matcher.replaceAll(Matcher.quoteReplacement(name)));
        }
        return expanded;
    }
}
