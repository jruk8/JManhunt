package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Optional;

/**
 * Flag tags behind {@code <gflag>}, {@code <pflag>}, and
 * {@code <lflag>}. One value arg sets, none gets; a get of an unset
 * flag yields {@code null} so {@code ??} can catch it. Sets return
 * empty like the other side-effect tags. Names match exactly;
 * global and player flags need a live match while local flags only
 * need their run.
 */
public final class TagFlags {

    private TagFlags() {
    }

    /** Resolves {@code <gflag:name>} and {@code <gflag:name,value>}. */
    static String global(String tag, String args, TagContext context) {
        return flag(tag, args, context, "gflag", context.flagStore()::setGlobal,
                key -> context.flagStore().global(context.matchId(), key));
    }

    /** Resolves {@code <pflag:name>} and {@code <pflag:name,value>}. */
    static String player(String tag, String args, TagContext context) {
        String suffix = FlagStore.suffixFor(context.scope());
        return flag(tag, args, context, "pflag",
                (matchId, key, value) -> context.flagStore()
                        .setPlayer(matchId, FlagStore.playerKey(key, suffix), value),
                key -> context.flagStore().player(context.matchId(),
                        FlagStore.playerKey(key, suffix)));
    }

    /** Resolves {@code <lflag:name>} and {@code <lflag:name,value>}. */
    static String local(String tag, String args, TagContext context) {
        List<String> parts = TagLists.splitTopLevel(args);
        Optional<String> name = flagName(tag, parts, context, "lflag");
        if (name.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return context.localFlags().getOrDefault(name.get(), FlagStore.UNSET);
        }
        Optional<String> value = CommandPlaceholders.parsePickItem(parts.get(1));
        if (value.isEmpty()) {
            context.scope().warn("Tag <lflag> has a malformed value: " + tag);
            return "";
        }
        context.localFlags().put(name.get(), value.get());
        return "";
    }

    /**
     * Loads one flag value for list write-back; unset reads as
     * {@code null} and match-less match-scoped reads stay silent.
     */
    static String loadFlag(String kind, String name, TagContext context) {
        String tag = "<" + kind + ":" + name + ">";
        return switch (kind) {
            case "gflag" -> global(tag, name, context);
            case "pflag" -> player(tag, name, context);
            default -> local(tag, name, context);
        };
    }

    /**
     * Stores one flag value for list write-back. Match-scoped stores
     * without a live match warn and skip the write.
     */
    static void storeFlag(String kind, String name, String value, String tag, TagContext context) {
        switch (kind) {
            case "gflag" -> {
                if (context.matchId() == TagContext.NO_MATCH) {
                    context.scope().warn("Tag <gflag> needs a live match: " + tag);
                } else {
                    context.flagStore().setGlobal(context.matchId(), name, value);
                }
            }
            case "pflag" -> {
                if (context.matchId() == TagContext.NO_MATCH) {
                    context.scope().warn("Tag <pflag> needs a live match: " + tag);
                } else {
                    String suffix = FlagStore.suffixFor(context.scope());
                    context.flagStore().setPlayer(context.matchId(),
                            FlagStore.playerKey(name, suffix), value);
                }
            }
            default -> context.localFlags().put(name, value);
        }
    }

    /** Shared get/set shape for the match-scoped flag kinds. */
    private static String flag(String tag, String args, TagContext context, String root,
            FlagSetter setter, FlagGetter getter) {
        List<String> parts = TagLists.splitTopLevel(args);
        Optional<String> name = flagName(tag, parts, context, root);
        if (name.isEmpty()) {
            return "";
        }
        if (context.matchId() == TagContext.NO_MATCH) {
            if (parts.size() == 1) {
                return FlagStore.UNSET;
            }
            context.scope().warn("Tag <" + root + "> needs a live match: " + tag);
            return "";
        }
        if (parts.size() == 1) {
            return getter.get(name.get());
        }
        Optional<String> value = CommandPlaceholders.parsePickItem(parts.get(1));
        if (value.isEmpty()) {
            context.scope().warn("Tag <" + root + "> has a malformed value: " + tag);
            return "";
        }
        setter.set(context.matchId(), name.get(), value.get());
        return "";
    }

    /** Arity plus name shape shared by every flag kind. */
    private static Optional<String> flagName(String tag, List<String> parts, TagContext context,
            String root) {
        if (parts.size() < 1 || parts.size() > 2) {
            context.scope().warn("Tag <" + root + "> needs a name plus an optional value: " + tag);
            return Optional.empty();
        }
        Optional<String> name = FlagStore.parseName(parts.get(0));
        if (name.isEmpty()) {
            context.scope().warn("Tag <" + root + "> needs a name: " + tag);
            return Optional.empty();
        }
        return name;
    }

    /** Stores one match-scoped flag. */
    private interface FlagSetter {
        void set(long matchId, String key, String value);
    }

    /** Reads one match-scoped flag. */
    private interface FlagGetter {
        String get(String key);
    }
}
