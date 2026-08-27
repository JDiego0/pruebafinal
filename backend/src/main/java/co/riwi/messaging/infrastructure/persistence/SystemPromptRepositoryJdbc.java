package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.port.SystemPromptRepository;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reads the active system prompt from rw.rw_system_prompts (AI-14). */
public class SystemPromptRepositoryJdbc implements SystemPromptRepository {

    private final JdbcTemplate jdbc;

    public SystemPromptRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ActivePrompt active() {
        return jdbc.queryForObject("""
                SELECT rw_system_prompt_id, rw_prompt_body, rw_prompt_version
                FROM rw.rw_system_prompts
                WHERE rw_is_active
                LIMIT 1
                """,
                (rs, rowNum) -> new ActivePrompt(
                        rs.getShort("rw_system_prompt_id"),
                        rs.getString("rw_prompt_body"),
                        rs.getString("rw_prompt_version")));
    }
}
