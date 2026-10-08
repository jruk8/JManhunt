package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.player.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Extended tag expressions for modifier commands: {@code <if>},
 * {@code <min>}, {@code <max>}, {@code <clamp>}, {@code <id>},
 * message and sound tags, math values, conditions, and the lone
 * {@code exit} line. Pure: side effects run through the context
 * sinks. No Bukkit types.
 */
public final class TagExpressions {

    private TagExpressions() {
    }

    /** Condition structure problem: callers warn and yield empty. */
    public static final class ExprException extends Exception {
        ExprException(String message) {
            super(message);
        }
    }

    /**
     * One-arg math tags ({@code floor}, {@code ceil}, {@code round},
     * {@code abs}, {@code sign}): the arg may itself be math.
     * Non-numeric input (including {@code null}) warns plus
     * {@code "null"}. Rounds half up like {@code Math.round} without
     * long overflow.
     */
    static String mathUnary(String tag, String op, String args, TagContext context) {
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() != 1) {
            context.scope().warn("Tag <" + op + "> needs one number like <" + op + ":2.5>: " + tag);
            return "null";
        }
        Optional<String> item = CommandPlaceholders.parsePickItem(parts.get(0));
        if (item.isEmpty() || item.get().isBlank()) {
            context.scope().warn("Tag <" + op + "> needs one number like <" + op + ":2.5>: " + tag);
            return "null";
        }
        TagMath.Value value = TagMath.evalValue(item.get(), context.scope()::warn, tag);
        if (!(value instanceof TagMath.Value.Num num)) {
            context.scope().warn("Tag <" + op + "> needs a number, using null: " + tag);
            return "null";
        }
        double result = switch (op) {
            case "floor" -> Math.floor(num.number());
            case "ceil" -> Math.ceil(num.number());
            case "round" -> Math.floor(num.number() + 0.5);
            case "abs" -> Math.abs(num.number());
            case "sqrt" -> Math.sqrt(num.number());
            case "cbrt" -> Math.cbrt(num.number());
            default -> Math.signum(num.number());
        };
        if (Double.isNaN(result)) {
            context.scope().warn("Tag <" + op + "> has no real result for this input: " + tag);
            return "null";
        }
        return TagMath.formatNumber(result);
    }

    /**
     * Evaluates a condition: {@code or} splits first, then {@code and},
     * then leading {@code not} words, then one comparison per part.
     * The words match case-blindly; {@code and} and {@code or} need
     * whitespace on both sides while each {@code not} needs it after.
     */
    static boolean evalCondition(String condition, Consumer<String> warn, String where)
            throws ExprException {
        for (String orPart : splitWords(condition, "or")) {
            boolean matches = true;
            for (String andPart : splitWords(orPart, "and")) {
                if (!evalNotPart(andPart, warn, where)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    /**
     * One and-part with leading {@code not} words stripped: each word
     * flips the comparison, so chains associate right ({@code not not
     * x} is {@code not (not x)}). A word matches case-blindly at the
     * part start with whitespace or the end right after, which keeps
     * {@code notable} and {@code knot} plain text.
     */
    private static boolean evalNotPart(String part, Consumer<String> warn, String where)
            throws ExprException {
        String rest = part.strip();
        boolean negated = false;
        while (isNotWord(rest)) {
            negated = !negated;
            rest = rest.substring(3).strip();
        }
        boolean value = evalComparison(rest, warn, where);
        return negated ? !value : value;
    }

    private static boolean isNotWord(String text) {
        if (text.length() < 3 || !text.regionMatches(true, 0, "not", 0, 3)) {
            return false;
        }
        return text.length() == 3 || Character.isWhitespace(text.charAt(3));
    }

    private static boolean evalComparison(String part, Consumer<String> warn, String where)
            throws ExprException {
        validateConditionBrackets(part);
        TagComparison.Comparison comparison = TagComparison.findComparison(part);
        if (comparison == null) {
            throw new ExprException("condition needs a comparison (==, !=, lt, le, gt, ge)");
        }
        TagMath.Value left = TagMath.evalValue(comparison.left(), warn, where);
        TagMath.Value right = TagMath.evalValue(comparison.right(), warn, where);
        return switch (comparison.operator()) {
            case "==" -> TagComparison.equalsValue(left, right);
            case "!=" -> !TagComparison.equalsValue(left, right);
            default -> orderValue(left, right, normalizeOperator(comparison.operator()));
        };
    }

    /** Maps word operators to the symbols orderValue compares with. */
    private static String normalizeOperator(String operator) {
        return switch (operator) {
            case "lt" -> "<";
            case "le" -> "<=";
            case "gt" -> ">";
            case "ge" -> ">=";
            default -> operator;
        };
    }

    /**
     * Closing {@code >} of the nested tag opening at {@code index},
     * or null when the bracket opens no tag. Only a tag-name letter
     * after {@code <} qualifies for the skip; anything else is a
     * stray bracket that validation reports.
     */
    static Integer nestedTagClose(String part, int index) {
        if (part.charAt(index) != '<' || index + 1 >= part.length()
                || Character.isWhitespace(part.charAt(index + 1))) {
            return null;
        }
        if (!Character.isLetter(part.charAt(index + 1))) {
            return null;
        }
        return spanEnd(part, index);
    }

    /**
     * True when a comparison sits outside quotes, groups, and
     * nested tags. Shared by runtime evaluation and static
     * validation.
     */
    static boolean hasComparison(String part) {
        return TagComparison.findOperator(part) >= 0;
    }

    /**
     * Rejects stray angle brackets outside quotes, groups, and nested
     * tags: they are not comparisons anymore. Shared by runtime
     * evaluation and static validation.
     */
    static void validateConditionBrackets(String part) throws ExprException {
        int depth = 0;
        char quote = 0;
        for (int index = 0; index < part.length(); index++) {
            char letter = part.charAt(index);
            if (quote != 0) {
                if (letter == quote) {
                    quote = 0;
                }
                continue;
            }
            if (letter == '"' || letter == '\'') {
                quote = letter;
            } else if (letter == '(') {
                depth++;
            } else if (letter == ')') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0) {
                Integer tagClose = nestedTagClose(part, index);
                if (tagClose != null) {
                    index = tagClose;
                    continue;
                }
                if (letter == '<' || letter == '>') {
                    throw new ExprException(
                            "'" + letter + "' is not a comparison; use lt, le, gt, ge");
                }
            }
        }
    }

    private static boolean orderValue(TagMath.Value left, TagMath.Value right, String operator)
            throws ExprException {
        double leftNumber = orderNumber(left);
        double rightNumber = orderNumber(right);
        return switch (operator) {
            case ">" -> leftNumber > rightNumber;
            case "<" -> leftNumber < rightNumber;
            case ">=" -> leftNumber >= rightNumber;
            case "<=" -> leftNumber <= rightNumber;
            default -> throw new ExprException("unknown operator '" + operator + "'");
        };
    }

    /**
     * The double behind an ordering operand. Any finite number works;
     * text, null, NaN, and infinities throw. Pure for tests.
     */
    static double orderNumber(TagMath.Value value) throws ExprException {
        if (value instanceof TagMath.Value.Num number && Double.isFinite(number.number())) {
            return number.number();
        }
        throw new ExprException("ordering comparisons need numbers");
    }

    private static List<String> splitWords(String text, String word) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        int index = 0;
        while (index < text.length()) {
            char letter = text.charAt(index);
            if (quote != 0) {
                current.append(letter);
                if (letter == quote) {
                    quote = 0;
                }
                index++;
            } else if (letter == '"' || letter == '\'') {
                quote = letter;
                current.append(letter);
                index++;
            } else if (letter == '(') {
                depth++;
                current.append(letter);
                index++;
            } else if (letter == ')') {
                depth = Math.max(0, depth - 1);
                current.append(letter);
                index++;
            } else if (depth == 0 && isWord(text, index, word)) {
                parts.add(current.toString());
                current.setLength(0);
                index += word.length() + 1;
            } else {
                current.append(letter);
                index++;
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static boolean isWord(String text, int index, String word) {
        if (index == 0 || !Character.isWhitespace(text.charAt(index - 1))) {
            return false;
        }
        int end = index + word.length();
        return end < text.length() && Character.isWhitespace(text.charAt(end))
                && text.substring(index, end).toLowerCase(Locale.ROOT).equals(word);
    }

    /**
     * {@code <if:condition,then,else>}: branches are literal text
     * (quotes stripped); a missing else yields empty. Any structural
     * problem warns and yields empty.
     */
    static String ifEval(String tag, String args, TagContext context) {
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() < 2 || parts.size() > 3) {
            context.scope().warn("Tag <if> needs a condition plus one or two branches: " + tag);
            return "";
        }
        Optional<String> condition = CommandPlaceholders.parsePickItem(parts.get(0));
        Optional<String> thenBranch = parts.get(1).isBlank()
                ? Optional.of("") : CommandPlaceholders.parsePickItem(parts.get(1));
        Optional<String> elseBranch = parts.size() > 2 && !parts.get(2).isBlank()
                ? CommandPlaceholders.parsePickItem(parts.get(2)) : Optional.of("");
        if (condition.isEmpty() || thenBranch.isEmpty() || elseBranch.isEmpty()
                || condition.get().isBlank()) {
            context.scope().warn("Tag <if> has malformed branches: " + tag);
            return "";
        }
        try {
            boolean result = evalCondition(condition.get(), context.scope()::warn, tag);
            return result ? thenBranch.get() : elseBranch.get();
        } catch (ExprException failed) {
            context.scope().warn("Tag <if> " + failed.getMessage() + ": " + tag);
            return "";
        }
    }

    /** {@code <min:a,b>}, {@code <max:a,b>}, {@code <clamp:x,low,high>}. */
    static String minMaxClamp(String tag, String name, String args, TagContext context) {
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        int arity = name.equals("clamp") ? 3 : 2;
        if (parts.size() != arity) {
            context.scope().warn("Tag <" + name + "> needs " + arity + " numbers: " + tag);
            return "0";
        }
        List<Double> numbers = new ArrayList<>();
        for (String part : parts) {
            Optional<String> item = CommandPlaceholders.parsePickItem(part);
            if (item.isEmpty()) {
                context.scope().warn("Tag <" + name + "> has a malformed number: " + tag);
                return "0";
            }
            TagMath.Value value = TagMath.evalValue(item.get(), context.scope()::warn, tag);
            if (!(value instanceof TagMath.Value.Num number)) {
                context.scope().warn("Tag <" + name + "> needs numbers: " + tag);
                return "0";
            }
            numbers.add(number.number());
        }
        double result = name.equals("max")
                ? Math.max(numbers.get(0), numbers.get(1))
                : numbers.get(0);
        if (name.equals("min")) {
            result = Math.min(numbers.get(0), numbers.get(1));
        } else if (name.equals("clamp")) {
            result = Math.min(Math.max(numbers.get(0), numbers.get(1)), numbers.get(2));
        } else if (name.equals("root")) {
            Optional<Double> root = TagComparison.rootOf(numbers.get(0), numbers.get(1));
            if (root.isEmpty() || !Double.isFinite(root.get())) {
                context.scope().warn("Tag <root> needs a real result: no zero index, "
                        + "no even root of a negative: " + tag);
                return "0";
            }
            result = root.get();
        }
        return TagMath.formatNumber(result);
    }

    /**
     * {@code <default:value,fallback>}: the value unless it is blank
     * or case-blind {@code null}, else the fallback. Quote-parsed
     * like list args; misuse warns plus {@code "null"}.
     */
    static String defaultValue(String tag, String args, TagContext context) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 2) {
            context.scope().warn("Tag <default> needs a value plus a fallback like "
                    + "<default:<pflag:x>,0>: " + tag);
            return "null";
        }
        Optional<String> value = CommandPlaceholders.parsePickItem(parts.get(0));
        Optional<String> fallback = CommandPlaceholders.parsePickItem(parts.get(1));
        if ((value.isEmpty() && !parts.get(0).isBlank())
                || (fallback.isEmpty() && !parts.get(1).isBlank())) {
            context.scope().warn("Tag <default> mixes quotes: " + tag);
            return "null";
        }
        String text = value.orElse("");
        if (text.isBlank() || text.strip().equalsIgnoreCase("null")) {
            return fallback.orElse("");
        }
        return text;
    }

    /**
     * {@code <loseplayer:player,reason>}: eliminates one player by
     * name through the context sink and returns empty. The reason is
     * everything after the first comma and defaults to unknown
     * reason; the head arg is required.
     */
    static String loseplayer(String tag, String args, TagContext context) {
        Optional<String> player = headArg(tag, args, context, "loseplayer", "a player");
        if (player.isEmpty()) {
            return "";
        }
        context.losePlayer(EngineEscapes.restore(player.get()),
                EngineEscapes.restore(reasonArg(args)));
        return "";
    }

    /**
     * {@code <win:ROLE,reason>}: ends the match for HUNTER or
     * SPEEDRUNNER through the context sink and returns empty. The
     * reason is everything after the first comma and defaults to
     * unknown reason; it becomes the win screen reason.
     */
    static String win(String tag, String args, TagContext context) {
        Optional<String> head = headArg(tag, args, context, "win", "a role");
        if (head.isEmpty()) {
            return "";
        }
        String role = head.get().toUpperCase(Locale.ROOT);
        if (!role.equals("HUNTER") && !role.equals("SPEEDRUNNER")) {
            context.scope().warn("Tag <win> needs HUNTER or SPEEDRUNNER: " + tag);
            return "";
        }
        context.winMatch(EngineEscapes.restore(role),
                EngineEscapes.restore(reasonArg(args)));
        return "";
    }

    /**
     * {@code <pswitch:player,ROLE>}: switches one player to the
     * named role through the context sink and returns empty. The
     * player is the text before the first comma (quote-checked);
     * the role is everything after it and must parse. Match and
     * roster failures warn from the sink, like {@code <loseplayer>}.
     */
    static String pswitch(String tag, String args, TagContext context) {
        Optional<String> player = headArg(tag, args, context, "pswitch", "a player");
        if (player.isEmpty()) {
            return "";
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() != 2) {
            context.scope().warn("Tag <pswitch> needs a player and a role: " + tag);
            return "";
        }
        Optional<String> rawRole = CommandPlaceholders.parsePickItem(parts.get(1));
        Optional<Role> role = rawRole.flatMap(Role::parse);
        if (role.isEmpty()) {
            context.scope().warn("Tag <pswitch> needs a valid role: " + tag);
            return "";
        }
        context.switchPlayerRole(EngineEscapes.restore(player.get()), role.get().name());
        return "";
    }

    /**
     * Head arg of a player/reason tag: the text before the first
     * comma, quote-checked. Warns and returns empty when missing.
     */
    private static Optional<String> headArg(String tag, String args, TagContext context,
            String name, String what) {
        if (args == null || args.isBlank()) {
            context.scope().warn("Tag <" + name + "> needs " + what + ": " + tag);
            return Optional.empty();
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        Optional<String> head = CommandPlaceholders.parsePickItem(parts.get(0));
        if (head.isEmpty()) {
            context.scope().warn("Tag <" + name + "> needs " + what + ": " + tag);
            return Optional.empty();
        }
        return head;
    }

    /**
     * Reason of a player/reason tag: everything after the first
     * comma, so reasons may contain commas. One balanced quote
     * layer is stripped and the rest passes through untouched;
     * blank reasons default to unknown reason.
     */
    private static String reasonArg(String args) {
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() < 2) {
            return "unknown reason";
        }
        String joined = String.join(",", parts.subList(1, parts.size())).strip();
        String reason = TagMath.unquote(joined);
        return reason.isBlank() ? "unknown reason" : reason;
    }

    static boolean isRootChar(char letter) {
        return letter == '_' || letter == '-' || letter == '.'
                || Character.isLetterOrDigit(letter);
    }

    /** Inclusive index of the balancing {@code >}, or null when unclosed. Shared with loops. */
    static Integer spanEnd(String line, int open) {
        int depth = 0;
        StringBuilder quotes = new StringBuilder();
        for (int index = open; index < line.length(); index++) {
            char letter = line.charAt(index);
            if (quotes.length() > 0) {
                if (letter == quotes.charAt(quotes.length() - 1)) {
                    quotes.setLength(quotes.length() - 1);
                } else if (letter == '"' || letter == '\'') {
                    quotes.append(letter);
                }
                continue;
            }
            if (letter == '"' || letter == '\'') {
                quotes.append(letter);
            } else if (letter == '<') {
                depth++;
            } else if (letter == '>') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return null;
    }

    /** True when the args hold another {@code <if} tag. */
    /** True when the line is just {@code exit}, ignoring one leading slash. */
    /** True when {@code exit} leads the line but is not alone. */
}
