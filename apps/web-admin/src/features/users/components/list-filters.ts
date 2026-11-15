/**
 * Bố cục thanh lọc của màn danh sách (Tài khoản, Lớp học), chia theo bề rộng của thẻ lọc (`@container` đặt trên
 * thẻ), không theo bề rộng màn hình.
 *
 * - Khung hẹp (điện thoại, tablet dọc): lưới tự chia cột, mỗi cột tối thiểu 8.5rem. Ô tìm kiếm chiếm cả hàng, các
 *   ô chọn xếp hai cột ở bề rộng điện thoại (một cột khi thẻ hẹp hơn khoảng 300px), các ô đánh dấu và nút Xóa bộ
 *   lọc chung một hàng tự xuống dòng.
 * - Từ `@3xl` (48rem): giữ bố cục một dải `flex-wrap` như trước; hàng ô đánh dấu dùng `contents` để từng ô lại là
 *   phần tử của dải.
 *
 * DOM và thứ tự Tab giống nhau ở mọi bề rộng, chỉ đổi cách xếp.
 */
export const FILTER_FORM =
  "grid grid-cols-[repeat(auto-fill,minmax(8.5rem,1fr))] items-end gap-3 @3xl:flex @3xl:flex-wrap";

/** Ô tìm kiếm: cả hàng khi khung hẹp, phần rộng nhất của dải khi khung rộng. */
export const FILTER_SEARCH = "col-span-full @3xl:min-w-60 @3xl:flex-[2_1_240px]";

/** Ô chọn: một ô lưới khi khung hẹp; khi khung rộng bên gọi thêm bề rộng tối thiểu riêng (`@3xl:min-w-*`). */
export const FILTER_SELECT = "min-w-0 @3xl:flex-1";

/** Hàng ô đánh dấu và nút Xóa bộ lọc. */
export const FILTER_TOGGLES =
  "col-span-full flex flex-wrap items-center gap-x-4 gap-y-1 @3xl:contents";
