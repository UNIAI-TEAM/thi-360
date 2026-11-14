package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.identity.application.PermissionSetNormalizer;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.shared.api.ValidationException;

class PermissionSetNormalizerTest {

    private final PermissionSetNormalizer normalizer = new PermissionSetNormalizer();

    @Test
    void normalize_roleWithOnlyUserModify_addsUserView() {
        Set<Permission> result = normalizer.normalize(EnumSet.of(Permission.USER_MODIFY));

        assertThat(result).containsExactlyInAnyOrder(Permission.USER_VIEW, Permission.USER_MODIFY);
    }

    @Test
    void normalize_appearanceModify_doesNotAddAnyViewPermission() {
        Set<Permission> result = normalizer.normalize(EnumSet.of(Permission.APPEARANCE_MODIFY));

        assertThat(result).containsExactly(Permission.APPEARANCE_MODIFY);
    }

    @Test
    void normalize_extraPermissionUserAssign_addsUserView() {
        Set<Permission> result = normalizer.normalize(EnumSet.of(Permission.USER_ASSIGN));

        assertThat(result).containsExactlyInAnyOrder(Permission.USER_VIEW, Permission.USER_ASSIGN);
    }

    @Test
    void removeUserView_whileUserAssignRemains_throwsPermissionRequiresView() {
        Set<Permission> current = EnumSet.of(Permission.USER_VIEW, Permission.USER_ASSIGN);

        assertThatThrownBy(() -> normalizer.removeAndValidate(current, EnumSet.of(Permission.USER_VIEW)))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode())
                        .isEqualTo(PermissionSetNormalizer.ERROR_PERMISSION_REQUIRES_VIEW));
    }

    @Test
    void validateRequiredViews_userAssignWithoutUserView_throwsPermissionRequiresView() {
        assertThatThrownBy(() -> normalizer.validateRequiredViews(EnumSet.of(Permission.USER_ASSIGN)))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode())
                        .isEqualTo(PermissionSetNormalizer.ERROR_PERMISSION_REQUIRES_VIEW));
    }

    @Test
    void removeUserViewAndUserAssignTogether_succeedsWithEmptySet() {
        Set<Permission> current = EnumSet.of(Permission.USER_VIEW, Permission.USER_ASSIGN);

        Set<Permission> result = normalizer.removeAndValidate(
                current, EnumSet.of(Permission.USER_VIEW, Permission.USER_ASSIGN));

        assertThat(result).isEmpty();
    }
}
