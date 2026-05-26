package org.chrisbarbati.pushoverclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import org.chrisbarbati.pushoverclient.callback.CallbackServer;
import org.chrisbarbati.pushoverclient.crypto.E2eeEncryptor;
import org.chrisbarbati.pushoverclient.exception.PushoverApiException;
import org.chrisbarbati.pushoverclient.exception.PushoverClientException;
import org.chrisbarbati.pushoverclient.model.ApiErrorResponse;
import org.chrisbarbati.pushoverclient.model.attachment.Attachment;
import org.chrisbarbati.pushoverclient.model.attachment.AttachmentEncoding;
import org.chrisbarbati.pushoverclient.model.LimitsResponse;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.message.MessageResponse;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCancellationResponse;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptStatusResponse;
import org.chrisbarbati.pushoverclient.model.SoundsResponse;
import org.chrisbarbati.pushoverclient.model.validation.UserGroupValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Minimal Java client for core Pushover REST endpoints.
 *
 * @apiNote This client currently targets messages, user/group validation, receipts, sounds, and app limits endpoints.
 * @implNote Validation follows documented Pushover API constraints before HTTP submission.
 * @since 0.1.0
 * @author Christian Barbati
 */
public class PushoverClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushoverClient.class);

    // Pushover user/group keys are 30 alphanumeric chars.
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9]{30}$");
    // Pushover user/group keys support up to 50 comma-separated identifiers for message sending.
    private static final Pattern IDENTIFIER_LIST = Pattern.compile("^[A-Za-z0-9]{30}(,[A-Za-z0-9]{30}){0,49}$");
    // Emergency receipt ids are 30 alphanumeric chars in API responses and follow-up endpoints.
    private static final Pattern RECEIPT_ID = Pattern.compile("^[A-Za-z0-9]{30}$");
    // Device names support [A-Za-z0-9_-] and max length 25 per documented API limits.
    private static final Pattern DEVICE_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,25}$");
    private static final Pattern DEVICE_LIST = Pattern.compile("^[A-Za-z0-9_-]{1,25}(,[A-Za-z0-9_-]{1,25})*$");

    private final String appToken;
    private final String encryptionKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final E2eeEncryptor encryptor;
    private final String baseUrl;
    private final AsyncReceiptProperties asyncReceiptProperties;
    private final CallbackServer callbackServer;
    private final ConcurrentMap<String, PendingReceiptFuture<ReceiptStatusResponse>> pendingPollFutures;
    private final ConcurrentMap<String, PendingReceiptFuture<ReceiptCallbackResponse>> pendingCallbackFutures;

    /**
     * Creates a client without end-to-end encryption.
     *
     * @param appToken application API token
     * @since 0.1.0
     */
    public PushoverClient(String appToken) {
        this(appToken, null);
    }

    /**
     * Creates a client with optional end-to-end encryption key.
     *
     * @param appToken application API token
     * @param encryptionKey optional 64-char hex key for encrypted messages
     * @since 0.1.0
     */
    public PushoverClient(String appToken, String encryptionKey) {
        this(appToken, encryptionKey, HttpClient.newHttpClient(), "https://api.pushover.net");
    }

    /**
     * Creates a client with explicit transport and base URL.
     *
     * @param appToken application API token
     * @param encryptionKey optional 64-char hex key for encrypted messages
     * @param httpClient HTTP transport implementation
     * @param baseUrl API base URL
     * @since 0.1.0
     */
    public PushoverClient(String appToken, String encryptionKey, HttpClient httpClient, String baseUrl) {
        this(appToken, encryptionKey, httpClient, baseUrl, null, null);
    }

    /**
     * Creates a client with explicit transport, base URL, and async receipt support.
     *
     * @param appToken application API token
     * @param encryptionKey optional 64-char hex key for encrypted messages
     * @param httpClient HTTP transport implementation
     * @param baseUrl API base URL
     * @param asyncReceiptProperties async polling/callback defaults; when null, library defaults are used
     * @param callbackServer callback server implementation used by callback-based futures
     * @since 0.3.0
     */
    public PushoverClient(
            String appToken,
            String encryptionKey,
            HttpClient httpClient,
            String baseUrl,
            AsyncReceiptProperties asyncReceiptProperties,
            CallbackServer callbackServer
    ) {
        this.appToken = requireNonBlank(appToken, "appToken is required");
        this.encryptionKey = isBlank(encryptionKey) ? null : encryptionKey;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
        this.baseUrl = requireNonBlank(baseUrl, "baseUrl is required");
        this.objectMapper = new ObjectMapper();
        this.encryptor = new E2eeEncryptor();
        this.asyncReceiptProperties = asyncReceiptProperties == null ? AsyncReceiptProperties.defaults() : asyncReceiptProperties;
        this.callbackServer = callbackServer;
        this.pendingPollFutures = new ConcurrentHashMap<>();
        this.pendingCallbackFutures = new ConcurrentHashMap<>();
    }

    /**
     * Sends a message through {@code /1/messages.json}.
     *
     * @param request message request payload
     * @return parsed success response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     * @throws PushoverClientException when request validation fails
     * @throws PushoverApiException when Pushover returns a non-success response
     * @since 0.1.0
     */
    public MessageResponse sendMessage(MessageRequest request) throws IOException, InterruptedException {
        validate(request);
        Map<String, String> fields = buildMessageFields(request);

        Attachment attachment = request.attachment();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "Sending message: endpoint={}, encrypted={}, hasAttachment={}, attachmentEncoding={}, messageLength={}",
                    PushoverEndpoints.SEND_MESSAGE,
                    request.encrypted(),
                    attachment != null,
                    attachment == null ? "none" : attachment.encoding(),
                    request.message().length()
            );
        }

        HttpRequest httpRequest;
        // Pushover requires binary attachments via multipart/form-data.
        if (attachment != null && attachment.encoding() == AttachmentEncoding.MULTIPART) {
            httpRequest = buildMultipartRequest(PushoverEndpoints.SEND_MESSAGE, fields, attachment);
        } else {
            // Default to standard form encoding, including attachment_base64 mode.
            httpRequest = buildFormRequest(PushoverEndpoints.SEND_MESSAGE, fields);
        }

        return send(httpRequest, MessageResponse.class);
    }

    /**
     * Sends an emergency message and polls the receipts endpoint until acknowledgement.
     *
     * @param request emergency message request
     * @param executorService caller-managed executor used for async work
     * @return future completing with the acknowledged receipt payload
     * @since 0.3.0
     */
    public CompletableFuture<ReceiptStatusResponse> sendEmergencyMessageAndAwaitAcknowledgement(
            MessageRequest request,
            ExecutorService executorService
    ) {
        return sendEmergencyMessageAndAwaitAcknowledgement(request, executorService, asyncReceiptProperties.pollFutureExpiration());
    }

    /**
     * Sends an emergency message and polls the receipts endpoint until acknowledgement.
     *
     * @param request emergency message request
     * @param executorService caller-managed executor used for async work
     * @param futureExpiration override expiration for the returned future
     * @return future completing with the acknowledged receipt payload
     * @since 0.3.0
     */
    public CompletableFuture<ReceiptStatusResponse> sendEmergencyMessageAndAwaitAcknowledgement(
            MessageRequest request,
            ExecutorService executorService,
            Duration futureExpiration
    ) {
        MessageRequest emergencyRequest = requireEmergencyRequest(request, "sendEmergencyMessageAndAwaitAcknowledgement");
        ExecutorService executor = Objects.requireNonNull(executorService, "executorService is required");
        Duration expiration = resolveFutureExpiration(
                futureExpiration,
                asyncReceiptProperties.pollFutureExpiration(),
                AsyncReceiptProperties.POLL_FUTURE_EXPIRATION_PROPERTY
        );

        CompletableFuture<ReceiptStatusResponse> future = new CompletableFuture<>();
        submitAsyncTask(executor, future, () -> {
            MessageResponse response = sendMessage(emergencyRequest);
            String receiptId = requireAsyncReceipt(response);
            registerPendingFuture(pendingPollFutures, receiptId, future, parseTags(emergencyRequest.tags()));
            pollReceiptUntilTerminal(receiptId, future, expiration);
        });
        return future;
    }

    /**
     * Sends an emergency message and completes the future when a configured callback endpoint delivers Pushover's POST back into this client.
     *
     * @param request emergency message request
     * @param executorService caller-managed executor used for async work
     * @return future completing with the callback payload
     * @since 0.3.0
     */
    public CompletableFuture<ReceiptCallbackResponse> sendEmergencyMessageAndAwaitCallback(
            MessageRequest request,
            ExecutorService executorService
    ) {
        return sendEmergencyMessageAndAwaitCallback(request, executorService, asyncReceiptProperties.callbackFutureExpiration());
    }

    /**
     * Sends an emergency message and completes the future when a configured callback endpoint delivers Pushover's POST back into this client.
     *
     * @param request emergency message request
     * @param executorService caller-managed executor used for async work
     * @param futureExpiration override expiration for the returned future
     * @return future completing with the callback payload
     * @since 0.3.0
     */
    public CompletableFuture<ReceiptCallbackResponse> sendEmergencyMessageAndAwaitCallback(
            MessageRequest request,
            ExecutorService executorService,
            Duration futureExpiration
    ) {
        MessageRequest emergencyRequest = requireEmergencyRequest(request, "sendEmergencyMessageAndAwaitCallback");
        ExecutorService executor = Objects.requireNonNull(executorService, "executorService is required");
        Duration expiration = resolveFutureExpiration(
                futureExpiration,
                asyncReceiptProperties.callbackFutureExpiration(),
                AsyncReceiptProperties.CALLBACK_FUTURE_EXPIRATION_PROPERTY
        );

        CompletableFuture<ReceiptCallbackResponse> future = new CompletableFuture<>();
        submitAsyncTask(executor, future, () -> {
            if (callbackServer != null) {
                ensureCallbackServerStarted();
            }
            MessageRequest callbackRequest = withResolvedCallback(emergencyRequest);
            MessageResponse response = sendMessage(callbackRequest);
            String receiptId = requireAsyncReceipt(response);
            registerPendingFuture(pendingCallbackFutures, receiptId, future, parseTags(callbackRequest.tags()));
            monitorFutureExpiration(receiptId, future, expiration, executor, pendingCallbackFutures, "callback");
        });
        return future;
    }


    /**
     * Accepts a parsed callback payload and completes any matching callback-based futures managed by this client.
     *
     * @param callbackResponse parsed callback payload delivered by a host-managed adapter
     * @since 0.4.0
     */
    public void acceptCallbackReceipt(ReceiptCallbackResponse callbackResponse) {
        handleCallbackReceipt(Objects.requireNonNull(callbackResponse, "callbackResponse is required"));
    }

    /**
     * Validates a single user or group key via {@code /1/users/validate.json}.
     *
     * @param userOrGroup user key or group key to validate
     * @return parsed validation response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     * @throws PushoverClientException when the identifier is invalid
     * @throws PushoverApiException when Pushover returns a non-success response
     * @since 0.6.0
     */
    public UserGroupValidationResponse validateUserOrGroup(String userOrGroup) throws IOException, InterruptedException {
        return validateUserOrGroup(userOrGroup, null);
    }

    /**
     * Validates a single user or group key, optionally constrained to a specific device, via {@code /1/users/validate.json}.
     *
     * @param userOrGroup user key or group key to validate
     * @param device optional single device name to validate for the target account
     * @return parsed validation response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     * @throws PushoverClientException when the identifier or device is invalid
     * @throws PushoverApiException when Pushover returns a non-success response
     * @since 0.6.0
     */
    public UserGroupValidationResponse validateUserOrGroup(String userOrGroup, String device) throws IOException, InterruptedException {
        String normalizedIdentifier = requireUserOrGroup(userOrGroup);
        String normalizedDevice = requireSingleDeviceName(device);
        LOGGER.debug(
                "Validating user/group target: endpoint={}, device={}",
                PushoverEndpoints.VALIDATE_USER_OR_GROUP,
                normalizedDevice
        );

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user", normalizedIdentifier);
        putIfPresent(fields, "device", normalizedDevice);
        return postForm(PushoverEndpoints.VALIDATE_USER_OR_GROUP, fields, UserGroupValidationResponse.class);
    }

    /**
     * Fetches available sounds from {@code /1/sounds.json}.
     *
     * @return parsed sounds response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     * @throws PushoverApiException when Pushover returns a non-success response
     * @since 0.1.0
     */
    public SoundsResponse getSounds() throws IOException, InterruptedException {
        LOGGER.debug("Fetching sounds from endpoint={}", PushoverEndpoints.SOUNDS);
        return getJson(PushoverEndpoints.SOUNDS, SoundsResponse.class);
    }

    /**
     * Fetches monthly quota data from {@code /1/apps/limits.json}.
     *
     * @return parsed limits response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     * @throws PushoverApiException when Pushover returns a non-success response
     * @since 0.1.0
     */
    public LimitsResponse getLimits() throws IOException, InterruptedException {
        LOGGER.debug("Fetching limits from endpoint={}", PushoverEndpoints.LIMITS);
        return getJson(PushoverEndpoints.LIMITS, LimitsResponse.class);
    }

    /**
     * Fetches emergency receipt status from {@code /1/receipts/{receipt}.json}.
     *
     * @param receiptId emergency receipt id returned by {@link #sendMessage(MessageRequest)}
     * @return parsed receipt status response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     * @throws PushoverClientException when the receipt id is invalid
     * @throws PushoverApiException when Pushover returns a non-success response
     * @since 0.2.0
     */
    public ReceiptStatusResponse getReceipt(String receiptId) throws IOException, InterruptedException {
        String normalizedReceiptId = requireReceiptId(receiptId);
        LOGGER.debug("Fetching receipt status: endpoint={}, receiptId={}", PushoverEndpoints.RECEIPTS, normalizedReceiptId);
        return getJson(PushoverEndpoints.RECEIPTS + "/" + encodePathSegment(normalizedReceiptId) + ".json", ReceiptStatusResponse.class);
    }

    /**
     * Cancels an emergency receipt via {@code /1/receipts/{receipt}/cancel.json}.
     *
     * @param receiptId emergency receipt id returned by {@link #sendMessage(MessageRequest)}
     * @return parsed receipt cancellation response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     * @throws PushoverClientException when the receipt id is invalid
     * @throws PushoverApiException when Pushover returns a non-success response
     * @since 0.2.0
     */
    public ReceiptCancellationResponse cancelReceipt(String receiptId) throws IOException, InterruptedException {
        String normalizedReceiptId = requireReceiptId(receiptId);
        LOGGER.debug("Cancelling receipt: endpoint={}, receiptId={}", PushoverEndpoints.RECEIPTS, normalizedReceiptId);
        ReceiptCancellationResponse response = postForm(
                PushoverEndpoints.RECEIPTS + "/" + encodePathSegment(normalizedReceiptId) + "/cancel.json",
                Map.of(),
                ReceiptCancellationResponse.class
        );
        cancelPendingReceipt(normalizedReceiptId);
        return response;
    }

    /**
     * Cancels outstanding emergency receipts associated with a tag via {@code /1/receipts/cancel_by_tag/{tag}.json}.
     *
     * @param tag message tag to cancel
     * @return parsed receipt cancellation response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     * @throws PushoverClientException when the tag is blank
     * @throws PushoverApiException when Pushover returns a non-success response
     * @since 0.2.0
     */
    public ReceiptCancellationResponse cancelByTag(String tag) throws IOException, InterruptedException {
        String normalizedTag = requireTag(tag);
        LOGGER.debug("Cancelling receipts by tag: endpoint={}, tag={}", PushoverEndpoints.CANCEL_BY_TAG, normalizedTag);
        ReceiptCancellationResponse response = postForm(
                PushoverEndpoints.CANCEL_BY_TAG + "/" + encodePathSegment(normalizedTag) + ".json",
                Map.of(),
                ReceiptCancellationResponse.class
        );
        cancelPendingReceiptsByTag(normalizedTag);
        return response;
    }

    /**
     * Builds API fields for a message request.
     *
     * @param request message request
     * @return ordered key/value map for request encoding
     * @throws PushoverClientException when encrypted mode is requested without a key
     * @since 0.1.0
     */
    private Map<String, String> buildMessageFields(MessageRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("token", appToken);
        fields.put("user", request.user());

        putIfPresent(fields, "device", request.device());
        putIfPresent(fields, "sound", request.sound());
        putIfPresent(fields, "callback", request.callback());
        putIfPresent(fields, "tags", request.tags());

        if (request.priority() != null) {
            fields.put("priority", Integer.toString(request.priority().value()));
        }
        if (request.timestamp() != null) {
            fields.put("timestamp", Long.toString(request.timestamp()));
        }
        if (request.ttl() != null) {
            fields.put("ttl", Integer.toString(request.ttl()));
        }
        if (request.retry() != null) {
            fields.put("retry", Integer.toString(request.retry()));
        }
        if (request.expire() != null) {
            fields.put("expire", Integer.toString(request.expire()));
        }
        if (request.html() != null) {
            fields.put("html", Integer.toString(request.html()));
        }
        if (request.monospace() != null) {
            fields.put("monospace", Integer.toString(request.monospace()));
        }

        if (request.encrypted()) {
            if (encryptionKey == null) {
                throw new PushoverClientException("Encrypted messages require an encryption key");
            }
            // encrypted=1 tells Pushover to decrypt message/title/url/url_title server-side.
            fields.put("encrypted", "1");
            fields.put("message", encryptor.encryptField(request.message(), encryptionKey));
            putEncryptedIfPresent(fields, "title", request.title());
            putEncryptedIfPresent(fields, "url", request.url());
            putEncryptedIfPresent(fields, "url_title", request.urlTitle());
        } else {
            fields.put("message", request.message());
            putIfPresent(fields, "title", request.title());
            putIfPresent(fields, "url", request.url());
            putIfPresent(fields, "url_title", request.urlTitle());
        }

        Attachment attachment = request.attachment();
        if (attachment != null && attachment.encoding() == AttachmentEncoding.BASE64) {
            // Base64 mode uses attachment_base64 + attachment_type instead of multipart bodies.
            fields.put("attachment_base64", Base64.getEncoder().encodeToString(attachment.data()));
            fields.put("attachment_type", attachment.contentType());
        }

        return fields;
    }

    /**
     * Encrypts optional fields when present.
     *
     * @param fields output map
     * @param key API field name
     * @param value plaintext field value
     * @since 0.1.0
     */
    private void putEncryptedIfPresent(Map<String, String> fields, String key, String value) {
        if (!isBlank(value)) {
            fields.put(key, encryptor.encryptField(value, encryptionKey));
        }
    }

    /**
     * Creates a form-encoded POST request.
     *
     * @param path endpoint path
     * @param fields request key/value fields
     * @return configured HTTP request
     * @since 0.1.0
     */
    private HttpRequest buildFormRequest(String path, Map<String, String> fields) {
        String body = encodeFormBody(fields);
        return HttpRequest.newBuilder(buildUri(path))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Creates a multipart/form-data POST request including binary attachment bytes.
     *
     * @param path endpoint path
     * @param fields non-binary fields
     * @param attachment attachment payload
     * @return configured HTTP request
     * @since 0.1.0
     */
    private HttpRequest buildMultipartRequest(String path, Map<String, String> fields, Attachment attachment) {
        String boundary = "----PushoverClient" + UUID.randomUUID();
        byte[] body = multipartBody(fields, attachment, boundary);
        return HttpRequest.newBuilder(buildUri(path))
                .header("Accept", "application/json")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
    }

    /**
     * Builds endpoint URI from base URL and path.
     *
     * @param path endpoint path
     * @return resolved URI
     * @since 0.1.0
     */
    private URI buildUri(String path) {
        return URI.create(baseUrl + path);
    }

    /**
     * Executes a GET request with application token authentication.
     *
     * @param path endpoint path without query string
     * @param responseType expected success payload type
     * @param <T> success payload type
     * @return parsed success response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     * @since 0.2.0
     */
    private <T> T getJson(String path, Class<T> responseType) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path + "?token=" + encode(appToken)))
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(request, responseType);
    }

    /**
     * Executes a form-encoded POST request with application token authentication.
     *
     * @param path endpoint path
     * @param fields additional form fields excluding the application token
     * @param responseType expected success payload type
     * @param <T> success payload type
     * @return parsed success response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     * @since 0.2.0
     */
    private <T> T postForm(String path, Map<String, String> fields, Class<T> responseType) throws IOException, InterruptedException {
        Map<String, String> requestFields = new LinkedHashMap<>();
        requestFields.put("token", appToken);
        requestFields.putAll(fields);
        return send(buildFormRequest(path, requestFields), responseType);
    }

    /**
     * Sends an HTTP request and parses its JSON response.
     *
     * @param httpRequest prepared request
     * @param responseType expected success payload type
     * @param <T> success payload type
     * @return parsed success response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     * @since 0.2.0
     */
    private <T> T send(HttpRequest httpRequest, Class<T> responseType) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return parseResponse(response, responseType);
    }

    /**
     * Parses successful responses and maps error payloads to {@link PushoverApiException}.
     *
     * @param response raw HTTP response
     * @param responseType expected success payload type
     * @param <T> success payload type
     * @return parsed success object
     * @throws PushoverApiException when API returns an error response body
     * @throws PushoverClientException when response parsing fails
     * @since 0.1.0
     */
    private <T> T parseResponse(HttpResponse<String> response, Class<T> responseType) {
        try {
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                T value = objectMapper.readValue(response.body(), responseType);
                Integer status = extractStatus(value);
                String requestId = extractRequestId(value);
                if (status != null && status != 1) {
                    List<String> errors = extractErrors(response.body());
                    LOGGER.warn(
                            "API returned non-success status despite HTTP success: responseType={}, httpStatus={}, requestId={}",
                            responseType.getSimpleName(),
                            response.statusCode(),
                            requestId
                    );
                    throw new PushoverApiException(
                            "Pushover API reported failure",
                            response.statusCode(),
                            requestId,
                            errors.isEmpty() ? List.of("status != 1") : errors
                    );
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("API call succeeded: responseType={}, httpStatus={}, requestId={}",
                            responseType.getSimpleName(), response.statusCode(), requestId);
                }
                return value;
            }

            ApiErrorResponse errorResponse = objectMapper.readValue(response.body(), ApiErrorResponse.class);
            LOGGER.warn(
                    "API request failed: httpStatus={}, requestId={}, errorCount={}",
                    response.statusCode(),
                    errorResponse.request(),
                    errorResponse.errors() == null ? 0 : errorResponse.errors().size()
            );

            throw new PushoverApiException(
                    "Pushover API request failed",
                    response.statusCode(),
                    errorResponse.request(),
                    errorResponse.errors()
            );
        } catch (IOException ex) {
            LOGGER.warn("Failed to parse API response: responseType={}, httpStatus={}", responseType.getSimpleName(), response.statusCode());
            throw new PushoverClientException("Failed to parse API response", ex);
        }
    }

    /**
     * Extracts the common API status field from known success payloads.
     *
     * @param value parsed success payload
     * @return status when available, otherwise null
     * @since 0.2.0
     */
    private static Integer extractStatus(Object value) {
        if (value instanceof MessageResponse messageResponse) {
            return messageResponse.status();
        }
        if (value instanceof UserGroupValidationResponse userGroupValidationResponse) {
            return userGroupValidationResponse.status();
        }
        if (value instanceof SoundsResponse soundsResponse) {
            return soundsResponse.status();
        }
        if (value instanceof LimitsResponse limitsResponse) {
            return limitsResponse.status();
        }
        if (value instanceof ReceiptStatusResponse receiptStatusResponse) {
            return receiptStatusResponse.status();
        }
        if (value instanceof ReceiptCancellationResponse receiptCancellationResponse) {
            return receiptCancellationResponse.status();
        }
        return null;
    }

    /**
     * Extracts the common API request id field from known success payloads.
     *
     * @param value parsed success payload
     * @return request id when available, otherwise null
     * @since 0.2.0
     */
    private static String extractRequestId(Object value) {
        if (value instanceof MessageResponse messageResponse) {
            return messageResponse.request();
        }
        if (value instanceof UserGroupValidationResponse userGroupValidationResponse) {
            return userGroupValidationResponse.request();
        }
        if (value instanceof SoundsResponse soundsResponse) {
            return soundsResponse.request();
        }
        if (value instanceof LimitsResponse limitsResponse) {
            return limitsResponse.request();
        }
        if (value instanceof ReceiptStatusResponse receiptStatusResponse) {
            return receiptStatusResponse.request();
        }
        if (value instanceof ReceiptCancellationResponse receiptCancellationResponse) {
            return receiptCancellationResponse.request();
        }
        return null;
    }

    private List<String> extractErrors(String responseBody) {
        try {
            ApiErrorResponse errorResponse = objectMapper.readValue(responseBody, ApiErrorResponse.class);
            if (errorResponse.errors() == null) {
                return List.of();
            }
            return errorResponse.errors();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private MessageRequest requireEmergencyRequest(MessageRequest request, String methodName) {
        Objects.requireNonNull(request, "request is required");
        if (request.priority() != MessagePriority.EMERGENCY) {
            throw new PushoverClientException(methodName + " requires priority=EMERGENCY");
        }
        validate(request);
        return request;
    }

    private Duration resolveFutureExpiration(Duration override, Duration configuredDefault, String propertyName) {
        Duration resolved = override == null ? configuredDefault : override;
        if (resolved == null || resolved.isZero() || resolved.isNegative()) {
            throw new PushoverClientException(propertyName + " must be a positive duration");
        }
        return resolved;
    }

    private void submitAsyncTask(ExecutorService executor, CompletableFuture<?> future, InterruptibleTask task) {
        try {
            executor.submit(() -> {
                try {
                    task.run();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    future.completeExceptionally(ex);
                } catch (CancellationException ex) {
                    future.cancel(false);
                } catch (Exception ex) {
                    future.completeExceptionally(ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            future.completeExceptionally(ex);
        }
    }

    private void pollReceiptUntilTerminal(
            String receiptId,
            CompletableFuture<ReceiptStatusResponse> future,
            Duration futureExpiration
    ) throws IOException, InterruptedException {
        long startedAt = System.nanoTime();
        while (!future.isDone()) {
            if (hasExpired(startedAt, futureExpiration)) {
                future.completeExceptionally(new TimeoutException("Polling future expired before acknowledgement for receipt " + receiptId));
                return;
            }

            ReceiptStatusResponse response = getReceipt(receiptId);
            if (response.acknowledged() == 1) {
                future.complete(response);
                return;
            }
            if (response.expired() == 1) {
                future.completeExceptionally(new PushoverClientException("Receipt expired before acknowledgement for receipt " + receiptId));
                return;
            }

            sleep(asyncReceiptProperties.pollInterval());
        }
    }

    private void monitorFutureExpiration(
            String receiptId,
            CompletableFuture<?> future,
            Duration futureExpiration,
            ExecutorService executor,
            ConcurrentMap<String, ? extends PendingReceiptFuture<?>> pendingFutures,
            String futureType
    ) {
        submitAsyncTask(executor, future, () -> {
            long startedAt = System.nanoTime();
            while (!future.isDone()) {
                if (hasExpired(startedAt, futureExpiration)) {
                    pendingFutures.remove(receiptId);
                    future.completeExceptionally(new TimeoutException(
                            "The " + futureType + " future expired before completion for receipt " + receiptId
                    ));
                    return;
                }
                sleep(asyncReceiptProperties.pollInterval());
            }
        });
    }

    private synchronized void ensureCallbackServerStarted() throws Exception {
        if (callbackServer == null) {
            throw new PushoverClientException("A callbackServer is required for callback-based asynchronous receipt handling");
        }
        if (!callbackServer.isRunning()) {
            callbackServer.start(this::handleCallbackReceipt);
        }
    }

    private MessageRequest withResolvedCallback(MessageRequest request) {
        String defaultCallback = asyncReceiptProperties.defaultCallbackUrl();
        String providedCallback = normalizeBlank(request.callback());
        String effectiveCallback;

        if (!isBlank(providedCallback)) {
            if (!isBlank(defaultCallback)
                    && !providedCallback.equals(defaultCallback)
                    && asyncReceiptProperties.warnOnConflictingCallback()) {
                LOGGER.warn(
                        "Provided callback {} conflicts with default callback {}. Using the provided callback. This warning log can be disabled via property {}",
                        providedCallback,
                        defaultCallback,
                        AsyncReceiptProperties.WARN_ON_CONFLICTING_CALLBACK_PROPERTY
                );
            }
            effectiveCallback = providedCallback;
        } else if (!isBlank(defaultCallback)) {
            effectiveCallback = defaultCallback;
        } else if (callbackServer != null && callbackServer.callbackUri() != null) {
            effectiveCallback = callbackServer.callbackUri().toString();
        } else {
            throw new PushoverClientException(
                    "A callback URL is required for callback-based asynchronous receipt handling; configure "
                            + AsyncReceiptProperties.DEFAULT_CALLBACK_URL_PROPERTY
                            + ", provide request.callback(), or configure a callbackServer"
            );
        }

        if (effectiveCallback.equals(normalizeBlank(request.callback()))) {
            return request;
        }
        return request.toBuilder().callback(effectiveCallback).build();
    }

    private void handleCallbackReceipt(ReceiptCallbackResponse callbackResponse) {
        String receiptId = requireReceiptId(callbackResponse.receipt());
        PendingReceiptFuture<ReceiptCallbackResponse> pending = pendingCallbackFutures.get(receiptId);
        if (pending == null) {
            LOGGER.debug("Ignoring callback for receipt {} because no pending callback future is registered", receiptId);
            return;
        }
        pending.future().complete(callbackResponse);
    }

    private String requireAsyncReceipt(MessageResponse response) {
        if (response == null || isBlank(response.receipt())) {
            throw new PushoverClientException("Emergency notification response did not include a receipt");
        }
        return requireReceiptId(response.receipt());
    }

    private <T> void registerPendingFuture(
            ConcurrentMap<String, PendingReceiptFuture<T>> pendingFutures,
            String receiptId,
            CompletableFuture<T> future,
            Set<String> tags
    ) {
        PendingReceiptFuture<T> pendingFuture = new PendingReceiptFuture<>(future, tags);
        future.whenComplete((ignored, throwable) -> pendingFutures.remove(receiptId, pendingFuture));
        pendingFutures.put(receiptId, pendingFuture);
    }

    private void cancelPendingReceipt(String receiptId) {
        cancelPendingFuture(pendingPollFutures.remove(receiptId));
        cancelPendingFuture(pendingCallbackFutures.remove(receiptId));
    }

    private void cancelPendingReceiptsByTag(String tag) {
        cancelPendingReceiptsByTag(pendingPollFutures, tag);
        cancelPendingReceiptsByTag(pendingCallbackFutures, tag);
    }

    private static void cancelPendingFuture(PendingReceiptFuture<?> pendingFuture) {
        if (pendingFuture != null) {
            pendingFuture.future().cancel(false);
        }
    }

    private static void cancelPendingReceiptsByTag(
            ConcurrentMap<String, ? extends PendingReceiptFuture<?>> pendingFutures,
            String tag
    ) {
        for (Map.Entry<String, ? extends PendingReceiptFuture<?>> entry : pendingFutures.entrySet()) {
            if (entry.getValue().tags().contains(tag) && pendingFutures.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().future().cancel(false);
            }
        }
    }

    private static Set<String> parseTags(String tags) {
        if (isBlank(tags)) {
            return Collections.emptySet();
        }
        Set<String> values = new HashSet<>();
        for (String tag : tags.split(",")) {
            String normalized = normalizeBlank(tag);
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return Collections.unmodifiableSet(values);
    }

    private static boolean hasExpired(long startedAt, Duration expiration) {
        return System.nanoTime() - startedAt >= expiration.toNanos();
    }

    private static void sleep(Duration duration) throws InterruptedException {
        long millis = duration.toMillis();
        int nanos = duration.toNanosPart() % 1_000_000;
        Thread.sleep(millis, nanos);
    }

    /**
     * Encodes fields as application/x-www-form-urlencoded.
     *
     * @param fields request fields
     * @return encoded body text
     * @since 0.1.0
     */
    private static String encodeFormBody(Map<String, String> fields) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!isBlank(entry.getValue())) {
                pairs.add(encode(entry.getKey()) + "=" + encode(entry.getValue()));
            }
        }
        return String.join("&", pairs);
    }

    /**
     * Builds multipart content where each field is a form part and attachment is binary.
     *
     * @param fields scalar fields
     * @param attachment attachment metadata and bytes
     * @param boundary multipart boundary token
     * @return serialized multipart bytes
     * @throws PushoverClientException when multipart serialization fails
     * @since 0.1.0
     */
    private static byte[] multipartBody(Map<String, String> fields, Attachment attachment, String boundary) {
        String separator = "--" + boundary + "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            for (Map.Entry<String, String> field : fields.entrySet()) {
                if (isBlank(field.getValue())) {
                    continue;
                }
                // Pushover expects text parts before binary part in a standard multipart payload.
                out.write(separator.getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(field.getValue().getBytes(StandardCharsets.UTF_8));
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }

            // Attachment part name must be exactly "attachment" for multipart uploads.
            out.write(separator.getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"attachment\"; filename=\"" + attachment.fileName() + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + attachment.contentType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(attachment.data());
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));

            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException ex) {
            throw new PushoverClientException("Failed to build multipart request", ex);
        }
    }

    /**
     * Applies client-side validation for documented Pushover constraints.
     *
     * @param request message request to validate
     * @throws PushoverClientException when one or more constraints are violated
     * @since 0.1.0
     */
    private void validate(MessageRequest request) {
        Objects.requireNonNull(request, "request is required");

        if (isBlank(request.user()) || !IDENTIFIER_LIST.matcher(request.user()).matches()) {
            throw new PushoverClientException("user must be one or more 30-char identifiers separated by commas");
        }
        if (request.user().split(",").length > 50) {
            throw new PushoverClientException("user can contain at most 50 comma-separated identifiers");
        }
        if (isBlank(request.message())) {
            throw new PushoverClientException("message is required");
        }
        if (!isBlank(request.device()) && !DEVICE_LIST.matcher(request.device()).matches()) {
            throw new PushoverClientException("device must contain valid names separated by commas");
        }
        if (request.html() != null && request.monospace() != null) {
            throw new PushoverClientException("html and monospace cannot both be enabled");
        }

        if (request.priority() == MessagePriority.EMERGENCY) {
            // Emergency messages must include retry + expire per API contract.
            if (request.retry() == null || request.expire() == null) {
                throw new PushoverClientException("priority=EMERGENCY requires retry and expire");
            }
            if (request.retry() < 30) {
                throw new PushoverClientException("retry must be at least 30 seconds");
            }
            if (request.expire() <= 0 || request.expire() > 10800) {
                throw new PushoverClientException("expire must be between 1 and 10800 seconds");
            }
            if (request.ttl() != null) {
                throw new PushoverClientException("ttl is ignored for emergency priority and should not be sent");
            }
        }

        if (request.ttl() != null && request.ttl() <= 0) {
            throw new PushoverClientException("ttl must be positive");
        }

        if (!request.encrypted()) {
            if (request.message().length() > 1024) {
                throw new PushoverClientException("message length exceeds 1024 characters");
            }
            if (!isBlank(request.title()) && request.title().length() > 250) {
                throw new PushoverClientException("title length exceeds 250 characters");
            }
            if (!isBlank(request.url()) && request.url().length() > 512) {
                throw new PushoverClientException("url length exceeds 512 characters");
            }
            if (!isBlank(request.urlTitle()) && request.urlTitle().length() > 100) {
                throw new PushoverClientException("url_title length exceeds 100 characters");
            }
        }

        Attachment attachment = request.attachment();
        if (attachment != null) {
            // Pushover limit: 5,242,880 bytes (5 MiB).
            if (attachment.data().length > 5_242_880) {
                throw new PushoverClientException("attachment exceeds 5MB limit");
            }
            if (attachment.encoding() == AttachmentEncoding.BASE64 && isBlank(attachment.contentType())) {
                throw new PushoverClientException("base64 attachment requires content type");
            }
        }
    }

    /**
     * Adds a value only when non-blank.
     *
     * @param fields output map
     * @param key field name
     * @param value field value
     * @since 0.1.0
     */
    private static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (!isBlank(value)) {
            fields.put(key, value);
        }
    }

    /**
     * Ensures a required string is present.
     *
     * @param value input value
     * @param message exception message
     * @return original input when non-blank
     * @throws PushoverClientException when value is blank
     * @since 0.1.0
     */
    private static String requireNonBlank(String value, String message) {
        if (isBlank(value)) {
            throw new PushoverClientException(message);
        }
        return value;
    }

    private static String normalizeBlank(String value) {
        return isBlank(value) ? null : value.trim();
    }

    /**
     * Validates and normalizes a receipt id used in path parameters.
     *
     * @param receiptId candidate receipt id
     * @return normalized receipt id
     * @throws PushoverClientException when the receipt id is blank or malformed
     * @since 0.2.0
     */
    private static String requireReceiptId(String receiptId) {
        String normalized = requireNonBlank(receiptId, "receiptId is required").trim();
        if (!RECEIPT_ID.matcher(normalized).matches()) {
            throw new PushoverClientException("receiptId must be a 30-character alphanumeric identifier");
        }
        return normalized;
    }

    /**
     * Validates and normalizes a user/group identifier used by the validation endpoint.
     *
     * @param userOrGroup candidate user or group key
     * @return normalized user/group key
     * @throws PushoverClientException when the identifier is blank or malformed
     * @since 0.6.0
     */
    private static String requireUserOrGroup(String userOrGroup) {
        String normalized = requireNonBlank(userOrGroup, "userOrGroup is required").trim();
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new PushoverClientException("userOrGroup must be a 30-character alphanumeric identifier");
        }
        return normalized;
    }

    /**
     * Validates and normalizes an optional single device name used by the validation endpoint.
     *
     * @param device candidate device name, or null to omit device-specific validation
     * @return normalized device name, or null when not supplied
     * @throws PushoverClientException when the device is blank or malformed
     * @since 0.6.0
     */
    private static String requireSingleDeviceName(String device) {
        if (device == null) {
            return null;
        }
        String normalized = device.trim();
        if (normalized.isEmpty() || !DEVICE_NAME.matcher(normalized).matches()) {
            throw new PushoverClientException("device must be a single valid device name");
        }
        return normalized;
    }

    /**
     * Validates and normalizes a tag used in path parameters.
     *
     * @param tag candidate tag value
     * @return normalized tag
     * @throws PushoverClientException when the tag is blank
     * @since 0.2.0
     */
    private static String requireTag(String tag) {
        return requireNonBlank(tag, "tag is required").trim();
    }

    /**
     * Checks whether a string is null/blank.
     *
     * @param value input string
     * @return true when null or blank
     * @since 0.1.0
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Percent-encodes values for form body and query string usage.
     *
     * @param value unescaped input
     * @return URL-encoded string
     * @since 0.1.0
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Percent-encodes a single path segment for dynamic endpoint usage.
     *
     * @param value unescaped path segment
     * @return encoded path segment
     * @since 0.2.0
     */
    private static String encodePathSegment(String value) {
        return encode(value).replace("+", "%20");
    }

    @FunctionalInterface
    private interface InterruptibleTask {
        void run() throws Exception;
    }

    private record PendingReceiptFuture<T>(CompletableFuture<T> future, Set<String> tags) {
    }
}

