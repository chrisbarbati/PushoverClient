package org.chrisbarbati.pushoverclient.model.attachment;

/**
 * Selects how attachment data is submitted to Pushover.
 *
 * @since 0.1.0
 * @author Christian Barbati
 */
public enum AttachmentEncoding {
    /** Send raw attachment bytes using multipart/form-data and the attachment field. */
    MULTIPART,
    /** Send base64 attachment data using attachment_base64 plus attachment_type. */
    BASE64
}

