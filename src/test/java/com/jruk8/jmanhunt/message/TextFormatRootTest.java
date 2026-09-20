package com.jruk8.jmanhunt.message;

import com.jruk8.jmanhunt.command.ManhuntCommand;
import com.jruk8.jmanhunt.config.ConfigService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFormatRootTest {

    @Test
    void textFormatLivesAtRoot() throws Exception {
        YamlConfiguration defaults = loadBundledConfig();

        assertEquals("minimessage", defaults.getString("text-format"));
        assertFalse(defaults.contains("settings.text-format"));
    }

    @Test
    void rootTextFormatStaysEditableInGame() throws Exception {
        YamlConfiguration defaults = loadBundledConfig();

        var editable = ConfigService.settingNames(defaults);
        var resolved = ManhuntCommand.resolveDrill(defaults, editable, List.of("text-format"));

        assertTrue(resolved != null && resolved.leaf());
        assertTrue(ManhuntCommand.drillChildren(defaults, editable, List.of()).contains("text-format"));
    }

    private static YamlConfiguration loadBundledConfig() throws Exception {
        try (InputStream stream = Objects.requireNonNull(
                TextFormatRootTest.class.getClassLoader().getResourceAsStream("config.yml"),
                "missing test resource: config.yml")) {
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
