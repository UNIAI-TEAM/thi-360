package vn.unicom.thi360.exam.domain.solver;

/**
 * Chế độ duyệt ứng viên của bộ giải (spec 5.4).
 *
 * <p>{@link Check} dùng khi kiểm tra cấu hình: ứng viên được duyệt theo đúng thứ tự danh sách đầu vào,
 * kết quả luôn giống nhau với cùng một đầu vào. {@link Generate} dùng khi xem trước / sinh đề: ứng viên
 * được xáo trộn bằng {@link java.util.Random} với hạt giống cho trước — cùng hạt giống và cùng đầu vào
 * cho cùng một đề.
 */
public sealed interface SolveMode {

    /** Thứ tự cố định, dùng cho kiểm tra cấu hình. */
    record Check() implements SolveMode {
    }

    /**
     * Xáo trộn có hạt giống, dùng cho xem trước và sinh đề.
     *
     * @param seed hạt giống của {@link java.util.Random}
     */
    record Generate(long seed) implements SolveMode {
    }

    static SolveMode check() {
        return new Check();
    }

    static SolveMode generate(long seed) {
        return new Generate(seed);
    }
}
