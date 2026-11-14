"use client";

import { Badge, Card, EmptyState } from "@thi360/ui";
import { Shield } from "lucide-react";
import { useTranslations } from "next-intl";
import type { UserRoleSummary } from "../api";
import { formatRoleLabel } from "../utils";

export interface UserRolesPermissionsPlaceholderProps {
  userId: string;
  roles: UserRoleSummary[];
}

/**
 * Chỗ chờ cho thành phần Vai trò & quyền từ T08.
 * Hiện tóm tắt vai trò đang có của tài khoản cho tới khi T08 merge vào integration/dot-1.
 */
export function UserRolesPermissionsPlaceholder({
  userId,
  roles,
}: UserRolesPermissionsPlaceholderProps) {
  const t = useTranslations("users.rolesPlaceholder");
  const rbacT = useTranslations("rbac");
  return (
    <Card data-slot="user-roles-permissions-placeholder" data-user-id={userId} className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <p className="text-field-label font-semibold text-text-secondary">
          {t("currentRolesLabel")}
        </p>
        {roles.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {roles.map((role) => (
              <Badge key={role.id}>
                {formatRoleLabel(role, (code) => rbacT(`systemRoles.${code}`))}
              </Badge>
            ))}
          </div>
        ) : (
          <p className="text-body text-text-muted">{t("noRoles")}</p>
        )}
      </div>
      <EmptyState icon={<Shield />} title={t("title")} description={t("description")} />
    </Card>
  );
}
