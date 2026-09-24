package com.jruk8.jmanhunt.core;

import java.util.List;

/**
 * The one compact banner logged when the plugin enables. Lines use
 * legacy section colors, which Paper renders in the console; no
 * MiniMessage tags, so this never touches the message pipeline.
 */
public final class StartupBanner {

    private StartupBanner() {
    }

    /** Banner lines for the running plugin version. Pure for tests. */
    public static List<String> lines(String version) {
        return List.of(
                "\u00A7d\u00A7lJManhunt \u00A78v" + version + " \u00A77enabled",
                "\u00A78\u00BB \u00A77Docs: \u00A7fhttps://jruk8.github.io/JManhunt/",
                "\u00A78\u00BB \u00A77Issues: \u00A7fhttps://github.com/jruk8/JManhunt/issues");
    }

    /** Logs the banner, one line per call. */
    public static void print(JManhuntLogger log, String version) {
        for (String line : lines(version)) {
            log.info(line);
        }
    }
}
