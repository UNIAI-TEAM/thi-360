package vn.unicom.thi360.identity.spi;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ClassMembershipPort {

    List<UUID> validateAssignableClasses(Collection<UUID> classIds);

    void assignUserToClasses(UUID userId, Collection<UUID> classIds, UUID actorId, Instant occurredAt);

    Set<UUID> findUserIdsInClass(UUID classId);

    void ensureSeedClass(
            String code,
            String name,
            String description,
            UUID ownerUserId,
            Collection<UUID> studentIds,
            Instant occurredAt);
}
