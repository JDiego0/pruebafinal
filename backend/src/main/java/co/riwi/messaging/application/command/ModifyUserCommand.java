package co.riwi.messaging.application.command;

public record ModifyUserCommand(String roleCode, Boolean deactivate) {
}
