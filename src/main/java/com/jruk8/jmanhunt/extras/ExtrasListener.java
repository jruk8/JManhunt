package com.jruk8.jmanhunt.extras;

public interface ExtrasListener {
    void onReload();

    // such that resources\extras\<dataPath>
    String getDataPath();
}
