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
 * Tab "Vai tro & quyen" tren chi tiet tai khoan — hien thi `UserRbacTab` (T08).
 * Giu nguyen ten file va ten export de tranh xung dot khi merge voi cac lane khac.
 *
 * Tai khoan da xoa mem: backend tu choi ca ba API doc phan quyen (404), nen khong mount
 * `UserRbacTab` (khong gui request nao) ma chi hien tom tat vai tro lay tu ho so tai khoan.
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
