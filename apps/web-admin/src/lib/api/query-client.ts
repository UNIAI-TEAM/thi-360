import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";
/** Lỗi mang mã trạng thái HTTP. */
export class ApiStatusError extends Error {
  readonly status: number;

  constructor(status: number, message = "") {
    super(message);
    this.name = "ApiStatusError";
    this.status = status;
  }
}

export interface ApiErrorHandlers {
  /** 401: phiên hết hạn hoặc chưa đăng nhập. */
  onUnauthenticated: () => void;
  /** 403: có phiên nhưng thiếu quyền. */
  onForbidden: () => void;
}

export function getErrorStatus(error: unknown): number | null {
  if (typeof error !== "object" || error === null || !("status" in error)) return null;
  return typeof error.status === "number" ? error.status : null;
}

/** Xử lý lỗi dùng chung theo mã HTTP. Lỗi khác (400, 409, 5xx…) do màn hình tự hiển thị. */
export function handleApiError(error: unknown, handlers: ApiErrorHandlers): void {
  const status = getErrorStatus(error);
  if (status === 401) handlers.onUnauthenticated();
  else if (status === 403) handlers.onForbidden();
}

/** Không thử lại lỗi 4xx: lặp lại cũng cho kết quả đó, và 401/403 đã được xử lý chung. */
function shouldRetry(failureCount: number, error: unknown): boolean {
  const status = getErrorStatus(error);
  if (status !== null && status >= 400 && status < 500) return false;
  return failureCount < 2;
}

export function createQueryClient(handlers: ApiErrorHandlers): QueryClient {
  return new QueryClient({
    queryCache: new QueryCache({ onError: (error) => handleApiError(error, handlers) }),
    mutationCache: new MutationCache({ onError: (error) => handleApiError(error, handlers) }),
    defaultOptions: {
      queries: { retry: shouldRetry, refetchOnWindowFocus: false },
    },
  });
}
