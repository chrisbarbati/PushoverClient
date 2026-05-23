package org.chrisbarbati.pushoverclient.async;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for async receipt property defaults and validation.
 *
 * @since 0.5.0
 * @author Christian Barbati
 */
class AsyncReceiptPropertiesTest {

    /**
     * Verifies library defaults are stable and positive.
     *
     * @since 0.5.0
     */
    @Test
    void defaults_returnsExpectedValues() {
        AsyncReceiptProperties properties = AsyncReceiptProperties.defaults();

        assertEquals(Duration.ofSeconds(5), properties.pollInterval());
        assertEquals(Duration.ofMinutes(10), properties.pollFutureExpiration());
        assertEquals(Duration.ofMinutes(10), properties.callbackFutureExpiration());
        assertNull(properties.defaultCallbackUrl());
        assertTrue(properties.warnOnConflictingCallback());
    }

    /**
     * Verifies blank default callback URLs are normalized to null.
     *
     * @since 0.5.0
     */
    @Test
    void builder_normalizesBlankDefaultCallbackUrl() {
        AsyncReceiptProperties properties = AsyncReceiptProperties.builder()
                .defaultCallbackUrl("   ")
                .build();

        assertNull(properties.defaultCallbackUrl());
    }

    /**
     * Verifies custom builder values are retained on the immutable configuration.
     *
     * @since 0.5.0
     */
    @Test
    void builder_appliesCustomValues() {
        AsyncReceiptProperties properties = AsyncReceiptProperties.builder()
                .pollInterval(Duration.ofMillis(250))
                .pollFutureExpiration(Duration.ofSeconds(30))
                .callbackFutureExpiration(Duration.ofSeconds(45))
                .defaultCallbackUrl(" https://callbacks.example.test/pushover/callback ")
                .warnOnConflictingCallback(false)
                .build();

        assertEquals(Duration.ofMillis(250), properties.pollInterval());
        assertEquals(Duration.ofSeconds(30), properties.pollFutureExpiration());
        assertEquals(Duration.ofSeconds(45), properties.callbackFutureExpiration());
        assertEquals("https://callbacks.example.test/pushover/callback", properties.defaultCallbackUrl());
        org.junit.jupiter.api.Assertions.assertFalse(properties.warnOnConflictingCallback());
    }

    /**
     * Verifies polling interval validation rejects null, zero, and negative durations.
     *
     * @since 0.5.0
     */
    @Test
    void builder_rejectsInvalidPollInterval() {
        assertThrows(IllegalArgumentException.class, () -> AsyncReceiptProperties.builder()
                .pollInterval(null)
                .build());
        assertThrows(IllegalArgumentException.class, () -> AsyncReceiptProperties.builder()
                .pollInterval(Duration.ZERO)
                .build());
        assertThrows(IllegalArgumentException.class, () -> AsyncReceiptProperties.builder()
                .pollInterval(Duration.ofSeconds(-1))
                .build());
    }

    /**
     * Verifies polling future expiration validation rejects null, zero, and negative durations.
     *
     * @since 0.5.0
     */
    @Test
    void builder_rejectsInvalidPollFutureExpiration() {
        assertThrows(IllegalArgumentException.class, () -> AsyncReceiptProperties.builder()
                .pollFutureExpiration(null)
                .build());
        assertThrows(IllegalArgumentException.class, () -> AsyncReceiptProperties.builder()
                .pollFutureExpiration(Duration.ZERO)
                .build());
        assertThrows(IllegalArgumentException.class, () -> AsyncReceiptProperties.builder()
                .pollFutureExpiration(Duration.ofSeconds(-1))
                .build());
    }

    /**
     * Verifies callback future expiration validation rejects null, zero, and negative durations.
     *
     * @since 0.5.0
     */
    @Test
    void builder_rejectsInvalidCallbackFutureExpiration() {
        assertThrows(IllegalArgumentException.class, () -> AsyncReceiptProperties.builder()
                .callbackFutureExpiration(null)
                .build());
        assertThrows(IllegalArgumentException.class, () -> AsyncReceiptProperties.builder()
                .callbackFutureExpiration(Duration.ZERO)
                .build());
        assertThrows(IllegalArgumentException.class, () -> AsyncReceiptProperties.builder()
                .callbackFutureExpiration(Duration.ofSeconds(-1))
                .build());
    }
}

