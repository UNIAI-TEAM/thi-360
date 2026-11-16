package vn.unicom.thi360.questionbank.spi;

/**
 * Cổng ngược: {@code exam} cài interface này (cùng cách {@code CatalogUsageProvider}) để
 * {@code questionbank} hỏi câu hỏi đang nằm trong nguồn của khối nào, ở bài nào — phục vụ màn
 * nơi đang dùng và ảnh hưởng khi ngừng câu (spec mục 3).
 *
 * <p>Định nghĩa đầy đủ trong BE-6 (UNI-2618); tránh khai báo hàm sớm để không khóa sai hình dạng.
 */
public interface QuestionUsageProvider {}
