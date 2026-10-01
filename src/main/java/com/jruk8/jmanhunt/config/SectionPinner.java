package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps nested config section instances stable across Okaeri loads.
 * Okaeri load() replaces nested section objects, which would strand
 * consumers holding the old ones with stale values. Each registrar
 * calls {@link #pin(Object)} after every load: the first call captures
 * the live sections as canonical, later calls copy fresh values into
 * them and re-point the root at them. Consumers hold canonical refs
 * forever and always read current values.
 */
public final class SectionPinner {

    private final Map<String, Section> canonical = new LinkedHashMap<>();

    private record Section(Field rootField, Object instance) {
    }

    /**
     * Pins the nested sections of one root after a load. Self
     * initializing: the first call adopts the live instances.
     */
    public void pin(Object root) {
        if (root == null) {
            return;
        }
        for (Field field : root.getClass().getDeclaredFields()) {
            if (skip(field) || !OkaeriConfig.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object fresh = read(field, root);
            Section pinned = canonical.get(key(field));
            if (pinned == null) {
                if (fresh != null) {
                    canonical.put(key(field), new Section(field, fresh));
                }
                continue;
            }
            if (fresh != null && fresh != pinned.instance()) {
                copyFields(fresh, pinned.instance());
            }
            write(pinned.rootField(), root, pinned.instance());
        }
    }

    private static void copyFields(Object from, Object to) {
        for (Field field : from.getClass().getDeclaredFields()) {
            if (skip(field)) {
                continue;
            }
            field.setAccessible(true);
            Object fresh = read(field, from);
            Object current = read(field, to);
            if (fresh == current) {
                continue;
            }
            if (fresh instanceof OkaeriConfig && current instanceof OkaeriConfig) {
                copyFields(fresh, current);
                continue;
            }
            // A null fresh value keeps the last good canonical value.
            if (fresh != null) {
                write(field, to, fresh);
            }
        }
    }

    private static boolean skip(Field field) {
        return Modifier.isStatic(field.getModifiers()) || field.isSynthetic();
    }

    private static String key(Field field) {
        CustomKey custom = field.getAnnotation(CustomKey.class);
        return custom == null ? field.getName() : custom.value();
    }

    private static Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read " + owner.getClass().getSimpleName()
                    + "." + field.getName(), exception);
        }
    }

    private static void write(Field field, Object owner, Object value) {
        try {
            field.set(owner, value);
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot write " + owner.getClass().getSimpleName()
                    + "." + field.getName(), exception);
        }
    }
}
