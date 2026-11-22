"use client";

import { useQuery } from "@tanstack/react-query";
import { loadOptionalSession, OPTIONAL_SESSION_QUERY_KEY, type OptionalSessionLoader } from "./api";

/**
 * Phiên cho trang công khai: khách nhận `data = null` (không phải lỗi, không kích hoạt luồng 401 về `/login` của
 * `query-provider.tsx`). Thanh đầu trang và nút mua cùng khóa nên dùng chung một lời gọi `/api/v1/me`.
 */
export function useOptionalSession(loadCurrentSession: OptionalSessionLoader = loadOptionalSession) {
  return useQuery({ queryKey: OPTIONAL_SESSION_QUERY_KEY, queryFn: loadCurrentSession });
}
