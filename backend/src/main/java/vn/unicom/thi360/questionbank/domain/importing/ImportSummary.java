package vn.unicom.thi360.questionbank.domain.importing;

/**
 * Tổng hợp một lần đọc tệp (mirror {@code ImportSummary} của web): số dòng từng trạng thái và
 * số bộ/câu con hợp lệ (đã nằm trong {@code valid}).
 */
public record ImportSummary(
    int total,
    int valid,
    int error,
    int duplicate,
    int validSets,
    int validSetChildren) {}
