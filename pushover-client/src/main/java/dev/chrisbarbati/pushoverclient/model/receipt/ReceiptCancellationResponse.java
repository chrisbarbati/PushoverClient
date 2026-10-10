package dev.chrisbarbati.pushoverclient.model.receipt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response payload for receipt cancellation operations.
 *
 * @param status Pushover status value (1 indicates success)
 * @param request unique request id for support/tracing
 * @since 0.2.0
 * @author Christian Barbati
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReceiptCancellationResponse(
		int status,
		String request
) {
}

