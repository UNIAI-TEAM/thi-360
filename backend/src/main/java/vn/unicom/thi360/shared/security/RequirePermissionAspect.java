package vn.unicom.thi360.shared.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.unicom.thi360.shared.api.ForbiddenException;

@Aspect
@Component
public class RequirePermissionAspect implements SmartInitializingSingleton {

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");

    private final CurrentUserProvider currentUserProvider;
    private final PermissionCatalog permissionCatalog;
    private final RequestMappingHandlerMapping handlerMapping;

    public RequirePermissionAspect(
            CurrentUserProvider currentUserProvider,
            @Autowired(required = false) PermissionCatalog permissionCatalog,
            @Autowired(required = false)
                    @Qualifier("requestMappingHandlerMapping")
                    RequestMappingHandlerMapping handlerMapping) {
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider, "currentUserProvider must not be null");
        this.permissionCatalog = permissionCatalog;
        this.handlerMapping = handlerMapping;
    }

    @Before("@annotation(requirePermission)")
    public void checkMethodPermission(JoinPoint joinPoint, RequirePermission requirePermission) {
        assertAuthorized(joinPoint, requirePermission);
    }

    @Before("@within(requirePermission) && !@annotation(vn.unicom.thi360.shared.security.RequirePermission)")
    public void checkTypePermission(JoinPoint joinPoint, RequirePermission requirePermission) {
        assertAuthorized(joinPoint, requirePermission);
    }

    /**
     * Sau khi mọi singleton đã dựng: quét mọi handler MVC; một {@code @RequirePermission} mà cả
     * {@code value} lẫn {@code anyOf} đều rỗng là lỗi cấu hình (endpoint không chặn được ai) nên
     * ứng dụng từ chối khởi động thay vì chạy với một endpoint bỏ trống quyền.
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (handlerMapping == null) {
            return;
        }
        List<String> invalid = findHandlersWithEmptyRequirePermission(handlerMapping);
        if (!invalid.isEmpty()) {
            throw new IllegalStateException(
                    "@RequirePermission with empty value and anyOf on: " + String.join(", ", invalid));
        }
    }

    /**
     * Liệt kê các handler mang {@code @RequirePermission} mà cả {@code value} lẫn {@code anyOf}
     * đều không có mã nào (tách ra để test kiểm tra được mà không cần dựng cả context).
     */
    public static List<String> findHandlersWithEmptyRequirePermission(
            RequestMappingHandlerMapping handlerMapping) {
        List<String> invalid = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            RequirePermission annotation = resolveAnnotation(handler);
            if (annotation == null) {
                continue;
            }
            if (normalizeCodes(annotation.value()).isEmpty()
                    && normalizeCodes(annotation.anyOf()).isEmpty()) {
                invalid.add(handler.getShortLogMessage());
            }
        }
        return invalid;
    }

    /** Annotation hợp lệ trên method (ưu tiên) hoặc trên kiểu của handler. */
    public static RequirePermission resolveAnnotation(HandlerMethod handler) {
        RequirePermission onMethod =
                AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequirePermission.class);
        return onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequirePermission.class);
    }

    private void assertAuthorized(JoinPoint joinPoint, RequirePermission requirePermission) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        PermissionSet granted = user.permissions();
        if (granted.isAllGranted()) {
            return;
        }

        List<String> allOf = normalizeCodes(requirePermission.value());
        List<String> anyOf = normalizeCodes(requirePermission.anyOf());
        if (allOf.isEmpty() && anyOf.isEmpty()) {
            throw new IllegalStateException(
                    "@RequirePermission declares no permission on " + joinPoint.getSignature().toShortString());
        }

        List<String> missing = new ArrayList<>();
        for (String code : allOf) {
            if (!hasPermission(granted, code)) {
                missing.add(code);
            }
        }
        if (!anyOf.isEmpty()) {
            boolean anyGranted = false;
            for (String code : anyOf) {
                if (hasPermission(granted, code)) {
                    anyGranted = true;
                    break;
                }
            }
            if (!anyGranted) {
                missing.add("anyOf(" + String.join(",", anyOf) + ")");
            }
        }

        if (!missing.isEmpty()) {
            String actorId = user.realm() == Realm.MASTER
                    ? "platform:" + user.id()
                    : user.id().toString();
            securityLog.atWarn()
                    .addKeyValue("event", "authz.forbidden")
                    .addKeyValue("userId", actorId)
                    .addKeyValue("missingPermissions", String.join(",", missing))
                    .addKeyValue("method", joinPoint.getSignature().toShortString())
                    .log("Request rejected due to missing required permissions");
            throw new ForbiddenException("FORBIDDEN", null);
        }
    }

    private boolean hasPermission(PermissionSet granted, String code) {
        if (permissionCatalog != null) {
            OptionalInt bit = permissionCatalog.findBit(code);
            if (bit.isEmpty()) {
                throw new IllegalStateException("Unknown permission code in @RequirePermission: " + code);
            }
            return granted.hasBit(bit.getAsInt());
        }
        return granted.hasCode(code);
    }

    private static List<String> normalizeCodes(String[] rawCodes) {
        List<String> codes = new ArrayList<>();
        if (rawCodes != null) {
            for (String rawCode : rawCodes) {
                if (rawCode != null && !rawCode.isBlank()) {
                    codes.add(rawCode.trim());
                }
            }
        }
        return List.copyOf(codes);
    }
}
