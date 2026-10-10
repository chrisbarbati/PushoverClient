package dev.chrisbarbati.pushoverclient.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response payload for monthly usage limits.
 *
 * @param status Pushover status value (1 indicates success)
 * @param request unique request id for support/tracing
 * @param limit monthly message limit for the application owner
 * @param remaining remaining messages for the current month
 * @param reset unix timestamp for next monthly reset
 * @since 0.1.0
 * @author Christian Barbati
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LimitsResponse(
        int status,
        String request,
        int limit,
        int remaining,
        long reset
) {
}

