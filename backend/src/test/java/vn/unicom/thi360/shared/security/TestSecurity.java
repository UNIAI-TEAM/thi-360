package vn.unicom.thi360.shared.security;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.shared.ids.Ids;

@TestConfiguration
public class TestSecurity {

    @Bean
    @ConditionalOnMissingBean(TestSecuritySupport.class)
    public TestSecuritySupport defaultTestSecuritySupport() {
        return new SecurityContextTestSecuritySupport();
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
        @Override
        public RequestPostProcessor apply(SimulatedPrincipal principal) {
            Realm realm = principal.platformAdmin() ? Realm.MASTER : Realm.APP;
            Access access = (principal.platformAdmin() || !"STUDENT".equalsIgnoreCase(principal.roleCode()))
                    ? Access.MANAGE
                    : Access.EXAM;
            PermissionSet perms = principal.platformAdmin()
                    ? PermissionSet.all()
                    : PermissionSet.ofCodes(principal.permissions());
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

    public static RequestPostProcessor asRole(TestSecuritySupport support, String roleCode) {
        SimulatedPrincipal principal = new SimulatedPrincipal(
                Ids.newId(),
                "test." + roleCode.toLowerCase(),
                roleCode,
                false,
                List.of());
        return support.apply(principal);
    }

    public static RequestPostProcessor asPlatformAdmin(TestSecuritySupport support) {
        SimulatedPrincipal principal = new SimulatedPrincipal(
                Ids.newId(),
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
