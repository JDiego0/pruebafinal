package co.riwi.messaging.presentation.controller;

import co.riwi.messaging.application.usecase.SearchMessagesUseCase;
import co.riwi.messaging.domain.model.AuthenticatedActor;
import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.SearchHit;
import co.riwi.messaging.infrastructure.web.CurrentActor;
import co.riwi.messaging.infrastructure.web.SearchCursor;
import co.riwi.messaging.presentation.dto.PageInfo;
import co.riwi.messaging.presentation.dto.PagedResponse;
import co.riwi.messaging.presentation.dto.SearchHitResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** SRCH-01..SRCH-04: authorization is enforced by the database inside the query itself, never by post-filtering here. */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchMessagesUseCase searchMessagesUseCase;

    public SearchController(SearchMessagesUseCase searchMessagesUseCase) {
        this.searchMessagesUseCase = searchMessagesUseCase;
    }

    @GetMapping("/messages")
    public PagedResponse<SearchHitResponse> search(
            @CurrentActor AuthenticatedActor actor,
            @RequestParam("q") String query,
            @RequestParam(required = false) String channelId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor
    ) {
        var decoded = cursor == null ? null : SearchCursor.decode(cursor);
        List<SearchHit> hits = searchMessagesUseCase.execute(
                actor.userId(), query, channelId == null ? null : ChannelId.of(channelId),
                decoded == null ? null : decoded.rank(), decoded == null ? null : MessageId.of(decoded.messageId()),
                limit
        );
        List<SearchHitResponse> items = hits.stream().map(SearchHitResponse::from).toList();
        boolean hasMore = hits.size() >= limit;
        String nextCursor = hasMore && !hits.isEmpty()
                ? SearchCursor.encode(hits.get(hits.size() - 1).rank(), hits.get(hits.size() - 1).messageId().value())
                : null;
        return new PagedResponse<>(items, new PageInfo(nextCursor, hasMore));
    }
}
