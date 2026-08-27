package co.riwi.messaging.domain.port;

/** AI-14: versioned system prompt stored and retrieved from the database. */
public interface SystemPromptRepository {

    record ActivePrompt(String promptText, String version) {}

    /** Returns the single active system prompt (rw_ux_system_prompts_one_active enforces uniqueness). */
    ActivePrompt active();
}
