package swd392.group6.AIVES.ai;

/** An AI provider failed, timed out or returned nothing usable. Callers apply their degrade path. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
