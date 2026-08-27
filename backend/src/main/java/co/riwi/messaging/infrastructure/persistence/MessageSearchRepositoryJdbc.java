package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.SearchHit;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.MessageSearchRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class MessageSearchRepositoryJdbc implements MessageSearchRepository {

    private final JdbcTemplate jdbc;

    public MessageSearchRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SearchHit> search(String query, ChannelId channelId, Float beforeRank, MessageId beforeMessageId, int limit) {
        return jdbc.query(
                "SELECT rw_message_id, rw_channel_id, rw_sender_id, rw_created_at, rw_highlight, rw_rank " +
                        "FROM rw.rw_fn_search_messages(?, ?, ?, ?, ?)",
                (rs, rowNum) -> new SearchHit(
                        MessageId.of(rs.getLong("rw_message_id")),
                        ChannelId.of((UUID) rs.getObject("rw_channel_id")),
                        UserId.of((UUID) rs.getObject("rw_sender_id")),
                        rs.getTimestamp("rw_created_at").toInstant(),
                        rs.getString("rw_highlight"),
                        rs.getFloat("rw_rank")
                ),
                query, channelId == null ? null : channelId.value(), beforeRank,
                beforeMessageId == null ? null : beforeMessageId.value(), limit
        );
    }
}
