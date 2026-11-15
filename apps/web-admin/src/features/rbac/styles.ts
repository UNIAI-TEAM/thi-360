/**
 * Thanh lọc ở thẻ hẹp (điện thoại, tablet dọc; UNI-2586): ô tìm kiếm hết chiều ngang (`col-span-full` trên ô đó),
 * các ô chọn xếp lưới hai cột, một cột khi vùng chứa hẹp hơn 300px. Phần tử dùng lớp này phải nằm trong một
 * `@container`; mỗi màn tự thêm ngưỡng chuyển về hàng `flex` cũ, chọn đúng bề rộng mà cả hàng vừa một dòng, để bố
 * cục ở cửa sổ từ 1024px không đổi.
 */
export const FILTER_GRID = "grid grid-cols-1 gap-3 @min-[300px]:grid-cols-2";
