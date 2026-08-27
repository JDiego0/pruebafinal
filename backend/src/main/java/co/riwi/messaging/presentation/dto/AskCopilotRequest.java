package co.riwi.messaging.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskCopilotRequest(
        @NotBlank
        @Size(max = 1000, message = "Question must not exceed 1000 characters")
        String question,

        /** Optional: scope retrieval to a single channel. */
        String channelId,

        /** Optional: continue an existing copilot thread. */
        String threadId
) {}
