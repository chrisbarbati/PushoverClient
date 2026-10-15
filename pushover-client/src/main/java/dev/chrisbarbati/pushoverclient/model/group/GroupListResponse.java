package dev.chrisbarbati.pushoverclient.model.group;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dev.chrisbarbati.pushoverclient.model.PushoverResponse;

import java.util.List;

/**
 * Response payload for listing delivery groups on an account.
 *
 * @param status Pushover status value (1 indicates success)
 * @param request unique request id for support/tracing
 * @param groups delivery groups with their keys and names
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupListResponse(
        int status,
        String request,
        List<GroupInfo> groups
) implements PushoverResponse {
}
