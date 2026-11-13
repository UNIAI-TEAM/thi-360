/**
 * Cấu hình phía trình duyệt. Mỗi biến `NEXT_PUBLIC_*` phải được đọc bằng tên đầy đủ để Next.js
 * thay giá trị lúc build.
 */

const DEV_WEB_URL = "http://localhost:3000";

/** Địa chỉ web học viên: nơi duy nhất có biểu mẫu đăng nhập (ARCHITECTURE mục 5.1). */
export function webUrl(): string {
  const configured = process.env.NEXT_PUBLIC_THI360_WEB_URL?.trim();
  return (configured || DEV_WEB_URL).replace(/\/+$/, "");
}

/**
 * Phiên giả để xem khung khi backend chưa có `/api/v1/me`. Không bao giờ bật ở bản production,
 * kể cả khi biến được đặt. T18 (UNI-2556) gỡ bỏ biến này.
 */
export function isFakeSessionEnabled(): boolean {
  return process.env.NODE_ENV !== "production" && process.env.NEXT_PUBLIC_DEV_FAKE_SESSION === "1";
}
