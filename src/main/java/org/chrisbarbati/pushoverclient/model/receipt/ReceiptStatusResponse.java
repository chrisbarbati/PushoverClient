package org.chrisbarbati.pushoverclient.model.receipt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload for receipt status lookups.
 *
 * @param status Pushover status value (1 indicates success)
 * @param request unique request id for support/tracing
 * @param receipt receipt id associated with the emergency notification when present
 * @param acknowledged 1 when the notification has been acknowledged, otherwise 0
 * @param acknowledgedAt unix timestamp for acknowledgement time when present
 * @param acknowledgedBy user who acknowledged the notification when present
 * @param acknowledgedByDevice device name that acknowledged the notification when present
 * @param lastDeliveredAt unix timestamp of the last delivery attempt when present
 * @param expired 1 when the receipt has expired, otherwise 0
 * @param expiresAt unix timestamp when the receipt will expire when present
 * @param calledBack 1 when the callback URL has been invoked, otherwise 0
 * @param calledBackAt unix timestamp when the callback URL was invoked when present
 * @since 0.2.0
 * @author Christian Barbati
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReceiptStatusResponse(
        int status,
        String request,
        String receipt,
        int acknowledged,
        @JsonProperty("acknowledged_at") Long acknowledgedAt,
        @JsonProperty("acknowledged_by") String acknowledgedBy,
        @JsonProperty("acknowledged_by_device") String acknowledgedByDevice,
        @JsonProperty("last_delivered_at") Long lastDeliveredAt,
        int expired,
        @JsonProperty("expires_at") Long expiresAt,
        @JsonProperty("called_back") int calledBack,
        @JsonProperty("called_back_at") Long calledBackAt
) {
}

