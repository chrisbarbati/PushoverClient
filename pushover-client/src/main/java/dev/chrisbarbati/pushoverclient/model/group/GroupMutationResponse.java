package dev.chrisbarbati.pushoverclient.model.group;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dev.chrisbarbati.pushoverclient.model.PushoverResponse;

/**
 * Response payload for delivery group mutation operations (add, remove, disable, enable, and rename).
 *
 * @param status Pushover status value (1 indicates success)
 * @param request unique request id for support/tracing
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupMutationResponse(
        int status,
        String request
) implements PushoverResponse {
}
