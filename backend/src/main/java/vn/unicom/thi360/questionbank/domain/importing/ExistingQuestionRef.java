package vn.unicom.thi360.questionbank.domain.importing;

import java.util.UUID;

import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Tham chiếu tối thiểu tới một câu đã có trong ngân hàng, dùng để phát hiện trùng
 * {@code source_code} (so trên mã chuẩn hóa: bỏ khoảng trắng đầu cuối, viết hoa) và chặn đổi
 * dạng ({@code IMPORT_SOURCE_TYPE_MISMATCH}, spec 4.5). Lớp ứng dụng nạp theo lô trước khi
 * gọi parser; chỉ câu độc lập và dòng bộ so mã nguồn (mã của câu con bị bỏ qua, quyết định
 * 8.8).
 */
public record ExistingQuestionRef(UUID id, String code, QuestionType type, String sourceCode) {

    /** Mã nguồn chuẩn hóa để so khớp; {@code null} khi câu không có mã nguồn. */
    public String sourceCodeNormalized() {
        return sourceCode == null ? null : sourceCode.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
