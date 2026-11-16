import { beforeEach, vi } from "vitest";

/**
 * Các test dưới đây kiểm hành vi của kho dữ liệu mẫu trong bộ nhớ (`mock/store`, `mock/review-store`): chúng đặt lại,
 * đọc và sửa kho đó trực tiếp. Gọi hàm này ở đầu tệp để ghim cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật cho từng test,
 * dù chạy với cờ nào (`NEXT_PUBLIC_THI360_CONTENT_MOCK=false pnpm test` cũng xanh). Nhánh API thật được kiểm ở
 * `*-real.test.ts` bằng `fetch` giả (`fake-exams-server.ts`), không dùng hàm này.
 */
export function useInMemoryBackend(): void {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "true");
  });
}
