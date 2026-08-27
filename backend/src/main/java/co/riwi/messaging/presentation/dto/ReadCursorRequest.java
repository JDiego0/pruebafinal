package co.riwi.messaging.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record ReadCursorRequest(@NotNull Long lastReadMessageId) {
}
