package vn.unicom.thi360.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTests {

    @Test
    void removesSensitiveKeysCaseInsensitivelyAndRecursively_whileKeepingEmailAndPhone() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("resetToken", "tok-123");
        nested.put("otp", "999999");
        nested.put("displayName", "Nguyen Van A");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("username", "student01");
        input.put("email", "student01@thi360.vn");
        input.put("phone", "0901234567");
        input.put("password", "Secret@123");
        input.put("NewPassword", "Secret@456");
        input.put("client_secret", "sec-abc");
        input.put("accessToken", "jwt-xyz");
        input.put("OTP_CODE", "123456");
        input.put("nested", nested);
        input.put("items", List.of(Map.of("secretKey", "k1", "label", "keep")));
        input.put("nullableField", null);

        Map<String, Object> masked = SensitiveDataMasker.mask(input);

        assertThat(masked)
                .containsEntry("username", "student01")
                .containsEntry("email", "student01@thi360.vn")
                .containsEntry("phone", "0901234567")
                .containsEntry("nullableField", null)
                .doesNotContainKeys("password", "NewPassword", "client_secret", "accessToken", "OTP_CODE");

        @SuppressWarnings("unchecked")
        Map<String, Object> maskedNested = (Map<String, Object>) masked.get("nested");
        assertThat(maskedNested)
                .containsEntry("displayName", "Nguyen Van A")
                .doesNotContainKeys("resetToken", "otp");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> maskedItems = (List<Map<String, Object>>) masked.get("items");
        assertThat(maskedItems).hasSize(1);
        assertThat(maskedItems.getFirst())
                .containsEntry("label", "keep")
                .doesNotContainKey("secretKey");
    }

    @Test
    void returnsNullWhenInputIsNull() {
        assertThat(SensitiveDataMasker.mask(null)).isNull();
    }
}
