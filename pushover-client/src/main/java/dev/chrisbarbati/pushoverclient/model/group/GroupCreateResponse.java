package dev.chrisbarbati.pushoverclient.model.group;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dev.chrisbarbati.pushoverclient.model.PushoverResponse;

/**
 * Response payload for creating a delivery group.
 *
 * @param status Pushover status value (1 indicates success)
 * @param request unique request id for support/tracing
 * @param group the newly created group key
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupCreateResponse(
        int status,
        String request,
        String group
) implements PushoverResponse {
}
