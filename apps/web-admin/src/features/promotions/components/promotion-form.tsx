"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import {
  Button,
  Card,
  Checkbox,
  Chip,
  FormField,
  Input,
  Label,
  Select,
  Textarea,
  cn,
  focusRing,
  notify,
} from "@thi360/ui";
import { AlertCircle, CheckCircle2, Lock, Sparkles } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useMemo, useState } from "react";
import { Controller, type FieldPath, useForm, useWatch } from "react-hook-form";

import { withIdempotencyKey } from "@/features/content/idempotency";

import { getSuggestedPromotionCode } from "../api";
import {
  formatPromotionDateTime,
  parseVietnamDatetimeLocalMs,
  vietnamDatetimeLocalToIso,
} from "../datetime";
import { promotionErrorCode, promotionFieldErrors, resolvePromotionErrorMessage } from "../errors";
import { formatDiscountSummary, formatPositiveInteger, formatVnd, parseDigits } from "../format";
import { useCreatePromotionMutation, useUpdatePromotionMutation } from "../queries";
import {
  type PromotionFormValues,
  buildCreatePromotionRequest,
  buildPromotionFormSchema,
  buildUpdatePromotionRequest,
  createInitialFormValues,
  detailToFormValues,
} from "../schema";
import type {
  PromotionDetail,
  PromotionDiscountType,
  PromotionKind,
  PromotionStatus,
} from "../types";
import { PromotionPricePreviewCard } from "./promotion-price-preview";
import {
  AudienceTargetsPicker,
  ExamKindPicker,
  ExamScopePicker,
} from "./promotion-target-pickers";

const SERVER_FIELD_SET = new Set<FieldPath<PromotionFormValues>>([
  "kind",
  "name",
  "description",
  "code",
  "discountType",
  "discountValue",
  "maxDiscountVnd",
  "startsAt",
  "endsAt",
  "totalLimit",
  "perUserLimit",
  "combinable",
  "scope",
  "examKindIds",
  "examIds",
  "audience",
  "audienceClassIds",
  "audienceUserIds",
  "status",
]);

function mapBackendFieldError(
  field: string,
  code: string | undefined,
  tFieldErrors: (key: string) => string,
  tErrors: (key: string) => string,
  discountType: PromotionDiscountType,
): string {
  switch (field) {
    case "name":
      return code === "REQUIRED" ? tFieldErrors("nameRequired") : tFieldErrors("nameTooLong");
    case "description":
      return tFieldErrors("descriptionTooLong");
    case "code":
      return code === "REQUIRED" ? tFieldErrors("codeRequired") : tFieldErrors("codeFormat");
    case "discountValue":
      return discountType === "PERCENT"
        ? tFieldErrors("discountPercentRange")
        : tFieldErrors("discountAmountMin");
    case "maxDiscountVnd":
      return tFieldErrors("maxDiscountMin");
    case "startsAt":
      return tFieldErrors("startsAtRequired");
    case "endsAt":
      return code === "FUTURE" ? tFieldErrors("endsAtFuture") : tFieldErrors("endsAfterStarts");
    case "totalLimit":
      return tFieldErrors("totalLimitMin");
    case "perUserLimit":
      return tFieldErrors("perUserLimitMin");
    case "examKindIds":
      return tFieldErrors("scopeKindsRequired");
    case "examIds":
      return tFieldErrors("scopeExamsRequired");
    case "audienceClassIds":
    case "audienceUserIds":
      return tFieldErrors("audienceTargetsRequired");
    default:
      return tErrors("VALIDATION_FAILED");
  }
}

export function PromotionForm({
  mode,
  initialKind = "AUTO",
  initialDetail,
  onVersionConflict,
  conflictBanner,
}: {
  mode: "create" | "edit";
  initialKind?: PromotionKind;
  initialDetail?: PromotionDetail;
  onVersionConflict?: () => void;
  conflictBanner?: React.ReactNode;
}) {
  const t = useTranslations("promotions");
  const tFieldErrors = useTranslations("promotions.fieldErrors");
  const tErrors = useTranslations("promotions.errors");
  const locale = useLocale();
  const router = useRouter();

  const isUsed = mode === "edit" && (initialDetail?.usedCount ?? 0) > 0;
  const usedCount = initialDetail?.usedCount ?? 0;

  const schema = useMemo(
    () =>
      buildPromotionFormSchema((k) => tFieldErrors(k), {
        mode,
        usedCount,
      }),
    [mode, tFieldErrors, usedCount],
  );

  const defaultValues = useMemo(
    () => (initialDetail ? detailToFormValues(initialDetail) : createInitialFormValues(initialKind)),
    [initialDetail, initialKind],
  );

  const {
    control,
    register,
    handleSubmit,
    setValue,
    setError,
    setFocus,
    clearErrors,
    formState: { errors },
  } = useForm<PromotionFormValues>({
    defaultValues,
    resolver: zodResolver(schema),
    mode: "onTouched",
    shouldFocusError: true,
  });

  const formInstanceScope = useId();
  const formId = useId();

  const createMutation = useCreatePromotionMutation();
  const updateMutation = useUpdatePromotionMutation(initialDetail?.id ?? "");
  const [formBannerError, setFormBannerError] = useState<string | null>(null);
  const [suggestingCode, setSuggestingCode] = useState(false);

  const values = useWatch({ control, defaultValue: defaultValues }) as PromotionFormValues;
  const pending = createMutation.isPending || updateMutation.isPending;

  const nameSectionId = useId();
  const scheduleSectionId = useId();
  const limitsSectionId = useId();
  const scopeSectionId = useId();
  const audienceSectionId = useId();

  const handleSuggestCode = async () => {
    if (isUsed || values.kind !== "VOUCHER") return;
    setSuggestingCode(true);
    clearErrors("code");
    try {
      const code = await getSuggestedPromotionCode();
      setValue("code", code.toUpperCase(), {
        shouldValidate: true,
        shouldDirty: true,
        shouldTouch: true,
      });
    } catch {
      setError("code", { type: "manual", message: tFieldErrors("suggestCodeFailed") });
    } finally {
      setSuggestingCode(false);
    }
  };

  const onSubmit = async (submittedValues: PromotionFormValues) => {
    setFormBannerError(null);
    try {
      if (mode === "create") {
        const body = buildCreatePromotionRequest(submittedValues);
        const fingerprint = JSON.stringify(body);
        const created = await withIdempotencyKey(
          `promotion-create:${formInstanceScope}`,
          (key) => createMutation.mutateAsync({ body, idempotencyKey: key }),
          fingerprint,
        );
        notify.success(t("form.toast.created"));
        router.push(`/promotions/${created.id}`);
        return;
      }

      if (!initialDetail) return;
      const body = buildUpdatePromotionRequest(submittedValues, initialDetail);
      await updateMutation.mutateAsync(body);
      notify.success(t("form.toast.updated"));
      router.push(`/promotions/${initialDetail.id}`);
    } catch (err) {
      const errCode = promotionErrorCode(err);
      if (errCode === "VERSION_CONFLICT" || errCode === "PROMOTION_IN_USE") {
        onVersionConflict?.();
        setFormBannerError(resolvePromotionErrorMessage(err, tErrors, { usedCount }));
        return;
      }

      if (errCode === "VOUCHER_CODE_TAKEN") {
        const msg = tErrors("VOUCHER_CODE_TAKEN");
        setError("code", { type: "server", message: msg });
        setFocus("code");
        return;
      }

      if (errCode === "PROMOTION_TOTAL_LIMIT_BELOW_USED") {
        const msg = resolvePromotionErrorMessage(err, tErrors, { usedCount });
        setError("totalLimit", { type: "server", message: msg });
        setFocus("totalLimit");
        return;
      }

      if (errCode === "PROMOTION_ENDS_AT_PAST") {
        const msg = tErrors("PROMOTION_ENDS_AT_PAST");
        setError("endsAt", { type: "server", message: msg });
        setFocus("endsAt");
        return;
      }

      if (errCode === "PROMOTION_SCOPE_INVALID") {
        setFormBannerError(tErrors("PROMOTION_SCOPE_INVALID"));
        if (submittedValues.scope === "EXAM_KIND") {
          setError("examKindIds", { type: "server", message: tErrors("PROMOTION_SCOPE_INVALID") });
        } else if (submittedValues.scope === "EXAM") {
          setError("examIds", { type: "server", message: tErrors("PROMOTION_SCOPE_INVALID") });
        }
        return;
      }

      const srvFieldErrors = promotionFieldErrors(err);
      if (srvFieldErrors.length > 0) {
        let focused = false;
        for (const fe of srvFieldErrors) {
          if (SERVER_FIELD_SET.has(fe.field as FieldPath<PromotionFormValues>)) {
            const path = fe.field as FieldPath<PromotionFormValues>;
            const msg = mapBackendFieldError(
              fe.field,
              fe.code,
              (k) => tFieldErrors(k),
              (k) => tErrors(k),
              submittedValues.discountType,
            );
            setError(path, { type: "server", message: msg });
            if (!focused) {
              focused = true;
              try {
                setFocus(path);
              } catch {
                // ignore focus on custom pickers
              }
            }
          }
        }
        setFormBannerError(tErrors("VALIDATION_FAILED"));
        return;
      }

      setFormBannerError(resolvePromotionErrorMessage(err, tErrors, { usedCount }));
    }
  };

  const todoItems = useMemo(() => {
    const items: {
      key: string;
      label: string;
      anchor: string;
      focusField?: FieldPath<PromotionFormValues>;
    }[] = [];
    if (!values.name.trim()) {
      items.push({
        key: "name",
        label: t("form.aside.todoItems.name"),
        anchor: `#${nameSectionId}`,
        focusField: "name",
      });
    }
    if (values.kind === "VOUCHER") {
      const upper = values.code.trim().toUpperCase();
      if (!upper || upper.length < 3 || !/^[A-Z0-9-]+$/.test(upper)) {
        items.push({
          key: "code",
          label: t("form.aside.todoItems.code"),
          anchor: `#${nameSectionId}`,
          focusField: "code",
        });
      }
    }
    const startMs = parseVietnamDatetimeLocalMs(values.startsAt);
    const endMs = parseVietnamDatetimeLocalMs(values.endsAt);
    if (!isUsed && startMs === null) {
      items.push({
        key: "startsAt",
        label: t("form.aside.todoItems.startsAt"),
        anchor: `#${scheduleSectionId}`,
        focusField: "startsAt",
      });
    }
    if (endMs === null || (startMs !== null && endMs <= startMs)) {
      items.push({
        key: "endsAt",
        label: t("form.aside.todoItems.endsAt"),
        anchor: `#${scheduleSectionId}`,
        focusField: "endsAt",
      });
    }
    const discVal =
      values.discountType === "PERCENT"
        ? Number(values.discountValue.trim() || 0)
        : (parseDigits(values.discountValue) ?? 0);
    if (
      !Number.isFinite(discVal) ||
      discVal < 1 ||
      (values.discountType === "PERCENT" && discVal > 100)
    ) {
      items.push({
        key: "discountValue",
        label: t("form.aside.todoItems.discountValue"),
        anchor: `#${limitsSectionId}`,
        focusField: "discountValue",
      });
    }
    if (values.scope === "EXAM_KIND" && values.examKindIds.length === 0) {
      items.push({
        key: "scopeTargets",
        label: t("form.aside.todoItems.scopeTargets"),
        anchor: `#${scopeSectionId}`,
      });
    }
    if (values.scope === "EXAM" && values.examIds.length === 0) {
      items.push({
        key: "scopeTargets",
        label: t("form.aside.todoItems.scopeTargets"),
        anchor: `#${scopeSectionId}`,
      });
    }
    if (
      values.kind === "VOUCHER" &&
      values.audience === "SELECTED" &&
      values.audienceClassIds.length === 0 &&
      values.audienceUserIds.length === 0
    ) {
      items.push({
        key: "audienceTargets",
        label: t("form.aside.todoItems.audienceTargets"),
        anchor: `#${audienceSectionId}`,
      });
    }
    return items;
  }, [
    audienceSectionId,
    isUsed,
    limitsSectionId,
    nameSectionId,
    scheduleSectionId,
    scopeSectionId,
    t,
    values,
  ]);

  const parsedDiscountNum =
    values.discountType === "PERCENT"
      ? Number(values.discountValue.trim() || 0)
      : (parseDigits(values.discountValue) ?? 0);
  const parsedMaxCap =
    values.discountType === "PERCENT" ? parseDigits(values.maxDiscountVnd) : null;
  const formattedDiscountPreview =
    parsedDiscountNum > 0
      ? formatDiscountSummary(values.discountType, parsedDiscountNum, parsedMaxCap, locale, (k, v) =>
          t(`discountLabels.${k}`, v),
        )
      : t("form.aside.summaryPlaceholder");

  const startIsoPreview = vietnamDatetimeLocalToIso(values.startsAt);
  const endIsoPreview = vietnamDatetimeLocalToIso(values.endsAt);

  return (
    <div className="flex flex-col gap-5">
      {conflictBanner}

      {isUsed && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-lg border border-warning/40 bg-warning-bg p-4 text-warning"
        >
          <Lock aria-hidden className="mt-0.5 size-5 shrink-0" />
          <div className="flex flex-col gap-1">
            <p className="text-body font-semibold">{t("form.banner.inUseTitle")}</p>
            <p className="text-caption">{t("form.banner.inUseDescription", { count: usedCount })}</p>
          </div>
        </div>
      )}

      {formBannerError && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-lg border border-danger/40 bg-danger-bg p-4 text-danger"
        >
          <AlertCircle aria-hidden className="mt-0.5 size-5 shrink-0" />
          <div className="flex flex-col gap-0.5">
            <p className="text-body font-semibold">{t("form.banner.formErrorTitle")}</p>
            <p className="text-caption">{formBannerError}</p>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_320px] lg:items-start">
        <form
          id={formId}
          onSubmit={handleSubmit(onSubmit)}
          noValidate
          className="flex min-w-0 flex-col gap-5"
        >
          {/* 1. General info */}
          <Card id={nameSectionId} className="flex flex-col gap-4 p-5">
            <h2 className="text-title font-semibold text-text">{t("form.sections.general")}</h2>

            <div className="flex flex-col gap-2">
              <div className="flex items-center gap-2">
                <Label>{t("form.fields.kind")}</Label>
                {(mode === "edit" || isUsed) && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
              </div>
              <div
                className="grid grid-cols-1 gap-3 sm:grid-cols-2"
                role="radiogroup"
                aria-label={t("form.fields.kind")}
              >
                {(["AUTO", "VOUCHER"] as const).map((option) => {
                  const selected = values.kind === option;
                  const kindDisabled = mode === "edit";
                  return (
                    <button
                      key={option}
                      type="button"
                      role="radio"
                      aria-checked={selected}
                      disabled={kindDisabled}
                      onClick={() => {
                        if (!kindDisabled) {
                          setValue("kind", option, { shouldDirty: true, shouldValidate: true });
                        }
                      }}
                      className={cn(
                        "flex flex-col items-start gap-1 rounded-lg border p-3.5 text-left transition-colors duration-150 motion-reduce:transition-none",
                        selected ? "border-primary-600 bg-primary-50" : "border-border bg-surface hover:bg-subtle",
                        kindDisabled && "cursor-not-allowed opacity-70",
                        focusRing,
                      )}
                    >
                      <span className="text-body font-semibold text-text">
                        {option === "AUTO" ? t("form.fields.kindAuto") : t("form.fields.kindVoucher")}
                      </span>
                      <span className="text-caption text-text-muted">
                        {option === "AUTO" ? t("form.fields.kindAutoHint") : t("form.fields.kindVoucherHint")}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>

            <FormField label={t("form.fields.name")} required error={errors.name?.message}>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  placeholder={t("form.fields.namePlaceholder")}
                  {...register("name")}
                />
              )}
            </FormField>

            {values.kind === "VOUCHER" && (
              <div className="flex flex-col gap-1.5">
                {isUsed && (
                  <div>
                    <Chip variant="tag">{t("form.lockedChip")}</Chip>
                  </div>
                )}
                <FormField
                  label={t("form.fields.code")}
                  required
                  hint={t("form.fields.codeHint")}
                  error={errors.code?.message}
                >
                  {(controlProps) => (
                    <div className="flex flex-col gap-2 sm:flex-row">
                      <Input
                        {...controlProps}
                        disabled={isUsed}
                        placeholder={t("form.fields.codePlaceholder")}
                        className="font-mono uppercase"
                        {...register("code", {
                          onChange: (event) => {
                            event.target.value = String(event.target.value).toUpperCase();
                          },
                        })}
                      />
                      <Button
                        type="button"
                        variant="secondary"
                        disabled={isUsed || suggestingCode}
                        onClick={() => void handleSuggestCode()}
                        className="shrink-0"
                      >
                        <Sparkles />
                        <span>{suggestingCode ? t("actions.suggestingCode") : t("actions.suggestCode")}</span>
                      </Button>
                    </div>
                  )}
                </FormField>
              </div>
            )}

            <FormField label={t("form.fields.description")} error={errors.description?.message}>
              {(controlProps) => (
                <Textarea
                  {...controlProps}
                  rows={3}
                  placeholder={t("form.fields.descriptionPlaceholder")}
                  {...register("description")}
                />
              )}
            </FormField>

            <FormField label={t("form.fields.status")}>
              {(controlProps) => (
                <Controller
                  control={control}
                  name="status"
                  render={({ field }) => (
                    <Select
                      {...controlProps}
                      value={field.value}
                      onValueChange={(next) => field.onChange(next as PromotionStatus)}
                      options={[
                        { value: "ACTIVE", label: t("form.fields.statusActive") },
                        { value: "STOPPED", label: t("form.fields.statusStopped") },
                      ]}
                    />
                  )}
                />
              )}
            </FormField>
          </Card>

          {/* 2. Schedule */}
          <Card id={scheduleSectionId} className="flex flex-col gap-4 p-5">
            <div className="flex items-center gap-2">
              <h2 className="text-title font-semibold text-text">{t("form.sections.schedule")}</h2>
              {isUsed && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
            </div>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField
                label={t("form.fields.startsAt")}
                required={!isUsed}
                error={errors.startsAt?.message}
              >
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    type="datetime-local"
                    disabled={isUsed}
                    {...register("startsAt")}
                  />
                )}
              </FormField>

              <FormField label={t("form.fields.endsAt")} required error={errors.endsAt?.message}>
                {(controlProps) => (
                  <Input {...controlProps} type="datetime-local" {...register("endsAt")} />
                )}
              </FormField>
            </div>
          </Card>

          {/* 3. Discount & Limits */}
          <Card id={limitsSectionId} className="flex flex-col gap-4 p-5">
            <div className="flex items-center gap-2">
              <h2 className="text-title font-semibold text-text">{t("form.sections.limits")}</h2>
              {isUsed && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField label={t("form.fields.discountType")}>
                {(controlProps) => (
                  <Controller
                    control={control}
                    name="discountType"
                    render={({ field }) => (
                      <Select
                        {...controlProps}
                        disabled={isUsed}
                        value={field.value}
                        onValueChange={(next) => {
                          field.onChange(next as PromotionDiscountType);
                          clearErrors("discountValue");
                          if (next === "AMOUNT") {
                            setValue("maxDiscountVnd", "");
                            clearErrors("maxDiscountVnd");
                          }
                        }}
                        options={[
                          { value: "PERCENT", label: t("form.fields.discountTypePercent") },
                          { value: "AMOUNT", label: t("form.fields.discountTypeAmount") },
                        ]}
                      />
                    )}
                  />
                )}
              </FormField>

              <FormField
                label={
                  values.discountType === "PERCENT"
                    ? t("form.fields.discountValuePercent")
                    : t("form.fields.discountValueAmount")
                }
                required={!isUsed}
                hint={
                  values.discountType === "AMOUNT" && parseDigits(values.discountValue)
                    ? t("form.fields.discountValueAmountHint", {
                        formatted: formatVnd(parseDigits(values.discountValue), locale),
                      })
                    : undefined
                }
                error={errors.discountValue?.message}
              >
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    type="text"
                    inputMode="numeric"
                    disabled={isUsed}
                    {...register("discountValue", {
                      onChange: (event) => {
                        event.target.value = formatPositiveInteger(event.target.value);
                      },
                    })}
                  />
                )}
              </FormField>
            </div>

            {values.discountType === "PERCENT" && (
              <FormField
                label={t("form.fields.maxDiscountVnd")}
                hint={t("form.fields.maxDiscountVndHint", {
                  formatted: parseDigits(values.maxDiscountVnd)
                    ? formatVnd(parseDigits(values.maxDiscountVnd), locale)
                    : t("common.unlimited"),
                })}
                error={errors.maxDiscountVnd?.message}
              >
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    type="text"
                    inputMode="numeric"
                    disabled={isUsed}
                    placeholder={t("form.fields.maxDiscountVndPlaceholder")}
                    {...register("maxDiscountVnd", {
                      onChange: (event) => {
                        event.target.value = formatPositiveInteger(event.target.value);
                      },
                    })}
                  />
                )}
              </FormField>
            )}

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField
                label={t("form.fields.totalLimit")}
                hint={
                  usedCount > 0
                    ? t("form.fields.totalLimitHint", { used: usedCount })
                    : t("form.fields.totalLimitHintUnlimited")
                }
                error={errors.totalLimit?.message}
              >
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    type="text"
                    inputMode="numeric"
                    placeholder={t("form.fields.totalLimitPlaceholder")}
                    {...register("totalLimit", {
                      onChange: (event) => {
                        event.target.value = formatPositiveInteger(event.target.value);
                      },
                    })}
                  />
                )}
              </FormField>

              {values.kind === "VOUCHER" && (
                <FormField
                  label={t("form.fields.perUserLimit")}
                  hint={t("form.fields.perUserLimitHint")}
                  error={errors.perUserLimit?.message}
                >
                  {(controlProps) => (
                    <Input
                      {...controlProps}
                      type="text"
                      inputMode="numeric"
                      disabled={isUsed}
                      placeholder={t("common.unlimited")}
                      {...register("perUserLimit", {
                        onChange: (event) => {
                          event.target.value = formatPositiveInteger(event.target.value);
                        },
                      })}
                    />
                  )}
                </FormField>
              )}
            </div>

            {values.kind === "VOUCHER" && (
              <div className="flex flex-col gap-1.5 rounded-md border border-border p-3">
                <div className="flex items-center gap-2">
                  <Controller
                    control={control}
                    name="combinable"
                    render={({ field }) => (
                      <Checkbox
                        id="promo-combinable"
                        checked={field.value}
                        disabled={isUsed}
                        onCheckedChange={(checked) => field.onChange(checked === true)}
                      />
                    )}
                  />
                  <Label
                    htmlFor="promo-combinable"
                    aria-disabled={isUsed || undefined}
                    className={isUsed ? "cursor-not-allowed" : "cursor-pointer"}
                  >
                    {t("form.fields.combinableLabel")}
                  </Label>
                  {isUsed && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
                </div>
                <p className="pl-7 text-caption text-text-muted">{t("form.fields.combinableHint")}</p>
              </div>
            )}
          </Card>

          {/* 4. Scope */}
          <Card id={scopeSectionId} className="flex flex-col gap-4 p-5">
            <div className="flex items-center gap-2">
              <h2 className="text-title font-semibold text-text">{t("form.sections.scope")}</h2>
              {isUsed && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
            </div>

            <div
              className="grid grid-cols-1 gap-3 sm:grid-cols-3"
              role="radiogroup"
              aria-label={t("form.fields.scope")}
            >
              {(["ALL", "EXAM_KIND", "EXAM"] as const).map((option) => {
                const selected = values.scope === option;
                return (
                  <button
                    key={option}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    disabled={isUsed}
                    onClick={() => {
                      if (!isUsed) {
                        setValue("scope", option, { shouldDirty: true, shouldValidate: true });
                        clearErrors(["examKindIds", "examIds"]);
                      }
                    }}
                    className={cn(
                      "flex flex-col items-start gap-1 rounded-lg border p-3 text-left transition-colors duration-150 motion-reduce:transition-none",
                      selected ? "border-primary-600 bg-primary-50" : "border-border bg-surface hover:bg-subtle",
                      isUsed && "cursor-not-allowed opacity-70",
                      focusRing,
                    )}
                  >
                    <span className="text-body font-semibold text-text">
                      {t(`form.fields.scopeOptions.${option}`)}
                    </span>
                    <span className="text-caption text-text-muted">
                      {t(`form.fields.scopeOptions.${option}Hint`)}
                    </span>
                  </button>
                );
              })}
            </div>

            {values.scope === "EXAM_KIND" && (
              <Controller
                control={control}
                name="examKindIds"
                render={({ field }) => (
                  <ExamKindPicker
                    selectedIds={field.value}
                    onChange={field.onChange}
                    disabled={isUsed}
                    initialScopeItems={initialDetail?.scopeItems}
                    error={errors.examKindIds?.message}
                  />
                )}
              />
            )}

            {values.scope === "EXAM" && (
              <Controller
                control={control}
                name="examIds"
                render={({ field }) => (
                  <ExamScopePicker
                    selectedIds={field.value}
                    onChange={field.onChange}
                    disabled={isUsed}
                    initialScopeItems={initialDetail?.scopeItems}
                    error={errors.examIds?.message}
                  />
                )}
              />
            )}
          </Card>

          {/* 5. Audience (VOUCHER only) */}
          {values.kind === "VOUCHER" && (
            <Card id={audienceSectionId} className="flex flex-col gap-4 p-5">
              <div className="flex items-center gap-2">
                <h2 className="text-title font-semibold text-text">{t("form.sections.audience")}</h2>
                {isUsed && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
              </div>

              <div
                className="grid grid-cols-1 gap-3 sm:grid-cols-3"
                role="radiogroup"
                aria-label={t("form.fields.audience")}
              >
                {(["ALL", "NEW_ACCOUNT", "SELECTED"] as const).map((option) => {
                  const selected = values.audience === option;
                  return (
                    <button
                      key={option}
                      type="button"
                      role="radio"
                      aria-checked={selected}
                      disabled={isUsed}
                      onClick={() => {
                        if (!isUsed) {
                          setValue("audience", option, { shouldDirty: true, shouldValidate: true });
                          clearErrors(["audienceClassIds", "audienceUserIds"]);
                        }
                      }}
                      className={cn(
                        "flex flex-col items-start gap-1 rounded-lg border p-3 text-left transition-colors duration-150 motion-reduce:transition-none",
                        selected ? "border-primary-600 bg-primary-50" : "border-border bg-surface hover:bg-subtle",
                        isUsed && "cursor-not-allowed opacity-70",
                        focusRing,
                      )}
                    >
                      <span className="text-body font-semibold text-text">
                        {t(`form.fields.audienceOptions.${option}`)}
                      </span>
                      <span className="text-caption text-text-muted">
                        {t(`form.fields.audienceOptions.${option}Hint`)}
                      </span>
                    </button>
                  );
                })}
              </div>

              {values.audience === "SELECTED" && (
                <AudienceTargetsPicker
                  selectedClassIds={values.audienceClassIds}
                  selectedUserIds={values.audienceUserIds}
                  onChangeClasses={(next) => {
                    setValue("audienceClassIds", next, { shouldDirty: true, shouldValidate: true });
                    clearErrors("audienceClassIds");
                  }}
                  onChangeUsers={(next) => {
                    setValue("audienceUserIds", next, { shouldDirty: true, shouldValidate: true });
                  }}
                  disabled={isUsed}
                  initialClasses={initialDetail?.audienceClasses}
                  initialUsers={initialDetail?.audienceUsers}
                  error={errors.audienceClassIds?.message}
                />
              )}
            </Card>
          )}
        </form>

        {/* Sticky Sidebar Aside */}
        <aside className="flex flex-col gap-4 lg:sticky lg:top-4">
          <Card className="flex flex-col gap-3 p-4">
            <h3 className="text-body font-semibold text-text">{t("form.aside.summaryTitle")}</h3>
            <dl className="flex flex-col gap-2 text-caption">
              <div className="flex justify-between gap-2">
                <dt className="text-text-muted">{t("form.aside.summaryKind")}</dt>
                <dd className="font-medium text-text">{t(`kinds.${values.kind}`)}</dd>
              </div>
              <div className="flex justify-between gap-2">
                <dt className="text-text-muted">{t("form.aside.summaryDiscount")}</dt>
                <dd className="font-medium text-text tabular-nums">{formattedDiscountPreview}</dd>
              </div>
              <div className="flex justify-between gap-2">
                <dt className="text-text-muted">{t("form.aside.summaryScope")}</dt>
                <dd className="font-medium text-text">{t(`list.scopeSummary.${values.scope}`)}</dd>
              </div>
              {values.kind === "VOUCHER" && (
                <div className="flex justify-between gap-2">
                  <dt className="text-text-muted">{t("form.aside.summaryAudience")}</dt>
                  <dd className="font-medium text-text">
                    {t(`list.audienceSummary.${values.audience}`)}
                  </dd>
                </div>
              )}
              <div className="flex flex-col gap-0.5 border-t border-border pt-2">
                <dt className="text-text-muted">{t("form.aside.summaryPeriod")}</dt>
                <dd className="font-medium text-text tabular-nums">
                  {startIsoPreview && endIsoPreview
                    ? `${formatPromotionDateTime(startIsoPreview, locale)} - ${formatPromotionDateTime(endIsoPreview, locale)}`
                    : t("form.aside.summaryPlaceholder")}
                </dd>
              </div>
            </dl>

            <div className="flex flex-col gap-2 border-t border-border pt-3">
              <Button
                type="submit"
                form={formId}
                disabled={pending}
                aria-busy={pending || undefined}
                className="w-full"
              >
                {pending
                  ? t("actions.saving")
                  : mode === "create"
                    ? t("actions.create")
                    : t("actions.save")}
              </Button>
              <Button type="button" variant="secondary" asChild className="w-full">
                <Link href={initialDetail ? `/promotions/${initialDetail.id}` : "/promotions"}>
                  {t("actions.cancel")}
                </Link>
              </Button>
            </div>
          </Card>

          <Card className="flex flex-col gap-2.5 p-4">
            <h3 className="text-body font-semibold text-text">
              {todoItems.length === 0
                ? t("form.aside.todoTitle")
                : t("form.aside.todoRemaining", { count: todoItems.length })}
            </h3>
            {todoItems.length === 0 ? (
              <p className="flex items-center gap-1.5 text-caption text-success">
                <CheckCircle2 aria-hidden className="size-4 shrink-0" />
                <span>{t("form.aside.todoComplete")}</span>
              </p>
            ) : (
              <ul role="list" className="flex flex-col gap-1.5 text-caption">
                {todoItems.map((item) => (
                  <li key={item.key}>
                    <a
                      href={item.anchor}
                      onClick={(event) => {
                        if (item.focusField) {
                          event.preventDefault();
                          try {
                            setFocus(item.focusField);
                          } catch {
                            document.querySelector(item.anchor)?.scrollIntoView();
                          }
                        }
                      }}
                      className={cn("text-primary-text hover:underline", focusRing)}
                    >
                      • {item.label}
                    </a>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <PromotionPricePreviewCard
            promotionId={initialDetail?.id}
            scopeItems={initialDetail?.scopeItems}
            capWarnings={initialDetail?.capWarnings}
          />
        </aside>
      </div>
    </div>
  );
}
