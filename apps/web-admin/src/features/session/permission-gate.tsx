"use client";

import type { ReactNode } from "react";
import { ForbiddenView } from "./forbidden-view";
import type { PermissionRequirement } from "./permissions";
import { usePermissions } from "./session-provider";

/**
 * Cổng quyền của các màn được bảo vệ: chỉ cho vào khi người dùng có `permission`, còn lại hiện trang 403.
 * Chỉ để ẩn hiện cho tiện, máy chủ mới là nơi chặn (RULES mục 5).
 */
export function PermissionGate({
  permission,
  children,
}: {
  permission: PermissionRequirement;
  children: ReactNode;
}) {
  const { isLoading, can } = usePermissions();
  if (can(permission)) return children;
  return isLoading ? null : <ForbiddenView />;
}
