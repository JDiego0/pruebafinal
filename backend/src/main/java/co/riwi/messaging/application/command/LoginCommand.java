package co.riwi.messaging.application.command;

public record LoginCommand(String email, String rawPassword, String userAgent, String ipAddress) {
}
