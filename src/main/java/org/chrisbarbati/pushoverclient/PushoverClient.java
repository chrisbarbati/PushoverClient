package org.chrisbarbati.pushoverclient;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.chrisbarbati.pushoverclient.model.SoundsResponse;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Minimal Java client for core Pushover REST endpoints.
 *
 * @apiNote This client currently targets messages, sounds, and app limits endpoints.
 * @implNote Validation follows documented Pushover API constraints before HTTP submission.
 * @since 0.1.0
 * @author Christian Barbati
 */
public class PushoverClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushoverClient.class);

    // Pushover user/group keys are 30 alphanumeric chars; up to 50 can be sent comma-separated.
    private static final Pattern IDENTIFIER_LIST = Pattern.compile("^[A-Za-z0-9]{30}(,[A-Za-z0-9]{30}){0,49}$");
    // Device names support [A-Za-z0-9_-] and max length 25 per documented API limits.
    private static final Pattern DEVICE_LIST = Pattern.compile("^[A-Za-z0-9_-]{1,25}(,[A-Za-z0-9_-]{1,25})*$");

    private final String appToken;
    private final String encryptionKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final E2eeEncryptor encryptor;
    private final String baseUrl;

    /**
     * Creates a client without end-to-end encryption.
     *
     * @param appToken application API token
     * @since 0.1.0
     * @author Christian Barbati
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
     * @author Christian Barbati
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
     * @author Christian Barbati
     */
    public PushoverClient(String appToken, String encryptionKey, HttpClient httpClient, String baseUrl) {
        this.appToken = requireNonBlank(appToken, "appToken is required");
        this.encryptionKey = isBlank(encryptionKey) ? null : encryptionKey;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
        this.baseUrl = requireNonBlank(baseUrl, "baseUrl is required");
        this.objectMapper = new ObjectMapper();
        this.encryptor = new E2eeEncryptor();
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
     * @author Christian Barbati
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

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return parseResponse(response, MessageResponse.class);
    }

    /**
     * Fetches available sounds from {@code /1/sounds.json}.
     *
     * @return parsed sounds response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     * @throws PushoverApiException when Pushover returns a non-success response
     * @since 0.1.0
     * @author Christian Barbati
     */
    public SoundsResponse getSounds() throws IOException, InterruptedException {
        LOGGER.debug("Fetching sounds from endpoint={}", PushoverEndpoints.SOUNDS);
        HttpRequest request = HttpRequest.newBuilder(buildUri(PushoverEndpoints.SOUNDS + "?token=" + encode(appToken)))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return parseResponse(response, SoundsResponse.class);
    }

    /**
     * Fetches monthly quota data from {@code /1/apps/limits.json}.
     *
     * @return parsed limits response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     * @throws PushoverApiException when Pushover returns a non-success response
     * @since 0.1.0
     * @author Christian Barbati
     */
    public LimitsResponse getLimits() throws IOException, InterruptedException {
        LOGGER.debug("Fetching limits from endpoint={}", PushoverEndpoints.LIMITS);
        HttpRequest request = HttpRequest.newBuilder(buildUri(PushoverEndpoints.LIMITS + "?token=" + encode(appToken)))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return parseResponse(response, LimitsResponse.class);
    }

    /**
     * Builds API fields for a message request.
     *
     * @param request message request
     * @return ordered key/value map for request encoding
     * @throws PushoverClientException when encrypted mode is requested without a key
     * @since 0.1.0
     * @author Christian Barbati
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
     * @author Christian Barbati
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
     * @author Christian Barbati
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
     * @author Christian Barbati
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
     * @author Christian Barbati
     */
    private URI buildUri(String path) {
        return URI.create(baseUrl + path);
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
     * @author Christian Barbati
     */
    private <T> T parseResponse(HttpResponse<String> response, Class<T> responseType) {
        try {
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                T value = objectMapper.readValue(response.body(), responseType);
                // Messages endpoint uses status=1 in JSON body to indicate acceptance.
                if (value instanceof MessageResponse msg && msg.status() != 1) {
                    LOGGER.warn(
                            "API returned non-success message status despite HTTP success: httpStatus={}, requestId={}",
                            response.statusCode(),
                            msg.request()
                    );
                    throw new PushoverApiException("Pushover API reported failure", response.statusCode(), msg.request(), List.of("status != 1"));
                }

                if (LOGGER.isDebugEnabled()) {
                    String requestId = null;
                    if (value instanceof MessageResponse msg) {
                        requestId = msg.request();
                    } else if (value instanceof SoundsResponse sounds) {
                        requestId = sounds.request();
                    } else if (value instanceof LimitsResponse limits) {
                        requestId = limits.request();
                    }
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
     * Encodes fields as application/x-www-form-urlencoded.
     *
     * @param fields request fields
     * @return encoded body text
     * @since 0.1.0
     * @author Christian Barbati
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
     * @author Christian Barbati
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
     * @author Christian Barbati
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
     * @author Christian Barbati
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
     * @author Christian Barbati
     */
    private static String requireNonBlank(String value, String message) {
        if (isBlank(value)) {
            throw new PushoverClientException(message);
        }
        return value;
    }

    /**
     * Checks whether a string is null/blank.
     *
     * @param value input string
     * @return true when null or blank
     * @since 0.1.0
     * @author Christian Barbati
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
     * @author Christian Barbati
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
