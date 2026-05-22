package org.chrisbarbati.pushoverclient.model.message;

import org.chrisbarbati.pushoverclient.model.attachment.Attachment;

import java.util.Objects;

/**
 * Immutable request model for sending a Pushover message.
 *
 * @apiNote Validation happens in {@code PushoverClient} at send time.
 * @since 0.1.0
 * @author Christian Barbati
 */
public final class MessageRequest {
    private final String user;
    private final String message;
    private final String device;
    private final String title;
    private final String url;
    private final String urlTitle;
    private final Integer html;
    private final Integer monospace;
    private final MessagePriority priority;
    private final String sound;
    private final Long timestamp;
    private final Integer ttl;
    private final Integer retry;
    private final Integer expire;
    private final String callback;
    private final String tags;
    private final Attachment attachment;
    private final boolean encrypted;

    /**
     * Creates an immutable request from a builder.
     *
     * @param builder populated builder instance
     * @since 0.1.0
     * @author Christian Barbati
     */
    private MessageRequest(Builder builder) {
        this.user = builder.user;
        this.message = builder.message;
        this.device = builder.device;
        this.title = builder.title;
        this.url = builder.url;
        this.urlTitle = builder.urlTitle;
        this.html = builder.html;
        this.monospace = builder.monospace;
        this.priority = builder.priority;
        this.sound = builder.sound;
        this.timestamp = builder.timestamp;
        this.ttl = builder.ttl;
        this.retry = builder.retry;
        this.expire = builder.expire;
        this.callback = builder.callback;
        this.tags = builder.tags;
        this.attachment = builder.attachment;
        this.encrypted = builder.encrypted;
    }

    /**
     * Starts a fluent builder with required message fields.
     *
     * @param user target user/group key or a comma-separated user list
     * @param message notification body
     * @return builder for optional fields
     * @since 0.1.0
     * @author Christian Barbati
     */
    public static Builder builder(String user, String message) {
        return new Builder(user, message);
    }

    /**
     * @return target user/group identifier list
     * @since 0.1.0
     * @author Christian Barbati
     */
    public String user() {
        return user;
    }

    /**
     * @return message body
     * @since 0.1.0
     * @author Christian Barbati
     */
    public String message() {
        return message;
    }

    /**
     * @return optional device name or comma-separated device names
     * @since 0.1.0
     * @author Christian Barbati
     */
    public String device() {
        return device;
    }

    /**
     * @return optional message title
     * @since 0.1.0
     * @author Christian Barbati
     */
    public String title() {
        return title;
    }

    /**
     * @return optional supplementary URL
     * @since 0.1.0
     * @author Christian Barbati
     */
    public String url() {
        return url;
    }

    /**
     * @return optional title for the supplementary URL
     * @since 0.1.0
     * @author Christian Barbati
     */
    public String urlTitle() {
        return urlTitle;
    }

    /**
     * @return 1 when HTML parsing is enabled, otherwise null
     * @since 0.1.0
     * @author Christian Barbati
     */
    public Integer html() {
        return html;
    }

    /**
     * @return 1 when monospace rendering is enabled, otherwise null
     * @since 0.1.0
     * @author Christian Barbati
     */
    public Integer monospace() {
        return monospace;
    }

    /**
     * @return configured message priority
     * @since 0.1.0
     * @author Christian Barbati
     */
    public MessagePriority priority() {
        return priority;
    }

    /**
     * @return optional override sound key
     * @since 0.1.0
     * @author Christian Barbati
     */
    public String sound() {
        return sound;
    }

    /**
     * @return optional unix timestamp to display
     * @since 0.1.0
     * @author Christian Barbati
     */
    public Long timestamp() {
        return timestamp;
    }

    /**
     * @return optional TTL in seconds
     * @since 0.1.0
     * @author Christian Barbati
     */
    public Integer ttl() {
        return ttl;
    }

    /**
     * @return optional emergency retry interval
     * @since 0.1.0
     * @author Christian Barbati
     */
    public Integer retry() {
        return retry;
    }

    /**
     * @return optional emergency expiration seconds
     * @since 0.1.0
     * @author Christian Barbati
     */
    public Integer expire() {
        return expire;
    }

    /**
     * @return optional emergency callback URL
     * @since 0.1.0
     * @author Christian Barbati
     */
    public String callback() {
        return callback;
    }

    /**
     * @return optional emergency tags for cancel-by-tag APIs
     * @since 0.1.0
     * @author Christian Barbati
     */
    public String tags() {
        return tags;
    }

    /**
     * @return optional attachment payload
     * @since 0.1.0
     * @author Christian Barbati
     */
    public Attachment attachment() {
        return attachment;
    }

    /**
     * @return true when message, title, url, and url_title should be encrypted
     * @since 0.1.0
     * @author Christian Barbati
     */
    public boolean encrypted() {
        return encrypted;
    }

    /**
     * Creates a builder pre-populated with this request's values.
     *
     * @return builder initialized from this request
     * @since 0.3.0
     * @author Christian Barbati
     */
    public Builder toBuilder() {
        return builder(user, message)
                .device(device)
                .title(title)
                .url(url)
                .urlTitle(urlTitle)
                .priority(priority)
                .sound(sound)
                .callback(callback)
                .tags(tags)
                .attachment(attachment)
                .encrypted(encrypted)
                .timestamp(timestamp == null ? 0L : timestamp)
                .ttl(ttl == null ? 0 : ttl)
                .retry(retry == null ? 0 : retry)
                .expire(expire == null ? 0 : expire)
                .html(html != null && html == 1)
                .monospace(monospace != null && monospace == 1)
                .clearUnsetOptionalNumbers(timestamp, ttl, retry, expire);
    }

    /**
     * Fluent builder for optional Pushover parameters.
     *
     * @implNote Builder performs no network-level validation; use PushoverClient to validate.
     * @since 0.1.0
     * @author Christian Barbati
     */
    public static final class Builder {
        private final String user;
        private final String message;
        private String device;
        private String title;
        private String url;
        private String urlTitle;
        private Integer html;
        private Integer monospace;
        private MessagePriority priority = MessagePriority.NORMAL;
        private String sound;
        private Long timestamp;
        private Integer ttl;
        private Integer retry;
        private Integer expire;
        private String callback;
        private String tags;
        private Attachment attachment;
        private boolean encrypted;

        /**
         * Creates a builder with required fields.
         *
         * @param user target user/group key or comma-separated list
         * @param message notification body
         * @since 0.1.0
         * @author Christian Barbati
         */
        private Builder(String user, String message) {
            this.user = Objects.requireNonNull(user, "user is required");
            this.message = Objects.requireNonNull(message, "message is required");
        }

        /**
         * Restricts delivery to one or more devices.
         *
         * @param device device name or comma-separated names
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder device(String device) {
            this.device = device;
            return this;
        }

        /**
         * Sets a custom message title.
         *
         * @param title title text
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Sets a supplementary URL.
         *
         * @param url URL shown below the message
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * Sets a label for the supplementary URL.
         *
         * @param urlTitle URL display title
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder urlTitle(String urlTitle) {
            this.urlTitle = urlTitle;
            return this;
        }

        /**
         * Enables or disables HTML rendering.
         *
         * @param htmlEnabled true to send html=1
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder html(boolean htmlEnabled) {
            this.html = htmlEnabled ? 1 : null;
            return this;
        }

        /**
         * Enables or disables monospace rendering.
         *
         * @param monospaceEnabled true to send monospace=1
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder monospace(boolean monospaceEnabled) {
            this.monospace = monospaceEnabled ? 1 : null;
            return this;
        }

        /**
         * Sets message priority.
         *
         * @param priority priority enum
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder priority(MessagePriority priority) {
            this.priority = Objects.requireNonNull(priority, "priority is required");
            return this;
        }

        /**
         * Overrides the recipient's default sound.
         *
         * @param sound sound key from the sounds API
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder sound(String sound) {
            this.sound = sound;
            return this;
        }

        /**
         * Sets displayed message timestamp.
         *
         * @param timestamp unix timestamp
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Sets time-to-live for automatic message removal.
         *
         * @param ttl seconds to keep the message
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder ttl(int ttl) {
            this.ttl = ttl;
            return this;
        }

        /**
         * Sets emergency retry interval.
         *
         * @param retry seconds between retries (min 30)
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder retry(int retry) {
            this.retry = retry;
            return this;
        }

        /**
         * Sets emergency expiration window.
         *
         * @param expire seconds before retries stop (max 10800)
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder expire(int expire) {
            this.expire = expire;
            return this;
        }

        /**
         * Sets emergency acknowledgement callback URL.
         *
         * @param callback callback endpoint
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder callback(String callback) {
            this.callback = callback;
            return this;
        }

        /**
         * Sets emergency tags for later cancel-by-tag calls.
         *
         * @param tags comma-separated tag list
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder tags(String tags) {
            this.tags = tags;
            return this;
        }

        /**
         * Adds a single attachment.
         *
         * @param attachment attachment payload
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder attachment(Attachment attachment) {
            this.attachment = attachment;
            return this;
        }

        /**
         * Enables end-to-end encryption for supported fields.
         *
         * @param encrypted true to send encrypted=1 and encrypted field values
         * @return this builder
         * @since 0.1.0
         * @author Christian Barbati
         */
        public Builder encrypted(boolean encrypted) {
            this.encrypted = encrypted;
            return this;
        }

        private Builder clearUnsetOptionalNumbers(Long timestamp, Integer ttl, Integer retry, Integer expire) {
            if (timestamp == null) {
                this.timestamp = null;
            }
            if (ttl == null) {
                this.ttl = null;
            }
            if (retry == null) {
                this.retry = null;
            }
            if (expire == null) {
                this.expire = null;
            }
            return this;
        }

        /**
         * Builds an immutable request.
         *
         * @return immutable message request
         * @since 0.1.0
         * @author Christian Barbati
         */
        public MessageRequest build() {
            return new MessageRequest(this);
        }
    }
}

