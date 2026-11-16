package vn.unicom.thi360.questionbank.domain.importing;

/**
 * Trạng thái một dòng sau khi đọc (mirror web): {@link #VALID} nhập được (kể cả kèm cảnh
 * báo), {@link #ERROR} có lỗi chặn, {@link #DUPLICATE} trùng Mã nguồn với câu đã có.
 */
public enum ImportRowStatus {
    VALID,
    ERROR,
    DUPLICATE
}
