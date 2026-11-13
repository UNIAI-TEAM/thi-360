package vn.unicom.thi360.shared.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.shared.api.ForbiddenException;

@Aspect
@Component
public class RequirePermissionAspect {

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");

    private final CurrentUserProvider currentUserProvider;
    private final PermissionCatalog permissionCatalog;

    public RequirePermissionAspect(
            CurrentUserProvider currentUserProvider,
            @Autowired(required = false) PermissionCatalog permissionCatalog) {
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider, "currentUserProvider must not be null");
        this.permissionCatalog = permissionCatalog;
    }

    @Before("@annotation(requirePermission)")
    public void checkMethodPermission(JoinPoint joinPoint, RequirePermission requirePermission) {
        assertAuthorized(joinPoint, requirePermission);
    }

    @Before("@within(requirePermission) && !@annotation(vn.unicom.thi360.shared.security.RequirePermission)")
    public void checkTypePermission(JoinPoint joinPoint, RequirePermission requirePermission) {
        assertAuthorized(joinPoint, requirePermission);
    }

    private void assertAuthorized(JoinPoint joinPoint, RequirePermission requirePermission) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        PermissionSet granted = user.permissions();
        if (granted.isAllGranted()) {
            return;
        }

        String[] requiredCodes = requirePermission.value();
        if (requiredCodes == null || requiredCodes.length == 0) {
            return;
        }

        List<String> missing = new ArrayList<>();
        for (String rawCode : requiredCodes) {
            if (rawCode == null || rawCode.isBlank()) {
                continue;
            }
            String code = rawCode.trim();
            if (permissionCatalog != null) {
                OptionalInt bit = permissionCatalog.findBit(code);
                if (bit.isEmpty()) {
                    throw new IllegalStateException("Unknown permission code in @RequirePermission: " + code);
                }
                if (!granted.hasBit(bit.getAsInt())) {
                    missing.add(code);
                }
            } else if (!granted.hasCode(code)) {
                missing.add(code);
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
}
