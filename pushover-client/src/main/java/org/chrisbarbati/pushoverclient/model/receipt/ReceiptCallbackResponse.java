package org.chrisbarbati.pushoverclient.model.receipt;

import org.chrisbarbati.pushoverclient.exception.PushoverClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed payload received from a Pushover emergency callback POST.
 *
 * @param receipt emergency receipt id associated with the callback
 * @param acknowledged 1 when acknowledged, otherwise null when absent
 * @param acknowledgedAt unix timestamp for acknowledgement time when present
 * @param acknowledgedBy user who acknowledged the notification when present
 * @param acknowledgedByDevice device that acknowledged the notification when present
 * @param expired 1 when expired, otherwise null when absent
 * @param expiresAt unix timestamp when the receipt expired when present
 * @param calledBack 1 when Pushover marks the callback as invoked, otherwise null when absent
 * @param calledBackAt unix timestamp when the callback was invoked when present
 * @param rawFields immutable map of all received form fields
 * @since 0.3.0
 * @author Christian Barbati
 */
public record ReceiptCallbackResponse(
        String receipt,
        Integer acknowledged,
        Long acknowledgedAt,
        String acknowledgedBy,
        String acknowledgedByDevice,
        Integer expired,
        Long expiresAt,
        Integer calledBack,
        Long calledBackAt,
        Map<String, String> rawFields
) {
    /**
     * Creates a typed callback response from form-encoded POST fields.
     *
     * @param fields parsed callback fields
     * @return typed callback response
     * @since 0.3.0
     */
    public static ReceiptCallbackResponse fromFields(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new PushoverClientException("Callback payload is required");
        }

        Map<String, String> raw = Map.copyOf(new LinkedHashMap<>(fields));
        String receipt = firstNonBlank(raw, "receipt", "receipt_id", "message_receipt");
        if (receipt == null || receipt.isBlank()) {
            throw new PushoverClientException("Callback payload did not include a receipt");
        }

        return new ReceiptCallbackResponse(
                receipt,
                parseInteger(raw.get("acknowledged")),
                parseLong(raw.get("acknowledged_at")),
                raw.get("acknowledged_by"),
                raw.get("acknowledged_by_device"),
                parseInteger(raw.get("expired")),
                parseLong(raw.get("expires_at")),
                parseInteger(raw.get("called_back")),
                parseLong(raw.get("called_back_at")),
                raw
        );
    }

    private static String firstNonBlank(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = fields.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new PushoverClientException("Callback field was not a valid integer: " + value, ex);
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new PushoverClientException("Callback field was not a valid long: " + value, ex);
        }
    }
}


