package dev.chrisbarbati.pushoverclient.model.group;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dev.chrisbarbati.pushoverclient.model.PushoverResponse;

import java.util.List;

/**
 * Response payload for retrieving a single delivery group's details and members.
 *
 * @param status Pushover status value (1 indicates success)
 * @param request unique request id for support/tracing
 * @param name the group name
 * @param users the group's members
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupDetailsResponse(
        int status,
        String request,
        String name,
        List<GroupUser> users
) implements PushoverResponse {
}
