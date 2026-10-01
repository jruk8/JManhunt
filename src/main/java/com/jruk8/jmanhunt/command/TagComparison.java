package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Optional;

/** Comparison engine behind {@link TagExpressions}. */
public final class TagComparison {
    private TagComparison() {
    }

    /**
     * Real nth root of value, or empty when none exists: a zero
     * index, or a negative value under a non-odd-integer index.
     * Indexes 2 and 3 use sqrt/cbrt directly for exact results.
     */
    static Optional<Double> rootOf(double value, double index) {
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

    public record Comparison(String operator, String left, String right) {
    }

    static Comparison findComparison(String part) throws TagExpressions.ExprException {
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
                Integer tagClose = TagExpressions.nestedTagClose(part, index);
                if (tagClose != null) {
                    index = tagClose;
                    continue;
                }
                String operator = matchOperator(part, index);
                if (operator != null) {
                    String left = part.substring(0, index);
                    String right = part.substring(index + operator.length());
                    if (findOperator(left) >= 0 || findOperator(right) >= 0) {
                        throw new TagExpressions.ExprException("only one comparison per 'and' part");
                    }
                    return new Comparison(operator, left, right);
                }
            }
        }
        return null;
    }

    static String matchOperator(String part, int index) {
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
    static String matchWordOperator(String part, int index) {
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

    static int findOperator(String part) {
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
                Integer tagClose = TagExpressions.nestedTagClose(part, index);
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

    static boolean equalsValue(TagMath.Value left, TagMath.Value right) {
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

    static String displayValue(TagMath.Value value) {
        if (value instanceof TagMath.Value.Num number) {
            return TagMath.formatNumber(number.number());
        }
        if (value instanceof TagMath.Value.Text text) {
            return text.text();
        }
        return "null";
    }
}
