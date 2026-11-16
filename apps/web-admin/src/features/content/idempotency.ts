/**
 * Khóa idempotency của lệnh ghi (plan B7, spec 7.1): một khóa cho mỗi lần bấm, gửi lại đúng khóa đó khi thử lại cùng
 * lệnh. `scope` mô tả lệnh và dữ liệu của nó (ví dụ `duplicate:<id>`); khóa chỉ được nhớ tới khi lệnh thành công, nên
 * lần bấm kế tiếp sau một lần xong sẽ có khóa mới, còn lần thử lại sau lỗi dùng lại khóa cũ.
 */
const pending = new Map<string, string>();

export async function withIdempotencyKey<T>(scope: string, run: (key: string) => Promise<T>): Promise<T> {
  const key = pending.get(scope) ?? crypto.randomUUID();
  pending.set(scope, key);
  const result = await run(key);
  pending.delete(scope);
  return result;
}

/** Xóa các khóa đang nhớ (test). */
export function resetIdempotencyKeys(): void {
  pending.clear();
}
