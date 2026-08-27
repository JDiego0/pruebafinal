package co.riwi.messaging.application.usecase;

import co.riwi.messaging.application.command.ModifyUserCommand;
import co.riwi.messaging.domain.model.Role;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.AdminUserPort;
import co.riwi.messaging.domain.port.TransactionRunner;

/**
 * OBJ-02: role change or logical deactivation of ANOTHER user. rw_sp_modify_user
 * (V5) itself re-checks that the actor is an ADMIN before allowing this —
 * this use case does not duplicate that check, it only shapes the call.
 */
public class ModifyUserUseCase {

    private final AdminUserPort adminUserPort;
    private final TransactionRunner transactionRunner;

    public ModifyUserUseCase(AdminUserPort adminUserPort, TransactionRunner transactionRunner) {
        this.adminUserPort = adminUserPort;
        this.transactionRunner = transactionRunner;
    }

    public void execute(UserId actor, UserId targetUserId, ModifyUserCommand command) {
        Role role = command.roleCode() == null ? null : Role.fromCode(command.roleCode());
        transactionRunner.runAsActor(actor, () -> {
            adminUserPort.modify(targetUserId, null, null, null, role, command.deactivate());
            return null;
        });
    }
}
