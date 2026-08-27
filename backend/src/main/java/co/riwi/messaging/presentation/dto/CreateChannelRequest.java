package co.riwi.messaging.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateChannelRequest(
        @NotBlank @Pattern(regexp = "PUBLIC|PRIVATE") String type,
        @NotBlank String name,
        @NotBlank String slug,
        String topic
) {
}
