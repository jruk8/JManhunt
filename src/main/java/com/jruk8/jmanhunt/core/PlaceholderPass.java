package com.jruk8.jmanhunt.core;

import com.jruk8.jmanhunt.command.PlaceholderResolver;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Server-backed placeholder expansion for modifier commands. With
 * PlaceholderAPI installed and the executor online, every expansion
 * resolves through it; otherwise only {@code %jmanhunt_*%} spans
 * resolve in-house and anything else stays verbatim. PAPI classes
 * are touched only inside the enabled branch, so the softdepend
 * stays soft.
 */
public final class PlaceholderPass implements PlaceholderResolver {
    private static final Pattern SPAN = Pattern.compile("%([^%]+)%");
    private static final String PREFIX = "jmanhunt_";

    private final JManhuntPlaceholders fallback;

    public PlaceholderPass(JManhuntPlaceholders fallback) {
        this.fallback = fallback;
    }

    @Override
    public String resolve(String text, String playerName) {
        if (!text.contains("%")) {
            return text;
        }
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && papiEnabled()) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return inHouse(text, player);
    }

    /** Expands {@code %jmanhunt_*%} spans; anything else stays verbatim. */
    private String inHouse(String text, Player player) {
        return expandSpans(text, key -> {
            if (!key.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
                return null;
            }
            return fallback.resolve(player, key.substring(PREFIX.length()));
        });
    }

    /**
     * Expands {@code %...%} spans through a lookup. A null lookup
     * keeps the span verbatim. Pure for tests.
     */
    static String expandSpans(String text, Function<String, String> lookup) {
        Matcher spans = SPAN.matcher(text);
        StringBuffer out = new StringBuffer();
        while (spans.find()) {
            String value = lookup.apply(spans.group(1));
            spans.appendReplacement(out,
                    Matcher.quoteReplacement(value == null ? spans.group(0) : value));
        }
        spans.appendTail(out);
        return out.toString();
    }

    private static boolean papiEnabled() {
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        return papi != null && papi.isEnabled();
    }
}
