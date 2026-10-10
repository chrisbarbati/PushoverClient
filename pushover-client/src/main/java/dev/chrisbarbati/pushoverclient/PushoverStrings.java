package dev.chrisbarbati.pushoverclient;

import dev.chrisbarbati.pushoverclient.exception.PushoverClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Shared string, encoding, and map helpers for the client internals.
 *
 * @since 0.7.0
 */
final class PushoverStrings {
    private PushoverStrings() {
    }

    /**
     * @param value candidate value
     * @return true when null or blank
     */
    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * @param value candidate value
     * @return trimmed value, or null when blank
     */
    static String normalizeBlank(String value) {
        return isBlank(value) ? null : value.trim();
    }

    /**
     * @param value candidate value
     * @param message exception message when blank
     * @return original value when non-blank
     * @throws PushoverClientException when value is blank
     */
    static String requireNonBlank(String value, String message) {
        if (isBlank(value)) {
            throw new PushoverClientException(message);
        }
        return value;
    }

    /**
     * @param fields output map
     * @param key field name
     * @param value field value
     */
    static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (!isBlank(value)) {
            fields.put(key, value);
        }
    }

    /**
     * Percent-encodes values for form body and query string usage.
     *
     * @param value unescaped input
     * @return URL-encoded string
     */
    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Percent-encodes a single path segment for dynamic endpoint usage.
     *
     * @param value unescaped path segment
     * @return encoded path segment
     */
    static String encodePathSegment(String value) {
        return encode(value).replace("+", "%20");
    }
}