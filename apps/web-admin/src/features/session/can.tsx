"use client";

import type { ReactNode } from "react";
import type { PermissionRequirement } from "./permissions";
import { usePermissions } from "./session-provider";

interface CanProps {
  /** Một mã quyền, hoặc danh sách mã mà có một trong số đó là đủ. */
  permission: PermissionRequirement;
  /** Hiện khi thiếu quyền hoặc phiên chưa tải xong. Mặc định không hiện gì. */
  fallback?: ReactNode;
  children: ReactNode;
}

export function Can({ permission, fallback = null, children }: CanProps) {
  const { can } = usePermissions();
  return <>{can(permission) ? children : fallback}</>;
}
