package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.annotation.CustomKey;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dotted-path access over Okaeri schema objects. Field lookup matches the
 * {@link CustomKey} value first, then the exact field name, then the
 * kebab-case form, so every YAML path resolves without a naming strategy.
 * Map segments resolve against map keys. Reads return null and writes
 * return false when a path does not resolve.
 */
public final class ConfigPathMapper {

    private ConfigPathMapper() {
    }

    /** Reads the value at the path, or null when it does not resolve. */
    public static Object get(Object root, String path) {
        if (root == null || path == null || path.isEmpty()) {
            return null;
        }
        Object current = root;
        for (String segment : path.split("\\.")) {
            current = step(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /** Writes a pre-validated value at the path. False when it cannot apply. */
    public static boolean set(Object root, String path, Object value) {
        if (root == null || path == null || path.isEmpty()) {
            return false;
        }
        String[] segments = path.split("\\.");
        Object current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            current = step(current, segments[index]);
            if (current == null) {
                return false;
            }
        }
        return writeLeaf(current, segments[segments.length - 1], value);
    }

    private static Object step(Object current, String segment) {
        if (current instanceof Map<?, ?> map) {
            return mapGet(map, segment);
        }
        if (current instanceof List<?> list) {
            return listGet(list, segment);
        }
        Field field = findField(current.getClass(), segment);
        if (field == null) {
            return null;
        }
        try {
            return field.get(current);
        } catch (IllegalAccessException exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean writeLeaf(Object parent, String segment, Object value) {
        if (parent instanceof List list) {
            try {
                int index = Integer.parseInt(segment.trim());
                if (index < 0 || index >= list.size()) {
                    return false;
                }
                ((List<Object>) list).set(index, value);
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        }
        if (parent instanceof Map _map) {
            try {
                ((Map<String, Object>) parent).put(mapKey((Map<?, ?>) parent, segment), value);
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        }
        Field field = findField(parent.getClass(), segment);
        if (field == null) {
            return false;
        }
        Object coerced = coerce(field.getType(), value);
        if (coerced == null && value != null) {
            return false;
        }
        try {
            field.set(parent, coerced);
            return true;
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            return false;
        }
    }

    private static Object mapGet(Map<?, ?> map, String segment) {
        if (map.containsKey(segment)) {
            return map.get(segment);
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && key.equalsIgnoreCase(segment)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Object listGet(List<?> list, String segment) {
        try {
            int index = Integer.parseInt(segment.trim());
            return index >= 0 && index < list.size() ? list.get(index) : null;
        } catch (NumberFormatException expected) {
            return null;
        }
    }

    private static String mapKey(Map<?, ?> map, String segment) {
        if (map.containsKey(segment)) {
            return segment;
        }
        for (Object key : map.keySet()) {
            if (key instanceof String text && text.equalsIgnoreCase(segment)) {
                return text;
            }
        }
        return segment;
    }

    private static Field findField(Class<?> type, String segment) {
        Field fallback = null;
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            CustomKey custom = field.getAnnotation(CustomKey.class);
            String key = custom == null ? field.getName() : custom.value();
            if (key.equals(segment)) {
                field.setAccessible(true);
                return field;
            }
            if (fallback == null && (key.equalsIgnoreCase(segment)
                    || field.getName().equalsIgnoreCase(segment)
                    || toKebab(field.getName()).equalsIgnoreCase(segment))) {
                field.setAccessible(true);
                fallback = field;
            }
        }
        return fallback;
    }

    private static Object coerce(Class<?> target, Object value) {
        if (value == null) {
            return null;
        }
        // Class.isInstance always returns false for primitive types, so
        // normalize to the wrapper before the identity check. Without this,
        // writes to primitive boolean fields silently fail.
        Class<?> effective = target.isPrimitive() ? toWrapper(target) : target;
        if (effective.isInstance(value)) {
            return value;
        }
        if (target.isEnum() && value instanceof String raw) {
            return matchEnum(target, raw);
        }
        if (value instanceof Enum<?> enumValue && target == String.class) {
            return enumValue.name();
        }
        if (value instanceof Number number) {
            if (target == int.class || target == Integer.class) {
                return number.intValue();
            }
            if (target == long.class || target == Long.class) {
                return number.longValue();
            }
            if (target == double.class || target == Double.class) {
                return number.doubleValue();
            }
            if (target == float.class || target == Float.class) {
                return number.floatValue();
            }
        }
        if (target == String.class) {
            return String.valueOf(value);
        }
        return null;
    }

    private static Class<?> toWrapper(Class<?> primitive) {
        if (primitive == boolean.class) {
            return Boolean.class;
        }
        if (primitive == int.class) {
            return Integer.class;
        }
        if (primitive == long.class) {
            return Long.class;
        }
        if (primitive == double.class) {
            return Double.class;
        }
        if (primitive == float.class) {
            return Float.class;
        }
        return primitive;
    }

    private static Object matchEnum(Class<?> target, String raw) {
        for (Object constant : target.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(raw.trim())) {
                return constant;
            }
        }
        return null;
    }

    private static String toKebab(String camel) {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < camel.length(); index++) {
            char cell = camel.charAt(index);
            if (Character.isUpperCase(cell) && index > 0) {
                out.append('-');
            }
            out.append(Character.toLowerCase(cell));
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }
}
