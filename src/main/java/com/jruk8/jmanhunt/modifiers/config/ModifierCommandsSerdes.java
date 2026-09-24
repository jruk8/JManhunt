package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.schema.GenericsDeclaration;
import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.ObjectSerializer;
import eu.okaeri.configs.serdes.SerializationData;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes behavior commands blocks. Every list-valued key
 * round-trips verbatim so renamed or custom lists survive. Non-list
 * garbage is skipped, never fatal.
 */
public class ModifierCommandsSerdes implements ObjectSerializer<ModifierCommands> {

    @Override
    public boolean supports(Class<? super ModifierCommands> type) {
        return ModifierCommands.class.isAssignableFrom(type);
    }

    @Override
    public void serialize(ModifierCommands object, SerializationData data, GenericsDeclaration generics) {
        for (Map.Entry<String, List<String>> entry : object.getLists().entrySet()) {
            data.addCollection(entry.getKey(), entry.getValue(), String.class);
        }
    }

    @Override
    public ModifierCommands deserialize(DeserializationData data, GenericsDeclaration generics) {
        ModifierCommands commands = new ModifierCommands();
        for (Map.Entry<?, ?> entry : data.asMap().entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (entry.getValue() instanceof List) {
                commands.getLists().put(key, data.getAsList(key, String.class));
            }
        }
        return commands;
    }
}
