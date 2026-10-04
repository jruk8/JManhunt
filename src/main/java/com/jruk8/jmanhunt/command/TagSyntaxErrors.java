package com.jruk8.jmanhunt.command;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Per-tag validation errors behind {@link CommandSyntax}. */
final class TagSyntaxErrors {
    private static final Pattern LETTERS_ONLY = Pattern.compile("[A-Za-z]+");
    private static final Set<String> KNOWN_TAGS = Set.copyOf(CommandSyntax.knownTags());

    private TagSyntaxErrors() {
    }

    static Optional<String> tagError(String body) {
        int separator = body.indexOf(':');
        String name = (separator < 0 ? body : body.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
        String args = separator < 0 ? null : body.substring(separator + 1);
        return switch (name) {
            case "random-num" -> randomNumberError(args);
            case "random-pick" -> randomPickError(args);
            case "id", "i" -> noArgsError(name, args);
            case "min", "max" -> arityError(name, args, 2, "two numbers");
            case "clamp" -> arityError(name, args, 3, "a value plus low and high");
            case "gmessage", "gmsg" -> arityError(name, args, 1, "one text");
            case "pmessage", "pmsg" -> arityError(name, args, 2, "a player and a text");
            case "rmessage", "rmsg" -> RoleTagSyntax.messageError(name, args);
            case "gsound" -> TagSinks.soundError(name, args);
            case "psound" -> TagSinks.playerSoundError(name, args);
            case "rsound" -> RoleTagSyntax.soundError(name, args);
            case "if" -> ifError(args);
            case "pstat" -> statError(name, args, 1, TagStats.PSTAT_KEYS);
            case "gstat" -> statError(name, args, 0, TagStats.GSTAT_KEYS);
            case "phasitem" -> TagItems.syntaxError(name, args);
            case "gflag", "gf", "pflag", "pf", "lflag", "lf" -> flagError(name, args);
            case "rflag" -> RoleTagSyntax.flagError(name, args);
            case "placeholder" -> arityError(name, args, 1, "one key");
            case "loseplayer" -> loseplayerError(name, args);
            case "win" -> winError(name, args);
            case "args" -> argsError(name, args);
            case "list.append", "list.get", "list.set", "list.remove", "list.contains",
                    "list.clear", "list.pop", "len", "list.shuffle", "list.filter", "list.reverse",
                    "list.join", "list.slice", "list.first", "list.last" ->
                    TagLists.opError(name, args);
            case "range" -> rangeError(name, args);
            case "str.join", "str.split", "str.lower", "str.upper", "str.contains" ->
                    TagStrings.opError(name, args);
            case "pcooldown", "pcooldown.get", "pcooldown.reset", "gcooldown", "gcooldown.get",
                    "gcooldown.reset" -> TagCooldowns.syntaxError(name, args);
            case "default" -> topLevelArityError(name, args, 2, "<default:value,fallback>");
            case "active-players" -> activePlayersError(name, args);
            case "plocation", "ploc", "prole", "pworld", "world", "px", "py", "pz", "pyaw",
                    "ppitch", "pheld" -> playerNameError(name, args);
            case "vec.add", "vec.sub", "vec.mult", "vec.normalize", "vec.sqrdist", "vec.dist",
                    "vec.dot", "vec.cross", "loc.shift", "pdir" ->
                    TagVectors.opError(name, args);
            default -> tailError(name, args);
        };
    }

    /** Trailing tag names behind {@link #tagError}: proximity, math, loops, defs. */
    static Optional<String> tailError(String name, String args) {
        return switch (name) {
            case "pstate", "pstandingon", "ptitle", "pslot" -> TagPlayers.opError(name, args);
            case "overlap-players", "nearby-players" -> proximityError(name, args);
            case "distance" -> topLevelArityError(name, args, 2, "<distance:loc1,loc2>");
            case "floor", "ceil", "round", "abs", "sign", "sqrt", "cbrt" -> mathUnaryError(name, args);
            case "root" -> topLevelArityError(name, args, 2, "<root:x,n>");
            case "while", "for" -> loopError(name, args);
            case "def" -> defError(name, args);
            case "format" -> TagFormat.syntaxError(name, args);
            default -> Optional.empty();
        };
    }

    static Optional<String> defError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <def> needs a name and a body like <def:double,x+x,x>.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() < 2 || parts.get(0).isBlank()) {
            return Optional.of("Tag <def> needs a name and a body like <def:double,x+x,x>.");
        }
        return Optional.empty();
    }

    static void collectWarnings(String body, List<String> found,
            Collection<String> knownFunctions) {
        int separator = body.indexOf(':');
        String name = (separator < 0 ? body : body.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
        String args = separator < 0 ? null : body.substring(separator + 1);
        if (!KNOWN_TAGS.contains(name) && !knownFunctions.contains(name)) {
            found.add("Unknown tag '<" + body.trim() + ">', left untouched at runtime.");
            return;
        }
        if ("random-pick".equals(name) && args != null) {
            List<String> items = CommandPlaceholders.pickItems(args);
            for (int index = 0; index < items.size(); index++) {
                if (CommandPlaceholders.parsePickItem(items.get(index)).isEmpty()) {
                    found.add("Skipping invalid <random-pick> item (" + items.get(index).trim()
                            + ") at index " + index + ".");
                }
            }
        } else if ("all-players".equals(name) && args != null && !LETTERS_ONLY.matcher(args.trim()).matches()) {
            found.add("Tag <all-players:" + args.trim() + "> filter should be letters only.");
        }
    }

    static Optional<String> randomNumberError(String args) {
        if (args == null) {
            return Optional.of("Tag <random-num> needs two numbers like <random-num:1,6>.");
        }
        List<String> bounds = CommandPlaceholders.splitPickArgs(args);
        if (bounds.size() != 2) {
            return Optional.of("Tag <random-num:" + args.trim() + "> needs two numbers like <random-num:1,6>.");
        }
        Optional<String> firstItem = CommandPlaceholders.parsePickItem(bounds.get(0));
        Optional<String> secondItem = CommandPlaceholders.parsePickItem(bounds.get(1));
        if (firstItem.isEmpty() || secondItem.isEmpty()) {
            return Optional.of("Tag <random-num:" + args.trim() + "> mixes quotes.");
        }
        try {
            long first = Long.parseLong(firstItem.get().strip());
            long second = Long.parseLong(secondItem.get().strip());
            long low = Math.min(first, second);
            long high = Math.max(first, second);
            if (high - low + 1 <= 0) {
                return Optional.of("Tag <random-num:" + args.trim() + "> range is too large.");
            }
            if (low < Integer.MIN_VALUE || high > Integer.MAX_VALUE) {
                return Optional.of("Tag <random-num:" + args.trim() + "> is outside the integer range.");
            }
        } catch (NumberFormatException unmatched) {
            return Optional.of("Tag <random-num:" + args.trim() + "> needs two numbers like <random-num:1,6>.");
        }
        return Optional.empty();
    }

    static Optional<String> randomPickError(String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <random-pick> needs at least one item.");
        }
        for (String item : CommandPlaceholders.pickItems(args)) {
            if (CommandPlaceholders.parsePickItem(item).isPresent()) {
                return Optional.empty();
            }
        }
        return Optional.of("Tag <random-pick:" + args.trim() + "> has no valid item.");
    }

    static Optional<String> noArgsError(String name, String args) {
        if (args != null && !args.isBlank()) {
            return Optional.of("Tag <" + name + "> takes no arguments.");
        }
        return Optional.empty();
    }

    /**
     * Loop shape: a condition or list plus a body. Split top-level
     * so list args survive, mirroring the runtime split.
     */
    static Optional<String> loopError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a condition or list plus a body.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 2) {
            return Optional.of("Tag <" + name + "> needs a condition or list plus a body.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    /**
     * Loseplayer tag shape: a required head player plus an optional
     * reason after the first comma.
     */
    static Optional<String> loseplayerError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <loseplayer> needs a player like <loseplayer:Steve>.");
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (CommandPlaceholders.parsePickItem(parts.get(0)).isEmpty()) {
            return Optional.of("Tag <loseplayer> needs a player like <loseplayer:Steve>.");
        }
        return Optional.empty();
    }

    /**
     * Win tag shape: a head role, HUNTER or SPEEDRUNNER, plus an
     * optional reason after the first comma.
     */
    static Optional<String> winError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <win> needs HUNTER or SPEEDRUNNER.");
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        Optional<String> item = CommandPlaceholders.parsePickItem(parts.get(0));
        if (item.isEmpty()) {
            return Optional.of("Tag <win> mixes quotes.");
        }
        String role = item.get().strip().toUpperCase(Locale.ROOT);
        if (!role.equals("HUNTER") && !role.equals("SPEEDRUNNER")) {
            return Optional.of("Tag <win> needs HUNTER or SPEEDRUNNER.");
        }
        return Optional.empty();
    }

    /**
     * Args tag shape: bare or one whole index. Like
     * {@code <random-num>} bounds the index must be literal here;
     * nested tags resolve at runtime but the editor cannot see
     * through them.
     */
    static Optional<String> argsError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.empty();
        }
        try {
            Integer.parseInt(args.strip());
        } catch (NumberFormatException unmatched) {
            return Optional.of("Tag <args> needs a whole index like <args:0>.");
        }
        return Optional.empty();
    }

    /**
     * Fixed-arity shape over top-level segments (list literals stay
     * whole), each quote-clean unless blank, mirroring the runtime
     * splitter. Serves every tag whose args may hold lists.
     */
    static Optional<String> topLevelArityError(String name, String args, int arity, String example) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs " + arity + " args like " + example + ".");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != arity) {
            return Optional.of("Tag <" + name + "> needs " + arity + " args like " + example + ".");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty() && !part.isBlank()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    /** Single-number math tags share one example shape. */
    static Optional<String> mathUnaryError(String name, String args) {
        return topLevelArityError(name, args, 1, "<" + name + ":x>");
    }

    /**
     * Active-players shape: one role, HUNTER, SPEEDRUNNER, or ALL.
     * Like {@code <win>} the role must be literal here.
     */
    /**
     * Range shape: one to three quote-clean bounds; values are
     * checked at runtime since they may be math or nested tags.
     */
    static Optional<String> rangeError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <range> needs stop like <range:5>.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.isEmpty() || parts.size() > 3) {
            return Optional.of("Tag <range> needs stop like <range:5>.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <range> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    /**
     * Proximity shape: an origin or player, a literal role, a radius,
     * and a max. Like {@code <win>} the role must be literal here;
     * values may be nested since they resolve at runtime.
     */
    static Optional<String> proximityError(String name, String args) {
        String shape = name.equals("overlap-players") ? "an origin" : "a player";
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs " + shape
                    + ", a role, a radius, and a max.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 4) {
            return Optional.of("Tag <" + name + "> needs " + shape
                    + ", a role, a radius, and a max.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        String role = CommandPlaceholders.parsePickItem(parts.get(1)).orElse("").strip()
                .toUpperCase(Locale.ROOT);
        if (!role.equals("HUNTER") && !role.equals("SPEEDRUNNER") && !role.equals("ALL")) {
            return Optional.of("Tag <" + name + "> needs HUNTER, SPEEDRUNNER, or ALL.");
        }
        return Optional.empty();
    }

    static Optional<String> activePlayersError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <active-players> needs HUNTER, SPEEDRUNNER, or ALL.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 1) {
            return Optional.of("Tag <active-players> needs HUNTER, SPEEDRUNNER, or ALL.");
        }
        Optional<String> item = CommandPlaceholders.parsePickItem(parts.get(0));
        if (item.isEmpty()) {
            return Optional.of("Tag <active-players> mixes quotes.");
        }
        String role = item.get().strip().toUpperCase(Locale.ROOT);
        if (!role.equals("HUNTER") && !role.equals("SPEEDRUNNER") && !role.equals("ALL")) {
            return Optional.of("Tag <active-players> needs HUNTER, SPEEDRUNNER, or ALL.");
        }
        return Optional.empty();
    }

    /**
     * Player-name shape for {@code <plocation>} and {@code <prole>}:
     * one present, quote-clean name. Like {@code <loseplayer>} the
     * name may resolve from a nested tag at runtime.
     */
    static Optional<String> playerNameError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a player like <" + name + ":Steve>.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 1 || CommandPlaceholders.parsePickItem(parts.get(0)).isEmpty()) {
            return Optional.of("Tag <" + name + "> needs a player like <" + name + ":Steve>.");
        }
        return Optional.empty();
    }

    static Optional<String> arityError(String name, String args, int arity, String what) {
        if (args == null || args.isBlank()
                || CommandPlaceholders.splitPickArgs(args).size() != arity) {
            return Optional.of("Tag <" + name + "> needs " + what + ".");
        }
        for (String part : CommandPlaceholders.splitPickArgs(args)) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    /**
     * Stat tag shape: arity, quote hygiene, plus the key. Keys must be
     * literal here like {@code <random-num>} bounds; nested tags
     * resolve at runtime but the editor cannot see through them.
     */
    static Optional<String> statError(String name, String args, int keyIndex,
            List<String> validKeys) {
        int arity = keyIndex + 1;
        String what = arity == 1 ? "one key" : "a player plus a key";
        if (args == null || args.isBlank()
                || CommandPlaceholders.splitPickArgs(args).size() != arity) {
            return Optional.of("Tag <" + name + "> needs " + what + ".");
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        String key = CommandPlaceholders.parsePickItem(parts.get(keyIndex)).orElse("");
        if (!validKeys.contains(TagStats.normalizeKey(key))) {
            return Optional.of("Unknown <" + name + "> key '" + key.strip() + "'. Valid keys: "
                    + String.join(", ", validKeys) + ".");
        }
        return Optional.empty();
    }

    /** Flag tag shape: a name plus an optional value, quotes parsed. */
    static Optional<String> flagError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a name plus an optional value.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() < 1 || parts.size() > 2) {
            return Optional.of("Tag <" + name + "> needs a name plus an optional value.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        if (FlagStore.parseName(parts.get(0)).isEmpty()) {
            return Optional.of("Tag <" + name + "> needs a name.");
        }
        return Optional.empty();
    }

    static Optional<String> ifError(String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <if> needs a condition plus one or two branches.");
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() < 2 || parts.size() > 3) {
            return Optional.of("Tag <if> needs a condition plus one or two branches.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <if> mixes quotes.");
            }
        }
        String condition = CommandPlaceholders.parsePickItem(parts.get(0)).orElse("").strip();
        try {
            TagExpressions.validateConditionBrackets(condition);
        } catch (TagExpressions.ExprException spacing) {
            return Optional.of("Tag <if> " + spacing.getMessage() + ".");
        }
        if (!TagExpressions.hasComparison(condition)) {
            return Optional.of("Tag <if> condition needs a comparison (==, !=, lt, le, gt, ge).");
        }
        return Optional.empty();
    }
}
