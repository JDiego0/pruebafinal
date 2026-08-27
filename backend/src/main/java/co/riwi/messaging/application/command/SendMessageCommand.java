package co.riwi.messaging.application.command;

import java.util.UUID;

public record SendMessageCommand(String body, UUID clientMessageId, Long parentMessageId) {
}
