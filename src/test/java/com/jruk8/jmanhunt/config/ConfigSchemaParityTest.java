package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Two-way parity between the Okaeri schema and the setting registry:
 * every editable scalar is registered with the schema default and
 * every schema leaf is reachable. The data-folder files generate
 * from the schema, so no bundled copy exists to compare against.
 */
class ConfigSchemaParityTest {

    private static final Set<String> EXCLUDED_SCALARS =
            Set.of("config-version", "send-anonymous-statistics");

    private record SchemaWalk(Map<String, Object> leaves, List<String> lists) {
    }

    @Test
    void everyRegistryDefaultMatchesSchema() {
        JManhuntConfig schema = new JManhuntConfig();
        for (SettingDescriptor descriptor : SettingRegistry.settingNames().stream()
                .map(SettingRegistry::byPath).toList()) {
            Object schemaValue = ConfigPathMapper.get(schema, descriptor.path());
            Object parsedDefault = parseDefault(descriptor);
            assertEquals(ConfigService.displayValue(parsedDefault),
                    ConfigService.displayValue(schemaValue),
                    "default mismatch: " + descriptor.path());
        }
    }

    @Test
    void everyRegistryPathResolvesInSchema() {
        JManhuntConfig schema = new JManhuntConfig();
        for (String path : SettingRegistry.settingNames()) {
            Object value = ConfigPathMapper.get(schema, path);
            assertTrue(value != null, "unresolvable: " + path);
        }
        for (String path : SettingRegistry.listPaths()) {
            Object value = ConfigPathMapper.get(schema, path);
            assertTrue(value instanceof List, "not a list: " + path);
        }
    }

    @Test
    void everySchemaLeafIsRegisteredOrExcluded() {
        SchemaWalk walk = walk(new JManhuntConfig());
        for (String path : walk.leaves().keySet()) {
            assertTrue(SettingRegistry.byPath(path) != null || EXCLUDED_SCALARS.contains(path),
                    "unregistered leaf: " + path);
        }
        for (String path : SettingRegistry.settingNames()) {
            assertTrue(walk.leaves().containsKey(path), "registry path missing in schema: " + path);
        }
        for (String path : walk.lists()) {
            assertTrue(SettingRegistry.isListPath(path), "unregistered list: " + path);
        }
        for (String path : SettingRegistry.listPaths()) {
            assertTrue(walk.lists().contains(path), "registry list missing in schema: " + path);
        }
    }

    private static Object parseDefault(SettingDescriptor descriptor) {
        return switch (descriptor.type()) {
            case BOOL -> Boolean.parseBoolean(descriptor.defaultValue());
            case INT -> Integer.parseInt(descriptor.defaultValue());
            case FLOAT -> Double.parseDouble(descriptor.defaultValue());
            case STRING, OPTION -> descriptor.defaultValue();
        };
    }

    private static String normalize(Object value) {
        if (value instanceof Number number) {
            return ConfigService.displayValue(number.doubleValue());
        }
        if (value instanceof Enum<?> option) {
            return option.name();
        }
        return String.valueOf(value);
    }

    private static SchemaWalk walk(OkaeriConfig root) {
        Map<String, Object> leaves = new HashMap<>();
        List<String> lists = new ArrayList<>();
        collectNode(root, "", leaves, lists);
        return new SchemaWalk(leaves, lists);
    }

    private static void collectNode(OkaeriConfig node, String prefix,
            Map<String, Object> leaves, List<String> lists) {
        for (Field field : node.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            CustomKey custom = field.getAnnotation(CustomKey.class);
            String key = custom == null ? field.getName() : custom.value();
            if (custom == null && !field.getName().equals(field.getName().toLowerCase(Locale.ROOT))) {
                fail("Field " + node.getClass().getSimpleName() + "." + field.getName()
                        + " needs @CustomKey");
            }
            field.setAccessible(true);
            Object value;
            try {
                value = field.get(node);
            } catch (IllegalAccessException exception) {
                throw new AssertionError(exception);
            }
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            collectValue(value, path, leaves, lists);
        }
    }

    private static void collectValue(Object value, String path,
            Map<String, Object> leaves, List<String> lists) {
        if (value instanceof OkaeriConfig nested) {
            collectNode(nested, path, leaves, lists);
        } else if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                collectValue(entry.getValue(), path + "." + entry.getKey(), leaves, lists);
            }
        } else if (value instanceof List) {
            lists.add(path);
        } else {
            leaves.put(path, value);
        }
    }

}
