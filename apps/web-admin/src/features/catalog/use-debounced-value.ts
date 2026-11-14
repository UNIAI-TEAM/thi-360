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
