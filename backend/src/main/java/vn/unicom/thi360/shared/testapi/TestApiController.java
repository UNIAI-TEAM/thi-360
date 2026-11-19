package vn.unicom.thi360.shared.testapi;

import io.swagger.v3.oas.annotations.Hidden;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller dưới {@link TestApiPaths#PREFIX}: chỉ đăng ký khi bật profile {@code e2e}, không có
 * trong OpenAPI. Controller tự khai {@code @RequestMapping(TestApiPaths.PREFIX + "/...")}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RestController
@Hidden
@Profile(TestApiPaths.E2E_PROFILE)
public @interface TestApiController {

    /** Tên bean, như {@link RestController#value()}. */
    @AliasFor(annotation = RestController.class, attribute = "value")
    String value() default "";
}
