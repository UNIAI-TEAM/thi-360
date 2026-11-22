package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import vn.unicom.thi360.identity.application.PermissionSetNormalizer;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.identity.spi.PermissionObject;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.Access;

class Wave3PermissionsTests {

    private static final List<PermissionObject> NEW_OBJECTS = List.of(
            PermissionObject.PROMOTION,
            PermissionObject.ORDER,
            PermissionObject.GRADING,
            PermissionObject.ATTEMPT);

    /** TM 13 / plan B3: fixed bit per code, in this order. */
    private static final Map<String, Integer> NEW_CODE_BITS = Map.of(
            Permissions.PROMOTION_VIEW, 18,
            Permissions.PROMOTION_MODIFY, 19,
            Permissions.ORDER_VIEW, 20,
            Permissions.ORDER_REFUND, 21,
            Permissions.ORDER_RECOVER, 22,
            Permissions.GRADING_VIEW, 23,
            Permissions.GRADING_MODIFY, 24,
            Permissions.ATTEMPT_VIEW, 25,
            Permissions.ATTEMPT_COMPENSATE, 26);

    @Test
    void nineNewPermissionsUseFixedBitsAndManageAccess() {
        assertThat(NEW_CODE_BITS).hasSize(9);
        NEW_CODE_BITS.forEach((code, bit) -> {
            Permission permission = Permission.fromCode(code);
            assertThat(permission.bitIndex()).as(code).isEqualTo(bit);
            assertThat(permission.scope()).as(code).isEqualTo(Access.MANAGE);
        });
    }

    @Test
    void everyNewObjectHasExactlyOneViewPermission() {
        for (PermissionObject object : NEW_OBJECTS) {
            List<Permission> views = Arrays.stream(Permission.values())
                    .filter(p -> p.object() == object && p.kind() == Permission.Kind.VIEW)
                    .toList();
            assertThat(views).as("VIEW permissions of %s", object).hasSize(1);
            assertThat(Permission.viewPermissionOf(object)).contains(views.getFirst());
        }
    }

    @Test
    void customRoleWithOnlyOrderRefundGetsOrderView() {
        Set<Permission> result = new PermissionSetNormalizer().normalize(EnumSet.of(Permission.ORDER_REFUND));

        assertThat(result).containsExactlyInAnyOrder(Permission.ORDER_VIEW, Permission.ORDER_REFUND);
    }

    @Test
    void everyNewPermissionHasLabels() throws IOException {
        Properties vi = load("messages_vi.properties");
        Properties en = load("messages_en.properties");

        Set<String> codes = NEW_CODE_BITS.keySet();
        for (Properties bundle : List.of(vi, en)) {
            for (String code : codes) {
                assertThat(bundle.getProperty("permission." + code + ".name")).as(code + " name").isNotBlank();
                assertThat(bundle.getProperty("permission." + code + ".description"))
                        .as(code + " description").isNotBlank();
            }
            for (PermissionObject object : NEW_OBJECTS) {
                assertThat(bundle.getProperty("permission.object." + object.code()))
                        .as("object " + object.code()).isNotBlank();
            }
        }
        // The two languages must really differ, otherwise the English bundle was copied.
        Set<String> sameText = codes.stream()
                .filter(code -> vi.getProperty("permission." + code + ".name")
                        .equals(en.getProperty("permission." + code + ".name")))
                .collect(Collectors.toSet());
        assertThat(sameText).isEmpty();
    }

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource(resource).getInputStream();
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
