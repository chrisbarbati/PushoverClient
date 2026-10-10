package dev.chrisbarbati.pushoverclient;

import dev.chrisbarbati.pushoverclient.exception.PushoverClientException;
import dev.chrisbarbati.pushoverclient.model.attachment.Attachment;
import dev.chrisbarbati.pushoverclient.model.attachment.AttachmentEncoding;
import dev.chrisbarbati.pushoverclient.model.glance.GlanceRequest;
import dev.chrisbarbati.pushoverclient.model.message.MessagePriority;
import dev.chrisbarbati.pushoverclient.model.message.MessageRequest;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Internal validation for documented Pushover API constraints.
 *
 * <p>This class is package-private: validation failures surface through
 * {@link PushoverClient} public methods as {@link PushoverClientException}.</p>
 *
 * @since 0.7.0
 */
final class RequestValidator {

    // Pushover user/group keys are 30 alphanumeric chars.
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9]{30}$");
    // Pushover user/group keys support up to 50 comma-separated identifiers for message sending.
    private static final Pattern IDENTIFIER_LIST = Pattern.compile("^[A-Za-z0-9]{30}(,[A-Za-z0-9]{30}){0,49}$");
    // Emergency receipt ids are 30 alphanumeric chars in API responses and follow-up endpoints.
    private static final Pattern RECEIPT_ID = Pattern.compile("^[A-Za-z0-9]{30}$");
    // Device names support [A-Za-z0-9_-] and max length 25 per documented API limits.
    private static final Pattern DEVICE_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,25}$");
    private static final Pattern DEVICE_LIST = Pattern.compile("^[A-Za-z0-9_-]{1,25}(,[A-Za-z0-9_-]{1,25})*$");

    /**
     * Applies client-side validation for documented Pushover constraints.
     *
     * @param request message request to validate
     * @throws PushoverClientException when one or more constraints are violated
     */
    void validate(MessageRequest request) {
        Objects.requireNonNull(request, "request is required");

        if (PushoverStrings.isBlank(request.user()) || !IDENTIFIER_LIST.matcher(request.user()).matches()) {
            throw new PushoverClientException("user must be one or more 30-char identifiers separated by commas");
        }
        if (request.user().split(",").length > 50) {
            throw new PushoverClientException("user can contain at most 50 comma-separated identifiers");
        }
        if (PushoverStrings.isBlank(request.message())) {
            throw new PushoverClientException("message is required");
        }
        if (!PushoverStrings.isBlank(request.device()) && !DEVICE_LIST.matcher(request.device()).matches()) {
            throw new PushoverClientException("device must contain valid names separated by commas");
        }
        if (request.html() != null && request.monospace() != null) {
            throw new PushoverClientException("html and monospace cannot both be enabled");
        }

        if (request.priority() == MessagePriority.EMERGENCY) {
            // Emergency messages must include retry + expire per API contract.
            if (request.retry() == null || request.expire() == null) {
                throw new PushoverClientException("priority=EMERGENCY requires retry and expire");
            }
            if (request.retry() < 30) {
                throw new PushoverClientException("retry must be at least 30 seconds");
            }
            if (request.expire() <= 0 || request.expire() > 10800) {
                throw new PushoverClientException("expire must be between 1 and 10800 seconds");
            }
            if (request.ttl() != null) {
                throw new PushoverClientException("ttl is ignored for emergency priority and should not be sent");
            }
        }

        if (request.ttl() != null && request.ttl() <= 0) {
            throw new PushoverClientException("ttl must be positive");
        }

        if (!request.encrypted()) {
            if (request.message().length() > 1024) {
                throw new PushoverClientException("message length exceeds 1024 characters");
            }
            if (!PushoverStrings.isBlank(request.title()) && request.title().length() > 250) {
                throw new PushoverClientException("title length exceeds 250 characters");
            }
            if (!PushoverStrings.isBlank(request.url()) && request.url().length() > 512) {
                throw new PushoverClientException("url length exceeds 512 characters");
            }
            if (!PushoverStrings.isBlank(request.urlTitle()) && request.urlTitle().length() > 100) {
                throw new PushoverClientException("url_title length exceeds 100 characters");
            }
        }

        Attachment attachment = request.attachment();
        if (attachment != null) {
            // Pushover limit: 5,242,880 bytes (5 MiB).
            if (attachment.data().length > 5_242_880) {
                throw new PushoverClientException("attachment exceeds 5MB limit");
            }
            if (attachment.encoding() == AttachmentEncoding.BASE64 && PushoverStrings.isBlank(attachment.contentType())) {
                throw new PushoverClientException("base64 attachment requires content type");
            }
        }
    }

    /**
     * Validates and normalizes a receipt id used in path parameters.
     *
     * @param receiptId candidate receipt id
     * @return normalized receipt id
     * @throws PushoverClientException when the receipt id is blank or malformed
     */
    String requireReceiptId(String receiptId) {
        String normalized = PushoverStrings.requireNonBlank(receiptId, "receiptId is required").trim();
        if (!RECEIPT_ID.matcher(normalized).matches()) {
            throw new PushoverClientException("receiptId must be a 30-character alphanumeric identifier");
        }
        return normalized;
    }

    /**
     * Applies client-side validation for a glance (widget) update.
     *
     * @param request glance request to validate
     * @throws PushoverClientException when one or more constraints are violated
     * @since 0.9.0
     */
    void validateGlance(GlanceRequest request) {
        Objects.requireNonNull(request, "request is required");

        if (PushoverStrings.isBlank(request.user()) || !IDENTIFIER.matcher(request.user()).matches()) {
            throw new PushoverClientException("glance user must be a 30-character alphanumeric identifier");
        }
        if (!PushoverStrings.isBlank(request.device()) && !DEVICE_NAME.matcher(request.device()).matches()) {
            throw new PushoverClientException("glance device must be a single valid device name");
        }
        if (!hasGlanceDataField(request)) {
            throw new PushoverClientException("glance requires at least one of title, text, subtext, count, or percent");
        }
        requireGlanceTextLength("title", request.title());
        requireGlanceTextLength("text", request.text());
        requireGlanceTextLength("subtext", request.subtext());
        if (request.percent() != null && (request.percent() < 0 || request.percent() > 100)) {
            throw new PushoverClientException("percent must be between 0 and 100 inclusive");
        }
    }

    private static boolean hasGlanceDataField(GlanceRequest request) {
        return !PushoverStrings.isBlank(request.title())
                || !PushoverStrings.isBlank(request.text())
                || !PushoverStrings.isBlank(request.subtext())
                || request.count() != null
                || request.percent() != null;
    }

    private static void requireGlanceTextLength(String fieldName, String value) {
        if (!PushoverStrings.isBlank(value) && value.length() > 100) {
            throw new PushoverClientException(fieldName + " exceeds 100 characters");
        }
    }

    /**
     * Validates and normalizes a user/group identifier used by the validation endpoint.
     *
     * @param userOrGroup candidate user or group key
     * @return normalized user/group key
     * @throws PushoverClientException when the identifier is blank or malformed
     */
    String requireUserOrGroup(String userOrGroup) {
        String normalized = PushoverStrings.requireNonBlank(userOrGroup, "userOrGroup is required").trim();
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new PushoverClientException("userOrGroup must be a 30-character alphanumeric identifier");
        }
        return normalized;
    }

    /**
     * Validates and normalizes an optional single device name used by the validation endpoint.
     *
     * @param device candidate device name, or null to omit device-specific validation
     * @return normalized device name, or null when not supplied
     * @throws PushoverClientException when the device is blank or malformed
     */
    String requireSingleDeviceName(String device) {
        if (device == null) {
            return null;
        }
        String normalized = device.trim();
        if (normalized.isEmpty() || !DEVICE_NAME.matcher(normalized).matches()) {
            throw new PushoverClientException("device must be a single valid device name");
        }
        return normalized;
    }

    /**
     * Validates and normalizes a tag used in path parameters.
     *
     * @param tag candidate tag value
     * @return normalized tag
     * @throws PushoverClientException when the tag is blank
     */
    String requireTag(String tag) {
        return PushoverStrings.requireNonBlank(tag, "tag is required").trim();
    }
}