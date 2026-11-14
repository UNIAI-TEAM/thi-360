"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import {
  Button,
  Chip,
  cn,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
  IconButton,
  Input,
  Select,
  Textarea,
} from "@thi360/ui";
import { Lock, Plus, X } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
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
  pickName,
  resolveCatalogErrorMessage,
} from "../utils";

export type GroupFormDialogProps = {
  open: boolean;
  mode: "create" | "edit";
  group: CatalogGroupResponse | null;
  allGroups: CatalogGroupResponse[];
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
  allGroups,
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
        <DialogContent closeLabel={t("close")} size="lg">
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
            allGroups={allGroups}
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
  allGroups,
  onCancel,
  onSubmittingChange,
  onSubmit,
}: {
  mode: "create" | "edit";
  group: CatalogGroupResponse | null;
  allGroups: CatalogGroupResponse[];
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onSubmit: GroupFormDialogProps["onSubmit"];
}) {
  const t = useTranslations("catalog");
  const locale = useLocale();
  const [codeLocked, setCodeLocked] = useState(false);
  const [levelsLocked, setLevelsLocked] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [refGroupPickerValue, setRefGroupPickerValue] = useState("");
  const initialStatus = mode === "edit" ? group?.status : undefined;

  const schema = useMemo(
    () =>
      createGroupFormSchema(initialStatus, {
        codeRequired: t("validation.codeRequired"),
        codePattern: t("validation.codePattern"),
        nameViRequired: t("validation.nameViRequired"),
        displayOrderInvalid: t("validation.displayOrderInvalid"),
        reasonRequired: t("validation.reasonRequired"),
        levelsCount: t("validation.levelsCount"),
        levelsRequired: t("validation.levelsRequired"),
        levelsDuplicate: t("validation.levelsDuplicate"),
      }),
    [initialStatus, t],
  );

  const defaultLevels = useMemo(
    () => (group?.levels && group.levels.length >= 2 ? [...group.levels] : ["", ""]),
    [group],
  );
  const defaultRefGroupIds = useMemo(
    () => (group?.refGroups ? group.refGroups.map((r) => r.refGroupId) : []),
    [group],
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
            kind: group.kind === "TREE" ? "TREE" : "SIMPLE",
            levels: defaultLevels,
            refGroupIds: defaultRefGroupIds,
            description: group.description ?? "",
            reason: "",
          }
        : {
            code: "",
            nameVi: "",
            nameEn: "",
            status: "ACTIVE",
            kind: "SIMPLE",
            levels: ["", ""],
            refGroupIds: [],
            description: "",
            reason: "",
          },
  });

  const watchedStatus = useWatch({ control, name: "status" });
  const watchedKind = useWatch({ control, name: "kind" });
  const watchedLevels = useWatch({ control, name: "levels" });
  const watchedRefGroupIds = useWatch({ control, name: "refGroupIds" });

  const showReason = mode === "edit" && group?.status === "ACTIVE" && watchedStatus === "INACTIVE";
  const effectiveLevelsLocked =
    levelsLocked || (mode === "edit" && Boolean(group && group.itemCount > 0));

  const simpleGroupLookup = useMemo(() => {
    const map = new Map<string, CatalogGroupResponse>();
    for (const candidate of allGroups) {
      if (candidate.kind === "SIMPLE" && candidate.id !== group?.id) {
        map.set(candidate.id, candidate);
      }
    }
    return map;
  }, [allGroups, group?.id]);

  const defaultRefGroupIdSet = useMemo(
    () => new Set((group?.refGroups ?? []).filter((r) => r.isDefault).map((r) => r.refGroupId)),
    [group?.refGroups],
  );

  const linkedCountByRefGroupId = useMemo(() => {
    const map = new Map<string, number>();
    for (const ref of group?.refGroups ?? []) {
      map.set(ref.refGroupId, ref.linkedItemCount);
    }
    return map;
  }, [group?.refGroups]);

  const availableSimpleGroupOptions = useMemo(() => {
    const selectedSet = new Set(watchedRefGroupIds);
    return allGroups
      .filter(
        (candidate) =>
          candidate.kind === "SIMPLE" &&
          candidate.status === "ACTIVE" &&
          candidate.id !== group?.id &&
          !selectedSet.has(candidate.id),
      )
      .map((candidate) => ({
        value: candidate.id,
        label: `${pickName(candidate, locale)} (${candidate.code})`,
      }));
  }, [allGroups, group?.id, locale, watchedRefGroupIds]);

  const statusOptions = useMemo(
    () => [
      { value: "ACTIVE" satisfies CatalogStatus, label: t("statuses.ACTIVE") },
      { value: "INACTIVE" satisfies CatalogStatus, label: t("statuses.INACTIVE") },
    ],
    [t],
  );

  function handleLevelChange(index: number, nextValue: string) {
    const next = [...watchedLevels];
    next[index] = nextValue;
    setValue("levels", next, { shouldValidate: true });
  }

  function handleAddLevel() {
    if (effectiveLevelsLocked || watchedLevels.length >= 6) return;
    setValue("levels", [...watchedLevels, ""], { shouldValidate: false });
  }

  function handleRemoveLevel(index: number) {
    if (effectiveLevelsLocked || watchedLevels.length <= 2) return;
    setValue(
      "levels",
      watchedLevels.filter((_, idx) => idx !== index),
      { shouldValidate: true },
    );
  }

  function handleAddRefGroup(refGroupId: string) {
    if (!refGroupId || watchedRefGroupIds.includes(refGroupId)) return;
    setValue("refGroupIds", [...watchedRefGroupIds, refGroupId], { shouldValidate: true });
    setRefGroupPickerValue("");
  }

  function handleRemoveRefGroup(refGroupId: string) {
    if (defaultRefGroupIdSet.has(refGroupId)) return;
    if ((linkedCountByRefGroupId.get(refGroupId) ?? 0) > 0) return;
    setValue(
      "refGroupIds",
      watchedRefGroupIds.filter((id) => id !== refGroupId),
      { shouldValidate: true },
    );
  }
  async function handleFormSubmit(values: GroupFormValues) {
    setServerError(null);
    onSubmittingChange(true);
    const trimmedCode = values.code.trim();
    const trimmedNameVi = values.nameVi.trim();
    const trimmedNameEn = values.nameEn.trim();
    const trimmedDescription = values.description.trim();
    const trimmedReason = values.reason.trim();
    const trimmedLevels = values.levels.map((level) => level.trim());

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
            kind: values.kind,
            ...(values.kind === "TREE"
              ? {
                  levels: trimmedLevels,
                  ...(values.refGroupIds.length > 0 ? { refGroupIds: values.refGroupIds } : {}),
                }
              : {}),
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
            ...(group.kind === "TREE"
              ? {
                  levels: trimmedLevels,
                  refGroupIds: values.refGroupIds,
                }
              : {}),
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

      if (code === "CATALOG_LEVELS_LOCKED") {
        setLevelsLocked(true);
        setError("levels", { type: "server", message: translated });
        return;
      }

      if (code === "CATALOG_REF_GROUP_IN_USE" || code === "CATALOG_REF_GROUP_DEFAULT") {
        setError("refGroupIds", { type: "server", message: translated });
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
          fe.field === "reason" ||
          fe.field === "levels" ||
          fe.field === "refGroupIds"
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

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <FormField
              label={t("groupDialog.kindLabel")}
              required
              hint={mode === "edit" ? t("groupDialog.kindFixedHint") : undefined}
            >
              {() =>
                mode === "create" ? (
                  <div
                    role="group"
                    aria-label={t("groupDialog.kindLabel")}
                    className="grid h-10 grid-cols-2 gap-1 rounded-md bg-subtle p-1"
                  >
                    <button
                      type="button"
                      aria-pressed={watchedKind === "SIMPLE"}
                      disabled={isSubmitting}
                      onClick={() => setValue("kind", "SIMPLE", { shouldValidate: true })}
                      className={cn(
                        "rounded-sm text-field-label font-medium transition-colors",
                        watchedKind === "SIMPLE"
                          ? "bg-surface font-semibold text-primary-text shadow-xs"
                          : "text-text-secondary hover:text-text",
                      )}
                    >
                      {t("groupDialog.kindSimple")}
                    </button>
                    <button
                      type="button"
                      aria-pressed={watchedKind === "TREE"}
                      disabled={isSubmitting}
                      onClick={() => setValue("kind", "TREE", { shouldValidate: true })}
                      className={cn(
                        "rounded-sm text-field-label font-medium transition-colors",
                        watchedKind === "TREE"
                          ? "bg-surface font-semibold text-primary-text shadow-xs"
                          : "text-text-secondary hover:text-text",
                      )}
                    >
                      {t("groupDialog.kindTree")}
                    </button>
                  </div>
                ) : (
                  <div className="flex h-10 items-center rounded-md border border-border bg-page px-3 text-field-label font-medium text-text-secondary">
                    {t(`kinds.${group?.kind ?? "SIMPLE"}`)}
                  </div>
                )
              }
            </FormField>

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
          </div>

          {watchedKind === "TREE" && (
            <>
              <div className="flex flex-col gap-2">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-field-label font-medium text-text-secondary">
                    {t("groupDialog.levelsLabel")}
                    <span aria-hidden className="ml-0.5 text-danger">
                      *
                    </span>
                  </span>
                  {!effectiveLevelsLocked && watchedLevels.length < 6 && (
                    <Button
                      type="button"
                      variant="soft"
                      size="sm"
                      onClick={handleAddLevel}
                      disabled={isSubmitting}
                    >
                      <Plus aria-hidden className="mr-1 size-3.5" />
                      {t("groupDialog.addLevel")}
                    </Button>
                  )}
                </div>

                <div className="flex flex-col gap-2 rounded-md border border-border bg-page p-3">
                  {watchedLevels.map((levelName, index) => (
                    <div key={index} className="flex items-center gap-2">
                      <span className="w-16 shrink-0 text-caption font-medium text-text-muted">
                        {t("groupDialog.levelIndexLabel", { index: index + 1 })}
                      </span>
                      <Input
                        aria-label={t("groupDialog.levelNameAria", { index: index + 1 })}
                        value={levelName}
                        placeholder={t("groupDialog.levelPlaceholder")}
                        onChange={(event) => handleLevelChange(index, event.target.value)}
                        disabled={isSubmitting}
                      />
                      {!effectiveLevelsLocked && watchedLevels.length > 2 && (
                        <IconButton
                          icon={<X className="size-4" />}
                          tone="danger"
                          aria-label={t("groupDialog.removeLevelLabel", { index: index + 1 })}
                          tooltip={t("groupDialog.removeLevelLabel", { index: index + 1 })}
                          onClick={() => handleRemoveLevel(index)}
                          disabled={isSubmitting}
                          className="size-9 shrink-0"
                        />
                      )}
                    </div>
                  ))}
                </div>

                {effectiveLevelsLocked && (
                  <p className="text-caption text-text-muted">{t("groupDialog.levelsLockedHint")}</p>
                )}
                {errors.levels?.message && (
                  <p role="alert" className="text-caption font-medium text-danger">
                    {errors.levels.message}
                  </p>
                )}
              </div>

              <FormField
                label={t("groupDialog.refGroupsLabel")}
                hint={
                  availableSimpleGroupOptions.length === 0 && watchedRefGroupIds.length === 0
                    ? t("groupDialog.refGroupsEmpty")
                    : t("groupDialog.refGroupsHint")
                }
                error={errors.refGroupIds?.message}
              >
                {(fieldProps) => (
                  <div className="flex flex-col gap-2">
                    {watchedRefGroupIds.length > 0 && (
                      <div className="flex flex-wrap gap-1.5">
                        {watchedRefGroupIds.map((refGroupId) => {
                          const refGroup = simpleGroupLookup.get(refGroupId);
                          const refName = refGroup ? pickName(refGroup, locale) : refGroupId;
                          const isDefault = defaultRefGroupIdSet.has(refGroupId);
                          const linkedCount = linkedCountByRefGroupId.get(refGroupId) ?? 0;
                          const locked = isDefault || linkedCount > 0;
                          return (
                            <Chip key={refGroupId} variant="tag" className="gap-1.5">
                              {isDefault && <Lock aria-hidden className="size-3" />}
                              <span>{refName}</span>
                              {!locked && (
                                <button
                                  type="button"
                                  aria-label={t("groupDialog.removeRefGroupLabel", {
                                    name: refName,
                                  })}
                                  onClick={() => handleRemoveRefGroup(refGroupId)}
                                  className="inline-flex size-4 items-center justify-center rounded-full hover:bg-subtle"
                                >
                                  <X aria-hidden className="size-3" />
                                </button>
                              )}
                            </Chip>
                          );
                        })}
                      </div>
                    )}
                    {availableSimpleGroupOptions.length > 0 && (
                      <Select
                        id={fieldProps.id}
                        options={availableSimpleGroupOptions}
                        value={refGroupPickerValue}
                        placeholder={t("groupDialog.addRefGroupPlaceholder")}
                        onValueChange={handleAddRefGroup}
                        disabled={isSubmitting}
                      />
                    )}
                  </div>
                )}
              </FormField>
            </>
          )}
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
