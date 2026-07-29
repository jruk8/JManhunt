package com.jruk8.jmanhunt;

public interface ExtrasListener {
    void onReload();

    // such that resources\extras\<dataPath>
    String getDataPath();
}
