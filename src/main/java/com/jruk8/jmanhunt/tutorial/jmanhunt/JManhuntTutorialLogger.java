package com.jruk8.jmanhunt.tutorial.jmanhunt;

import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.tutorial.ports.TutorialLogger;

/** Routes engine logging to the plugin logger. */
public final class JManhuntTutorialLogger implements TutorialLogger {

    private final JManhuntLogger logger;

    public JManhuntTutorialLogger(JManhuntLogger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warning(message);
    }
}
