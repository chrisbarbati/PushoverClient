package org.chrisbarbati.pushoverclient.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Response payload for the sounds endpoint.
 *
 * @param status Pushover status value (1 indicates success)
 * @param request unique request id for support/tracing
 * @param sounds map of sound key to display name
 * @since 0.1.0
 * @author Christian Barbati
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SoundsResponse(
        int status,
        String request,
        Map<String, String> sounds
) {
}

