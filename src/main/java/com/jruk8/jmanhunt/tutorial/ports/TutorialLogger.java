package com.jruk8.jmanhunt.tutorial.ports;

/** Engine logging, adapted to the host plugin's logger. */
public interface TutorialLogger {

    void info(String message);

    void warning(String message);
}
