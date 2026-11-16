package vn.unicom.thi360.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Yêu cầu quyền cho một endpoint.
 *
 * <ul>
 *   <li>{@code value}: người gọi phải có <b>đủ</b> mọi quyền liệt kê (giữ nghĩa cũ).
 *   <li>{@code anyOf}: người gọi phải có <b>ít nhất một</b> quyền liệt kê.
 *   <li>Có cả hai: phải đủ {@code value} <b>và</b> khớp ít nhất một trong {@code anyOf}.
 *   <li>Cả hai cùng rỗng là lỗi cấu hình: ứng dụng ném lỗi khi khởi động
 *       ({@link RequirePermissionAspect} quét mọi handler sau khi dựng singleton).
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    String[] value() default {};

    String[] anyOf() default {};
}
