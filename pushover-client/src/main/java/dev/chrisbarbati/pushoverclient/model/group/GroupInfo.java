package dev.chrisbarbati.pushoverclient.model.group;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Summary entry for a delivery group returned by the group listing endpoint.
 *
 * @param group the group key
 * @param name the group name
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupInfo(
        String group,
        String name
) {
}
