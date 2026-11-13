package vn.unicom.thi360.identity.spi;

import java.time.Instant;
import java.util.UUID;

public record UserDeleted(
        UUID userId,
        String username,
        Instant deletedAt,
        UUID actorId
) {
    public static final String OUTBOX_TOPIC = "identity.user.deleted";
}
