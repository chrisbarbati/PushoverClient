package dev.chrisbarbati.pushoverclient.model.subscription;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.chrisbarbati.pushoverclient.model.PushoverResponse;

/**
 * Response payload for migrating an existing user key to a subscription.
 *
 * @param status Pushover status value (1 indicates success)
 * @param subscribedUserKey the new subscription user key to store in place of the original key
 * @param request unique request id for support/tracing
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubscriptionMigrationResponse(
        int status,
        @JsonProperty("subscribed_user_key") String subscribedUserKey,
        String request
) implements PushoverResponse {
}
