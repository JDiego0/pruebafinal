package co.riwi.messaging.application.command;

public record UpdateChannelCommand(String name, String topic, Boolean archived) {
}
