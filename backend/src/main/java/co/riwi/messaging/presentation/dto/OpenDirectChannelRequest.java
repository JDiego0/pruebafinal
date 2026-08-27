package co.riwi.messaging.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record OpenDirectChannelRequest(@NotBlank String counterpartUserId) {
}
