package co.riwi.messaging.presentation.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Map<String, String> liveness() {
        return Map.of("status", "UP");
    }

    @GetMapping("/ready")
    public Map<String, String> readiness() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return Map.of("status", "READY");
    }
}
