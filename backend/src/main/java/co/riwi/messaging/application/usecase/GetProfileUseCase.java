package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.error.ResourceNotVisible;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.model.UserProfile;
import co.riwi.messaging.domain.port.TransactionRunner;
import co.riwi.messaging.domain.port.UserRepository;

/** GET /me — the actor id comes from the token only (AUTH-09), never a path parameter. */
public class GetProfileUseCase {

    private final UserRepository userRepository;
    private final TransactionRunner transactionRunner;

    public GetProfileUseCase(UserRepository userRepository, TransactionRunner transactionRunner) {
        this.userRepository = userRepository;
        this.transactionRunner = transactionRunner;
    }

    public UserProfile execute(UserId actor) {
        return transactionRunner.runAsActor(actor, () -> userRepository.findById(actor))
                .orElseThrow(() -> new ResourceNotVisible("user", actor));
    }
}
