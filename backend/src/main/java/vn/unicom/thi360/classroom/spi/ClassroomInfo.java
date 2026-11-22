package vn.unicom.thi360.classroom.spi;

import java.util.UUID;

public record ClassroomInfo(
        UUID id,
        String code,
        String name,
        boolean active,
        boolean deleted) {}
