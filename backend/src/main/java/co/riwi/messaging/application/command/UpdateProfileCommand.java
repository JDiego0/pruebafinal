package co.riwi.messaging.application.command;

public record UpdateProfileCommand(String displayName, String locale, String avatarUrl) {
}
