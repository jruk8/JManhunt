package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Optional;

/**
 * Side-effect delivery tags: messages and sounds in their global,
 * player, and role flavors. Each sends or plays through the context
 * sinks and returns empty. No Bukkit types.
 */
final class TagSinks {

    private TagSinks() {
    }

    /**
     * {@code <gmessage:text>}, {@code <pmessage:text>}, and
     * {@code <rmessage:text>}: sends through the context sinks and
     * returns empty.
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
        } else if (name.equals("pmessage")) {
            context.sendPlayerMessage(text);
        } else {
            context.sendRoleMessage(text);
        }
        return "";
    }

    /**
     * {@code <gsound:id,pitch,volume>}, {@code <psound:...>},
     * and {@code <rsound:...>}: plays through the context sinks and
     * returns empty. Pitch and volume default to 1 and fall back to 1
     * on bad numbers.
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
        } else if (name.equals("psound")) {
            context.playPlayerSound(id.get(), pitch, volume);
        } else {
            context.playRoleSound(id.get(), pitch, volume);
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
}
