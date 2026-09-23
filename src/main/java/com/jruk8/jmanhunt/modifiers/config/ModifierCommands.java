package com.jruk8.jmanhunt.modifiers.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One behavior commands block: role command lists by key plus the
 * optional shared execution settings. List keys are user-owned, so
 * custom lists round-trip untouched. A plain object (not an
 * OkaeriConfig) because {@link ModifierCommandsSerdes} owns the
 * whole section: lists and the execution section share one mapping.
 */
public class ModifierCommands {

    private final Map<String, List<String>> lists = new LinkedHashMap<>();
    private ModifierExecution execution;

    public Map<String, List<String>> getLists() {
        return lists;
    }

    public ModifierExecution getExecution() {
        return execution;
    }

    public void setExecution(ModifierExecution execution) {
        this.execution = execution;
    }
}
