/**
 * Cờ chuyển giữa dữ liệu mẫu và API thật cho các màn đợt 2 (plan B7).
 *
 * - `NEXT_PUBLIC_THI360_CONTENT_MOCK`: Ngân hàng câu hỏi, Nhập câu hỏi, Bài thi (PBI-03, PBI-04).
 * - `NEXT_PUBLIC_THI360_GRADING_MOCK`: hàng chờ chấm `/grading`.
 *
 * Chưa đặt: bật khi `next dev`, tắt khi build. `features/<x>/api.ts` gọi hàm trong `api.mock.ts` khi cờ bật, không
 * thì chạy nhánh API thật (tới FE-1, FE-2 là `notWired`).
 */

function readFlag(value: string | undefined): boolean {
  const flag = value?.trim().toLowerCase();
  if (flag === "true") return true;
  if (flag === "false") return false;
  return process.env.NODE_ENV === "development";
}

/** Lưu ý: Next.js chỉ thay `process.env.NEXT_PUBLIC_*` khi viết nguyên tên biến, nên không gom hai hàm làm một. */
export function isContentMockEnabled(): boolean {
  return readFlag(process.env.NEXT_PUBLIC_THI360_CONTENT_MOCK);
}

export function isGradingMockEnabled(): boolean {
  return readFlag(process.env.NEXT_PUBLIC_THI360_GRADING_MOCK);
}

/** Nhánh API thật chưa nối (FE-1, FE-2). Viết bằng mã thoát Unicode vì test quét mã nguồn chặn chữ có dấu trong `.ts`. */
export function notWired(): never {
  throw new Error("ch\u01b0a n\u1ed1i API");
}

type AsyncFn<A extends unknown[], R> = (...args: A) => Promise<R>;

/** Hàm của `api.ts`: gọi bản mẫu khi `enabled()` đúng, không thì `notWired`. Giữ nguyên chữ ký của hàm mẫu. */
export function guardApi<A extends unknown[], R>(enabled: () => boolean, mockFn: AsyncFn<A, R>): AsyncFn<A, R> {
  return async (...args) => {
    if (!enabled()) notWired();
    return mockFn(...args);
  };
}

/** Theo cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` (PBI-03, PBI-04). */
export function contentApi<A extends unknown[], R>(mockFn: AsyncFn<A, R>): AsyncFn<A, R> {
  return guardApi(isContentMockEnabled, mockFn);
}

/** Theo cờ `NEXT_PUBLIC_THI360_GRADING_MOCK` (`/grading`). */
export function gradingApi<A extends unknown[], R>(mockFn: AsyncFn<A, R>): AsyncFn<A, R> {
  return guardApi(isGradingMockEnabled, mockFn);
}
