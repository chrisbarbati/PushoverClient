package org.chrisbarbati.pushoverclient.model.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Success response payload for the messages endpoint.
 *
 * @param status Pushover status value (1 indicates success)
 * @param request unique request id for support/tracing
 * @param receipt receipt id returned for emergency notifications
 * @since 0.1.0
 * @author Christian Barbati
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageResponse(
        int status,
        String request,
        String receipt
) {
}

