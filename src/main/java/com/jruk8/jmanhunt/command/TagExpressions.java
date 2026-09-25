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

    /** Evaluated operand: null, number, or literal text. */
    public sealed interface Value permits Value.Null, Value.Num, Value.Text {
        record Null() implements Value {
        }

        record Num(double number) implements Value {
        }

        record Text(String text) implements Value {
        }
    }

    /**
     * Evaluates one operand: math when it parses (numbers, groups,
     * {@code ??}), else literal text. Math that parses but cannot
     * run warns and yields 0.
     */
    static Value evalValue(String raw, Consumer<String> warn, String where) {
        String text = unquote(raw.strip());
        try {
            Double number = TagMath.evaluate(text);
            return number == null ? new Value.Null() : new Value.Num(number);
        } catch (TagMath.SyntaxException syntax) {
            return new Value.Text(text);
        } catch (TagMath.EvalException failed) {
            warn.accept(where + ": " + failed.getMessage() + ", using 0");
            return new Value.Num(0.0);
        }
    }

    /** Strips one balanced outer quote layer, else returns the text. */
    static String unquote(String text) {
        if (text.length() >= 2) {
            char first = text.charAt(0);
            if ((first == '"' || first == '\'') && text.charAt(text.length() - 1) == first
                    && text.indexOf(first, 1) == text.length() - 1) {
                return text.substring(1, text.length() - 1);
            }
        }
        return text;
    }

    /**
     * Evaluates a condition: {@code or} splits first, then {@code and},
     * then one comparison per part. The words match case-blindly and
     * need whitespace on both sides.
     */
    static boolean evalCondition(String condition, Consumer<String> warn, String where)
            throws ExprException {
        for (String orPart : splitWords(condition, "or")) {
            boolean matches = true;
            for (String andPart : splitWords(orPart, "and")) {
                if (!evalComparison(andPart, warn, where)) {
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

    private static boolean evalComparison(String part, Consumer<String> warn, String where)
            throws ExprException {
        validateConditionSpacing(part);
        Comparison comparison = findComparison(part);
        if (comparison == null) {
            throw new ExprException("condition needs a comparison (==, !=, >, <, >=, <=)");
        }
        Value left = evalValue(comparison.left(), warn, where);
        Value right = evalValue(comparison.right(), warn, where);
        return switch (comparison.operator()) {
            case "==" -> equalsValue(left, right);
            case "!=" -> !equalsValue(left, right);
            default -> orderValue(left, right, comparison.operator());
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
        for (String operator : List.of("==", "!=", ">=", "<=", ">", "<")) {
            if (part.startsWith(operator, index)) {
                return operator;
            }
        }
        return null;
    }

    /**
     * Closing {@code >} of the nested tag opening at {@code index},
     * or null when the bracket is a comparison. A space after
     * {@code <} always means a comparison, as does a spaced
     * {@code <=} (checked before the balance scan so a later
     * {@code >} cannot swallow the operator); anything else must
     * start with a tag-name letter to qualify for the skip.
     */
    private static Integer nestedTagClose(String part, int index) {
        if (part.charAt(index) != '<' || index + 1 >= part.length()
                || Character.isWhitespace(part.charAt(index + 1))) {
            return null;
        }
        if (isSpacedEquals(part, index) || !Character.isLetter(part.charAt(index + 1))) {
            return null;
        }
        return spanEnd(part, index);
    }

    /** True for {@code <=} with a space (or edge) on both sides. */
    private static boolean isSpacedEquals(String part, int index) {
        if (part.charAt(index + 1) != '=') {
            return false;
        }
        char before = index > 0 ? part.charAt(index - 1) : ' ';
        int after = index + 2;
        char afterChar = after < part.length() ? part.charAt(after) : ' ';
        return Character.isWhitespace(before) && Character.isWhitespace(afterChar);
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
     * Rejects top-level {@code < > <= >=} without whitespace on both
     * sides. Shared by runtime evaluation and static validation.
     */
    static void validateConditionSpacing(String part) throws ExprException {
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
                if (operator != null && (operator.contains("<") || operator.contains(">"))) {
                    char before = index > 0 ? part.charAt(index - 1) : 0;
                    int afterIndex = index + operator.length();
                    char after = afterIndex < part.length() ? part.charAt(afterIndex) : 0;
                    if (!Character.isWhitespace(before) || !Character.isWhitespace(after)) {
                        throw new ExprException(
                                "'" + operator + "' needs a space on both sides");
                    }
                }
            }
        }
    }

    private static boolean equalsValue(Value left, Value right) {
        if (left instanceof Value.Null && right instanceof Value.Null) {
            return true;
        }
        if (left instanceof Value.Null || right instanceof Value.Null) {
            return false;
        }
        if (left instanceof Value.Num leftNumber && right instanceof Value.Num rightNumber) {
            return leftNumber.number() == rightNumber.number();
        }
        return displayValue(left).equals(displayValue(right));
    }

    private static boolean orderValue(Value left, Value right, String operator)
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

    private static long wholeNumber(Value value) throws ExprException {
        if (value instanceof Value.Num number && number.number() == Math.floor(number.number())
                && Double.isFinite(number.number())) {
            return (long) number.number();
        }
        throw new ExprException("ordering comparisons need whole numbers");
    }

    private static String displayValue(Value value) {
        if (value instanceof Value.Num number) {
            return TagMath.formatNumber(number.number());
        }
        if (value instanceof Value.Text text) {
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
            Value value = evalValue(item.get(), context.scope()::warn, tag);
            if (!(value instanceof Value.Num number)) {
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
        }
        return TagMath.formatNumber(result);
    }

    /**
     * {@code <gmessage:text>} and {@code <pmessage:text>}: sends
     * through the context sinks and returns empty.
     */
    static String message(String tag, String name, String args, TagContext context) {
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() != 1) {
            context.scope().warn("Tag <" + name + "> needs one text: " + tag);
            return "";
        }
        Optional<String> item = CommandPlaceholders.parsePickItem(parts.get(0));
        if (item.isEmpty() && !parts.get(0).isBlank()) {
            context.scope().warn("Tag <" + name + "> has malformed quotes: " + tag);
            return "";
        }
        String text = item.orElse("");
        if (name.equals("gmessage")) {
            context.sendGlobalMessage(text);
        } else {
            context.sendPlayerMessage(text);
        }
        return "";
    }

    /**
     * {@code <gsound:id,pitch,volume>} and {@code <psound:...>}:
     * plays through the context sinks and returns empty. Pitch and
     * volume default to 1 and fall back to 1 on bad numbers.
     */
    static String sound(String tag, String name, String args, TagContext context) {
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() < 1 || parts.size() > 3) {
            context.scope().warn("Tag <" + name + "> needs an id plus pitch and volume: " + tag);
            return "";
        }
        Optional<String> id = CommandPlaceholders.parsePickItem(parts.get(0));
        if (id.isEmpty() || id.get().isBlank()) {
            context.scope().warn("Tag <" + name + "> needs a sound id: " + tag);
            return "";
        }
        float pitch = soundNumber(parts, 1, tag, context);
        float volume = soundNumber(parts, 2, tag, context);
        if (name.equals("gsound")) {
            context.playGlobalSound(id.get(), pitch, volume);
        } else {
            context.playPlayerSound(id.get(), pitch, volume);
        }
        return "";
    }

    private static float soundNumber(List<String> parts, int index, String tag,
            TagContext context) {
        if (index >= parts.size()) {
            return 1.0f;
        }
        Optional<String> item = CommandPlaceholders.parsePickItem(parts.get(index));
        if (item.isEmpty()) {
            context.scope().warn("Tag sound number has malformed quotes, using 1: " + tag);
            return 1.0f;
        }
        try {
            return Float.parseFloat(item.get().strip());
        } catch (NumberFormatException invalid) {
            context.scope().warn("Tag sound number '" + item.get().strip()
                    + "' is not a number, using 1: " + tag);
            return 1.0f;
        }
    }

    /**
     * One {@code <if>} span: offsets plus the raw args between the
     * root colon and the balancing {@code >}. End is exclusive.
     */
    public record IfSpan(int start, int end, String args) {
    }

    /**
     * Finds {@code <if>} spans, whose conditions may hold bare
     * {@code < > <= >=} that the plain innermost-tag scan cannot see.
     * Quotes nest by alternation and only unquoted brackets count
     * toward depth. Spans that never balance are skipped.
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

    private static boolean isRootChar(char letter) {
        return letter == '_' || letter == '-' || Character.isLetterOrDigit(letter);
    }

    private static Integer spanEnd(String line, int open) {
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
}
