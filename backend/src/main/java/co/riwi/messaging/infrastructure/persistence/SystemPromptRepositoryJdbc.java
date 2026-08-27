package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.port.SystemPromptRepository;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reads the active system prompt from rw_system_prompts. */
public class SystemPromptRepositoryJdbc implements SystemPromptRepository {

    private final JdbcTemplate jdbc;

    public SystemPromptRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ActivePrompt active() {
        return jdbc.queryForObject(
                "SELECT prompt_text, version FROM rw.rw_system_prompts WHERE is_active = true LIMIT 1",
                (rs, rowNum) -> new ActivePrompt(rs.getString("prompt_text"), rs.getString("version"))
        );
    }
}
