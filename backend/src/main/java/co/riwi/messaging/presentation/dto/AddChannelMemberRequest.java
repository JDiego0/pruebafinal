package co.riwi.messaging.presentation.dto;

import jakarta.validation.constraints.Pattern;

/** userId absent -> self-join a PUBLIC channel; present -> owner/moderator invites someone else. */
public record AddChannelMemberRequest(String userId, @Pattern(regexp = "OWNER|MODERATOR|MEMBER") String role) {
}
