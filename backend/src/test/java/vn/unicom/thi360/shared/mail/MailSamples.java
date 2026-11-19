package vn.unicom.thi360.shared.mail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Model hợp lệ cho từng loại thư, và địa chỉ người nhận ngẫu nhiên trên miền {@code example.test}. */
final class MailSamples {

    static final String FULL_NAME = "Nguyễn Văn Kiểm Thử";
    static final String RESET_URL = "https://thi360.example.test/reset-password?token=tok-abc123";
    static final List<String> ORDER_ITEMS = List.of("Đề SP2E 2026 Toán", "Đề SP2E 2026 Văn", "Đề SP2E 2026 Anh");

    private MailSamples() {
    }

    static String randomAddress() {
        return "u-" + UUID.randomUUID() + "@example.test";
    }

    /** Mã OTP 6 số ngẫu nhiên, để mỗi ca có mã riêng. */
    static String randomCode() {
        return String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000));
    }

    static Map<String, Object> model(MailKind kind) {
        return model(kind, "482913");
    }

    static Map<String, Object> model(MailKind kind, String code) {
        Map<String, Object> model = new HashMap<>();
        model.put("fullName", FULL_NAME);
        switch (kind) {
            case EMAIL_VERIFY -> model.put("code", code);
            case EMAIL_CHANGE -> {
                model.put("code", code);
                model.put("newEmail", "new-address@example.test");
            }
            case EMAIL_CHANGED_NOTICE -> model.put("newEmail", "new-address@example.test");
            case PASSWORD_RESET -> model.put("resetUrl", RESET_URL);
            case ORDER_FULFILLED -> {
                model.put("orderCode", "DH-0001");
                model.put("items", ORDER_ITEMS.stream().map(name -> Map.<String, Object>of("name", name)).toList());
            }
            case REFUND_DONE -> {
                model.put("orderCode", "DH-0001");
                model.put("refundAmount", "100.000 ₫");
            }
        }
        return model;
    }

    /** Giá trị mà thư của {@code kind} phải hiện ra, ở cả HTML và văn bản. */
    static List<String> visibleValues(MailKind kind) {
        return switch (kind) {
            case EMAIL_VERIFY -> List.of(FULL_NAME, "482913");
            case EMAIL_CHANGE -> List.of(FULL_NAME, "482913", "new-address@example.test");
            case EMAIL_CHANGED_NOTICE -> List.of(FULL_NAME, "new-address@example.test");
            case PASSWORD_RESET -> List.of(FULL_NAME, RESET_URL);
            case ORDER_FULFILLED -> {
                java.util.ArrayList<String> values = new java.util.ArrayList<>(List.of(FULL_NAME, "DH-0001"));
                values.addAll(ORDER_ITEMS);
                yield values;
            }
            case REFUND_DONE -> List.of(FULL_NAME, "DH-0001", "100.000 ₫");
        };
    }
}
