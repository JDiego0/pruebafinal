package co.riwi.messaging.domain.model;

public enum ChannelMemberRole {
    OWNER, MODERATOR, MEMBER;

    public static ChannelMemberRole fromCode(String code) {
        return valueOf(code.toUpperCase());
    }

    public String code() {
        return name();
    }
}
