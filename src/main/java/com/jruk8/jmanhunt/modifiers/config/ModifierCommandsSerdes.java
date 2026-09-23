package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.schema.GenericsDeclaration;
import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.ObjectSerializer;
import eu.okaeri.configs.serdes.SerializationData;
import org.bukkit.configuration.ConfigurationSection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes behavior commands blocks. The {@code execution}
 * section splits out into {@link ModifierExecution}; every other
 * list-valued key round-trips verbatim so renamed or custom lists
 * survive. Non-list garbage is skipped, never fatal.
 *
 * <p>Execution maps by hand on both sides: {@code DeserializationData}
 * getters only do scalar and transformer conversions, so nested
 * objects would silently come back null. Section values arrive as
 * Bukkit sections on this backend, so execution reads those
 * directly; plain maps stay supported for other callers.
 */
public class ModifierCommandsSerdes implements ObjectSerializer<ModifierCommands> {

    private static final String EXECUTION_KEY = "execution";
    private static final String PICK_RANDOM_KEY = "pick-random";

    @Override
    public boolean supports(Class<? super ModifierCommands> type) {
        return ModifierCommands.class.isAssignableFrom(type);
    }

    @Override
    public void serialize(ModifierCommands object, SerializationData data, GenericsDeclaration generics) {
        if (object.getExecution() != null) {
            Map<String, Object> section = writeExecution(object.getExecution());
            if (!section.isEmpty()) {
                data.addRaw(EXECUTION_KEY, section);
            }
        }
        for (Map.Entry<String, List<String>> entry : object.getLists().entrySet()) {
            data.addCollection(entry.getKey(), entry.getValue(), String.class);
        }
    }

    @Override
    public ModifierCommands deserialize(DeserializationData data, GenericsDeclaration generics) {
        ModifierCommands commands = new ModifierCommands();
        for (Map.Entry<?, ?> entry : data.asMap().entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (EXECUTION_KEY.equals(key)) {
                if (entry.getValue() instanceof ConfigurationSection section) {
                    commands.setExecution(readExecution(section));
                } else if (entry.getValue() instanceof Map<?, ?> map) {
                    commands.setExecution(readExecution(map));
                }
                continue;
            }
            if (entry.getValue() instanceof List) {
                commands.getLists().put(key, data.getAsList(key, String.class));
            }
        }
        return commands;
    }

    private static ModifierExecution readExecution(ConfigurationSection section) {
        ModifierExecution execution = new ModifierExecution();
        execution.setSelection(section.getString("selection"));
        ConfigurationSection pick = section.getConfigurationSection(PICK_RANDOM_KEY);
        if (pick != null) {
            ModifierPickRandom pickRandom = new ModifierPickRandom();
            if (pick.get("count") instanceof Number count) {
                pickRandom.setCount(count.intValue());
            }
            pickRandom.setBehavior(pick.getString("behavior"));
            execution.setPickRandom(pickRandom);
        }
        return execution;
    }

    private static ModifierExecution readExecution(Map<?, ?> section) {
        ModifierExecution execution = new ModifierExecution();
        execution.setSelection(stringOrNull(section.get("selection")));
        if (section.get(PICK_RANDOM_KEY) instanceof Map<?, ?> pick) {
            ModifierPickRandom pickRandom = new ModifierPickRandom();
            if (pick.get("count") instanceof Number count) {
                pickRandom.setCount(count.intValue());
            }
            pickRandom.setBehavior(stringOrNull(pick.get("behavior")));
            execution.setPickRandom(pickRandom);
        }
        return execution;
    }

    private static Map<String, Object> writeExecution(ModifierExecution execution) {
        Map<String, Object> section = new LinkedHashMap<>();
        if (execution.getSelection() != null) {
            section.put("selection", execution.getSelection());
        }
        if (execution.getPickRandom() != null) {
            Map<String, Object> pick = new LinkedHashMap<>();
            if (execution.getPickRandom().getCount() != null) {
                pick.put("count", execution.getPickRandom().getCount());
            }
            if (execution.getPickRandom().getBehavior() != null) {
                pick.put("behavior", execution.getPickRandom().getBehavior());
            }
            if (!pick.isEmpty()) {
                section.put(PICK_RANDOM_KEY, pick);
            }
        }
        return section;
    }

    private static String stringOrNull(Object value) {
        return value instanceof String text ? text : value == null ? null : String.valueOf(value);
    }
}
