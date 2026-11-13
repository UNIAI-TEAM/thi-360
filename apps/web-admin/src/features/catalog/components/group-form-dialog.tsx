"use client";

import { zodResolver } from "@hookform/resolvers/zod";
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
  Select,
  Textarea,
} from "@thi360/ui";
import { Lock } from "lucide-react";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import type {
  CatalogGroupResponse,
  CatalogStatus,
  CreateCatalogGroupRequest,
  UpdateCatalogGroupRequest,
} from "../api";
import { createGroupFormSchema, type GroupFormValues } from "../schemas";
import {
  getCatalogErrorCode,
  getCatalogFieldErrors,
  resolveCatalogErrorMessage,
} from "../utils";
export type GroupFormDialogProps = {
  open: boolean;
  mode: "create" | "edit";
  group: CatalogGroupResponse | null;
  onOpenChange: (open: boolean) => void;
  onSubmit: (
    payload:
      | { mode: "create"; data: CreateCatalogGroupRequest }
      | { mode: "edit"; id: string; data: UpdateCatalogGroupRequest },
  ) => Promise<void>;
};

export function GroupFormDialog({
  open,
  mode,
  group,
  onOpenChange,
  onSubmit,
}: GroupFormDialogProps) {
  const t = useTranslations("catalog");
  const [submitting, setSubmitting] = useState(false);

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (submitting && !next) return;
        if (!next) setSubmitting(false);
        onOpenChange(next);
      }}
    >
      {open && (
        <DialogContent closeLabel={t("close")} size="md">
          <DialogHeader>
            <DialogTitle>
              {mode === "create" ? t("groupDialog.createTitle") : t("groupDialog.editTitle")}
            </DialogTitle>
            <DialogDescription>{t("groupDialog.subtitle")}</DialogDescription>
          </DialogHeader>
          <GroupFormBody
            key={`${mode}-${group?.id ?? "new"}`}
            mode={mode}
            group={group}
            onCancel={() => onOpenChange(false)}
            onSubmittingChange={setSubmitting}
            onSubmit={onSubmit}
          />
        </DialogContent>
      )}
    </Dialog>
  );
}

function GroupFormBody({
  mode,
  group,
  onCancel,
  onSubmittingChange,
  onSubmit,
}: {
  mode: "create" | "edit";
  group: CatalogGroupResponse | null;
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onSubmit: GroupFormDialogProps["onSubmit"];
}) {
  const t = useTranslations("catalog");
  const [codeLocked, setCodeLocked] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const initialStatus = mode === "edit" ? group?.status : undefined;

  const schema = useMemo(
    () =>
      createGroupFormSchema(initialStatus, {
        codeRequired: t("validation.codeRequired"),
        codePattern: t("validation.codePattern"),
        nameViRequired: t("validation.nameViRequired"),
        displayOrderInvalid: t("validation.displayOrderInvalid"),
        reasonRequired: t("validation.reasonRequired"),
      }),
    [initialStatus, t],
  );

  const {
    control,
    register,
    handleSubmit,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<GroupFormValues>({
    resolver: zodResolver(schema),
    defaultValues:
      mode === "edit" && group
        ? {
            code: group.code,
            nameVi: group.nameVi,
            nameEn: group.nameEn ?? "",
            status: group.status,
            description: group.description ?? "",
            reason: "",
          }
        : {
            code: "",
            nameVi: "",
            nameEn: "",
            status: "ACTIVE",
            description: "",
            reason: "",
          },
  });

  const watchedStatus = useWatch({ control, name: "status" });
  const showReason = mode === "edit" && group?.status === "ACTIVE" && watchedStatus === "INACTIVE";
  const statusOptions = useMemo(
    () => [
      { value: "ACTIVE" satisfies CatalogStatus, label: t("statuses.ACTIVE") },
      { value: "INACTIVE" satisfies CatalogStatus, label: t("statuses.INACTIVE") },
    ],
    [t],
  );

  async function handleFormSubmit(values: GroupFormValues) {
    setServerError(null);
    onSubmittingChange(true);
    const trimmedCode = values.code.trim();
    const trimmedNameVi = values.nameVi.trim();
    const trimmedNameEn = values.nameEn.trim();
    const trimmedDescription = values.description.trim();
    const trimmedReason = values.reason.trim();

    try {
      if (mode === "create") {
        await onSubmit({
          mode: "create",
          data: {
            code: trimmedCode,
            nameVi: trimmedNameVi,
            ...(trimmedNameEn ? { nameEn: trimmedNameEn } : {}),
            ...(trimmedDescription ? { description: trimmedDescription } : {}),
            status: values.status,
            kind: "SIMPLE",
          },
        });
      } else if (group) {
        const shouldSendCode = !codeLocked && trimmedCode !== group.code;
        await onSubmit({
          mode: "edit",
          id: group.id,
          data: {
            ...(shouldSendCode ? { code: trimmedCode } : {}),
            nameVi: trimmedNameVi,
            nameEn: trimmedNameEn,
            description: trimmedDescription,
            status: values.status,
            ...(showReason ? { reason: trimmedReason } : {}),
            version: group.version,
          },
        });
      }
    } catch (error) {
      const code = getCatalogErrorCode(error);
      const translated = resolveCatalogErrorMessage(error, (key) => t(`errorCodes.${key}`));

      if (code === "CATALOG_CODE_LOCKED") {
        setCodeLocked(true);
        if (group) {
          setValue("code", group.code, { shouldValidate: false });
        }
        setError("code", { type: "server", message: translated });
        return;
      }

      if (code === "CATALOG_CODE_TAKEN") {
        setError("code", { type: "server", message: translated });
        return;
      }

      const fieldErrors = getCatalogFieldErrors(error);
      let mappedToField = false;
      for (const fe of fieldErrors) {
        if (
          fe.field === "code" ||
          fe.field === "nameVi" ||
          fe.field === "nameEn" ||
          fe.field === "status" ||
          fe.field === "description" ||
          fe.field === "reason"
        ) {
          setError(fe.field, { type: "server", message: translated });
          mappedToField = true;
        }
      }
      if (!mappedToField) {
        setServerError(translated);
      }
    } finally {
      onSubmittingChange(false);
    }
  }

  return (
    <form onSubmit={handleSubmit(handleFormSubmit)} noValidate className="flex flex-col gap-4">
          {serverError && (
            <div
              role="alert"
              className="rounded-md border border-danger bg-danger-bg px-3.5 py-2.5 text-field-label font-medium text-danger"
            >
              {serverError}
            </div>
          )}

          <FormField
            label={t("groupDialog.codeLabel")}
            required
            error={errors.code?.message}
            hint={codeLocked ? t("groupDialog.codeLockedHint") : t("groupDialog.codeHint")}
          >
            {(fieldProps) => (
              <Input
                {...fieldProps}
                {...register("code")}
                disabled={isSubmitting || codeLocked}
                leadingIcon={codeLocked ? <Lock aria-hidden /> : undefined}
              />
            )}
          </FormField>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <FormField
              label={t("groupDialog.nameViLabel")}
              required
              error={errors.nameVi?.message}
            >
              {(fieldProps) => (
                <Input {...fieldProps} {...register("nameVi")} disabled={isSubmitting} />
              )}
            </FormField>

            <FormField label={t("groupDialog.nameEnLabel")} error={errors.nameEn?.message}>
              {(fieldProps) => (
                <Input
                  {...fieldProps}
                  {...register("nameEn")}
                  placeholder={t("groupDialog.nameEnPlaceholder")}
                  disabled={isSubmitting}
                />
              )}
            </FormField>
          </div>

          <FormField
            label={t("groupDialog.statusLabel")}
            required
            error={errors.status?.message}
          >
            {(fieldProps) => (
              <Controller
                control={control}
                name="status"
                render={({ field }) => (
                  <Select
                    id={fieldProps.id}
                    aria-invalid={fieldProps["aria-invalid"]}
                    aria-required={fieldProps["aria-required"]}
                    aria-describedby={fieldProps["aria-describedby"]}
                    options={statusOptions}
                    value={field.value}
                    onValueChange={(value) => field.onChange(value as CatalogStatus)}
                    disabled={isSubmitting}
                  />
                )}
              />
            )}
          </FormField>

          {showReason && (
            <FormField
              label={t("groupDialog.reasonLabel")}
              required
              hint={t("groupDialog.reasonHint")}
              error={errors.reason?.message}
            >
              {(fieldProps) => (
                <Textarea
                  {...fieldProps}
                  {...register("reason")}
                  rows={2}
                  disabled={isSubmitting}
                />
              )}
            </FormField>
          )}

          <FormField label={t("groupDialog.descriptionLabel")} error={errors.description?.message}>
            {(fieldProps) => (
              <Textarea
                {...fieldProps}
                {...register("description")}
                rows={2}
                disabled={isSubmitting}
              />
            )}
          </FormField>

          <DialogFooter className="pt-1">
            <Button
              variant="secondary"
              onClick={onCancel}
              disabled={isSubmitting}
            >
              {t("cancel")}
            </Button>
            <Button type="submit" variant="primary" loading={isSubmitting}>
              {t("groupDialog.save")}
            </Button>
          </DialogFooter>
        </form>
  );
}
