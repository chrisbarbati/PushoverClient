package org.chrisbarbati.pushoverclient.callback.servlet;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for the generic servlet callback adapter.
 *
 * @since 0.4.0
 * @author Christian Barbati
 */
class CallbackServletsTest {
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
    void create_acceptsValidPostAndDispatchesCallback() throws Exception {
        AtomicReference<ReceiptCallbackResponse> delivered = new AtomicReference<>();
        HttpServlet servlet = CallbackServlets.create(delivered::set);
        ResponseCapture responseCapture = responseCapture();

        servlet.service(
                request("POST", "receipt=abcdefghijklmnopqrstuvwx123456&acknowledged=1&acknowledged_by=chris"),
                responseCapture.response()
        );

        assertEquals(200, responseCapture.status().get());
        assertEquals("text/plain;charset=UTF-8", responseCapture.contentType().get());
        assertEquals("OK", responseCapture.body().toString());
        assertNotNull(delivered.get());
        assertEquals("abcdefghijklmnopqrstuvwx123456", delivered.get().receipt());
        assertEquals("chris", delivered.get().acknowledgedBy());
    }

    @Test
    void create_rejectsInvalidPayload() throws Exception {
        AtomicReference<ReceiptCallbackResponse> delivered = new AtomicReference<>();
        HttpServlet servlet = CallbackServlets.create(delivered::set);
        ResponseCapture responseCapture = responseCapture();

        servlet.service(request("POST", "acknowledged=1"), responseCapture.response());

        assertEquals(400, responseCapture.status().get());
        assertEquals("Callback payload did not include a receipt", responseCapture.body().toString());
        assertNull(delivered.get());
    }

    @Test
    void create_rejectsGetRequests() throws Exception {
        HttpServlet servlet = CallbackServlets.create(callback -> {
        });
        ResponseCapture responseCapture = responseCapture();

        servlet.service(request("GET", null), responseCapture.response());

        assertEquals(405, responseCapture.status().get());
        assertEquals("Method Not Allowed", responseCapture.body().toString());
    }

    @Test
    void create_withPushoverClientCompletesPendingFuture() throws Exception {
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
                java.net.http.HttpClient.newHttpClient(),
                server.url("/").toString().replaceAll("/$", ""),
                properties,
                null
        );
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            var future = client.sendEmergencyMessageAndAwaitCallback(emergencyRequest(), executorService);
            RecordedRequest sendRequest = server.takeRequest();
            assertEquals("/1/messages.json", sendRequest.getPath());
            awaitPendingFutureCount(client, 1);

            HttpServlet servlet = CallbackServlets.create(client);
            ResponseCapture responseCapture = responseCapture();
            servlet.service(
                    request("POST", "receipt=" + RECEIPT + "&acknowledged=1&acknowledged_by=servlet-host"),
                    responseCapture.response()
            );

            ReceiptCallbackResponse callbackResponse = future.get(2, TimeUnit.SECONDS);
            assertEquals(200, responseCapture.status().get());
            assertEquals("OK", responseCapture.body().toString());
            assertEquals(RECEIPT, callbackResponse.receipt());
            assertEquals("servlet-host", callbackResponse.acknowledgedBy());
        } finally {
            executorService.shutdownNow();
        }
    }

    private static HttpServletRequest request(String method, String body) {
        byte[] requestBody = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        ServletInputStream inputStream = new ByteArrayServletInputStream(requestBody);

        return (HttpServletRequest) Proxy.newProxyInstance(
                CallbackServletsTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "getMethod" -> method;
                    case "getInputStream" -> inputStream;
                    case "getReader" -> new BufferedReader(new InputStreamReader(new ByteArrayInputStream(requestBody), StandardCharsets.UTF_8));
                    case "getCharacterEncoding" -> StandardCharsets.UTF_8.name();
                    case "getProtocol" -> "HTTP/1.1";
                    case "getScheme" -> "http";
                    case "getServerName" -> "localhost";
                    case "getServerPort" -> 8080;
                    case "getLocale" -> Locale.US;
                    case "getLocales" -> java.util.Collections.enumeration(java.util.List.of(Locale.US));
                    case "isSecure" -> false;
                    default -> defaultValue(invokedMethod.getReturnType());
                }
        );
    }

    private static ResponseCapture responseCapture() {
        AtomicInteger status = new AtomicInteger(HttpServletResponse.SC_OK);
        AtomicReference<String> contentType = new AtomicReference<>();
        StringWriter body = new StringWriter();
        PrintWriter writer = new PrintWriter(body, true);

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                CallbackServletsTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "setStatus" -> {
                        status.set((Integer) args[0]);
                        yield null;
                    }
                    case "getStatus" -> status.get();
                    case "setContentType" -> {
                        contentType.set((String) args[0]);
                        yield null;
                    }
                    case "getContentType" -> contentType.get();
                    case "getWriter" -> writer;
                    case "getCharacterEncoding" -> StandardCharsets.UTF_8.name();
                    case "flushBuffer" -> {
                        writer.flush();
                        yield null;
                    }
                    default -> defaultValue(invokedMethod.getReturnType());
                }
        );

        return new ResponseCapture(response, status, contentType, body);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0F;
        }
        if (double.class.equals(returnType)) {
            return 0D;
        }
        return null;
    }

    private static MessageRequest emergencyRequest() {
        return MessageRequest.builder(USER, "Servlet host-managed async test")
                .priority(MessagePriority.EMERGENCY)
                .retry(30)
                .expire(300)
                .build();
    }

    private static void awaitPendingFutureCount(PushoverClient client, int expectedCount) throws Exception {
        Field field = PushoverClient.class.getDeclaredField("pendingCallbackFutures");
        field.setAccessible(true);

        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            Object value = field.get(client);
            if (value instanceof Map<?, ?> map && map.size() == expectedCount) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for pendingCallbackFutures to reach size " + expectedCount);
    }

    private record ResponseCapture(
            HttpServletResponse response,
            AtomicInteger status,
            AtomicReference<String> contentType,
            StringWriter body
    ) {
    }

    private static final class ByteArrayServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        private ByteArrayServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            if (readListener != null) {
                try {
                    readListener.onDataAvailable();
                    if (isFinished()) {
                        readListener.onAllDataRead();
                    }
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to notify read listener", ex);
                }
            }
        }
    }
}


