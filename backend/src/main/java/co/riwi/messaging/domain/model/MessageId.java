package co.riwi.messaging.domain.model;

public record MessageId(long value) {

    public static MessageId of(long value) {
        return new MessageId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
