import { isContentMockEnabled } from "@/lib/mock/flags";

type AsyncFn<A extends unknown[], R> = (...args: A) => Promise<R>;

/**
 * Hàm của `api.ts`: chạy bản mẫu khi `useMock()` đúng, không thì chạy bản API thật. Giữ nguyên chữ ký của hàm mẫu
 * (plan B7). Mặc định theo cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK`; cờ đọc mỗi lần gọi nên test đổi cờ giữa chừng được.
 */
export function selectApi<A extends unknown[], R>(
  mockFn: AsyncFn<A, R>,
  realFn: AsyncFn<A, R>,
  useMock: () => boolean = isContentMockEnabled,
): AsyncFn<A, R> {
  return (...args) => (useMock() ? mockFn(...args) : realFn(...args));
}
