/**
 * Cỡ chữ ô nhập trong kit: 16px ở màn hẹp và trên màn cảm ứng (kit mặc định 14px). iOS phóng to cả trang khi
 * focus vào ô có chữ dưới 16px, giữa lúc đang làm bài (DESIGN.md mục 5.1). Cùng giá trị với `touchFieldText`
 * của web học viên; đặt qua `className` của `Input`/`Textarea`/trigger chọn, không đổi `fieldBase`.
 */
export const touchFieldText = "max-sm:text-title pointer-coarse:text-title";

/** Vùng bấm tối thiểu 44x44 trên màn cảm ứng cho nút có chữ hoặc icon nhỏ (DESIGN.md mục 5.1). */
export const coarseTarget = "pointer-coarse:min-h-11 pointer-coarse:min-w-11";

/** Ô nhập cao 40px (`Input` md) lên 44px trên màn cảm ứng. */
export const coarseFieldHeight = "pointer-coarse:h-11";
