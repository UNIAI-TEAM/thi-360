"use client";

import { useQueryClient } from "@tanstack/react-query";
import { UserRbacTab, type UserRbacTabProps } from "@/features/rbac";
import { usePermissions } from "@/features/session/session-provider";
import { USERS_QUERY_KEYS, type UserDetail, type UserRoleSummary } from "../api";

export interface UserRolesPermissionsPlaceholderProps {
  userId: string;
  roles?: UserRoleSummary[];
  readOnly?: boolean;
  username?: string;
  fullName?: string;
  userRbacTabProps?: Omit<UserRbacTabProps, "userId" | "readOnly" | "username" | "fullName">;
}

/**
 * Tab "Vai trò & quyền" trên chi tiết tài khoản — hiển thị `UserRbacTab` (T08).
 * Giữ nguyên tên file và tên export để tránh xung đột khi merge với các lane khác.
 */
export function UserRolesPermissionsPlaceholder({
  userId,
  readOnly,
  username,
  fullName,
  userRbacTabProps,
}: UserRolesPermissionsPlaceholderProps) {
  const { can, isLoading: sessionLoading } = usePermissions();
  const queryClient = useQueryClient();
  const detail = queryClient.getQueryData<UserDetail>(USERS_QUERY_KEYS.detail(userId));
  const isDeleted = Boolean(detail?.deletedAt);
  const isReadOnly = readOnly ?? (sessionLoading || !can("USER.ASSIGN") || isDeleted);

  return (
    <UserRbacTab
      userId={userId}
      readOnly={isReadOnly}
      username={username ?? detail?.username}
      fullName={fullName ?? detail?.fullName}
      {...userRbacTabProps}
    />
  );
}
