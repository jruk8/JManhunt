package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.serdes.OkaeriSerdesPack;
import eu.okaeri.configs.serdes.SerdesRegistry;

/** Registers the commands-block serializer with the modifiers store. */
public class ModifierCommandsPack implements OkaeriSerdesPack {

    @Override
    public void register(SerdesRegistry registry) {
        registry.register(new ModifierCommandsSerdes());
    }
}
