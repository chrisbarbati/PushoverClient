package dev.chrisbarbati.pushoverclient.model;

/**
 * Common contract shared by all successful Pushover API response payloads.
 *
 * <p>Every Pushover API response includes a {@code status} code and a
 * {@code request} identifier, so this interface lets the transport layer read
 * those fields uniformly without type-specific handling.</p>
 *
 * @since 1.0.0
 */
public interface PushoverResponse {

    /**
     * @return Pushover status value (1 indicates success)
     */
    int status();

    /**
     * @return unique request id for support/tracing
     */
    String request();
}
