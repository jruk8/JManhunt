package com.jruk8.jmanhunt.command;

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
     * Real nth root of value, or empty when none exists: a zero
     * index, or a negative value under a non-odd-integer index.
     * Indexes 2 and 3 use sqrt/cbrt directly for exact results.
     */
    private static Optional<Double> rootOf(double value, double index) {
        if (index == 0.0 || Double.isNaN(index) || Double.isInfinite(index)) {
            return Optional.empty();
        }
        if (index == 2.0) {
            return value < 0 ? Optional.empty() : Optional.of(Math.sqrt(value));
        }
        if (index == 3.0) {
            return Optional.of(Math.cbrt(value));
        }
        if (value < 0) {
            if (index != Math.floor(index) || Math.abs(index) > 1e15) {
                return Optional.empty();
            }
            long whole = (long) index;
            if (whole % 2 == 0) {
                return Optional.empty();
            }
            return Optional.of(-Math.pow(-value, 1.0 / index));
        }
        return Optional.of(Math.pow(value, 1.0 / index));
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
        Comparison comparison = findComparison(part);
        if (comparison == null) {
            throw new ExprException("condition needs a comparison (==, !=, lt, le, gt, ge)");
        }
        TagMath.Value left = TagMath.evalValue(comparison.left(), warn, where);
        TagMath.Value right = TagMath.evalValue(comparison.right(), warn, where);
        return switch (comparison.operator()) {
            case "==" -> equalsValue(left, right);
            case "!=" -> !equalsValue(left, right);
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

    private record Comparison(String operator, String left, String right) {
    }

    private static Comparison findComparison(String part) throws ExprException {
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
                String operator = matchOperator(part, index);
                if (operator != null) {
                    String left = part.substring(0, index);
                    String right = part.substring(index + operator.length());
                    if (findOperator(left) >= 0 || findOperator(right) >= 0) {
                        throw new ExprException("only one comparison per 'and' part");
                    }
                    return new Comparison(operator, left, right);
                }
            }
        }
        return null;
    }

    private static String matchOperator(String part, int index) {
        for (String operator : List.of("==", "!=")) {
            if (part.startsWith(operator, index)) {
                return operator;
            }
        }
        return matchWordOperator(part, index);
    }

    /**
     * Word comparison operator at {@code index} (case-blind), or null
     * when absent. Both sides need a non-letter-or-digit boundary so
     * words like {@code alt} or {@code glee} never split. Angle
     * brackets are deliberately not operators: they would be
     * ambiguous with tag brackets.
     */
    private static String matchWordOperator(String part, int index) {
        for (String word : List.of("lt", "le", "gt", "ge")) {
            int end = index + word.length();
            if (end > part.length()
                    || !part.regionMatches(true, index, word, 0, word.length())) {
                continue;
            }
            char before = index > 0 ? part.charAt(index - 1) : ' ';
            char after = end < part.length() ? part.charAt(end) : ' ';
            if (!Character.isLetterOrDigit(before) && !Character.isLetterOrDigit(after)) {
                return word;
            }
        }
        return null;
    }

    /**
     * Closing {@code >} of the nested tag opening at {@code index},
     * or null when the bracket opens no tag. Only a tag-name letter
     * after {@code <} qualifies for the skip; anything else is a
     * stray bracket that validation reports.
     */
    private static Integer nestedTagClose(String part, int index) {
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
        return findOperator(part) >= 0;
    }

    private static int findOperator(String part) {
        int depth = 0;
        char quote = 0;
        for (int index = 0; index < part.length(); index++) {
            char letter = part.charAt(index);
            if (quote != 0) {
                if (letter == quote) {
                    quote = 0;
                }
            } else if (letter == '"' || letter == '\'') {
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
                if (matchOperator(part, index) != null) {
                    return index;
                }
            }
        }
        return -1;
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

    private static boolean equalsValue(TagMath.Value left, TagMath.Value right) {
        if (left instanceof TagMath.Value.Null && right instanceof TagMath.Value.Null) {
            return true;
        }
        if (left instanceof TagMath.Value.Null || right instanceof TagMath.Value.Null) {
            return false;
        }
        if (left instanceof TagMath.Value.Num leftNumber && right instanceof TagMath.Value.Num rightNumber) {
            return leftNumber.number() == rightNumber.number();
        }
        return displayValue(left).equals(displayValue(right));
    }

    private static boolean orderValue(TagMath.Value left, TagMath.Value right, String operator)
            throws ExprException {
        long leftNumber = wholeNumber(left);
        long rightNumber = wholeNumber(right);
        return switch (operator) {
            case ">" -> leftNumber > rightNumber;
            case "<" -> leftNumber < rightNumber;
            case ">=" -> leftNumber >= rightNumber;
            case "<=" -> leftNumber <= rightNumber;
            default -> throw new ExprException("unknown operator '" + operator + "'");
        };
    }

    private static long wholeNumber(TagMath.Value value) throws ExprException {
        if (value instanceof TagMath.Value.Num number && number.number() == Math.floor(number.number())
                && Double.isFinite(number.number())) {
            return (long) number.number();
        }
        throw new ExprException("ordering comparisons need whole numbers");
    }

    private static String displayValue(TagMath.Value value) {
        if (value instanceof TagMath.Value.Num number) {
            return TagMath.formatNumber(number.number());
        }
        if (value instanceof TagMath.Value.Text text) {
            return text.text();
        }
        return "null";
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
        Optional<String> thenBranch = CommandPlaceholders.parsePickItem(parts.get(1));
        Optional<String> elseBranch = parts.size() > 2
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
            Optional<Double> root = rootOf(numbers.get(0), numbers.get(1));
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
        context.losePlayer(player.get(), reasonArg(args));
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
        context.winMatch(role, reasonArg(args));
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

    /**
     * One {@code <if>} span: offsets plus the raw args between the
     * root colon and the balancing {@code >}. End is exclusive.
     */
    public record IfSpan(int start, int end, String args) {
    }

    /**
     * Finds {@code <if>} spans, whose conditions may hold nested tags
     * that the plain innermost-tag scan cannot see. Quotes nest by
     * alternation and only unquoted brackets count toward depth.
     * Spans that never balance are skipped.
     */
    public static List<IfSpan> findIfSpans(String line) {
        List<IfSpan> spans = new ArrayList<>();
        int index = 0;
        while (index < line.length()) {
            int open = line.indexOf('<', index);
            if (open < 0) {
                return spans;
            }
            IfSpan span = ifSpanAt(line, open);
            if (span != null) {
                spans.add(span);
            }
            index = open + 1;
        }
        return spans;
    }

    private static IfSpan ifSpanAt(String line, int open) {
        int cursor = open + 1;
        while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
            cursor++;
        }
        int rootEnd = cursor;
        while (rootEnd < line.length() && isRootChar(line.charAt(rootEnd))) {
            rootEnd++;
        }
        if (!line.substring(cursor, rootEnd).equalsIgnoreCase("if")) {
            return null;
        }
        cursor = rootEnd;
        while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= line.length() || (line.charAt(cursor) != ':'
                && line.charAt(cursor) != '>')) {
            return null;
        }
        int argsStart = cursor + 1;
        Integer end = spanEnd(line, open);
        if (end == null) {
            return null;
        }
        return new IfSpan(open, end + 1, line.substring(argsStart, end));
    }

    static boolean isRootChar(char letter) {
        return letter == '_' || letter == '-' || Character.isLetterOrDigit(letter);
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
    public static boolean hasNestedIf(String args) {
        String lower = args.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int at = lower.indexOf("<if", from);
            if (at < 0) {
                return false;
            }
            int after = at + 3;
            if (after >= lower.length()) {
                return true;
            }
            char next = lower.charAt(after);
            if (next == ':' || next == '>' || Character.isWhitespace(next)) {
                return true;
            }
            from = after;
        }
    }

    /** True when the line is just {@code exit}, ignoring one leading slash. */
    public static boolean isExit(String line) {
        return stripSlash(line).strip().equals("exit");
    }

    /** True when {@code exit} leads the line but is not alone. */
    public static boolean isExitMisuse(String line) {
        String stripped = stripSlash(line).strip();
        if (stripped.equals("exit")) {
            return false;
        }
        int end = stripped.indexOf(' ');
        int tab = stripped.indexOf('\t');
        if (tab >= 0 && (end < 0 || tab < end)) {
            end = tab;
        }
        String first = end < 0 ? stripped : stripped.substring(0, end);
        return first.equals("exit");
    }

    private static String stripSlash(String line) {
        String stripped = line.strip();
        return stripped.startsWith("/") ? stripped.substring(1) : stripped;
    }

    /**
     * Dispatch text for a parsed command line. Tag-only lines evaluate
     * to nothing (a bare {@code <pflag:...>} set, {@code <gmessage:...>},
     * ...), and dispatching blank text crashes the server dispatcher,
     * so blank lines resolve empty and the caller skips them quietly.
     * One leading slash is dropped; surrounding whitespace is trimmed.
     */
    public static Optional<String> dispatchableLine(String parsed) {
        String line = stripSlash(parsed);
        return line.isEmpty() ? Optional.empty() : Optional.of(line);
    }

    /**
     * True when a dispatchable line is exactly the engine null
     * literal: trimmed, case-sensitive lowercase. Callers warn and
     * skip dispatch instead of sending {@code null} to the console
     * dispatcher, which would only raise unknown-command noise.
     */
    public static boolean isPureNull(String line) {
        return line.strip().equals("null");
    }
}
