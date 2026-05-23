package org.chrisbarbati.pushoverclient.model.attachment;

import java.util.Arrays;
import java.util.Objects;

/**
 * Attachment payload metadata and bytes.
 *
 * @apiNote Pushover accepts only one attachment per message.
 * @since 0.1.0
 * @author Christian Barbati
 */
public final class Attachment {
    private final String fileName;
    private final byte[] data;
    private final String contentType;
    private final AttachmentEncoding encoding;

    /**
     * Creates an attachment descriptor.
     *
     * @param fileName attachment file name shown in multipart metadata
     * @param data binary attachment bytes
     * @param contentType MIME type (for multipart header or attachment_type)
     * @param encoding upload format to use with the API
     * @since 0.1.0
     */
    public Attachment(String fileName, byte[] data, String contentType, AttachmentEncoding encoding) {
        this.fileName = Objects.requireNonNull(fileName, "fileName is required");
        this.data = Objects.requireNonNull(data, "data is required");
        this.contentType = Objects.requireNonNull(contentType, "contentType is required");
        this.encoding = Objects.requireNonNull(encoding, "encoding is required");
    }

    /**
     * @return file name used in multipart requests
     * @since 0.1.0
     */
    public String fileName() {
        return fileName;
    }

    /**
     * @return defensive copy of attachment bytes
     * @since 0.1.0
     */
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * @return MIME type of the attachment
     * @since 0.1.0
     */
    public String contentType() {
        return contentType;
    }

    /**
     * @return configured attachment encoding mode
     * @since 0.1.0
     */
    public AttachmentEncoding encoding() {
        return encoding;
    }
}


