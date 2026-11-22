package vn.unicom.thi360.classroom.spi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClassMembersAdded(
        UUID classId,
        List<UUID> userIds,
        UUID actorId,
        Instant occurredAt) {

    public ClassMembersAdded {
        userIds = userIds == null ? List.of() : List.copyOf(userIds);
    }
}
