package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.CopilotRepository;
import co.riwi.messaging.domain.port.TransactionRunner;

/** Returns usage ledger for the authenticated user. */
public class GetCopilotUsageUseCase {

    private final CopilotRepository copilotRepository;
    private final TransactionRunner transactionRunner;

    public GetCopilotUsageUseCase(CopilotRepository copilotRepository,
                                   TransactionRunner transactionRunner) {
        this.copilotRepository = copilotRepository;
        this.transactionRunner = transactionRunner;
    }

    public CopilotRepository.UsageSummary execute(UserId actorId) {
        return transactionRunner.runAsActor(actorId, () ->
                copilotRepository.usageSummary(actorId)
        );
    }
}
