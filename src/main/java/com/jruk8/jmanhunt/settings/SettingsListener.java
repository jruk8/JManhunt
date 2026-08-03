package com.jruk8.jmanhunt.settings;

public interface SettingsListener {
    void onStart();

    void onReload();

    // Full resource path relative to src/main/resources/
    String getDataPath();
}