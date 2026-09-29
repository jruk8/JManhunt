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
     * {@code <gmessage:text>} and {@code <pmessage:player,text>}:
     * sends through the context sinks and returns empty. The player
     * form names its audience, so console-evaluated runs deliver.
     */
    static String message(String tag, String name, String args, TagContext context) {
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (name.equals("rmessage")) {
            return roleMessage(tag, parts, context);
        }
        if (name.equals("pmessage")) {
            return playerMessage(tag, parts, context);
        }
        if (parts.size() != 1) {
            context.scope().warn("Tag <" + name + "> needs one text: " + tag);
            return "";
        }
        Optional<String> item = CommandPlaceholders.parsePickItem(parts.get(0));
        if (item.isEmpty() && !parts.get(0).isBlank()) {
            context.scope().warn("Tag <" + name + "> has malformed quotes: " + tag);
            return "";
        }
        context.sendGlobalMessage(item.orElse(""));
        return "";
    }

    /**
     * {@code <pmessage:player,text>}: sends to the named player only
     * through the context sink and returns empty. Offline players
     * warn and skip.
     */
    private static String playerMessage(String tag, List<String> parts, TagContext context) {
        if (parts.size() != 2) {
            context.scope().warn("Tag <pmessage> needs a player and a text: " + tag);
            return "";
        }
        Optional<String> name = CommandPlaceholders.parsePickItem(parts.get(0));
        if (name.isEmpty() || name.get().isBlank()) {
            context.scope().warn("Tag <pmessage> has a malformed player: " + tag);
            return "";
        }
        Optional<String> item = CommandPlaceholders.parsePickItem(parts.get(1));
        if (item.isEmpty() && !parts.get(1).isBlank()) {
            context.scope().warn("Tag <pmessage> has malformed quotes: " + tag);
            return "";
        }
        if (!context.playerSinks().message(name.get().strip(), item.orElse(""))) {
            context.scope().warn("Tag <pmessage> player '" + name.get().strip()
                    + "' is offline: " + tag);
        }
        return "";
    }

    /**
     * {@code <rmessage:role,text>}: sends preset literal text to the
     * named role members through the context sink and returns empty.
     */
    private static String roleMessage(String tag, List<String> parts, TagContext context) {
        if (parts.size() != 2) {
            context.scope().warn("Tag <rmessage> needs a role and a text: " + tag);
            return "";
        }
        Optional<String> role = FlagStore.parseRole(tag, "rmessage", parts.get(0),
                context.scope());
        if (role.isEmpty()) {
            return "";
        }
        Optional<String> item = CommandPlaceholders.parsePickItem(parts.get(1));
        if (item.isEmpty() && !parts.get(1).isBlank()) {
            context.scope().warn("Tag <rmessage> has malformed quotes: " + tag);
            return "";
        }
        context.sendRoleMessage(role.get(), item.orElse(""));
        return "";
    }

    /**
     * {@code <gsound:id,pitch,volume>} and
     * {@code <psound:player,id,pitch,volume>}: plays through the
     * context sinks and returns empty. Pitch and volume default to 1
     * and fall back to 1 on bad numbers.
     */
    static String sound(String tag, String name, String args, TagContext context) {
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (name.equals("rsound")) {
            return roleSound(tag, parts, context);
        }
        if (name.equals("psound")) {
            return playerSound(tag, parts, context);
        }
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
        context.playGlobalSound(id.get(), pitch, volume);
        return "";
    }

    /**
     * {@code <psound:player,id,pitch,volume>}: plays for the named
     * player only through the context sink and returns empty. Pitch
     * and volume default to 1 and fall back to 1 on bad numbers.
     * Offline players warn and skip.
     */
    private static String playerSound(String tag, List<String> parts, TagContext context) {
        if (parts.size() < 2 || parts.size() > 4) {
            context.scope().warn("Tag <psound> needs a player plus an id, pitch, and volume: "
                    + tag);
            return "";
        }
        Optional<String> name = CommandPlaceholders.parsePickItem(parts.get(0));
        if (name.isEmpty() || name.get().isBlank()) {
            context.scope().warn("Tag <psound> has a malformed player: " + tag);
            return "";
        }
        List<String> rest = parts.subList(1, parts.size());
        Optional<String> id = CommandPlaceholders.parsePickItem(rest.get(0));
        if (id.isEmpty() || id.get().isBlank()) {
            context.scope().warn("Tag <psound> needs a sound id: " + tag);
            return "";
        }
        float pitch = soundNumber(rest, 1, tag, context);
        float volume = soundNumber(rest, 2, tag, context);
        if (!context.playerSinks().sound(name.get().strip(), id.get(), pitch, volume)) {
            context.scope().warn("Tag <psound> player '" + name.get().strip()
                    + "' is offline: " + tag);
        }
        return "";
    }

    /**
     * {@code <rsound:role,id,pitch,volume>}: plays for the named
     * role members through the context sink and returns empty. Pitch
     * and volume default to 1 and fall back to 1 on bad numbers.
     */
    private static String roleSound(String tag, List<String> parts, TagContext context) {
        if (parts.size() < 2 || parts.size() > 4) {
            context.scope().warn("Tag <rsound> needs a role plus an id, pitch, and volume: "
                    + tag);
            return "";
        }
        Optional<String> role = FlagStore.parseRole(tag, "rsound", parts.get(0),
                context.scope());
        if (role.isEmpty()) {
            return "";
        }
        List<String> rest = parts.subList(1, parts.size());
        Optional<String> id = CommandPlaceholders.parsePickItem(rest.get(0));
        if (id.isEmpty() || id.get().isBlank()) {
            context.scope().warn("Tag <rsound> needs a sound id: " + tag);
            return "";
        }
        float pitch = soundNumber(rest, 1, tag, context);
        float volume = soundNumber(rest, 2, tag, context);
        context.playRoleSound(role.get(), id.get(), pitch, volume);
        return "";
    }

    /**
     * Edit-time shape for {@code <psound>}: a player plus an id,
     * pitch, and volume, quotes parsed. Mirrors the runtime warns.
     */
    static Optional<String> playerSoundError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a player plus a sound id.");
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() < 2 || parts.size() > 4) {
            return Optional.of("Tag <" + name + "> needs a player plus an id, pitch, and volume.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
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
