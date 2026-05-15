package org.chrisbarbati.pushoverclient;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.chrisbarbati.pushoverclient.exception.PushoverApiException;
import org.chrisbarbati.pushoverclient.model.attachment.Attachment;
import org.chrisbarbati.pushoverclient.model.attachment.AttachmentEncoding;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.SoundsResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP-level tests that validate request construction and response mapping.
 *
 * @since 0.1.0
 * @author Christian Barbati
 */
class PushoverClientHttpTest {
    private static final String TOKEN = "a12345678901234567890123456789";
    private static final String USER = "uQiRzpo4DXghDmr9QzzfQu27cmVRsG";
    private static final String ENCRYPTION_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private MockWebServer server;
    private PushoverClient client;

    /**
     * Starts mock server and creates a client bound to that server.
     *
     * @throws IOException when server startup fails
     * @since 0.1.0
     * @author Christian Barbati
     */
    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new PushoverClient(TOKEN, null, HttpClient.newHttpClient(), server.url("/").toString().replaceAll("/$", ""));
    }

    /**
     * Shuts down the mock server.
     *
     * @throws IOException when shutdown fails
     * @since 0.1.0
     * @author Christian Barbati
     */
    @AfterEach
    void teardown() throws IOException {
        server.shutdown();
    }

    /**
     * Verifies standard message posts as application/x-www-form-urlencoded.
     *
     * @throws Exception when request processing fails
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void sendMessage_postsFormEncodedWithoutAttachment() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-1\"}"));

        client.sendMessage(MessageRequest.builder(USER, "plain message").title("title").build());

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/1/messages.json", request.getPath());
        String contentType = request.getHeader("Content-Type");
        assertNotNull(contentType);
        assertTrue(contentType.contains("application/x-www-form-urlencoded"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("token="));
        assertTrue(body.contains("user="));
        assertTrue(body.contains("message=plain+message"));
    }

    /**
     * Verifies multipart upload is used for binary attachment mode.
     *
     * @throws Exception when request processing fails
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void sendMessage_postsMultipartAttachment() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-2\"}"));

        Attachment attachment = new Attachment("image.jpg", "abc".getBytes(), "image/jpeg", AttachmentEncoding.MULTIPART);
        client.sendMessage(MessageRequest.builder(USER, "multipart message").attachment(attachment).build());

        RecordedRequest request = server.takeRequest();
        String contentType = request.getHeader("Content-Type");
        assertNotNull(contentType);
        assertTrue(contentType.contains("multipart/form-data"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("name=\"attachment\"; filename=\"image.jpg\""));
        assertTrue(body.contains("Content-Type: image/jpeg"));
        assertTrue(body.contains("multipart message"));
    }

    /**
     * Verifies base64 attachment mode is encoded as form fields.
     *
     * @throws Exception when request processing fails
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void sendMessage_postsBase64Attachment() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-3\"}"));

        Attachment attachment = new Attachment("image.jpg", "abc".getBytes(), "image/jpeg", AttachmentEncoding.BASE64);
        client.sendMessage(MessageRequest.builder(USER, "base64 message").attachment(attachment).build());

        RecordedRequest request = server.takeRequest();
        String contentType = request.getHeader("Content-Type");
        assertNotNull(contentType);
        assertTrue(contentType.contains("application/x-www-form-urlencoded"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("attachment_base64=YWJj"));
        assertTrue(body.contains("attachment_type=image%2Fjpeg"));
    }

    /**
     * Verifies sounds endpoint parsing for successful responses.
     *
     * @throws Exception when request processing fails
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void getSounds_parsesResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-s\",\"sounds\":{\"pushover\":\"Pushover\"}}"));

        SoundsResponse response = client.getSounds();

        assertEquals(1, response.status());
        assertEquals(Map.of("pushover", "Pushover"), response.sounds());
        RecordedRequest request = server.takeRequest();
        String path = request.getPath();
        assertNotNull(path);
        assertTrue(path.startsWith("/1/sounds.json?token="));
    }

    /**
     * Verifies limits endpoint parsing for successful responses.
     *
     * @throws Exception when request processing fails
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void getLimits_parsesResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-l\",\"limit\":10000,\"remaining\":9999,\"reset\":1700000000}"));

        var response = client.getLimits();

        assertEquals(10000, response.limit());
        assertEquals(9999, response.remaining());
        RecordedRequest request = server.takeRequest();
        String path = request.getPath();
        assertNotNull(path);
        assertTrue(path.startsWith("/1/apps/limits.json?token="));
    }

    /**
     * Verifies encrypted mode omits plaintext from transmitted request bodies.
     *
     * @throws Exception when request processing fails
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void sendMessage_withEncryption_sendsEncryptedFields() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-enc\"}"));
        PushoverClient encryptedClient = new PushoverClient(
                TOKEN,
                ENCRYPTION_KEY,
                HttpClient.newHttpClient(),
                server.url("/").toString().replaceAll("/$", "")
        );

        encryptedClient.sendMessage(MessageRequest.builder(USER, "sensitive payload for test")
                .title("hidden title")
                .url("https://example.test/secure")
                .urlTitle("hidden link")
                .encrypted(true)
                .build());

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("encrypted=1"));
        assertFalse(body.contains("sensitive+payload+for+test"));
        assertFalse(body.contains("hidden+title"));
        assertFalse(body.contains("example.test"));
    }

    /**
     * Verifies message endpoint failures map to {@link PushoverApiException}.
     *
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void sendMessage_throwsApiExceptionOnFailureResponse() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"status\":0,\"request\":\"req-fail\",\"errors\":[\"user identifier is invalid\"]}"));

        PushoverApiException ex = assertThrows(PushoverApiException.class,
                () -> client.sendMessage(MessageRequest.builder(USER, "plain message").build()));

        assertEquals(400, ex.getHttpStatus());
        assertEquals("req-fail", ex.getRequestId());
        assertEquals("user identifier is invalid", ex.getErrors().get(0));
    }

    /**
     * Verifies sounds endpoint failures map to {@link PushoverApiException}.
     *
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void getSounds_throwsApiExceptionOnFailureResponse() {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"status\":0,\"request\":\"req-auth\",\"errors\":[\"application token is invalid\"]}"));

        PushoverApiException ex = assertThrows(PushoverApiException.class, () -> client.getSounds());

        assertEquals(401, ex.getHttpStatus());
        assertEquals("req-auth", ex.getRequestId());
        assertTrue(ex.getErrors().contains("application token is invalid"));
    }
}

