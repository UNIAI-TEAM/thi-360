"use client";

import type { ReactNode } from "react";
import { type MockFlag, isContentMockEnabled, isGradingMockEnabled } from "@/lib/mock/flags";
import { ForbiddenView } from "./forbidden-view";
import type { PermissionRequirement } from "./permissions";
import { usePermissions } from "./session-provider";

function flagOn(flag: MockFlag): boolean {
  return flag === "content" ? isContentMockEnabled() : isGradingMockEnabled();
}

/**
 * Cổng của một mục chạy được trên dữ liệu mẫu: cho vào khi người dùng có `permission` hoặc cờ dữ liệu mẫu bật; còn lại
 * hiện trang 403. Chỉ để ẩn hiện cho tiện, máy chủ mới là nơi chặn (RULES mục 5).
 */
export function MockAccessGate({
  permission,
  flag,
  children,
}: {
  permission: PermissionRequirement;
  flag: MockFlag;
  children: ReactNode;
}) {
  const { isLoading, can } = usePermissions();
  if (flagOn(flag) || can(permission)) return children;
  return isLoading ? null : <ForbiddenView />;
}
