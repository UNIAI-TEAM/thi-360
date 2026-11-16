package vn.unicom.thi360.questionbank.domain.importing;

/**
 * Mức độ của một vấn đề trên dòng nhập (spec 4.5): {@link #ERROR} chặn dòng (và cả bộ khi lỗi
 * nằm ở câu con); {@link #WARNING} vẫn nhập, câu ở trạng thái Nháp.
 */
public enum ImportIssueSeverity {
    ERROR,
    WARNING
}
