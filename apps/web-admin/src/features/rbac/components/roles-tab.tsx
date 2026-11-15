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
  StatusChip,
  Table,
  focusRing,
} from "@thi360/ui";
import { Eye, Pencil, Search, Shield, Trash2, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
import { usePermissions } from "@/features/session/session-provider";
import {
  deleteRole as defaultDeleteRole,
  listPermissions as defaultListPermissions,
  listRoles as defaultListRoles,
  rbacKeys,
  type ListRolesParams,
} from "../api";
import {
  KNOWN_OBJECT_CODES,
  KNOWN_PERMISSION_CODES,
  resolveObjectName,
  resolvePermissionName,
  resolveRbacErrorMessage,
  resolveRoleName,
  type RbacTranslator,
} from "../messages";
import type {
  AccessScope,
  PageResponseRoleSummaryResponse,
  PermissionObjectGroup,
  PermissionResponse,
  RoleSummaryResponse,
} from "../types";
import { groupPermissionsByObject } from "./object-permission-picker";
import { RoleDialog, type RoleDialogProps } from "./role-dialog";

/**
 * Bảng vai trò ở thẻ hẹp (dưới 40rem: điện thoại, tablet dọc): chỉ còn cột Vai trò và Thao tác; phạm vi, loại,
 * trạng thái, số quyền và số tài khoản hiện thành dòng phụ dưới tên (`data-col-summary`). Cột phụ được nhận ra qua
 * `data-col` trên nội dung do màn này vẽ, cùng cách với danh sách tài khoản (`users-list-view.tsx`).
 */
const COMPACT_ROLE_COLUMNS = [
  "@container",
  "@max-[40rem]:[&_:is(th,td):has([data-col=secondary])]:hidden",
].join(" ");

export interface RolesTabProps {
  createOpen?: boolean;
  onCreateOpenChange?: (open: boolean) => void;
  listRoles?: (params?: ListRolesParams) => Promise<PageResponseRoleSummaryResponse>;
  listPermissions?: () => Promise<PermissionResponse[]>;
  deleteRole?: (id: string) => Promise<void>;
  roleDialogApi?: Pick<
    RoleDialogProps,
    "getRole" | "createRole" | "updateRole" | "updateRolePermissions"
  >;
}

function buildFallbackPermissions(t: RbacTranslator): PermissionResponse[] {
  return KNOWN_PERMISSION_CODES.map((code, index) => {
    const objectCode = code.split(".")[0] ?? "USER";
    const groupCode = (KNOWN_OBJECT_CODES as readonly string[]).includes(objectCode)
      ? objectCode
      : "USER";
    return {
      id: code,
      code,
      bitIndex: index,
      groupCode,
      groupName: resolveObjectName(t, groupCode),
      name: resolvePermissionName(t, code),
      scope: "MANAGE",
      roleCount: 0,
      userCount: 0,
    };
  });
}

export function RolesTab({
  createOpen = false,
  onCreateOpenChange,
  listRoles = defaultListRoles,
  listPermissions = defaultListPermissions,
  deleteRole = defaultDeleteRole,
  roleDialogApi,
}: RolesTabProps) {
  const t = useTranslations("rbac");
  const rbacT: RbacTranslator = (key, values) => t(key, values);
  const { can } = usePermissions();
  const queryClient = useQueryClient();

  const canModify = can("ROLE.MODIFY");
  const canViewPermissions = can("PERMISSION.VIEW");

  const [search, setSearch] = useState("");
  const [accessFilter, setAccessFilter] = useState<"ALL" | AccessScope>("ALL");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const [dialogState, setDialogState] = useState<{
    open: boolean;
    mode: "create" | "edit" | "view";
    roleId?: string;
  }>({ open: false, mode: "create" });
  const [deleteTarget, setDeleteTarget] = useState<RoleSummaryResponse | null>(null);

  const rolesParams: ListRolesParams = useMemo(
    () => ({
      ...(search.trim() ? { q: search.trim() } : {}),
      ...(accessFilter !== "ALL" ? { access: accessFilter } : {}),
      page: page - 1,
      size: pageSize,
    }),
    [search, accessFilter, page, pageSize],
  );

  const rolesQuery = useQuery({
    queryKey: rbacKeys.roles(rolesParams),
    queryFn: () => listRoles(rolesParams),
    placeholderData: keepPreviousWhileSameScope(rbacKeys.roles(rolesParams)),
  });

  const permissionsQuery = useQuery({
    queryKey: rbacKeys.permissions(),
    queryFn: () => listPermissions(),
    enabled: canViewPermissions,
  });

  const permissionGroups = useMemo<PermissionObjectGroup[]>(() => {
    const items =
      canViewPermissions && permissionsQuery.data
        ? permissionsQuery.data
        : buildFallbackPermissions(rbacT);
    return groupPermissionsByObject(items, rbacT);
  }, [canViewPermissions, permissionsQuery.data, t]); // eslint-disable-line react-hooks/exhaustive-deps

  const deleteMutation = useMutation({
    mutationFn: (roleId: string) => deleteRole(roleId),
    onSuccess: async () => {
      setDeleteTarget(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["rbac", "roles"] }),
        queryClient.invalidateQueries({ queryKey: ["rbac", "permissions"] }),
        queryClient.invalidateQueries({ queryKey: ["rbac", "permission-users"] }),
        queryClient.invalidateQueries({ queryKey: ["rbac", "users"] }),
        queryClient.invalidateQueries({ queryKey: ["rbac", "user-roles"] }),
        queryClient.invalidateQueries({ queryKey: ["rbac", "user-effective-permissions"] }),
      ]);
      notify.success(t("rolesTab.deleteSuccess"));
    },
    onError: (error) => {
      setDeleteTarget(null);
      notify.error(resolveRbacErrorMessage(rbacT, error, "rolesTab.deleteFailed"));
    },
  });

  const effectiveDialogOpen = dialogState.open || (canModify && createOpen);
  const effectiveDialogMode = dialogState.open ? dialogState.mode : "create";
  const effectiveDialogRoleId = dialogState.open ? dialogState.roleId : undefined;

  function closeDialog() {
    setDialogState({
      open: false,
      mode: "create",
    });
    onCreateOpenChange?.(false);
  }
  const columns = useMemo<ColumnDef<RoleSummaryResponse>[]>(() => {
    const accessChip = (r: RoleSummaryResponse) => (
      <StatusChip tone={r.access === "MANAGE" ? "info" : "warning"}>
        {t(`access.${r.access}`)}
      </StatusChip>
    );
    const kindChip = (r: RoleSummaryResponse) =>
      r.system ? (
        <StatusChip tone="neutral">{t("rolesTab.kindSystem")}</StatusChip>
      ) : (
        <Chip variant="tag">{t("rolesTab.kindCustom")}</Chip>
      );

    const baseColumns: ColumnDef<RoleSummaryResponse>[] = [
      {
        id: "role",
        header: t("rolesTab.columns.role"),
        enableSorting: false,
        cell: ({ row }) => {
          const r = row.original;
          const displayName = resolveRoleName(rbacT, r.code, r.name, r.system);
          return (
            <div className="min-w-36 py-2">
              <div className="font-semibold wrap-break-word text-text">{displayName}</div>
              <div className="text-caption wrap-anywhere text-text-muted">{r.code}</div>
              {/*
                Thẻ hẹp (dưới 40rem): các cột Phạm vi, Số quyền, Số người, Loại, Trạng thái ẩn đi và hiện ở đây dưới
                tên vai trò. Chip trạng thái chỉ hiện khi khác Hoạt động.
              */}
              <div data-col-summary className="mt-1.5 flex flex-col gap-1 @min-[40rem]:hidden">
                <div className="flex flex-wrap gap-1.5">
                  {accessChip(r)}
                  {kindChip(r)}
                  {r.status !== "ACTIVE" && (
                    <StatusChip tone="neutral">{t("rolesTab.statusInactive")}</StatusChip>
                  )}
                </div>
                <div className="text-caption text-text-secondary tabular-nums">
                  {t("rolesTab.countsSummary", {
                    permissions: r.permissionCount,
                    users: r.userCount,
                  })}
                </div>
              </div>
            </div>
          );
        },
      },
      {
        id: "access",
        header: () => <span data-col="secondary">{t("rolesTab.columns.scope")}</span>,
        enableSorting: false,
        cell: ({ row }) => <span data-col="secondary">{accessChip(row.original)}</span>,
      },
      {
        id: "permissionCount",
        header: () => <span data-col="secondary">{t("rolesTab.columns.permissions")}</span>,
        enableSorting: false,
        cell: ({ row }) => <span data-col="secondary">{row.original.permissionCount}</span>,
      },
      {
        id: "userCount",
        header: () => <span data-col="secondary">{t("rolesTab.columns.users")}</span>,
        enableSorting: false,
        cell: ({ row }) => <span data-col="secondary">{row.original.userCount}</span>,
      },
      {
        id: "kind",
        header: () => <span data-col="secondary">{t("rolesTab.columns.kind")}</span>,
        enableSorting: false,
        cell: ({ row }) => <span data-col="secondary">{kindChip(row.original)}</span>,
      },
      {
        id: "status",
        header: () => <span data-col="secondary">{t("rolesTab.columns.status")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="secondary">
            <StatusChip tone={row.original.status === "ACTIVE" ? "success" : "neutral"}>
              {row.original.status === "ACTIVE"
                ? t("rolesTab.statusActive")
                : t("rolesTab.statusInactive")}
            </StatusChip>
          </span>
        ),
      },
      {
        id: "actions",
        header: () => <span className="block text-right">{t("rolesTab.columns.actions")}</span>,
        enableSorting: false,
        cell: ({ row }) => {
          const r = row.original;
          const displayName = resolveRoleName(rbacT, r.code, r.name, r.system);

          if (!canModify) {
            return (
              <div className="flex justify-end">
                <IconButton
                  icon={<Eye />}
                  aria-label={t("rolesTab.viewAria", { name: displayName })}
                  tooltip={t("rolesTab.viewTooltip")}
                  onClick={() =>
                    setDialogState({
                      open: true,
                      mode: "view",
                      roleId: r.id,
                    })
                  }
                />
              </div>
            );
          }

          const deleteLocked = r.system || r.userCount > 0;
          const deleteTooltip = r.system
            ? t("rolesTab.deleteSystemTooltip")
            : r.userCount > 0
              ? t("rolesTab.deleteInUseTooltip")
              : t("rolesTab.deleteTooltip");

          return (
            <div className="flex items-center justify-end gap-1">
              <IconButton
                icon={<Pencil />}
                aria-label={t("rolesTab.editAria", { name: displayName })}
                tooltip={t("rolesTab.editTooltip")}
                onClick={() =>
                  setDialogState({
                    open: true,
                    mode: "edit",
                    roleId: r.id,
                  })
                }
              />
              <IconButton
                icon={<Trash2 />}
                tone="danger"
                disabled={deleteLocked}
                aria-label={t("rolesTab.deleteAria", { name: displayName })}
                tooltip={deleteTooltip}
                onClick={() => setDeleteTarget(r)}
              />
            </div>
          );
        },
      },
    ];

    return baseColumns;
  }, [canModify, t]); // eslint-disable-line react-hooks/exhaustive-deps

  const items = rolesQuery.data?.items ?? [];
  const total = rolesQuery.data?.totalItems ?? 0;

  return (
    <div className="flex flex-col gap-4">
      <Card padding="md" className="flex flex-wrap items-end gap-3">
        <label className="flex min-w-[220px] flex-[2_1_240px] flex-col gap-1.5 text-caption text-text-muted">
          <span>{t("rolesTab.filterSearchLabel")}</span>
          <Input
            type="search"
            value={search}
            leadingIcon={<Search />}
            placeholder={t("rolesTab.filterSearchPlaceholder")}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(1);
            }}
          />
        </label>

        <fieldset className="flex flex-col gap-1.5">
          <legend className="text-caption text-text-muted">
            {t("rolesTab.filterAccessLabel")}
          </legend>
          <div className="inline-flex h-10 items-center gap-1 rounded-lg bg-subtle p-1">
            {(
              [
                { value: "ALL", label: t("rolesTab.filterAccessAll") },
                { value: "MANAGE", label: t("rolesTab.filterAccessManage") },
                { value: "EXAM", label: t("rolesTab.filterAccessExam") },
              ] as const
            ).map((option) => {
              const active = accessFilter === option.value;
              return (
                <button
                  key={option.value}
                  type="button"
                  aria-pressed={active}
                  onClick={() => {
                    setAccessFilter(option.value);
                    setPage(1);
                  }}
                  className={cn(
                    "inline-flex h-8 items-center justify-center rounded-md px-3.5 text-label transition-colors duration-150 motion-reduce:transition-none",
                    focusRing,
                    active
                      ? "bg-surface font-semibold text-primary-text shadow-xs"
                      : "font-medium text-text-secondary hover:bg-surface/60",
                  )}
                >
                  {option.label}
                </button>
              );
            })}
          </div>
        </fieldset>
      </Card>

      <Card padding="none" className="overflow-hidden">
        <div className="border-b border-border px-4 py-3 text-body font-semibold text-text tabular-nums">
          {t("rolesTab.totalCount", { count: total })}
        </div>

        <div className={COMPACT_ROLE_COLUMNS}>
          <Table
            aria-label={t("tabs.roles")}
            columns={columns}
            data={items}
            getRowId={(row) => row.id}
            loading={rolesQuery.isPending}
            refreshing={rolesQuery.isPlaceholderData}
            error={
              rolesQuery.isError ? (
                <EmptyState
                  icon={<TriangleAlert />}
                  title={t("rolesTab.errorTitle")}
                  description={t("rolesTab.errorDescription")}
                  action={
                    <Button onClick={() => void rolesQuery.refetch()}>
                      {t("actions.retry")}
                    </Button>
                  }
                />
              ) : undefined
            }
            empty={
              <EmptyState
                icon={<Shield />}
                title={t("rolesTab.emptyTitle")}
                description={t("rolesTab.emptyDescription")}
                action={
                  canModify ? (
                    <Button
                      onClick={() => {
                        if (onCreateOpenChange) {
                          onCreateOpenChange(true);
                        } else {
                          setDialogState({
                            open: true,
                            mode: "create",
                          });
                        }
                      }}
                    >
                      {t("rolesTab.createButton")}
                    </Button>
                  ) : undefined
                }
              />
            }
          />
        </div>

        {total > 0 && (
          <div className="border-t border-border px-4 py-3">
            <Pagination
              page={page}
              pageSize={pageSize}
              total={total}
              onPageChange={setPage}
              pageSizeOptions={[10, 20, 50]}
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
                range: ({ from, to, total: count }) =>
                  t("rolesTab.pagination.range", { from, to, total: count }),
              }}
            />
          </div>
        )}

        <div className="border-t border-border px-4 py-3 text-caption text-text-muted">
          {t("rolesTab.footnote")}
        </div>
      </Card>

      <RoleDialog
        open={effectiveDialogOpen}
        onOpenChange={(nextOpen) => {
          if (!nextOpen) closeDialog();
        }}
        mode={effectiveDialogMode}
        roleId={effectiveDialogRoleId}
        permissionGroups={permissionGroups}
        {...roleDialogApi}
      />

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
        title={t("rolesTab.deleteConfirmTitle")}
        description={
          deleteTarget
            ? t("rolesTab.deleteConfirmDescription", {
                name: resolveRoleName(
                  rbacT,
                  deleteTarget.code,
                  deleteTarget.name,
                  deleteTarget.system,
                ),
                code: deleteTarget.code,
              })
            : ""
        }
        confirmLabel={t("rolesTab.deleteConfirmAction")}
        cancelLabel={t("actions.cancel")}
        closeLabel={t("actions.close")}
        loading={deleteMutation.isPending}
        onConfirm={() => {
          if (deleteTarget) deleteMutation.mutate(deleteTarget.id);
        }}
      />
    </div>
  );
}
