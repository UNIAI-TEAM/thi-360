import { validateCallbackUrl } from "@thi360/web-kit/redirect";

/** Tham số `reset` trên `/login` báo đã đặt lại mật khẩu xong (câu thông báo hiện ở trang đăng nhập). */
export const PASSWORD_RESET_DONE_PARAM = "reset";
export const PASSWORD_RESET_DONE_VALUE = "done";

/**
 * Lối đi giữa các trang tài khoản (`/login`, `/register`, `/forgot-password`, `/verify-email`) giữ `callbackUrl`
 * qua query, khi `raw` hợp lệ theo `validateCallbackUrl`. `raw` trỏ ra ngoài hệ thống hoặc méo thì bị bỏ qua:
 * liên kết đi không kèm `callbackUrl`.
 */
export function withCallbackUrl(path: string, raw: string | null | undefined): string {
  const callbackUrl = validateCallbackUrl(raw);
  if (!callbackUrl) return path;
  const separator = path.includes("?") ? "&" : "?";
  return `${path}${separator}callbackUrl=${encodeURIComponent(callbackUrl)}`;
}
