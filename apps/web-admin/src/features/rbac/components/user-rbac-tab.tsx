"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ColumnDef } from "@tanstack/react-table";
import {
  Button,
  Card,
  Checkbox,
  Chip,
  ConfirmDialog,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  EmptyState,
  IconButton,
  Input,
  notify,
  Skeleton,
  StatusChip,
  Table,
  Textarea,
  Tooltip,
} from "@thi360/ui";
import { Lock, Search, Shield, TriangleAlert, X } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { usePermissions } from "@/features/session/session-provider";
import {
  getEffectivePermissions as defaultGetEffectivePermissions,
  getUserPermissions as defaultGetUserPermissions,
  getUserRoles as defaultGetUserRoles,
  listAllRoles,
  listPermissions as defaultListPermissions,
  listRoles as defaultListRoles,
  rbacKeys,
  updateUserPermissions as defaultUpdateUserPermissions,
  updateUserRoles as defaultUpdateUserRoles,
  type ListRolesParams,
} from "../api";
import {
  resolveObjectName,
  resolvePermissionName,
  resolvePermissionShortName,
  resolveRbacErrorMessage,
  resolveRoleName,
  type RbacTranslator,
} from "../messages";
import type {
  EffectivePermissionItemResponse,
  EffectivePermissionsResponse,
  PageResponseRoleSummaryResponse,
  PermissionResponse,
  UpdateUserPermissionsRequest,
  UpdateUserRolesRequest,
  UserDirectPermissionItemResponse,
  UserDirectPermissionsResponse,
  UserRoleItemResponse,
  UserRolesResponse,
} from "../types";
import {
  ObjectPermissionPicker,
  groupPermissionsByObject,
} from "./object-permission-picker";

export interface UserRbacTabProps {
  userId: string;
  readOnly?: boolean;
  username?: string;
  fullName?: string;
  getUserRoles?: (userId: string) => Promise<UserRolesResponse>;
  updateUserRoles?: (userId: string, payload: UpdateUserRolesRequest) => Promise<UserRolesResponse>;
  getUserPermissions?: (userId: string) => Promise<UserDirectPermissionsResponse>;
  updateUserPermissions?: (
    userId: string,
    payload: UpdateUserPermissionsRequest,
  ) => Promise<UserDirectPermissionsResponse>;
  getEffectivePermissions?: (userId: string) => Promise<EffectivePermissionsResponse>;
  listRoles?: (params?: ListRolesParams) => Promise<PageResponseRoleSummaryResponse>;
  listPermissions?: () => Promise<PermissionResponse[]>;
}

export function UserRbacTab({
  userId,
  readOnly = false,
  username,
  fullName,
  getUserRoles = defaultGetUserRoles,
  updateUserRoles = defaultUpdateUserRoles,
  getUserPermissions = defaultGetUserPermissions,
  updateUserPermissions = defaultUpdateUserPermissions,
  getEffectivePermissions = defaultGetEffectivePermissions,
  listRoles = defaultListRoles,
  listPermissions = defaultListPermissions,
}: UserRbacTabProps) {
  const t = useTranslations("rbac");
  const commonT = useTranslations("common");
  const formatter = useFormatter();
  const rbacT: RbacTranslator = (key, values) => t(key, values);
  const { can, isLoading: sessionLoading } = usePermissions();
  const queryClient = useQueryClient();

  const canEdit = !readOnly && !sessionLoading && can("USER.ASSIGN");
  const canFetchPermissions = canEdit && can("PERMISSION.VIEW");
  const canFetchRoles = canEdit && can("ROLE.VIEW");

  const [rolesDialogOpen, setRolesDialogOpen] = useState(false);
  const [draftRoleIds, setDraftRoleIds] = useState<string[]>([]);
  const [rolesDialogError, setRolesDialogError] = useState<string | null>(null);
  const [roleSearch, setRoleSearch] = useState("");

  const [directDialogOpen, setDirectDialogOpen] = useState(false);
  const [draftDirectCodes, setDraftDirectCodes] = useState<string[]>([]);
  const [directReason, setDirectReason] = useState("");
  const [directDialogError, setDirectDialogError] = useState<string | null>(null);

  const [removeRoleTarget, setRemoveRoleTarget] = useState<UserRoleItemResponse | null>(null);
  const [removeDirectTarget, setRemoveDirectTarget] =
    useState<UserDirectPermissionItemResponse | null>(null);

  const userRolesQuery = useQuery({
    queryKey: rbacKeys.userRoles(userId),
    queryFn: () => getUserRoles(userId),
    enabled: Boolean(userId),
  });

  const userPermsQuery = useQuery({
    queryKey: rbacKeys.userDirectPermissions(userId),
    queryFn: () => getUserPermissions(userId),
    enabled: Boolean(userId),
  });

  const effectiveQuery = useQuery({
    queryKey: rbacKeys.userEffectivePermissions(userId),
    queryFn: () => getEffectivePermissions(userId),
    enabled: Boolean(userId),
  });

  const allRolesQuery = useQuery({
    queryKey: rbacKeys.allRoles(),
    queryFn: () => listAllRoles(listRoles),
    enabled: canFetchRoles && rolesDialogOpen,
  });

  const allPermissionsQuery = useQuery({
    queryKey: rbacKeys.permissions(),
    queryFn: () => listPermissions(),
    enabled: canFetchPermissions && directDialogOpen,
  });

  const filteredAllRoles = useMemo(() => {
    const items = allRolesQuery.data ?? [];
    const q = roleSearch.trim().toLowerCase();
    if (!q) return items;
    return items.filter((role) => {
      const localized = resolveRoleName(rbacT, role.code, role.name, role.system).toLowerCase();
      return (
        localized.includes(q) ||
        role.name.toLowerCase().includes(q) ||
        role.code.toLowerCase().includes(q)
      );
    });
  }, [allRolesQuery.data, roleSearch, t]); // eslint-disable-line react-hooks/exhaustive-deps

  const permissionGroups = useMemo(
    () => groupPermissionsByObject(allPermissionsQuery.data ?? [], rbacT),
    [allPermissionsQuery.data, t], // eslint-disable-line react-hooks/exhaustive-deps
  );

  const lockedObjects = useMemo(() => {
    const effective = effectiveQuery.data?.permissions ?? [];
    const rolePermByCode = new Map<string, string[]>();
    for (const item of effective) {
      const roleNames = item.sources
        .filter((s) => s.type === "ROLE")
        .map((s) => resolveRoleName(rbacT, s.roleCode ?? "", s.roleName ?? ""));
      if (roleNames.length > 0) {
        rolePermByCode.set(item.code, roleNames);
      }
    }

    const locked: Record<string, string> = {};
    for (const group of permissionGroups) {
      const allGroupCodes = [
        ...(group.viewPermission ? [group.viewPermission.code] : []),
        ...(group.modifyPermission ? [group.modifyPermission.code] : []),
        ...group.extraPermissions.map((e) => e.code),
      ];
      if (allGroupCodes.length === 0) continue;
      if (allGroupCodes.every((code) => rolePermByCode.has(code))) {
        const heldRoles = Array.from(
          new Set(allGroupCodes.flatMap((code) => rolePermByCode.get(code) ?? [])),
        );
        locked[group.objectCode] = t("userRbac.heldByRoleHint", {
          roles: heldRoles.join(", "),
        });
      }
    }
    return locked;
  }, [effectiveQuery.data, permissionGroups, t]); // eslint-disable-line react-hooks/exhaustive-deps

  const inheritedRoleCodes = useMemo(() => {
    const effective = effectiveQuery.data?.permissions ?? [];
    return effective
      .filter((item) => item.sources.some((s) => s.type === "ROLE"))
      .map((item) => item.code);
  }, [effectiveQuery.data]);

  async function invalidateUserAuthz() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: rbacKeys.userRoles(userId) }),
      queryClient.invalidateQueries({ queryKey: rbacKeys.userDirectPermissions(userId) }),
      queryClient.invalidateQueries({ queryKey: rbacKeys.userEffectivePermissions(userId) }),
      queryClient.invalidateQueries({ queryKey: ["rbac", "roles"] }),
      queryClient.invalidateQueries({ queryKey: ["rbac", "permissions"] }),
      queryClient.invalidateQueries({ queryKey: ["rbac", "permission-users"] }),
    ]);
  }

  const updateRolesMutation = useMutation({
    mutationFn: (roleIds: string[]) => updateUserRoles(userId, { roleIds }),
    onSuccess: async () => {
      setRolesDialogOpen(false);
      setRemoveRoleTarget(null);
      setRolesDialogError(null);
      await invalidateUserAuthz();
      notify.success(t("userRbac.rolesSaveSuccess"));
    },
    onError: (error) => {
      const message = resolveRbacErrorMessage(rbacT, error, "rolesTab.deleteFailed");
      if (rolesDialogOpen) {
        setRolesDialogError(message);
      } else {
        setRemoveRoleTarget(null);
        notify.error(message);
      }
    },
  });

  const updateDirectMutation = useMutation({
    mutationFn: (payload: UpdateUserPermissionsRequest) => updateUserPermissions(userId, payload),
    onSuccess: async (_data, variables) => {
      const wasRemoval = Boolean(removeDirectTarget);
      setDirectDialogOpen(false);
      setRemoveDirectTarget(null);
      setDirectDialogError(null);
      await invalidateUserAuthz();
      notify.success(
        wasRemoval || variables.permissionCodes.length < (userPermsQuery.data?.permissions.length ?? 0)
          ? t("userRbac.removeDirectSuccess")
          : t("userRbac.directSaveSuccess"),
      );
    },
    onError: (error) => {
      const message = resolveRbacErrorMessage(rbacT, error, "permissionUsers.revokeError");
      if (directDialogOpen) {
        setDirectDialogError(message);
      } else {
        setRemoveDirectTarget(null);
        notify.error(message);
      }
    },
  });

  const effectiveColumns = useMemo<ColumnDef<EffectivePermissionItemResponse>[]>(() => {
    return [
      {
        id: "object",
        header: t("userRbac.effectiveColumns.object"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="text-text-secondary">
            {resolveObjectName(rbacT, row.original.groupCode, row.original.groupName)}
          </span>
        ),
      },
      {
        id: "level",
        header: t("userRbac.effectiveColumns.level"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="font-medium text-text">
            {resolvePermissionName(rbacT, row.original.code, row.original.name)}
          </span>
        ),
      },
      {
        id: "code",
        header: t("userRbac.effectiveColumns.code"),
        enableSorting: false,
        cell: ({ row }) => (
          <code className="text-field-label text-text-muted">{row.original.code}</code>
        ),
      },
      {
        id: "source",
        header: t("userRbac.effectiveColumns.source"),
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex flex-wrap gap-1.5">
            {row.original.sources.map((src, idx) =>
              src.type === "ROLE" ? (
                <Chip key={`${src.roleId ?? idx}`} variant="tag">
                  {t("userRbac.effectiveSourceRole", {
                    name: resolveRoleName(rbacT, src.roleCode ?? "", src.roleName ?? ""),
                  })}
                </Chip>
              ) : (
                <StatusChip key={`direct-${idx}`} tone="warning">
                  {t("userRbac.effectiveSourceDirect")}
                </StatusChip>
              ),
            )}
          </div>
        ),
      },
    ];
  }, [t]); // eslint-disable-line react-hooks/exhaustive-deps

  const isLoading =
    userRolesQuery.isPending || userPermsQuery.isPending || effectiveQuery.isPending;
  const isError =
    userRolesQuery.isError || userPermsQuery.isError || effectiveQuery.isError;

  if (isLoading) {
    return (
      <div role="status" aria-label={commonT("loading")} className="flex flex-col gap-4">
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <Skeleton className="h-48 w-full" />
          <Skeleton className="h-48 w-full" />
        </div>
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (isError) {
    return (
      <Card padding="md">
        <EmptyState
          icon={<TriangleAlert />}
          title={t("userRbac.errorTitle")}
          description={t("userRbac.errorDescription")}
          action={
            <Button
              variant="secondary"
              onClick={() => {
                userRolesQuery.refetch();
                userPermsQuery.refetch();
                effectiveQuery.refetch();
              }}
            >
              {t("actions.retry")}
            </Button>
          }
        />
      </Card>
    );
  }

  const assignedRoles = userRolesQuery.data?.roles ?? [];
  const directPermissions = userPermsQuery.data?.permissions ?? [];
  const effectivePermissions = effectiveQuery.data?.permissions ?? [];
  const hasManageRole =
    userPermsQuery.data?.hasManageRole ?? userRolesQuery.data?.access === "MANAGE";
  const hasInactiveDirect = directPermissions.some((item) => !item.effective);

  function openAssignRolesDialog() {
    if (!canFetchRoles) return;
    setDraftRoleIds(assignedRoles.map((r) => r.roleId));
    setRoleSearch("");
    setRolesDialogError(null);
    setRolesDialogOpen(true);
  }

  function openAddDirectDialog() {
    if (!canFetchPermissions) return;
    setDraftDirectCodes(directPermissions.map((p) => p.permissionCode));
    setDirectReason("");
    setDirectDialogError(null);
    setDirectDialogOpen(true);
  }

  function handleSaveDirectPermissions(event: React.FormEvent) {
    event.preventDefault();
    const existingSet = new Set(directPermissions.map((p) => p.permissionCode));
    const hasAddedNew = draftDirectCodes.some((code) => !existingSet.has(code));
    if (hasAddedNew && directReason.trim().length === 0) {
      setDirectDialogError(t("userRbac.directReasonRequired"));
      return;
    }
    setDirectDialogError(null);
    updateDirectMutation.mutate({
      permissionCodes: draftDirectCodes,
      ...(directReason.trim() ? { reason: directReason.trim() } : {}),
    });
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card padding="md" className="flex flex-col gap-3.5">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-title font-semibold text-text">{t("userRbac.rolesSectionTitle")}</h2>
            {canEdit && (
              <Button
                size="sm"
                variant="soft"
                disabled={!canFetchRoles}
                onClick={openAssignRolesDialog}
              >
                {t("userRbac.assignRolesButton")}
              </Button>
            )}
          </div>
          {canEdit && !sessionLoading && !canFetchRoles && (
            <p className="text-caption text-text-muted">{t("userRbac.missingRoleViewNote")}</p>
          )}

          {assignedRoles.length === 0 ? (
            <p className="text-body text-text-muted">{t("userRbac.rolesEmpty")}</p>
          ) : (
            <div className="flex flex-col gap-2.5">
              {assignedRoles.map((role) => {
                const displayName = resolveRoleName(
                  rbacT,
                  role.roleCode,
                  role.roleName,
                  role.system,
                );
                return (
                  <div
                    key={role.roleId}
                    className="flex items-center justify-between gap-3 rounded-md border border-border bg-surface p-3"
                  >
                    <div className="min-w-0">
                      <div className="font-semibold text-text">{displayName}</div>
                      <div className="text-caption text-text-muted">
                        {role.roleCode} ·{" "}
                        {role.system
                          ? t("userRbac.roleSystemKind")
                          : t("userRbac.roleCustomKind")}
                      </div>
                    </div>
                    <div className="flex shrink-0 items-center gap-1.5">
                      <StatusChip tone={role.access === "MANAGE" ? "info" : "warning"}>
                        {t(`access.${role.access}`)}
                      </StatusChip>
                      {canEdit && (
                        <IconButton
                          icon={<X />}
                          tone="danger"
                          aria-label={t("userRbac.removeRoleAria", { name: displayName })}
                          tooltip={t("userRbac.removeRoleConfirmAction")}
                          onClick={() => setRemoveRoleTarget(role)}
                        />
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          <p className="mt-auto text-caption text-text-muted">
            {hasManageRole ? t("userRbac.rolesFootnote") : t("userRbac.rolesExamFootnote")}
          </p>
        </Card>

        <Card padding="md" className="flex flex-col gap-3.5">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-title font-semibold text-text">
              {t("userRbac.directSectionTitle")}
            </h2>
            {canEdit &&
              (!hasManageRole ? (
                <Tooltip content={t("userRbac.addDirectDisabledTooltip")}>
                  <span className="inline-flex">
                    <Button size="sm" variant="soft" disabled>
                      {t("userRbac.addDirectButton")}
                    </Button>
                  </span>
                </Tooltip>
              ) : (
                <Button
                  size="sm"
                  variant="soft"
                  disabled={!canFetchPermissions}
                  onClick={openAddDirectDialog}
                >
                  {t("userRbac.addDirectButton")}
                </Button>
              ))}
          </div>
          {canEdit && hasManageRole && !sessionLoading && !canFetchPermissions && (
            <p className="text-caption text-text-muted">
              {t("userRbac.missingPermissionViewNote")}
            </p>
          )}

          {!hasManageRole && directPermissions.length === 0 ? (
            <div className="flex items-start gap-3 rounded-md border border-dashed border-border-strong bg-table-head p-3.5">
              <span
                aria-hidden="true"
                className="flex size-9 shrink-0 items-center justify-center rounded-md border border-border bg-surface text-text-muted"
              >
                <Lock className="size-4" />
              </span>
              <div>
                <div className="font-semibold text-text">
                  {t("userRbac.directExamBlockedTitle")}
                </div>
                <p className="mt-0.5 text-body text-text-muted">
                  {t("userRbac.directExamBlockedDescription")}
                </p>
              </div>
            </div>
          ) : directPermissions.length === 0 ? (
            <p className="text-body text-text-muted">{t("userRbac.directEmpty")}</p>
          ) : (
            <div className="flex flex-col gap-2.5">
              {directPermissions.map((item) => {
                const objName = resolveObjectName(rbacT, item.groupCode, item.groupName);
                const permName = resolvePermissionName(
                  rbacT,
                  item.permissionCode,
                  item.permissionName,
                );
                const shortName = resolvePermissionShortName(
                  rbacT,
                  item.permissionCode,
                  item.permissionName,
                );
                return (
                  <div
                    key={item.permissionId}
                    className="flex items-center justify-between gap-3 rounded-md border border-border bg-surface p-3"
                  >
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2 font-semibold text-text">
                        <span>
                          {objName} · {permName}{" "}
                          <span className="font-normal text-text-muted">
                            · {item.permissionCode}
                          </span>
                        </span>
                        {!item.effective && (
                          <StatusChip tone="warning">{t("userRbac.inactiveBadge")}</StatusChip>
                        )}
                      </div>
                      <div className="text-caption text-text-muted">
                        {t("userRbac.directItemMeta", {
                          reason: item.reason,
                          actor: item.grantedByUsername ?? t("permissionUsers.unknownActor"),
                          date: formatter.dateTime(new Date(item.grantedAt), {
                            day: "2-digit",
                            month: "2-digit",
                            year: "numeric",
                          }),
                        })}
                      </div>
                    </div>

                    {canEdit && (
                      <IconButton
                        icon={<X />}
                        tone="danger"
                        aria-label={t("userRbac.removeDirectAria", { name: shortName })}
                        tooltip={t("userRbac.removeDirectConfirmAction")}
                        onClick={() => setRemoveDirectTarget(item)}
                      />
                    )}
                  </div>
                );
              })}
            </div>
          )}

          <div className="mt-auto flex flex-col gap-1 text-caption text-text-muted">
            <p>{t("userRbac.directFootnote")}</p>
            {(!hasManageRole || hasInactiveDirect) && (
              <p>{t("userRbac.directInactiveNote")}</p>
            )}
          </div>
        </Card>
      </div>

      <Card padding="none" className="overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-5 py-4">
          <h2 className="text-title font-semibold text-text">
            {t("userRbac.effectiveSectionTitle")}
          </h2>
          <span className="text-body text-text-muted">
            {t("userRbac.effectiveCount", { count: effectivePermissions.length })}
          </span>
        </div>

        <Table
          data={effectivePermissions}
          columns={effectiveColumns}
          getRowId={(row) => row.permissionId}
          aria-label={t("userRbac.effectiveSectionTitle")}
          empty={
            <EmptyState
              icon={<Shield />}
              title={t("userRbac.effectiveSectionTitle")}
              description={t("userRbac.effectiveEmpty")}
            />
          }
        />
      </Card>

      <Dialog open={rolesDialogOpen} onOpenChange={setRolesDialogOpen}>
        <DialogContent size="md" closeLabel={t("actions.close")}>
          <DialogHeader>
            <DialogTitle>{t("userRbac.assignRolesTitle")}</DialogTitle>
            <DialogDescription>{t("userRbac.assignRolesDescription")}</DialogDescription>
          </DialogHeader>

          {rolesDialogError && (
            <div
              role="alert"
              className="rounded-md border border-danger/30 bg-danger-bg px-3.5 py-2.5 text-body text-danger"
            >
              {rolesDialogError}
            </div>
          )}

          {allRolesQuery.isPending ? (
            <div role="status" aria-label={commonT("loading")} className="flex flex-col gap-2">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              <Input
                type="search"
                value={roleSearch}
                leadingIcon={<Search />}
                placeholder={t("userRbac.roleSearchPlaceholder")}
                aria-label={t("userRbac.roleSearchPlaceholder")}
                onChange={(event) => setRoleSearch(event.target.value)}
              />
              {filteredAllRoles.length === 0 ? (
                <p className="text-caption text-text-muted">{t("bulk.rolesEmpty")}</p>
              ) : (
                <div className="flex max-h-[50vh] flex-col gap-2 overflow-y-auto pr-1">
                  {filteredAllRoles.map((role) => {
                    const checked = draftRoleIds.includes(role.id);
                    const displayName = resolveRoleName(rbacT, role.code, role.name, role.system);
                    return (
                      <label
                        key={role.id}
                        className="flex cursor-pointer items-center justify-between gap-3 rounded-md border border-border bg-surface px-3 py-2.5 hover:bg-subtle"
                      >
                        <span className="flex min-w-0 items-center gap-3">
                          <Checkbox
                            checked={checked}
                            onCheckedChange={(next) => {
                              setDraftRoleIds((prev) =>
                                next
                                  ? prev.includes(role.id)
                                    ? prev
                                    : [...prev, role.id]
                                  : prev.filter((id) => id !== role.id),
                              );
                            }}
                          />
                          <span className="min-w-0">
                            <span className="block font-semibold text-text">{displayName}</span>
                            <span className="block text-caption text-text-muted">{role.code}</span>
                          </span>
                        </span>
                        <div className="flex shrink-0 items-center gap-1.5">
                          {role.status === "INACTIVE" && (
                            <StatusChip tone="neutral">{t("rolesTab.statusInactive")}</StatusChip>
                          )}
                          <StatusChip tone={role.access === "MANAGE" ? "info" : "warning"}>
                            {t(`access.${role.access}`)}
                          </StatusChip>
                        </div>
                      </label>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          <DialogFooter>
            <Button variant="secondary" onClick={() => setRolesDialogOpen(false)}>
              {t("actions.cancel")}
            </Button>
            <Button
              variant="primary"
              loading={updateRolesMutation.isPending}
              onClick={() => updateRolesMutation.mutate(draftRoleIds)}
            >
              {t("userRbac.assignRolesConfirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={directDialogOpen} onOpenChange={setDirectDialogOpen}>
        <DialogContent size="lg" closeLabel={t("actions.close")}>
          <form onSubmit={handleSaveDirectPermissions} className="flex flex-col gap-4">
            <DialogHeader>
              <DialogTitle>{t("userRbac.directDialogTitle")}</DialogTitle>
              {(username || fullName) && (
                <DialogDescription>
                  {[username, fullName].filter(Boolean).join(" · ")}
                </DialogDescription>
              )}
            </DialogHeader>

            {directDialogError && (
              <div
                role="alert"
                className="rounded-md border border-danger/30 bg-danger-bg px-3.5 py-2.5 text-body text-danger"
              >
                {directDialogError}
              </div>
            )}

            <div className="max-h-[48vh] overflow-y-auto pr-1">
              <ObjectPermissionPicker
                groups={permissionGroups}
                selectedCodes={draftDirectCodes}
                onChange={setDraftDirectCodes}
                lockedObjects={lockedObjects}
                inheritedCodes={inheritedRoleCodes}
              />
            </div>

            <label className="flex flex-col gap-1.5">
              <span className="text-field-label font-medium text-text-secondary">
                {t("userRbac.directReasonLabel")} <span className="text-danger">*</span>
              </span>
              <Textarea
                rows={2}
                value={directReason}
                placeholder={t("userRbac.directReasonPlaceholder")}
                onChange={(event) => setDirectReason(event.target.value)}
              />
              <span className="text-caption text-text-muted">{t("userRbac.reasonHelper")}</span>
            </label>

            <DialogFooter>
              <Button
                type="button"
                variant="secondary"
                onClick={() => setDirectDialogOpen(false)}
              >
                {t("actions.cancel")}
              </Button>
              <Button type="submit" variant="primary" loading={updateDirectMutation.isPending}>
                {t("userRbac.directDialogSubmit")}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={Boolean(removeRoleTarget)}
        onOpenChange={(open) => {
          if (!open) setRemoveRoleTarget(null);
        }}
        tone="danger"
        loading={updateRolesMutation.isPending}
        title={t("userRbac.removeRoleConfirmTitle")}
        description={
          removeRoleTarget
            ? t("userRbac.removeRoleConfirmDescription", {
                name: resolveRoleName(
                  rbacT,
                  removeRoleTarget.roleCode,
                  removeRoleTarget.roleName,
                  removeRoleTarget.system,
                ),
              })
            : ""
        }
        confirmLabel={t("userRbac.removeRoleConfirmAction")}
        cancelLabel={t("actions.cancel")}
        closeLabel={t("actions.close")}
        onConfirm={() => {
          if (!removeRoleTarget) return;
          const nextIds = assignedRoles
            .filter((r) => r.roleId !== removeRoleTarget.roleId)
            .map((r) => r.roleId);
          updateRolesMutation.mutate(nextIds);
        }}
      />

      <ConfirmDialog
        open={Boolean(removeDirectTarget)}
        onOpenChange={(open) => {
          if (!open) setRemoveDirectTarget(null);
        }}
        tone="danger"
        loading={updateDirectMutation.isPending}
        title={t("userRbac.removeDirectConfirmTitle")}
        description={
          removeDirectTarget
            ? t("userRbac.removeDirectConfirmDescription", {
                name: resolvePermissionShortName(
                  rbacT,
                  removeDirectTarget.permissionCode,
                  removeDirectTarget.permissionName,
                ),
              })
            : ""
        }
        confirmLabel={t("userRbac.removeDirectConfirmAction")}
        cancelLabel={t("actions.cancel")}
        closeLabel={t("actions.close")}
        onConfirm={() => {
          if (!removeDirectTarget) return;
          const nextCodes = directPermissions
            .filter((p) => p.permissionCode !== removeDirectTarget.permissionCode)
            .map((p) => p.permissionCode);
          updateDirectMutation.mutate({ permissionCodes: nextCodes });
        }}
      />
    </div>
  );
}
