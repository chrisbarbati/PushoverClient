package dev.chrisbarbati.pushoverclient.async;

import java.time.Duration;

/**
 * Client-level configuration for asynchronous emergency receipt workflows.
 *
 * @since 0.3.0
 * @author Christian Barbati
 */
public final class AsyncReceiptProperties {
    /** Property name for the polling interval setting. */
    public static final String POLL_INTERVAL_PROPERTY = "pollInterval";
    /** Property name for the polling future expiration setting. */
    public static final String POLL_FUTURE_EXPIRATION_PROPERTY = "pollFutureExpiration";
    /** Property name for the callback future expiration setting. */
    public static final String CALLBACK_FUTURE_EXPIRATION_PROPERTY = "callbackFutureExpiration";
    /** Property name for the default callback URL setting. */
    public static final String DEFAULT_CALLBACK_URL_PROPERTY = "defaultCallbackUrl";
    /** Property name for the conflicting-callback warning toggle. */
    public static final String WARN_ON_CONFLICTING_CALLBACK_PROPERTY = "warnOnConflictingCallback";

    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration DEFAULT_POLL_FUTURE_EXPIRATION = Duration.ofMinutes(10);
    private static final Duration DEFAULT_CALLBACK_FUTURE_EXPIRATION = Duration.ofMinutes(10);

    private final Duration pollInterval;
    private final Duration pollFutureExpiration;
    private final Duration callbackFutureExpiration;
    private final String defaultCallbackUrl;
    private final boolean warnOnConflictingCallback;

    private AsyncReceiptProperties(Builder builder) {
        this.pollInterval = requirePositive(builder.pollInterval, POLL_INTERVAL_PROPERTY);
        this.pollFutureExpiration = requirePositive(builder.pollFutureExpiration, POLL_FUTURE_EXPIRATION_PROPERTY);
        this.callbackFutureExpiration = requirePositive(builder.callbackFutureExpiration, CALLBACK_FUTURE_EXPIRATION_PROPERTY);
        this.defaultCallbackUrl = normalizeBlank(builder.defaultCallbackUrl);
        this.warnOnConflictingCallback = builder.warnOnConflictingCallback;
    }

    /**
     * @return default async receipt properties
     * @since 0.3.0
     */
    public static AsyncReceiptProperties defaults() {
        return builder().build();
    }

    /**
     * @return builder initialized with library defaults
     * @since 0.3.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return polling interval used by the polling-based future flow
     * @since 0.3.0
     */
    public Duration pollInterval() {
        return pollInterval;
    }

    /**
     * @return default expiration used by polling futures when no override is supplied
     * @since 0.3.0
     */
    public Duration pollFutureExpiration() {
        return pollFutureExpiration;
    }

    /**
     * @return default expiration used by callback futures when no override is supplied
     * @since 0.3.0
     */
    public Duration callbackFutureExpiration() {
        return callbackFutureExpiration;
    }

    /**
     * @return default callback URL to send when the request does not provide one
     * @since 0.3.0
     */
    public String defaultCallbackUrl() {
        return defaultCallbackUrl;
    }

    /**
     * @return true when conflicting request/default callbacks should log a warning
     * @since 0.3.0
     */
    public boolean warnOnConflictingCallback() {
        return warnOnConflictingCallback;
    }

    private static Duration requirePositive(Duration value, String propertyName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be a positive duration");
        }
        return value;
    }

    private static String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * Fluent builder for async receipt properties.
     *
     * @since 0.3.0
     */
    public static final class Builder {
        private Duration pollInterval = DEFAULT_POLL_INTERVAL;
        private Duration pollFutureExpiration = DEFAULT_POLL_FUTURE_EXPIRATION;
        private Duration callbackFutureExpiration = DEFAULT_CALLBACK_FUTURE_EXPIRATION;
        private String defaultCallbackUrl;
        private boolean warnOnConflictingCallback = true;

        private Builder() {
        }

        /**
         * @param pollInterval polling interval used by polling-based acknowledgement lookups
         * @return this builder
         * @since 0.3.0
         */
        public Builder pollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        /**
         * @param pollFutureExpiration default lifetime for polling futures
         * @return this builder
         * @since 0.3.0
         */
        public Builder pollFutureExpiration(Duration pollFutureExpiration) {
            this.pollFutureExpiration = pollFutureExpiration;
            return this;
        }

        /**
         * @param callbackFutureExpiration default lifetime for callback futures
         * @return this builder
         * @since 0.3.0
         */
        public Builder callbackFutureExpiration(Duration callbackFutureExpiration) {
            this.callbackFutureExpiration = callbackFutureExpiration;
            return this;
        }

        /**
         * @param defaultCallbackUrl default callback URL to send when the request omits one
         * @return this builder
         * @since 0.3.0
         */
        public Builder defaultCallbackUrl(String defaultCallbackUrl) {
            this.defaultCallbackUrl = defaultCallbackUrl;
            return this;
        }

        /**
         * @param warnOnConflictingCallback true to log when a request callback overrides the configured default
         * @return this builder
         * @since 0.3.0
         */
        public Builder warnOnConflictingCallback(boolean warnOnConflictingCallback) {
            this.warnOnConflictingCallback = warnOnConflictingCallback;
            return this;
        }

        /**
         * @return immutable async receipt properties
         * @since 0.3.0
         */
        public AsyncReceiptProperties build() {
            return new AsyncReceiptProperties(this);
        }
    }
}


