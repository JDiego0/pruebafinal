package co.riwi.messaging.presentation.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 80) String displayName,
        @Pattern(regexp = "es|en") String locale,
        String avatarUrl
) {
}
