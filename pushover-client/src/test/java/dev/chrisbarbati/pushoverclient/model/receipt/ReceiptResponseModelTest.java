package dev.chrisbarbati.pushoverclient.model.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

/**
 * Unit tests for receipt response model deserialization.
 *
 * @since 0.2.0
 * @author Christian Barbati
 */
class ReceiptResponseModelTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Verifies snake_case receipt payload fields map onto the typed status model.
     *
     * @throws Exception when JSON parsing fails unexpectedly
     * @since 0.2.0
     */
    @Test
    void receiptStatusResponse_deserializesAllDocumentedFields() throws Exception {
        String json = """
                {
                  "status": 1,
                  "request": "req-receipt",
                  "receipt": "abcdefghijklmnopqrstuvwx123456",
                  "acknowledged": 1,
                  "acknowledged_at": 1715800000,
                  "acknowledged_by": "chris",
                  "acknowledged_by_device": "pixel",
                  "last_delivered_at": 1715799990,
                  "expired": 0,
                  "expires_at": 1715800300,
                  "called_back": 1,
                  "called_back_at": 1715800010
                }
                """;

        ReceiptStatusResponse response = OBJECT_MAPPER.readValue(json, ReceiptStatusResponse.class);

        assertEquals(1, response.status());
        assertEquals("req-receipt", response.request());
        assertEquals("abcdefghijklmnopqrstuvwx123456", response.receipt());
        assertEquals(1, response.acknowledged());
        assertEquals(1715800000L, response.acknowledgedAt());
        assertEquals("chris", response.acknowledgedBy());
        assertEquals("pixel", response.acknowledgedByDevice());
        assertEquals(1715799990L, response.lastDeliveredAt());
        assertEquals(0, response.expired());
        assertEquals(1715800300L, response.expiresAt());
        assertEquals(1, response.calledBack());
        assertEquals(1715800010L, response.calledBackAt());
    }

    /**
     * Verifies receipt cancellation payloads deserialize cleanly.
     *
     * @throws Exception when JSON parsing fails unexpectedly
     * @since 0.2.0
     */
    @Test
    void receiptCancellationResponse_deserializesStatusAndRequest() throws Exception {
        ReceiptCancellationResponse response = OBJECT_MAPPER.readValue(
                "{\"status\":1,\"request\":\"req-cancel\"}",
                ReceiptCancellationResponse.class
        );

        assertEquals(1, response.status());
        assertEquals("req-cancel", response.request());
    }

    /**
     * Verifies absent optional receipt timestamps remain null.
     *
     * @throws Exception when JSON parsing fails unexpectedly
     * @since 0.2.0
     */
    @Test
    void receiptStatusResponse_allowsMissingOptionalFields() throws Exception {
        ReceiptStatusResponse response = OBJECT_MAPPER.readValue(
                "{\"status\":1,\"request\":\"req-min\",\"acknowledged\":0,\"expired\":0,\"called_back\":0}",
                ReceiptStatusResponse.class
        );

        assertNull(response.receipt());
        assertNull(response.acknowledgedAt());
        assertNull(response.acknowledgedBy());
        assertNull(response.acknowledgedByDevice());
        assertNull(response.lastDeliveredAt());
        assertNull(response.expiresAt());
        assertNull(response.calledBackAt());
    }

    /**
     * Verifies callback POST form fields map onto the typed callback response model.
     *
     * @since 0.3.0
     */
    @Test
    void receiptCallbackResponse_fromFields_mapsTypedValues() {
        ReceiptCallbackResponse response = ReceiptCallbackResponse.fromFields(Map.of(
                "receipt", "abcdefghijklmnopqrstuvwx123456",
                "acknowledged", "1",
                "acknowledged_at", "1715800000",
                "acknowledged_by", "chris",
                "acknowledged_by_device", "pixel"
        ));

        assertEquals("abcdefghijklmnopqrstuvwx123456", response.receipt());
        assertEquals(1, response.acknowledged());
        assertEquals(1715800000L, response.acknowledgedAt());
        assertEquals("chris", response.acknowledgedBy());
        assertEquals("pixel", response.acknowledgedByDevice());
    }

    /**
     * Verifies alternate receipt field names can still be mapped from callback POST payloads.
     *
     * @since 0.3.0
     */
    @Test
    void receiptCallbackResponse_fromFields_acceptsAlternateReceiptKey() {
        ReceiptCallbackResponse response = ReceiptCallbackResponse.fromFields(Map.of(
                "receipt_id", "abcdefghijklmnopqrstuvwx123456",
                "acknowledged", "1"
        ));

        assertEquals("abcdefghijklmnopqrstuvwx123456", response.receipt());
        assertEquals(1, response.acknowledged());
    }

    /**
     * Verifies malformed callback payloads fail fast when the required receipt field is absent.
     *
     * @since 0.3.0
     */
    @Test
    void receiptCallbackResponse_fromFields_requiresReceipt() {
        assertThrows(RuntimeException.class, () -> ReceiptCallbackResponse.fromFields(Map.of("acknowledged", "1")));
    }
}



