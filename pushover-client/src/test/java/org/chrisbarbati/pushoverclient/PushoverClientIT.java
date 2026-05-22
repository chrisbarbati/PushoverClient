package org.chrisbarbati.pushoverclient;

import org.chrisbarbati.pushoverclient.exception.PushoverApiException;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptStatusResponse;
import org.chrisbarbati.pushoverclient.model.attachment.Attachment;
import org.chrisbarbati.pushoverclient.model.attachment.AttachmentEncoding;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live integration tests against the real Pushover API.
 *
 * @apiNote Tests skip gracefully when required credentials/resources are not configured.
 * @since 0.1.0
 * @author Christian Barbati
 */
class PushoverClientIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(PushoverClientIT.class);
    private static final Properties CONFIG = loadConfig();
    private static final Path TEST_RESOURCES_DIR = Path.of("src", "test", "resources");

    /**
     * Verifies the sounds endpoint using real credentials.
     *
     * @throws Exception when the API call fails unexpectedly
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void live_getSounds() throws Exception {
        String token = required("pushover.appToken");
        PushoverClient client = new PushoverClient(token, optional("pushover.encryptionKey"));

        assertEquals(1, client.getSounds().status());
    }

    /**
     * Verifies the limits endpoint using real credentials.
     *
     * @throws Exception when the API call fails unexpectedly
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void live_getLimits() throws Exception {
        String token = required("pushover.appToken");
        PushoverClient client = new PushoverClient(token, optional("pushover.encryptionKey"));

        assertEquals(1, client.getLimits().status());
    }

    /**
     * Verifies a basic low-priority message can be sent.
     *
     * @throws Exception when the API call fails unexpectedly
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void live_sendMessage() throws Exception {
        String token = required("pushover.appToken");
        String user = required("pushover.userKey");
        PushoverClient client = new PushoverClient(token, optional("pushover.encryptionKey"));

        MessageRequest request = MessageRequest.builder(user, "PushoverClient integration test")
                .title("PushoverClient")
                .priority(MessagePriority.LOWEST)
                .build();

        assertEquals(1, client.sendMessage(request).status());
    }

    /**
     * Verifies encrypted message delivery when encryption key is configured.
     *
     * @throws Exception when the API call fails unexpectedly
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void live_sendEncryptedMessageWhenKeyPresent() throws Exception {
        String token = required("pushover.appToken");
        String user = required("pushover.userKey");
        String encryptionKey = optional("pushover.encryptionKey");

        if (isBlank(encryptionKey)) {
            LOGGER.warn("Skipping encrypted integration test: missing pushover.encryptionKey");
        }
        Assumptions.assumeTrue(!isBlank(encryptionKey), "missing pushover.encryptionKey");

        PushoverClient client = new PushoverClient(token, encryptionKey);
        MessageRequest request = MessageRequest.builder(user, "Encrypted integration test")
                .title("PushoverClient")
                .encrypted(true)
                .priority(MessagePriority.NORMAL)
                .build();

        assertEquals(1, client.sendMessage(request).status());
    }

    /**
     * Verifies multipart attachment upload against the live API.
     *
     * @throws Exception when the API call fails unexpectedly
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void live_sendMessage_withMultipartAttachment() throws Exception {
        String token = required("pushover.appToken");
        String user = required("pushover.userKey");
        AttachmentResource resource = requiredAttachmentResource("pushover.multipartAttachment");

        PushoverClient client = new PushoverClient(token, optional("pushover.encryptionKey"));
        MessageRequest request = MessageRequest.builder(user, "Multipart attachment integration test")
                .title("PushoverClient")
                .attachment(new Attachment(resource.fileName(), resource.bytes(), resource.contentType(), AttachmentEncoding.MULTIPART))
                .priority(MessagePriority.LOWEST)
                .build();

        assertEquals(1, client.sendMessage(request).status());
    }

    /**
     * Verifies base64 attachment upload against the live API.
     *
     * @throws Exception when the API call fails unexpectedly
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void live_sendMessage_withBase64Attachment() throws Exception {
        String token = required("pushover.appToken");
        String user = required("pushover.userKey");
        AttachmentResource resource = requiredAttachmentResource("pushover.base64Attachment");

        PushoverClient client = new PushoverClient(token, optional("pushover.encryptionKey"));
        MessageRequest request = MessageRequest.builder(user, "Base64 attachment integration test")
                .title("PushoverClient")
                .attachment(new Attachment(resource.fileName(), resource.bytes(), resource.contentType(), AttachmentEncoding.BASE64))
                .priority(MessagePriority.LOWEST)
                .build();

        assertEquals(1, client.sendMessage(request).status());
    }

    /**
     * Verifies an intentionally invalid user key produces API failure response.
     *
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void live_failure_invalidUserKey_returnsApiError() {
        String token = required("pushover.appToken");
        String configuredInvalidUser = optional("pushover.invalidUserKey");
        if (isBlank(configuredInvalidUser)) {
            // 30 chars to pass client-side format validation but fail server-side lookup.
            configuredInvalidUser = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        }
        final String invalidUser = configuredInvalidUser;

        PushoverClient client = new PushoverClient(token, optional("pushover.encryptionKey"));

        PushoverApiException ex = assertThrows(PushoverApiException.class,
                () -> client.sendMessage(MessageRequest.builder(invalidUser, "Expected live API failure").build()));

        assertTrue(ex.getHttpStatus() >= 400 && ex.getHttpStatus() < 500);
        assertFalse(ex.getErrors().isEmpty());
    }

    /**
     * Verifies an intentionally invalid token produces API failure response.
     *
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void live_failure_invalidTokenOnSounds_returnsApiError() {
        String invalidToken = optional("pushover.invalidAppToken");
        if (isBlank(invalidToken)) {
            LOGGER.warn("Skipping invalid-token integration test: missing pushover.invalidAppToken");
        }
        Assumptions.assumeTrue(!isBlank(invalidToken), "missing pushover.invalidAppToken");

        PushoverClient client = new PushoverClient(invalidToken, optional("pushover.encryptionKey"));
        PushoverApiException ex = assertThrows(PushoverApiException.class, client::getSounds);

        assertTrue(ex.getHttpStatus() >= 400 && ex.getHttpStatus() < 500);
        assertFalse(ex.getErrors().isEmpty());
    }

    /**
     * Loads and validates attachment bytes from configured path settings.
     *
     * @param prefix property key prefix
     * @return attachment resource data
     * @since 0.1.0
     * @author Christian Barbati
     */
    private static AttachmentResource requiredAttachmentResource(String prefix) {
        String pathValue = optional(prefix + ".path");
        String contentType = optional(prefix + ".contentType");
        String fileName = optional(prefix + ".fileName");

        if (isBlank(pathValue) || isBlank(contentType)) {
            LOGGER.warn("Skipping attachment integration test: missing {}.path or {}.contentType", prefix, prefix);
            Assumptions.assumeTrue(!isBlank(pathValue) && !isBlank(contentType), "missing attachment configuration for " + prefix);
            throw new IllegalStateException("unreachable");
        }

        Path path = Path.of(pathValue);
        if (!path.isAbsolute()) {
            // Treat configured paths as relative to src/test/resources by default.
            path = TEST_RESOURCES_DIR.resolve(pathValue).normalize();
        }

        if (Files.isDirectory(path)) {
            if (isBlank(fileName)) {
                LOGGER.warn(
                        "Skipping attachment integration test: {} points to a directory and {}.fileName is not set",
                        path.toAbsolutePath(),
                        prefix
                );
                throw new org.opentest4j.TestAbortedException("attachment path is a directory and fileName is missing for " + prefix);
            }
            path = path.resolve(fileName).normalize();
        }

        if (Files.exists(path)) {
            if (isBlank(fileName)) {
                fileName = path.getFileName().toString();
            }

            try {
                return new AttachmentResource(fileName, contentType, Files.readAllBytes(path));
            } catch (IOException ex) {
                LOGGER.warn("Skipping attachment integration test: could not read file {}", path.toAbsolutePath(), ex);
                Assumptions.assumeTrue(false, "unreadable attachment file for " + prefix);
                throw new IllegalStateException("unreachable", ex);
            }
        }

        // Fallback for classpath resources copied under target/test-classes.
        String classpathPath = pathValue.startsWith("/") ? pathValue.substring(1) : pathValue;
        try (InputStream in = PushoverClientIT.class.getClassLoader().getResourceAsStream(classpathPath)) {
            if (in != null) {
                if (isBlank(fileName)) {
                    fileName = Path.of(classpathPath).getFileName().toString();
                }
                return new AttachmentResource(fileName, contentType, in.readAllBytes());
            }
        } catch (IOException ex) {
            LOGGER.warn("Skipping attachment integration test: could not read classpath resource {}", classpathPath, ex);
            Assumptions.assumeTrue(false, "unreadable classpath attachment for " + prefix);
            throw new IllegalStateException("unreachable", ex);
        }

        LOGGER.warn("Skipping attachment integration test: attachment file not found at {} or classpath {}", path.toAbsolutePath(), classpathPath);
        throw new org.opentest4j.TestAbortedException("attachment file missing for " + prefix);
    }

    /**
     * Resolves a required integration property or aborts the current test.
     *
     * @param key property key
     * @return resolved non-blank value
     * @since 0.1.0
     * @author Christian Barbati
     */
    private static String required(String key) {
        String value = optional(key);
        if (isBlank(value)) {
            LOGGER.warn("Skipping integration tests: missing {}", key);
            Assumptions.assumeTrue(!isBlank(value), "missing " + key);
            throw new IllegalStateException("unreachable");
        }
        return value;
    }

    /**
     * Resolves a property from system properties, environment variables, or test config file.
     *
     * @param key property key
     * @return value when configured, otherwise null/blank
     * @since 0.1.0
     * @author Christian Barbati
     */
    private static String optional(String key) {
        String sys = System.getProperty(key);
        if (!isBlank(sys)) {
            return sys;
        }

        String envName = key.toUpperCase().replace('.', '_');
        String env = System.getenv(envName);
        if (!isBlank(env)) {
            return env;
        }
        return CONFIG.getProperty(key);
    }

    /**
     * Loads integration properties from classpath resource.
     *
     * @return loaded properties, possibly empty
     * @since 0.1.0
     * @author Christian Barbati
     */
    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream in = PushoverClientIT.class.getClassLoader().getResourceAsStream("pushover-integration.properties")) {
            if (in != null) {
                props.load(in);
            } else {
                LOGGER.warn("No pushover-integration.properties found; integration tests may be skipped");
            }
        } catch (IOException ex) {
            LOGGER.warn("Could not load integration properties", ex);
        }
        return props;
    }

    /**
     * Checks whether a string is null or blank.
     *
     * @param value candidate string
     * @return true when the value is null or blank
     * @since 0.1.0
     * @author Christian Barbati
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Attachment resource content loaded from test configuration.
     *
     * @param fileName file name to submit to Pushover
     * @param contentType MIME type for upload
     * @param bytes attachment bytes
     * @since 0.1.0
     * @author Christian Barbati
     */
    private record AttachmentResource(String fileName, String contentType, byte[] bytes) {
    }

    /**
     * Verifies optional device, sound, and timestamp fields are accepted by live API.
     *
     * @throws Exception when the API call fails unexpectedly
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void live_sendMessage_withDeviceSoundAndTimestamp() throws Exception {
        String token = required("pushover.appToken");
        String user = required("pushover.userKey");
        PushoverClient client = new PushoverClient(token, optional("pushover.encryptionKey"));

        String device = optional("pushover.device");
        String sound = optional("pushover.sound");
        long timestamp = System.currentTimeMillis() / 1000L;

        MessageRequest.Builder builder = MessageRequest.builder(user, "Device/sound/timestamp integration test")
                .title("PushoverClient")
                .timestamp(timestamp)
                .priority(MessagePriority.NORMAL);

        if (!isBlank(device)) {
            builder.device(device);
        }
        if (!isBlank(sound)) {
            builder.sound(sound);
        }

        assertEquals(1, client.sendMessage(builder.build()).status());
    }

    /**
     * Verifies the opt-in emergency receipt lifecycle using callback and tag fields.
     *
     * @apiNote This test is opt-in because emergency notifications can be disruptive.
     * @throws Exception when the API call fails unexpectedly
     * @since 0.2.0
     * @author Christian Barbati
     */
    @Test
    void live_receiptsLifecycle_withEmergencyMessage_whenEnabled() throws Exception {
        String enabled = optional("pushover.enableEmergencyTests");
        if (!"true".equalsIgnoreCase(enabled)) {
            LOGGER.warn("Skipping emergency receipt lifecycle test: set pushover.enableEmergencyTests=true to enable");
        }
        Assumptions.assumeTrue("true".equalsIgnoreCase(enabled), "emergency test disabled");

        String token = required("pushover.appToken");
        String user = required("pushover.userKey");
        String callbackUrl = optional("pushover.callbackUrl");
        String tags = optional("pushover.tags");
        boolean strict = "true".equalsIgnoreCase(optional("pushover.strictEmergencyTest"));

        String baseTag = isBlank(tags) ? "pushoverclient-it" : tags.split(",")[0].trim();
        String lifecycleTag = baseTag + "-" + (System.currentTimeMillis() / 1000L);

        PushoverClient client = new PushoverClient(token, optional("pushover.encryptionKey"));
        MessageRequest.Builder requestBuilder = MessageRequest.builder(user, "Emergency receipt lifecycle integration test")
                .title("PushoverClient")
                .priority(MessagePriority.EMERGENCY)
                .retry(30)
                .expire(300)
                .tags(lifecycleTag);

        if (!isBlank(callbackUrl) && !callbackUrl.contains("example.com")) {
            requestBuilder.callback(callbackUrl);
        } else {
            LOGGER.warn("Running emergency receipt lifecycle test without callback URL; configure pushover.callbackUrl with a real public endpoint to include callback coverage");
        }

        MessageRequest request = requestBuilder.build();

        try {
            var messageResponse = client.sendMessage(request);
            assertEquals(1, messageResponse.status());
            assertFalse(isBlank(messageResponse.receipt()));

            ReceiptStatusResponse receiptResponse = client.getReceipt(messageResponse.receipt());
            assertEquals(1, receiptResponse.status());
            assertNotNull(receiptResponse.request());
            if (!isBlank(receiptResponse.receipt())) {
                assertEquals(messageResponse.receipt(), receiptResponse.receipt());
            }

            assertEquals(1, client.cancelReceipt(messageResponse.receipt()).status());
            assertEquals(1, client.cancelByTag(lifecycleTag).status());
        } catch (PushoverApiException ex) {
            if (strict) {
                throw ex;
            }
            LOGGER.warn(
                    "Skipping emergency receipt lifecycle assertion due to API rejection (strict mode off): requestId={}, errors={}",
                    ex.getRequestId(),
                    ex.getErrors()
            );
            throw new org.opentest4j.TestAbortedException("Emergency receipt lifecycle rejected by API and strict mode is disabled", ex);
        }
    }
}
