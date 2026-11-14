package vn.unicom.thi360.identity.spi;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserDirectory {

    Optional<UserSummary> findById(UUID userId);
    Optional<UserSummary> findActiveByUsername(String username);


    Map<UUID, UserSummary> findByIds(Collection<UUID> userIds);

    Optional<UserDetailSummary> findDetailById(UUID userId);

    Map<UUID, UserDetailSummary> findDetailsByIds(Collection<UUID> userIds);

    default Optional<String> findDeleteReason(UUID userId) {
        return Optional.empty();
    }

    record UserDetailSummary(
            UUID id,
            String code,
            String username,
            String fullName,
            String email,
            String phone,
            String status,
            boolean deleted,
            boolean platformAdmin
    ) {
        public boolean isAvailableForMembership() {
            return !platformAdmin && !deleted && "ACTIVE".equals(status);
        }
    }

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
