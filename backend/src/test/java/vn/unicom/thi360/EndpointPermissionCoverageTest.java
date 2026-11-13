package vn.unicom.thi360;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.unicom.thi360.shared.security.RequirePermission;

class EndpointPermissionCoverageTest extends AbstractIntegrationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void allAdminEndpointsMustDeclareRequirePermission() {
        List<String> missingHandlers = new ArrayList<>();
        int scannedAdminHandlers = 0;

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();
            Set<String> patterns = info.getPatternValues();
            boolean isAdminEndpoint = patterns.stream()
                    .anyMatch(p -> p.equals("/api/v1/admin") || p.startsWith("/api/v1/admin/"));
            if (!isAdminEndpoint) {
                continue;
            }
            scannedAdminHandlers++;
            RequirePermission methodAnnotation =
                    AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequirePermission.class);
            RequirePermission classAnnotation =
                    AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequirePermission.class);
            RequirePermission resolved = methodAnnotation != null ? methodAnnotation : classAnnotation;
            if (resolved == null || resolved.value().length == 0) {
                missingHandlers.add(patterns + " -> " + handlerMethod.getShortLogMessage());
            }
        }

        assertThat(scannedAdminHandlers).isGreaterThanOrEqualTo(16);
        assertThat(missingHandlers)
                .as("Every /api/v1/admin/** handler must declare @RequirePermission")
                .isEmpty();
    }
}
