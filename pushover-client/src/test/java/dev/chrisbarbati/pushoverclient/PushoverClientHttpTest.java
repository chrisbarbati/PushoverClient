package dev.chrisbarbati.pushoverclient;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import dev.chrisbarbati.pushoverclient.exception.PushoverApiException;
import dev.chrisbarbati.pushoverclient.exception.PushoverClientException;
import dev.chrisbarbati.pushoverclient.model.attachment.Attachment;
import dev.chrisbarbati.pushoverclient.model.attachment.AttachmentEncoding;
import dev.chrisbarbati.pushoverclient.model.glance.GlanceRequest;
import dev.chrisbarbati.pushoverclient.model.group.GroupCreateResponse;
import dev.chrisbarbati.pushoverclient.model.group.GroupDetailsResponse;
import dev.chrisbarbati.pushoverclient.model.group.GroupListResponse;
import dev.chrisbarbati.pushoverclient.model.group.GroupMutationResponse;
import dev.chrisbarbati.pushoverclient.model.group.GroupUser;
import dev.chrisbarbati.pushoverclient.model.message.MessageRequest;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCancellationResponse;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptStatusResponse;
import dev.chrisbarbati.pushoverclient.model.subscription.SubscriptionMigrationResponse;
import dev.chrisbarbati.pushoverclient.model.SoundsResponse;
import dev.chrisbarbati.pushoverclient.model.validation.UserGroupValidationResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private static final String RECEIPT = "abcdefghijklmnopqrstuvwx123456";
    private static final String GROUP = "go4abk17j3itsva6thz99mdudgq2gm";
    private static final String SUBSCRIPTION = "Forum-f504h08fhlasdfj";

    private MockWebServer server;
    private PushoverClient client;

    /**
     * Starts mock server and creates a client bound to that server.
     *
     * @throws IOException when server startup fails
     * @since 0.1.0
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
     * Verifies user/group validation posts the expected form fields and parses success payloads.
     *
     * @throws Exception when request processing fails
     * @since 0.6.0
     */
    @Test
    void validateUserOrGroup_postsFormEncodedAndParsesResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-v\",\"devices\":[\"pixel-8\",\"ipad\"],\"licenses\":[\"android\",\"ios\"]}"));

        UserGroupValidationResponse response = client.validateUserOrGroup(USER);

        assertEquals(1, response.status());
        assertEquals(List.of("pixel-8", "ipad"), response.devices());
        assertEquals(List.of("android", "ios"), response.licenses());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/1/users/validate.json", request.getPath());
        assertEquals("token=" + TOKEN + "&user=" + USER, request.getBody().readUtf8());
    }

    /**
     * Verifies user/group validation includes an optional device field when requested.
     *
     * @throws Exception when request processing fails
     * @since 0.6.0
     */
    @Test
    void validateUserOrGroup_withDevice_postsDeviceField() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-vd\",\"devices\":[\"phone_1\"],\"licenses\":[\"android\"]}"));

        UserGroupValidationResponse response = client.validateUserOrGroup(USER, "phone_1");

        assertEquals(1, response.status());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/1/users/validate.json", request.getPath());
        assertEquals("token=" + TOKEN + "&user=" + USER + "&device=phone_1", request.getBody().readUtf8());
    }

    /**
     * Verifies sounds endpoint parsing for successful responses.
     *
     * @throws Exception when request processing fails
     * @since 0.1.0
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
     * Verifies glance update request construction and success response parsing.
     *
     * @throws Exception when request processing fails
     * @since 0.9.0
     */
    @Test
    void sendGlance_postsFormEncodedAndParsesResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-gl\"}"));

        var response = client.sendGlance(GlanceRequest.builder(USER)
                .device("watch")
                .text("Widgets sold")
                .count(30)
                .percent(75)
                .build());

        assertEquals(1, response.status());
        assertEquals("req-gl", response.request());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/1/glances.json", request.getPath());
        assertEquals(
                "token=" + TOKEN + "&user=" + USER + "&device=watch&text=Widgets+sold&count=30&percent=75",
                request.getBody().readUtf8()
        );
    }

    /**
     * Verifies glance failures map to {@link PushoverApiException}.
     *
     * @since 0.9.0
     */
    @Test
    void sendGlance_throwsApiExceptionOnFailureResponse() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"status\":0,\"request\":\"req-gl-fail\",\"errors\":[\"user identifier is invalid\"]}"));

        PushoverApiException ex = assertThrows(
                PushoverApiException.class,
                () -> client.sendGlance(GlanceRequest.builder(USER).text("test").build())
        );

        assertEquals(400, ex.getHttpStatus());
        assertEquals("req-gl-fail", ex.getRequestId());
        assertTrue(ex.getErrors().contains("user identifier is invalid"));
    }

    /**
     * Verifies receipt status endpoint parsing and authenticated GET construction.
     *
     * @throws Exception when request processing fails
     * @since 0.2.0
     */
    @Test
    void getReceipt_parsesResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-r\",\"receipt\":\"" + RECEIPT
                        + "\",\"acknowledged\":0,\"last_delivered_at\":1715799990,\"expired\":0,\"expires_at\":1715800300,\"called_back\":0}"));

        ReceiptStatusResponse response = client.getReceipt(RECEIPT);

        assertEquals(1, response.status());
        assertEquals(RECEIPT, response.receipt());
        assertEquals(1715799990L, response.lastDeliveredAt());
        assertEquals(1715800300L, response.expiresAt());
        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/1/receipts/" + RECEIPT + ".json?token=" + TOKEN, request.getPath());
    }

    /**
     * Verifies receipt cancel endpoint posts only the authenticated token field.
     *
     * @throws Exception when request processing fails
     * @since 0.2.0
     */
    @Test
    void cancelReceipt_postsFormEncodedToken() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-cancel\"}"));

        ReceiptCancellationResponse response = client.cancelReceipt(RECEIPT);

        assertEquals(1, response.status());
        assertEquals("req-cancel", response.request());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/1/receipts/" + RECEIPT + "/cancel.json", request.getPath());
        assertEquals("token=" + TOKEN, request.getBody().readUtf8());
    }

    /**
     * Verifies cancel-by-tag encodes dynamic path segments and posts the token body.
     *
     * @throws Exception when request processing fails
     * @since 0.2.0
     */
    @Test
    void cancelByTag_encodesTagPathAndPostsToken() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-tag\"}"));

        ReceiptCancellationResponse response = client.cancelByTag("ops prod/blue");

        assertEquals(1, response.status());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/1/receipts/cancel_by_tag/ops%20prod%2Fblue.json", request.getPath());
        assertEquals("token=" + TOKEN, request.getBody().readUtf8());
    }

    /**
     * Verifies encrypted mode omits plaintext from transmitted request bodies.
     *
     * @throws Exception when request processing fails
     * @since 0.1.0
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
     * Verifies malformed JSON in otherwise successful responses maps to {@link PushoverClientException}.
     *
     * @since 0.5.0
     */
    @Test
    void sendMessage_throwsClientExceptionOnMalformedSuccessPayload() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("not-json"));

        PushoverClientException ex = assertThrows(
                PushoverClientException.class,
                () -> client.sendMessage(MessageRequest.builder(USER, "plain message").build())
        );

        assertEquals("Failed to parse API response", ex.getMessage());
    }

    @Test
    void sendMessage_throwsClientExceptionOnNullSuccessBody() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("null"));

        PushoverClientException ex = assertThrows(
                PushoverClientException.class,
                () -> client.sendMessage(MessageRequest.builder(USER, "plain message").build())
        );

        assertTrue(ex.getMessage().contains("null success body"));
    }

    @Test
    void sendMessage_toleratesTrailingSlashInBaseUrl() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-ts\"}"));

        PushoverClient trailingSlashClient = new PushoverClient(
                TOKEN,
                null,
                HttpClient.newHttpClient(),
                server.url("/").toString()
        );

        trailingSlashClient.sendMessage(MessageRequest.builder(USER, "trailing slash").build());

        RecordedRequest request = server.takeRequest();
        assertEquals("/1/messages.json", request.getPath());
    }

    /**
     * Verifies HTTP 2xx responses that still report {@code status=0} map to {@link PushoverApiException}.
     *
     * @since 0.5.0
     */
    @Test
    void sendMessage_throwsApiExceptionWhenHttpSuccessReportsFailureStatus() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":0,\"request\":\"req-soft-fail\"}"));

        PushoverApiException ex = assertThrows(
                PushoverApiException.class,
                () -> client.sendMessage(MessageRequest.builder(USER, "plain message").build())
        );

        assertEquals(200, ex.getHttpStatus());
        assertEquals("req-soft-fail", ex.getRequestId());
        assertEquals(java.util.List.of("status != 1"), ex.getErrors());
    }

    /**
     * Verifies a non-JSON error body preserves the HTTP status in {@link PushoverApiException}.
     *
     * @since 0.7.0
     */
    @Test
    void getSounds_throwsApiExceptionPreservingHttpStatusOnNonJsonErrorBody() {
        server.enqueue(new MockResponse().setResponseCode(502)
                .setBody("<!DOCTYPE html><html><body>Bad Gateway</body></html>"));

        PushoverApiException ex = assertThrows(PushoverApiException.class, () -> client.getSounds());

        assertEquals(502, ex.getHttpStatus());
        assertTrue(ex.getErrors().isEmpty());
    }

    /**
     * Verifies sounds endpoint failures map to {@link PushoverApiException}.
     *
     * @since 0.1.0
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

    /**
     * Verifies user/group validation failures map to {@link PushoverApiException}.
     *
     * @since 0.6.0
     */
    @Test
    void validateUserOrGroup_throwsApiExceptionOnFailureResponse() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":0,\"request\":\"req-v-fail\",\"errors\":[\"user identifier is invalid\"]}"));

        PushoverApiException ex = assertThrows(PushoverApiException.class, () -> client.validateUserOrGroup(USER));

        assertEquals(200, ex.getHttpStatus());
        assertEquals("req-v-fail", ex.getRequestId());
        assertTrue(ex.getErrors().contains("user identifier is invalid"));
    }

    /**
     * Verifies receipt status failures map to {@link PushoverApiException}.
     *
     * @since 0.2.0
     */
    @Test
    void getReceipt_throwsApiExceptionOnFailureResponse() {
        server.enqueue(new MockResponse().setResponseCode(404)
                .setBody("{\"status\":0,\"request\":\"req-r-fail\",\"errors\":[\"receipt is invalid\"]}"));

        PushoverApiException ex = assertThrows(PushoverApiException.class, () -> client.getReceipt(RECEIPT));

        assertEquals(404, ex.getHttpStatus());
        assertEquals("req-r-fail", ex.getRequestId());
        assertTrue(ex.getErrors().contains("receipt is invalid"));
    }

    /**
     * Verifies receipt cancel failures map to {@link PushoverApiException}.
     *
     * @since 0.2.0
     */
    @Test
    void cancelReceipt_throwsApiExceptionOnFailureResponse() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"status\":0,\"request\":\"req-cancel-fail\",\"errors\":[\"receipt has already expired\"]}"));

        PushoverApiException ex = assertThrows(PushoverApiException.class, () -> client.cancelReceipt(RECEIPT));

        assertEquals(400, ex.getHttpStatus());
        assertEquals("req-cancel-fail", ex.getRequestId());
        assertTrue(ex.getErrors().contains("receipt has already expired"));
    }

    /**
     * Verifies cancel-by-tag failures map to {@link PushoverApiException}.
     *
     * @since 0.2.0
     */
    @Test
    void cancelByTag_throwsApiExceptionOnFailureResponse() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"status\":0,\"request\":\"req-tag-fail\",\"errors\":[\"tag is invalid\"]}"));

        PushoverApiException ex = assertThrows(PushoverApiException.class, () -> client.cancelByTag("team-blue"));

        assertEquals(400, ex.getHttpStatus());
        assertEquals("req-tag-fail", ex.getRequestId());
        assertTrue(ex.getErrors().contains("tag is invalid"));
    }

    /**
     * Verifies group creation posts the expected form fields and parses the returned group key.
     *
     * @throws Exception when request processing fails
     * @since 1.0.0
     */
    @Test
    void createGroup_postsFormEncodedAndParsesResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-cg\",\"group\":\"" + GROUP + "\"}"));

        GroupCreateResponse response = client.createGroup("on-call");

        assertEquals(1, response.status());
        assertEquals(GROUP, response.group());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/1/groups.json", request.getPath());
        assertEquals("token=" + TOKEN + "&name=on-call", request.getBody().readUtf8());
    }

    /**
     * Verifies group listing constructs an authenticated GET and parses group entries.
     *
     * @throws Exception when request processing fails
     * @since 1.0.0
     */
    @Test
    void listGroups_parsesResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-lg\",\"groups\":[{\"group\":\"" + GROUP + "\",\"name\":\"on-call\"}]}"));

        GroupListResponse response = client.listGroups();

        assertEquals(1, response.status());
        assertEquals(1, response.groups().size());
        assertEquals(GROUP, response.groups().get(0).group());
        assertEquals("on-call", response.groups().get(0).name());
        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().startsWith("/1/groups.json?token="));
    }

    /**
     * Verifies group details retrieval constructs an authenticated GET and parses members.
     *
     * @throws Exception when request processing fails
     * @since 1.0.0
     */
    @Test
    void getGroup_parsesResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"request\":\"req-gg\",\"name\":\"My Delivery Group\",\"users\":[{\"user\":\"" + USER
                        + "\",\"device\":null,\"memo\":\"Jim\",\"disabled\":false}]}"));

        GroupDetailsResponse response = client.getGroup(GROUP);

        assertEquals(1, response.status());
        assertEquals("My Delivery Group", response.name());
        assertEquals(1, response.users().size());
        GroupUser member = response.users().get(0);
        assertEquals(USER, member.user());
        assertNull(member.device());
        assertEquals("Jim", member.memo());
        assertFalse(member.disabled());
        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/1/groups/" + GROUP + ".json?token=" + TOKEN, request.getPath());
    }

    /**
     * Verifies adding a user to a group posts the expected fields including device and memo.
     *
     * @throws Exception when request processing fails
     * @since 1.0.0
     */
    @Test
    void addUserToGroup_postsFormEncodedAndParsesResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-ag\"}"));

        GroupMutationResponse response = client.addUserToGroup(GROUP, USER, "iphone", "Jim");

        assertEquals(1, response.status());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/1/groups/" + GROUP + "/add_user.json", request.getPath());
        assertEquals("token=" + TOKEN + "&user=" + USER + "&device=iphone&memo=Jim", request.getBody().readUtf8());
    }

    /**
     * Verifies removing a user from a group posts only the token and user fields.
     *
     * @throws Exception when request processing fails
     * @since 1.0.0
     */
    @Test
    void removeUserFromGroup_postsFormEncoded() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-rg\"}"));

        GroupMutationResponse response = client.removeUserFromGroup(GROUP, USER, null);

        assertEquals(1, response.status());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/1/groups/" + GROUP + "/remove_user.json", request.getPath());
        assertEquals("token=" + TOKEN + "&user=" + USER, request.getBody().readUtf8());
    }

    /**
     * Verifies disabling a user in a group posts the expected device field.
     *
     * @throws Exception when request processing fails
     * @since 1.0.0
     */
    @Test
    void disableUserInGroup_postsFormEncoded() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-dg\"}"));

        GroupMutationResponse response = client.disableUserInGroup(GROUP, USER, "iphone");

        assertEquals(1, response.status());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/1/groups/" + GROUP + "/disable_user.json", request.getPath());
        assertEquals("token=" + TOKEN + "&user=" + USER + "&device=iphone", request.getBody().readUtf8());
    }

    /**
     * Verifies re-enabling a user in a group posts only the token and user fields.
     *
     * @throws Exception when request processing fails
     * @since 1.0.0
     */
    @Test
    void enableUserInGroup_postsFormEncoded() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-eg\"}"));

        GroupMutationResponse response = client.enableUserInGroup(GROUP, USER, null);

        assertEquals(1, response.status());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/1/groups/" + GROUP + "/enable_user.json", request.getPath());
        assertEquals("token=" + TOKEN + "&user=" + USER, request.getBody().readUtf8());
    }

    /**
     * Verifies renaming a group posts the new name.
     *
     * @throws Exception when request processing fails
     * @since 1.0.0
     */
    @Test
    void renameGroup_postsFormEncoded() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1,\"request\":\"req-rn\"}"));

        GroupMutationResponse response = client.renameGroup(GROUP, "on-call-ops");

        assertEquals(1, response.status());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/1/groups/" + GROUP + "/rename.json", request.getPath());
        assertEquals("token=" + TOKEN + "&name=on-call-ops", request.getBody().readUtf8());
    }

    /**
     * Verifies subscription migration posts the expected fields and parses the new subscription user key.
     *
     * @throws Exception when request processing fails
     * @since 1.0.0
     */
    @Test
    void migrateUserToSubscription_postsFormEncodedAndParsesResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":1,\"subscribed_user_key\":\"sPfjsD2fGzEd9TR52DU31Hv4A61Vvk\",\"request\":\"req-m\"}"));

        SubscriptionMigrationResponse response = client.migrateUserToSubscription(SUBSCRIPTION, USER, "iphone", "pushover");

        assertEquals(1, response.status());
        assertEquals("sPfjsD2fGzEd9TR52DU31Hv4A61Vvk", response.subscribedUserKey());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/1/subscriptions/migrate.json", request.getPath());
        assertEquals(
                "token=" + TOKEN + "&subscription=" + SUBSCRIPTION + "&user=" + USER + "&device_name=iphone&sound=pushover",
                request.getBody().readUtf8()
        );
    }

    /**
     * Verifies group creation failures map to {@link PushoverApiException}.
     *
     * @since 1.0.0
     */
    @Test
    void createGroup_throwsApiExceptionOnFailureResponse() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"status\":0,\"request\":\"req-cg-fail\",\"errors\":[\"name is required\"]}"));

        PushoverApiException ex = assertThrows(PushoverApiException.class, () -> client.createGroup("on-call"));

        assertEquals(400, ex.getHttpStatus());
        assertEquals("req-cg-fail", ex.getRequestId());
        assertTrue(ex.getErrors().contains("name is required"));
    }
}


