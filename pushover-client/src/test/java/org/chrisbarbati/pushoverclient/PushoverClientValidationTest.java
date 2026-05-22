package org.chrisbarbati.pushoverclient;

import org.chrisbarbati.pushoverclient.exception.PushoverClientException;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final String INVALID_RECEIPT = "too-short";

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

    /**
     * Verifies blank receipt ids are rejected before receipt lookup network calls.
     *
     * @since 0.2.0
     * @author Christian Barbati
     */
    @Test
    void getReceipt_rejectsBlankReceiptId() {
        PushoverClient client = new PushoverClient(TOKEN);

        assertThrows(PushoverClientException.class, () -> client.getReceipt("   "));
    }

    /**
     * Verifies malformed receipt ids are rejected before cancellation network calls.
     *
     * @since 0.2.0
     * @author Christian Barbati
     */
    @Test
    void cancelReceipt_rejectsMalformedReceiptId() {
        PushoverClient client = new PushoverClient(TOKEN);

        assertThrows(PushoverClientException.class, () -> client.cancelReceipt(INVALID_RECEIPT));
    }

    /**
     * Verifies blank tags are rejected before cancel-by-tag network calls.
     *
     * @since 0.2.0
     * @author Christian Barbati
     */
    @Test
    void cancelByTag_rejectsBlankTag() {
        PushoverClient client = new PushoverClient(TOKEN);

        assertThrows(PushoverClientException.class, () -> client.cancelByTag("   "));
    }

    /**
     * Verifies the polling async flow rejects non-emergency messages before background execution.
     *
     * @since 0.3.0
     * @author Christian Barbati
     */
    @Test
    void sendEmergencyMessageAndAwaitAcknowledgement_rejectsNonEmergencyMessages() {
        PushoverClient client = new PushoverClient(TOKEN);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            MessageRequest request = MessageRequest.builder(USER, "hello")
                    .priority(MessagePriority.NORMAL)
                    .build();

            assertThrows(
                    PushoverClientException.class,
                    () -> client.sendEmergencyMessageAndAwaitAcknowledgement(request, executorService)
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * Verifies the callback async flow fails fast when no callback server is configured.
     *
     * @since 0.3.0
     * @author Christian Barbati
     */
    @Test
    void sendEmergencyMessageAndAwaitCallback_requiresCallbackServer() {
        PushoverClient client = new PushoverClient(TOKEN);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            MessageRequest request = MessageRequest.builder(USER, "hello")
                    .priority(MessagePriority.EMERGENCY)
                    .retry(30)
                    .expire(300)
                    .build();

            assertThrows(
                    PushoverClientException.class,
                    () -> client.sendEmergencyMessageAndAwaitCallback(request, executorService)
            );
        } finally {
            executorService.shutdownNow();
        }
    }
}

