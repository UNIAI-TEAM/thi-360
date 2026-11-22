package vn.unicom.thi360.classroom.spi;

import java.time.Instant;
import java.util.UUID;

public record ClassMemberRemoved(
        UUID classId,
        UUID userId,
        UUID actorId,
        Instant occurredAt) {}
