"use client";

import { useQuery } from "@tanstack/react-query";
import { loadOptionalSession, OPTIONAL_SESSION_QUERY_KEY, type OptionalSessionLoader } from "@/features/session/api";
import { type HeaderAccount, StudentHeader } from "./student-header";

/**
 * Thanh đầu trang của trang công khai: đọc phiên theo cách tùy chọn (khách không bị đưa về `/login`) rồi hiện bản
 * khách hoặc bản đã đăng nhập. Lỗi tải phiên (không phải 401) hiện như khách: nút mua của trang tự xử lý trạng thái
 * "chưa biết quyền".
 */
export function PublicStudentHeader({
  loadCurrentSession = loadOptionalSession,
}: {
  loadCurrentSession?: OptionalSessionLoader;
}) {
  const sessionQuery = useQuery({
    queryKey: OPTIONAL_SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
  });

  const account: HeaderAccount = sessionQuery.isLoading
    ? { kind: "loading" }
    : sessionQuery.data
      ? { kind: "user", session: sessionQuery.data }
      : { kind: "guest" };

  return <StudentHeader account={account} />;
}
