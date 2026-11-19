package vn.unicom.thi360.shared.testapi;

/**
 * Quy tắc chung của {@code /api/v1/test/**} (plan A6, B6, B9). Chỉ profile {@code e2e} đăng ký
 * handler dưới tiền tố này; profile khác không có handler nên gọi vào nhận 404. Bảo mật mở tiền tố
 * này cho người gọi ẩn danh (để không trả 401 thay cho 404) và {@code CookieAuthFilter} bỏ qua nó.
 *
 * <p>Task thêm endpoint thử (ML: {@code /mail}, CM-3a: cổng giả lập) chỉ cần gắn
 * {@link TestApiController}; không sửa lại hai chỗ trên.
 */
public final class TestApiPaths {

    /** Tên profile bật toàn bộ phần thử. */
    public static final String E2E_PROFILE = "e2e";

    /** Profile của môi trường thật; bật cùng {@link #E2E_PROFILE} thì ứng dụng không khởi động. */
    public static final String PROD_PROFILE = "prod";

    /** Tiền tố gốc, không có dấu gạch chéo cuối. */
    public static final String PREFIX = "/api/v1/test";

    /** Mẫu cho {@code requestMatchers}. */
    public static final String PATTERN = PREFIX + "/**";

    private TestApiPaths() {
    }

    /** Đường dẫn (đã bỏ context path) có thuộc phần thử không. */
    public static boolean matches(String path) {
        return path != null && (path.equals(PREFIX) || path.startsWith(PREFIX + "/"));
    }
}
