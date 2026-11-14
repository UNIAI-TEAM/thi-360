package vn.unicom.thi360.identity.application;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.web.AppMessages;

@Component
public class PermissionSetNormalizer {

    public static final String ERROR_PERMISSION_REQUIRES_VIEW = "PERMISSION_REQUIRES_VIEW";

    private final AppMessages messages;

    public PermissionSetNormalizer() {
        this(AppMessages.defaultInstance());
    }

    @Autowired
    public PermissionSetNormalizer(AppMessages messages) {
        this.messages = messages != null ? messages : AppMessages.defaultInstance();
    }
    public Set<Permission> normalize(Collection<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Set.of();
        }
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (Permission p : permissions) {
            if (p == null) {
                continue;
            }
            result.add(p);
            if (p.kind() != Permission.Kind.VIEW) {
                Permission.viewPermissionOf(p.object()).ifPresent(result::add);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<String> normalizeCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        EnumSet<Permission> parsed = EnumSet.noneOf(Permission.class);
        for (String code : codes) {
            if (code != null && !code.isBlank()) {
                parsed.add(Permission.fromCode(code));
            }
        }
        Set<Permission> normalized = normalize(parsed);
        Set<String> result = new LinkedHashSet<>(normalized.size());
        for (Permission p : normalized) {
            result.add(p.code());
        }
        return Collections.unmodifiableSet(result);
    }

    public void validateRequiredViews(Collection<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        EnumSet<Permission> present = EnumSet.copyOf(permissions);
        for (Permission p : present) {
            if (p.kind() != Permission.Kind.VIEW) {
                Permission requiredView = Permission.viewPermissionOf(p.object()).orElse(null);
                if (requiredView != null && !present.contains(requiredView)) {
                    throw new ValidationException(
                            ERROR_PERMISSION_REQUIRES_VIEW,
                            null,
                            List.of(new FieldError(
                                    "permissions",
                                    ERROR_PERMISSION_REQUIRES_VIEW,
                                    messages.get("validation.permission.requiresView", requiredView.code(), p.code()))));
                }
            }
        }
    }

    public Set<Permission> removeAndValidate(Collection<Permission> current, Collection<Permission> toRemove) {
        EnumSet<Permission> remaining = (current == null || current.isEmpty())
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(current);
        if (toRemove != null && !toRemove.isEmpty()) {
            remaining.removeAll(toRemove);
        }
        validateRequiredViews(remaining);
        return Collections.unmodifiableSet(remaining);
    }

    public boolean hasMissingRequiredView(
            Collection<Permission> directPermissions, Collection<Permission> rolePermissions) {
        if (directPermissions == null || directPermissions.isEmpty()) {
            return false;
        }
        EnumSet<Permission> directSet = EnumSet.copyOf(directPermissions);
        EnumSet<Permission> roleSet = (rolePermissions == null || rolePermissions.isEmpty())
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(rolePermissions);
        for (Permission p : directSet) {
            if (p.kind() != Permission.Kind.VIEW) {
                Permission requiredView = Permission.viewPermissionOf(p.object()).orElse(null);
                if (requiredView != null && !directSet.contains(requiredView) && !roleSet.contains(requiredView)) {
                    return true;
                }
            }
        }
        return false;
    }
}
