package dev.chrisbarbati.pushoverclient.exception;

/**
 * Base unchecked exception for transport, validation, or parsing failures in this client.
 *
 * @since 0.1.0
 * @author Christian Barbati
 */
public class PushoverClientException extends RuntimeException {
    /**
     * Creates an exception with a message.
     *
     * @param message error description
     * @since 0.1.0
     */
    public PushoverClientException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and cause.
     *
     * @param message error description
     * @param cause root cause
     * @since 0.1.0
     */
    public PushoverClientException(String message, Throwable cause) {
        super(message, cause);
    }
}


