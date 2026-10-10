package dev.chrisbarbati.pushoverclient.callback;

import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses form-encoded Pushover emergency callback payloads.
 *
 * @since 0.4.0
 * @author Christian Barbati
 */
public final class CallbackPayloadParser {
    private CallbackPayloadParser() {
    }

    /**
     * Parses a raw {@code application/x-www-form-urlencoded} body into callback fields.
     *
     * @param body raw request body
     * @return immutable ordered field map
     * @since 0.4.0
     */
    public static Map<String, String> parseFormEncodedFields(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        if (body == null || body.isBlank()) {
            return Map.copyOf(values);
        }

        for (String pair : body.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return Map.copyOf(values);
    }

    /**
     * Parses a raw {@code application/x-www-form-urlencoded} body into a typed callback response.
     *
     * @param body raw request body
     * @return typed callback response
     * @since 0.4.0
     */
    public static ReceiptCallbackResponse parseFormEncodedBody(String body) {
        return ReceiptCallbackResponse.fromFields(parseFormEncodedFields(body));
    }
}


