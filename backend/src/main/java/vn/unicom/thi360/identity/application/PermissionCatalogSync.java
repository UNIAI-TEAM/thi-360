package vn.unicom.thi360.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.identity.domain.PermissionEntity;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.RolePermission;
import vn.unicom.thi360.identity.infrastructure.PermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RolePermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.shared.ids.Ids;

@Service
public class PermissionCatalogSync implements SmartInitializingSingleton {

    private static final Map<String, Set<Permission>> SYSTEM_ROLE_DEFAULTS = Map.of(
            "system_admin", EnumSet.allOf(Permission.class),
            "content_admin", EnumSet.of(Permission.CATALOG_VIEW, Permission.CATALOG_MODIFY),
            "operations", EnumSet.of(
                    Permission.USER_VIEW,
                    Permission.USER_MODIFY,
                    Permission.USER_LEARNING_VIEW,
                    Permission.CLASS_VIEW,
                    Permission.CLASS_MODIFY),
            "student", EnumSet.noneOf(Permission.class));

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PermissionCatalogSync(
            PermissionRepository permissionRepository,
            RoleRepository roleRepository,
            RolePermissionRepository rolePermissionRepository,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.permissionRepository = Objects.requireNonNull(permissionRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.rolePermissionRepository = Objects.requireNonNull(rolePermissionRepository);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void afterSingletonsInstantiated() {
        sync();
    }

    public void sync() {
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            Map<String, PermissionEntity> existingByCode = new HashMap<>();
            Map<Integer, PermissionEntity> existingByBit = new HashMap<>();
            for (PermissionEntity entity : permissionRepository.findAll()) {
                existingByCode.put(entity.getCode(), entity);
                existingByBit.put(entity.getBitIndex(), entity);
            }

            Map<String, UUID> idByCode = new HashMap<>();
            for (Permission p : Permission.values()) {
                PermissionEntity existing = existingByCode.get(p.code());
                if (existing != null) {
                    if (existing.getBitIndex() != p.bitIndex()) {
                        throw new IllegalStateException(
                                "Permission bit_index drift detected for code '"
                                        + p.code()
                                        + "': database has bit_index="
                                        + existing.getBitIndex()
                                        + " but enum defines bit_index="
                                        + p.bitIndex()
                                        + ". Add a Flyway migration to reconcile bit_index before startup.");
                    }
                    if (existing.syncMetadata(p.scope(), p.displayName(), p.object().code(), p.description(), now)) {
                        permissionRepository.save(existing);
                    }
                    idByCode.put(p.code(), existing.getId());
                } else {
                    PermissionEntity bitOwner = existingByBit.get(p.bitIndex());
                    if (bitOwner != null) {
                        throw new IllegalStateException(
                                "Permission bit_index conflict for code '"
                                        + p.code()
                                        + "' at bit_index="
                                        + p.bitIndex()
                                        + ": already occupied by '"
                                        + bitOwner.getCode()
                                        + "'");
                    }
                    PermissionEntity created = new PermissionEntity(
                            Ids.newId(),
                            p.code(),
                            p.bitIndex(),
                            p.scope(),
                            p.displayName(),
                            p.object().code(),
                            p.description(),
                            now);
                    permissionRepository.save(created);
                    idByCode.put(p.code(), created.getId());
                }
            }

            permissionRepository.flush();
            seedSystemRoleDefaults(idByCode, now);
        });
    }

    private void seedSystemRoleDefaults(Map<String, UUID> permissionIdByCode, Instant now) {
        for (Map.Entry<String, Set<Permission>> entry : SYSTEM_ROLE_DEFAULTS.entrySet()) {
            Set<Permission> defaults = entry.getValue();
            if (defaults.isEmpty()) {
                continue;
            }
            Role role = roleRepository.findByCodeNormalized(Normalizer.normalize(entry.getKey())).orElse(null);
            if (role == null || !role.isSystem()) {
                continue;
            }
            boolean neverEdited = Long.valueOf(0L).equals(role.getVersion()) && role.getUpdatedBy() == null;
            if (!neverEdited || rolePermissionRepository.existsByIdRoleId(role.getId())) {
                continue;
            }
            List<RolePermission> links = defaults.stream()
                    .map(p -> new RolePermission(
                            role.getId(),
                            Objects.requireNonNull(permissionIdByCode.get(p.code())),
                            null,
                            now))
                    .toList();
            rolePermissionRepository.saveAll(links);
        }
    }
}
