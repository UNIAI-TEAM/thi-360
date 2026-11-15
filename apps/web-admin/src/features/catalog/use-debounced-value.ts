import { useEffect, useState } from "react";

/** Độ trễ tìm kiếm dùng chung với danh sách tài khoản và lớp học. */
export const SEARCH_DEBOUNCE_MS = 300;

/**
 * Trả về `value` sau khi nó đứng yên `delayMs`. Dùng cho ô tìm kiếm (gõ liên tục chỉ gửi một yêu cầu) và cho câu
 * thông báo của vùng `role="status"` (trình đọc màn hình không bị đọc dồn theo từng phím).
 */
export function useDebouncedValue<TValue>(value: TValue, delayMs: number = SEARCH_DEBOUNCE_MS): TValue {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    if (Object.is(value, debounced)) return;
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, debounced, delayMs]);

  return debounced;
}

/**
 * Từ khóa tìm kiếm đã bỏ khoảng trắng hai đầu và chờ người dùng ngừng gõ. Xóa trống ô tìm (hoặc đổi sang nhóm
 * khác, nơi ô tìm được đặt lại) có hiệu lực ngay: không chờ và không gửi thêm yêu cầu nào với từ khóa cũ.
 */
export function useDebouncedSearch(input: string, delayMs: number = SEARCH_DEBOUNCE_MS): string {
  const term = input.trim();
  const [debounced, setDebounced] = useState(term);
  if (term === "" && debounced !== "") {
    setDebounced("");
  }

  useEffect(() => {
    if (term === "" || term === debounced) return;
    const timer = setTimeout(() => setDebounced(term), delayMs);
    return () => clearTimeout(timer);
  }, [term, debounced, delayMs]);

  return term === "" ? "" : debounced;
}
