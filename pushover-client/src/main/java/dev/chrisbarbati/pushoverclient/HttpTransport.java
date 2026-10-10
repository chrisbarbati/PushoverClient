package dev.chrisbarbati.pushoverclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chrisbarbati.pushoverclient.exception.PushoverApiException;
import dev.chrisbarbati.pushoverclient.exception.PushoverClientException;
import dev.chrisbarbati.pushoverclient.model.ApiErrorResponse;
import dev.chrisbarbati.pushoverclient.model.LimitsResponse;
import dev.chrisbarbati.pushoverclient.model.SoundsResponse;
import dev.chrisbarbati.pushoverclient.model.attachment.Attachment;
import dev.chrisbarbati.pushoverclient.model.glance.GlanceResponse;
import dev.chrisbarbati.pushoverclient.model.message.MessageResponse;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCancellationResponse;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptStatusResponse;
import dev.chrisbarbati.pushoverclient.model.validation.UserGroupValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal HTTP transport for the Pushover REST API.
 *
 * <p>Owns the {@link HttpClient}, JSON mapping, request building, and
 * success/error response handling. This class is package-private: all public
 * entry points remain on {@link PushoverClient}.</p>
 *
 * @since 0.7.0
 */
final class HttpTransport {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpTransport.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String appToken;
    private final String baseUrl;

    /**
     * Creates a transport for one API base URL and application token.
     *
     * @param appToken application API token
     * @param httpClient HTTP transport implementation
     * @param baseUrl API base URL
     */
    HttpTransport(String appToken, HttpClient httpClient, String baseUrl) {
        this.appToken = PushoverStrings.requireNonBlank(appToken, "appToken is required");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
        this.baseUrl = PushoverStrings.requireNonBlank(baseUrl, "baseUrl is required");
        this.objectMapper = new ObjectMapper();
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
     */
    <T> T getJson(String path, Class<T> responseType) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path + "?token=" + PushoverStrings.encode(appToken)))
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
     */
    <T> T postForm(String path, Map<String, String> fields, Class<T> responseType) throws IOException, InterruptedException {
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
     */
    <T> T send(HttpRequest httpRequest, Class<T> responseType) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return parseResponse(response, responseType);
    }

    /**
     * Creates a form-encoded POST request.
     *
     * @param path endpoint path
     * @param fields request key/value fields
     * @return configured HTTP request
     */
    HttpRequest buildFormRequest(String path, Map<String, String> fields) {
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
     */
    HttpRequest buildMultipartRequest(String path, Map<String, String> fields, Attachment attachment) {
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
     */
    private URI buildUri(String path) {
        return URI.create(baseUrl + path);
    }

    /**
     * Parses successful responses and maps error payloads to {@link PushoverApiException}.
     *
     * <p>When an error response body is not valid JSON (for example a proxy
     * generated HTML error page), the HTTP status is still preserved in the
     * resulting {@link PushoverApiException} instead of being lost to a generic
     * client parsing failure.</p>
     *
     * @param response raw HTTP response
     * @param responseType expected success payload type
     * @param <T> success payload type
     * @return parsed success object
     * @throws PushoverApiException when API returns an error response body
     * @throws PushoverClientException when the success response body cannot be parsed
     */
    private <T> T parseResponse(HttpResponse<String> response, Class<T> responseType) {
        String body = response.body();
        try {
            int httpStatus = response.statusCode();
            if (httpStatus >= 200 && httpStatus < 300) {
                T value = objectMapper.readValue(body, responseType);
                Integer status = extractStatus(value);
                String requestId = extractRequestId(value);
                if (status != null && status != 1) {
                    ApiErrorResponse error = parseErrorBody(body);
                    List<String> errors = error == null || error.errors() == null ? List.of() : error.errors();
                    LOGGER.warn(
                            "API returned non-success status despite HTTP success: responseType={}, httpStatus={}, requestId={}",
                            responseType.getSimpleName(),
                            httpStatus,
                            requestId
                    );
                    throw new PushoverApiException(
                            "Pushover API reported failure",
                            httpStatus,
                            requestId,
                            errors.isEmpty() ? List.of("status != 1") : errors
                    );
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("API call succeeded: responseType={}, httpStatus={}, requestId={}",
                            responseType.getSimpleName(), httpStatus, requestId);
                }
                return value;
            }

            ApiErrorResponse errorResponse = parseErrorBody(body);
            LOGGER.warn(
                    "API request failed: httpStatus={}, requestId={}, errorCount={}",
                    httpStatus,
                    errorResponse == null ? null : errorResponse.request(),
                    errorResponse == null || errorResponse.errors() == null ? 0 : errorResponse.errors().size()
            );

            throw new PushoverApiException(
                    "Pushover API request failed",
                    httpStatus,
                    errorResponse == null ? null : errorResponse.request(),
                    errorResponse == null || errorResponse.errors() == null ? List.of() : errorResponse.errors()
            );
        } catch (IOException ex) {
            LOGGER.warn("Failed to parse API response: responseType={}, httpStatus={}",
                    responseType.getSimpleName(), response.statusCode());
            throw new PushoverClientException("Failed to parse API response", ex);
        }
    }

    /**
     * Parses a raw body into an API error payload, tolerating non-JSON bodies.
     *
     * @param body raw response body
     * @return parsed error payload, or null when the body is blank or not JSON
     */
    private ApiErrorResponse parseErrorBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, ApiErrorResponse.class);
        } catch (IOException ignored) {
            return null;
        }
    }

    /**
     * Extracts the common API status field from known success payloads.
     *
     * @param value parsed success payload
     * @return status when available, otherwise null
     */
    private static Integer extractStatus(Object value) {
        if (value instanceof MessageResponse messageResponse) {
            return messageResponse.status();
        }
        if (value instanceof GlanceResponse glanceResponse) {
            return glanceResponse.status();
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
     */
    private static String extractRequestId(Object value) {
        if (value instanceof MessageResponse messageResponse) {
            return messageResponse.request();
        }
        if (value instanceof GlanceResponse glanceResponse) {
            return glanceResponse.request();
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

    /**
     * Encodes fields as application/x-www-form-urlencoded.
     *
     * @param fields request fields
     * @return encoded body text
     */
    private static String encodeFormBody(Map<String, String> fields) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (PushoverStrings.isBlank(entry.getValue())) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(PushoverStrings.encode(entry.getKey()))
                    .append('=')
                    .append(PushoverStrings.encode(entry.getValue()));
        }
        return builder.toString();
    }

    /**
     * Builds multipart content where each field is a form part and attachment is binary.
     *
     * @param fields scalar fields
     * @param attachment attachment metadata and bytes
     * @param boundary multipart boundary token
     * @return serialized multipart bytes
     * @throws PushoverClientException when multipart serialization fails
     */
    private static byte[] multipartBody(Map<String, String> fields, Attachment attachment, String boundary) {
        String separator = "--" + boundary + "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            for (Map.Entry<String, String> field : fields.entrySet()) {
                if (PushoverStrings.isBlank(field.getValue())) {
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
}