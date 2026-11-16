package vn.unicom.thi360.questionbank.spi;

import java.util.List;
import java.util.UUID;

/**
 * Cổng ngược: {@code exam} cài interface này (cùng cách {@code CatalogUsageProvider}) để
 * {@code questionbank} hỏi câu hỏi đang nằm trong nguồn của khối nào, ở bài nào — phục vụ màn
 * nơi đang dùng và ảnh hưởng khi ngừng câu (spec mục 3).
 *
 * <p>BE-6 (UNI-2618) cài bản thật lọc thô trên {@code exam_block_ref}; hình dạng
 * {@link ExamUsageRef} tối thiểu, BE-6 mở rộng thêm khi cần (block/part/option của bài).
 */
public interface QuestionUsageProvider {

    /**
     * Các bài thi đang có câu này trong nguồn của một khối (phần exam của màn nơi đang dùng và
     * ảnh hưởng khi ngừng). Cài đặt mặc định rỗng — an toàn để gọi trước khi BE-6 có mặt.
     */
    default List<ExamUsageRef> examUsagesOf(UUID questionId) {
        return List.of();
    }

    /**
     * Một khối bài thi đang lấy câu làm nguồn (hình dạng tối thiểu; BE-6 bổ sung
     * block/part/option, trạng thái bài khi có).
     */
    record ExamUsageRef(UUID examId) {}
}
