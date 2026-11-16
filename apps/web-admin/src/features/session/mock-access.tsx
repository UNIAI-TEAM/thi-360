"use client";

import type { ReactNode } from "react";
import { isGradingMockEnabled } from "@/lib/mock/flags";
import { ForbiddenView } from "./forbidden-view";
import type { PermissionRequirement } from "./permissions";
import { usePermissions } from "./session-provider";

/**
 * Cổng của một mục trong nhóm Nội dung: cho vào khi người dùng có `permission`; với `flag="grading"` (chỉ còn
 * `/grading` sau QA UNI-2621) cũng cho vào khi cờ `GRADING_MOCK` bật. Còn lại hiện trang 403. Chỉ để ẩn hiện cho
 * tiện, máy chủ mới là nơi chặn (RULES mục 5).
 */
export function MockAccessGate({
  permission,
  flag,
  children,
}: {
  permission: PermissionRequirement;
  flag?: "grading";
  children: ReactNode;
}) {
  const { isLoading, can } = usePermissions();
  if ((flag === "grading" && isGradingMockEnabled()) || can(permission)) return children;
  return isLoading ? null : <ForbiddenView />;
}
