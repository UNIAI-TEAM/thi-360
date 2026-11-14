import { webUrl } from "./env";

/** URL trang đăng nhập (web học viên) kèm `callbackUrl` là URL đầy đủ của trang đang mở. */
export function buildLoginUrl(callbackUrl?: string): string {
  const base = `${webUrl()}/login`;
  return callbackUrl ? `${base}?callbackUrl=${encodeURIComponent(callbackUrl)}` : base;
}

/**
 * Chuyển trình duyệt sang trang đăng nhập; mặc định kèm trang đang mở để quay lại sau khi đăng nhập.
 * Tách riêng để test thay thế được.
 */
export function redirectToLogin({ returnHere = true }: { returnHere?: boolean } = {}): void {
  window.location.assign(buildLoginUrl(returnHere ? window.location.href : undefined));
}
