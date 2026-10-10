package dev.chrisbarbati.pushoverclient;

import dev.chrisbarbati.pushoverclient.exception.PushoverClientException;
import dev.chrisbarbati.pushoverclient.model.glance.GlanceRequest;
import dev.chrisbarbati.pushoverclient.model.message.MessagePriority;
import dev.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
     * Verifies encrypted message submission fails fast when the client has no encryption key configured.
     *
     * @since 0.5.0
     */
    @Test
    void sendMessage_rejectsEncryptedMessageWithoutConfiguredKey() {
        PushoverClient client = new PushoverClient(TOKEN);
        MessageRequest request = MessageRequest.builder(USER, "hello")
                .encrypted(true)
                .build();

        assertThrows(PushoverClientException.class, () -> client.sendMessage(request));
    }

    /**
     * Verifies blank user/group identifiers are rejected before validation network calls.
     *
     * @since 0.6.0
     */
    @Test
    void validateUserOrGroup_rejectsBlankIdentifier() {
        PushoverClient client = new PushoverClient(TOKEN);

        assertThrows(PushoverClientException.class, () -> client.validateUserOrGroup("   "));
    }

    /**
     * Verifies malformed user/group identifiers are rejected before validation network calls.
     *
     * @since 0.6.0
     */
    @Test
    void validateUserOrGroup_rejectsMalformedIdentifier() {
        PushoverClient client = new PushoverClient(TOKEN);

        assertThrows(PushoverClientException.class, () -> client.validateUserOrGroup("too-short"));
    }

    /**
     * Verifies validation accepts only a single well-formed device name.
     *
     * @since 0.6.0
     */
    @Test
    void validateUserOrGroup_rejectsInvalidDevice() {
        PushoverClient client = new PushoverClient(TOKEN);

        assertThrows(PushoverClientException.class, () -> client.validateUserOrGroup(USER, "   "));
        assertThrows(PushoverClientException.class, () -> client.validateUserOrGroup(USER, "phone-1,tablet"));
    }

    /**
     * Verifies blank receipt ids are rejected before receipt lookup network calls.
     *
     * @since 0.2.0
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
     * Verifies the callback async flow fails when neither a callback URL nor a callback server is configured.
     *
     * @since 0.3.0
     */
    @Test
    void sendEmergencyMessageAndAwaitCallback_requiresCallbackConfiguration() {
        PushoverClient client = new PushoverClient(TOKEN);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            MessageRequest request = MessageRequest.builder(USER, "hello")
                    .priority(MessagePriority.EMERGENCY)
                    .retry(30)
                    .expire(300)
                    .build();

            ExecutionException ex = assertThrows(
                    ExecutionException.class,
                    () -> client.sendEmergencyMessageAndAwaitCallback(request, executorService).get()
            );
            assertInstanceOf(PushoverClientException.class, ex.getCause());
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * Verifies polling async flows reject non-positive expiration overrides before background work starts.
     *
     * @since 0.5.0
     */
    @Test
    void sendEmergencyMessageAndAwaitAcknowledgement_rejectsNonPositiveOverrideExpiration() {
        PushoverClient client = new PushoverClient(TOKEN);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            assertThrows(
                    PushoverClientException.class,
                    () -> client.sendEmergencyMessageAndAwaitAcknowledgement(
                            emergencyRequest(),
                            executorService,
                            java.time.Duration.ZERO
                    )
            );
            assertThrows(
                    PushoverClientException.class,
                    () -> client.sendEmergencyMessageAndAwaitAcknowledgement(
                            emergencyRequest(),
                            executorService,
                            java.time.Duration.ofSeconds(-1)
                    )
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * Verifies callback async flows reject non-positive expiration overrides before background work starts.
     *
     * @since 0.5.0
     */
    @Test
    void sendEmergencyMessageAndAwaitCallback_rejectsNonPositiveOverrideExpiration() {
        PushoverClient client = new PushoverClient(TOKEN);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            assertThrows(
                    PushoverClientException.class,
                    () -> client.sendEmergencyMessageAndAwaitCallback(
                            emergencyRequest(),
                            executorService,
                            java.time.Duration.ZERO
                    )
            );
            assertThrows(
                    PushoverClientException.class,
                    () -> client.sendEmergencyMessageAndAwaitCallback(
                            emergencyRequest(),
                            executorService,
                            java.time.Duration.ofSeconds(-1)
                    )
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    private static MessageRequest emergencyRequest() {
        return MessageRequest.builder(USER, "hello")
                .priority(MessagePriority.EMERGENCY)
                .retry(30)
                .expire(300)
                .build();
    }

    /**
     * Verifies a glance update requires at least one data field.
     *
     * @since 0.9.0
     */
    @Test
    void sendGlance_rejectsMissingDataFields() {
        PushoverClient client = new PushoverClient(TOKEN);

        assertThrows(
                PushoverClientException.class,
                () -> client.sendGlance(GlanceRequest.builder(USER).build())
        );
    }

    /**
     * Verifies a glance update rejects an invalid user key.
     *
     * @since 0.9.0
     */
    @Test
    void sendGlance_rejectsInvalidUser() {
        PushoverClient client = new PushoverClient(TOKEN);

        assertThrows(
                PushoverClientException.class,
                () -> client.sendGlance(GlanceRequest.builder("short").text("x").build())
        );
    }

    /**
     * Verifies a glance update rejects a device name outside the documented character set.
     *
     * @since 0.9.0
     */
    @Test
    void sendGlance_rejectsInvalidDevice() {
        PushoverClient client = new PushoverClient(TOKEN);

        assertThrows(
                PushoverClientException.class,
                () -> client.sendGlance(GlanceRequest.builder(USER).device("bad device name!").text("x").build())
        );
    }

    /**
     * Verifies a glance update rejects title, text, and subtext over 100 characters.
     *
     * @since 0.9.0
     */
    @Test
    void sendGlance_rejectsOversizedTextFields() {
        PushoverClient client = new PushoverClient(TOKEN);
        String longValue = "x".repeat(101);

        assertThrows(
                PushoverClientException.class,
                () -> client.sendGlance(GlanceRequest.builder(USER).title(longValue).build())
        );
        assertThrows(
                PushoverClientException.class,
                () -> client.sendGlance(GlanceRequest.builder(USER).text(longValue).build())
        );
        assertThrows(
                PushoverClientException.class,
                () -> client.sendGlance(GlanceRequest.builder(USER).subtext(longValue).count(1).build())
        );
    }

    /**
     * Verifies a glance update rejects a percent value outside 0..100.
     *
     * @since 0.9.0
     */
    @Test
    void sendGlance_rejectsPercentOutOfRange() {
        PushoverClient client = new PushoverClient(TOKEN);

        assertThrows(
                PushoverClientException.class,
                () -> client.sendGlance(GlanceRequest.builder(USER).percent(-1).build())
        );
        assertThrows(
                PushoverClientException.class,
                () -> client.sendGlance(GlanceRequest.builder(USER).percent(101).build())
        );
    }
}


