/**
 * "Dùng gần đây" của các hộp chèn (spec 4.2, 4.3, 4.4): danh sách gần nhất theo id người dùng, lưu ở
 * `localStorage`. Mọi đọc/ghi trong try/catch — môi trường chặn storage (riêng tư, hết dung lượng)
 * thì mục "Dùng gần đây" đơn giản là không có.
 */

const PREFIX = "thi360-rich-text-recent";
const MAX_ITEMS = 8;

function storageKey(userId: string, scope: string): string {
  return `${PREFIX}:${userId}:${scope}`;
}

/** Đọc danh sách gần đây (mới nhất trước). Lỗi parse/storage → `[]`. */
export function loadRecent(userId: string, scope: string): string[] {
  try {
    const raw = window.localStorage.getItem(storageKey(userId, scope));
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === "string") : [];
  } catch {
    return [];
  }
}

/** Đưa `value` lên đầu danh sách (bỏ trùng, giữ tối đa 8 mục). Lỗi storage → bỏ qua lặng lẽ. */
export function pushRecent(userId: string, scope: string, value: string): void {
  try {
    const next = [value, ...loadRecent(userId, scope).filter((item) => item !== value)].slice(0, MAX_ITEMS);
    window.localStorage.setItem(storageKey(userId, scope), JSON.stringify(next));
  } catch {
    // Không có storage: không hiện mục "Dùng gần đây" ở lần mở sau.
  }
}
