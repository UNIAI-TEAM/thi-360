package vn.unicom.thi360.identity.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

            if (!staleIds.isEmpty()) {
                jobLog.atInfo()
                        .addKeyValue("scanned", staleIds.size())
                        .addKeyValue("promoted", promoted)
                        .addKeyValue("deleted", deleted)
                        .addKeyValue("failed", failed)
                        .log("Provisioning reconciler completed");
            }
            return new ReconcileResult(promoted, deleted, failed);
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
            if (kcIdOpt.isPresent()) {
                UUID kcId = kcIdOpt.get();
                if (keycloakClient.hasPasswordCredential(kcId)) {
                    Instant now = Instant.now(clock);
                    int updated = userAccountRepository.promoteProvisionedUser(
                            provisionalId,
                            kcId,
                            UserStatus.PROVISIONING.code(),
                            UserStatus.ACTIVE.code(),
                            now,
                            null);
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
                        Role studentRole = roleRepository.findByCodeNormalized("student").orElse(null);
                        if (studentRole != null) {
                            userRoleRepository.save(new UserRole(kcId, studentRole.getId(), null, now));
                        }
                    }

                    Map<String, Object> after = new LinkedHashMap<>();
                    after.put("code", active.getCode());
                    after.put("username", active.getUsername());
                    after.put("status", active.getStatus().name());
                    auditRecorder.record(AuditEvent.builder("IDENTITY", "USER_CREATED")
                            .target("USER", kcId.toString(), active.getUsername())
                            .after(after)
                            .reason("Reconciled from PROVISIONING")
                            .actor(null, "system", List.of(), AuditActorType.SYSTEM)
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

    public record ReconcileResult(int promoted, int deleted, int failed) {
    }
}
