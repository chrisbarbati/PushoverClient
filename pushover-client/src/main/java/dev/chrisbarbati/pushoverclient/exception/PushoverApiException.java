package dev.chrisbarbati.pushoverclient.exception;

import java.util.List;

/**
 * Exception representing an HTTP-level API failure returned by Pushover.
 *
 * @since 0.1.0
 * @author Christian Barbati
 */
public class PushoverApiException extends PushoverClientException {
    private final int httpStatus;
    private final String requestId;
    private final List<String> errors;

    /**
     * Creates an exception that includes Pushover request metadata.
     *
     * @param message high-level error summary
     * @param httpStatus HTTP status code from the API response
     * @param requestId Pushover request identifier used for support/tracing
     * @param errors API validation errors, if provided
     * @since 0.1.0
     */
    public PushoverApiException(String message, int httpStatus, String requestId, List<String> errors) {
        super(message);
        this.httpStatus = httpStatus;
        this.requestId = requestId;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * @return HTTP status code from Pushover
     * @since 0.1.0
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * @return unique Pushover request identifier
     * @since 0.1.0
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * @return immutable list of API error messages
     * @since 0.1.0
     */
    public List<String> getErrors() {
        return errors;
    }
}


