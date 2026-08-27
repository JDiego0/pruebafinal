package co.riwi.messaging.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditMessageRequest(@NotBlank @Size(max = 4000) String body) {
}
