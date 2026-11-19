package vn.unicom.thi360.identity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.identity.domain.PermissionEntity;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.RolePermission;
import vn.unicom.thi360.identity.domain.RolePermissionId;
import vn.unicom.thi360.identity.infrastructure.PermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RolePermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.shared.ids.Ids;

@Service
public class PermissionCatalogSync implements SmartInitializingSingleton {

    /**
     * Khóa một lần trong {@code shared.system_setting}: trên DB đợt 2 đã tồn tại, mọi dòng quyền
     * đều có sẵn nên nhánh "quyền vừa được tạo" không chạy và {@code content_admin} nguyên trạng
     * (chỉ 2 grant danh mục) sẽ không bao giờ nhận 5 quyền mặc định mới. Lần đầu đồng bộ sau khi
     * triển khai, nếu vai trò hệ thống này còn đúng bộ grant cũ thì gán thêm các mặc định mới;
     * đánh dấu để lần sau không đánh giá lại — vì vậy vai trò đã tùy biến hay quyền bị gỡ cố ý
     * không bị khôi phục (giữ lựa chọn của quản trị viên, spec mục 7).
     */
    private static final String CONTENT_ADMIN_UPGRADE_KEY =
            "identity.permission_upgrade.content_admin_defaults";
    private static final Set<Permission> CONTENT_ADMIN_LEGACY = Set.of(
            Permission.CATALOG_VIEW,
            Permission.CATALOG_MODIFY);

    /**
     * Mặc định của {@code content_admin} ở đợt 2: chỉ dùng cho nâng cấp một lần bên dưới. Chín quyền
     * của đợt 3 đến qua nhánh "quyền vừa được tạo" ({@link #grantNewPermissionsToSystemRoles}).
     */
    private static final Set<Permission> CONTENT_ADMIN_WAVE2_DEFAULTS = EnumSet.of(
            Permission.CATALOG_VIEW,
            Permission.CATALOG_MODIFY,
            Permission.QUESTION_VIEW,
            Permission.QUESTION_MODIFY,
            Permission.EXAM_VIEW,
            Permission.EXAM_MODIFY,
            Permission.EXAM_PUBLISH);

    private static final Map<String, Set<Permission>> SYSTEM_ROLE_DEFAULTS = Map.of(
            "system_admin", EnumSet.allOf(Permission.class),
            "content_admin", EnumSet.of(
                    Permission.CATALOG_VIEW,
                    Permission.CATALOG_MODIFY,
                    Permission.QUESTION_VIEW,
                    Permission.QUESTION_MODIFY,
                    Permission.EXAM_VIEW,
                    Permission.EXAM_MODIFY,
                    Permission.EXAM_PUBLISH,
                    Permission.GRADING_VIEW,
                    Permission.GRADING_MODIFY,
                    Permission.ATTEMPT_VIEW),
            "operations", EnumSet.of(
                    Permission.USER_VIEW,
                    Permission.USER_MODIFY,
                    Permission.USER_LEARNING_VIEW,
                    Permission.CLASS_VIEW,
                    Permission.CLASS_MODIFY,
                    Permission.PROMOTION_VIEW,
                    Permission.PROMOTION_MODIFY,
                    Permission.ORDER_VIEW,
                    Permission.ORDER_RECOVER,
                    Permission.ATTEMPT_VIEW,
                    Permission.ATTEMPT_COMPENSATE),
            "student", EnumSet.noneOf(Permission.class));

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final TransactionTemplate transactionTemplate;
    private final AuthzSnapshotService authzSnapshotService;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public PermissionCatalogSync(
            PermissionRepository permissionRepository,
            RoleRepository roleRepository,
            RolePermissionRepository rolePermissionRepository,
            TransactionTemplate transactionTemplate,
            AuthzSnapshotService authzSnapshotService,
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            Clock clock) {
        this.permissionRepository = Objects.requireNonNull(permissionRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.rolePermissionRepository = Objects.requireNonNull(rolePermissionRepository);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.authzSnapshotService = Objects.requireNonNull(authzSnapshotService);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.clock = Objects.requireNonNull(clock);
        this.objectMapper = Objects.requireNonNull(objectMapper);
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
            List<Permission> createdPermissions = new ArrayList<>();
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
                    createdPermissions.add(p);
                }
            }

            permissionRepository.flush();
            seedSystemRoleDefaults(idByCode, now);
            grantNewPermissionsToSystemRoles(createdPermissions, idByCode, now);
            upgradeContentAdminDefaultsOnce(idByCode, now);
        });
    }

    /**
     * Khi sync() tạo mới một dòng quyền (ví dụ nâng cấp từ đợt 1), gán ngay quyền đó cho các vai
     * trò hệ thống đã tồn tại mà SYSTEM_ROLE_DEFAULTS liệt kê quyền này — kể cả vai trò đã bị
     * chỉnh sửa. Quyền tồn tại từ trước thì không đụng (giữ lựa chọn của quản trị viên, kể cả
     * việc đã gỡ một quyền khỏi vai trò). Sau khi gán, xóa ảnh chụp quyền của người dùng thuộc
     * vai trò để thay đổi có hiệu lực ở request kế tiếp (spec đợt 1 mục 5.7).
     */
    private void grantNewPermissionsToSystemRoles(
            List<Permission> createdPermissions, Map<String, UUID> idByCode, Instant now) {
        if (createdPermissions.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Set<Permission>> entry : SYSTEM_ROLE_DEFAULTS.entrySet()) {
            Role role = roleRepository.findByCodeNormalized(Normalizer.normalize(entry.getKey())).orElse(null);
            if (role == null || !role.isSystem()) {
                continue;
            }
            List<RolePermission> newLinks = new ArrayList<>();
            for (Permission p : createdPermissions) {
                if (!entry.getValue().contains(p)) {
                    continue;
                }
                UUID permissionId = Objects.requireNonNull(idByCode.get(p.code()));
                RolePermissionId linkId = new RolePermissionId(role.getId(), permissionId);
                if (!rolePermissionRepository.existsById(linkId)) {
                    newLinks.add(new RolePermission(role.getId(), permissionId, null, now));
                }
            }
            if (!newLinks.isEmpty()) {
                rolePermissionRepository.saveAll(newLinks);
                rolePermissionRepository.flush();
                authzSnapshotService.evictByRole(role.getId());
            }
        }
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

    /**
     * Nâng cấp một lần cho DB đợt 2 đã chạy trước khi mặc định {@code content_admin} được mở
     * rộng: mọi dòng quyền đã tồn tại nên cả {@code seedSystemRoleDefaults} (bỏ qua vai trò có
     * liên kết) lẫn {@code grantNewPermissionsToSystemRoles} (không có quyền mới tạo) đều bỏ
     * qua nó. Nếu vai trò hệ thống vẫn đúng bộ grant cũ (hai quyền danh mục) thì gán thêm các
     * mặc định còn thiếu; vai trò đã bị tùy biến (thêm hoặc bớt quyền) thì không đụng. Kết quả
     * được khóa vào {@code shared.system_setting} nên chạy lại là no-op và không bao giờ khôi
     * phục một grant bị gỡ cố ý. Ảnh chụp quyền của người dùng thuộc vai trò bị xóa sau commit.
     */
    private void upgradeContentAdminDefaultsOnce(Map<String, UUID> idByCode, Instant now) {
        int inserted = jdbc.update(
                "INSERT INTO shared.system_setting (key, value) VALUES (?, ?::jsonb)"
                        + " ON CONFLICT (key) DO NOTHING",
                CONTENT_ADMIN_UPGRADE_KEY,
                "{\"applied\": false}");
        if (inserted == 0) {
            return;
        }

        boolean applied = false;
        int granted = 0;
        String reason = "no system content_admin role";
        Role role = roleRepository
                .findByCodeNormalized(Normalizer.normalize("content_admin"))
                .filter(Role::isSystem)
                .orElse(null);
        if (role != null) {
            Set<UUID> defaults = CONTENT_ADMIN_WAVE2_DEFAULTS.stream()
                    .map(p -> Objects.requireNonNull(idByCode.get(p.code())))
                    .collect(Collectors.toSet());
            Set<UUID> legacy = CONTENT_ADMIN_LEGACY.stream()
                    .map(p -> Objects.requireNonNull(idByCode.get(p.code())))
                    .collect(Collectors.toSet());
            Set<UUID> actual = rolePermissionRepository.findAllByIdRoleId(role.getId()).stream()
                    .map(rp -> rp.getId().getPermissionId())
                    .collect(Collectors.toSet());
            if (actual.equals(legacy)) {
                List<RolePermission> newLinks = defaults.stream()
                        .filter(permissionId -> !actual.contains(permissionId))
                        .map(permissionId -> new RolePermission(role.getId(), permissionId, null, now))
                        .toList();
                rolePermissionRepository.saveAll(newLinks);
                rolePermissionRepository.flush();
                authzSnapshotService.evictByRole(role.getId());
                applied = true;
                granted = newLinks.size();
                reason = null;
            } else {
                reason = actual.containsAll(defaults) ? "defaults already granted" : "role customized";
            }
        }
        ObjectNode marker = objectMapper.createObjectNode()
                .put("applied", applied)
                .put("outcome", applied ? "applied" : "skipped")
                .put("granted", granted);
        if (reason == null) {
            marker.putNull("reason");
        } else {
            marker.put("reason", reason);
        }
        jdbc.update(
                "UPDATE shared.system_setting SET value = ?::jsonb, updated_at = ? WHERE key = ?",
                marker.toString(),
                java.sql.Timestamp.from(now),
                CONTENT_ADMIN_UPGRADE_KEY);
    }
}
