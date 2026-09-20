package com.jruk8.jmanhunt.message;

public record SoundSettings(String configKey, boolean enabled, String sound, float pitch, float volume) {}