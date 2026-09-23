package com.jruk8.jmanhunt.updatechecker.jmanhunt;

import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.updatechecker.ports.UpdateCheckLogger;

/** Routes engine logging to the plugin logger. */
public final class JManhuntUpdateCheckLogger implements UpdateCheckLogger {
    private final JManhuntLogger logger;

    public JManhuntUpdateCheckLogger(JManhuntLogger logger) {
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

    @Override
    public void severe(String message) {
        logger.severe(message);
    }
}
