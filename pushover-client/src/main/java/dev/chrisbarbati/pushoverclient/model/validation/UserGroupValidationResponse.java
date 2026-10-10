package dev.chrisbarbati.pushoverclient.model.validation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response payload for the user/group validation endpoint.
 *
 * @param status Pushover status value (1 indicates success)
 * @param request unique request id for support/tracing
 * @param devices active device names available for the validated account when present
 * @param licenses licensed platform names for the validated account when present
 * @since 0.6.0
 * @author Christian Barbati
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserGroupValidationResponse(
        int status,
        String request,
        List<String> devices,
        List<String> licenses
) {
}

