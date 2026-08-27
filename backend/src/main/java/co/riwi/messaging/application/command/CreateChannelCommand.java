package co.riwi.messaging.application.command;

public record CreateChannelCommand(String type, String name, String slug, String topic) {
}
