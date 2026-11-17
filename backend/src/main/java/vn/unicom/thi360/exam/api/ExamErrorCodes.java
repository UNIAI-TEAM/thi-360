package vn.unicom.thi360.exam.api;

/** Mã lỗi nghiệp vụ của module bài thi (spec 9.2); khóa i18n trùng tên trong messages*. */
public final class ExamErrorCodes {

    private ExamErrorCodes() {
    }

    /** 422 — kỳ thi/phạm vi/phần/môn không hợp lệ hoặc không được phép. */
    public static final String KIND_UNKNOWN = "EXAM_KIND_UNKNOWN";
    public static final String SCOPE_NOT_ALLOWED = "EXAM_SCOPE_NOT_ALLOWED";
    public static final String PART_OR_SUBJECT_REQUIRED = "EXAM_PART_OR_SUBJECT_REQUIRED";
    /** 422 — mẫu không khớp kỳ thi/phạm vi/phần/môn của bài. */
    public static final String TEMPLATE_MISMATCH = "EXAM_TEMPLATE_MISMATCH";
    /** 422 — thang điểm không dương hoặc bắt buộc mà thiếu. */
    public static final String MAX_SCORE_INVALID = "EXAM_MAX_SCORE_INVALID";
    /** 422 — cấu trúc vi phạm quy tắc (sau validator). */
    public static final String STRUCTURE_INVALID = "EXAM_STRUCTURE_INVALID";
    /** 422 — hướng dẫn làm bài chứa HTML bị cấm (khi sanitizer BE-2a được nối). */
    public static final String INSTRUCTIONS_UNSAFE = "EXAM_INSTRUCTIONS_UNSAFE";
    /** 409 — mã bài thi trùng (không phân biệt hoa thường). */
    public static final String CODE_TAKEN = "EXAM_CODE_TAKEN";
    /** 409 — đổi phạm vi của bài đã phát hành. */
    public static final String SCOPE_LOCKED = "EXAM_SCOPE_LOCKED";
    /** 409 — thay thế cấu trúc đã sửa mà thiếu confirmReplace. */
    public static final String REPLACE_CONFIRM = "EXAM_STRUCTURE_REPLACE_CONFIRM";
    /** 412 — expectedRevision lệch revision hiện tại. */
    public static final String REVISION_CONFLICT = "EXAM_REVISION_CONFLICT";
    public static final String CHECK_REQUIRED = "CHECK_REQUIRED";
    public static final String NOTHING_TO_PUBLISH = "NOTHING_TO_PUBLISH";
    public static final String NO_PATH_SELECTED = "NO_PATH_SELECTED";
    public static final String PATH_NOT_VALID = "PATH_NOT_VALID";
    public static final String VERSION_NOT_FOUND = "VERSION_NOT_FOUND";
}
