package com.jruk8.jmanhunt.command;

import java.util.function.Consumer;

/**
 * Engine-wide backslash escapes via sentinel substitution. Every
 * {@code \X} pair converts up front: structural chars become single
 * private-use sentinels (inert to every scanner, one char each so
 * counts hold) and all other chars emit literally. Scanners run on
 * substituted text where escaped structure is simply absent; restore
 * points convert sentinels back at the exact exits.
 *
 * <p>Rules: the 27 structural chars are {@code < > , " ' \ : @ ~ ? &
 * | ! = + - * / % ( ) [ ] { }}, space, and tab. Any other escaped
 * char emits verbatim ({@code \n} is a literal {@code n}, escaped
 * letters and digits pass through, so word operators are
 * unaffected). A trailing lone {@code \} stays a literal backslash.
 * Substitution is idempotent (output holds no escapable pairs) and
 * null-safe. Restored output is never rescanned.
 *
 * <p>Acceptance: {@code \<yellow\>} survives tag parsing and renders
 * MiniMessage yellow; {@code \\} collapses to {@code \};
 * {@code \\<yellow\\>} reads as an unknown-tag validation error;
 * {@code \,} passes commas through arg splitting.
 *
 * <p>Literal private-use chars U+E000..U+E01A in raw input are
 * reserved and restore unpredictably; Minecraft input cannot
 * produce them. No Bukkit types.
 */
public final class EngineEscapes {

    /** Structural chars in fixed order; index i maps to U+E000 + i. */
    private static final String STRUCTURED = "<>,\"'\\:@~?&|!=+-*/%()[]{} \t";

    private static final char SENTINEL_BASE = 0xE000;

    private EngineEscapes() {
    }

    /**
     * Converts every {@code \X} pair: structural chars become
     * sentinels, all other chars emit verbatim. Null returns null.
     */
    public static String substitute(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int index = 0; index < raw.length(); index++) {
            char letter = raw.charAt(index);
            if (letter != '\\' || index + 1 >= raw.length()) {
                out.append(letter);
                continue;
            }
            char next = raw.charAt(index + 1);
            int slot = STRUCTURED.indexOf(next);
            if (slot < 0) {
                out.append(next);
            } else {
                out.append((char) (SENTINEL_BASE + slot));
            }
            index++;
        }
        return out.toString();
    }

    /** Maps sentinels back to their structural chars. Null-safe. */
    public static String restore(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char letter = text.charAt(index);
            int slot = letter - SENTINEL_BASE;
            if (slot >= 0 && slot < STRUCTURED.length()) {
                out.append(STRUCTURED.charAt(slot));
            } else {
                out.append(letter);
            }
        }
        return out.toString();
    }

    /** Wraps warn/log sinks so call sites gain restore for free. */
    public static Consumer<String> restoring(Consumer<String> sink) {
        return message -> sink.accept(restore(message));
    }
}
