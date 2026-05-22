package org.chrisbarbati.pushoverclient;

import org.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import org.chrisbarbati.pushoverclient.callback.JettyCallbackServer;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptStatusResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Live integration tests for async emergency receipt flows that require user acknowledgement.
 *
 * @apiNote These tests are opt-in and intended for interactive runs because they require the user to acknowledge the delivered notification.
 * @since 0.3.0
 * @author Christian Barbati
 */
class PushoverClientAsyncIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(PushoverClientAsyncIT.class);
    private static final Properties CONFIG = loadConfig();
    private static final Duration USER_ACK_TIMEOUT = Duration.ofMinutes(1);

    /**
     * Verifies the polling-based async emergency flow completes after the user acknowledges the notification.
     *
     * @throws Exception when the API call or async wait fails unexpectedly
     * @since 0.3.0
     * @author Christian Barbati
     */
    @Test
    void live_asyncPollingAcknowledgement_whenEnabled() throws Exception {
        requireAsyncEmergencyTestsEnabled();

        String token = required("pushover.appToken");
        String user = required("pushover.userKey");
        String encryptionKey = optional("pushover.encryptionKey");
        String tag = uniqueTag("pushoverclient-async-poll");

        AsyncReceiptProperties asyncProperties = AsyncReceiptProperties.builder()
                .pollInterval(Duration.ofSeconds(5))
                .pollFutureExpiration(USER_ACK_TIMEOUT)
                .build();
        PushoverClient client = new PushoverClient(
                token,
                encryptionKey,
                HttpClient.newHttpClient(),
                "https://api.pushover.net",
                asyncProperties,
                null
        );

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            MessageRequest request = MessageRequest.builder(
                            user,
                            "[Async Polling IT] Please acknowledge this notification within 1 minute so the polling async integration test can pass."
                    )
                    .title("PushoverClient Async Polling IT")
                    .priority(MessagePriority.EMERGENCY)
                    .retry(30)
                    .expire(300)
                    .tags(tag)
                    .build();

            ReceiptStatusResponse response = client.sendEmergencyMessageAndAwaitAcknowledgement(
                    request,
                    executorService,
                    USER_ACK_TIMEOUT
            ).get(USER_ACK_TIMEOUT.toSeconds() + 10, TimeUnit.SECONDS);

            assertEquals(1, response.status());
            assertEquals(1, response.acknowledged());
            System.out.printf(
                    "SUCCESS: Async polling IT acknowledged by %s on device %s for request %s%n",
                    valueOrPlaceholder(response.acknowledgedBy()),
                    valueOrPlaceholder(response.acknowledgedByDevice()),
                    valueOrPlaceholder(response.request())
            );
        } finally {
            cancelByTagQuietly(client, tag);
            executorService.shutdownNow();
        }
    }

    /**
     * Verifies the Jetty callback-based async emergency flow completes after the user acknowledges the notification.
     *
     * @throws Exception when the API call, callback server setup, or async wait fails unexpectedly
     * @since 0.3.0
     * @author Christian Barbati
     */
    @Test
    void live_asyncJettyCallbackAcknowledgement_whenEnabled() throws Exception {
        requireAsyncEmergencyTestsEnabled();

        String token = required("pushover.appToken");
        String user = required("pushover.userKey");
        String encryptionKey = optional("pushover.encryptionKey");
        URI callbackBindUri = requiredUri("pushover.asyncCallbackBindUri");
        URI callbackPublicUri = requiredUri("pushover.asyncCallbackUrl");
        validateJettyCallbackReachability(callbackBindUri, callbackPublicUri);
        String tag = uniqueTag("pushoverclient-async-callback");

        ThreadPoolExecutor executorService = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        JettyCallbackServer callbackServer = new JettyCallbackServer(callbackBindUri, executorService);
        AsyncReceiptProperties asyncProperties = AsyncReceiptProperties.builder()
                .defaultCallbackUrl(callbackPublicUri.toString())
                .pollInterval(Duration.ofSeconds(2))
                .callbackFutureExpiration(USER_ACK_TIMEOUT)
                .build();
        PushoverClient client = new PushoverClient(
                token,
                encryptionKey,
                HttpClient.newHttpClient(),
                "https://api.pushover.net",
                asyncProperties,
                callbackServer
        );

        try {
            System.out.printf(
                    "Waiting for Jetty callback acknowledgement. Local bind=%s, public callback=%s%n",
                    callbackBindUri,
                    callbackPublicUri
            );
            MessageRequest request = MessageRequest.builder(
                            user,
                            "[Async Jetty Callback IT] Please acknowledge this notification within 1 minute so the Jetty callback async integration test can pass."
                    )
                    .title("PushoverClient Async Jetty Callback IT")
                    .priority(MessagePriority.EMERGENCY)
                    .retry(30)
                    .expire(300)
                    .tags(tag)
                    .build();

            ReceiptCallbackResponse response = client.sendEmergencyMessageAndAwaitCallback(
                    request,
                    executorService,
                    USER_ACK_TIMEOUT
            ).get(USER_ACK_TIMEOUT.toSeconds() + 10, TimeUnit.SECONDS);

            assertNotNull(response);
            assertFalse(isBlank(response.receipt()));
            assertEquals(1, response.acknowledged());
            System.out.printf(
                    "SUCCESS: Async Jetty callback IT acknowledged by %s on device %s for receipt %s%n",
                    valueOrPlaceholder(response.acknowledgedBy()),
                    valueOrPlaceholder(response.acknowledgedByDevice()),
                    response.receipt()
            );
        } finally {
            cancelByTagQuietly(client, tag);
            callbackServer.stop();
            executorService.shutdownNow();
        }
    }

    private static void requireAsyncEmergencyTestsEnabled() {
        String enabled = optional("pushover.enableAsyncEmergencyTests");
        if (!"true".equalsIgnoreCase(enabled)) {
            LOGGER.warn("Skipping async emergency integration tests: set pushover.enableAsyncEmergencyTests=true to enable interactive acknowledgement tests");
        }
        Assumptions.assumeTrue("true".equalsIgnoreCase(enabled), "async emergency tests disabled");
    }

    private static void cancelByTagQuietly(PushoverClient client, String tag) {
        try {
            client.cancelByTag(tag);
        } catch (Exception ex) {
            LOGGER.warn("Could not cancel async emergency receipt by tag {} during cleanup", tag, ex);
        }
    }

    private static String uniqueTag(String prefix) {
        return prefix + "-" + (System.currentTimeMillis() / 1000L);
    }

    private static String valueOrPlaceholder(String value) {
        return isBlank(value) ? "<unknown>" : value;
    }

    private static URI requiredUri(String key) {
        String value = required(key);
        try {
            return new URI(value.trim());
        } catch (URISyntaxException ex) {
            throw new org.opentest4j.TestAbortedException("invalid URI for " + key + ": " + value, ex);
        }
    }

    private static void validateJettyCallbackReachability(URI bindUri, URI publicUri) {
        String bindHost = bindUri.getHost();
        String publicHost = publicUri.getHost();

        boolean bindLoopback = isLoopbackHost(bindHost);
        boolean publicLoopback = isLoopbackHost(publicHost);

        if (bindLoopback && !publicLoopback) {
            throw new org.opentest4j.TestAbortedException(
                    "Jetty callback server is bound to loopback address " + bindUri
                            + " but the public callback URL is " + publicUri
                            + ". Incoming internet traffic cannot reach a server bound only to 127.0.0.1/localhost. "
                            + "Use 0.0.0.0 or the machine's LAN IP for pushover.asyncCallbackBindUri when relying on port forwarding."
            );
        }
    }

    private static boolean isLoopbackHost(String host) {
        return host != null && (
                "localhost".equalsIgnoreCase(host)
                        || "127.0.0.1".equals(host)
                        || "::1".equals(host)
        );
    }

    /**
     * Resolves a required integration property or aborts the current test.
     *
     * @param key property key
     * @return resolved non-blank value
     */
    private static String required(String key) {
        String value = optional(key);
        if (isBlank(value)) {
            LOGGER.warn("Skipping async integration tests: missing {}", key);
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
     */
    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream in = PushoverClientAsyncIT.class.getClassLoader().getResourceAsStream("pushover-integration.properties")) {
            if (in != null) {
                props.load(in);
            } else {
                LOGGER.warn("No pushover-integration.properties found; async integration tests may be skipped");
            }
        } catch (IOException ex) {
            LOGGER.warn("Could not load async integration properties", ex);
        }
        return props;
    }

    /**
     * Checks whether a string is null or blank.
     *
     * @param value candidate string
     * @return true when the value is null or blank
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

