package dev.chrisbarbati.pushoverclient;

import dev.chrisbarbati.pushoverclient.crypto.E2eeEncryptor;
import dev.chrisbarbati.pushoverclient.exception.PushoverClientException;
import dev.chrisbarbati.pushoverclient.model.attachment.Attachment;
import dev.chrisbarbati.pushoverclient.model.attachment.AttachmentEncoding;
import dev.chrisbarbati.pushoverclient.model.message.MessageRequest;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal builder that maps a {@link MessageRequest} to Pushover API form fields.
 *
 * <p>This class is package-private: field encoding remains an implementation
 * detail of {@link PushoverClient}.</p>
 *
 * @since 0.7.0
 */
final class MessageFieldsBuilder {

    private final String appToken;
    private final String encryptionKey;
    private final E2eeEncryptor encryptor;

    /**
     * Creates a field builder for one application token and optional encryption key.
     *
     * @param appToken application API token
     * @param encryptionKey optional 64-char hex key for encrypted messages
     */
    MessageFieldsBuilder(String appToken, String encryptionKey) {
        this.appToken = appToken;
        this.encryptionKey = PushoverStrings.isBlank(encryptionKey) ? null : encryptionKey;
        this.encryptor = new E2eeEncryptor();
    }

    /**
     * Builds API fields for a message request.
     *
     * @param request message request
     * @return ordered key/value map for request encoding
     * @throws PushoverClientException when encrypted mode is requested without a key
     */
    Map<String, String> buildMessageFields(MessageRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("token", appToken);
        fields.put("user", request.user());

        PushoverStrings.putIfPresent(fields, "device", request.device());
        PushoverStrings.putIfPresent(fields, "sound", request.sound());
        PushoverStrings.putIfPresent(fields, "callback", request.callback());
        PushoverStrings.putIfPresent(fields, "tags", request.tags());

        if (request.priority() != null) {
            fields.put("priority", Integer.toString(request.priority().value()));
        }
        if (request.timestamp() != null) {
            fields.put("timestamp", Long.toString(request.timestamp()));
        }
        if (request.ttl() != null) {
            fields.put("ttl", Integer.toString(request.ttl()));
        }
        if (request.retry() != null) {
            fields.put("retry", Integer.toString(request.retry()));
        }
        if (request.expire() != null) {
            fields.put("expire", Integer.toString(request.expire()));
        }
        if (request.html() != null) {
            fields.put("html", Integer.toString(request.html()));
        }
        if (request.monospace() != null) {
            fields.put("monospace", Integer.toString(request.monospace()));
        }

        if (request.encrypted()) {
            if (encryptionKey == null) {
                throw new PushoverClientException("Encrypted messages require an encryption key");
            }
            // encrypted=1 tells Pushover to decrypt message/title/url/url_title server-side.
            fields.put("encrypted", "1");
            fields.put("message", encryptor.encryptField(request.message(), encryptionKey));
            putEncryptedIfPresent(fields, "title", request.title());
            putEncryptedIfPresent(fields, "url", request.url());
            putEncryptedIfPresent(fields, "url_title", request.urlTitle());
        } else {
            fields.put("message", request.message());
            PushoverStrings.putIfPresent(fields, "title", request.title());
            PushoverStrings.putIfPresent(fields, "url", request.url());
            PushoverStrings.putIfPresent(fields, "url_title", request.urlTitle());
        }

        Attachment attachment = request.attachment();
        if (attachment != null && attachment.encoding() == AttachmentEncoding.BASE64) {
            // Base64 mode uses attachment_base64 + attachment_type instead of multipart bodies.
            fields.put("attachment_base64", Base64.getEncoder().encodeToString(attachment.data()));
            fields.put("attachment_type", attachment.contentType());
        }

        return fields;
    }

    /**
     * Encrypts optional fields when present.
     *
     * @param fields output map
     * @param key API field name
     * @param value plaintext field value
     */
    private void putEncryptedIfPresent(Map<String, String> fields, String key, String value) {
        if (!PushoverStrings.isBlank(value)) {
            fields.put(key, encryptor.encryptField(value, encryptionKey));
        }
    }
}