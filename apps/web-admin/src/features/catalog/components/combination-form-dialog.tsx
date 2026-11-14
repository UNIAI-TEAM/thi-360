"use client";

import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
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
  Skeleton,
  Textarea,
} from "@thi360/ui";
import { AlertTriangle, ArrowDown, ArrowUp, GripVertical, Lock, TriangleAlert, X } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useCallback, useMemo, useState, type CSSProperties } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import {
  catalogQueryKeys,
  type CatalogApi,
  type CatalogGroupResponse,
  type CatalogItemResponse,
  type CatalogStatus,
  type CreateCatalogItemRequest,
  type UpdateCatalogItemRequest,
} from "../api";
import { useCatalogDndAccessibility } from "../dnd-accessibility";
import { createCombinationFormSchema, type CombinationFormValues } from "../schemas";
import {
  getCatalogErrorCode,
  getCatalogFieldErrors,
  pickName,
  resolveCatalogErrorMessage,
  resolveCatalogUsageTypeKey,
} from "../utils";

export type CombinationFormDialogProps = {
  open: boolean;
  mode: "create" | "edit";
  group: CatalogGroupResponse;
  item: CatalogItemResponse | null;
  api: CatalogApi;
  onOpenChange: (open: boolean) => void;
  onSubmit: (
    payload:
      | { mode: "create"; groupId: string; data: CreateCatalogItemRequest; addAnother: boolean }
      | { mode: "edit"; itemId: string; data: UpdateCatalogItemRequest },
  ) => Promise<void>;
};

interface SubjectOptionEntry {
  id: string;
  code: string;
  nameVi: string;
  nameEn?: string;
  inactive?: boolean;
}

export function CombinationFormDialog({
  open,
  mode,
  group,
  item,
  api,
  onOpenChange,
  onSubmit,
}: CombinationFormDialogProps) {
  const t = useTranslations("catalog");
  const locale = useLocale();
  const [submitting, setSubmitting] = useState(false);

  const currentVersionNo = item?.combination?.versionNo ?? 1;

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
              {mode === "create"
                ? t("combinationDialog.createTitle")
                : t("combinationDialog.editTitle")}
            </DialogTitle>
            <DialogDescription>
              {mode === "edit"
                ? t("combinationDialog.currentVersionMeta", {
                    groupName: pickName(group, locale),
                    groupCode: group.code,
                    version: currentVersionNo,
                  })
                : `${pickName(group, locale)} (${group.code})`}
            </DialogDescription>
          </DialogHeader>
          <CombinationFormBody
            key={`${mode}-${group.id}-${item?.id ?? "new"}`}
            mode={mode}
            group={group}
            item={item}
            api={api}
            onCancel={() => onOpenChange(false)}
            onSubmittingChange={setSubmitting}
            onSubmit={onSubmit}
          />
        </DialogContent>
      )}
    </Dialog>
  );
}

function CombinationFormBody({
  mode,
  group,
  item,
  api,
  onCancel,
  onSubmittingChange,
  onSubmit,
}: {
  mode: "create" | "edit";
  group: CatalogGroupResponse;
  item: CatalogItemResponse | null;
  api: CatalogApi;
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onSubmit: CombinationFormDialogProps["onSubmit"];
}) {
  const t = useTranslations("catalog");
  const commonT = useTranslations("common");
  const locale = useLocale();
  const [codeLocked, setCodeLocked] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [subjectPickerValue, setSubjectPickerValue] = useState("");

  const examsQuery = useQuery({
    queryKey: catalogQueryKeys.options("EXAM"),
    queryFn: () => api.getOptions("EXAM"),
  });

  const subjectsQuery = useQuery({
    queryKey: catalogQueryKeys.options("SUBJECT"),
    queryFn: () => api.getOptions("SUBJECT"),
  });

  const usagesQuery = useQuery({
    queryKey: catalogQueryKeys.itemUsages(item?.id ?? "none"),
    queryFn: () => api.getItemUsages(item!.id),
    enabled: mode === "edit" && Boolean(item?.id),
  });

  const initialCombo = mode === "edit" ? item?.combination : undefined;
  const initialExamId = initialCombo?.examItemId ?? "";
  const initialSubjectIds = useMemo(
    () =>
      initialCombo
        ? [...initialCombo.subjects]
            .sort((a, b) => a.position - b.position)
            .map((subject) => subject.subjectItemId)
        : [],
    [initialCombo],
  );

  const initialStatus = mode === "edit" ? item?.status : undefined;
  const isSystemItem = mode === "edit" && Boolean(item?.system);
  const effectiveCodeLocked = codeLocked || isSystemItem;

  const schema = useMemo(
    () =>
      createCombinationFormSchema(initialStatus, {
        codeRequired: t("validation.codeRequired"),
        codePattern: t("validation.codePattern"),
        nameViRequired: t("validation.nameViRequired"),
        displayOrderInvalid: t("validation.displayOrderInvalid"),
        reasonRequired: t("validation.reasonRequired"),
        examRequired: t("validation.examRequired"),
        subjectsRequired: t("validation.subjectsRequired"),
      }),
    [initialStatus, t],
  );

  const {
    control,
    register,
    handleSubmit,
    reset,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CombinationFormValues>({
    resolver: zodResolver(schema),
    defaultValues:
      mode === "edit" && item
        ? {
            code: item.code,
            displayOrder: String(item.displayOrder),
            nameVi: item.nameVi,
            nameEn: item.nameEn ?? "",
            status: item.status,
            examItemId: initialExamId,
            subjectItemIds: initialSubjectIds,
            sourceNote: initialCombo?.sourceNote ?? "",
            description: item.description ?? "",
            reason: "",
          }
        : {
            code: "",
            displayOrder: "",
            nameVi: "",
            nameEn: "",
            status: "ACTIVE",
            examItemId: "",
            subjectItemIds: [],
            sourceNote: "",
            description: "",
            reason: "",
          },
  });

  const watchedStatus = useWatch({ control, name: "status" });
  const watchedExamId = useWatch({ control, name: "examItemId" });
  const watchedSubjectIds = useWatch({ control, name: "subjectItemIds" });

  const showReason = mode === "edit" && item?.status === "ACTIVE" && watchedStatus === "INACTIVE";
  const stopBlocked =
    showReason &&
    (usagesQuery.isPending || usagesQuery.isError || Boolean(usagesQuery.data?.blocking));

  const compositionChanged =
    mode === "edit" &&
    (watchedExamId !== initialExamId ||
      watchedSubjectIds.length !== initialSubjectIds.length ||
      watchedSubjectIds.some((id, index) => id !== initialSubjectIds[index]));

  const currentVersionNo = initialCombo?.versionNo ?? 1;
  const isReferenced = (usagesQuery.data?.totalCount ?? 0) > 0;
  const willCreateNewVersion = mode === "edit" && compositionChanged && isReferenced;

  const examOptions = useMemo(() => {
    const active = (examsQuery.data ?? []).map((opt) => ({
      value: opt.id,
      label: `${opt.code} — ${pickName(opt, locale)}`,
    }));
    if (
      initialCombo &&
      initialCombo.examItemId &&
      !active.some((opt) => opt.value === initialCombo.examItemId)
    ) {
      const inactiveSuffix =
        initialCombo.examStatus === "INACTIVE" ? ` (${t("statuses.INACTIVE")})` : "";
      active.unshift({
        value: initialCombo.examItemId,
        label: `${initialCombo.examCode} — ${pickName(
          { nameVi: initialCombo.examNameVi, nameEn: initialCombo.examNameEn },
          locale,
        )}${inactiveSuffix}`,
      });
    }
    return active;
  }, [examsQuery.data, initialCombo, locale, t]);

  const subjectLookup = useMemo(() => {
    const map = new Map<string, SubjectOptionEntry>();
    for (const opt of subjectsQuery.data ?? []) {
      map.set(opt.id, {
        id: opt.id,
        code: opt.code,
        nameVi: opt.nameVi,
        nameEn: opt.nameEn,
      });
    }
    for (const sub of initialCombo?.subjects ?? []) {
      if (!map.has(sub.subjectItemId)) {
        map.set(sub.subjectItemId, {
          id: sub.subjectItemId,
          code: sub.code,
          nameVi: sub.nameVi,
          nameEn: sub.nameEn,
          inactive: sub.status === "INACTIVE",
        });
      }
    }
    return map;
  }, [initialCombo?.subjects, subjectsQuery.data]);

  const availableSubjects = useMemo(() => {
    const selectedSet = new Set(watchedSubjectIds);
    return (subjectsQuery.data ?? [])
      .filter((opt) => !selectedSet.has(opt.id))
      .map((opt) => ({
        value: opt.id,
        label: `${opt.code} — ${pickName(opt, locale)}`,
      }));
  }, [locale, subjectsQuery.data, watchedSubjectIds]);

  const statusOptions = useMemo(
    () => [
      { value: "ACTIVE" satisfies CatalogStatus, label: t("statuses.ACTIVE") },
      { value: "INACTIVE" satisfies CatalogStatus, label: t("statuses.INACTIVE") },
    ],
    [t],
  );

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const resolveDndName = useCallback(
    (id: string) => {
      const entry = subjectLookup.get(id);
      return entry ? pickName(entry, locale) : id;
    },
    [subjectLookup, locale],
  );
  const dndAccessibility = useCatalogDndAccessibility(resolveDndName);

  function handleSubjectDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = watchedSubjectIds.indexOf(String(active.id));
    const newIndex = watchedSubjectIds.indexOf(String(over.id));
    if (oldIndex === -1 || newIndex === -1) return;
    setValue("subjectItemIds", arrayMove(watchedSubjectIds, oldIndex, newIndex), {
      shouldValidate: true,
    });
  }

  function handleMoveSubject(index: number, direction: -1 | 1) {
    const target = index + direction;
    if (target < 0 || target >= watchedSubjectIds.length) return;
    setValue("subjectItemIds", arrayMove(watchedSubjectIds, index, target), {
      shouldValidate: true,
    });
  }

  function handleRemoveSubject(subjectId: string) {
    setValue(
      "subjectItemIds",
      watchedSubjectIds.filter((id) => id !== subjectId),
      { shouldValidate: true },
    );
  }

  function handleAddSubject(subjectId: string) {
    if (!subjectId || watchedSubjectIds.includes(subjectId)) return;
    setValue("subjectItemIds", [...watchedSubjectIds, subjectId], { shouldValidate: true });
    setSubjectPickerValue("");
  }

  async function handleFormSubmit(values: CombinationFormValues, addAnother: boolean) {
    if (stopBlocked) return;
    setServerError(null);
    onSubmittingChange(true);
    const trimmedCode = values.code.trim();
    const trimmedNameVi = values.nameVi.trim();
    const trimmedNameEn = values.nameEn.trim();
    const trimmedSourceNote = values.sourceNote.trim();
    const trimmedDescription = values.description.trim();
    const trimmedDisplayOrder = values.displayOrder.trim();
    const trimmedReason = values.reason.trim();
    const parsedOrder = trimmedDisplayOrder ? Number(trimmedDisplayOrder) : undefined;

    try {
      if (mode === "create") {
        await onSubmit({
          mode: "create",
          groupId: group.id,
          addAnother,
          data: {
            code: trimmedCode,
            nameVi: trimmedNameVi,
            ...(trimmedNameEn ? { nameEn: trimmedNameEn } : {}),
            ...(trimmedDescription ? { description: trimmedDescription } : {}),
            ...(parsedOrder !== undefined ? { displayOrder: parsedOrder } : {}),
            status: values.status,
            examItemId: values.examItemId,
            subjectItemIds: values.subjectItemIds,
            ...(trimmedSourceNote ? { sourceNote: trimmedSourceNote } : {}),
          },
        });
        if (addAnother) {
          reset({
            code: "",
            displayOrder: "",
            nameVi: "",
            nameEn: "",
            status: "ACTIVE",
            examItemId: "",
            subjectItemIds: [],
            sourceNote: "",
            description: "",
            reason: "",
          });
        }
      } else if (item) {
        const shouldSendCode = !effectiveCodeLocked && trimmedCode !== item.code;
        await onSubmit({
          mode: "edit",
          itemId: item.id,
          data: {
            ...(shouldSendCode ? { code: trimmedCode } : {}),
            nameVi: trimmedNameVi,
            nameEn: trimmedNameEn,
            description: trimmedDescription,
            ...(parsedOrder !== undefined ? { displayOrder: parsedOrder } : {}),
            status: values.status,
            ...(showReason ? { reason: trimmedReason } : {}),
            examItemId: values.examItemId,
            subjectItemIds: values.subjectItemIds,
            sourceNote: trimmedSourceNote,
            version: item.version,
          },
        });
      }
    } catch (error) {
      const code = getCatalogErrorCode(error);
      const translated = resolveCatalogErrorMessage(error, (key) => t(`errorCodes.${key}`));

      if (code === "CATALOG_CODE_LOCKED") {
        setCodeLocked(true);
        if (item) {
          setValue("code", item.code, { shouldValidate: false });
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
          fe.field === "displayOrder" ||
          fe.field === "nameVi" ||
          fe.field === "nameEn" ||
          fe.field === "status" ||
          fe.field === "examItemId" ||
          fe.field === "subjectItemIds" ||
          fe.field === "sourceNote" ||
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
    <form
      onSubmit={handleSubmit((values) => handleFormSubmit(values, false))}
      noValidate
      className="flex flex-col gap-4"
    >
      {serverError && (
        <div
          role="alert"
          className="rounded-md border border-danger bg-danger-bg px-3.5 py-2.5 text-field-label font-medium text-danger"
        >
          {serverError}
        </div>
      )}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <FormField
          label={t("itemDialog.codeLabel")}
          required
          error={errors.code?.message}
          hint={
            effectiveCodeLocked
              ? t("itemDialog.codeLockedHint")
              : t("combinationDialog.codeConventionHint")
          }
        >
          {(fieldProps) => (
            <Input
              {...fieldProps}
              {...register("code")}
              disabled={isSubmitting || effectiveCodeLocked}
              leadingIcon={effectiveCodeLocked ? <Lock aria-hidden /> : undefined}
            />
          )}
        </FormField>

        <FormField
          label={t("itemDialog.nameViLabel")}
          required
          error={errors.nameVi?.message}
        >
          {(fieldProps) => (
            <Input {...fieldProps} {...register("nameVi")} disabled={isSubmitting} />
          )}
        </FormField>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <FormField label={t("itemDialog.nameEnLabel")} error={errors.nameEn?.message}>
          {(fieldProps) => (
            <Input
              {...fieldProps}
              {...register("nameEn")}
              placeholder={t("itemDialog.nameEnPlaceholder")}
              disabled={isSubmitting}
            />
          )}
        </FormField>

        <FormField
          label={t("itemDialog.displayOrderLabel")}
          error={errors.displayOrder?.message}
          hint={t("itemDialog.displayOrderHint")}
        >
          {(fieldProps) => (
            <Input
              {...fieldProps}
              {...register("displayOrder")}
              inputMode="numeric"
              disabled={isSubmitting}
            />
          )}
        </FormField>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <FormField
          label={t("combinationDialog.examLabel")}
          required
          error={errors.examItemId?.message}
          hint={
            !examsQuery.isPending && !examsQuery.isError && examOptions.length === 0
              ? t("combinationDialog.noActiveExams")
              : undefined
          }
        >
          {(fieldProps) =>
            examsQuery.isPending ? (
              <Skeleton className="h-10 w-full" />
            ) : examsQuery.isError ? (
              <div
                role="alert"
                className="flex items-center justify-between gap-2 rounded-md border border-danger bg-danger-bg px-3 py-2 text-field-label text-danger"
              >
                <span className="flex items-center gap-1.5">
                  <TriangleAlert aria-hidden="true" className="size-4 shrink-0" />
                  {t("combinationDialog.optionsLoadError")}
                </span>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => void examsQuery.refetch()}
                >
                  {commonT("retry")}
                </Button>
              </div>
            ) : (
              <Controller
                control={control}
                name="examItemId"
                render={({ field }) => (
                  <Select
                    id={fieldProps.id}
                    aria-invalid={fieldProps["aria-invalid"]}
                    aria-required={fieldProps["aria-required"]}
                    aria-describedby={fieldProps["aria-describedby"]}
                    options={examOptions}
                    value={field.value}
                    placeholder={t("combinationDialog.examPlaceholder")}
                    onValueChange={field.onChange}
                    disabled={isSubmitting || examOptions.length === 0}
                  />
                )}
              />
            )
          }
        </FormField>

        <FormField
          label={t("itemDialog.statusLabel")}
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

      <fieldset className="flex flex-col gap-2">
        <legend className="text-field-label font-medium text-text-secondary">
          {t("combinationDialog.subjectsLabel")}
          <span aria-hidden className="ml-0.5 text-danger">
            *
          </span>
        </legend>

        {subjectsQuery.isPending ? (
          <Skeleton className="h-24 w-full" />
        ) : subjectsQuery.isError ? (
          <div
            role="alert"
            className="flex items-center justify-between gap-2 rounded-md border border-danger bg-danger-bg px-3.5 py-2.5 text-field-label text-danger"
          >
            <span className="flex items-center gap-2">
              <TriangleAlert aria-hidden="true" className="size-4 shrink-0" />
              {t("combinationDialog.optionsLoadError")}
            </span>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => void subjectsQuery.refetch()}
            >
              {commonT("retry")}
            </Button>
          </div>
        ) : (
          <>
            {watchedSubjectIds.length > 0 && (
              <DndContext
                accessibility={dndAccessibility}
                sensors={sensors}
                collisionDetection={closestCenter}
                onDragEnd={handleSubjectDragEnd}
              >
                <SortableContext
                  items={watchedSubjectIds}
                  strategy={verticalListSortingStrategy}
                >
                  <ol className="overflow-hidden rounded-md border border-border bg-surface">
                    {watchedSubjectIds.map((subjectId, index) => {
                      const entry = subjectLookup.get(subjectId);
                      const label = entry
                        ? `${pickName(entry, locale)} (${entry.code})${
                            entry.inactive ? ` · ${t("statuses.INACTIVE")}` : ""
                          }`
                        : subjectId;
                      return (
                        <SortableSubjectRow
                          key={subjectId}
                          id={subjectId}
                          index={index}
                          total={watchedSubjectIds.length}
                          label={label}
                          disabled={isSubmitting}
                          onMoveUp={() => handleMoveSubject(index, -1)}
                          onMoveDown={() => handleMoveSubject(index, 1)}
                          onRemove={() => handleRemoveSubject(subjectId)}
                        />
                      );
                    })}
                  </ol>
                </SortableContext>
              </DndContext>
            )}

            <FormField
              label={t("combinationDialog.addSubjectLabel")}
              error={errors.subjectItemIds?.message}
              hint={
                !subjectsQuery.isPending && availableSubjects.length === 0
                  ? t("combinationDialog.noActiveSubjects")
                  : undefined
              }
            >
              {(fieldProps) => (
                <Select
                  id={fieldProps.id}
                  aria-invalid={fieldProps["aria-invalid"]}
                  aria-describedby={fieldProps["aria-describedby"]}
                  options={availableSubjects}
                  value={subjectPickerValue}
                  placeholder={t("combinationDialog.addSubjectPlaceholder")}
                  onValueChange={handleAddSubject}
                  disabled={isSubmitting || availableSubjects.length === 0}
                />
              )}
            </FormField>
          </>
        )}
      </fieldset>

      <FormField
        label={t("combinationDialog.sourceNoteLabel")}
        error={errors.sourceNote?.message}
      >
        {(fieldProps) => (
          <Input
            {...fieldProps}
            {...register("sourceNote")}
            placeholder={t("combinationDialog.sourceNotePlaceholder")}
            disabled={isSubmitting}
          />
        )}
      </FormField>

      {showReason && (
        <>
          {usagesQuery.isPending ? (
            <div className="flex flex-col gap-1.5" role="status" aria-label={commonT("loading")}>
              <Skeleton className="h-10 w-full" />
            </div>
          ) : usagesQuery.isError ? (
            <div
              role="alert"
              className="flex items-center justify-between gap-3 rounded-md border border-danger bg-danger-bg px-3.5 py-2.5 text-field-label text-danger"
            >
              <span className="flex items-center gap-2">
                <TriangleAlert aria-hidden="true" className="size-4 shrink-0" />
                {t("stopItemDialog.usagesLoadError")}
              </span>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => void usagesQuery.refetch()}
              >
                {commonT("retry")}
              </Button>
            </div>
          ) : usagesQuery.data && usagesQuery.data.usages.length > 0 ? (
            <div
              role={usagesQuery.data.blocking ? "alert" : "note"}
              className={cn(
                "flex flex-col gap-1.5 rounded-md border px-3.5 py-2.5 text-field-label",
                usagesQuery.data.blocking
                  ? "border-danger bg-danger-bg text-danger"
                  : "border-warning bg-warning-bg text-warning",
              )}
            >
              <p className="font-medium">
                {usagesQuery.data.blocking
                  ? t("stopItemDialog.blockedAlert")
                  : t("detailDrawer.usagesTotalSummary", { count: usagesQuery.data.totalCount })}
              </p>
              <ul className="list-disc pl-5">
                {usagesQuery.data.usages.map((usage) => (
                  <li key={`${usage.source}-${usage.objectType}`}>
                    {t(`usageTypes.${resolveCatalogUsageTypeKey(usage.objectType)}`)} ({usage.count})
                  </li>
                ))}
              </ul>
            </div>
          ) : null}

          <FormField
            label={t("itemDialog.reasonLabel")}
            required
            hint={t("itemDialog.reasonHint")}
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
        </>
      )}

      <FormField label={t("itemDialog.descriptionLabel")} error={errors.description?.message}>
        {(fieldProps) => (
          <Textarea
            {...fieldProps}
            {...register("description")}
            rows={2}
            disabled={isSubmitting}
          />
        )}
      </FormField>

      {willCreateNewVersion ? (
        <div
          role="note"
          className="flex items-start gap-2.5 rounded-md border border-warning bg-warning-bg px-3.5 py-2.5 text-field-label text-warning"
        >
          <AlertTriangle aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>
            {t("combinationDialog.newVersionNotice", {
              currentVersion: currentVersionNo,
              nextVersion: currentVersionNo + 1,
            })}
          </span>
        </div>
      ) : (
        mode === "edit" &&
        compositionChanged &&
        !usagesQuery.isPending && (
          <p className="rounded-md border border-border bg-page px-3.5 py-2.5 text-field-label text-text-secondary">
            {t("combinationDialog.inPlaceVersionHint", { version: currentVersionNo })}
          </p>
        )
      )}

      <DialogFooter className="flex-wrap pt-1">
        <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>
          {t("cancel")}
        </Button>
        {mode === "create" && (
          <Button
            type="button"
            variant="soft"
            loading={isSubmitting}
            onClick={handleSubmit((values) => handleFormSubmit(values, true))}
          >
            {t("itemDialog.saveAndAddMore")}
          </Button>
        )}
        <Button
          type="submit"
          variant="primary"
          loading={isSubmitting}
          disabled={stopBlocked}
        >
          {willCreateNewVersion
            ? t("combinationDialog.saveNewVersion")
            : t("itemDialog.save")}
        </Button>
      </DialogFooter>
    </form>
  );
}

function SortableSubjectRow({
  id,
  index,
  total,
  label,
  disabled,
  onMoveUp,
  onMoveDown,
  onRemove,
}: {
  id: string;
  index: number;
  total: number;
  label: string;
  disabled: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
  onRemove: () => void;
}) {
  const t = useTranslations("catalog");
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id,
    disabled,
  });

  const style: CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  return (
    <li
      ref={setNodeRef}
      style={style}
      className={cn(
        "flex min-h-12 items-center gap-2 border-t border-border px-2 py-1 first:border-t-0",
        isDragging && "relative z-10 bg-primary-50 shadow-sm",
      )}
    >
      <button
        type="button"
        aria-label={t("combinationDialog.dragSubjectLabel", { name: label })}
        className="inline-flex size-8 shrink-0 cursor-grab items-center justify-center rounded-sm text-text-muted hover:bg-subtle hover:text-text-secondary active:cursor-grabbing disabled:cursor-not-allowed disabled:opacity-40"
        disabled={disabled}
        {...attributes}
        {...listeners}
      >
        <GripVertical aria-hidden className="size-4" />
      </button>

      <span
        aria-hidden
        className="inline-flex size-6.5 shrink-0 items-center justify-center rounded-full bg-primary-50 text-field-label font-semibold text-primary-text tabular-nums"
      >
        {index + 1}
      </span>

      <span className="min-w-0 flex-1 truncate font-medium text-text">{label}</span>

      <div className="flex shrink-0 items-center gap-0.5">
        <IconButton
          icon={<ArrowUp className="size-3.5" />}
          aria-label={t("combinationDialog.moveSubjectUpLabel", { name: label })}
          tooltip={t("combinationDialog.moveSubjectUpLabel", { name: label })}
          disabled={disabled || index === 0}
          onClick={onMoveUp}
          className="size-7 [&_svg]:size-3.5"
        />
        <IconButton
          icon={<ArrowDown className="size-3.5" />}
          aria-label={t("combinationDialog.moveSubjectDownLabel", { name: label })}
          tooltip={t("combinationDialog.moveSubjectDownLabel", { name: label })}
          disabled={disabled || index >= total - 1}
          onClick={onMoveDown}
          className="size-7 [&_svg]:size-3.5"
        />
        <IconButton
          icon={<X className="size-4" />}
          tone="danger"
          aria-label={t("combinationDialog.removeSubjectLabel", { name: label })}
          tooltip={t("combinationDialog.removeSubjectLabel", { name: label })}
          disabled={disabled}
          onClick={onRemove}
          className="size-8"
        />
      </div>
    </li>
  );
}
