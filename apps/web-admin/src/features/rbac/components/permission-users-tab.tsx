"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ColumnDef } from "@tanstack/react-table";
import {
  Button,
  Card,
  Chip,
  cn,
  ConfirmDialog,
  EmptyState,
  IconButton,
  Input,
  notify,
  Pagination,
  Select,
  Skeleton,
  StatusChip,
  Table,
} from "@thi360/ui";
import { Download, MinusCircle, Search, Shield, TriangleAlert, Users } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
import { usePermissions } from "@/features/session/session-provider";
import {
  exportUsersByPermission as defaultExportUsersByPermission,
  getUserPermissions as defaultGetUserPermissions,
  listPermissions as defaultListPermissions,
  listUsersByPermission as defaultListUsersByPermission,
  rbacKeys,
  updateUserPermissions as defaultUpdateUserPermissions,
  type ListPermissionUsersParams,
} from "../api";
import {
  KNOWN_OBJECT_CODES,
  resolveObjectName,
  resolvePermissionName,
  resolvePermissionShortName,
  resolveRbacErrorMessage,
  resolveRoleName,
  type RbacTranslator,
} from "../messages";
import type {
  PageResponsePermissionUserResponse,
  PermissionResponse,
  PermissionSourceType,
  PermissionUserResponse,
  UpdateUserPermissionsRequest,
  UserDirectPermissionsResponse,
  UserStatus,
} from "../types";

export interface PermissionUsersTabProps {
  selectedPermissionCode?: string | null;
  onSelectPermission?: (permissionCode: string) => void;
  onAssignMany?: (permissionCode: string) => void;
  onViewRoles?: () => void;
  listPermissions?: () => Promise<PermissionResponse[]>;
  listUsersByPermission?: (
    id: string,
    params?: ListPermissionUsersParams,
  ) => Promise<PageResponsePermissionUserResponse>;
  exportUsersByPermission?: (
    id: string,
    params?: Omit<ListPermissionUsersParams, "page" | "size">,
  ) => Promise<{ blob: Blob; fileName: string }>;
  getUserPermissions?: (userId: string) => Promise<UserDirectPermissionsResponse>;
  updateUserPermissions?: (
    userId: string,
    payload: UpdateUserPermissionsRequest,
  ) => Promise<UserDirectPermissionsResponse>;
}

function getObjectRank(objectCode: string): number {
  const index = (KNOWN_OBJECT_CODES as readonly string[]).indexOf(objectCode);
  return index === -1 ? KNOWN_OBJECT_CODES.length : index;
}

function triggerBlobDownload(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function PermissionUsersTab({
  selectedPermissionCode,
  onSelectPermission,
  onAssignMany,
  onViewRoles,
  listPermissions = defaultListPermissions,
  listUsersByPermission = defaultListUsersByPermission,
  exportUsersByPermission = defaultExportUsersByPermission,
  getUserPermissions = defaultGetUserPermissions,
  updateUserPermissions = defaultUpdateUserPermissions,
}: PermissionUsersTabProps) {
  const t = useTranslations("rbac");
  const commonT = useTranslations("common");
  const formatter = useFormatter();
  const rbacT: RbacTranslator = (key, values) => t(key, values);
  const { can } = usePermissions();
  const queryClient = useQueryClient();

  const canAssign = can("USER.ASSIGN");
  const canViewRoles = can("ROLE.VIEW");

  const [permissionSearch, setPermissionSearch] = useState("");
  const [accountSearch, setAccountSearch] = useState("");
  const [sourceFilter, setSourceFilter] = useState<"ALL" | PermissionSourceType>("ALL");
  const [statusFilter, setStatusFilter] = useState<"ALL" | UserStatus>("ALL");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [revokeTarget, setRevokeTarget] = useState<PermissionUserResponse | null>(null);

  const permissionsQuery = useQuery({
    queryKey: rbacKeys.permissions(),
    queryFn: () => listPermissions(),
  });

  const sortedPermissions = useMemo(() => {
    const items = permissionsQuery.data ?? [];
    return [...items].sort(
      (a, b) =>
        getObjectRank(a.groupCode) - getObjectRank(b.groupCode) || a.bitIndex - b.bitIndex,
    );
  }, [permissionsQuery.data]);

  const filteredPermissions = useMemo(() => {
    const query = permissionSearch.trim().toLowerCase();
    if (!query) return sortedPermissions;
    return sortedPermissions.filter((perm) => {
      const objectName = resolveObjectName(rbacT, perm.groupCode, perm.groupName).toLowerCase();
      const permName = resolvePermissionName(rbacT, perm.code, perm.name).toLowerCase();
      return (
        perm.code.toLowerCase().includes(query) ||
        objectName.includes(query) ||
        permName.includes(query) ||
        perm.name.toLowerCase().includes(query)
      );
    });
  }, [permissionSearch, sortedPermissions, t]); // eslint-disable-line react-hooks/exhaustive-deps

  const activePermission = useMemo(() => {
    if (sortedPermissions.length === 0) return null;
    if (selectedPermissionCode) {
      const matched = sortedPermissions.find(
        (item) => item.code.toUpperCase() === selectedPermissionCode.toUpperCase(),
      );
      if (matched) return matched;
    }
    return sortedPermissions[0]!;
  }, [selectedPermissionCode, sortedPermissions]);

  const userQueryParams: ListPermissionUsersParams = useMemo(
    () => ({
      ...(sourceFilter !== "ALL" ? { source: sourceFilter } : {}),
      ...(statusFilter !== "ALL" ? { status: statusFilter } : {}),
      ...(accountSearch.trim() ? { q: accountSearch.trim() } : {}),
      page: page - 1,
      size: pageSize,
    }),
    [accountSearch, page, pageSize, sourceFilter, statusFilter],
  );

  const usersQuery = useQuery({
    queryKey: activePermission
      ? rbacKeys.permissionUsers(activePermission.id, userQueryParams)
      : ["rbac", "permission-users", "none"],
    queryFn: () => listUsersByPermission(activePermission!.id, userQueryParams),
    placeholderData: keepPreviousWhileSameScope(
      activePermission ? rbacKeys.permissionUsers(activePermission.id, userQueryParams) : ["rbac", "permission-users", "none"],
    ),
    enabled: Boolean(activePermission),
  });

  const exportMutation = useMutation({
    mutationFn: async () => {
      if (!activePermission) return null;
      return exportUsersByPermission(activePermission.id, {
        ...(sourceFilter !== "ALL" ? { source: sourceFilter } : {}),
        ...(statusFilter !== "ALL" ? { status: statusFilter } : {}),
        ...(accountSearch.trim() ? { q: accountSearch.trim() } : {}),
      });
    },
    onSuccess: (result) => {
      if (!result) return;
      triggerBlobDownload(result.blob, result.fileName);
    },
    onError: (error) => {
      notify.error(resolveRbacErrorMessage(rbacT, error, "permissionUsers.exportError"));
    },
  });

  const revokeMutation = useMutation({
    mutationFn: async (user: PermissionUserResponse) => {
      if (!activePermission) return;
      const current = await getUserPermissions(user.userId);
      const nextCodes = current.permissions
        .map((item) => item.permissionCode)
        .filter((code) => code !== activePermission.code);
      await updateUserPermissions(user.userId, { permissionCodes: nextCodes });
    },
    onSuccess: async (_data, user) => {
      setRevokeTarget(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["rbac", "permissions"] }),
        queryClient.invalidateQueries({ queryKey: ["rbac", "permission-users"] }),
        queryClient.invalidateQueries({ queryKey: ["rbac", "users"] }),
        queryClient.invalidateQueries({ queryKey: rbacKeys.userRoles(user.userId) }),
        queryClient.invalidateQueries({ queryKey: rbacKeys.userDirectPermissions(user.userId) }),
        queryClient.invalidateQueries({ queryKey: rbacKeys.userEffectivePermissions(user.userId) }),
      ]);
      notify.success(t("permissionUsers.revokeSuccess", { username: user.username }));
    },
    onError: (error) => {
      setRevokeTarget(null);
      notify.error(resolveRbacErrorMessage(rbacT, error, "permissionUsers.revokeError"));
    },
  });

  const sourceOptions = useMemo(
    () => [
      { value: "ALL", label: t("permissionUsers.filterSourceAll") },
      { value: "ROLE", label: t("permissionUsers.filterSourceRole") },
      { value: "DIRECT", label: t("permissionUsers.filterSourceDirect") },
    ],
    [t],
  );

  const statusOptions = useMemo(
    () => [
      { value: "ALL", label: t("permissionUsers.filterStatusAll") },
      { value: "ACTIVE", label: t("permissionUsers.filterStatusActive") },
      { value: "INACTIVE", label: t("permissionUsers.filterStatusInactive") },
    ],
    [t],
  );

  const columns = useMemo<ColumnDef<PermissionUserResponse>[]>(() => {
    return [
      {
        id: "account",
        header: t("permissionUsers.columns.account"),
        enableSorting: false,
        cell: ({ row }) => {
          const u = row.original;
          return (
            <div className="py-2">
              <div className="font-semibold text-text">{u.username}</div>
              <div className="text-caption text-text-muted">
                {u.fullName} · {u.code}
              </div>
            </div>
          );
        },
      },
      {
        id: "source",
        header: t("permissionUsers.columns.source"),
        enableSorting: false,
        cell: ({ row }) => {
          const sources = row.original.sources;
          const hasRole = sources.some((item) => item.type === "ROLE");
          const hasDirect = sources.some((item) => item.type === "DIRECT");
          return (
            <div className="flex flex-wrap gap-1.5">
              {hasRole && (
                <Chip variant="tag">{t("permissionUsers.roleSource")}</Chip>
              )}
              {hasDirect && (
                <StatusChip tone="warning">{t("permissionUsers.directSource")}</StatusChip>
              )}
            </div>
          );
        },
      },
      {
        id: "detail",
        header: t("permissionUsers.columns.detail"),
        enableSorting: false,
        cell: ({ row }) => {
          const sources = row.original.sources;
          const roleSources = sources.filter((item) => item.type === "ROLE");
          const directSource = sources.find((item) => item.type === "DIRECT");

          const roleNames = roleSources.map((item) =>
            resolveRoleName(rbacT, item.roleCode ?? "", item.roleName ?? ""),
          );

          return (
            <div className="flex flex-col gap-1 text-text-secondary">
              {roleNames.length > 0 && (
                <span>
                  {t("permissionUsers.roleDetail", { roles: roleNames.join(", ") })}
                </span>
              )}
              {directSource && (
                <span>
                  {t("permissionUsers.directDetail", {
                    actor: directSource.grantedByUsername ?? t("permissionUsers.unknownActor"),
                    date: directSource.grantedAt
                      ? formatter.dateTime(new Date(directSource.grantedAt), {
                          day: "2-digit",
                          month: "2-digit",
                          year: "numeric",
                        })
                      : "—",
                    reason: directSource.reason ?? "",
                  })}
                </span>
              )}
            </div>
          );
        },
      },
      {
        id: "status",
        header: t("permissionUsers.columns.status"),
        enableSorting: false,
        cell: ({ row }) => {
          const status = row.original.status;
          const tone =
            status === "ACTIVE" ? "success" : status === "INACTIVE" ? "neutral" : "info";
          return <StatusChip tone={tone}>{t(`userStatuses.${status}`)}</StatusChip>;
        },
      },
      {
        id: "actions",
        header: () => (
          <span className="block text-right">{t("permissionUsers.columns.actions")}</span>
        ),
        enableSorting: false,
        cell: ({ row }) => {
          const u = row.original;
          const hasDirect = u.sources.some((item) => item.type === "DIRECT");
          const hasRole = u.sources.some((item) => item.type === "ROLE");

          return (
            <div className="flex items-center justify-end gap-1">
              {hasDirect && canAssign && (
                <IconButton
                  icon={<MinusCircle />}
                  tone="danger"
                  aria-label={t("permissionUsers.revokeAria", { username: u.username })}
                  tooltip={t("permissionUsers.revokeTooltip")}
                  onClick={() => setRevokeTarget(u)}
                />
              )}
              {hasRole && canViewRoles && onViewRoles && (
                <IconButton
                  icon={<Shield />}
                  aria-label={t("permissionUsers.viewRoleAria", { username: u.username })}
                  tooltip={t("permissionUsers.viewRoleTooltip")}
                  onClick={onViewRoles}
                />
              )}
            </div>
          );
        },
      },
    ];
  }, [canAssign, canViewRoles, formatter, onViewRoles, t]); // eslint-disable-line react-hooks/exhaustive-deps

  const activeObjectName = activePermission
    ? resolveObjectName(rbacT, activePermission.groupCode, activePermission.groupName)
    : "";
  const activePermissionName = activePermission
    ? resolvePermissionName(rbacT, activePermission.code, activePermission.name)
    : "";
  const activePermissionShortName = activePermission
    ? resolvePermissionShortName(rbacT, activePermission.code, activePermission.name)
    : "";

  const users = usersQuery.data?.items ?? [];
  const totalUsers = usersQuery.data?.totalItems ?? 0;

  return (
    <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
      <Card
        padding="none"
        role="region"
        aria-labelledby="rbac-perm-picker-heading"
        className="w-full overflow-hidden lg:w-[320px] lg:shrink-0"
      >
        <div className="flex flex-col gap-2.5 p-4">
          <h2 id="rbac-perm-picker-heading" className="text-title font-semibold text-text">
            {t("permissionUsers.pickerLabel")}
          </h2>
          <label className="flex flex-col gap-1.5 text-caption text-text-muted">
            <span>{t("permissionUsers.pickerSearchLabel")}</span>
            <Input
              type="search"
              value={permissionSearch}
              leadingIcon={<Search />}
              placeholder={t("permissionUsers.pickerSearchPlaceholder")}
              onChange={(event) => setPermissionSearch(event.target.value)}
            />
          </label>
        </div>

        {permissionsQuery.isPending ? (
          <div role="status" aria-label={commonT("loading")} className="flex flex-col gap-2 p-4 pt-0">
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
          </div>
        ) : permissionsQuery.isError ? (
          <div className="p-4 pt-0">
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
          </div>
        ) : filteredPermissions.length === 0 ? (
          <div className="p-4 pt-0">
            <EmptyState
              icon={<Search />}
              title={t("permissionsTab.emptyTitle")}
              description={t("permissionsTab.emptyDescription")}
            />
          </div>
        ) : (
          <ul className="m-0 max-h-[640px] list-none overflow-y-auto p-0">
            {filteredPermissions.map((perm) => {
              const isSelected = activePermission?.id === perm.id;
              const objName = resolveObjectName(rbacT, perm.groupCode, perm.groupName);
              const permName = resolvePermissionName(rbacT, perm.code, perm.name);
              return (
                <li key={perm.id} className="border-t border-border">
                  <button
                    type="button"
                    aria-pressed={isSelected}
                    onClick={() => {
                      setPage(1);
                      onSelectPermission?.(perm.code);
                    }}
                    className={cn(
                      "flex min-h-[60px] w-full cursor-pointer items-center gap-2.5 px-4 py-2 text-left transition-colors duration-150 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-primary-600/30",
                      isSelected
                        ? "border-l-[3px] border-l-primary-600 bg-primary-50"
                        : "bg-surface hover:bg-subtle",
                    )}
                  >
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-semibold text-text">
                        {objName} · {permName}
                      </span>
                      <span className="block text-caption text-text-muted">{perm.code}</span>
                    </span>
                    <Chip variant="tag" className="shrink-0 tabular-nums">
                      {t("permissionUsers.userCountChip", { count: perm.userCount })}
                    </Chip>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </Card>

      <Card
        padding="none"
        role="region"
        aria-labelledby="rbac-perm-users-heading"
        className="min-w-0 flex-1 overflow-hidden"
      >
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3.5">
          <div className="min-w-0">
            <h2 id="rbac-perm-users-heading" className="text-title font-semibold text-text">
              {activePermission ? (
                <>
                  {activeObjectName} · {activePermissionName} ·{" "}
                  <span className="font-normal text-text-muted">{activePermission.code}</span>
                </>
              ) : (
                t("tabs.users")
              )}
            </h2>
            {activePermission && (
              <p className="mt-0.5 text-caption text-text-muted">
                {t("permissionUsers.summaryCount", {
                  count: usersQuery.data ? totalUsers : activePermission.userCount,
                })}
              </p>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="secondary"
              loading={exportMutation.isPending}
              disabled={!activePermission}
              onClick={() => exportMutation.mutate()}
            >
              <Download aria-hidden="true" className="size-4" />
              <span>{t("permissionUsers.exportExcel")}</span>
            </Button>
            {canAssign && activePermission && onAssignMany && (
              <Button
                variant="primary"
                onClick={() => onAssignMany(activePermission.code)}
              >
                {t("permissionUsers.assignMany")}
              </Button>
            )}
          </div>
        </div>

        <div className="flex flex-wrap items-end gap-3 border-b border-border px-4 py-3">
          <label className="flex min-w-[200px] flex-[2_1_220px] flex-col gap-1.5 text-caption text-text-muted">
            <span>{t("permissionUsers.filterSearchLabel")}</span>
            <Input
              type="search"
              value={accountSearch}
              leadingIcon={<Search />}
              placeholder={t("permissionUsers.filterAccountPlaceholder")}
              onChange={(event) => {
                setAccountSearch(event.target.value);
                setPage(1);
              }}
            />
          </label>

          <div className="flex min-w-[150px] flex-[1_1_150px] flex-col gap-1.5">
            <span className="text-caption text-text-muted">
              {t("permissionUsers.filterSourceLabel")}
            </span>
            <Select
              aria-label={t("permissionUsers.filterSourceLabel")}
              value={sourceFilter}
              options={sourceOptions}
              onValueChange={(next) => {
                setSourceFilter(next as "ALL" | PermissionSourceType);
                setPage(1);
              }}
            />
          </div>

          <div className="flex min-w-[150px] flex-[1_1_150px] flex-col gap-1.5">
            <span className="text-caption text-text-muted">
              {t("permissionUsers.filterStatusLabel")}
            </span>
            <Select
              aria-label={t("permissionUsers.filterStatusLabel")}
              value={statusFilter}
              options={statusOptions}
              onValueChange={(next) => {
                setStatusFilter(next as "ALL" | UserStatus);
                setPage(1);
              }}
            />
          </div>
        </div>

        <Table
          data={users}
          columns={columns}
          getRowId={(row) => row.userId}
          loading={permissionsQuery.isPending || usersQuery.isPending}
          refreshing={usersQuery.isPlaceholderData}
          aria-label={t("tabs.users")}
          error={
            usersQuery.isError ? (
              <EmptyState
                icon={<TriangleAlert />}
                title={t("permissionUsers.errorTitle")}
                description={t("permissionUsers.errorDescription")}
                action={
                  <Button variant="secondary" onClick={() => usersQuery.refetch()}>
                    {t("actions.retry")}
                  </Button>
                }
              />
            ) : undefined
          }
          empty={
            <EmptyState
              icon={<Users />}
              title={t("permissionUsers.emptyTitle")}
              description={t("permissionUsers.emptyDescription")}
            />
          }
        />

        {totalUsers > 0 && (
          <div className="border-t border-border px-4 py-3">
            <Pagination
              page={page}
              pageSize={pageSize}
              total={totalUsers}
              onPageChange={setPage}
              onPageSizeChange={(nextSize) => {
                setPageSize(nextSize);
                setPage(1);
              }}
              labels={{
                nav: t("rolesTab.pagination.nav"),
                previous: t("rolesTab.pagination.previous"),
                next: t("rolesTab.pagination.next"),
                rowsPerPage: t("rolesTab.pagination.rowsPerPage"),
                page: (num) => t("rolesTab.pagination.page", { page: num }),
                range: ({ from, to, total: rangeTotal }) =>
                  t("rolesTab.pagination.range", { from, to, total: rangeTotal }),
              }}
            />
          </div>
        )}

        <div className="border-t border-border px-4 py-3 text-caption text-text-muted">
          {t("permissionUsers.footnote")}
        </div>
      </Card>
      <ConfirmDialog
        open={Boolean(revokeTarget)}
        onOpenChange={(open) => {
          if (!open) setRevokeTarget(null);
        }}
        tone="danger"
        loading={revokeMutation.isPending}
        title={t("permissionUsers.revokeConfirmTitle")}
        description={
          revokeTarget
            ? t("permissionUsers.revokeConfirmDescription", {
                permission: activePermissionShortName,
                username: revokeTarget.username,
              })
            : ""
        }
        confirmLabel={t("permissionUsers.revokeConfirmAction")}
        cancelLabel={t("actions.cancel")}
        closeLabel={t("actions.close")}
        onConfirm={() => {
          if (revokeTarget) {
            revokeMutation.mutate(revokeTarget);
          }
        }}
      />
    </div>
  );
}
