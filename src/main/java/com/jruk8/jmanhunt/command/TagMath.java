package com.jruk8.jmanhunt.command;

/**
 * Bare arithmetic for modifier commands. Expressions use standard
 * precedence: parentheses, exponents ({@code **}, right associative),
 * unary signs, then {@code * / // %} left to right, then
 * {@code + -} left to right. {@code //} is floor division,
 * {@code %} is remainder, and {@code ??} is null coalescing with
 * the loosest binding. Quoted spans group subexpressions and the
 * {@code null} literal propagates through math. No Bukkit types.
 */
public final class TagMath {

    private TagMath() {
    }

    /** Text that is not math at all: callers leave it verbatim. */
    public static final class SyntaxException extends Exception {
        SyntaxException(String message) {
            super(message);
        }
    }

    /** Math that parses but cannot run, like division by zero. */
    public static final class EvalException extends Exception {
        EvalException(String message) {
            super(message);
        }
    }

    /**
     * Evaluates one expression, or null when it yields null. Throws
     * {@link SyntaxException} when the text is not an expression and
     * {@link EvalException} when it parses but cannot run. Surrounding
     * and internal whitespace is ignored: bare-command detection splits
     * tokens on whitespace before calling, so spaced prose never
     * arrives here from that path.
     */
    public static Double evaluate(String expression) throws SyntaxException, EvalException {
        String compact = expression.replaceAll("\\s+", "");
        if (compact.isEmpty()) {
            throw new SyntaxException("empty expression");
        }
        Parser parser = new Parser(compact);
        Double value = parser.coalesce();
        if (parser.pos != compact.length()) {
            throw new SyntaxException("unexpected '" + compact.charAt(parser.pos) + "'");
        }
        return value;
    }

    /** Whole numbers print bare; decimals trim trailing zeros. */
    public static String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)
                && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        String text = Double.toString(value);
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return text;
    }

    private static final class Parser {
        private final String text;
        private int pos;

        private Parser(String text) {
            this.text = text;
        }

        private Double coalesce() throws SyntaxException, EvalException {
            Double left = additive();
            if (peek("??")) {
                pos += 2;
                Double right = coalesce();
                return left != null ? left : right;
            }
            return left;
        }

        private Double additive() throws SyntaxException, EvalException {
            Double value = multiplicative();
            while (peek("+") || peek("-")) {
                boolean plus = text.charAt(pos) == '+';
                pos++;
                Double rhs = multiplicative();
                value = apply(value, rhs, plus ? "+" : "-");
            }
            return value;
        }

        private Double multiplicative() throws SyntaxException, EvalException {
            Double value = unary();
            while (true) {
                if (peek("//")) {
                    pos += 2;
                    value = apply(value, unary(), "//");
                } else if (peek("*") || peek("/") || peek("%")) {
                    String op = String.valueOf(text.charAt(pos));
                    pos++;
                    value = apply(value, unary(), op);
                } else {
                    return value;
                }
            }
        }

        private Double unary() throws SyntaxException, EvalException {
            if (peek("+")) {
                pos++;
                return unary();
            }
            if (peek("-")) {
                pos++;
                Double value = unary();
                return value == null ? null : -value;
            }
            return power();
        }

        private Double power() throws SyntaxException, EvalException {
            Double base = primary();
            if (peek("**")) {
                pos += 2;
                return apply(base, unary(), "**");
            }
            return base;
        }

        private Double primary() throws SyntaxException, EvalException {
            if (peek("(")) {
                pos++;
                Double value = coalesce();
                if (!peek(")")) {
                    throw new SyntaxException("unclosed '('");
                }
                pos++;
                return value;
            }
            char letter = peekChar();
            if (letter == '"' || letter == '\'') {
                return group();
            }
            if (peek("null") && !isWordChar(lookahead(4))) {
                pos += 4;
                return null;
            }
            return number();
        }

        private Double group() throws SyntaxException, EvalException {
            char quote = text.charAt(pos);
            int end = text.indexOf(quote, pos + 1);
            if (end < 0) {
                throw new SyntaxException("unbalanced quote");
            }
            String inner = text.substring(pos + 1, end);
            pos = end + 1;
            return TagMath.evaluate(inner);
        }

        private Double number() throws SyntaxException {
            int start = pos;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            if (pos < text.length() && text.charAt(pos) == '.') {
                pos++;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            if (start == pos || (start + 1 == pos && text.charAt(start) == '.')) {
                throw new SyntaxException("expected a number");
            }
            try {
                return Double.parseDouble(text.substring(start, pos));
            } catch (NumberFormatException invalid) {
                throw new SyntaxException("bad number");
            }
        }

        private Double apply(Double left, Double right, String op) throws EvalException {
            if (left == null || right == null) {
                return null;
            }
            double result;
            switch (op) {
                case "+":
                    result = left + right;
                    break;
                case "-":
                    result = left - right;
                    break;
                case "*":
                    result = left * right;
                    break;
                case "/":
                    if (right == 0.0) {
                        throw new EvalException("division by zero");
                    }
                    result = left / right;
                    break;
                case "//":
                    if (right == 0.0) {
                        throw new EvalException("division by zero");
                    }
                    result = Math.floor(left / right);
                    break;
                case "%":
                    if (right == 0.0) {
                        throw new EvalException("division by zero");
                    }
                    result = left % right;
                    break;
                default:
                    result = Math.pow(left, right);
                    break;
            }
            if (!Double.isFinite(result)) {
                throw new EvalException("result out of range");
            }
            return result;
        }

        private boolean peek(String token) {
            return text.startsWith(token, pos);
        }

        private char peekChar() {
            return pos < text.length() ? text.charAt(pos) : 0;
        }

        private char lookahead(int offset) {
            return pos + offset < text.length() ? text.charAt(pos + offset) : 0;
        }

        private static boolean isWordChar(char letter) {
            return letter == '_' || Character.isLetterOrDigit(letter);
        }
    }
}
