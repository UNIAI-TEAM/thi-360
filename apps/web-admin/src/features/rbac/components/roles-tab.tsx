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
} from "@thi360/ui";
import { Eye, Pencil, Search, Shield, Trash2, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useMemo, useRef, useState } from "react";
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

export interface RolesTabProps {
  createRequestNonce?: number;
  onCreateRequestConsumed?: (nonce: number) => void;
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
  createRequestNonce = 0,
  onCreateRequestConsumed,
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
  const lastConsumedNonceRef = useRef(0);

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

  useEffect(() => {
    if (!canModify || createRequestNonce <= 0) return;
    if (lastConsumedNonceRef.current >= createRequestNonce) return;
    lastConsumedNonceRef.current = createRequestNonce;
    setDialogState({
      open: true,
      mode: "create",
    });
    onCreateRequestConsumed?.(createRequestNonce);
  }, [canModify, createRequestNonce, onCreateRequestConsumed]);

  function closeDialog() {
    setDialogState({
      open: false,
      mode: "create",
    });
  }

  const columns = useMemo<ColumnDef<RoleSummaryResponse>[]>(() => {
    const baseColumns: ColumnDef<RoleSummaryResponse>[] = [
      {
        id: "role",
        header: t("rolesTab.columns.role"),
        enableSorting: false,
        cell: ({ row }) => {
          const r = row.original;
          const displayName = resolveRoleName(rbacT, r.code, r.name, r.system);
          return (
            <div className="py-2">
              <div className="font-semibold text-text">{displayName}</div>
              <div className="text-caption text-text-muted">{r.code}</div>
            </div>
          );
        },
      },
      {
        id: "access",
        header: t("rolesTab.columns.scope"),
        enableSorting: false,
        cell: ({ row }) => {
          const scope = row.original.access;
          return (
            <StatusChip tone={scope === "MANAGE" ? "info" : "warning"}>
              {t(`access.${scope}`)}
            </StatusChip>
          );
        },
      },
      {
        id: "permissionCount",
        header: t("rolesTab.columns.permissions"),
        enableSorting: false,
        cell: ({ row }) => row.original.permissionCount,
      },
      {
        id: "userCount",
        header: t("rolesTab.columns.users"),
        enableSorting: false,
        cell: ({ row }) => row.original.userCount,
      },
      {
        id: "kind",
        header: t("rolesTab.columns.kind"),
        enableSorting: false,
        cell: ({ row }) =>
          row.original.system ? (
            <StatusChip tone="neutral">{t("rolesTab.kindSystem")}</StatusChip>
          ) : (
            <Chip variant="tag">{t("rolesTab.kindCustom")}</Chip>
          ),
      },
      {
        id: "status",
        header: t("rolesTab.columns.status"),
        enableSorting: false,
        cell: ({ row }) => (
          <StatusChip tone={row.original.status === "ACTIVE" ? "success" : "neutral"}>
            {row.original.status === "ACTIVE"
              ? t("rolesTab.statusActive")
              : t("rolesTab.statusInactive")}
          </StatusChip>
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
                    "focus-visible:ring-3 focus-visible:ring-primary-600/30 focus-visible:outline-none",
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

        <Table
          aria-label={t("tabs.vai-tro")}
          columns={columns}
          data={items}
          getRowId={(row) => row.id}
          loading={rolesQuery.isPending}
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
                    onClick={() =>
                      setDialogState({
                        open: true,
                        mode: "create",
                      })
                    }
                  >
                    {t("rolesTab.createButton")}
                  </Button>
                ) : undefined
              }
            />
          }
        />

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
        open={dialogState.open}
        onOpenChange={(nextOpen) => {
          if (!nextOpen) closeDialog();
          else setDialogState((prev) => ({ ...prev, open: true }));
        }}
        mode={dialogState.mode}
        roleId={dialogState.roleId}
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
