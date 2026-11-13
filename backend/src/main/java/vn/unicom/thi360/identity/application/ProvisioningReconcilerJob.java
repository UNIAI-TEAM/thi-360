package vn.unicom.thi360.identity.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.domain.IdentityProvider;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserIdentity;
import vn.unicom.thi360.identity.domain.UserRole;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserIdentityRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@Component
@Profile("worker")
public class ProvisioningReconcilerJob {

    public static final String JOB_NAME = "provisioning-reconcile";
    public static final Duration STALE_AGE = Duration.ofMinutes(10);
    private static final int BATCH_LIMIT = 100;

    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");

    private final UserAccountRepository userAccountRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final KeycloakIdentityClient keycloakClient;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    public ProvisioningReconcilerJob(
            UserAccountRepository userAccountRepository,
            UserIdentityRepository userIdentityRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            KeycloakIdentityClient keycloakClient,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.keycloakClient = keycloakClient;
        this.auditRecorder = auditRecorder;
        this.jdbcTemplate = jdbcTemplate;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${thi360.identity.provisioning-reconcile-interval:PT5M}")
    public ReconcileResult reconcile() {
        String prevTraceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        String prevJob = MDC.get("job");
        String prevJobId = MDC.get("jobId");

        if (prevTraceId == null || prevTraceId.isBlank()) {
            MDC.put(TraceIdFilter.MDC_TRACE_ID, Ids.newId().toString());
        }
        MDC.put("job", JOB_NAME);
        MDC.put("jobId", Ids.newId().toString());

        int promoted = 0;
        int deleted = 0;
        int failed = 0;
        int enabledSynced = 0;
        try {
            Instant cutoff = Instant.now(clock).minus(STALE_AGE);
            List<UUID> staleIds = jdbcTemplate.queryForList(
                    """
                    SELECT id
                    FROM identity.user_account
                    WHERE status = ?
                      AND deleted_at IS NULL
                      AND created_at < ?
                    ORDER BY created_at ASC
                    LIMIT ?
                    """,
                    UUID.class,
                    UserStatus.PROVISIONING.code(),
                    Timestamp.from(cutoff),
                    BATCH_LIMIT);

            for (UUID staleId : staleIds) {
                try {
                    ItemOutcome outcome = reconcileOne(staleId);
                    if (outcome == ItemOutcome.PROMOTED) {
                        promoted++;
                    } else if (outcome == ItemOutcome.DELETED) {
                        deleted++;
                    }
                } catch (RuntimeException ex) {
                    failed++;
                    jobLog.atWarn()
                            .setCause(ex)
                            .addKeyValue("provisionalUserId", staleId.toString())
                            .log("Failed to reconcile stuck PROVISIONING account");
                }
            }
            List<UUID> cleanupIds = jdbcTemplate.queryForList(
                    """
                    SELECT id
                    FROM identity.user_account
                    WHERE idp_cleanup_pending = true
                      AND deleted_at IS NOT NULL
                    ORDER BY updated_at ASC
                    LIMIT ?
                    """,
                    UUID.class,
                    BATCH_LIMIT);

            for (UUID cleanupId : cleanupIds) {
                try {
                    if (reconcilePendingDeletion(cleanupId)) {
                        deleted++;
                    }
                } catch (RuntimeException ex) {
                    failed++;
                    jobLog.atWarn()
                            .setCause(ex)
                            .addKeyValue("userId", cleanupId.toString())
                            .log("Failed to reconcile pending Keycloak deletion");
                }
            }

            List<UUID> enabledSyncIds = jdbcTemplate.queryForList(
                    """
                    SELECT id
                    FROM identity.user_account
                    WHERE idp_enabled_sync_pending = true
                      AND deleted_at IS NULL
                    ORDER BY updated_at ASC
                    LIMIT ?
                    """,
                    UUID.class,
                    BATCH_LIMIT);

            for (UUID syncId : enabledSyncIds) {
                try {
                    if (reconcilePendingEnabledSync(syncId)) {
                        enabledSynced++;
                    }
                } catch (RuntimeException ex) {
                    failed++;
                    jobLog.atWarn()
                            .setCause(ex)
                            .addKeyValue("userId", syncId.toString())
                            .log("Failed to reconcile pending Keycloak enabled state sync");
                }
            }

            if (!staleIds.isEmpty() || !cleanupIds.isEmpty() || !enabledSyncIds.isEmpty()) {
                jobLog.atInfo()
                        .addKeyValue("scanned", staleIds.size())
                        .addKeyValue("pendingDeletions", cleanupIds.size())
                        .addKeyValue("pendingEnabledSync", enabledSyncIds.size())
                        .addKeyValue("promoted", promoted)
                        .addKeyValue("deleted", deleted)
                        .addKeyValue("enabledSynced", enabledSynced)
                        .addKeyValue("failed", failed)
                        .log("Provisioning reconciler completed");
            }
            return new ReconcileResult(promoted, deleted, failed, enabledSynced);
        } finally {
            restoreMdc(TraceIdFilter.MDC_TRACE_ID, prevTraceId);
            restoreMdc("job", prevJob);
            restoreMdc("jobId", prevJobId);
        }
    }

    private ItemOutcome reconcileOne(UUID provisionalId) {
        return requiresNewTx.execute(status -> {
            List<UUID> locked = jdbcTemplate.queryForList(
                    """
                    SELECT id
                    FROM identity.user_account
                    WHERE id = ?
                      AND status = ?
                      AND deleted_at IS NULL
                    FOR UPDATE SKIP LOCKED
                    """,
                    UUID.class,
                    provisionalId,
                    UserStatus.PROVISIONING.code());
            if (locked.isEmpty()) {
                return ItemOutcome.SKIPPED;
            }

            UserAccount account = userAccountRepository.findById(provisionalId).orElse(null);
            if (account == null || account.getStatus() != UserStatus.PROVISIONING || account.isDeleted()) {
                return ItemOutcome.SKIPPED;
            }

            Optional<UUID> kcIdOpt = keycloakClient.findUserIdByUsername(account.getUsername());
            if (account.isIdpCleanupPending()) {
                kcIdOpt.ifPresent(keycloakClient::deleteUser);
                userAccountRepository.deleteById(provisionalId);
                return ItemOutcome.DELETED;
            }

            if (kcIdOpt.isPresent()) {
                UUID kcId = kcIdOpt.get();
                if (keycloakClient.hasPasswordCredential(kcId)) {
                    List<Role> intendedRoles = resolveIntendedRoles(account.getProvisioningRoleIds());
                    if (intendedRoles == null) {
                        jobLog.atWarn()
                                .addKeyValue("provisionalUserId", provisionalId.toString())
                                .addKeyValue("keycloakUserId", kcId.toString())
                                .log("Cleaning up PROVISIONING account with missing or invalid role intent");
                        keycloakClient.deleteUser(kcId);
                        userAccountRepository.deleteById(provisionalId);
                        return ItemOutcome.DELETED;
                    }

                    Instant now = Instant.now(clock);
                    int updated = userAccountRepository.promoteProvisionedUser(
                            provisionalId,
                            kcId,
                            UserStatus.PROVISIONING.code(),
                            UserStatus.ACTIVE.code(),
                            now,
                            account.getCreatedBy());
                    if (updated != 1) {
                        return ItemOutcome.SKIPPED;
                    }
                    UserAccount active = userAccountRepository.findById(kcId)
                            .orElseThrow(() -> new IllegalStateException("Promoted user missing: " + kcId));
                    if (userIdentityRepository
                            .findByProviderAndProviderSubject(IdentityProvider.LOCAL, kcId.toString())
                            .isEmpty()) {
                        userIdentityRepository.save(new UserIdentity(
                                Ids.newId(),
                                kcId,
                                IdentityProvider.LOCAL,
                                kcId.toString(),
                                active.getEmailNormalized(),
                                now));
                    }
                    if (userRoleRepository.findAllByIdUserId(kcId).isEmpty()) {
                        List<UserRole> assignments = intendedRoles.stream()
                                .map(role -> new UserRole(kcId, role.getId(), account.getCreatedBy(), now))
                                .toList();
                        userRoleRepository.saveAll(assignments);
                        userRoleRepository.flush();
                    }

                    Map<String, Object> after = new LinkedHashMap<>();
                    after.put("code", active.getCode());
                    after.put("username", active.getUsername());
                    after.put("status", active.getStatus().name());
                    after.put("mustChangePassword", active.isMustChangePassword());
                    after.put("roleCodes", intendedRoles.stream().map(Role::getCode).toList());
                    auditRecorder.record(AuditEvent.builder("IDENTITY", "USER_CREATED")
                            .target("USER", kcId.toString(), active.getUsername())
                            .after(after)
                            .reason("Reconciled from PROVISIONING")
                            .actor(account.getCreatedBy(), "system", List.of(), AuditActorType.SYSTEM)
                            .build());
                    return ItemOutcome.PROMOTED;
                }

                // Partial creation without password credential -> delete both sides
                keycloakClient.deleteUser(kcId);
            }

            userAccountRepository.deleteById(provisionalId);
            return ItemOutcome.DELETED;
        });
    }

    private boolean reconcilePendingDeletion(UUID userId) {
        return Boolean.TRUE.equals(requiresNewTx.execute(status -> {
            List<UUID> locked = jdbcTemplate.queryForList(
                    """
                    SELECT id
                    FROM identity.user_account
                    WHERE id = ?
                      AND idp_cleanup_pending = true
                      AND deleted_at IS NOT NULL
                    FOR UPDATE SKIP LOCKED
                    """,
                    UUID.class,
                    userId);
            if (locked.isEmpty()) {
                return false;
            }
            keycloakClient.deleteUser(userId);
            userAccountRepository.updateIdpCleanupPending(userId, false, Instant.now(clock));
            return true;
        }));
    }

    private boolean reconcilePendingEnabledSync(UUID userId) {
        return Boolean.TRUE.equals(requiresNewTx.execute(status -> {
            List<UUID> locked = jdbcTemplate.queryForList(
                    """
                    SELECT id
                    FROM identity.user_account
                    WHERE id = ?
                      AND idp_enabled_sync_pending = true
                      AND deleted_at IS NULL
                    FOR UPDATE SKIP LOCKED
                    """,
                    UUID.class,
                    userId);
            if (locked.isEmpty()) {
                return false;
            }
            UserAccount account = userAccountRepository.findById(userId).orElse(null);
            if (account == null || account.isDeleted() || !account.isIdpEnabledSyncPending()) {
                return false;
            }
            if (account.getStatus() == UserStatus.PROVISIONING) {
                return false;
            }
            boolean targetEnabled = account.getStatus() == UserStatus.ACTIVE;
            keycloakClient.setEnabled(userId, targetEnabled);
            userAccountRepository.updateIdpEnabledSyncPending(userId, false, Instant.now(clock));
            return true;
        }));
    }

    private List<Role> resolveIntendedRoles(String rawRoleIds) {
        if (rawRoleIds == null || rawRoleIds.isBlank()) {
            return null;
        }
        Set<UUID> orderedIds = new LinkedHashSet<>();
        for (String token : rawRoleIds.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                orderedIds.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        if (orderedIds.isEmpty()) {
            return null;
        }
        Map<UUID, Role> byId = new LinkedHashMap<>();
        for (Role role : roleRepository.findAllById(orderedIds)) {
            byId.put(role.getId(), role);
        }
        if (byId.size() != orderedIds.size()) {
            return null;
        }
        List<Role> roles = new ArrayList<>(orderedIds.size());
        for (UUID id : orderedIds) {
            Role role = byId.get(id);
            if (role == null || role.getStatus() != RoleStatus.ACTIVE) {
                return null;
            }
            roles.add(role);
        }
        return List.copyOf(roles);
    }

    private static void restoreMdc(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }

    private enum ItemOutcome {
        PROMOTED,
        DELETED,
        SKIPPED
    }

    public record ReconcileResult(int promoted, int deleted, int failed, int enabledSynced) {
        public ReconcileResult(int promoted, int deleted, int failed) {
            this(promoted, deleted, failed, 0);
        }
    }
}
