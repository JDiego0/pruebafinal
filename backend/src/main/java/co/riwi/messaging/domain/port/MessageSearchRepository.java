package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.SearchHit;

import java.util.List;

/**
 * SRCH-01/SRCH-02: the tsquery match runs against the RLS-filtered
 * rw_messages relation (rw_fn_search_messages is SECURITY INVOKER) — an
 * unauthorized row is never a candidate, never scored, never returned.
 * There is no "search everywhere then filter" step to forget.
 */
public interface MessageSearchRepository {

    List<SearchHit> search(String query, ChannelId channelId, Float beforeRank, MessageId beforeMessageId, int limit);
}
