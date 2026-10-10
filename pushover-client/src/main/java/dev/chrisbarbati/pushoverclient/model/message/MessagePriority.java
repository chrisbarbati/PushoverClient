package dev.chrisbarbati.pushoverclient.model.message;

/**
 * Pushover message priority values.
 *
 * @since 0.1.0
 * @author Christian Barbati
 */
public enum MessagePriority {
    /** Lowest priority (-2): no notification, badge only on iOS. */
    LOWEST(-2),
    /** Low priority (-1): quiet notification. */
    LOW(-1),
    /** Normal priority (0): default notification behavior. */
    NORMAL(0),
    /** High priority (1): bypasses quiet hours. */
    HIGH(1),
    /** Emergency priority (2): requires retry + expire. */
    EMERGENCY(2);

    private final int value;

    /**
     * Creates an enum constant with the documented numeric priority value.
     *
     * @param value integer value accepted by Pushover
     * @since 0.1.0
     */
    MessagePriority(int value) {
        this.value = value;
    }

    /**
     * @return raw integer value expected by the Pushover API
     * @since 0.1.0
     */
    public int value() {
        return value;
    }
}


