package com.jruk8.jmanhunt.loot;

import com.google.gson.JsonElement;

public record Function(String function, JsonElement count, String id) {}