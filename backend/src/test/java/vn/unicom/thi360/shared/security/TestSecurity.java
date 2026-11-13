package vn.unicom.thi360.shared.security;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.event.command.CommandListener;
import io.lettuce.core.event.command.CommandStartedEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.shared.ids.Ids;

@TestConfiguration
public class TestSecurity {
    private static final UUID PLATFORM_ADMIN_TEST_ID =
            UUID.fromString("00000000-0000-7000-8000-0000000000ff");


    @Bean
    @ConditionalOnMissingBean(TestSecuritySupport.class)
    public TestSecuritySupport defaultTestSecuritySupport(
            @Autowired(required = false) PermissionCatalog permissionCatalog,
            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return new SecurityContextTestSecuritySupport(permissionCatalog, jdbcTemplate);
    }

    @Bean
    public LettuceCommandCounter lettuceCommandCounter() {
        return new LettuceCommandCounter();
    }

    @Bean
    public static BeanPostProcessor lettuceCommandListenerRegistrar(LettuceCommandCounter counter) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof LettuceConnectionFactory factory) {
                    AbstractRedisClient client = factory.getNativeClient();
                    if (client != null) {
                        client.addListener(counter);
                    }
                }
                return bean;
            }
        };
    }

    public static class LettuceCommandCounter implements CommandListener {
        private final AtomicBoolean recording = new AtomicBoolean(false);
        private final List<String> commands = Collections.synchronizedList(new ArrayList<>());

        public void resetAndStart() {
            commands.clear();
            recording.set(true);
        }

        public void stop() {
            recording.set(false);
        }

        public List<String> commands() {
            synchronized (commands) {
                return List.copyOf(commands);
            }
        }

        @Override
        public void commandStarted(CommandStartedEvent event) {
            if (recording.get()) {
                commands.add(String.valueOf(event.getCommand().getType()));
            }
        }
    }

    public record SimulatedPrincipal(
            UUID userId,
            String username,
            String roleCode,
            boolean platformAdmin,
            List<String> permissions
    ) {
    }

    public interface TestSecuritySupport {
        RequestPostProcessor apply(SimulatedPrincipal principal);
    }

    public static class SecurityContextTestSecuritySupport implements TestSecuritySupport {

        private final PermissionCatalog permissionCatalog;
        private final JdbcTemplate jdbcTemplate;

        public SecurityContextTestSecuritySupport() {
            this(null, null);
        }

        public SecurityContextTestSecuritySupport(
                PermissionCatalog permissionCatalog,
                JdbcTemplate jdbcTemplate) {
            this.permissionCatalog = permissionCatalog;
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public RequestPostProcessor apply(SimulatedPrincipal principal) {
            Realm realm = principal.platformAdmin() ? Realm.MASTER : Realm.APP;
            Access access;
            PermissionSet perms;

            if (principal.platformAdmin()) {
                access = Access.MANAGE;
                perms = permissionCatalog != null
                        ? PermissionSet.all(permissionCatalog.allCodes())
                        : PermissionSet.all();
            } else if (principal.roleCode() != null && !principal.roleCode().isBlank()) {
                ResolvedRole resolved = resolveRole(principal.roleCode());
                access = resolved.access();
                perms = resolved.permissions();
            } else {
                access = Access.MANAGE;
                perms = permissionCatalog != null
                        ? permissionCatalog.encodeCodes(principal.permissions())
                        : PermissionSet.ofCodes(principal.permissions());
            }

            CurrentUser currentUser = new CurrentUser(
                    principal.userId(),
                    principal.username(),
                    realm,
                    access,
                    perms);
            CurrentUserAuthenticationToken auth = new CurrentUserAuthenticationToken(currentUser, "test-sid");
            RequestPostProcessor authProcessor = SecurityMockMvcRequestPostProcessors.authentication(auth);
            return request -> {
                request.setAttribute(AdminOriginFilter.SIMULATED_PRINCIPAL_ATTR, principal);
                return authProcessor.postProcessRequest(request);
            };
        }

        private ResolvedRole resolveRole(String roleCode) {
            String normalized = roleCode.trim().toLowerCase(Locale.ROOT);
            if ("platform_admin".equals(normalized)) {
                PermissionSet all = permissionCatalog != null
                        ? PermissionSet.all(permissionCatalog.allCodes())
                        : PermissionSet.all();
                return new ResolvedRole(Access.MANAGE, all);
            }
            if (jdbcTemplate == null) {
                Access access = "student".equals(normalized) ? Access.EXAM : Access.MANAGE;
                return new ResolvedRole(access, PermissionSet.empty());
            }

            List<RoleRow> roles = jdbcTemplate.query(
                    "SELECT id, access, status FROM identity.role WHERE code_normalized = ?",
                    (rs, rowNum) -> new RoleRow(
                            rs.getObject(1, UUID.class),
                            Access.fromCode(rs.getShort(2)),
                            rs.getShort(3)),
                    normalized);
            if (roles.isEmpty()) {
                throw new IllegalArgumentException("Unknown role code in TestSecurity.asRole: " + roleCode);
            }
            RoleRow role = roles.getFirst();
            if (role.status() != 1) {
                return new ResolvedRole(role.access(), PermissionSet.empty());
            }

            List<String> permCodes = jdbcTemplate.queryForList(
                    """
                    SELECT p.code
                    FROM identity.role_permission rp
                    JOIN identity.permission p ON p.id = rp.permission_id
                    WHERE rp.role_id = ?
                    """,
                    String.class,
                    role.id());
            PermissionSet perms = permissionCatalog != null
                    ? permissionCatalog.encodeCodes(permCodes)
                    : PermissionSet.ofCodes(permCodes);
            return new ResolvedRole(role.access(), perms);
        }

        private record RoleRow(UUID id, Access access, short status) {
        }

        private record ResolvedRole(Access access, PermissionSet permissions) {
        }
    }

    public static RequestPostProcessor asUser(TestSecuritySupport support, String... permissions) {
        SimulatedPrincipal principal = new SimulatedPrincipal(
                Ids.newId(),
                "test.user",
                null,
                false,
                permissions == null ? List.of() : List.of(permissions));
        return support.apply(principal);
    }

    public static RequestPostProcessor asUser(
            TestSecuritySupport support, UUID userId, String username, String... permissions) {
        SimulatedPrincipal principal = new SimulatedPrincipal(
                userId,
                username,
                null,
                false,
                permissions == null ? List.of() : List.of(permissions));
        return support.apply(principal);
    }

    public static RequestPostProcessor asRole(TestSecuritySupport support, String roleCode) {
        SimulatedPrincipal principal = new SimulatedPrincipal(
                Ids.newId(),
                "test." + roleCode.toLowerCase(Locale.ROOT),
                roleCode,
                false,
                List.of());
        return support.apply(principal);
    }

    public static RequestPostProcessor asPlatformAdmin(TestSecuritySupport support) {
        SimulatedPrincipal principal = new SimulatedPrincipal(
                PLATFORM_ADMIN_TEST_ID,
                "platform",
                "PLATFORM_ADMIN",
                true,
                List.of());
        return support.apply(principal);
    }

    public static RequestPostProcessor asStudent(TestSecuritySupport support) {
        return asRole(support, "STUDENT");
    }
}
