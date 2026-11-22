package vn.unicom.thi360.classroom.spi;

import java.time.Instant;
import java.util.UUID;

public record ClassDeleted(
        UUID classId,
        UUID actorId,
        String reason,
        Instant occurredAt) {}
