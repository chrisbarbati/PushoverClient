package org.chrisbarbati.pushoverclient;

import org.chrisbarbati.pushoverclient.exception.PushoverClientException;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for request validation failures handled before network submission.
 *
 * @since 0.1.0
 * @author Christian Barbati
 */
class PushoverClientValidationTest {
    private static final String TOKEN = "a12345678901234567890123456789";
    private static final String USER = "uQiRzpo4DXghDmr9QzzfQu27cmVRsG";

    /**
     * Verifies mutually exclusive formatting flags are rejected.
     *
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void sendMessage_rejectsHtmlAndMonospaceTogether() {
        PushoverClient client = new PushoverClient(TOKEN);
        MessageRequest request = MessageRequest.builder(USER, "hello")
                .html(true)
                .monospace(true)
                .build();

        assertThrows(PushoverClientException.class, () -> client.sendMessage(request));
    }

    /**
     * Verifies emergency messages require retry and expire values.
     *
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void sendMessage_rejectsEmergencyWithoutRetryAndExpire() {
        PushoverClient client = new PushoverClient(TOKEN);
        MessageRequest request = MessageRequest.builder(USER, "hello")
                .priority(MessagePriority.EMERGENCY)
                .build();

        assertThrows(PushoverClientException.class, () -> client.sendMessage(request));
    }

    /**
     * Verifies TTL is not accepted for emergency priority messages.
     *
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void sendMessage_rejectsTtlForEmergencyPriority() {
        PushoverClient client = new PushoverClient(TOKEN);
        MessageRequest request = MessageRequest.builder(USER, "hello")
                .priority(MessagePriority.EMERGENCY)
                .retry(30)
                .expire(300)
                .ttl(30)
                .build();

        assertThrows(PushoverClientException.class, () -> client.sendMessage(request));
    }
}

