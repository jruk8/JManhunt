package com.jruk8.jmanhunt.settings;

public interface SettingsListener {
    void onStart();

    void onReload();

    // such that resources\settings\<dataPath>
    String getDataPath();
}
