package org.chrisbarbati.pushoverclient.callback.jetty;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Jetty host-managed callback handlers.
 *
 * @since 0.4.0
 * @author Christian Barbati
 */
class JettyCallbackHandlersTest {
    private static final String TOKEN = "a12345678901234567890123456789";
    private static final String USER = "uQiRzpo4DXghDmr9QzzfQu27cmVRsG";
    private static final String RECEIPT = "abcdefghijklmnopqrstuvwx123456";

    private MockWebServer apiServer;

    @BeforeEach
    void setup() throws IOException {
        apiServer = new MockWebServer();
        apiServer.start();
    }

    @AfterEach
    void teardown() throws IOException {
        apiServer.shutdown();
    }

    @Test
    void create_dispatchesCallbacksWithoutInterferingWithOtherRoutes() throws Exception {
        AtomicReference<ReceiptCallbackResponse> delivered = new AtomicReference<>();
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);

        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(JettyCallbackHandlers.create("/pushover/callback", delivered::set));
        handlers.addHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
                if (!"/health".equals(target)) {
                    return;
                }
                baseRequest.setHandled(true);
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("healthy");
            }
        });
        server.setHandler(handlers);
        server.start();

        try {
            URI callbackUri = URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/pushover/callback");
            HttpResponse<String> callbackResponse = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(callbackUri)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "receipt=abcdefghijklmnopqrstuvwx123456&acknowledged=1&acknowledged_by=host-app",
                                    StandardCharsets.UTF_8
                            ))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            HttpResponse<String> healthResponse = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/health"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            assertEquals(200, callbackResponse.statusCode());
            assertEquals("OK", callbackResponse.body());
            assertNotNull(delivered.get());
            assertEquals("abcdefghijklmnopqrstuvwx123456", delivered.get().receipt());
            assertEquals("host-app", delivered.get().acknowledgedBy());
            assertEquals(200, healthResponse.statusCode());
            assertEquals("healthy", healthResponse.body());
        } finally {
            server.stop();
        }
    }

    @Test
    void create_withPushoverClientCompletesPendingFuture() throws Exception {
        apiServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-send\",\"receipt\":\"" + RECEIPT + "\"}"));

        Server callbackHost = new Server();
        ServerConnector connector = new ServerConnector(callbackHost);
        callbackHost.addConnector(connector);

        AsyncReceiptProperties properties = AsyncReceiptProperties.builder()
                .pollInterval(Duration.ofMillis(10))
                .callbackFutureExpiration(Duration.ofSeconds(2))
                .defaultCallbackUrl("https://callbacks.example.test/pushover/callback")
                .build();
        PushoverClient client = new PushoverClient(
                TOKEN,
                null,
                HttpClient.newHttpClient(),
                apiServer.url("/").toString().replaceAll("/$", ""),
                properties,
                null
        );
        callbackHost.setHandler(JettyCallbackHandlers.create("/pushover/callback", client));
        callbackHost.start();
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            var future = client.sendEmergencyMessageAndAwaitCallback(emergencyRequest(), executorService);
            RecordedRequest sendRequest = apiServer.takeRequest();
            assertEquals("/1/messages.json", sendRequest.getPath());
            assertTrue(sendRequest.getBody().readUtf8().contains("callback=https%3A%2F%2Fcallbacks.example.test%2Fpushover%2Fcallback"));
            awaitPendingFutureCount(client, "pendingCallbackFutures", 1);

            HttpResponse<String> callbackResponse = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/pushover/callback"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "receipt=" + RECEIPT + "&acknowledged=1&acknowledged_by=jetty-host",
                                    StandardCharsets.UTF_8
                            ))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            ReceiptCallbackResponse callbackPayload = future.get(2, TimeUnit.SECONDS);
            assertEquals(200, callbackResponse.statusCode());
            assertEquals("OK", callbackResponse.body());
            assertEquals(RECEIPT, callbackPayload.receipt());
            assertEquals("jetty-host", callbackPayload.acknowledgedBy());
        } finally {
            executorService.shutdownNow();
            callbackHost.stop();
        }
    }

    private static MessageRequest emergencyRequest() {
        return MessageRequest.builder(USER, "Jetty host-managed async test")
                .priority(MessagePriority.EMERGENCY)
                .retry(30)
                .expire(300)
                .build();
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




