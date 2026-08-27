package co.riwi.messaging.domain.model;

import java.time.Instant;

/** One row of a search result — highlight is pre-rendered by ts_headline, never raw body (SRCH-03). */
public record SearchHit(
        MessageId messageId,
        ChannelId channelId,
        UserId senderId,
        Instant createdAt,
        String highlight,
        float rank
) {
}
