package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
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
     * Every form also takes an {@code [enabled,text]} array that
     * sends only when the gate is {@code true} or {@code 1}.
     */
    static String message(String tag, String name, String args, TagContext context) {
        if (name.equals("rmessage") || name.equals("rmsg")) {
            return roleMessage(tag, args, context);
        }
        if (name.equals("pmessage") || name.equals("pmsg")) {
            return playerMessage(tag, args, context);
        }
        Optional<List<String>> array = globalArray(args);
        if (array.isPresent()) {
            Optional<String> text = messageArrayText(tag, name, array.get(), context);
            if (text.isEmpty()) {
                return "";
            }
            context.sendGlobalMessage(text.get());
            return "";
        }
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
        context.sendGlobalMessage(EngineEscapes.restore(item.orElse("")));
        return "";
    }

    /**
     * {@code <pmessage:player,text>}: sends to the named player only
     * through the context sink and returns empty. Offline players
     * warn and skip. Also takes {@code player,[enabled,text]}.
     */
    private static String playerMessage(String tag, String args, TagContext context) {
        Optional<AudienceArray> array = audienceArray(args);
        if (array.isPresent()) {
            Optional<String> text = messageArrayText(tag, "pmessage",
                    array.get().elements(), context);
            if (text.isEmpty()) {
                return "";
            }
            Optional<String> name = CommandPlaceholders.parsePickItem(array.get().audience());
            if (name.isEmpty() || name.get().isBlank()) {
                context.scope().warn("Tag <pmessage> has a malformed player: " + tag);
                return "";
            }
            return deliverPlayerMessage(tag, EngineEscapes.restore(name.get().strip()),
                    EngineEscapes.restore(text.get()), context);
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
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
        return deliverPlayerMessage(tag, EngineEscapes.restore(name.get().strip()),
                EngineEscapes.restore(item.orElse("")), context);
    }

    /** Shared named-player send for both {@code <pmessage>} arg shapes. */
    private static String deliverPlayerMessage(String tag, String target, String text,
            TagContext context) {
        if (!context.playerSinks().message(target, text)) {
            context.scope().warn("Tag <pmessage> player '" + target
                    + "' is offline: " + tag);
        }
        return "";
    }

    /**
     * {@code <rmessage:role,text>}: sends preset literal text to the
     * named role members through the context sink and returns empty.
     * Also takes {@code role,[enabled,text]}.
     */
    private static String roleMessage(String tag, String args, TagContext context) {
        Optional<AudienceArray> array = audienceArray(args);
        if (array.isPresent()) {
            Optional<String> text = messageArrayText(tag, "rmessage",
                    array.get().elements(), context);
            if (text.isEmpty()) {
                return "";
            }
            Optional<String> role = FlagStore.parseRole(tag, "rmessage",
                    array.get().audience(), context.scope());
            if (role.isEmpty()) {
                return "";
            }
            return deliverRoleMessage(EngineEscapes.restore(role.get()),
                    EngineEscapes.restore(text.get()), context);
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
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
        return deliverRoleMessage(EngineEscapes.restore(role.get()),
                EngineEscapes.restore(item.orElse("")), context);
    }

    /** Shared role send for both {@code <rmessage>} arg shapes. */
    private static String deliverRoleMessage(String role, String text, TagContext context) {
        context.sendRoleMessage(role, text);
        return "";
    }

    /**
     * {@code <gsound:id,pitch,volume>} and
     * {@code <psound:player,id,pitch,volume>}: plays through the
     * context sinks and returns empty. Pitch and volume default to 1
     * and fall back to 1 on bad numbers. Every form also takes an
     * {@code [enabled,id,pitch,volume]} array that plays only when
     * the gate is {@code true} or {@code 1}.
     */
    static String sound(String tag, String name, String args, TagContext context) {
        if (name.equals("rsound")) {
            return roleSound(tag, args, context);
        }
        if (name.equals("psound")) {
            return playerSound(tag, args, context);
        }
        Optional<List<String>> array = globalArray(args);
        if (array.isPresent()) {
            Optional<SoundSpec> spec = soundArraySpec(tag, name, array.get(), context);
            if (spec.isEmpty()) {
                return "";
            }
            context.playGlobalSound(spec.get().id(), spec.get().pitch(), spec.get().volume());
            return "";
        }
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
        context.playGlobalSound(EngineEscapes.restore(id.get()), pitch, volume);
        return "";
    }

    /**
     * {@code <psound:player,id,pitch,volume>}: plays for the named
     * player only through the context sink and returns empty. Pitch
     * and volume default to 1 and fall back to 1 on bad numbers.
     * Offline players warn and skip. Also takes
     * {@code player,[enabled,id,pitch,volume]}.
     */
    private static String playerSound(String tag, String args, TagContext context) {
        Optional<AudienceArray> array = audienceArray(args);
        if (array.isPresent()) {
            Optional<SoundSpec> spec = soundArraySpec(tag, "psound",
                    array.get().elements(), context);
            if (spec.isEmpty()) {
                return "";
            }
            Optional<String> name = CommandPlaceholders.parsePickItem(array.get().audience());
            if (name.isEmpty() || name.get().isBlank()) {
                context.scope().warn("Tag <psound> has a malformed player: " + tag);
                return "";
            }
            return deliverPlayerSound(tag, EngineEscapes.restore(name.get().strip()),
                    spec.get(), context);
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
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
        return deliverPlayerSound(tag, EngineEscapes.restore(name.get().strip()),
                new SoundSpec(EngineEscapes.restore(id.get()), pitch, volume), context);
    }

    /** Shared named-player play for both {@code <psound>} arg shapes. */
    private static String deliverPlayerSound(String tag, String target, SoundSpec spec,
            TagContext context) {
        if (!context.playerSinks().sound(target, spec.id(), spec.pitch(), spec.volume())) {
            context.scope().warn("Tag <psound> player '" + target
                    + "' is offline: " + tag);
        }
        return "";
    }

    /**
     * {@code <rsound:role,id,pitch,volume>}: plays for the named
     * role members through the context sink and returns empty. Pitch
     * and volume default to 1 and fall back to 1 on bad numbers.
     * Also takes {@code role,[enabled,id,pitch,volume]}.
     */
    private static String roleSound(String tag, String args, TagContext context) {
        Optional<AudienceArray> array = audienceArray(args);
        if (array.isPresent()) {
            Optional<SoundSpec> spec = soundArraySpec(tag, "rsound",
                    array.get().elements(), context);
            if (spec.isEmpty()) {
                return "";
            }
            Optional<String> role = FlagStore.parseRole(tag, "rsound",
                    array.get().audience(), context.scope());
            if (role.isEmpty()) {
                return "";
            }
            return deliverRoleSound(EngineEscapes.restore(role.get()), spec.get(), context);
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
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
        return deliverRoleSound(EngineEscapes.restore(role.get()),
                new SoundSpec(EngineEscapes.restore(id.get()), pitch, volume), context);
    }

    /** Shared role play for both {@code <rsound>} arg shapes. */
    private static String deliverRoleSound(String role, SoundSpec spec, TagContext context) {
        context.playRoleSound(role, spec.id(), spec.pitch(), spec.volume());
        return "";
    }

    /**
     * {@code <gteleport:loc>}, {@code <pteleport:player,loc>}, and
     * {@code <rteleport:role,loc>}: teleports through the context
     * sinks and returns empty. The location is
     * {@code [x, y, z, world]} or
     * {@code [x, y, z, world, pitch, yaw]}.
     */
    static String teleport(String tag, String name, String args, TagContext context) {
        if (name.equals("rteleport")) {
            return roleTeleport(tag, args, context);
        }
        if (name.equals("pteleport")) {
            return playerTeleport(tag, args, context);
        }
        Optional<TagLocations.TeleportRequest> target = TagLocations.parseTeleport(args);
        if (target.isEmpty()) {
            context.scope().warn("Tag <" + name + "> needs a location "
                    + "[x, y, z, world, pitch, yaw]: " + tag);
            return "";
        }
        context.teleportGlobal(target.get());
        return "";
    }

    /**
     * {@code <pteleport:player,loc>}: teleports the named online
     * player through the context sink and returns empty. Offline
     * players warn and skip.
     */
    private static String playerTeleport(String tag, String args, TagContext context) {
        Optional<AudienceArray> array = audienceArray(args);
        if (array.isEmpty()) {
            context.scope().warn("Tag <pteleport> needs a player and a location "
                    + "[x, y, z, world, pitch, yaw]: " + tag);
            return "";
        }
        Optional<String> name = CommandPlaceholders.parsePickItem(array.get().audience());
        if (name.isEmpty() || name.get().isBlank()) {
            context.scope().warn("Tag <pteleport> has a malformed player: " + tag);
            return "";
        }
        Optional<TagLocations.TeleportRequest> target =
                TagLocations.teleportFromElements(array.get().elements());
        if (target.isEmpty()) {
            context.scope().warn("Tag <pteleport> needs a location "
                    + "[x, y, z, world, pitch, yaw]: " + tag);
            return "";
        }
        String player = EngineEscapes.restore(name.get().strip());
        if (!context.playerSinks().teleport(player, target.get())) {
            context.scope().warn("Tag <pteleport> player '" + player
                    + "' is offline: " + tag);
        }
        return "";
    }

    /**
     * {@code <rteleport:role,loc>}: teleports the named role members
     * through the context sink and returns empty.
     */
    private static String roleTeleport(String tag, String args, TagContext context) {
        Optional<AudienceArray> array = audienceArray(args);
        if (array.isEmpty()) {
            context.scope().warn("Tag <rteleport> needs a role and a location "
                    + "[x, y, z, world, pitch, yaw]: " + tag);
            return "";
        }
        Optional<String> role = FlagStore.parseRole(tag, "rteleport",
                array.get().audience(), context.scope());
        if (role.isEmpty()) {
            return "";
        }
        Optional<TagLocations.TeleportRequest> target =
                TagLocations.teleportFromElements(array.get().elements());
        if (target.isEmpty()) {
            context.scope().warn("Tag <rteleport> needs a location "
                    + "[x, y, z, world, pitch, yaw]: " + tag);
            return "";
        }
        context.teleportRole(EngineEscapes.restore(role.get()), target.get());
        return "";
    }

    /**
     * Shared enabled gate for every sound and message array form:
     * plays or sends only on {@code true} (any case) or {@code 1}.
     * Every other value skips silently.
     */
    static boolean enabledFlag(String raw) {
        if (raw == null) {
            return false;
        }
        String value = raw.strip();
        return value.equalsIgnoreCase("true") || value.equals("1");
    }

    /** Audience plus array elements behind the player and role forms. */
    record AudienceArray(String audience, List<String> elements) {
    }

    /**
     * Array elements when the whole args are one list literal (global
     * forms), else empty so callers keep the individual-arg path.
     */
    static Optional<List<String>> globalArray(String args) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 1 || !TagLists.isList(parts.get(0))) {
            return Optional.empty();
        }
        return Optional.of(arrayElements(parts.get(0)));
    }

    /**
     * Audience plus array elements when the args are an audience plus
     * one list literal (player and role forms), else empty so
     * callers keep the individual-arg path.
     */
    static Optional<AudienceArray> audienceArray(String args) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 2 || !TagLists.isList(parts.get(1))) {
            return Optional.empty();
        }
        return Optional.of(new AudienceArray(parts.get(0), arrayElements(parts.get(1))));
    }

    /**
     * Elements of one list literal, split quote-aware so quoted
     * commas inside text survive, unlike the plain list splitter.
     * Blank lists yield no elements.
     */
    private static List<String> arrayElements(String literal) {
        String inner = literal.strip();
        inner = inner.substring(1, inner.length() - 1);
        if (inner.isBlank()) {
            return List.of();
        }
        List<String> elements = new ArrayList<>();
        for (String element : TagLists.splitTopLevel(inner)) {
            elements.add(element.strip());
        }
        return elements;
    }

    /**
     * Shared message array body: arity, gate, then text. Empty means
     * skip (gated off or warned); callers deliver the text.
     */
    private static Optional<String> messageArrayText(String tag, String name,
            List<String> elements, TagContext context) {
        if (elements.size() != 2) {
            context.scope().warn("Tag <" + name + "> array needs enabled plus a text: " + tag);
            return Optional.empty();
        }
        Optional<String> enabled = CommandPlaceholders.parsePickItem(elements.get(0));
        if (enabled.isEmpty() && !elements.get(0).isBlank()) {
            context.scope().warn("Tag <" + name + "> has malformed quotes: " + tag);
            return Optional.empty();
        }
        if (!enabledFlag(enabled.orElse(""))) {
            return Optional.empty();
        }
        Optional<String> item = CommandPlaceholders.parsePickItem(elements.get(1));
        if (item.isEmpty() && !elements.get(1).isBlank()) {
            context.scope().warn("Tag <" + name + "> has malformed quotes: " + tag);
            return Optional.empty();
        }
        return Optional.of(EngineEscapes.restore(item.orElse("")));
    }

    /** Resolved sound id, pitch, and volume behind the array forms. */
    private record SoundSpec(String id, float pitch, float volume) {
    }

    /**
     * Shared sound array body: arity, gate, id, pitch, volume. Empty
     * means skip (gated off or warned); callers deliver the spec.
     */
    private static Optional<SoundSpec> soundArraySpec(String tag, String name,
            List<String> elements, TagContext context) {
        if (elements.size() < 2 || elements.size() > 4) {
            context.scope().warn("Tag <" + name + "> array needs enabled plus an id, pitch, "
                    + "and volume: " + tag);
            return Optional.empty();
        }
        Optional<String> enabled = CommandPlaceholders.parsePickItem(elements.get(0));
        if (enabled.isEmpty() && !elements.get(0).isBlank()) {
            context.scope().warn("Tag <" + name + "> has malformed quotes: " + tag);
            return Optional.empty();
        }
        if (!enabledFlag(enabled.orElse(""))) {
            return Optional.empty();
        }
        Optional<String> id = CommandPlaceholders.parsePickItem(elements.get(1));
        if (id.isEmpty() || id.get().isBlank()) {
            context.scope().warn("Tag <" + name + "> needs a sound id: " + tag);
            return Optional.empty();
        }
        float pitch = soundNumber(elements, 2, tag, context);
        float volume = soundNumber(elements, 3, tag, context);
        return Optional.of(new SoundSpec(EngineEscapes.restore(id.get()), pitch, volume));
    }

    /**
     * Edit-time shape for {@code <gmessage>}: one text, or an
     * {@code [enabled,text]} array. Mirrors the runtime warns.
     */
    static Optional<String> messageError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs one text.");
        }
        Optional<List<String>> array = globalArray(args);
        if (array.isPresent()) {
            return messageArrayError(name, array.get());
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() != 1) {
            return Optional.of("Tag <" + name + "> needs one text.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    /**
     * Edit-time shape for {@code <pmessage>}: a player plus a text,
     * or a player plus an {@code [enabled,text]} array. Mirrors the
     * runtime warns.
     */
    static Optional<String> playerMessageError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a player and a text.");
        }
        Optional<AudienceArray> array = audienceArray(args);
        if (array.isPresent()) {
            Optional<String> elements = messageArrayError(name, array.get().elements());
            if (elements.isPresent()) {
                return elements;
            }
            return audienceHygiene(name, array.get().audience());
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() != 2) {
            return Optional.of("Tag <" + name + "> needs a player and a text.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    /**
     * Edit-time shape for {@code <psound>}: a player plus an id,
     * pitch, and volume, quotes parsed, or a player plus an
     * {@code [enabled,id,pitch,volume]} array. Mirrors the runtime
     * warns.
     */
    static Optional<String> playerSoundError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a player plus a sound id.");
        }
        Optional<AudienceArray> array = audienceArray(args);
        if (array.isPresent()) {
            Optional<String> elements = soundArrayError(name, array.get().elements());
            if (elements.isPresent()) {
                return elements;
            }
            return audienceHygiene(name, array.get().audience());
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

    /**
     * Edit-time shape for {@code <gsound>}: an id, pitch, and volume,
     * quotes parsed, or an {@code [enabled,id,pitch,volume]} array.
     * Mirrors the runtime warns.
     */
    static Optional<String> soundError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a sound id.");
        }
        Optional<List<String>> array = globalArray(args);
        if (array.isPresent()) {
            return soundArrayError(name, array.get());
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() < 1 || parts.size() > 3) {
            return Optional.of("Tag <" + name + "> needs an id plus pitch and volume.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    /** Edit-time shape for message arrays: enabled plus a text. */
    static Optional<String> messageArrayError(String name, List<String> elements) {
        if (elements.size() != 2) {
            return Optional.of("Tag <" + name + "> array needs enabled plus a text.");
        }
        return elementsHygiene(name, elements);
    }

    /** Edit-time shape for sound arrays: enabled plus id, pitch, volume. */
    static Optional<String> soundArrayError(String name, List<String> elements) {
        if (elements.size() < 2 || elements.size() > 4) {
            return Optional.of("Tag <" + name + "> array needs enabled plus an id, pitch, "
                    + "and volume.");
        }
        return elementsHygiene(name, elements);
    }

    /** Quote hygiene across array elements; blank elements stay legal. */
    private static Optional<String> elementsHygiene(String name, List<String> elements) {
        for (String element : elements) {
            if (CommandPlaceholders.parsePickItem(element).isEmpty() && !element.isBlank()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    /**
     * Edit-time shape for {@code <gteleport>}: one location
     * {@code [x, y, z, world]} or
     * {@code [x, y, z, world, pitch, yaw]}. Mirrors the runtime
     * warns.
     */
    static Optional<String> teleportError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a location "
                    + "[x, y, z, world, pitch, yaw].");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 1) {
            return Optional.of("Tag <" + name + "> needs a location "
                    + "[x, y, z, world, pitch, yaw].");
        }
        if (CommandPlaceholders.parsePickItem(parts.get(0)).isEmpty()
                && !parts.get(0).isBlank()) {
            return Optional.of("Tag <" + name + "> mixes quotes.");
        }
        return teleportLocationError(name, parts.get(0));
    }

    /**
     * Edit-time shape for {@code <pteleport>}: a player plus one
     * location. Mirrors the runtime warns.
     */
    static Optional<String> playerTeleportError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a player and a location "
                    + "[x, y, z, world, pitch, yaw].");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 2) {
            return Optional.of("Tag <" + name + "> needs a player and a location "
                    + "[x, y, z, world, pitch, yaw].");
        }
        Optional<String> audience = audienceHygiene(name, parts.get(0));
        if (audience.isPresent()) {
            return audience;
        }
        return teleportLocationError(name, parts.get(1));
    }

    /**
     * Edit-time shape for one teleport location: a 4- or 6-element
     * list, or a collapsed nested tag the editor cannot see through
     * (runtime validates it).
     */
    static Optional<String> teleportLocationError(String name, String loc) {
        if (loc.strip().equals("?")) {
            return Optional.empty();
        }
        if (!TagLists.isList(loc)) {
            return Optional.of("Tag <" + name + "> needs a location "
                    + "[x, y, z, world, pitch, yaw].");
        }
        int elements = TagLists.parse(loc).size();
        if (elements != 4 && elements != 6) {
            return Optional.of("Tag <" + name + "> needs a location "
                    + "[x, y, z, world, pitch, yaw].");
        }
        return Optional.empty();
    }

    /** Quote hygiene for the audience head of an array form. */
    private static Optional<String> audienceHygiene(String name, String audience) {
        if (CommandPlaceholders.parsePickItem(audience).isEmpty()) {
            return Optional.of("Tag <" + name + "> mixes quotes.");
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
