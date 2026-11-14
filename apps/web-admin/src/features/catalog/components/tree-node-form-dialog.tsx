"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueries, useQuery } from "@tanstack/react-query";
import {
  Badge,
  Button,
  Checkbox,
  cn,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
  Input,
  Select,
  Skeleton,
  Textarea,
} from "@thi360/ui";
import { Lock } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import {
  catalogQueryKeys,
  type CatalogApi,
  type CatalogGroupRefResponse,
  type CatalogGroupResponse,
  type CatalogInheritedRefResponse,
  type CatalogItemResponse,
  type CatalogStatus,
  type CatalogTreeNodeResponse,
  type CreateCatalogItemRequest,
  type UpdateCatalogItemRequest,
} from "../api";
import { createTreeNodeFormSchema, type TreeNodeFormValues } from "../schemas";
import {
  getCatalogErrorCode,
  getCatalogFieldErrors,
  pickName,
  resolveCatalogErrorMessage,
} from "../utils";

export type TreeNodeFormDialogProps = {
  open: boolean;
  mode: "create" | "edit";
  group: CatalogGroupResponse;
  parentNode: CatalogTreeNodeResponse | null;
  node: CatalogItemResponse | CatalogTreeNodeResponse | null;
  refGroups: CatalogGroupRefResponse[];
  api: CatalogApi;
  onOpenChange: (open: boolean) => void;
  onSubmit: (
    payload:
      | { mode: "create"; groupId: string; data: CreateCatalogItemRequest; addAnother: boolean }
      | { mode: "edit"; itemId: string; data: UpdateCatalogItemRequest },
  ) => Promise<void>;
};

export function TreeNodeFormDialog({
  open,
  mode,
  group,
  parentNode,
  node,
  refGroups,
  api,
  onOpenChange,
  onSubmit,
}: TreeNodeFormDialogProps) {
  const t = useTranslations("catalog");
  const locale = useLocale();
  const [submitting, setSubmitting] = useState(false);

  const levels = group.levels ?? [];
  const targetDepth =
    mode === "edit" && node ? node.depth : parentNode ? parentNode.depth + 1 : 1;
  const levelName =
    levels[targetDepth - 1] ?? t("tree.rootNodeBadge");

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
                ? t("treeNodeDialog.createTitle", { levelName })
                : t("treeNodeDialog.editTitle", { levelName })}
            </DialogTitle>
            <DialogDescription>{pickName(group, locale)}</DialogDescription>
          </DialogHeader>
          <TreeNodeFormBody
            key={`${mode}-${group.id}-${parentNode?.id ?? "root"}-${node?.id ?? "new"}`}
            mode={mode}
            group={group}
            parentNode={parentNode}
            node={node}
            refGroups={refGroups}
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

function TreeNodeFormBody({
  mode,
  group,
  parentNode,
  node,
  refGroups,
  api,
  onCancel,
  onSubmittingChange,
  onSubmit,
}: {
  mode: "create" | "edit";
  group: CatalogGroupResponse;
  parentNode: CatalogTreeNodeResponse | null;
  node: CatalogItemResponse | CatalogTreeNodeResponse | null;
  refGroups: CatalogGroupRefResponse[];
  api: CatalogApi;
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onSubmit: TreeNodeFormDialogProps["onSubmit"];
}) {
  const t = useTranslations("catalog");
  const locale = useLocale();
  const [codeLocked, setCodeLocked] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const initialStatus = mode === "edit" ? node?.status : undefined;
  const isSystemItem = mode === "edit" && Boolean(node && "system" in node && node.system);
  const effectiveCodeLocked = codeLocked || isSystemItem;

  const inheritedRefs = useMemo<CatalogInheritedRefResponse[]>(() => {
    if (mode === "edit" && node) {
      return node.inheritedRefs;
    }
    if (!parentNode) return [];
    const inheritedFromParent = parentNode.inheritedRefs;
    const fromParentOwn: CatalogInheritedRefResponse[] = parentNode.ownRefs.map((ref) => ({
      refItemId: ref.refItemId,
      code: ref.code,
      nameVi: ref.nameVi,
      nameEn: ref.nameEn,
      status: ref.status,
      refGroupId: ref.refGroupId,
      refGroupCode: ref.refGroupCode,
      refGroupNameVi: ref.refGroupNameVi,
      refGroupNameEn: ref.refGroupNameEn,
      fromItemId: parentNode.id,
      fromItemCode: parentNode.code,
      fromItemNameVi: parentNode.nameVi,
      fromItemNameEn: parentNode.nameEn,
    }));
    const seen = new Set<string>();
    const combined: CatalogInheritedRefResponse[] = [];
    for (const ref of [...inheritedFromParent, ...fromParentOwn]) {
      if (!seen.has(ref.refItemId)) {
        seen.add(ref.refItemId);
        combined.push(ref);
      }
    }
    return combined;
  }, [mode, node, parentNode]);

  const inheritedRefIdSet = useMemo(
    () => new Set(inheritedRefs.map((r) => r.refItemId)),
    [inheritedRefs],
  );

  const initialOwnRefIds = useMemo(
    () => (mode === "edit" && node ? node.ownRefs.map((r) => r.refItemId) : []),
    [mode, node],
  );

  const refOptionQueries = useQueries({
    queries: refGroups.map((refGroup) => ({
      queryKey: catalogQueryKeys.options(refGroup.code),
      queryFn: () => api.getOptions(refGroup.code),
    })),
  });

  const schema = useMemo(
    () =>
      createTreeNodeFormSchema(initialStatus, {
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
    reset,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<TreeNodeFormValues>({
    resolver: zodResolver(schema),
    defaultValues:
      mode === "edit" && node
        ? {
            code: node.code,
            displayOrder: String(node.displayOrder),
            nameVi: node.nameVi,
            nameEn: node.nameEn ?? "",
            status: node.status,
            description: node.description ?? "",
            reason: "",
            refItemIds: initialOwnRefIds,
          }
        : {
            code: "",
            displayOrder: "",
            nameVi: "",
            nameEn: "",
            status: "ACTIVE",
            description: "",
            reason: "",
            refItemIds: [],
          },
  });

  const watchedStatus = useWatch({ control, name: "status" });
  const watchedRefItemIds = useWatch({ control, name: "refItemIds" });
  const showReason = mode === "edit" && node?.status === "ACTIVE" && watchedStatus === "INACTIVE";

  const usagesQuery = useQuery({
    queryKey: catalogQueryKeys.itemUsages(node?.id ?? "none"),
    queryFn: () => api.getItemUsages(node!.id),
    enabled: showReason && Boolean(node?.id),
  });
  const stopBlocked = showReason && Boolean(usagesQuery.data?.blocking);

  const statusOptions = useMemo(
    () => [
      { value: "ACTIVE" satisfies CatalogStatus, label: t("statuses.ACTIVE") },
      { value: "INACTIVE" satisfies CatalogStatus, label: t("statuses.INACTIVE") },
    ],
    [t],
  );

  const levels = group.levels ?? [];
  const parentLevelName = parentNode ? levels[parentNode.depth - 1] : null;

  function handleToggleRefItem(refItemId: string, checked: boolean) {
    if (inheritedRefIdSet.has(refItemId)) return;
    if (checked) {
      if (!watchedRefItemIds.includes(refItemId)) {
        setValue("refItemIds", [...watchedRefItemIds, refItemId], { shouldValidate: true });
      }
    } else {
      setValue(
        "refItemIds",
        watchedRefItemIds.filter((id) => id !== refItemId),
        { shouldValidate: true },
      );
    }
  }

  async function handleFormSubmit(values: TreeNodeFormValues, addAnother: boolean) {
    if (stopBlocked) return;
    setServerError(null);
    onSubmittingChange(true);
    const trimmedCode = values.code.trim();
    const trimmedNameVi = values.nameVi.trim();
    const trimmedNameEn = values.nameEn.trim();
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
            ...(parentNode ? { parentId: parentNode.id } : {}),
            ...(values.refItemIds.length > 0 ? { refItemIds: values.refItemIds } : {}),
            status: values.status,
          },
        });
        if (addAnother) {
          reset({
            code: "",
            displayOrder: "",
            nameVi: "",
            nameEn: "",
            status: "ACTIVE",
            description: "",
            reason: "",
            refItemIds: [],
          });
        }
      } else if (node) {
        const shouldSendCode = !effectiveCodeLocked && trimmedCode !== node.code;
        await onSubmit({
          mode: "edit",
          itemId: node.id,
          data: {
            ...(shouldSendCode ? { code: trimmedCode } : {}),
            nameVi: trimmedNameVi,
            nameEn: trimmedNameEn,
            description: trimmedDescription,
            ...(parsedOrder !== undefined ? { displayOrder: parsedOrder } : {}),
            status: values.status,
            ...(showReason ? { reason: trimmedReason } : {}),
            refItemIds: values.refItemIds,
            version: node.version,
          },
        });
      }
    } catch (error) {
      const code = getCatalogErrorCode(error);
      const translated = resolveCatalogErrorMessage(error, (key) => t(`errorCodes.${key}`));

      if (code === "CATALOG_CODE_LOCKED") {
        setCodeLocked(true);
        if (node) {
          setValue("code", node.code, { shouldValidate: false });
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

      <div className="flex flex-col gap-1.5">
        <span className="text-field-label font-medium text-text-secondary">
          {t("treeNodeDialog.parentLabel")}
        </span>
        <div className="flex min-h-11 flex-wrap items-center gap-2 rounded-md border border-border bg-page px-3 py-2">
          {parentNode ? (
            <>
              {parentLevelName && (
                <span className="text-caption text-text-muted">{parentLevelName}</span>
              )}
              <span className="rounded-sm bg-subtle px-2 py-0.5 text-caption font-semibold text-text">
                {parentNode.code}
              </span>
              <span className="font-medium text-text">{pickName(parentNode, locale)}</span>
            </>
          ) : (
            <span className="text-field-label text-text-secondary">
              {t("treeNodeDialog.rootParentLabel")}
            </span>
          )}
        </div>
        <p className="text-caption text-text-muted">{t("treeNodeDialog.parentHint")}</p>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-[2fr_1fr]">
        <FormField
          label={t("itemDialog.codeLabel")}
          required
          error={errors.code?.message}
          hint={
            effectiveCodeLocked
              ? t("itemDialog.codeLockedHint")
              : t("itemDialog.codeHint")
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
          label={t("itemDialog.nameViLabel")}
          required
          error={errors.nameVi?.message}
        >
          {(fieldProps) => (
            <Input {...fieldProps} {...register("nameVi")} disabled={isSubmitting} />
          )}
        </FormField>

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
      </div>

      <FormField label={t("itemDialog.statusLabel")} required error={errors.status?.message}>
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
        <>
          {usagesQuery.data && usagesQuery.data.usages.length > 0 && (
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
                    {usage.source} ({usage.count})
                  </li>
                ))}
              </ul>
            </div>
          )}

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

      {refGroups.length > 0 && (
        <fieldset className="flex flex-col gap-3 rounded-md border border-border p-3.5">
          <legend className="px-1.5 text-field-label font-semibold text-text">
            {t("treeNodeDialog.linksSection")}
          </legend>

          {refGroups.map((refGroup, idx) => {
            const optQuery = refOptionQueries[idx];
            const options = optQuery?.data ?? [];
            const inheritedForGroup = inheritedRefs.filter(
              (ref) => ref.refGroupId === refGroup.refGroupId,
            );
            const groupLabel = pickName(refGroup, locale);

            return (
              <div key={refGroup.refGroupId} className="flex flex-col gap-1.5">
                <span className="text-field-label font-medium text-text-secondary">
                  {groupLabel}
                </span>

                {inheritedForGroup.length > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {inheritedForGroup.map((ref) => (
                      <Badge
                        key={ref.refItemId}
                        title={t("tree.inheritedLockedHint")}
                        className="gap-1"
                      >
                        <Lock aria-hidden className="size-3" />
                        <span>{pickName(ref, locale)}</span>
                        <span className="text-text-muted">
                          ({t("treeNodeDialog.inheritedBadge")})
                        </span>
                      </Badge>
                    ))}
                  </div>
                )}

                {optQuery?.isPending ? (
                  <Skeleton className="h-9 w-full" />
                ) : (
                  <div
                    role="group"
                    aria-label={t("tree.pickRefValuesLabel", { name: groupLabel })}
                    className="flex flex-wrap gap-2 rounded-md border border-border bg-page p-2.5"
                  >
                    {options
                      .filter((opt) => !inheritedRefIdSet.has(opt.id))
                      .map((opt) => {
                        const checked = watchedRefItemIds.includes(opt.id);
                        return (
                          <label
                            key={opt.id}
                            className="inline-flex cursor-pointer items-center gap-1.5 rounded-sm bg-surface px-2.5 py-1 text-field-label text-text"
                          >
                            <Checkbox
                              checked={checked}
                              disabled={isSubmitting}
                              onCheckedChange={(next) =>
                                handleToggleRefItem(opt.id, Boolean(next))
                              }
                            />
                            <span>{pickName(opt, locale)}</span>
                          </label>
                        );
                      })}
                  </div>
                )}
              </div>
            );
          })}
        </fieldset>
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
          {t("itemDialog.save")}
        </Button>
      </DialogFooter>
    </form>
  );
}
