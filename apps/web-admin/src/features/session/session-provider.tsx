"use client";

import { useQuery } from "@tanstack/react-query";
import { createContext, type ReactNode, useContext, useMemo } from "react";
import { loadSession as defaultLoadSession, SESSION_QUERY_KEY, type SessionLoader } from "./api";
import { hasPermission, type PermissionRequirement } from "./permissions";
import type { Session } from "./types";

export type SessionState =
  | { status: "loading" }
  | { status: "error"; error: Error; retry: () => void }
  | { status: "ready"; session: Session };

interface SessionContextValue {
  state: SessionState;
  permissions: ReadonlySet<string>;
}

const EMPTY_PERMISSIONS: ReadonlySet<string> = new Set<string>();
const SessionContext = createContext<SessionContextValue | null>(null);

/**
 * Cung cấp phiên đăng nhập một lần cho cả ứng dụng. 401 và 403 do `QueryProvider` xử lý chung,
 * còn lại hiện ở trạng thái `error` để màn hình cho thử lại.
 */
export function SessionProvider({
  children,
  loadSession = defaultLoadSession,
}: {
  children: ReactNode;
  loadSession?: SessionLoader;
}) {
  const query = useQuery({ queryKey: SESSION_QUERY_KEY, queryFn: loadSession, staleTime: 60_000 });
  const { data, error, refetch } = query;

  const value = useMemo<SessionContextValue>(() => {
    if (data) {
      return { state: { status: "ready", session: data }, permissions: new Set(data.permissions) };
    }
    if (error) {
      return {
        state: { status: "error", error, retry: () => void refetch() },
        permissions: EMPTY_PERMISSIONS,
      };
    }
    return { state: { status: "loading" }, permissions: EMPTY_PERMISSIONS };
  }, [data, error, refetch]);

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

function useSessionContext(): SessionContextValue {
  const context = useContext(SessionContext);
  if (!context) throw new Error("useSession/usePermissions phải nằm trong <SessionProvider>.");
  return context;
}

export function useSession(): SessionState {
  return useSessionContext().state;
}

export interface PermissionsApi {
  isLoading: boolean;
  /** Mã quyền hiệu lực của tài khoản đang đăng nhập. */
  permissions: ReadonlySet<string>;
  /** `true` khi có mã (hoặc một trong các mã) được yêu cầu. Luôn `false` khi chưa có phiên. */
  can: (required: PermissionRequirement) => boolean;
}

/** Chỉ để ẩn/hiện giao diện cho tiện; backend mới là nơi chặn (RULES mục 5). */
export function usePermissions(): PermissionsApi {
  const { state, permissions } = useSessionContext();
  return useMemo(
    () => ({
      isLoading: state.status === "loading",
      permissions,
      can: (required) => hasPermission(permissions, required),
    }),
    [state.status, permissions],
  );
}
