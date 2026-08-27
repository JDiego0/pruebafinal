package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.*;

import java.util.List;
import java.util.Optional;

/**
 * Channel lifecycle. Creation and DM-open call the SECURITY DEFINER
 * bootstrap functions (D-08); everything else is a plain RLS-governed query.
 */
public interface ChannelRepository {

    Optional<ChannelDetail> findById(ChannelId id);

    /** True only for an actual member — distinct from "discoverable" (a PUBLIC channel the actor has not joined). */
    boolean isMember(ChannelId id);

    /** IBR-02: discoverable PUBLIC, non-archived channels only. */
    List<ChannelDetail> discoverPublic(String search);

    ChannelId create(ChannelType type, String name, ChannelSlug slug, String topic);

    DirectChannelResult openDirect(UserId counterpart);

    /** @return true if the update affected a row (actor was OWNER/MODERATOR of a visible channel). */
    boolean update(ChannelId id, String name, String topic, Boolean archived);

    List<ChannelMemberSummary> listMembers(ChannelId id);

    record DirectChannelResult(ChannelId channelId, boolean created) {
    }
}
