import type { ComponentProps } from "react";
import { cn } from "../lib/cn";

export type LiveStatusProps = Omit<ComponentProps<"div">, "children" | "role" | "aria-live"> & {
  /**
   * Câu thông báo cho trình đọc màn hình. Đổi chuỗi là đọc lại; chuỗi rỗng thì không đọc gì.
   * Ví dụ: "5 tài khoản khớp bộ lọc", "Đã chọn 3 dòng", "Tên đăng nhập dùng được".
   */
  message: string;
  /** `polite` (mặc định) chờ người dùng ngừng thao tác; `assertive` chỉ dùng cho lỗi cần biết ngay. */
  politeness?: "polite" | "assertive";
};

/**
 * Vùng thông báo ẩn về mặt hình ảnh cho kết quả của thao tác không đồng bộ: số kết quả sau khi lọc/tìm, sắp xếp,
 * số dòng đang chọn, kết quả kiểm tra. Người nhìn màn hình thấy thay đổi ngay trên trang; người dùng trình đọc
 * màn hình chỉ biết khi có vùng `role="status"` đổi nội dung (WCAG 4.1.3).
 *
 * Cách dùng: đặt MỘT `LiveStatus` cho mỗi khu vực (bảng, biểu mẫu), luôn có mặt trong DOM từ đầu, chỉ đổi `message`.
 * Vùng dựng lên cùng lúc với nội dung thì trình đọc màn hình không đọc. Không đưa nội dung đang hiện sẵn cho người
 * nhìn vào đây lần thứ hai dưới dạng nhìn thấy được; đây chỉ là kênh nói.
 */
export function LiveStatus({ message, politeness = "polite", className, ...props }: LiveStatusProps) {
  return (
    <div
      role={politeness === "assertive" ? "alert" : "status"}
      aria-live={politeness}
      aria-atomic="true"
      className={cn("sr-only", className)}
      {...props}
    >
      {message}
    </div>
  );
}
