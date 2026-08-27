package co.riwi.messaging.application.usecase;

import co.riwi.messaging.application.command.CreateChannelCommand;
import co.riwi.messaging.domain.error.ValidationError;
import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.ChannelSlug;
import co.riwi.messaging.domain.model.ChannelType;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ChannelRepository;
import co.riwi.messaging.domain.port.TransactionRunner;

/**
 * POST /channels — creator becomes OWNER atomically (rw_fn_create_channel,
 * D-08). PUBLIC/PRIVATE only; DIRECT channels go through OpenDirectChannelUseCase.
 */
public class CreateChannelUseCase {

    private final ChannelRepository channelRepository;
    private final TransactionRunner transactionRunner;

    public CreateChannelUseCase(ChannelRepository channelRepository, TransactionRunner transactionRunner) {
        this.channelRepository = channelRepository;
        this.transactionRunner = transactionRunner;
    }

    public ChannelId execute(UserId actor, CreateChannelCommand command) {
        ChannelType type = ChannelType.fromCode(command.type());
        if (type == ChannelType.DIRECT) {
            throw new ValidationError("type", "use POST /channels/direct to open a direct channel");
        }
        if (command.name() == null || command.name().trim().length() < 2 || command.name().trim().length() > 80) {
            throw new ValidationError("name", "must be between 2 and 80 characters");
        }
        ChannelSlug slug = ChannelSlug.of(command.slug());

        return transactionRunner.runAsActor(actor, () ->
                channelRepository.create(type, command.name().trim(), slug, command.topic()));
    }
}
