package co.riwi.messaging.domain.model;

import co.riwi.messaging.domain.error.ValidationError;

import java.util.regex.Pattern;

/** Mirrors rw_channels_slug_ck. */
public record ChannelSlug(String value) {

    private static final Pattern SHAPE = Pattern.compile("^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$");

    public ChannelSlug {
        if (value == null || !SHAPE.matcher(value).matches()) {
            throw new ValidationError("slug", "must be lowercase alphanumeric with hyphens, 3-80 characters");
        }
    }

    public static ChannelSlug of(String value) {
        return new ChannelSlug(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
