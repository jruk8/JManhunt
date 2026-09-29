package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code <format:string,params>}: plain {@code {0}}, {@code {1}}
 * substitution with no conversion specifiers. Params accept a
 * {@code [...]} list or a bare single value. Indexes that do not
 * exist, non-numeric braces, and stray braces stay verbatim with no
 * warning; replacement text is never rescanned. Malformed tag shape
 * (wrong arity, bad quotes) warns plus {@code "null"}. No Bukkit
 * types.
 */
final class TagFormat {

    private TagFormat() {
    }

    /** Runtime substitution plus co-located shape validation. */
    static String format(String tag, String args, TagContext context) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 2) {
            context.scope().warn("Tag <format> needs a string plus params like "
                    + "<format:\"{0} found {1}\",[Alex,gold]>: " + tag);
            return "null";
        }
        Optional<String> template = CommandPlaceholders.parsePickItem(parts.get(0));
        Optional<String> rawParams = CommandPlaceholders.parsePickItem(parts.get(1));
        if (template.isEmpty() || rawParams.isEmpty()) {
            context.scope().warn("Tag <format> needs a string plus params like "
                    + "<format:\"{0} found {1}\",[Alex,gold]>: " + tag);
            return "null";
        }
        List<String> params = params(rawParams.get());
        StringBuilder out = new StringBuilder();
        String text = template.get();
        for (int index = 0; index < text.length(); index++) {
            int end = placeholderEnd(text, index);
            if (end < 0) {
                out.append(text.charAt(index));
                continue;
            }
            int slot;
            try {
                slot = Integer.parseInt(text.substring(index + 1, end));
            } catch (NumberFormatException tooBig) {
                out.append(text.substring(index, end + 1));
                index = end;
                continue;
            }
            out.append(slot < params.size() ? params.get(slot) : text.substring(index, end + 1));
            index = end;
        }
        return out.toString();
    }

    /** End offset of the {@code {digits}} run at index, or -1. */
    private static int placeholderEnd(String text, int index) {
        if (text.charAt(index) != '{') {
            return -1;
        }
        int end = index + 1;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        if (end == index + 1 || end >= text.length() || text.charAt(end) != '}') {
            return -1;
        }
        return end;
    }

    /** Bare value as the single item, or the unquoted list items. */
    private static List<String> params(String raw) {
        if (!TagLists.isList(raw)) {
            return List.of(raw);
        }
        List<String> items = new ArrayList<>();
        for (String item : TagLists.parse(raw)) {
            items.add(TagMath.unquote(item));
        }
        return items;
    }

    /** Syntax check mirroring the runtime shape rules. */
    static Optional<String> syntaxError(String name, String args) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 2) {
            return Optional.of("Tag <format> needs a string plus params like "
                    + "<format:\"{0} found {1}\",[Alex,gold]>");
        }
        if (CommandPlaceholders.parsePickItem(parts.get(0)).isEmpty()
                || CommandPlaceholders.parsePickItem(parts.get(1)).isEmpty()) {
            return Optional.of("Tag <format> needs a string plus params like "
                    + "<format:\"{0} found {1}\",[Alex,gold]>");
        }
        return Optional.empty();
    }
}
