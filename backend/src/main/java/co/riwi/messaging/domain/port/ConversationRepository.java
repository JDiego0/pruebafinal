package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.ConversationSummary;

import java.util.List;

/** OBJ-03: reads rw_v_user_conversations — the actor's own conversations only. */
public interface ConversationRepository {
    List<ConversationSummary> listMine();
}
