package com.jruk8.jmanhunt.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.List;
import java.util.function.Consumer;

/**
 * The one compact banner sent to the console when the plugin enables.
 * Lines use legacy section colors, deserialized to components so the
 * console actually colorizes them; plain logger calls print the codes
 * raw. No MiniMessage tags, so this never touches the message pipeline.
 */
public final class StartupBanner {
    private static final int PADDING_LINES = 2;

    private StartupBanner() {
    }

    /** Banner lines for the running plugin version. Pure for tests. */
    public static List<String> lines(String version) {
        return List.of(
                "\u00A7d\u00A7lJManhunt \u00A78v" + version + " \u00A77enabled",
                "\u00A78\u00BB \u00A77Docs: \u00A7fhttps://jruk8.github.io/JManhunt/",
                "\u00A78\u00BB \u00A77Issues: \u00A7fhttps://github.com/jruk8/JManhunt/issues");
    }

    /** Sends the banner with blank padding around it. */
    public static void print(Consumer<Component> console, String version) {
        for (int index = 0; index < PADDING_LINES; index++) {
            console.accept(Component.empty());
        }
        for (String line : lines(version)) {
            console.accept(LegacyComponentSerializer.legacySection().deserialize(line));
        }
        for (int index = 0; index < PADDING_LINES; index++) {
            console.accept(Component.empty());
        }
    }
}
