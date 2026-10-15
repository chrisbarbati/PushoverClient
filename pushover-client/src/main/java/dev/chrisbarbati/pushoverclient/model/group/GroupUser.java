package dev.chrisbarbati.pushoverclient.model.group;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Member entry for a delivery group returned by the group details endpoint.
 *
 * @param user the user key
 * @param device the device name the membership is restricted to when present, otherwise null
 * @param memo free-text memo associated with the member when present
 * @param disabled true when the member is temporarily disabled
 * @param name member name when present (Team-owned groups only)
 * @param email member email when present (Team-owned groups only)
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupUser(
        String user,
        String device,
        String memo,
        boolean disabled,
        String name,
        String email
) {
}
