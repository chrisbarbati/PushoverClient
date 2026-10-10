package dev.chrisbarbati.pushoverclient.callback;

import dev.chrisbarbati.pushoverclient.exception.PushoverClientException;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for callback payload parsing shared by servlet and Jetty adapters.
 *
 * @since 0.4.0
 * @author Christian Barbati
 */
class CallbackPayloadParserTest {
    @Test
    void parseFormEncodedFields_decodesUrlEncodedPairs() {
        Map<String, String> fields = CallbackPayloadParser.parseFormEncodedFields(
                "receipt=abcdefghijklmnopqrstuvwx123456&acknowledged_by=ops%2Bprimary&note=hello+world"
        );

        assertEquals("abcdefghijklmnopqrstuvwx123456", fields.get("receipt"));
        assertEquals("ops+primary", fields.get("acknowledged_by"));
        assertEquals("hello world", fields.get("note"));
    }

    @Test
    void parseFormEncodedBody_supportsReceiptAliasFields() {
        ReceiptCallbackResponse response = CallbackPayloadParser.parseFormEncodedBody(
                "receipt_id=abcdefghijklmnopqrstuvwx123456&acknowledged=1&called_back=1"
        );

        assertEquals("abcdefghijklmnopqrstuvwx123456", response.receipt());
        assertEquals(1, response.acknowledged());
        assertEquals(1, response.calledBack());
    }

    @Test
    void parseFormEncodedBody_rejectsMissingReceipt() {
        PushoverClientException ex = assertThrows(
                PushoverClientException.class,
                () -> CallbackPayloadParser.parseFormEncodedBody("acknowledged=1")
        );

        assertEquals("Callback payload did not include a receipt", ex.getMessage());
    }
}

