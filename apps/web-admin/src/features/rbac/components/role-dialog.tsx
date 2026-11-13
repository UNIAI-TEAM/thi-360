"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  cn,
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  EmptyState,
  FormField,
  Input,
  notify,
  Select,
  Skeleton,
  Textarea,
} from "@thi360/ui";
import { TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import {
  createRole as defaultCreateRole,
  getRole as defaultGetRole,
  rbacKeys,
  updateRole as defaultUpdateRole,
  updateRolePermissions as defaultUpdateRolePermissions,
} from "../api";
import {
  resolveRbacErrorMessage,
  resolveRoleName,
  type RbacTranslator,
} from "../messages";
import { roleFormSchema, type RoleFormValues } from "../schema";
import type {
  AccessScope,
  CreateRoleRequest,
  PermissionObjectGroup,
  RoleDetailResponse,
  RoleStatus,
  UpdateRolePermissionsRequest,
  UpdateRoleRequest,
} from "../types";
import { ObjectPermissionPicker } from "./object-permission-picker";

export interface RoleDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: "create" | "edit" | "view";
  roleId?: string;
  permissionGroups: readonly PermissionObjectGroup[];
  getRole?: (id: string) => Promise<RoleDetailResponse>;
  createRole?: (payload: CreateRoleRequest) => Promise<RoleDetailResponse>;
  updateRole?: (id: string, payload: UpdateRoleRequest) => Promise<RoleDetailResponse>;
  updateRolePermissions?: (
    id: string,
    payload: UpdateRolePermissionsRequest,
  ) => Promise<RoleDetailResponse>;
}

function sameCodeSet(a: readonly string[], b: readonly string[]): boolean {
  if (a.length !== b.length) return false;
  const setA = new Set(a);
  if (setA.size !== b.length) return false;
  return b.every((item) => setA.has(item));
}

export function RoleDialog({
  open,
  onOpenChange,
  mode,
  roleId,
  permissionGroups,
  getRole = defaultGetRole,
  createRole = defaultCreateRole,
  updateRole = defaultUpdateRole,
  updateRolePermissions = defaultUpdateRolePermissions,
}: RoleDialogProps) {
  const t = useTranslations("rbac");
  const rbacT: RbacTranslator = (key, values) => t(key, values);

  const isCreate = mode === "create";

  const detailQuery = useQuery({
    queryKey: roleId ? rbacKeys.roleDetail(roleId) : ["rbac", "role", "none"],
    queryFn: () => getRole(roleId!),
    enabled: open && !isCreate && Boolean(roleId),
  });

  const title =
    mode === "create"
      ? t("roleDialog.createTitle")
      : mode === "edit"
        ? t("roleDialog.editTitle")
        : t("roleDialog.viewTitle");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        size="lg"
        closeLabel={t("actions.close")}
        className="max-w-[920px] overflow-hidden"
      >
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>

        {!isCreate && detailQuery.isPending && (
          <div role="status" aria-label={t("roleDialog.editTitle")} className="flex flex-col gap-3">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
            <Skeleton className="h-72" />
          </div>
        )}

        {!isCreate && detailQuery.isError && (
          <EmptyState
            icon={<TriangleAlert />}
            title={t("roleDialog.loadError")}
            action={
              <Button onClick={() => void detailQuery.refetch()}>
                {t("actions.retry")}
              </Button>
            }
          />
        )}

        {(isCreate || (!isCreate && detailQuery.data)) && (
          <RoleDialogBody
            key={`${mode}-${roleId ?? "new"}-${detailQuery.data?.version ?? 0}`}
            mode={mode}
            detail={isCreate ? undefined : detailQuery.data}
            permissionGroups={permissionGroups}
            rbacT={rbacT}
            onClose={() => onOpenChange(false)}
            createRole={createRole}
            updateRole={updateRole}
            updateRolePermissions={updateRolePermissions}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function RoleDialogBody({
  mode,
  detail,
  permissionGroups,
  rbacT,
  onClose,
  createRole,
  updateRole,
  updateRolePermissions,
}: {
  mode: "create" | "edit" | "view";
  detail?: RoleDetailResponse;
  permissionGroups: readonly PermissionObjectGroup[];
  rbacT: RbacTranslator;
  onClose: () => void;
  createRole: (payload: CreateRoleRequest) => Promise<RoleDetailResponse>;
  updateRole: (id: string, payload: UpdateRoleRequest) => Promise<RoleDetailResponse>;
  updateRolePermissions: (
    id: string,
    payload: UpdateRolePermissionsRequest,
  ) => Promise<RoleDetailResponse>;
}) {
  const t = useTranslations("rbac");
  const queryClient = useQueryClient();

  const isCreate = mode === "create";
  const isView = mode === "view";
  const accessLocked =
    isView || Boolean(detail && (detail.system || detail.userCount > 0));

  const initialName = detail
    ? resolveRoleName(rbacT, detail.code, detail.name, detail.system)
    : "";

  const [selectedCodes, setSelectedCodes] = useState<string[]>(
    () => detail?.permissionCodes ?? [],
  );
  const [serverError, setServerError] = useState<string | null>(null);

  const form = useForm<RoleFormValues>({
    resolver: zodResolver(roleFormSchema),
    defaultValues: {
      code: detail?.code ?? "",
      name: initialName,
      description: detail?.description ?? "",
      access: detail?.access ?? "MANAGE",
      status: detail?.status ?? "ACTIVE",
    },
  });

  const currentAccess = useWatch({ control: form.control, name: "access" });
  const currentStatus = useWatch({ control: form.control, name: "status" });


  const mutation = useMutation({
    mutationFn: async (values: RoleFormValues) => {
      const normalizedCodes = values.access === "EXAM" ? [] : selectedCodes;
      const trimmedDescription = values.description?.trim() ?? "";

      if (isCreate) {
        return createRole({
          code: values.code.trim(),
          name: values.name.trim(),
          ...(trimmedDescription ? { description: trimmedDescription } : {}),
          access: values.access,
          status: "ACTIVE",
          permissionCodes: normalizedCodes,
        });
      }

      if (!detail) throw new Error("Missing role detail");

      const accessChanged = values.access !== detail.access;
      const statusChanged = values.status !== detail.status;
      const nameChanged = values.name.trim() !== initialName.trim();
      const descChanged = trimmedDescription !== (detail.description ?? "").trim();
      const permissionsChanged = !sameCodeSet(normalizedCodes, detail.permissionCodes);

      let latest = detail;
      if (accessChanged || statusChanged || nameChanged || descChanged) {
        latest = await updateRole(detail.id, {
          ...(nameChanged ? { name: values.name.trim() } : {}),
          ...(descChanged ? { description: trimmedDescription } : {}),
          ...(accessChanged ? { access: values.access } : {}),
          ...(statusChanged ? { status: values.status } : {}),
          version: detail.version,
        });
      }

      if (permissionsChanged && values.access !== "EXAM") {
        latest = await updateRolePermissions(detail.id, {
          permissionCodes: normalizedCodes,
          version: latest.version,
        });
      }

      return latest;
    },
    onSuccess: async (saved) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["rbac", "roles"] }),
        queryClient.invalidateQueries({ queryKey: ["rbac", "permissions"] }),
        queryClient.invalidateQueries({ queryKey: rbacKeys.roleDetail(saved.id) }),
      ]);
      notify.success(isCreate ? t("roleDialog.createSuccess") : t("roleDialog.editSuccess"));
      onClose();
    },
    onError: (error) => {
      const msg = resolveRbacErrorMessage(rbacT, error, "roleDialog.saveFailed");
      setServerError(msg);
      notify.error(msg);
    },
  });

  const codeError = form.formState.errors.code?.message;
  const nameError = form.formState.errors.name?.message;

  const statusOptions = useMemo(
    () => [
      { value: "ACTIVE", label: t("roleDialog.statusActive") },
      { value: "INACTIVE", label: t("roleDialog.statusInactive") },
    ],
    [t],
  );

  function handleScopeChange(nextAccess: AccessScope) {
    if (accessLocked) return;
    form.setValue("access", nextAccess, { shouldDirty: true });
  }

  return (
    <form
      noValidate
      onSubmit={form.handleSubmit((values) => {
        setServerError(null);
        mutation.mutate(values);
      })}
      className="flex min-h-0 flex-1 flex-col gap-4"
    >
      {serverError && (
        <div
          role="alert"
          className="rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-2.5 text-body text-danger"
        >
          {serverError}
        </div>
      )}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <FormField
          label={t("roleDialog.codeLabel")}
          required={isCreate}
          error={codeError ? t(`roleDialog.${codeError}`) : undefined}
          hint={!isCreate ? t("roleDialog.codeLockedHint") : undefined}
        >
          {(fieldProps) => (
            <Input
              {...fieldProps}
              {...form.register("code")}
              disabled={!isCreate || mutation.isPending}
              placeholder={t("roleDialog.codePlaceholder")}
            />
          )}
        </FormField>

        <FormField
          label={t("roleDialog.nameLabel")}
          required={!isView}
          error={nameError ? t(`roleDialog.${nameError}`) : undefined}
        >
          {(fieldProps) => (
            <Input
              {...fieldProps}
              {...form.register("name")}
              disabled={isView || mutation.isPending}
              placeholder={t("roleDialog.namePlaceholder")}
            />
          )}
        </FormField>

        <div className="flex flex-col gap-1.5">
          <span className="text-field-label font-medium text-text-secondary">
            {t("roleDialog.accessLabel")}
            {!isView && <span className="ml-0.5 text-danger">*</span>}
          </span>
          <div
            role="group"
            aria-label={t("roleDialog.accessLabel")}
            className="grid h-10 grid-cols-2 gap-1 rounded-md bg-subtle p-1"
          >
            {(["MANAGE", "EXAM"] as const).map((scope) => {
              const active = currentAccess === scope;
              return (
                <button
                  key={scope}
                  type="button"
                  aria-pressed={active}
                  disabled={accessLocked || mutation.isPending}
                  onClick={() => handleScopeChange(scope)}
                  className={cn(
                    "inline-flex items-center justify-center rounded-sm text-label transition-colors duration-150 motion-reduce:transition-none",
                    "focus-visible:ring-3 focus-visible:ring-primary-600/30 focus-visible:outline-none",
                    "disabled:cursor-not-allowed disabled:opacity-60",
                    active
                      ? "bg-surface font-semibold text-primary-text shadow-xs"
                      : "font-medium text-text-secondary hover:bg-surface/60",
                  )}
                >
                  {t(`access.${scope}`)}
                </button>
              );
            })}
          </div>
          {!isCreate && accessLocked && !isView && (
            <p className="text-caption text-text-muted">{t("roleDialog.accessLockedHint")}</p>
          )}
        </div>
      </div>

      <div
        className={cn(
          "grid grid-cols-1 gap-3",
          !isCreate ? "sm:grid-cols-[2fr_1fr]" : "",
        )}
      >
        <FormField label={t("roleDialog.descriptionLabel")}>
          {(fieldProps) => (
            <Textarea
              {...fieldProps}
              {...form.register("description")}
              rows={2}
              disabled={isView || mutation.isPending}
              placeholder={t("roleDialog.descriptionPlaceholder")}
            />
          )}
        </FormField>

        {!isCreate && (
          <FormField label={t("roleDialog.statusLabel")}>
            {(fieldProps) => (
              <Select
                {...fieldProps}
                value={currentStatus}
                disabled={isView || Boolean(detail?.system) || mutation.isPending}
                options={statusOptions}
                onValueChange={(value) =>
                  form.setValue("status", value as RoleStatus, { shouldDirty: true })
                }
              />
            )}
          </FormField>
        )}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto pr-1">
        <ObjectPermissionPicker
          groups={permissionGroups}
          selectedCodes={selectedCodes}
          onChange={setSelectedCodes}
          examLocked={currentAccess === "EXAM"}
          readOnly={isView || mutation.isPending}
          sectionTitle={t("roleDialog.permissionsSectionTitle")}
        />
      </div>

      <DialogFooter>
        <Button
          variant="secondary"
          onClick={onClose}
          disabled={mutation.isPending}
        >
          {isView ? t("actions.close") : t("actions.cancel")}
        </Button>
        {!isView && (
          <Button type="submit" loading={mutation.isPending}>
            {isCreate ? t("roleDialog.createSubmit") : t("roleDialog.editSubmit")}
          </Button>
        )}
      </DialogFooter>
    </form>
  );
}
