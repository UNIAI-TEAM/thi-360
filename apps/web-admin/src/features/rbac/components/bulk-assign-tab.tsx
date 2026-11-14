"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Card,
  Checkbox,
  Chip,
  cn,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  EmptyState,
  Input,
  notify,
  Pagination,
  Select,
  Skeleton,
  StatusChip,
  Textarea,
} from "@thi360/ui";
import { Plus, Search, TriangleAlert, Users, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useMemo, useRef, useState } from "react";
import { usePermissions } from "@/features/session/session-provider";
import {
  applyBulkAuthz as defaultApplyBulkAuthz,
  listPermissions as defaultListPermissions,
  listRoles as defaultListRoles,
  listUsers as defaultListUsers,
  previewBulkAuthz as defaultPreviewBulkAuthz,
  rbacKeys,
  type ListRolesParams,
  type ListUsersParams,
} from "../api";
import {
  resolveBulkReasonMessage,
  resolveObjectName,
  resolvePermissionName,
  resolvePermissionShortName,
  resolveRbacErrorMessage,
  resolveRoleName,
  type RbacTranslator,
} from "../messages";
import type {
  BulkAuthzAction,
  BulkAuthzOutcome,
  BulkAuthzRequest,
  BulkAuthzResponse,
  BulkAuthzTarget,
  PageResponseRoleSummaryResponse,
  PageResponseUserSummaryResponse,
  PermissionResponse,
  UserStatus,
  UserSummaryResponse,
} from "../types";
import {
  ObjectPermissionPicker,
  groupPermissionsByObject,
} from "./object-permission-picker";

export const BULK_USER_IDS_STORAGE_KEY = "rbac.bulk.userIds";
export const MAX_BULK_USERS = 500;
export const PREVIEW_DEBOUNCE_MS = 400;
/** Ẩn bộ lọc lớp tới khi T10 (Lớp học) merge; API trả 400 CLASS_NOT_SUPPORTED trước T10. */
export const ENABLE_CLASS_FILTER = false;

export interface BulkAssignTabProps {
  initialPermissionCode?: string | null;
  listUsers?: (params?: ListUsersParams) => Promise<PageResponseUserSummaryResponse>;
  listRoles?: (params?: ListRolesParams) => Promise<PageResponseRoleSummaryResponse>;
  listPermissions?: () => Promise<PermissionResponse[]>;
  previewBulkAuthz?: (
    payload: BulkAuthzRequest,
    signal?: AbortSignal,
  ) => Promise<BulkAuthzResponse>;
  applyBulkAuthz?: (
    payload: BulkAuthzRequest,
    idempotencyKey: string,
  ) => Promise<BulkAuthzResponse>;
}

function readStoredBulkUserIds(): string[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.sessionStorage.getItem(BULK_USER_IDS_STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    const valid = parsed.filter((item): item is string => typeof item === "string" && item.length > 0);
    return Array.from(new Set(valid)).slice(0, MAX_BULK_USERS);
  } catch {
    return [];
  }
}

function generateIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `bulk-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function outcomeTone(outcome: BulkAuthzOutcome): "success" | "neutral" | "danger" {
  if (outcome === "WILL_CHANGE") return "success";
  if (outcome === "SKIPPED") return "danger";
  return "neutral";
}

export function BulkAssignTab({
  initialPermissionCode,
  listUsers = defaultListUsers,
  listRoles = defaultListRoles,
  listPermissions = defaultListPermissions,
  previewBulkAuthz = defaultPreviewBulkAuthz,
  applyBulkAuthz = defaultApplyBulkAuthz,
}: BulkAssignTabProps) {
  const t = useTranslations("rbac");
  const commonT = useTranslations("common");
  const rbacT: RbacTranslator = (key, values) => t(key, values);
  const { can, isLoading: sessionLoading } = usePermissions();
  const queryClient = useQueryClient();

  const canViewRoles = !sessionLoading && can("ROLE.VIEW");
  const canViewPermissions = !sessionLoading && can("PERMISSION.VIEW");
  const canViewUsers = !sessionLoading && can("USER.VIEW");

  const [selectedUserIds, setSelectedUserIds] = useState<string[]>(readStoredBulkUserIds);
  const [userLookup, setUserLookup] = useState<Record<string, UserSummaryResponse>>({});
  const [batchLimitWarning, setBatchLimitWarning] = useState(false);

  const [accountSearch, setAccountSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState<string>("ALL");
  const [statusFilter, setStatusFilter] = useState<"ALL" | UserStatus>("ALL");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const [action, setAction] = useState<BulkAuthzAction>("GRANT");
  const [target, setTarget] = useState<BulkAuthzTarget>(() =>
    initialPermissionCode ? "PERMISSION" : "ROLE",
  );
  const [selectedRoleIds, setSelectedRoleIds] = useState<string[]>([]);
  const [selectedPermissionCodes, setSelectedPermissionCodes] = useState<string[]>(() =>
    initialPermissionCode ? [initialPermissionCode] : [],
  );
  const [reason, setReason] = useState("");

  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerDraftCodes, setPickerDraftCodes] = useState<string[]>([]);

  const [previewResult, setPreviewResult] = useState<{
    signature: string;
    data: BulkAuthzResponse;
  } | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [previewLoadingSeq, setPreviewLoadingSeq] = useState<number | null>(null);
  const [appliedBatch, setAppliedBatch] = useState<{
    signature: string;
    data: BulkAuthzResponse;
  } | null>(null);

  const requestSeqRef = useRef(0);
  const idempotencyKeyRef = useRef<string>(generateIdempotencyKey());
  const lastKeySignatureRef = useRef<string | null>(null);

  useEffect(() => {
    if (typeof window === "undefined") return;
    try {
      window.sessionStorage.removeItem(BULK_USER_IDS_STORAGE_KEY);
    } catch {
      // ignore storage errors
    }
  }, []);

  const usersParams: ListUsersParams = useMemo(
    () => ({
      ...(accountSearch.trim() ? { q: accountSearch.trim() } : {}),
      ...(roleFilter !== "ALL" ? { roleId: roleFilter } : {}),
      ...(statusFilter !== "ALL" ? { status: statusFilter } : {}),
      page: page - 1,
      size: pageSize,
    }),
    [accountSearch, page, pageSize, roleFilter, statusFilter],
  );

  const effectiveTarget: BulkAuthzTarget =
    target === "PERMISSION" && !canViewPermissions && canViewRoles
      ? "ROLE"
      : target === "ROLE" && !canViewRoles && canViewPermissions
        ? "PERMISSION"
        : target;

  const usersQuery = useQuery({
    queryKey: rbacKeys.users(usersParams),
    queryFn: () => listUsers(usersParams),
    enabled: canViewUsers,
  });

  const rolesQuery = useQuery({
    queryKey: rbacKeys.roles({ page: 0, size: 100 }),
    queryFn: () => listRoles({ page: 0, size: 100 }),
    enabled: canViewRoles,
  });

  const permissionsQuery = useQuery({
    queryKey: rbacKeys.permissions(),
    queryFn: () => listPermissions(),
    enabled: canViewPermissions && effectiveTarget === "PERMISSION",
  });

  const permissionGroups = useMemo(
    () => groupPermissionsByObject(permissionsQuery.data ?? [], rbacT),
    [permissionsQuery.data, t], // eslint-disable-line react-hooks/exhaustive-deps
  );

  const permissionByCode = useMemo(() => {
    const map = new Map<string, PermissionResponse>();
    for (const perm of permissionsQuery.data ?? []) {
      map.set(perm.code, perm);
    }
    return map;
  }, [permissionsQuery.data]);

  const selectedPermissionIds = useMemo(
    () =>
      selectedPermissionCodes
        .map((code) => permissionByCode.get(code)?.id)
        .filter((id): id is string => Boolean(id)),
    [permissionByCode, selectedPermissionCodes],
  );

  const roles = useMemo(() => rolesQuery.data?.items ?? [], [rolesQuery.data]);

  const roleFilterOptions = useMemo(
    () => [
      { value: "ALL", label: t("bulk.filterRoleAll") },
      ...roles.map((role) => ({
        value: role.id,
        label: resolveRoleName(rbacT, role.code, role.name, role.system),
      })),
    ],
    [roles, t], // eslint-disable-line react-hooks/exhaustive-deps
  );

  const statusOptions = useMemo(
    () => [
      { value: "ALL", label: t("bulk.filterStatusAll") },
      { value: "ACTIVE", label: t("permissionUsers.filterStatusActive") },
      { value: "INACTIVE", label: t("permissionUsers.filterStatusInactive") },
    ],
    [t],
  );

  const needsReason = action === "GRANT" && effectiveTarget === "PERMISSION";
  const hasSelection =
    selectedUserIds.length > 0 &&
    (effectiveTarget === "ROLE" ? selectedRoleIds.length > 0 : selectedPermissionIds.length > 0);
  const missingReason = needsReason && reason.trim().length === 0;

  const previewPayload = useMemo<BulkAuthzRequest | null>(() => {
    if (!hasSelection || missingReason) return null;
    const trimmedReason = reason.trim();
    return {
      action,
      target: effectiveTarget,
      userIds: selectedUserIds,
      ...(effectiveTarget === "ROLE"
        ? { roleIds: selectedRoleIds }
        : { permissionIds: selectedPermissionIds }),
      ...(trimmedReason ? { reason: trimmedReason } : {}),
    };
  }, [
    action,
    effectiveTarget,
    hasSelection,
    missingReason,
    reason,
    selectedPermissionIds,
    selectedRoleIds,
    selectedUserIds,
  ]);

  const requestSignature = useMemo(
    () => (previewPayload ? JSON.stringify(previewPayload) : null),
    [previewPayload],
  );

  useEffect(() => {
    if (!previewPayload || !requestSignature) {
      return;
    }

    if (lastKeySignatureRef.current !== requestSignature) {
      idempotencyKeyRef.current = generateIdempotencyKey();
      lastKeySignatureRef.current = requestSignature;
    }

    const controller = new AbortController();
    const seq = ++requestSeqRef.current;

    const timer = window.setTimeout(() => {
      setPreviewLoadingSeq(seq);
      setPreviewError(null);
      previewBulkAuthz(previewPayload, controller.signal)
        .then((res) => {
          if (controller.signal.aborted || seq !== requestSeqRef.current) return;
          setPreviewResult({ signature: requestSignature, data: res });
          setPreviewError(null);
          setPreviewLoadingSeq((curr) => (curr === seq ? null : curr));
        })
        .catch((error) => {
          if (controller.signal.aborted || seq !== requestSeqRef.current) return;
          setPreviewResult(null);
          setPreviewError(resolveRbacErrorMessage(rbacT, error, "bulk.errorPreview"));
          setPreviewLoadingSeq((curr) => (curr === seq ? null : curr));
        });
    }, PREVIEW_DEBOUNCE_MS);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [previewBulkAuthz, previewPayload, requestSignature]); // eslint-disable-line react-hooks/exhaustive-deps

  const applyMutation = useMutation({
    mutationFn: async () => {
      if (!previewPayload) {
        throw new Error("Missing bulk payload");
      }
      return applyBulkAuthz(previewPayload, idempotencyKeyRef.current);
    },
    onSuccess: async (result) => {
      if (requestSignature) {
        setAppliedBatch({ signature: requestSignature, data: result });
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["rbac", "roles"] }),
        queryClient.invalidateQueries({ queryKey: ["rbac", "permissions"] }),
        queryClient.invalidateQueries({ queryKey: ["rbac", "permission-users"] }),
        queryClient.invalidateQueries({ queryKey: ["rbac", "users"] }),
        queryClient.invalidateQueries({ queryKey: ["rbac", "user"] }),
      ]);
      notify.success(t("bulk.applySuccess", { count: result.willChange }));
    },
    onError: (error) => {
      notify.error(resolveRbacErrorMessage(rbacT, error, "bulk.applyFailed"));
    },
  });

  function toggleUserSelection(user: UserSummaryResponse, checked: boolean) {
    setAppliedBatch(null);
    setUserLookup((prev) => ({ ...prev, [user.id]: user }));
    setSelectedUserIds((prev) => {
      if (checked) {
        if (prev.includes(user.id)) return prev;
        if (prev.length >= MAX_BULK_USERS) {
          setBatchLimitWarning(true);
          return prev;
        }
        setBatchLimitWarning(false);
        return [...prev, user.id];
      }
      setBatchLimitWarning(false);
      return prev.filter((id) => id !== user.id);
    });
  }

  function handleSelectCurrentPage() {
    const visibleUsers = usersQuery.data?.items ?? [];
    if (visibleUsers.length === 0) return;

    setAppliedBatch(null);
    setUserLookup((prev) => {
      const next = { ...prev };
      for (const user of visibleUsers) next[user.id] = user;
      return next;
    });

    setSelectedUserIds((prev) => {
      const next = [...prev];
      const existing = new Set(prev);
      let hitLimit = false;
      for (const user of visibleUsers) {
        if (existing.has(user.id)) continue;
        if (next.length >= MAX_BULK_USERS) {
          hitLimit = true;
          break;
        }
        existing.add(user.id);
        next.push(user.id);
      }
      setBatchLimitWarning(hitLimit);
      return next;
    });
  }

  function handleClearSelection() {
    setAppliedBatch(null);
    setSelectedUserIds([]);
    setBatchLimitWarning(false);
  }

  function handleResetBatch() {
    setSelectedUserIds([]);
    setSelectedRoleIds([]);
    setSelectedPermissionCodes([]);
    setReason("");
    setBatchLimitWarning(false);
    setPreviewResult(null);
    setPreviewError(null);
    setAppliedBatch(null);
    idempotencyKeyRef.current = generateIdempotencyKey();
    lastKeySignatureRef.current = null;
  }

  function resolveItemLabel(code: string): string {
    if (effectiveTarget === "PERMISSION") {
      const perm = permissionByCode.get(code);
      if (perm) {
        return `${resolveObjectName(rbacT, perm.groupCode, perm.groupName)} · ${resolvePermissionName(rbacT, perm.code, perm.name)}`;
      }
      return resolvePermissionShortName(rbacT, code);
    }
    const role = roles.find((item) => item.code === code);
    if (role) {
      return resolveRoleName(rbacT, role.code, role.name, role.system);
    }
    return resolveRoleName(rbacT, code, code);
  }

  const activePreview =
    requestSignature && previewResult?.signature === requestSignature ? previewResult.data : null;
  const activeApplied =
    requestSignature && appliedBatch?.signature === requestSignature ? appliedBatch.data : null;
  const displayBatch = activeApplied ?? activePreview;
  const isPreviewLoading = previewLoadingSeq !== null && requestSignature !== null;

  const visibleUsers = usersQuery.data?.items ?? [];
  const totalUsers = usersQuery.data?.totalItems ?? 0;

  return (
    <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
      <Card padding="none" className="min-w-0 flex-1 overflow-hidden">
        <div className="flex flex-col gap-3 border-b border-border p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-title font-semibold text-text">
              {t("bulk.step1Title")}{" "}
              <span className="font-normal text-text-muted">
                · {t("bulk.selectedCount", { count: selectedUserIds.length })}
              </span>
            </h2>
            <div className="flex flex-wrap items-center gap-2">
              <Button
                size="sm"
                variant="secondary"
                disabled={visibleUsers.length === 0}
                onClick={handleSelectCurrentPage}
              >
                {t("bulk.selectCurrentPage")}
              </Button>
              {selectedUserIds.length > 0 && (
                <Button size="sm" variant="secondary" onClick={handleClearSelection}>
                  {t("bulk.clearSelection")}
                </Button>
              )}
            </div>
          </div>

          {batchLimitWarning && (
            <p role="alert" className="text-caption font-medium text-warning">
              {t("bulk.maxBatchWarning")}
            </p>
          )}

          <div className="flex flex-wrap items-end gap-2.5">
            <label className="flex min-w-[180px] flex-[2_1_190px] flex-col gap-1 text-caption text-text-muted">
              <span>{t("bulk.searchLabel")}</span>
              <Input
                type="search"
                value={accountSearch}
                leadingIcon={<Search />}
                placeholder={t("bulk.accountSearchPlaceholder")}
                onChange={(event) => {
                  setAccountSearch(event.target.value);
                  setPage(1);
                }}
              />
            </label>

            {canViewRoles && (
              <div className="flex min-w-[140px] flex-[1_1_140px] flex-col gap-1">
                <span className="text-caption text-text-muted">{t("bulk.roleFilter")}</span>
                <Select
                  aria-label={t("bulk.roleFilter")}
                  value={roleFilter}
                  options={roleFilterOptions}
                  onValueChange={(next) => {
                    setRoleFilter(next);
                    setPage(1);
                  }}
                />
              </div>
            )}

            {ENABLE_CLASS_FILTER && (
              <div className="flex min-w-[130px] flex-[1_1_130px] flex-col gap-1">
                <span className="text-caption text-text-muted">{t("bulk.classFilter")}</span>
                <Select
                  aria-label={t("bulk.classFilter")}
                  value="ALL"
                  options={[{ value: "ALL", label: t("bulk.filterClassAll") }]}
                  onValueChange={() => {}}
                />
              </div>
            )}

            <div className="flex min-w-[130px] flex-[1_1_130px] flex-col gap-1">
              <span className="text-caption text-text-muted">{t("bulk.statusFilter")}</span>
              <Select
                aria-label={t("bulk.statusFilter")}
                value={statusFilter}
                options={statusOptions}
                onValueChange={(next) => {
                  setStatusFilter(next as "ALL" | UserStatus);
                  setPage(1);
                }}
              />
            </div>
          </div>
        </div>

        {!sessionLoading && !canViewUsers ? (
          <div className="p-4">
            <EmptyState
              icon={<Users />}
              title={t("bulk.missingUserViewTitle")}
              description={t("bulk.missingUserViewNote")}
            />
          </div>
        ) : sessionLoading || usersQuery.isPending ? (
          <div role="status" aria-label={commonT("loading")} className="flex flex-col gap-2 p-4">
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
          </div>
        ) : usersQuery.isError ? (
          <div className="p-4">
            <EmptyState
              icon={<TriangleAlert />}
              title={t("bulk.errorAccountsTitle")}
              description={t("bulk.errorAccountsDescription")}
              action={
                <Button variant="secondary" onClick={() => usersQuery.refetch()}>
                  {t("actions.retry")}
                </Button>
              }
            />
          </div>
        ) : visibleUsers.length === 0 ? (
          <div className="p-4">
            <EmptyState
              icon={<Users />}
              title={t("bulk.emptyAccountsTitle")}
              description={t("bulk.emptyAccountsDescription")}
            />
          </div>
        ) : (
          <ul className="m-0 list-none p-0">
            {visibleUsers.map((user) => {
              const isChecked = selectedUserIds.includes(user.id);
              const hasManage = user.roles.some((role) => role.access === "MANAGE");
              const roleLabel =
                user.roles.length > 0
                  ? user.roles
                      .map((role) => resolveRoleName(rbacT, role.code, role.name))
                      .join(", ")
                  : user.code;

              return (
                <li key={user.id} className="border-b border-border last:border-b-0">
                  <label
                    className={cn(
                      "flex min-h-[60px] cursor-pointer items-center gap-3 px-4 py-2 transition-colors duration-150",
                      isChecked ? "bg-primary-50" : "bg-surface hover:bg-subtle",
                    )}
                  >
                    <Checkbox
                      checked={isChecked}
                      onCheckedChange={(checked) => toggleUserSelection(user, Boolean(checked))}
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-semibold text-text">
                        {user.username}
                      </span>
                      <span className="block truncate text-caption text-text-muted">
                        {user.fullName} · {roleLabel}
                      </span>
                    </span>
                    <div className="flex shrink-0 items-center gap-1.5">
                      {user.status !== "ACTIVE" && (
                        <StatusChip tone="neutral">
                          {t(`userStatuses.${user.status}`)}
                        </StatusChip>
                      )}
                      <StatusChip tone={hasManage ? "info" : "warning"}>
                        {hasManage ? t("access.MANAGE") : t("access.EXAM")}
                      </StatusChip>
                    </div>
                  </label>
                </li>
              );
            })}
          </ul>
        )}

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
          {t("bulk.step1Footnote")}
        </div>
      </Card>

      <div className="flex min-w-0 flex-1 flex-col gap-4">
        <Card padding="md" className="flex flex-col gap-4">
          <h2 className="text-title font-semibold text-text">{t("bulk.step2Title")}</h2>

          <div className="flex flex-wrap gap-3">
            <fieldset className="flex flex-col gap-1.5 border-0 p-0">
              <legend className="text-caption text-text-muted">{t("bulk.actionLabel")}</legend>
              <div
                role="group"
                aria-label={t("bulk.actionLabel")}
                className="inline-flex h-11 gap-1 rounded-lg bg-subtle p-1"
              >
                {(["GRANT", "REVOKE"] as const).map((option) => {
                  const active = action === option;
                  return (
                    <button
                      key={option}
                      type="button"
                      aria-pressed={active}
                      onClick={() => {
                        setAppliedBatch(null);
                        setAction(option);
                      }}
                      className={cn(
                        "cursor-pointer rounded-md px-4 text-label transition-colors duration-150 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-primary-600/30",
                        active
                          ? "bg-surface font-semibold text-primary-text shadow-xs"
                          : "font-medium text-text-secondary hover:text-text",
                      )}
                    >
                      {option === "GRANT" ? t("bulk.actionGrant") : t("bulk.actionRevoke")}
                    </button>
                  );
                })}
              </div>
            </fieldset>

            <fieldset className="flex flex-col gap-1.5 border-0 p-0">
              <legend className="text-caption text-text-muted">{t("bulk.targetLabel")}</legend>
              <div
                role="group"
                aria-label={t("bulk.targetLabel")}
                className="inline-flex h-11 gap-1 rounded-lg bg-subtle p-1"
              >
                {(["ROLE", "PERMISSION"] as const).map((option) => {
                  const active = effectiveTarget === option;
                  const targetDisabled =
                    (option === "ROLE" && !canViewRoles) ||
                    (option === "PERMISSION" && !canViewPermissions);
                  return (
                    <button
                      key={option}
                      type="button"
                      disabled={targetDisabled}
                      aria-pressed={active}
                      onClick={() => {
                        if (targetDisabled) return;
                        setAppliedBatch(null);
                        setTarget(option);
                      }}
                      className={cn(
                        "cursor-pointer rounded-md px-4 text-label transition-colors duration-150 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-primary-600/30 disabled:cursor-not-allowed disabled:opacity-50",
                        active
                          ? "bg-surface font-semibold text-primary-text shadow-xs"
                          : "font-medium text-text-secondary hover:text-text",
                      )}
                    >
                      {option === "ROLE" ? t("bulk.targetRole") : t("bulk.targetPermission")}
                    </button>
                  );
                })}
              </div>
              {!sessionLoading && !canViewRoles && (
                <span className="text-caption text-text-muted">
                  {t("bulk.missingRoleViewNote")}
                </span>
              )}
              {!sessionLoading && !canViewPermissions && (
                <span className="text-caption text-text-muted">
                  {t("bulk.missingPermissionViewNote")}
                </span>
              )}
            </fieldset>
          </div>

          {effectiveTarget === "ROLE" ? (
            !canViewRoles ? (
              <p className="text-body text-text-muted">{t("bulk.missingRoleViewNote")}</p>
            ) : (
            <div className="flex flex-col gap-2">
              <div className="text-field-label font-medium text-text-secondary">
                {t("bulk.rolesLabel", {
                  mode:
                    action === "GRANT" ? t("bulk.pickerGrantMode") : t("bulk.pickerRevokeMode"),
                })}{" "}
                <span className="text-danger">*</span>
              </div>
              {rolesQuery.isPending ? (
                <Skeleton className="h-10 w-full" />
              ) : roles.length === 0 ? (
                <p className="text-caption text-text-muted">{t("bulk.rolesEmpty")}</p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {roles.map((role) => {
                    const selected = selectedRoleIds.includes(role.id);
                    const label = resolveRoleName(rbacT, role.code, role.name, role.system);
                    return (
                      <Chip
                        key={role.id}
                        variant="filter"
                        selected={selected}
                        onClick={() => {
                          setAppliedBatch(null);
                          setSelectedRoleIds((prev) =>
                            prev.includes(role.id)
                              ? prev.filter((id) => id !== role.id)
                              : [...prev, role.id],
                          );
                        }}
                      >
                        <span>{label}</span>
                        <span className="text-caption opacity-80">
                          ({t(`access.${role.access}`)})
                        </span>
                      </Chip>
                    );
                  })}
                </div>
              )}
            </div>
            )
          ) : !canViewPermissions ? (
            <p className="text-body text-text-muted">{t("bulk.missingPermissionViewNote")}</p>
          ) : (
            <div className="flex flex-col gap-2">
              <div className="text-field-label font-medium text-text-secondary">
                {t("bulk.selectedPermissionsLabel", {
                  mode:
                    action === "GRANT" ? t("bulk.pickerGrantMode") : t("bulk.pickerRevokeMode"),
                })}{" "}
                <span className="text-danger">*</span>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {selectedPermissionCodes.map((code) => {
                  const perm = permissionByCode.get(code);
                  const display = perm
                    ? `${resolveObjectName(rbacT, perm.groupCode, perm.groupName)} · ${resolvePermissionName(rbacT, perm.code, perm.name)}`
                    : resolvePermissionShortName(rbacT, code);
                  const shortName = resolvePermissionShortName(rbacT, code, perm?.name);
                  return (
                    <span
                      key={code}
                      className="inline-flex h-8 items-center gap-1 rounded-full bg-primary-50 pl-3 pr-1 text-body font-medium text-primary-text"
                    >
                      <span>{display}</span>
                      <button
                        type="button"
                        aria-label={t("bulk.removePermissionAria", { name: shortName })}
                        onClick={() => {
                          setAppliedBatch(null);
                          setSelectedPermissionCodes((prev) =>
                            prev.filter((item) => item !== code),
                          );
                        }}
                        className="inline-flex size-7 cursor-pointer items-center justify-center rounded-full text-primary-text hover:bg-primary-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-600/30"
                      >
                        <X aria-hidden="true" className="size-3.5" />
                      </button>
                    </span>
                  );
                })}
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  onClick={() => {
                    setPickerDraftCodes(selectedPermissionCodes);
                    setPickerOpen(true);
                  }}
                >
                  <Plus aria-hidden="true" className="size-4" />
                  <span>{t("bulk.addPermission")}</span>
                </Button>
              </div>
            </div>
          )}

          {effectiveTarget === "PERMISSION" && canViewPermissions && (
            <label className="flex flex-col gap-1.5">
              <span className="text-field-label font-medium text-text-secondary">
                {t("bulk.reasonLabel")}{" "}
                {needsReason && <span className="text-danger">*</span>}
              </span>
              <Textarea
                rows={2}
                value={reason}
                placeholder={t("bulk.reasonPlaceholder")}
                onChange={(event) => {
                  setAppliedBatch(null);
                  setReason(event.target.value);
                }}
              />
              <span className="text-caption text-text-muted">{t("bulk.reasonHelper")}</span>
            </label>
          )}
        </Card>

        <Card padding="none" className="overflow-hidden">
          <div className="border-b border-border px-4 py-3.5">
            <h2 className="text-title font-semibold text-text">{t("bulk.previewTitle")}</h2>
          </div>

          {activeApplied && (
            <div
              role="status"
              className="border-b border-border bg-success-bg px-4 py-3 text-body font-medium text-success"
            >
              {t("bulk.resultBanner", {
                changed: activeApplied.willChange,
                noChange: activeApplied.noChange,
                skipped: activeApplied.skipped,
              })}
            </div>
          )}

          {!hasSelection ? (
            <div className="px-4 py-6 text-center text-body text-text-muted">
              {t("bulk.previewEmptyHint")}
            </div>
          ) : missingReason ? (
            <div className="px-4 py-6 text-center text-body text-text-muted">
              {t("bulk.previewNeedReasonHint")}
            </div>
          ) : isPreviewLoading ? (
            <div role="status" aria-label={commonT("loading")} className="flex flex-col gap-2 p-4">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : previewError ? (
            <div className="p-4">
              <EmptyState
                icon={<TriangleAlert />}
                title={t("bulk.previewTitle")}
                description={previewError}
              />
            </div>
          ) : displayBatch ? (
            <>
              <ul className="m-0 list-none p-0">
                {displayBatch.items.map((item) => {
                  const username =
                    item.username ?? userLookup[item.userId]?.username ?? item.userId;

                  let detailText = t("bulk.summaryNoChange");
                  if (item.outcome === "SKIPPED") {
                    detailText = resolveBulkReasonMessage(rbacT, item.reasonCode);
                  } else if (item.outcome === "WILL_CHANGE") {
                    const parts: string[] = [];
                    if (item.added.length > 0) {
                      parts.push(
                        t("bulk.summaryAdded", {
                          items: item.added.map(resolveItemLabel).join(", "),
                        }),
                      );
                    }
                    if (item.removed.length > 0) {
                      parts.push(
                        t("bulk.summaryRemoved", {
                          items: item.removed.map(resolveItemLabel).join(", "),
                        }),
                      );
                    }
                    detailText = parts.join(" · ") || t("bulk.summaryNoChange");
                  }

                  return (
                    <li
                      key={item.userId}
                      className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3"
                    >
                      <div className="min-w-0">
                        <div className="font-semibold text-text">{username}</div>
                        <div className="text-caption text-text-muted">{detailText}</div>
                      </div>
                      <StatusChip tone={outcomeTone(item.outcome)}>
                        {t(`bulk.outcomes.${item.outcome}`)}
                      </StatusChip>
                    </li>
                  );
                })}
              </ul>

              <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3.5">
                <div className="text-body text-text-muted">
                  <span>{t("bulk.willChangeCount", { count: displayBatch.willChange })}</span>
                  {" · "}
                  <span>{t("bulk.noChangeCount", { count: displayBatch.noChange })}</span>
                  {" · "}
                  <span>{t("bulk.skippedCount", { count: displayBatch.skipped })}</span>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  {activeApplied ? (
                    <Button variant="secondary" onClick={handleResetBatch}>
                      {t("bulk.resetBatch")}
                    </Button>
                  ) : (
                    <Button
                      variant="primary"
                      loading={applyMutation.isPending}
                      disabled={displayBatch.willChange === 0 || isPreviewLoading}
                      onClick={() => applyMutation.mutate()}
                    >
                      {t("bulk.applyButton", { count: displayBatch.willChange })}
                    </Button>
                  )}
                </div>
              </div>
            </>
          ) : null}
        </Card>
      </div>

      <Dialog open={pickerOpen} onOpenChange={setPickerOpen}>
        <DialogContent size="lg" closeLabel={t("actions.close")}>
          <DialogHeader>
            <DialogTitle>{t("bulk.pickerTitle")}</DialogTitle>
            <DialogDescription>
              {t("bulk.pickerDescription", {
                mode: action === "GRANT" ? t("bulk.pickerGrantMode") : t("bulk.pickerRevokeMode"),
              })}
            </DialogDescription>
          </DialogHeader>

          <div className="max-h-[60vh] overflow-y-auto pr-1">
            <ObjectPermissionPicker
              groups={permissionGroups}
              selectedCodes={pickerDraftCodes}
              onChange={setPickerDraftCodes}
            />
          </div>

          <DialogFooter>
            <Button variant="secondary" onClick={() => setPickerOpen(false)}>
              {t("actions.cancel")}
            </Button>
            <Button
              variant="primary"
              onClick={() => {
                setAppliedBatch(null);
                setSelectedPermissionCodes(pickerDraftCodes);
                setPickerOpen(false);
              }}
            >
              {t("bulk.pickerConfirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
