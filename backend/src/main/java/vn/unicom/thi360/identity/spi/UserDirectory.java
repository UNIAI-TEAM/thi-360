package vn.unicom.thi360.identity.spi;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserDirectory {

    Optional<UserSummary> findById(UUID userId);

    Map<UUID, UserSummary> findByIds(Collection<UUID> userIds);

    record UserSummary(
            UUID id,
            String username,
            String fullName,
            boolean platformAdmin
    ) {
        public String displayName() {
            return (fullName != null && !fullName.isBlank()) ? fullName : username;
        }
    }
}
