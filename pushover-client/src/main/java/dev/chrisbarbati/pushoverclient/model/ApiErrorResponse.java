package dev.chrisbarbati.pushoverclient.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Error payload returned by Pushover for invalid requests.
 *
 * @param user user identifier status when present
 * @param errors list of API validation errors
 * @param status Pushover status value (typically 0 on failures)
 * @param request unique request id for support/tracing
 * @since 0.1.0
 * @author Christian Barbati
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiErrorResponse(
        String user,
        List<String> errors,
        int status,
        String request
) {
}

