package vn.unicom.thi360.identity.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.identity.domain.PlatformAdmin;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.infrastructure.PlatformAdminRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.spi.UserDirectory;

@Service
@Transactional(readOnly = true)
public class UserDirectoryImpl implements UserDirectory {

    private final UserAccountRepository userAccountRepository;
    private final PlatformAdminRepository platformAdminRepository;

    public UserDirectoryImpl(
            UserAccountRepository userAccountRepository,
            PlatformAdminRepository platformAdminRepository) {
        this.userAccountRepository = userAccountRepository;
        this.platformAdminRepository = platformAdminRepository;
    }

    @Override
    public Optional<UserSummary> findById(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userAccountRepository.findById(userId)
                .map(UserDirectoryImpl::toSummary)
                .or(() -> platformAdminRepository.findById(userId).map(UserDirectoryImpl::toSummary));
    }

    @Override
    public Map<UUID, UserSummary> findByIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>();
        for (UUID id : userIds) {
            if (id != null) {
                uniqueIds.add(id);
            }
        }
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UserSummary> result = new LinkedHashMap<>();
        for (UserAccount account : userAccountRepository.findAllByIdIn(uniqueIds)) {
            result.put(account.getId(), toSummary(account));
        }
        if (result.size() < uniqueIds.size()) {
            Set<UUID> missing = new LinkedHashSet<>(uniqueIds);
            missing.removeAll(result.keySet());
            for (PlatformAdmin admin : platformAdminRepository.findAllByIdIn(missing)) {
                result.put(admin.getId(), toSummary(admin));
            }
        }
        return Map.copyOf(result);
    }

    @Override
    public Optional<UserDetailSummary> findDetailById(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userAccountRepository.findById(userId)
                .map(UserDirectoryImpl::toDetailSummary)
                .or(() -> platformAdminRepository.findById(userId).map(UserDirectoryImpl::toDetailSummary));
    }

    @Override
    public Map<UUID, UserDetailSummary> findDetailsByIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>();
        for (UUID id : userIds) {
            if (id != null) {
                uniqueIds.add(id);
            }
        }
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UserDetailSummary> result = new LinkedHashMap<>();
        for (UserAccount account : userAccountRepository.findAllByIdIn(uniqueIds)) {
            result.put(account.getId(), toDetailSummary(account));
        }
        if (result.size() < uniqueIds.size()) {
            Set<UUID> missing = new LinkedHashSet<>(uniqueIds);
            missing.removeAll(result.keySet());
            for (PlatformAdmin admin : platformAdminRepository.findAllByIdIn(missing)) {
                result.put(admin.getId(), toDetailSummary(admin));
            }
        }
        return Map.copyOf(result);
    }

    private static UserSummary toSummary(UserAccount account) {
        return new UserSummary(
                account.getId(),
                account.getUsername(),
                account.getFullName(),
                false);
    }

    private static UserSummary toSummary(PlatformAdmin admin) {
        return new UserSummary(
                admin.getId(),
                admin.getUsername(),
                admin.getUsername(),
                true);
    }

    private static UserDetailSummary toDetailSummary(UserAccount account) {
        return new UserDetailSummary(
                account.getId(),
                account.getCode(),
                account.getUsername(),
                account.getFullName(),
                account.getEmail(),
                account.getPhone(),
                account.getStatus().name(),
                account.isDeleted(),
                false);
    }

    private static UserDetailSummary toDetailSummary(PlatformAdmin admin) {
        return new UserDetailSummary(
                admin.getId(),
                null,
                admin.getUsername(),
                admin.getUsername(),
                null,
                null,
                "ACTIVE",
                false,
                true);
    }
}
