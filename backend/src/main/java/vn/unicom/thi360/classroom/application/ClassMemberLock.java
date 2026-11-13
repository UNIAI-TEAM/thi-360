package vn.unicom.thi360.classroom.application;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ClassMemberLock {

    private final JdbcTemplate jdbcTemplate;

    ClassMemberLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void lockUser(UUID userId) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", rs -> null, lockKey(userId));
    }

    void lockUsers(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(this::lockUser);
    }

    static long lockKey(UUID userId) {
        return userId.getMostSignificantBits() ^ userId.getLeastSignificantBits();
    }
}
