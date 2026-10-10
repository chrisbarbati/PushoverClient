package dev.chrisbarbati.pushoverclient.model.glance;

import java.util.Objects;

/**
 * Immutable request model for updating a Pushover glance (widget) via the Glances API.
 *
 * <p>A glance update requires at least one data field ({@code title}, {@code text},
 * {@code subtext}, {@code count}, or {@code percent}); only the supplied fields are
 * overwritten. Validation happens in {@code PushoverClient} at send time.</p>
 *
 * @since 0.9.0
 */
public final class GlanceRequest {
    private final String user;
    private final String device;
    private final String title;
    private final String text;
    private final String subtext;
    private final Integer count;
    private final Integer percent;

    private GlanceRequest(Builder builder) {
        this.user = builder.user;
        this.device = builder.device;
        this.title = builder.title;
        this.text = builder.text;
        this.subtext = builder.subtext;
        this.count = builder.count;
        this.percent = builder.percent;
    }

    /**
     * Starts a fluent builder with the required user key.
     *
     * @param user target user key that registered the glance widget
     * @return builder for optional fields
     * @since 0.9.0
     */
    public static Builder builder(String user) {
        return new Builder(user);
    }

    /**
     * @return target user key
     * @since 0.9.0
     */
    public String user() {
        return user;
    }

    /**
     * @return optional device name restricting the update to one device's widget
     * @since 0.9.0
     */
    public String device() {
        return device;
    }

    /**
     * @return optional description of the data being shown (max 100 chars)
     * @since 0.9.0
     */
    public String title() {
        return title;
    }

    /**
     * @return optional main line of data (max 100 chars)
     * @since 0.9.0
     */
    public String text() {
        return text;
    }

    /**
     * @return optional second line of data (max 100 chars)
     * @since 0.9.0
     */
    public String subtext() {
        return subtext;
    }

    /**
     * @return optional integer count, may be negative
     * @since 0.9.0
     */
    public Integer count() {
        return count;
    }

    /**
     * @return optional percent value between 0 and 100 inclusive
     * @since 0.9.0
     */
    public Integer percent() {
        return percent;
    }

    /**
     * Fluent builder for optional glance fields.
     *
     * @implNote The builder performs no validation; use {@code PushoverClient} to validate.
     * @since 0.9.0
     */
    public static final class Builder {
        private final String user;
        private String device;
        private String title;
        private String text;
        private String subtext;
        private Integer count;
        private Integer percent;

        private Builder(String user) {
            this.user = Objects.requireNonNull(user, "user is required");
        }

        /**
         * Restricts the glance update to a single device's widget.
         *
         * @param device device name
         * @return this builder
         * @since 0.9.0
         */
        public Builder device(String device) {
            this.device = device;
            return this;
        }

        /**
         * Sets a description of the data being shown.
         *
         * @param title title text (max 100 chars)
         * @return this builder
         * @since 0.9.0
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Sets the main line of data.
         *
         * @param text text value (max 100 chars)
         * @return this builder
         * @since 0.9.0
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Sets a second line of data.
         *
         * @param subtext subtext value (max 100 chars)
         * @return this builder
         * @since 0.9.0
         */
        public Builder subtext(String subtext) {
            this.subtext = subtext;
            return this;
        }

        /**
         * Sets an integer count value.
         *
         * @param count integer count, may be negative
         * @return this builder
         * @since 0.9.0
         */
        public Builder count(int count) {
            this.count = count;
            return this;
        }

        /**
         * Sets a percentage value.
         *
         * @param percent value between 0 and 100 inclusive
         * @return this builder
         * @since 0.9.0
         */
        public Builder percent(int percent) {
            this.percent = percent;
            return this;
        }

        /**
         * Builds an immutable glance request.
         *
         * @return immutable glance request
         * @since 0.9.0
         */
        public GlanceRequest build() {
            return new GlanceRequest(this);
        }
    }
}