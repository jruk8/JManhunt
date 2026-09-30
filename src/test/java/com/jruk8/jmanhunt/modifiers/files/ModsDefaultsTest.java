package com.jruk8.jmanhunt.modifiers.files;

import org.junit.jupiter.api.Test;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModsDefaultsTest {

    @Test
    void everyDefaultResolvesAsResource() {
        for (ModFileKind kind : ModFileKind.values()) {
            for (String id : ModsDefaults.ids(kind)) {
                String resource = "mods/" + kind.dirName() + "/" + id + ".yml";
                assertNotNull(getClass().getClassLoader().getResourceAsStream(resource),
                        "missing bundled default: " + resource);
            }
        }
    }

    @Test
    void everyResourceIsListed() throws Exception {
        assertEquals(Set.copyOf(ModsDefaults.MODIFIERS),
                Set.copyOf(resourceIds("mods/modifiers")));
        assertEquals(Set.copyOf(ModsDefaults.PRESETS),
                Set.copyOf(resourceIds("mods/presets")));
    }

    private List<String> resourceIds(String dir) throws Exception {
        URL url = Objects.requireNonNull(getClass().getClassLoader().getResource(dir),
                "missing test resources: " + dir);
        assertEquals("file", url.getProtocol(), "test resources must be plain files: " + url);
        try (var stream = Files.list(Path.of(url.toURI()))) {
            return stream.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".yml"))
                    .map(name -> name.substring(0, name.length() - ".yml".length()))
                    .toList();
        }
    }
}
