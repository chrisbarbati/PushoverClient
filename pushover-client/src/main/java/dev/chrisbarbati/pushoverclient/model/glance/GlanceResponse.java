package dev.chrisbarbati.pushoverclient.model.glance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dev.chrisbarbati.pushoverclient.model.PushoverResponse;

/**
 * Success response payload for the glances endpoint.
 *
 * @param status Pushover status value (1 indicates success)
 * @param request unique request id for support/tracing
 * @since 0.9.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GlanceResponse(
        int status,
        String request
) implements PushoverResponse {
}