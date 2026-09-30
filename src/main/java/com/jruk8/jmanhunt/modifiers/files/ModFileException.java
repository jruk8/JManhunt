package com.jruk8.jmanhunt.modifiers.files;

/**
 * One mod file that failed to parse or validate. The message is a
 * single line with no path in it; the loader prefixes
 * {@code <path>: } when reporting.
 */
public class ModFileException extends Exception {

    private static final long serialVersionUID = 1L;

    public ModFileException(String message) {
        super(message);
    }

    public ModFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
