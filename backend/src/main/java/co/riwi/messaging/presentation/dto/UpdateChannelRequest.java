package co.riwi.messaging.presentation.dto;

public record UpdateChannelRequest(String name, String topic, Boolean isArchived) {
}
