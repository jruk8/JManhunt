package com.jruk8.jmanhunt.command;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces tags and resolves relative coordinates (~) in modifier commands.
 * All command dispatch stays through the console sender, so tilde resolution
 * is done here rather than relying on the sender's location.
 *
 * <p>Tags evaluate from the inside out, so nesting works:
 * {@code <random-pick:coal <random-num:4,12>, diamond>} first rolls the
 * number, then picks an item. Unknown tags are left untouched.
 *
 * <p>Vanilla {@code @a} and {@code @r} selectors are implicitly converted to
 * the match-scoped {@code <all-players>} and {@code <random-player>} tags so
 * a modifier can never leak into another running match. A {@code team=}
 * argument on {@code @a[...]} survives as a role filter; every other vanilla
 * selector argument is dropped. {@code @p} and {@code @s} become the
 * executor tag {@code <p>} for the same reason.
 */
public final class CommandPlaceholders {
    private static final Pattern TILDE_PATTERN = Pattern.compile("~([+-]?\\d+(?:\\.\\d+)?)?");
    /** Innermost tag: an angle pair containing no further angle brackets. */
    private static final Pattern INNER_TAG = Pattern.compile("<([^<>]*)>");
    /** Bare @a/@r plus optional vanilla [...] args, never @admin-style words. */
    private static final Pattern SELECTOR_ALL = Pattern.compile("@a(?![A-Za-z0-9_])(\\[[^\\]]*\\])?");
    private static final Pattern SELECTOR_RANDOM = Pattern.compile("@r(?![A-Za-z0-9_])(\\[[^\\]]*\\])?");
    /** Bare @p/@s plus optional vanilla [...] args, never @server-style words. */
    private static final Pattern SELECTOR_SELF = Pattern.compile("@[ps](?![A-Za-z0-9_])(\\[[^\\]]*\\])?");
    /** team=HUNTER inside vanilla selector args. */
    private static final Pattern TEAM_ARGUMENT = Pattern.compile("(?i)(?:^|[,\\[])\\s*team\\s*=\\s*([^,\\]]+)");
    /** Fan-out token, with an optional :TEAM filter. */
    private static final Pattern ALL_PLAYERS_TOKEN = Pattern.compile("<all-players(?::([A-Za-z]+))?>");
    /** Safety cap for the inside-out evaluation loop. */
    private static final int MAX_TAG_PASSES = 25;
    /** Random-pick draws at most ten candidates (indexes 0-9). */
    private static final int MAX_PICK_ATTEMPTS = 10;

    // Lazily initialized to avoid IllegalStateException when the class is
    // loaded in a unit test without a running Bukkit server.
    private static volatile List<EntityType> spawnableLiving;
    private static volatile List<Material> items;

    private CommandPlaceholders() {
    }

    /**
     * Replaces all placeholders in a command string.
     *
     * @param command    the raw command from config
     * @param playerName the participating player's name, or null for console commands
     * @param x          the player's x coordinate for tilde resolution, or 0 if no player
     * @param y          the player's y coordinate for tilde resolution, or 0 if no player
     * @param z          the player's z coordinate for tilde resolution, or 0 if no player
     * @return the parsed command ready for console dispatch
     */
    public static String replace(String command, String playerName, double x, double y, double z) {
        return replace(command, playerName, x, y, z,
                ModifierTagScope.executor(playerName, message -> { }));
    }

    /**
     * Same, with a match scope for {@code <all-players>} and
     * {@code <random-player>} plus warning delivery. Call
     * {@link #expandAllPlayers} first when a command may fan out to the
     * whole match; any surviving token here falls back to the executor.
     */
    public static String replace(String command, String playerName, double x, double y, double z,
            ModifierTagScope scope) {
        String parsed = convertSelectors(command);
        parsed = evaluateTags(parsed, playerName, scope);
        if (playerName != null) {
            parsed = resolveTildes(parsed, x, y, z);
        }
        return parsed;
    }

    /**
     * Replaces the &lt;duration&gt; placeholder with the given delay in whole
     * seconds, floored so commands like `effect give` receive an int.
     * Pure for tests.
     */
    public static String withDuration(String command, double delaySeconds) {
        if (!command.contains("<duration>")) {
            return command;
        }
        return command.replace("<duration>", String.valueOf((long) Math.floor(delaySeconds)));
    }

    /**
     * Converts vanilla selectors to match-scoped tags: {@code @a} becomes
     * {@code <all-players>} and {@code @r} becomes {@code <random-player>}.
     * A {@code team=} argument becomes a role filter ({@code <all-players:HUNTER>});
     * other vanilla arguments are dropped. {@code @p} and {@code @s} become
     * the executor tag {@code <p>}; their arguments are dropped. Pure for tests.
     */
    static String convertSelectors(String command) {
        String converted = replaceSelector(command, SELECTOR_ALL, true);
        converted = replaceSelector(converted, SELECTOR_RANDOM, false);
        return replaceSelfSelector(converted);
    }

    private static String replaceSelfSelector(String command) {
        Matcher matcher = SELECTOR_SELF.matcher(command);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement("<p>"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceSelector(String command, Pattern pattern, boolean keepTeam) {
        Matcher matcher = pattern.matcher(command);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = "<all-players>";
            if (!keepTeam) {
                replacement = "<random-player>";
            } else if (matcher.group(1) != null) {
                Matcher team = TEAM_ARGUMENT.matcher(matcher.group(1));
                if (team.find()) {
                    replacement = "<all-players:" + team.group(1).trim().toUpperCase(Locale.ROOT) + ">";
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Fans a command out to one copy per in-match participant when it holds
     * an {@code <all-players>} token (after implicit {@code @a} conversion).
     * With nobody to cover, warns and returns no commands so nothing leaks
     * outside the match. Pure for tests.
     */
    public static List<String> expandAllPlayers(String command, ModifierTagScope scope) {
        String converted = convertSelectors(command);
        Matcher matcher = ALL_PLAYERS_TOKEN.matcher(converted);
        if (!matcher.find()) {
            return List.of(command);
        }
        String team = matcher.group(1);
        List<String> names = scope.participantNames(team);
        if (names.isEmpty()) {
            scope.warn("Skipping command with <all-players"
                    + (team == null ? "" : ":" + team)
                    + "> because the match has no covered players: " + command);
            return List.of();
        }
        List<String> expanded = new ArrayList<>(names.size());
        for (String name : names) {
            expanded.add(matcher.replaceAll(Matcher.quoteReplacement(name)));
        }
        return expanded;
    }

    /**
     * Pre-resolves random-mob and random-item tags to activation-shared rolls
     * so PER_INVOKE sees one mob and one item across every executor. Rolls
     * come from the given roller and are cached in sharedDraws (one cache per
     * modifier activation, shared across every command list); lines without
     * those tags pass through untouched, and every other tag still resolves
     * per executor downstream. Matching mirrors tag evaluation: innermost
     * spans whose name (before any colon, trimmed, case-blind) is random-mob
     * or random-item. Pure apart from the roller, so unit tests cover it
     * with fixed draws.
     */
    public static List<String> preresolveSharedRandoms(List<String> lines, Map<String, String> sharedDraws,
            java.util.function.Function<String, String> roller) {
        boolean wanted = false;
        for (String line : lines) {
            if (containsSharedRandom(line)) {
                wanted = true;
                break;
            }
        }
        if (!wanted) {
            return lines;
        }
        List<String> fixed = new ArrayList<>(lines.size());
        for (String line : lines) {
            fixed.add(preresolveLine(line, sharedDraws, roller));
        }
        return fixed;
    }

    /** Draws one shared roll for a random tag name. */
    public static String rollSharedRandom(String name) {
        return "random-mob".equals(name) ? randomMob() : randomItem();
    }

    private static boolean containsSharedRandom(String line) {
        Matcher matcher = INNER_TAG.matcher(line);
        while (matcher.find()) {
            if (isSharedRandom(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static String preresolveLine(String line, Map<String, String> sharedDraws,
            java.util.function.Function<String, String> roller) {
        String current = line;
        for (int pass = 0; pass < MAX_TAG_PASSES; pass++) {
            Matcher matcher = INNER_TAG.matcher(current);
            StringBuffer result = new StringBuffer();
            boolean changed = false;
            while (matcher.find()) {
                String name = tagName(matcher.group(1));
                String draw = sharedDraws.get(name);
                if (draw == null && isSharedRandom(matcher.group(1))) {
                    draw = roller.apply(name);
                    sharedDraws.put(name, draw);
                }
                if (draw != null) {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(draw));
                    changed = true;
                } else {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                }
            }
            matcher.appendTail(result);
            current = result.toString();
            if (!changed) {
                return current;
            }
        }
        return current;
    }

    /** Tag name: before any colon, trimmed, lowercase. Mirrors tag evaluation. */
    private static String tagName(String body) {
        int separator = body.indexOf(':');
        String name = separator < 0 ? body : body.substring(0, separator);
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isSharedRandom(String body) {
        String name = tagName(body);
        return "random-mob".equals(name) || "random-item".equals(name);
    }

    /** Inside-out tag evaluation; unknown tags survive untouched. */
    private static String evaluateTags(String command, String playerName, ModifierTagScope scope) {
        String current = command;
        for (int pass = 0; pass < MAX_TAG_PASSES; pass++) {
            Matcher matcher = INNER_TAG.matcher(current);
            if (!matcher.find()) {
                return current;
            }
            StringBuffer result = new StringBuffer();
            boolean changed = false;
            do {
                String resolved = resolveTag(matcher.group(1), playerName, scope);
                if (!resolved.equals(matcher.group(0))) {
                    changed = true;
                }
                matcher.appendReplacement(result, Matcher.quoteReplacement(resolved));
            } while (matcher.find());
            matcher.appendTail(result);
            current = result.toString();
            if (!changed) {
                return current;
            }
        }
        scope.warn("Stopped evaluating nested tags after " + MAX_TAG_PASSES + " passes: " + command);
        return current;
    }

    private static String resolveTag(String body, String playerName, ModifierTagScope scope) {
        String tag = "<" + body + ">";
        int separator = body.indexOf(':');
        String name = (separator < 0 ? body : body.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
        String args = separator < 0 ? null : body.substring(separator + 1);
        return switch (name) {
            case "p" -> playerName != null ? playerName : tag;
            case "random-mob" -> randomMob();
            case "random-item" -> randomItem();
            case "random-num" -> randomNumber(args, scope, tag);
            case "random-pick" -> randomPick(args, scope, tag);
            case "random-player" -> scope.randomParticipant()
                    .or(() -> Optional.ofNullable(playerName))
                    .orElseGet(() -> {
                        scope.warn("Skipping <random-player> with no players in scope: " + tag);
                        return tag;
                    });
            case "all-players" -> {
                // Normally expanded up front by expandAllPlayers; reaching
                // here means executor-only dispatch, so cover the executor.
                scope.warn("<all-players> outside a match covers only the executor.");
                yield playerName != null ? playerName : tag;
            }
            // Handled separately by withDuration before evaluation.
            case "duration" -> tag;
            default -> tag;
        };
    }

    /**
     * Rolls an integer between a and b, inclusive; order does not matter.
     * Unparseable input warns and yields 0 so dispatch keeps a valid int.
     */
    static String randomNumber(String args, ModifierTagScope scope, String tag) {
        if (args != null) {
            String[] bounds = args.split(",", -1);
            if (bounds.length == 2) {
                try {
                    long first = Long.parseLong(bounds[0].trim());
                    long second = Long.parseLong(bounds[1].trim());
                    long low = Math.min(first, second);
                    long high = Math.max(first, second);
                    long span = high - low + 1;
                    if (span > 0) {
                        long rolled = low + nextLong(scope, span);
                        if (rolled >= Integer.MIN_VALUE && rolled <= Integer.MAX_VALUE) {
                            return String.valueOf(rolled);
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // Falls through to the warning below.
                }
            }
        }
        scope.warn("Invalid <random-num:a,b> tag, using 0: " + tag);
        return "0";
    }

    /** Bounded long draw from the scope's random source. */
    private static long nextLong(ModifierTagScope scope, long bound) {
        long drawn = scope.random().nextLong() >>> 1;
        return drawn % bound;
    }

    /**
     * Picks one item from a comma list. Items may be bare, double-quoted, or
     * single-quoted, and may hold spaces; spaces after a comma are skipped.
     * Invalid items warn with the item in parentheses plus its index while
     * another candidate is tried, up to ten draws or until every unique item
     * is exhausted. Total failure warns and yields an empty string.
     */
    static String randomPick(String args, ModifierTagScope scope, String tag) {
        if (args == null || args.isBlank()) {
            scope.warn("Empty <random-pick:...> tag: " + tag);
            return "";
        }
        List<String> rawItems = splitPickArgs(args);
        Set<String> seen = new LinkedHashSet<>();
        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < rawItems.size(); index++) {
            if (seen.add(rawItems.get(index))) {
                candidates.add(index);
            }
        }
        int attempts = Math.min(MAX_PICK_ATTEMPTS, candidates.size());
        for (int draw = 0; draw < attempts; draw++) {
            int slot = scope.random().nextInt(candidates.size());
            int index = candidates.remove(slot);
            Optional<String> parsed = parsePickItem(rawItems.get(index));
            if (parsed.isPresent()) {
                return parsed.get();
            }
            scope.warn("Skipping invalid <random-pick> item (" + rawItems.get(index).trim()
                    + ") at index " + index + " in: " + tag);
        }
        scope.warn("No valid <random-pick> item, using an empty string: " + tag);
        return "";
    }

    /** Splits pick args on commas outside single or double quotes. */
    static List<String> splitPickArgs(String args) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int index = 0; index < args.length(); index++) {
            char letter = args.charAt(index);
            if (quote != 0) {
                current.append(letter);
                if (letter == quote) {
                    quote = 0;
                }
            } else if (letter == '"' || letter == '\'') {
                quote = letter;
                current.append(letter);
            } else if (letter == ',') {
                items.add(current.toString());
                current.setLength(0);
            } else {
                current.append(letter);
            }
        }
        items.add(current.toString());
        return items;
    }

    /**
     * Validates one pick item: either bare with no quote characters, or one
     * balanced quoted string and nothing else. Empty when illegal.
     */
    static Optional<String> parsePickItem(String raw) {
        String item = raw.trim();
        if (item.isEmpty()) {
            return Optional.empty();
        }
        char first = item.charAt(0);
        if (first != '"' && first != '\'') {
            return item.indexOf('"') < 0 && item.indexOf('\'') < 0
                    ? Optional.of(item)
                    : Optional.empty();
        }
        if (item.length() < 2 || item.charAt(item.length() - 1) != first) {
            return Optional.empty();
        }
        String inner = item.substring(1, item.length() - 1);
        return inner.indexOf(first) < 0 ? Optional.of(inner) : Optional.empty();
    }

    /**
     * Resolves relative coordinates (~, ~5, ~-3) to absolute coordinates using
     * the given reference position. Tildes cycle through x, y, z in order.
     * This is package-private for testing.
     */
    static String resolveTildes(String command, double x, double y, double z) {
        Matcher matcher = TILDE_PATTERN.matcher(command);
        StringBuilder result = new StringBuilder();
        int[] index = {0};
        double[] coords = {x, y, z};
        while (matcher.find()) {
            String offsetStr = matcher.group(1);
            double offset = offsetStr == null ? 0.0 : Double.parseDouble(offsetStr);
            double base = coords[index[0] % 3];
            double resolved = base + offset;
            // Use integer string when the value is a whole number, otherwise
            // keep the decimal to avoid ".0" in coordinates.
            String replacement = resolved == Math.floor(resolved)
                    ? String.valueOf((long) resolved)
                    : String.valueOf(resolved);
            matcher.appendReplacement(result, replacement);
            index[0]++;
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Returns the list of spawnable living entity types. Package-private for testing. */
    static List<EntityType> spawnableLivingEntities() {
        if (spawnableLiving == null) {
            synchronized (CommandPlaceholders.class) {
                if (spawnableLiving == null) {
                    spawnableLiving = Arrays.stream(EntityType.values())
                            .filter(EntityType::isSpawnable)
                            .filter(EntityType::isAlive)
                            .toList();
                }
            }
        }
        return spawnableLiving;
    }

    /** Returns the list of item materials. Package-private for testing. */
    static List<Material> items() {
        if (items == null) {
            synchronized (CommandPlaceholders.class) {
                if (items == null) {
                    items = Arrays.stream(Material.values())
                            .filter(Material::isItem)
                            .toList();
                }
            }
        }
        return items;
    }

    private static String randomMob() {
        List<EntityType> mobs = spawnableLivingEntities();
        return mobs.get(ThreadLocalRandom.current().nextInt(mobs.size())).name().toLowerCase(Locale.ROOT);
    }

    private static String randomItem() {
        List<Material> itemList = items();
        return itemList.get(ThreadLocalRandom.current().nextInt(itemList.size())).name().toLowerCase(Locale.ROOT);
    }
}
