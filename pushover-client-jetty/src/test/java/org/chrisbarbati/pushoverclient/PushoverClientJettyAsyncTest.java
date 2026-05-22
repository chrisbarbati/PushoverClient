package org.chrisbarbati.pushoverclient;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import org.chrisbarbati.pushoverclient.callback.jetty.JettyCallbackServer;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jetty-specific async callback tests for the optional callback-server module.
 *
 * @since 0.3.0
 * @author Christian Barbati
 */
class PushoverClientJettyAsyncTest {
    private static final String TOKEN = "a12345678901234567890123456789";
    private static final String USER = "uQiRzpo4DXghDmr9QzzfQu27cmVRsG";
    private static final String RECEIPT = "abcdefghijklmnopqrstuvwx123456";

    private MockWebServer server;

    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void teardown() throws IOException {
        server.shutdown();
    }

    @Test
    void sendEmergencyMessageAndAwaitCallback_completesWhenJettyReceivesCallback() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-send\",\"receipt\":\"" + RECEIPT + "\"}"));

        AsyncReceiptProperties properties = AsyncReceiptProperties.builder()
                .pollInterval(Duration.ofMillis(10))
                .callbackFutureExpiration(Duration.ofSeconds(2))
                .build();
        ExecutorService executorService = Executors.newCachedThreadPool();
        JettyCallbackServer callbackServer = new JettyCallbackServer(
                URI.create("http://127.0.0.1:0/pushover/callback"),
                executorService
        );
        PushoverClient client = new PushoverClient(
                TOKEN,
                null,
                HttpClient.newHttpClient(),
                server.url("/").toString().replaceAll("/$", ""),
                properties,
                callbackServer
        );

        try {
            var future = client.sendEmergencyMessageAndAwaitCallback(emergencyRequest(), executorService);
            RecordedRequest sendRequest = server.takeRequest();
            assertTrue(sendRequest.getBody().readUtf8().contains("callback=" + encode(callbackServer.callbackUri().toString())));

            postCallback(callbackServer.callbackUri(),
                    "receipt=" + RECEIPT + "&acknowledged=1&acknowledged_at=1715800000&acknowledged_by=chris");

            ReceiptCallbackResponse response = future.get(2, TimeUnit.SECONDS);
            assertEquals(RECEIPT, response.receipt());
            assertEquals(1, response.acknowledged());
            assertEquals("chris", response.acknowledgedBy());
        } finally {
            callbackServer.stop();
            executorService.shutdownNow();
        }
    }

    @Test
    void sendEmergencyMessageAndAwaitCallback_usesRequestCallbackWhenItOverridesDefault() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-send\",\"receipt\":\"" + RECEIPT + "\"}"));

        int port = findFreePort();
        URI localCallbackUri = URI.create("http://127.0.0.1:" + port + "/pushover/callback");
        AsyncReceiptProperties properties = AsyncReceiptProperties.builder()
                .pollInterval(Duration.ofMillis(10))
                .callbackFutureExpiration(Duration.ofSeconds(2))
                .defaultCallbackUrl("https://default.example/pushover/callback")
                .build();
        ExecutorService executorService = Executors.newCachedThreadPool();
        JettyCallbackServer callbackServer = new JettyCallbackServer(localCallbackUri, executorService);
        PushoverClient client = new PushoverClient(
                TOKEN,
                null,
                HttpClient.newHttpClient(),
                server.url("/").toString().replaceAll("/$", ""),
                properties,
                callbackServer
        );

        try {
            MessageRequest request = emergencyRequest().toBuilder()
                    .callback(localCallbackUri.toString())
                    .build();
            var future = client.sendEmergencyMessageAndAwaitCallback(request, executorService);

            RecordedRequest sendRequest = server.takeRequest();
            String body = sendRequest.getBody().readUtf8();
            assertTrue(body.contains("callback=" + encode(localCallbackUri.toString())));
            assertFalse(body.contains(encode("https://default.example/pushover/callback")));

            postCallback(localCallbackUri, "receipt=" + RECEIPT + "&acknowledged=1&acknowledged_by=override-user");
            ReceiptCallbackResponse response = future.get(2, TimeUnit.SECONDS);
            assertEquals("override-user", response.acknowledgedBy());
        } finally {
            callbackServer.stop();
            executorService.shutdownNow();
        }
    }

    @Test
    void sendEmergencyMessageAndAwaitCallback_timesOutWhenOverrideExpires() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-send\",\"receipt\":\"" + RECEIPT + "\"}"));

        AsyncReceiptProperties properties = AsyncReceiptProperties.builder()
                .pollInterval(Duration.ofMillis(10))
                .callbackFutureExpiration(Duration.ofSeconds(5))
                .build();
        ExecutorService executorService = Executors.newCachedThreadPool();
        JettyCallbackServer callbackServer = new JettyCallbackServer(
                URI.create("http://127.0.0.1:0/pushover/callback"),
                executorService
        );
        PushoverClient client = new PushoverClient(
                TOKEN,
                null,
                HttpClient.newHttpClient(),
                server.url("/").toString().replaceAll("/$", ""),
                properties,
                callbackServer
        );

        try {
            var future = client.sendEmergencyMessageAndAwaitCallback(
                    emergencyRequest(),
                    executorService,
                    Duration.ofMillis(50)
            );
            RecordedRequest sendRequest = server.takeRequest();
            assertNotNull(sendRequest);

            ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, ex.getCause());
        } finally {
            callbackServer.stop();
            executorService.shutdownNow();
        }
    }

    @Test
    void cancelByTag_cancelsPendingCallbackFuture() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-send\",\"receipt\":\"" + RECEIPT + "\"}"));

        AsyncReceiptProperties properties = AsyncReceiptProperties.builder()
                .pollInterval(Duration.ofMillis(10))
                .callbackFutureExpiration(Duration.ofSeconds(5))
                .build();
        ExecutorService executorService = Executors.newCachedThreadPool();
        JettyCallbackServer callbackServer = new JettyCallbackServer(
                URI.create("http://127.0.0.1:0/pushover/callback"),
                executorService
        );
        PushoverClient client = new PushoverClient(
                TOKEN,
                null,
                HttpClient.newHttpClient(),
                server.url("/").toString().replaceAll("/$", ""),
                properties,
                callbackServer
        );

        try {
            MessageRequest request = emergencyRequest().toBuilder().tags("team-blue,ops").build();
            var future = client.sendEmergencyMessageAndAwaitCallback(request, executorService);
            server.takeRequest();
            awaitPendingFutureCount(client, "pendingCallbackFutures", 1);

            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-tag\"}"));
            client.cancelByTag("team-blue");

            assertThrows(CancellationException.class, future::join);
            RecordedRequest cancelRequest = server.takeRequest();
            assertEquals("/1/receipts/cancel_by_tag/team-blue.json", cancelRequest.getPath());
        } finally {
            callbackServer.stop();
            executorService.shutdownNow();
        }
    }

    private static MessageRequest emergencyRequest() {
        return MessageRequest.builder(USER, "Emergency async test")
                .priority(MessagePriority.EMERGENCY)
                .retry(30)
                .expire(300)
                .build();
    }

    private static void postCallback(URI callbackUri, String formBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(callbackUri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void awaitPendingFutureCount(PushoverClient client, String fieldName, int expectedCount) throws Exception {
        Field field = PushoverClient.class.getDeclaredField(fieldName);
        field.setAccessible(true);

        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            Object value = field.get(client);
            if (value instanceof Map<?, ?> map && map.size() == expectedCount) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for " + fieldName + " to reach size " + expectedCount);
    }
}

