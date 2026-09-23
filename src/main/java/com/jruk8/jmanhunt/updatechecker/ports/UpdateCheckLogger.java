package com.jruk8.jmanhunt.updatechecker.ports;

/** Engine logging, adapted to the host plugin's logger. */
public interface UpdateCheckLogger {
    void info(String message);

    void warning(String message);

    void severe(String message);
}
