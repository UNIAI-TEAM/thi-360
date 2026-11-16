package vn.unicom.thi360.questionbank.domain.importing;

/**
 * Giới hạn đọc tệp nhập, truyền vào từ cấu hình {@code thi360.questionbank.import.*}
 * (mặc định theo spec 4.5: 5 MB, 2000 dòng dữ liệu).
 *
 * @param maxFileBytes dung lượng tối đa của tệp tải lên
 * @param maxRows số dòng dữ liệu tối đa (không tính dòng tiêu đề)
 */
public record ImportLimits(long maxFileBytes, int maxRows) {

    public static final ImportLimits DEFAULT = new ImportLimits(5L * 1024L * 1024L, 2_000);
}
