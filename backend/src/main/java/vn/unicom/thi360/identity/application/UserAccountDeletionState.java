package vn.unicom.thi360.identity.application;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.identity.spi.AccountDeletionState;

@Component
public class UserAccountDeletionState implements AccountDeletionState {

    private final JdbcTemplate jdbcTemplate;

    public UserAccountDeletionState(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockAndIsDeleted(UUID userId) {
        if (userId == null) {
            return false;
        }
        List<Timestamp> rows = jdbcTemplate.query(
                "SELECT deleted_at FROM identity.user_account WHERE id = ? FOR SHARE",
                (rs, rowNum) -> rs.getTimestamp(1),
                userId);
        return !rows.isEmpty() && rows.getFirst() != null;
    }
}
