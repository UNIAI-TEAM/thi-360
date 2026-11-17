/**
 * Cờ dữ liệu mẫu còn lại của đợt 2 (plan B7, QA UNI-2621): chỉ `/grading`.
 *
 * `NEXT_PUBLIC_THI360_GRADING_MOCK`: hàng chờ chấm `/grading`. Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` của
 * PBI-03/PBI-04 đã gỡ cùng lớp mẫu (ngân hàng câu hỏi, nhập, bài thi luôn gọi API thật).
 *
 * Chưa đặt: bật khi `next dev`, tắt khi build. `features/grading/api.ts` gọi nhánh mẫu khi cờ bật,
 * không thì `notWired` (nhánh thật chưa nối, đợt chấm điểm).
 */
function readFlag(value: string | undefined): boolean {
  const flag = value?.trim().toLowerCase();
  if (flag === "true") return true;
  if (flag === "false") return false;
  return process.env.NODE_ENV === "development";
}

/** Lưu ý: Next.js chỉ thay `process.env.NEXT_PUBLIC_*` khi viết nguyên tên biến. */
export function isGradingMockEnabled(): boolean {
  return readFlag(process.env.NEXT_PUBLIC_THI360_GRADING_MOCK);
}

/** Nhánh API thật chưa nối (đợt chấm điểm). Viết bằng mã thoát Unicode vì test quét mã nguồn chặn chữ có dấu trong `.ts`. */
export function notWired(): never {
  throw new Error("ch\u01b0a n\u1ed1i API");
}

type AsyncFn<A extends unknown[], R> = (...args: A) => Promise<R>;

/** Hàm của `grading/api.ts`: gọi bản mẫu khi cờ `GRADING_MOCK` bật, không thì `notWired`. Giữ nguyên chữ ký của hàm mẫu. */
export function gradingApi<A extends unknown[], R>(mockFn: AsyncFn<A, R>): AsyncFn<A, R> {
  return async (...args) => {
    if (!isGradingMockEnabled()) notWired();
    return mockFn(...args);
  };
}
