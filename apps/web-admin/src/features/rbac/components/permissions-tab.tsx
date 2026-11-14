"use client";

import { useQuery } from "@tanstack/react-query";
import { Button, Card, EmptyState, Input, Select, Skeleton, StatusChip } from "@thi360/ui";
import { Info, KeyRound, Search, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { usePermissions } from "@/features/session/session-provider";
import { listPermissions as defaultListPermissions, rbacKeys } from "../api";
import {
  KNOWN_OBJECT_CODES,
  resolveObjectName,
  resolvePermissionName,
  resolvePermissionShortName,
  type RbacTranslator,
} from "../messages";
import type { AccessScope, PermissionResponse } from "../types";

export interface PermissionsTabProps {
  listPermissions?: () => Promise<PermissionResponse[]>;
  onSelectPermissionUsers?: (permissionCode: string) => void;
}

interface PermissionGroupRows {
  objectCode: string;
  objectName: string;
  items: PermissionResponse[];
}

function getObjectRank(objectCode: string): number {
  const index = (KNOWN_OBJECT_CODES as readonly string[]).indexOf(objectCode);
  return index === -1 ? KNOWN_OBJECT_CODES.length : index;
}

export function PermissionsTab({
  listPermissions = defaultListPermissions,
  onSelectPermissionUsers,
}: PermissionsTabProps) {
  const t = useTranslations("rbac");
  const commonT = useTranslations("common");
  const rbacT: RbacTranslator = (key, values) => t(key, values);
  const { can } = usePermissions();

  const canOpenUsersTab = can("PERMISSION.VIEW") && can("USER.VIEW");

  const [search, setSearch] = useState("");
  const [objectFilter, setObjectFilter] = useState<string>("ALL");
  const [scopeFilter, setScopeFilter] = useState<"ALL" | AccessScope>("ALL");

  const permissionsQuery = useQuery({
    queryKey: rbacKeys.permissions(),
    queryFn: () => listPermissions(),
  });

  const objectOptions = useMemo(() => {
    const seen = new Map<string, string>();
    for (const code of KNOWN_OBJECT_CODES) {
      seen.set(code, resolveObjectName(rbacT, code));
    }
    for (const item of permissionsQuery.data ?? []) {
      if (!seen.has(item.groupCode)) {
        seen.set(item.groupCode, resolveObjectName(rbacT, item.groupCode, item.groupName));
      }
    }
    return [
      { value: "ALL", label: t("permissionsTab.filterObjectAll") },
      ...Array.from(seen.entries())
        .sort(([a], [b]) => getObjectRank(a) - getObjectRank(b) || a.localeCompare(b))
        .map(([value, label]) => ({ value, label })),
    ];
  }, [permissionsQuery.data, t]); // eslint-disable-line react-hooks/exhaustive-deps

  const scopeOptions = useMemo(
    () => [
      { value: "ALL", label: t("permissionsTab.filterScopeAll") },
      { value: "MANAGE", label: t("access.MANAGE") },
      { value: "EXAM", label: t("access.EXAM") },
    ],
    [t],
  );

  const groupedRows = useMemo<PermissionGroupRows[]>(() => {
    const items = permissionsQuery.data ?? [];
    const normalizedSearch = search.trim().toLowerCase();

    const filtered = items.filter((item) => {
      if (objectFilter !== "ALL" && item.groupCode !== objectFilter) return false;
      if (scopeFilter !== "ALL" && item.scope !== scopeFilter) return false;
      if (!normalizedSearch) return true;

      const objectName = resolveObjectName(rbacT, item.groupCode, item.groupName).toLowerCase();
      const permName = resolvePermissionName(rbacT, item.code, item.name).toLowerCase();
      const shortName = resolvePermissionShortName(rbacT, item.code, item.name).toLowerCase();
      return (
        item.code.toLowerCase().includes(normalizedSearch) ||
        item.name.toLowerCase().includes(normalizedSearch) ||
        objectName.includes(normalizedSearch) ||
        permName.includes(normalizedSearch) ||
        shortName.includes(normalizedSearch)
      );
    });

    const groups = new Map<string, PermissionGroupRows>();
    for (const item of filtered) {
      let group = groups.get(item.groupCode);
      if (!group) {
        group = {
          objectCode: item.groupCode,
          objectName: resolveObjectName(rbacT, item.groupCode, item.groupName),
          items: [],
        };
        groups.set(item.groupCode, group);
      }
      group.items.push(item);
    }

    return Array.from(groups.values())
      .sort(
        (a, b) =>
          getObjectRank(a.objectCode) - getObjectRank(b.objectCode) ||
          a.objectCode.localeCompare(b.objectCode),
      )
      .map((group) => ({
        ...group,
        items: [...group.items].sort((a, b) => a.bitIndex - b.bitIndex),
      }));
  }, [objectFilter, permissionsQuery.data, scopeFilter, search, t]); // eslint-disable-line react-hooks/exhaustive-deps

  const totalPermissions = groupedRows.reduce((sum, group) => sum + group.items.length, 0);

  return (
    <div className="flex flex-col gap-4">
      <div
        role="note"
        className="flex items-start gap-2.5 rounded-lg border border-info/30 bg-info-bg px-3.5 py-3 text-body text-info"
      >
        <Info aria-hidden="true" className="mt-0.5 size-[18px] shrink-0" />
        <span>{t("permissionsTab.bannerNote")}</span>
      </div>

      <Card padding="md" className="flex flex-wrap items-end gap-3">
        <label className="flex min-w-[220px] flex-[2_1_240px] flex-col gap-1.5 text-caption text-text-muted">
          <span>{t("permissionsTab.filterSearchLabel")}</span>
          <Input
            type="search"
            value={search}
            leadingIcon={<Search />}
            placeholder={t("permissionsTab.filterSearchPlaceholder")}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>

        <div className="flex min-w-[160px] flex-[1_1_160px] flex-col gap-1.5">
          <span className="text-caption text-text-muted">{t("permissionsTab.filterObjectLabel")}</span>
          <Select
            aria-label={t("permissionsTab.filterObjectLabel")}
            value={objectFilter}
            options={objectOptions}
            onValueChange={setObjectFilter}
          />
        </div>

        <div className="flex min-w-[160px] flex-[1_1_160px] flex-col gap-1.5">
          <span className="text-caption text-text-muted">{t("permissionsTab.filterScopeLabel")}</span>
          <Select
            aria-label={t("permissionsTab.filterScopeLabel")}
            value={scopeFilter}
            options={scopeOptions}
            onValueChange={(next) => setScopeFilter(next as "ALL" | AccessScope)}
          />
        </div>
      </Card>

      {permissionsQuery.isPending ? (
        <Card padding="md" className="flex flex-col gap-3">
          <div role="status" aria-label={commonT("loading")} className="flex flex-col gap-3">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        </Card>
      ) : permissionsQuery.isError ? (
        <Card padding="md">
          <EmptyState
            icon={<TriangleAlert />}
            title={t("permissionsTab.errorTitle")}
            description={t("permissionsTab.errorDescription")}
            action={
              <Button variant="secondary" onClick={() => permissionsQuery.refetch()}>
                {t("actions.retry")}
              </Button>
            }
          />
        </Card>
      ) : groupedRows.length === 0 ? (
        <Card padding="md">
          <EmptyState
            icon={<KeyRound />}
            title={t("permissionsTab.emptyTitle")}
            description={t("permissionsTab.emptyDescription")}
          />
        </Card>
      ) : (
        <Card padding="none" className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[780px] border-collapse">
              <thead>
                <tr className="bg-table-head text-left text-field-label text-text-muted">
                  <th scope="col" className="px-4 py-3 font-semibold">
                    {t("permissionsTab.columns.name")}
                  </th>
                  <th scope="col" className="px-3 py-3 font-semibold">
                    {t("permissionsTab.columns.code")}
                  </th>
                  <th scope="col" className="px-3 py-3 font-semibold">
                    {t("permissionsTab.columns.scope")}
                  </th>
                  <th scope="col" className="px-3 py-3 font-semibold">
                    {t("permissionsTab.columns.roles")}
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    {t("permissionsTab.columns.users")}
                  </th>
                </tr>
              </thead>
              <tbody>
                {groupedRows.map((group) => (
                  <PermissionGroupSection
                    key={group.objectCode}
                    group={group}
                    rbacT={rbacT}
                    t={t}
                    canOpenUsersTab={canOpenUsersTab}
                    onSelectPermissionUsers={onSelectPermissionUsers}
                  />
                ))}
              </tbody>
            </table>
          </div>
          <div className="border-t border-border px-4 py-3 text-caption text-text-muted">
            {t("permissionsTab.footerSummary", {
              permissionCount: totalPermissions,
              objectCount: groupedRows.length,
            })}
          </div>
        </Card>
      )}
    </div>
  );
}

function PermissionGroupSection({
  group,
  rbacT,
  t,
  canOpenUsersTab,
  onSelectPermissionUsers,
}: {
  group: PermissionGroupRows;
  rbacT: RbacTranslator;
  t: ReturnType<typeof useTranslations<"rbac">>;
  canOpenUsersTab: boolean;
  onSelectPermissionUsers?: (permissionCode: string) => void;
}) {
  return (
    <>
      <tr className="border-t border-border bg-page">
        <th
          scope="colgroup"
          colSpan={5}
          className="px-4 py-2 text-left text-field-label font-semibold text-text-secondary"
        >
          {group.objectName}
        </th>
      </tr>
      {group.items.map((item) => {
        const isLevel = item.code.endsWith(".VIEW") || item.code.endsWith(".MODIFY");
        const baseName = resolvePermissionName(rbacT, item.code, item.name);
        const displayName = isLevel
          ? baseName
          : t("permissionsTab.extraPrefix", { name: baseName });
        const shortName = resolvePermissionShortName(rbacT, item.code, item.name);

        return (
          <tr key={item.id} className="h-12 border-t border-border bg-surface">
            <td className="px-4 py-2 font-medium text-text">{displayName}</td>
            <td className="px-3 py-2">
              <code className="text-field-label text-text-muted">{item.code}</code>
            </td>
            <td className="px-3 py-2">
              <StatusChip tone={item.scope === "MANAGE" ? "info" : "warning"}>
                {t(`access.${item.scope}`)}
              </StatusChip>
            </td>
            <td className="px-3 py-2 text-text-secondary tabular-nums">
              {item.roleCount > 0
                ? t("permissionsTab.roleCountValue", { count: item.roleCount })
                : "—"}
            </td>
            <td className="px-4 py-2">
              {canOpenUsersTab && onSelectPermissionUsers ? (
                <button
                  type="button"
                  aria-label={t("permissionsTab.userCountAria", { name: shortName })}
                  onClick={() => onSelectPermissionUsers(item.code)}
                  className="inline-flex min-h-11 cursor-pointer items-center font-semibold text-primary-text tabular-nums hover:underline focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-primary-600/30"
                >
                  {t("permissionsTab.userCountValue", { count: item.userCount })}
                </button>
              ) : (
                <span className="font-medium text-text-secondary tabular-nums">
                  {t("permissionsTab.userCountValue", { count: item.userCount })}
                </span>
              )}
            </td>
          </tr>
        );
      })}
    </>
  );
}
