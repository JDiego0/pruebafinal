package co.riwi.messaging.presentation.dto;

import jakarta.validation.constraints.Pattern;

public record ModifyUserRequest(@Pattern(regexp = "ADMIN|MEMBER") String role, Boolean deactivate) {
}
