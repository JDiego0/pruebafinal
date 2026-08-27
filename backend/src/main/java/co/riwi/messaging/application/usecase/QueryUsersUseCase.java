package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.TransactionRunner;
import co.riwi.messaging.domain.port.UserRepository;
import co.riwi.messaging.domain.port.UserRepository.UserQueryRow;

import java.util.List;

/** OBJ-01: directory search, membership-agnostic (any authenticated active user is listed). */
public class QueryUsersUseCase {

    private final UserRepository userRepository;
    private final TransactionRunner transactionRunner;

    public QueryUsersUseCase(UserRepository userRepository, TransactionRunner transactionRunner) {
        this.userRepository = userRepository;
        this.transactionRunner = transactionRunner;
    }

    public List<UserQueryRow> execute(UserId actor, String search, int limit, String cursorDisplayName, UserId cursorUserId) {
        return transactionRunner.runAsActor(actor, () -> userRepository.query(search, limit, cursorDisplayName, cursorUserId));
    }
}
