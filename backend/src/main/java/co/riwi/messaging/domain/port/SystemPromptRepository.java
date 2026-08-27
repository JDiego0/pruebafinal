package co.riwi.messaging.domain.port;

/** AI-14: versioned system prompt stored and retrieved from the database. */
public interface SystemPromptRepository {

    /**
     * @param id      surrogate key, recorded on every turn so an answer can always
     *                be traced back to the exact instructions that produced it
     * @param version human-readable version of those instructions
     */
    record ActivePrompt(short id, String promptText, String version) {}

    /** Returns the single active system prompt (rw_ux_system_prompts_one_active enforces uniqueness). */
    ActivePrompt active();
}
