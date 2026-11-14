/**
 * Vùng bấm tối thiểu 44px trên màn cảm ứng cho liên kết và nút dạng chữ (cao 20px): phần tử giả mở rộng vùng nhận
 * chạm lên trên, xuống dưới 12px và sang hai bên 8px, kích thước nhìn thấy không đổi. Chuột và bút giữ nguyên.
 */
export const coarseTextHitArea =
  "relative pointer-coarse:after:absolute pointer-coarse:after:-inset-x-2 pointer-coarse:after:-inset-y-3";
