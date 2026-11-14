"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
  Input,
  notify,
  Select,
  Textarea,
} from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { usePermissions } from "@/features/session/session-provider";
import {
  listUsers as defaultListUsers,
  type ListUsersQuery,
  type PageResponseUserSummary,
  type UserSummary,
} from "@/features/users/api";
import {
  ClassesApiError,
  type ClassDetail,
  type ClassSummary,
  createClass as defaultCreateClass,
  updateClass as defaultUpdateClass,
} from "../api";
import { type ClassFormValues, createClassFormSchema } from "../schema";
import { mapClassErrorCodeToKey, resolveClassesErrorMessage } from "../utils";

const NONE_OWNER_VALUE = "__NONE__";

export type ClassEditableFields = Pick<
  ClassSummary | ClassDetail,
  "id" | "code" | "name" | "description" | "ownerUserId" | "ownerUsername" | "ownerFullName" | "status" | "version"
>;

export interface ClassFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialClass?: ClassEditableFields | null;
  onSaved: (saved: ClassDetail) => void;
  createClass?: typeof defaultCreateClass;
  updateClass?: typeof defaultUpdateClass;
  listActiveUsers?: () => Promise<UserSummary[]>;
}

export async function fetchDefaultActiveUsers(
  fetchPage: (query: ListUsersQuery) => Promise<PageResponseUserSummary> = defaultListUsers,
): Promise<UserSummary[]> {
  const first = await fetchPage({
    status: "ACTIVE",
    page: 0,
    size: 100,
    sort: "username,asc",
  });
  if (first.totalPages <= 1) {
    return first.items;
  }
  const items = [...first.items];
  for (let page = 1; page < first.totalPages; page += 1) {
    const next = await fetchPage({
      status: "ACTIVE",
      page,
      size: 100,
      sort: "username,asc",
    });
    items.push(...next.items);
  }
  return items;
}

export function ClassFormDialog({
  open,
  onOpenChange,
  initialClass = null,
  onSaved,
  createClass = defaultCreateClass,
  updateClass = defaultUpdateClass,
  listActiveUsers = fetchDefaultActiveUsers,
}: ClassFormDialogProps) {
  const t = useTranslations("classes");
  const [submitting, setSubmitting] = useState(false);
  const isEdit = Boolean(initialClass);

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!submitting) onOpenChange(nextOpen);
      }}
    >
      <DialogContent
        size="md"
        closeLabel={t("formDialog.close")}
        className="flex max-h-[85vh] flex-col overflow-hidden"
      >
        <DialogHeader className="shrink-0">
          <DialogTitle>
            {isEdit ? t("formDialog.editTitle") : t("formDialog.createTitle")}
          </DialogTitle>
          <DialogDescription>
            {isEdit ? t("formDialog.editDescription") : t("formDialog.createDescription")}
          </DialogDescription>
        </DialogHeader>

        {open && (
          <ClassFormBody
            key={initialClass ? `${initialClass.id}-${initialClass.version}` : "create"}
            initialClass={initialClass}
            onCancel={() => onOpenChange(false)}
            onSubmittingChange={setSubmitting}
            onSaved={(saved) => {
              onOpenChange(false);
              onSaved(saved);
            }}
            createClass={createClass}
            updateClass={updateClass}
            listActiveUsers={listActiveUsers}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function ClassFormBody({
  initialClass,
  onCancel,
  onSubmittingChange,
  onSaved,
  createClass,
  updateClass,
  listActiveUsers,
}: {
  initialClass: ClassEditableFields | null;
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onSaved: (saved: ClassDetail) => void;
  createClass: typeof defaultCreateClass;
  updateClass: typeof defaultUpdateClass;
  listActiveUsers: () => Promise<UserSummary[]>;
}) {
  const t = useTranslations("classes");
  const common = useTranslations("common");
  const { can } = usePermissions();
  const isEdit = Boolean(initialClass);
  const canListUsers = can("USER.VIEW");
  const [submitError, setSubmitError] = useState<string | null>(null);

  const usersQuery = useQuery({
    queryKey: ["classes", "owner-candidates"],
    queryFn: listActiveUsers,
    enabled: canListUsers,
    staleTime: 60_000,
  });

  const schema = useMemo(
    () =>
      createClassFormSchema({
        codeRequired: t("validation.codeRequired"),
        codeInvalid: t("validation.codeInvalid"),
        nameRequired: t("validation.nameRequired"),
        nameMax: t("validation.nameMax"),
        descriptionMax: t("validation.descriptionMax"),
      }),
    [t],
  );

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<ClassFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      code: initialClass?.code ?? "",
      name: initialClass?.name ?? "",
      description: initialClass?.description ?? "",
      ownerUserId: initialClass?.ownerUserId ?? NONE_OWNER_VALUE,
      status: initialClass?.status ?? "ACTIVE",
    },
  });

  const mutation = useMutation({
    mutationFn: (values: ClassFormValues) => {
      onSubmittingChange(true);
      const ownerId =
        values.ownerUserId && values.ownerUserId !== NONE_OWNER_VALUE
          ? values.ownerUserId
          : undefined;
      if (initialClass) {
        const hadOwner = Boolean(initialClass.ownerUserId);
        const clearedOwner = canListUsers && hadOwner && !ownerId;
        return updateClass(initialClass.id, {
          code: values.code,
          name: values.name,
          description: values.description,
          ...(canListUsers && ownerId ? { ownerUserId: ownerId } : {}),
          ...(clearedOwner ? { clearOwner: true } : {}),
          version: initialClass.version,
        });
      }
      return createClass({
        code: values.code,
        name: values.name,
        ...(values.description ? { description: values.description } : {}),
        ...(canListUsers && ownerId ? { ownerUserId: ownerId } : {}),
        status: values.status,
      });
    },
    onSettled: () => {
      onSubmittingChange(false);
    },
    onSuccess: (saved) => {
      notify.success(
        isEdit
          ? t("formDialog.updateSuccess", { code: saved.code })
          : t("formDialog.createSuccess", { code: saved.code }),
      );
      onSaved(saved);
    },
    onError: (error) => {
      if (error instanceof ClassesApiError) {
        let appliedFieldError = false;
        if (error.code === "CLASS_CODE_TAKEN" || error.code === "CLASS_CODE_INVALID") {
          const key = mapClassErrorCodeToKey(error.code);
          if (key) {
            setError("code", { message: t(`errorCodes.${key}`) });
            appliedFieldError = true;
          }
        }
        for (const fe of error.fieldErrors) {
          const mappedKey = mapClassErrorCodeToKey(fe.code);
          const msg = mappedKey ? t(`errorCodes.${mappedKey}`) : t("errorCodes.VALIDATION_FAILED");
          if (fe.field === "code" || fe.field === "name" || fe.field === "description") {
            setError(fe.field, { message: msg });
            appliedFieldError = true;
          }
        }
        if (appliedFieldError) {
          setSubmitError(null);
          return;
        }
      }
      setSubmitError(
        resolveClassesErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("errorCodes.INTERNAL_ERROR"),
        ),
      );
    },
  });

  const ownerOptions = useMemo(() => {
    const items = usersQuery.data ?? [];
    const options = [
      { value: NONE_OWNER_VALUE, label: t("formDialog.ownerNone") },
      ...items.map((u) => ({
        value: u.id,
        label: u.fullName ? `${u.username} — ${u.fullName}` : u.username,
      })),
    ];
    if (
      initialClass?.ownerUserId &&
      !items.some((u) => u.id === initialClass.ownerUserId)
    ) {
      options.push({
        value: initialClass.ownerUserId,
        label: initialClass.ownerUsername
          ? initialClass.ownerFullName
            ? `${initialClass.ownerUsername} — ${initialClass.ownerFullName}`
            : initialClass.ownerUsername
          : initialClass.ownerUserId,
      });
    }
    return options;
  }, [usersQuery.data, initialClass, t]);

  const statusOptions = useMemo(
    () => [
      { value: "ACTIVE", label: t("statuses.ACTIVE") },
      { value: "INACTIVE", label: t("statuses.INACTIVE") },
    ],
    [t],
  );

  return (
    <form
      onSubmit={handleSubmit((values) => {
        setSubmitError(null);
        mutation.mutate(values);
      })}
      className="flex min-h-0 flex-1 flex-col gap-4"
      noValidate
    >
      <div className="flex min-h-0 flex-1 flex-col gap-3.5 overflow-y-auto pr-1">
        {submitError && (
          <div
            role="alert"
            className="rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
          >
            {submitError}
          </div>
        )}

        <FormField
          label={t("formDialog.codeLabel")}
          required
          error={errors.code?.message}
          hint={t("formDialog.codeHint")}
        >
          {(controlProps) => (
            <Input
              {...controlProps}
              {...register("code", {
                onChange: () => setSubmitError(null),
              })}
              placeholder={t("formDialog.codePlaceholder")}
              autoComplete="off"
              disabled={mutation.isPending}
            />
          )}
        </FormField>

        <FormField label={t("formDialog.nameLabel")} required error={errors.name?.message}>
          {(controlProps) => (
            <Input
              {...controlProps}
              {...register("name", {
                onChange: () => setSubmitError(null),
              })}
              placeholder={t("formDialog.namePlaceholder")}
              disabled={mutation.isPending}
            />
          )}
        </FormField>

        <FormField
          label={t("formDialog.descriptionLabel")}
          error={errors.description?.message}
        >
          {(controlProps) => (
            <Textarea
              {...controlProps}
              {...register("description", {
                onChange: () => setSubmitError(null),
              })}
              placeholder={t("formDialog.descriptionPlaceholder")}
              rows={3}
              disabled={mutation.isPending}
            />
          )}
        </FormField>

        {canListUsers ? (
          <FormField
            label={t("formDialog.ownerLabel")}
            hint={
              usersQuery.isLoading
                ? t("formDialog.ownerLoading")
                : usersQuery.isError
                  ? undefined
                  : t("formDialog.ownerHint")
            }
          >
            {(controlProps) => (
              <div className="flex flex-col gap-2">
                <Controller
                  control={control}
                  name="ownerUserId"
                  render={({ field }) => (
                    <Select
                      {...controlProps}
                      value={field.value || NONE_OWNER_VALUE}
                      onValueChange={field.onChange}
                      options={ownerOptions}
                      disabled={
                        mutation.isPending || usersQuery.isLoading || usersQuery.isError
                      }
                    />
                  )}
                />
                {usersQuery.isError && (
                  <div
                    role="alert"
                    className="flex items-center justify-between gap-2 rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
                  >
                    <span>{t("formDialog.ownerError")}</span>
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      onClick={() => void usersQuery.refetch()}
                    >
                      {common("retry")}
                    </Button>
                  </div>
                )}
              </div>
            )}
          </FormField>
        ) : (
          isEdit && (
            <FormField label={t("formDialog.ownerLabel")}>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  value={initialClass?.ownerUsername ?? t("formDialog.ownerNone")}
                  readOnly
                  disabled
                />
              )}
            </FormField>
          )
        )}

        <FormField
          label={t("formDialog.statusLabel")}
          hint={isEdit ? t("formDialog.statusEditHint") : undefined}
        >
          {(controlProps) => (
            <Controller
              control={control}
              name="status"
              render={({ field }) => (
                <Select
                  {...controlProps}
                  value={field.value}
                  onValueChange={(value) => field.onChange(value as "ACTIVE" | "INACTIVE")}
                  options={statusOptions}
                  disabled={mutation.isPending || isEdit}
                />
              )}
            />
          )}
        </FormField>
      </div>

      <DialogFooter className="shrink-0 pt-2">
        <Button
          type="button"
          variant="secondary"
          disabled={mutation.isPending}
          onClick={onCancel}
        >
          {t("formDialog.cancel")}
        </Button>
        <Button type="submit" loading={mutation.isPending}>
          {isEdit ? t("formDialog.submitEdit") : t("formDialog.submitCreate")}
        </Button>
      </DialogFooter>
    </form>
  );
}
