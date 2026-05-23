package org.chrisbarbati.pushoverclient;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptStatusResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Async receipt tests covering polling and callback-based flows.
 *
 * @since 0.3.0
 * @author Christian Barbati
 */
class PushoverClientAsyncTest {
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

    /**
     * Verifies the polling async flow completes when the receipt becomes acknowledged.
     *
     * @throws Exception when the test fails unexpectedly
     * @since 0.3.0
     * @author Christian Barbati
     */
    @Test
    void sendEmergencyMessageAndAwaitAcknowledgement_completesWhenReceiptIsAcknowledged() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-send\",\"receipt\":\"" + RECEIPT + "\"}"));
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-r1\",\"receipt\":\"" + RECEIPT
                        + "\",\"acknowledged\":0,\"expired\":0,\"called_back\":0}"));
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-r2\",\"receipt\":\"" + RECEIPT
                        + "\",\"acknowledged\":1,\"acknowledged_at\":1715800000,\"expired\":0,\"called_back\":0}"));

        AsyncReceiptProperties properties = AsyncReceiptProperties.builder()
                .pollInterval(Duration.ofMillis(10))
                .pollFutureExpiration(Duration.ofSeconds(1))
                .build();
        PushoverClient client = new PushoverClient(
                TOKEN,
                null,
                HttpClient.newHttpClient(),
                server.url("/").toString().replaceAll("/$", ""),
                properties,
                null
        );
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            var future = client.sendEmergencyMessageAndAwaitAcknowledgement(emergencyRequest(), executorService);
            ReceiptStatusResponse response = future.get(2, TimeUnit.SECONDS);

            assertEquals(1, response.acknowledged());
            assertEquals(RECEIPT, response.receipt());

            RecordedRequest sendRequest = server.takeRequest();
            RecordedRequest firstReceiptRequest = server.takeRequest();
            RecordedRequest secondReceiptRequest = server.takeRequest();
            assertEquals("/1/messages.json", sendRequest.getPath());
            assertEquals("/1/receipts/" + RECEIPT + ".json?token=" + TOKEN, firstReceiptRequest.getPath());
            assertEquals("/1/receipts/" + RECEIPT + ".json?token=" + TOKEN, secondReceiptRequest.getPath());
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * Verifies a per-call expiration override can terminate the polling async flow.
     *
     * @since 0.3.0
     * @author Christian Barbati
     */
    @Test
    void sendEmergencyMessageAndAwaitAcknowledgement_timesOutWhenOverrideExpires() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-send\",\"receipt\":\"" + RECEIPT + "\"}"));
        for (int i = 0; i < 6; i++) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setBody("{\"status\":1,\"request\":\"req-r\",\"receipt\":\"" + RECEIPT
                            + "\",\"acknowledged\":0,\"expired\":0,\"called_back\":0}"));
        }

        AsyncReceiptProperties properties = AsyncReceiptProperties.builder()
                .pollInterval(Duration.ofMillis(10))
                .pollFutureExpiration(Duration.ofSeconds(5))
                .build();
        PushoverClient client = new PushoverClient(
                TOKEN,
                null,
                HttpClient.newHttpClient(),
                server.url("/").toString().replaceAll("/$", ""),
                properties,
                null
        );
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            var future = client.sendEmergencyMessageAndAwaitAcknowledgement(
                    emergencyRequest(),
                    executorService,
                    Duration.ofMillis(35)
            );

            ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, ex.getCause());
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * Verifies cancelReceipt removes and cancels a pending polling future.
     *
     * @throws Exception when the test fails unexpectedly
     * @since 0.3.0
     * @author Christian Barbati
     */
    @Test
    void cancelReceipt_cancelsPendingPollingFuture() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-send\",\"receipt\":\"" + RECEIPT + "\"}"));
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-r1\",\"receipt\":\"" + RECEIPT
                        + "\",\"acknowledged\":0,\"expired\":0,\"called_back\":0}"));

        AsyncReceiptProperties properties = AsyncReceiptProperties.builder()
                .pollInterval(Duration.ofMillis(200))
                .pollFutureExpiration(Duration.ofSeconds(5))
                .build();
        PushoverClient client = new PushoverClient(
                TOKEN,
                null,
                HttpClient.newHttpClient(),
                server.url("/").toString().replaceAll("/$", ""),
                properties,
                null
        );
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            var future = client.sendEmergencyMessageAndAwaitAcknowledgement(emergencyRequest(), executorService);
            server.takeRequest();
            server.takeRequest();

            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-cancel\"}"));
            client.cancelReceipt(RECEIPT);

            assertThrows(CancellationException.class, future::join);
            RecordedRequest cancelRequest = server.takeRequest();
            assertEquals("/1/receipts/" + RECEIPT + "/cancel.json", cancelRequest.getPath());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void sendEmergencyMessageAndAwaitCallback_completesWithHostManagedServlet() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-send\",\"receipt\":\"" + RECEIPT + "\"}"));

        AsyncReceiptProperties properties = AsyncReceiptProperties.builder()
                .pollInterval(Duration.ofMillis(10))
                .callbackFutureExpiration(Duration.ofSeconds(2))
                .defaultCallbackUrl("https://callbacks.example.test/pushover/callback")
                .build();
        PushoverClient client = new PushoverClient(
                TOKEN,
                null,
                HttpClient.newHttpClient(),
                server.url("/").toString().replaceAll("/$", ""),
                properties,
                null
        );
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            var future = client.sendEmergencyMessageAndAwaitCallback(emergencyRequest(), executorService);
            RecordedRequest sendRequest = server.takeRequest();
            assertNotNull(sendRequest);
            assertTrue(sendRequest.getBody().readUtf8().contains("callback=https%3A%2F%2Fcallbacks.example.test%2Fpushover%2Fcallback"));
            awaitPendingFutureCount(client);

            client.acceptCallbackReceipt(
                    ReceiptCallbackResponse.fromFields(Map.of(
                            "receipt", RECEIPT,
                            "acknowledged", "1",
                            "acknowledged_by", "host-managed"
                    ))
            );

            ReceiptCallbackResponse callbackResponse = future.get(2, TimeUnit.SECONDS);
            assertEquals(RECEIPT, callbackResponse.receipt());
            assertEquals("host-managed", callbackResponse.acknowledgedBy());
        } finally {
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

    private static void awaitPendingFutureCount(PushoverClient client) throws Exception {
        Field field = PushoverClient.class.getDeclaredField("pendingCallbackFutures");
        field.setAccessible(true);

        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            Object value = field.get(client);
            if (value instanceof Map<?, ?> map && map.size() == 1) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for pendingCallbackFutures to reach size 1");
    }
}




