package co.riwi.messaging.application.usecase;

import co.riwi.messaging.application.command.UpdateProfileCommand;
import co.riwi.messaging.domain.model.DisplayName;
import co.riwi.messaging.domain.model.Locale;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.model.UserProfile;
import co.riwi.messaging.domain.port.TransactionRunner;
import co.riwi.messaging.domain.port.UserRepository;

/** PATCH /me — self-service only; the row is selected by RLS on rw_user_id = me(), never a path parameter. */
public class UpdateProfileUseCase {

    private final UserRepository userRepository;
    private final TransactionRunner transactionRunner;

    public UpdateProfileUseCase(UserRepository userRepository, TransactionRunner transactionRunner) {
        this.userRepository = userRepository;
        this.transactionRunner = transactionRunner;
    }

    public UserProfile execute(UserId actor, UpdateProfileCommand command) {
        DisplayName displayName = command.displayName() == null ? null : DisplayName.of(command.displayName());
        Locale locale = command.locale() == null ? null : Locale.fromCode(command.locale());

        return transactionRunner.runAsActor(actor, () ->
                userRepository.updateSelfProfile(actor, displayName, locale, command.avatarUrl()));
    }
}
