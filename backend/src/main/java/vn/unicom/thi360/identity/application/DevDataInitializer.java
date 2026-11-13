package vn.unicom.thi360.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.domain.IdentityProvider;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserIdentity;
import vn.unicom.thi360.identity.domain.UserRole;
import vn.unicom.thi360.identity.domain.UserRoleId;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserIdentityRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.ids.Ids;

@Component
@Profile("dev")
public class DevDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);
    private static final String MODULE = "identity";
    private static final String SEED_REASON = "dev_seed";

    private static final List<SeedUser> SEED_USERS = List.of(
            new SeedUser("sys.admin", "sys.admin@thi360.vn", "Hệ thống", "Quản trị", "Quản trị hệ thống", "SYSTEM_ADMIN"),
            new SeedUser("content.admin", "content.admin@thi360.vn", "Nội dung", "Quản trị", "Quản trị nội dung", "CONTENT_ADMIN"),
            new SeedUser("ops.admin", "ops.admin@thi360.vn", "Vận hành", "CSKH", "Vận hành CSKH", "OPERATIONS"),
            new SeedUser("student01", "student01@thi360.vn", "Học viên", "01", "Học viên 01", "STUDENT"),
            new SeedUser("student02", "student02@thi360.vn", "Học viên", "02", "Học viên 02", "STUDENT"),
            new SeedUser("student03", "student03@thi360.vn", "Học viên", "03", "Học viên 03", "STUDENT"),
            new SeedUser("student04", "student04@thi360.vn", "Học viên", "04", "Học viên 04", "STUDENT"),
            new SeedUser("student05", "student05@thi360.vn", "Học viên", "05", "Học viên 05", "STUDENT"));

    private final KeycloakIdentityClient keycloakClient;
    private final UserAccountRepository userAccountRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthzSnapshotService authzSnapshotService;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final String seedPassword;

    @Autowired
    public DevDataInitializer(
            KeycloakIdentityClient keycloakClient,
            UserAccountRepository userAccountRepository,
            UserIdentityRepository userIdentityRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            @Autowired(required = false) AuthzSnapshotService authzSnapshotService,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            Clock clock,
            @Value("${thi360.dev.seed-password:Thi360@dev}") String seedPassword) {
        this.keycloakClient = Objects.requireNonNull(keycloakClient);
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.userIdentityRepository = Objects.requireNonNull(userIdentityRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository);
        this.authzSnapshotService = authzSnapshotService;
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.clock = Objects.requireNonNull(clock);
        this.seedPassword = Objects.requireNonNull(seedPassword);
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        int createdUsers = 0;
        for (SeedUser spec : SEED_USERS) {
            if (ensureSeedUser(spec)) {
                createdUsers++;
            }
        }
        log.atInfo()
                .addKeyValue("event", "dev_data.seeded")
                .addKeyValue("totalUsers", SEED_USERS.size())
                .addKeyValue("createdUsers", createdUsers)
                .log("Dev seed accounts verified");
    }

    private boolean ensureSeedUser(SeedUser spec) {
        String normalizedUsername = Normalizer.normalize(spec.username());
        Optional<UserAccount> existingAccount = userAccountRepository.findActiveByUsernameNormalized(normalizedUsername);

        UUID keycloakUserId = existingAccount
                .map(UserAccount::getId)
                .orElseGet(() -> ensureKeycloakUser(spec));

        boolean[] createdAccount = {false};
        boolean[] roleAdded = {false};

        transactionTemplate.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            UserAccount account = userAccountRepository.findActiveByUsernameNormalized(normalizedUsername).orElse(null);
            UUID userId;
            if (account == null) {
                Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
                String code = String.format("U%06d", Objects.requireNonNull(seq));
                account = new UserAccount(
                        keycloakUserId,
                        code,
                        spec.username(),
                        spec.fullName(),
                        spec.email(),
                        null,
                        UserSource.ADMIN_CREATED,
                        UserStatus.ACTIVE,
                        false,
                        now,
                        null);
                userAccountRepository.save(account);
                createdAccount[0] = true;
                userId = keycloakUserId;
                auditRecorder.record(AuditEvent.builder(MODULE, "USER_CREATED")
                        .target("USER", userId.toString(), spec.username())
                        .after(userAuditState(code, spec))
                        .reason(SEED_REASON)
                        .actorType(AuditActorType.SYSTEM)
                        .build());
            } else {
                userId = account.getId();
            }

            if (userIdentityRepository
                    .findByProviderAndProviderSubject(IdentityProvider.LOCAL, userId.toString())
                    .isEmpty()) {
                userIdentityRepository.save(new UserIdentity(
                        Ids.newId(),
                        userId,
                        IdentityProvider.LOCAL,
                        userId.toString(),
                        spec.email(),
                        now));
            }

            Role role = roleRepository.findByCodeNormalized(Normalizer.normalize(spec.roleCode()))
                    .orElseThrow(() -> new IllegalStateException("Missing seeded role: " + spec.roleCode()));
            UserRoleId roleLinkId = new UserRoleId(userId, role.getId());
            if (!userRoleRepository.existsById(roleLinkId)) {
                List<String> beforeCodes = loadRoleCodes(userId);
                userRoleRepository.save(new UserRole(userId, role.getId(), null, now));
                roleAdded[0] = true;

                List<String> afterCodes = Stream.concat(beforeCodes.stream(), Stream.of(role.getCode()))
                        .distinct()
                        .sorted()
                        .toList();
                auditRecorder.record(AuditEvent.builder(MODULE, "USER_ROLES_CHANGED")
                        .target("USER", userId.toString(), spec.username())
                        .before(Map.of("roleCodes", beforeCodes))
                        .after(Map.of("roleCodes", afterCodes))
                        .reason(SEED_REASON)
                        .actorType(AuditActorType.SYSTEM)
                        .build());
            }
        });

        if (roleAdded[0] && authzSnapshotService != null) {
            authzSnapshotService.evict(keycloakUserId);
        }
        return createdAccount[0];
    }

    private List<String> loadRoleCodes(UUID userId) {
        List<UUID> roleIds = userRoleRepository.findAllByIdUserId(userId).stream()
                .map(ur -> ur.getId().getRoleId())
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.findAllById(roleIds).stream()
                .map(Role::getCode)
                .sorted()
                .toList();
    }

    private static Map<String, Object> userAuditState(String code, SeedUser spec) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("code", code);
        state.put("username", spec.username());
        state.put("fullName", spec.fullName());
        state.put("email", spec.email());
        state.put("source", UserSource.ADMIN_CREATED.name());
        state.put("status", UserStatus.ACTIVE.name());
        state.put("mustChangePassword", false);
        return state;
    }

    private UUID ensureKeycloakUser(SeedUser spec) {
        Optional<UUID> existing = keycloakClient.findUserIdByUsername(spec.username());
        if (existing.isPresent()) {
            UUID userId = existing.get();
            keycloakClient.setEnabled(userId, true);
            keycloakClient.setPassword(userId, seedPassword, false);
            return userId;
        }
        try {
            return keycloakClient.createUser(new CreateUserCommand(
                    spec.username(),
                    spec.email(),
                    spec.firstName(),
                    spec.lastName(),
                    seedPassword,
                    true,
                    false,
                    true));
        } catch (ConflictException ex) {
            return keycloakClient.findUserIdByUsername(spec.username())
                    .orElseThrow(() -> ex);
        }
    }

    private record SeedUser(
            String username,
            String email,
            String firstName,
            String lastName,
            String fullName,
            String roleCode
    ) {
    }
}
