package vn.unicom.thi360.exam.domain.solver;

/**
 * Ném trong lúc giải khi đồng hồ vượt hạn của lời gọi solve hiện tại.
 * Mang hạn để tầng ngoài ghi lại vào kết quả; không cần stack trace đầy đủ.
 */
final class DeadlineHit extends RuntimeException {

    private static final long serialVersionUID = 1L;

    DeadlineHit() {
        super("solve deadline exceeded", null, false, false);
    }
}
