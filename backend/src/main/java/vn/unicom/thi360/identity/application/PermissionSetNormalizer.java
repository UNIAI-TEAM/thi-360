package vn.unicom.thi360.identity.application;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;

@Component
public class PermissionSetNormalizer {

    public static final String ERROR_PERMISSION_REQUIRES_VIEW = "PERMISSION_REQUIRES_VIEW";

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
                                    "Phải có quyền " + requiredView.code() + " khi còn quyền " + p.code())));
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
}
