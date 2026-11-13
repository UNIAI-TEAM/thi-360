package vn.unicom.thi360.identity.application;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.ForbiddenException;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.Realm;

@Component
public class PrivilegeGuard {

    public static final String ERROR_PRIVILEGE_ESCALATION = "PRIVILEGE_ESCALATION";
    public static final String ERROR_SELF_LOCKOUT = "SELF_LOCKOUT";

    public boolean canGrant(CurrentUser actor, Collection<Permission> permissions) {
        if (actor == null) {
            return false;
        }
        if (actor.realm() == Realm.MASTER || actor.permissions().isAllGranted()) {
            return true;
        }
        if (permissions == null || permissions.isEmpty()) {
            return true;
        }
        for (Permission permission : permissions) {
            if (permission != null
                    && !actor.permissions().hasBit(permission.bitIndex())
                    && !actor.permissions().hasCode(permission.code())) {
                return false;
            }
        }
        return true;
    }

    public void assertCanGrant(CurrentUser actor, Collection<Permission> permissions) {
        if (!canGrant(actor, permissions)) {
            throw new ForbiddenException(ERROR_PRIVILEGE_ESCALATION, null);
        }
    }

    public boolean willCauseSelfLockout(
            CurrentUser actor, UUID targetUserId, Set<Permission> resultingEffectivePermissions) {
        if (actor == null || actor.realm() == Realm.MASTER || targetUserId == null) {
            return false;
        }
        if (!actor.id().equals(targetUserId)) {
            return false;
        }
        return resultingEffectivePermissions == null
                || !resultingEffectivePermissions.contains(Permission.USER_ASSIGN);
    }

    public void assertNoSelfLockout(
            CurrentUser actor, UUID targetUserId, Set<Permission> resultingEffectivePermissions) {
        if (willCauseSelfLockout(actor, targetUserId, resultingEffectivePermissions)) {
            throw new ConflictException(ERROR_SELF_LOCKOUT);
        }
    }
}
