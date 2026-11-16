import { ApiStatusError } from "@/lib/api/query-client";

/**
 * Khóa idempotency của lệnh ghi (plan B7, spec 7.1). Một quy tắc cho mọi lệnh có khóa (tạo, nhân bản, đổi trạng thái
 * hàng loạt, nhập câu hỏi): giữ khóa chỉ khi chưa biết lệnh đã chạy hay chưa, để lần thử lại gửi đúng khóa đó và máy
 * chủ trả kết quả đã lưu thay vì chạy lần hai; sau thành công hoặc sau lỗi chắc chắn là lệnh chưa chạy (4xx) thì quên
 * khóa, lần bấm kế tiếp có khóa mới.
 *
 * `scope` định danh một ý định của người dùng (`duplicate:<id>`, `create:<lần mở biểu mẫu>`, `commit:<uploadId>`), không
 * chứa dữ liệu của lệnh. `fingerprint` là dữ liệu của lệnh: cùng khóa mà khác thân bị máy chủ từ chối 409, nên đổi dữ
 * liệu giữa hai lần bấm là lệnh khác và nhận khóa mới.
 */
const pending = new Map<string, { key: string; fingerprint: string }>();

/**
 * Lệnh có thể đã chạy phía máy chủ: mất kết nối, hết thời gian chờ (408), 429 và 5xx. Lỗi không phải lỗi HTTP (mạng,
 * hủy) cũng tính là có thể đã chạy. Còn lại (400, 403, 404, 409, 422...) máy chủ đã từ chối và không lưu gì.
 */
export function mayHaveRun(failure: unknown): boolean {
  if (!(failure instanceof ApiStatusError)) return true;
  return failure.status >= 500 || failure.status === 429 || failure.status === 408;
}

export async function withIdempotencyKey<T>(
  scope: string,
  run: (key: string) => Promise<T>,
  fingerprint = "",
): Promise<T> {
  const existing = pending.get(scope);
  const entry = existing && existing.fingerprint === fingerprint ? existing : { key: crypto.randomUUID(), fingerprint };
  pending.set(scope, entry);
  try {
    const result = await run(entry.key);
    pending.delete(scope);
    return result;
  } catch (failure) {
    if (!mayHaveRun(failure)) pending.delete(scope);
    throw failure;
  }
}

/** Xóa các khóa đang nhớ (test). */
export function resetIdempotencyKeys(): void {
  pending.clear();
}
