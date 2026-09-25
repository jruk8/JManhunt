package com.jruk8.jmanhunt.command;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Bare arithmetic: precedence, parens, exponents, floor division,
 * remainder, unary signs, groups, and null coalescing. Anything
 * unparseable throws syntax (verbatim upstream); anything
 * unparseable-but-structured like division by zero throws eval.
 */
class TagMathTest {

    private static double value(String expression) throws Exception {
        Double result = TagMath.evaluate(expression);
        if (result == null) {
            throw new AssertionError("expected a number for: " + expression);
        }
        return result;
    }

    @Test
    void precedenceOrdersMulBeforeAdd() throws Exception {
        assertEquals(14.0, value("2+3*4"));
        assertEquals(20.0, value("(2+3)*4"));
        assertEquals(2.0, value("10-2*4"));
        assertEquals(32.0, value("(10-2)*4"));
    }

    @Test
    void exponentsBindBeforeUnaryAndMultiply() throws Exception {
        assertEquals(512.0, value("2**3**2"));
        assertEquals(-4.0, value("-2**2"));
        assertEquals(4.0, value("(-2)**2"));
        assertEquals(18.0, value("2*3**2"));
        assertEquals(0.5, value("2**-1"));
    }

    @Test
    void slashesDivideFloorAndRemainder() throws Exception {
        assertEquals(2.5, value("10/4"));
        assertEquals(2.0, value("8//3"));
        assertEquals(-3.0, value("-8//3"));
        assertEquals(2.0, value("8%3"));
        assertEquals(-2.0, value("-8%3"));
        assertEquals(1.5, value("7.5%2"));
    }

    @Test
    void unarySignsChain() throws Exception {
        assertEquals(-5.0, value("-5"));
        assertEquals(5.0, value("--5"));
        assertEquals(-6.0, value("2*-3"));
        assertEquals(8.0, value("5--3"));
    }

    @Test
    void decimalsAndParensMix() throws Exception {
        assertEquals(2.0, value("0.5*4"));
        assertEquals(225.0, value("((2+3)*(4-1))**2"));
        assertEquals(0.5, value(".5"));
        assertEquals(11.0, value("8+5-2"));
    }

    @Test
    void nullCoalescesAndPropagates() throws Exception {
        assertEquals(5.0, value("null ?? 5"));
        assertEquals(8.0, value("8 ?? 9"));
        assertEquals(3.0, value("null ?? null ?? 3"));
        assertNull(TagMath.evaluate("null"));
        assertNull(TagMath.evaluate("null+1"));
        assertEquals(7.0, value("(null ?? 6)+1"));
    }

    @Test
    void quotedSpansGroup() throws Exception {
        assertEquals(26.0, value("'8+5'*2"));
        assertEquals(5.0, value("\"(2+3)\""));
        assertEquals(-400.0, value("'100-500 ?? -1'"));
    }

    @Test
    void evalFailuresThrowEval() {
        assertThrows(TagMath.EvalException.class, () -> TagMath.evaluate("8/0"));
        assertThrows(TagMath.EvalException.class, () -> TagMath.evaluate("8//0"));
        assertThrows(TagMath.EvalException.class, () -> TagMath.evaluate("8%0"));
        assertThrows(TagMath.EvalException.class, () -> TagMath.evaluate("0**-1"));
        assertThrows(TagMath.EvalException.class, () -> TagMath.evaluate("2**100000"));
    }

    @Test
    void nonMathThrowsSyntax() {
        assertThrows(TagMath.SyntaxException.class, () -> TagMath.evaluate(""));
        assertThrows(TagMath.SyntaxException.class, () -> TagMath.evaluate("abc"));
        assertThrows(TagMath.SyntaxException.class, () -> TagMath.evaluate("8+"));
        assertThrows(TagMath.SyntaxException.class, () -> TagMath.evaluate("(8"));
        assertThrows(TagMath.SyntaxException.class, () -> TagMath.evaluate("8+abc"));
        assertThrows(TagMath.SyntaxException.class, () -> TagMath.evaluate("8+5*"));
        assertThrows(TagMath.SyntaxException.class, () -> TagMath.evaluate("cooldown-5"));
        assertThrows(TagMath.SyntaxException.class, () -> TagMath.evaluate("nullable"));
    }

    @Test
    void formatNumberTrimsWholeAndFraction() {
        assertEquals("13", TagMath.formatNumber(13.0));
        assertEquals("-4", TagMath.formatNumber(-4.0));
        assertEquals("2.5", TagMath.formatNumber(2.5));
        assertEquals("0", TagMath.formatNumber(0.0));
    }
}
