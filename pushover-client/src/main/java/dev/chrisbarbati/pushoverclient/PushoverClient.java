package dev.chrisbarbati.pushoverclient;

import dev.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import dev.chrisbarbati.pushoverclient.callback.CallbackServer;
import dev.chrisbarbati.pushoverclient.exception.PushoverApiException;
import dev.chrisbarbati.pushoverclient.exception.PushoverClientException;
import dev.chrisbarbati.pushoverclient.model.LimitsResponse;
import dev.chrisbarbati.pushoverclient.model.SoundsResponse;
import dev.chrisbarbati.pushoverclient.model.attachment.Attachment;
import dev.chrisbarbati.pushoverclient.model.attachment.AttachmentEncoding;
import dev.chrisbarbati.pushoverclient.model.message.MessagePriority;
import dev.chrisbarbati.pushoverclient.model.message.MessageRequest;
import dev.chrisbarbati.pushoverclient.model.message.MessageResponse;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCancellationResponse;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptStatusResponse;
import dev.chrisbarbati.pushoverclient.model.validation.UserGroupValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Minimal Java client for core Pushover REST endpoints.
 *
 * <p>This class is the single public entry point. HTTP transport, request
 * validation, field encoding, and the async receipt lifecycle are handled by
 * package-private collaborators, so the public API surface stays in one place.</p>
 *
 * @apiNote This client currently targets messages, user/group validation, receipts, sounds, and app limits endpoints.
 * @implNote Validation follows documented Pushover API constraints before HTTP submission.
 * @since 0.1.0
 * @author Christian Barbati
 */
public class PushoverClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushoverClient.class);

    private final HttpTransport transport;
    private final RequestValidator validator;
    private final MessageFieldsBuilder fieldsBuilder;
    private final ReceiptLifecycle receiptLifecycle;
    private final AsyncReceiptProperties asyncReceiptProperties;

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
        this.transport = new HttpTransport(appToken, httpClient, baseUrl);
        this.validator = new RequestValidator();
        this.fieldsBuilder = new MessageFieldsBuilder(appToken, encryptionKey);
        this.asyncReceiptProperties = asyncReceiptProperties == null
                ? AsyncReceiptProperties.defaults()
                : asyncReceiptProperties;
        this.receiptLifecycle = new ReceiptLifecycle(this.transport, this.validator, this.asyncReceiptProperties, callbackServer);
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
        validator.validate(request);
        Map<String, String> fields = fieldsBuilder.buildMessageFields(request);

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
            httpRequest = transport.buildMultipartRequest(PushoverEndpoints.SEND_MESSAGE, fields, attachment);
        } else {
            // Default to standard form encoding, including attachment_base64 mode.
            httpRequest = transport.buildFormRequest(PushoverEndpoints.SEND_MESSAGE, fields);
        }

        return transport.send(httpRequest, MessageResponse.class);
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
        Duration expiration = ReceiptLifecycle.resolveFutureExpiration(
                futureExpiration,
                asyncReceiptProperties.pollFutureExpiration(),
                AsyncReceiptProperties.POLL_FUTURE_EXPIRATION_PROPERTY
        );
        return receiptLifecycle.awaitPollAcknowledgement(emergencyRequest, executor, expiration, this::sendMessage);
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
        Duration expiration = ReceiptLifecycle.resolveFutureExpiration(
                futureExpiration,
                asyncReceiptProperties.callbackFutureExpiration(),
                AsyncReceiptProperties.CALLBACK_FUTURE_EXPIRATION_PROPERTY
        );
        return receiptLifecycle.awaitCallbackAcknowledgement(emergencyRequest, executor, expiration, this::sendMessage);
    }

    /**
     * Accepts a parsed callback payload and completes any matching callback-based futures managed by this client.
     *
     * @param callbackResponse parsed callback payload delivered by a host-managed adapter
     * @since 0.4.0
     */
    public void acceptCallbackReceipt(ReceiptCallbackResponse callbackResponse) {
        receiptLifecycle.handleCallbackReceipt(Objects.requireNonNull(callbackResponse, "callbackResponse is required"));
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
        String normalizedIdentifier = validator.requireUserOrGroup(userOrGroup);
        String normalizedDevice = validator.requireSingleDeviceName(device);
        LOGGER.debug(
                "Validating user/group target: endpoint={}, device={}",
                PushoverEndpoints.VALIDATE_USER_OR_GROUP,
                normalizedDevice
        );

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user", normalizedIdentifier);
        PushoverStrings.putIfPresent(fields, "device", normalizedDevice);
        return transport.postForm(PushoverEndpoints.VALIDATE_USER_OR_GROUP, fields, UserGroupValidationResponse.class);
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
        return transport.getJson(PushoverEndpoints.SOUNDS, SoundsResponse.class);
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
        return transport.getJson(PushoverEndpoints.LIMITS, LimitsResponse.class);
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
        String normalizedReceiptId = validator.requireReceiptId(receiptId);
        LOGGER.debug("Fetching receipt status: endpoint={}, receiptId={}", PushoverEndpoints.RECEIPTS, normalizedReceiptId);
        return transport.getJson(
                PushoverEndpoints.RECEIPTS + "/" + PushoverStrings.encodePathSegment(normalizedReceiptId) + ".json",
                ReceiptStatusResponse.class
        );
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
        String normalizedReceiptId = validator.requireReceiptId(receiptId);
        LOGGER.debug("Cancelling receipt: endpoint={}, receiptId={}", PushoverEndpoints.RECEIPTS, normalizedReceiptId);
        ReceiptCancellationResponse response = transport.postForm(
                PushoverEndpoints.RECEIPTS + "/" + PushoverStrings.encodePathSegment(normalizedReceiptId) + "/cancel.json",
                Map.of(),
                ReceiptCancellationResponse.class
        );
        receiptLifecycle.cancelReceipt(normalizedReceiptId);
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
        String normalizedTag = validator.requireTag(tag);
        LOGGER.debug("Cancelling receipts by tag: endpoint={}, tag={}", PushoverEndpoints.CANCEL_BY_TAG, normalizedTag);
        ReceiptCancellationResponse response = transport.postForm(
                PushoverEndpoints.CANCEL_BY_TAG + "/" + PushoverStrings.encodePathSegment(normalizedTag) + ".json",
                Map.of(),
                ReceiptCancellationResponse.class
        );
        receiptLifecycle.cancelByTag(normalizedTag);
        return response;
    }

    private MessageRequest requireEmergencyRequest(MessageRequest request, String methodName) {
        Objects.requireNonNull(request, "request is required");
        if (request.priority() != MessagePriority.EMERGENCY) {
            throw new PushoverClientException(methodName + " requires priority=EMERGENCY");
        }
        validator.validate(request);
        return request;
    }
}