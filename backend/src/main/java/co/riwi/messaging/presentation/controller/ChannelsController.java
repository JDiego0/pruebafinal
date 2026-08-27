package co.riwi.messaging.presentation.controller;

import co.riwi.messaging.application.command.CreateChannelCommand;
import co.riwi.messaging.application.command.SendMessageCommand;
import co.riwi.messaging.application.command.UpdateChannelCommand;
import co.riwi.messaging.application.usecase.*;
import co.riwi.messaging.domain.model.*;
import co.riwi.messaging.domain.port.ChannelRepository.DirectChannelResult;
import co.riwi.messaging.domain.port.MessageRepository.SendResult;
import co.riwi.messaging.infrastructure.web.CurrentActor;
import co.riwi.messaging.infrastructure.web.MessageCursor;
import co.riwi.messaging.presentation.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Channel discovery, lifecycle and membership. The actor's id is taken only
 * from {@link CurrentActor} — never a path/query/body parameter (AUTH-09).
 */
@RestController
@RequestMapping("/api/v1/channels")
public class ChannelsController {

    private final DiscoverChannelsUseCase discoverChannelsUseCase;
    private final CreateChannelUseCase createChannelUseCase;
    private final OpenDirectChannelUseCase openDirectChannelUseCase;
    private final GetChannelUseCase getChannelUseCase;
    private final UpdateChannelUseCase updateChannelUseCase;
    private final ListChannelMembersUseCase listChannelMembersUseCase;
    private final JoinChannelUseCase joinChannelUseCase;
    private final AddChannelMemberUseCase addChannelMemberUseCase;
    private final LeaveChannelUseCase leaveChannelUseCase;
    private final RemoveChannelMemberUseCase removeChannelMemberUseCase;
    private final UpdateReadCursorUseCase updateReadCursorUseCase;
    private final GetMessageHistoryUseCase getMessageHistoryUseCase;
    private final SendMessageUseCase sendMessageUseCase;

    public ChannelsController(
            DiscoverChannelsUseCase discoverChannelsUseCase, CreateChannelUseCase createChannelUseCase,
            OpenDirectChannelUseCase openDirectChannelUseCase, GetChannelUseCase getChannelUseCase,
            UpdateChannelUseCase updateChannelUseCase, ListChannelMembersUseCase listChannelMembersUseCase,
            JoinChannelUseCase joinChannelUseCase, AddChannelMemberUseCase addChannelMemberUseCase,
            LeaveChannelUseCase leaveChannelUseCase, RemoveChannelMemberUseCase removeChannelMemberUseCase,
            UpdateReadCursorUseCase updateReadCursorUseCase, GetMessageHistoryUseCase getMessageHistoryUseCase,
            SendMessageUseCase sendMessageUseCase
    ) {
        this.discoverChannelsUseCase = discoverChannelsUseCase;
        this.createChannelUseCase = createChannelUseCase;
        this.openDirectChannelUseCase = openDirectChannelUseCase;
        this.getChannelUseCase = getChannelUseCase;
        this.updateChannelUseCase = updateChannelUseCase;
        this.listChannelMembersUseCase = listChannelMembersUseCase;
        this.joinChannelUseCase = joinChannelUseCase;
        this.addChannelMemberUseCase = addChannelMemberUseCase;
        this.leaveChannelUseCase = leaveChannelUseCase;
        this.removeChannelMemberUseCase = removeChannelMemberUseCase;
        this.updateReadCursorUseCase = updateReadCursorUseCase;
        this.getMessageHistoryUseCase = getMessageHistoryUseCase;
        this.sendMessageUseCase = sendMessageUseCase;
    }

    @GetMapping
    public List<ChannelResponse> discover(@CurrentActor AuthenticatedActor actor, @RequestParam(required = false) String query) {
        return discoverChannelsUseCase.execute(actor.userId(), query).stream().map(ChannelResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<Void> create(@CurrentActor AuthenticatedActor actor, @Valid @RequestBody CreateChannelRequest request) {
        ChannelId id = createChannelUseCase.execute(actor.userId(),
                new CreateChannelCommand(request.type(), request.name(), request.slug(), request.topic()));
        return ResponseEntity.created(URI.create("/api/v1/channels/" + id)).build();
    }

    @PostMapping("/direct")
    public ResponseEntity<ChannelResponse> openDirect(@CurrentActor AuthenticatedActor actor, @Valid @RequestBody OpenDirectChannelRequest request) {
        DirectChannelResult result = openDirectChannelUseCase.execute(actor.userId(), UserId.of(request.counterpartUserId()));
        ChannelResponse body = ChannelResponse.from(getChannelUseCase.execute(actor.userId(), result.channelId()));
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(body)
                : ResponseEntity.ok(body);
    }

    @GetMapping("/{channelId}")
    public ChannelResponse get(@CurrentActor AuthenticatedActor actor, @PathVariable String channelId) {
        return ChannelResponse.from(getChannelUseCase.execute(actor.userId(), ChannelId.of(channelId)));
    }

    @PatchMapping("/{channelId}")
    public ChannelResponse update(@CurrentActor AuthenticatedActor actor, @PathVariable String channelId, @RequestBody UpdateChannelRequest request) {
        return ChannelResponse.from(updateChannelUseCase.execute(actor.userId(), ChannelId.of(channelId),
                new UpdateChannelCommand(request.name(), request.topic(), request.isArchived())));
    }

    @GetMapping("/{channelId}/members")
    public List<ChannelMemberResponse> members(@CurrentActor AuthenticatedActor actor, @PathVariable String channelId) {
        return listChannelMembersUseCase.execute(actor.userId(), ChannelId.of(channelId)).stream().map(ChannelMemberResponse::from).toList();
    }

    @PostMapping("/{channelId}/members")
    public ResponseEntity<Void> addMember(@CurrentActor AuthenticatedActor actor, @PathVariable String channelId,
                                           @RequestBody(required = false) AddChannelMemberRequest request) {
        ChannelId id = ChannelId.of(channelId);
        if (request == null || request.userId() == null) {
            joinChannelUseCase.execute(actor.userId(), id);
        } else {
            addChannelMemberUseCase.execute(actor.userId(), id, UserId.of(request.userId()), request.role());
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{channelId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@CurrentActor AuthenticatedActor actor, @PathVariable String channelId, @PathVariable String userId) {
        UserId target = UserId.of(userId);
        if (target.equals(actor.userId())) {
            leaveChannelUseCase.execute(actor.userId(), ChannelId.of(channelId));
        } else {
            removeChannelMemberUseCase.execute(actor.userId(), ChannelId.of(channelId), target);
        }
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{channelId}/read-cursor")
    public ResponseEntity<Void> readCursor(@CurrentActor AuthenticatedActor actor, @PathVariable String channelId, @Valid @RequestBody ReadCursorRequest request) {
        updateReadCursorUseCase.execute(actor.userId(), ChannelId.of(channelId), MessageId.of(request.lastReadMessageId()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{channelId}/messages")
    public PagedResponse<MessageResponse> history(
            @CurrentActor AuthenticatedActor actor, @PathVariable String channelId,
            @RequestParam(defaultValue = "50") int limit, @RequestParam(required = false) String cursor
    ) {
        var decoded = cursor == null ? null : MessageCursor.decode(cursor);
        List<MessageRecord> records = getMessageHistoryUseCase.execute(
                actor.userId(), ChannelId.of(channelId),
                decoded == null ? null : decoded.createdAt(), decoded == null ? null : MessageId.of(decoded.messageId()),
                limit
        );
        List<MessageResponse> items = records.stream().map(MessageResponse::from).toList();
        boolean hasMore = records.size() >= limit;
        String nextCursor = hasMore && !records.isEmpty()
                ? MessageCursor.encode(records.get(records.size() - 1).createdAt(), records.get(records.size() - 1).messageId().value())
                : null;
        return new PagedResponse<>(items, new PageInfo(nextCursor, hasMore));
    }

    @PostMapping("/{channelId}/messages")
    public ResponseEntity<MessageResponse> send(@CurrentActor AuthenticatedActor actor, @PathVariable String channelId,
                                                 @Valid @RequestBody SendMessageRequest request) {
        SendResult result = sendMessageUseCase.execute(actor.userId(), ChannelId.of(channelId),
                new SendMessageCommand(request.body(), UUID.fromString(request.clientMessageId()), request.parentMessageId()));
        MessageResponse body = new MessageResponse(
                result.messageId().value(), channelId, actor.userId().toString(),
                actor.displayName().value(), request.parentMessageId(),
                request.body(), request.clientMessageId(), false, 1, result.createdAt(), null
        );
        return result.created() ? ResponseEntity.status(HttpStatus.CREATED).body(body) : ResponseEntity.ok(body);
    }
}
