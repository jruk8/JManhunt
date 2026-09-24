package com.jruk8.jmanhunt.modifiers.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One behavior commands block: role command lists by key. List keys
 * are user-owned, so custom lists round-trip untouched. A plain
 * object (not an OkaeriConfig) because {@link ModifierCommandsSerdes}
 * owns the whole free-form section.
 */
public class ModifierCommands {

    private final Map<String, List<String>> lists = new LinkedHashMap<>();

    public Map<String, List<String>> getLists() {
        return lists;
    }
}
