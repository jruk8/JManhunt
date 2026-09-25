package com.jruk8.jmanhunt.config;

public interface SettingsListener {
    void onStart();

    void onReload();

    // Full resource path relative to src/main/resources/
    String getDataPath();
}