package co.riwi.messaging.domain.model;

public enum ChannelType {
    PUBLIC, PRIVATE, DIRECT;

    public static ChannelType fromCode(String code) {
        return valueOf(code.toUpperCase());
    }

    public String code() {
        return name();
    }
}
