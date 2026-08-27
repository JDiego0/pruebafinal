package co.riwi.messaging.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank @Size(max = 4000) String body,
        @NotNull String clientMessageId,
        Long parentMessageId
) {
}
