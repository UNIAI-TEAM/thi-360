package vn.unicom.thi360.questionbank.spi;

/** Loại sự kiện "đã dùng" ghi vào sổ {@code question_usage_ledger} (spec 4.1, D18). */
public enum QuestionUsageKind {
    /** Câu nằm trong đề xem trước đã sinh (BE-5b ghi). */
    PREVIEW,
    /** Câu nằm trong đề chứng minh của lần công bố (BE-6 ghi). */
    PUBLISH_WITNESS,
    /** Câu đã phát vào một lượt thi (đợt lượt thi ghi). */
    ATTEMPT,
}
