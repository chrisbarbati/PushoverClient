package org.chrisbarbati.pushoverclient;

/**
 * Relative endpoint paths used by the Pushover API.
 *
 * @since 0.1.0
 * @author Christian Barbati
 */
final class PushoverEndpoints {
    /** Messages endpoint for creating notifications. */
    static final String SEND_MESSAGE = "/1/messages.json";
    /** Sounds endpoint for available built-in and custom sounds. */
    static final String SOUNDS = "/1/sounds.json";
    /** Limits endpoint for monthly quota usage. */
    static final String LIMITS = "/1/apps/limits.json";

    /**
     * Utility class.
     *
     * @since 0.1.0
     * @author Christian Barbati
     */
    private PushoverEndpoints() {
    }
}

