"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Card, StatusChip } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { UserRbacTab, type UserRbacTabProps } from "@/features/rbac";
import { usePermissions } from "@/features/session/session-provider";
import { USERS_QUERY_KEYS, type UserDetail, type UserRoleSummary } from "../api";
import { formatRoleLabel } from "../utils";

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
 *
 * Tài khoản đã xóa mềm: backend từ chối cả ba API đọc phân quyền (404), nên không mount
 * `UserRbacTab` (không gửi request nào) mà chỉ hiện tóm tắt vai trò lấy từ hồ sơ tài khoản.
 */
export function UserRolesPermissionsPlaceholder({
  userId,
  roles,
  readOnly,
  username,
  fullName,
  userRbacTabProps,
}: UserRolesPermissionsPlaceholderProps) {
  const { can, isLoading: sessionLoading } = usePermissions();
  const queryClient = useQueryClient();
  const detail = queryClient.getQueryData<UserDetail>(USERS_QUERY_KEYS.detail(userId));
  const isDeleted = Boolean(detail?.deletedAt);

  if (isDeleted) {
    return <DeletedUserRolesSummary userId={userId} roles={roles ?? detail?.roles ?? []} />;
  }

  const isReadOnly = readOnly ?? (sessionLoading || !can("USER.ASSIGN"));

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

function DeletedUserRolesSummary({
  userId,
  roles,
}: {
  userId: string;
  roles: UserRoleSummary[];
}) {
  const t = useTranslations("rbac");
  return (
    <Card
      padding="md"
      data-slot="user-roles-deleted-summary"
      data-user-id={userId}
      className="flex flex-col gap-3.5"
    >
      <h2 className="text-title font-semibold text-text">{t("userRbac.rolesSectionTitle")}</h2>
      <p role="note" className="text-body text-text-muted">
        {t("userRbac.deletedAccountNote")}
      </p>
      {roles.length === 0 ? (
        <p className="text-body text-text-muted">{t("userRbac.rolesEmpty")}</p>
      ) : (
        <ul className="m-0 flex list-none flex-col gap-2.5 p-0">
          {roles.map((role) => (
            <li
              key={role.id}
              className="flex items-center justify-between gap-3 rounded-md border border-border bg-surface p-3"
            >
              <div className="min-w-0">
                <div className="font-semibold text-text">
                  {formatRoleLabel(role, (code) => t(`systemRoles.${code}`))}
                </div>
                <div className="text-caption text-text-muted">{role.code}</div>
              </div>
              <StatusChip tone={role.access === "MANAGE" ? "info" : "warning"}>
                {t(`access.${role.access}`)}
              </StatusChip>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
