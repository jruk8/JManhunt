package com.jruk8.jmanhunt.extras;

public interface ExtrasListener {
    void onStart();

    void onReload();

    // such that resources\extras\<dataPath>
    String getDataPath();
}
