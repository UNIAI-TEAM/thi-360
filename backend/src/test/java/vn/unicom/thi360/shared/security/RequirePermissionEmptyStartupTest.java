package vn.unicom.thi360.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * AC-5 (UNI-2607): startup từ chối chạy khi một handler có {@code @RequirePermission} mà cả
 * {@code value} lẫn {@code anyOf} đều rỗng — nghĩa là endpoint không chặn được ai.
 */
class RequirePermissionEmptyStartupTest {

    @RestController
    static class EmptyAnnotationController {
        @GetMapping("/api/v1/admin/__empty")
        @RequirePermission
        String empty() {
            return "x";
        }

        @GetMapping("/api/v1/admin/__empty_type")
        String other() {
            return "y";
        }
    }

    @RestController
    static class ValidAnnotationController {
        @GetMapping("/api/v1/admin/__valid")
        @RequirePermission(Permissions.USER_VIEW)
        String valid() {
            return "x";
        }

        @GetMapping("/api/v1/admin/__valid_anyof")
        @RequirePermission(anyOf = {Permissions.QUESTION_VIEW})
        String validAnyOf() {
            return "x";
        }
    }

    @Test
    void findHandlersWithEmptyRequirePermission_listsTheEmptyHandler_only() throws Exception {
        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        EmptyAnnotationController controller = new EmptyAnnotationController();

        mapping.registerMapping(
                RequestMappingInfo.paths("/api/v1/admin/__empty").build(),
                controller,
                EmptyAnnotationController.class.getDeclaredMethod("empty"));

        List<String> invalid = RequirePermissionAspect.findHandlersWithEmptyRequirePermission(mapping);
        assertThat(invalid).hasSize(1);
        assertThat(invalid.get(0)).contains("EmptyAnnotationController");
    }

    @Test
    void findHandlersWithEmptyRequirePermission_ignoresValueAndAnyOfHandlers() throws Exception {
        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        ValidAnnotationController controller = new ValidAnnotationController();

        mapping.registerMapping(
                RequestMappingInfo.paths("/api/v1/admin/__valid").build(),
                controller,
                ValidAnnotationController.class.getDeclaredMethod("valid"));
        mapping.registerMapping(
                RequestMappingInfo.paths("/api/v1/admin/__valid_anyof").build(),
                controller,
                ValidAnnotationController.class.getDeclaredMethod("validAnyOf"));

        List<String> invalid = RequirePermissionAspect.findHandlersWithEmptyRequirePermission(mapping);
        assertThat(invalid).isEmpty();
    }

    @Test
    void afterSingletonsInstantiated_failsStartup_whenEmptyAnnotationExists() throws Exception {
        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        EmptyAnnotationController controller = new EmptyAnnotationController();
        mapping.registerMapping(
                RequestMappingInfo.paths("/api/v1/admin/__empty").build(),
                controller,
                EmptyAnnotationController.class.getDeclaredMethod("empty"));

        RequirePermissionAspect aspect =
                new RequirePermissionAspect(new CurrentUserProvider(), null, mapping);

        assertThatThrownBy(aspect::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@RequirePermission with empty value and anyOf")
                .hasMessageContaining("EmptyAnnotationController");
    }

    @Test
    void afterSingletonsInstantiated_passes_whenNoEmptyAnnotation() throws Exception {
        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        ValidAnnotationController controller = new ValidAnnotationController();
        mapping.registerMapping(
                RequestMappingInfo.paths("/api/v1/admin/__valid").build(),
                controller,
                ValidAnnotationController.class.getDeclaredMethod("valid"));

        RequirePermissionAspect aspect =
                new RequirePermissionAspect(new CurrentUserProvider(), null, mapping);
        aspect.afterSingletonsInstantiated();

        // Không ném: startup tiếp tục
        RequirePermissionAspect noMapping =
                new RequirePermissionAspect(new CurrentUserProvider(), null, null);
        noMapping.afterSingletonsInstantiated();
    }

    @Test
    void registerMapping_smokeCheck() throws Exception {
        // Bảo vệ khỏi việc registerMapping thay đổi chữ ký: HandlerMethod phải khớp method.
        HandlerMethod hm = new HandlerMethod(
                new ValidAnnotationController(),
                ValidAnnotationController.class.getDeclaredMethod("valid"));
        assertThat(hm.getMethod().getName()).isEqualTo("valid");
    }
}
