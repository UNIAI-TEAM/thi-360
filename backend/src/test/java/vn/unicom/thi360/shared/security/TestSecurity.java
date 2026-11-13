package vn.unicom.thi360.shared.security;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.shared.ids.Ids;

@TestConfiguration
public class TestSecurity {

    @Bean
    @ConditionalOnMissingBean(TestSecuritySupport.class)
    public TestSecuritySupport defaultTestSecuritySupport() {
        return new NoopTestSecuritySupport();
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

    public static class NoopTestSecuritySupport implements TestSecuritySupport {
        @Override
        public RequestPostProcessor apply(SimulatedPrincipal principal) {
            return request -> {
                request.setAttribute(SimulatedPrincipal.class.getName(), principal);
                return request;
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
