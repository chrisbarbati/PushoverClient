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
    /** User/group validation endpoint for verifying target identifiers and optional devices. */
    static final String VALIDATE_USER_OR_GROUP = "/1/users/validate.json";
    /** Sounds endpoint for available built-in and custom sounds. */
    static final String SOUNDS = "/1/sounds.json";
    /** Limits endpoint for monthly quota usage. */
    static final String LIMITS = "/1/apps/limits.json";
    /** Receipt endpoint prefix for emergency notification lifecycle calls. */
    static final String RECEIPTS = "/1/receipts";
    /** Cancel-by-tag endpoint prefix for emergency notification lifecycle calls. */
    static final String CANCEL_BY_TAG = "/1/receipts/cancel_by_tag";

    /**
     * Utility class.
     *
     * @since 0.1.0
     */
    private PushoverEndpoints() {
    }
}


