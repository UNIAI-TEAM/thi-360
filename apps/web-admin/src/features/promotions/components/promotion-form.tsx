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
import { type FormEvent, useId, useMemo, useRef, useState } from "react";
import { type FieldErrors, Controller, type FieldPath, useForm, useWatch } from "react-hook-form";

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
  PROMOTION_CODE_MAX_LENGTH,
  PROMOTION_DESCRIPTION_MAX_LENGTH,
  PROMOTION_NAME_MAX_LENGTH,
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
import { handleRadioGroupKeyDown } from "./radio-group-key";

const KIND_OPTIONS = ["AUTO", "VOUCHER"] as const;
const SCOPE_OPTIONS = ["ALL", "EXAM_KIND", "EXAM"] as const;
const AUDIENCE_OPTIONS = ["ALL", "NEW_ACCOUNT", "SELECTED"] as const;

const FIELD_FOCUS_ORDER: readonly (keyof PromotionFormValues)[] = [
  "name",
  "code",
  "description",
  "startsAt",
  "endsAt",
  "discountValue",
  "maxDiscountVnd",
  "totalLimit",
  "perUserLimit",
  "examKindIds",
  "examIds",
  "audienceClassIds",
  "audienceUserIds",
];

function mergeDescribedBy(...ids: (string | undefined | false)[]): string | undefined {
  const joined = ids.filter(Boolean).join(" ");
  return joined || undefined;
}

function focusSectionControl(containerId: string) {
  const container = document.getElementById(containerId);
  if (!container) return;
  container.scrollIntoView?.();
  const target = container.querySelector<HTMLElement>(
    'input:not(:disabled), button[role="checkbox"]:not(:disabled), [role="checkbox"]:not([aria-disabled="true"]), [tabindex="0"]:not(:disabled)',
  );
  if (target) {
    target.focus();
  } else {
    container.focus();
  }
}

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
  discountType: PromotionDiscountType,
): string {
  switch (field) {
    case "name":
      return code === "REQUIRED" ? "field:nameRequired" : "field:nameTooLong";
    case "description":
      return "field:descriptionTooLong";
    case "code":
      return code === "REQUIRED" ? "field:codeRequired" : "field:codeFormat";
    case "discountValue":
      return discountType === "PERCENT" ? "field:discountPercentRange" : "field:discountAmountMin";
    case "maxDiscountVnd":
      return "field:maxDiscountMin";
    case "startsAt":
      return "field:startsAtRequired";
    case "endsAt":
      return code === "FUTURE" ? "field:endsAtFuture" : "field:endsAfterStarts";
    case "totalLimit":
      return code === "MAX" ? "field:totalLimitMax" : "field:totalLimitMin";
    case "perUserLimit":
      return code === "MAX" ? "field:perUserLimitMax" : "field:perUserLimitMin";
    case "examKindIds":
      return "field:scopeKindsRequired";
    case "examIds":
      return "field:scopeExamsRequired";
    case "audienceClassIds":
    case "audienceUserIds":
      return "field:audienceTargetsRequired";
    default:
      return "error:VALIDATION_FAILED";
  }
}

export function PromotionForm({
  mode,
  initialKind = "AUTO",
  initialDetail,
  onConflict,
  conflictBanner,
}: {
  mode: "create" | "edit";
  initialKind?: PromotionKind;
  initialDetail?: PromotionDetail;
  onConflict?: (reason: "version" | "in_use") => void | Promise<void>;
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
      buildPromotionFormSchema((k) => k, {
        mode,
        usedCount,
      }),
    [mode, usedCount],
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
  const inUseBannerId = useId();
  const bundleNoticeId = useId();
  const codeFieldId = useId();
  const codeHintId = `${codeFieldId}-hint`;
  const codeErrorId = `${codeFieldId}-error`;
  const kindLabelId = useId();
  const scopePickerId = useId();
  const audiencePickerId = useId();

  const createMutation = useCreatePromotionMutation();
  const updateMutation = useUpdatePromotionMutation(initialDetail?.id ?? "");
  const [formBannerError, setFormBannerError] = useState<unknown>(null);
  const [suggestingCode, setSuggestingCode] = useState(false);
  const submittingRef = useRef(false);

  const values = useWatch({ control, defaultValue: defaultValues }) as PromotionFormValues;
  const pending = createMutation.isPending || updateMutation.isPending;
  const isBundleScope = values.scope === "BUNDLE";
  const scopeDisabled = isUsed || isBundleScope;

  const nameSectionId = useId();
  const scheduleSectionId = useId();
  const limitsSectionId = useId();
  const scopeSectionId = useId();
  const audienceSectionId = useId();

  const renderFieldError = (rawKey: string | undefined): string | undefined => {
    if (!rawKey) return undefined;
    if (rawKey.startsWith("error:")) {
      const errKey = rawKey.slice("error:".length);
      if (errKey === "PROMOTION_TOTAL_LIMIT_BELOW_USED") {
        return tErrors("PROMOTION_TOTAL_LIMIT_BELOW_USED", { used: usedCount });
      }
      return tErrors(errKey);
    }
    const fieldKey = rawKey.startsWith("field:") ? rawKey.slice("field:".length) : rawKey;
    return tFieldErrors(fieldKey);
  };

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
      setError("code", { type: "manual", message: "field:suggestCodeFailed" });
    } finally {
      setSuggestingCode(false);
    }
  };

  const onSubmit = async (submittedValues: PromotionFormValues) => {
    if (pending || submittingRef.current) return;
    submittingRef.current = true;
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
      if (errCode === "VERSION_CONFLICT") {
        if (onConflict) {
          await onConflict("version");
        } else {
          setFormBannerError(err);
        }
        return;
      }

      if (errCode === "PROMOTION_IN_USE") {
        if (onConflict) {
          await onConflict("in_use");
        } else {
          setFormBannerError(err);
        }
        return;
      }

      if (errCode === "VOUCHER_CODE_TAKEN") {
        setError("code", { type: "server", message: "error:VOUCHER_CODE_TAKEN" });
        setFocus("code");
        return;
      }

      if (errCode === "PROMOTION_TOTAL_LIMIT_BELOW_USED") {
        setError("totalLimit", { type: "server", message: "error:PROMOTION_TOTAL_LIMIT_BELOW_USED" });
        setFocus("totalLimit");
        return;
      }

      if (errCode === "PROMOTION_ENDS_AT_PAST") {
        setError("endsAt", { type: "server", message: "error:PROMOTION_ENDS_AT_PAST" });
        setFocus("endsAt");
        return;
      }

      if (errCode === "PROMOTION_SCOPE_INVALID") {
        setFormBannerError(err);
        if (submittedValues.scope === "EXAM_KIND") {
          setError("examKindIds", { type: "server", message: "error:PROMOTION_SCOPE_INVALID" });
          focusSectionControl(scopePickerId);
        } else if (submittedValues.scope === "EXAM") {
          setError("examIds", { type: "server", message: "error:PROMOTION_SCOPE_INVALID" });
          focusSectionControl(scopePickerId);
        }
        return;
      }

      const srvFieldErrors = promotionFieldErrors(err);
      if (srvFieldErrors.length > 0) {
        let focused = false;
        for (const fe of srvFieldErrors) {
          if (SERVER_FIELD_SET.has(fe.field as FieldPath<PromotionFormValues>)) {
            const path = fe.field as FieldPath<PromotionFormValues>;
            const msgKey = mapBackendFieldError(
              fe.field,
              fe.code,
              submittedValues.discountType,
            );
            setError(path, { type: "server", message: msgKey });
            if (!focused) {
              focused = true;
              if (path === "examKindIds" || path === "examIds") {
                focusSectionControl(scopePickerId);
              } else if (path === "audienceClassIds" || path === "audienceUserIds") {
                focusSectionControl(audiencePickerId);
              } else {
                try {
                  setFocus(path);
                } catch {
                  // ignore focus on custom pickers
                }
              }
            }
          }
        }
        setFormBannerError(err);
        return;
      }

      setFormBannerError(err);
    } finally {
      submittingRef.current = false;
    }
  };

  const onInvalid = (invalidErrors: FieldErrors<PromotionFormValues>) => {
    const firstPath = FIELD_FOCUS_ORDER.find((field) => Boolean(invalidErrors[field]));
    if (firstPath === "examKindIds" || firstPath === "examIds") {
      focusSectionControl(scopePickerId);
    } else if (firstPath === "audienceClassIds" || firstPath === "audienceUserIds") {
      focusSectionControl(audiencePickerId);
    }
  };

  const handleFormSubmit = (event: FormEvent<HTMLFormElement>) => {
    if (pending || submittingRef.current) {
      event.preventDefault();
      return;
    }
    void handleSubmit(onSubmit, onInvalid)(event);
  };

  const todoItems = useMemo(() => {
    const items: {
      key: string;
      label: string;
      anchor: string;
      focusField?: FieldPath<PromotionFormValues>;
      focusContainerId?: string;
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
      if (
        !upper ||
        upper.length < 3 ||
        upper.length > PROMOTION_CODE_MAX_LENGTH ||
        !/^[A-Z0-9-]+$/.test(upper)
      ) {
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
        focusContainerId: scopePickerId,
      });
    }
    if (values.scope === "EXAM" && values.examIds.length === 0) {
      items.push({
        key: "scopeTargets",
        label: t("form.aside.todoItems.scopeTargets"),
        anchor: `#${scopeSectionId}`,
        focusContainerId: scopePickerId,
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
        focusContainerId: audiencePickerId,
      });
    }
    return items;
  }, [
    audiencePickerId,
    audienceSectionId,
    isUsed,
    limitsSectionId,
    nameSectionId,
    scheduleSectionId,
    scopePickerId,
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

  const renderedFormBannerError = formBannerError
    ? resolvePromotionErrorMessage(formBannerError, tErrors, { usedCount })
    : null;
  const codeErrorText = renderFieldError(errors.code?.message);

  return (
    <div className="@container flex flex-col gap-5">
      {conflictBanner}

      {isUsed && (
        <div
          id={inUseBannerId}
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

      {renderedFormBannerError && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-lg border border-danger/40 bg-danger-bg p-4 text-danger"
        >
          <AlertCircle aria-hidden className="mt-0.5 size-5 shrink-0" />
          <div className="flex flex-col gap-0.5">
            <p className="text-body font-semibold">{t("form.banner.formErrorTitle")}</p>
            <p className="text-caption">{renderedFormBannerError}</p>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 @4xl:grid-cols-[minmax(0,1fr)_20rem] @4xl:items-start">
        <form
          id={formId}
          onSubmit={handleFormSubmit}
          noValidate
          className="@container flex min-w-0 flex-col gap-5"
        >
          {/* 1. General info */}
          <Card id={nameSectionId} className="flex flex-col gap-4 p-5">
            <h2 className="text-title font-semibold text-text">{t("form.sections.general")}</h2>

            <div className="flex flex-col gap-2">
              <div className="flex items-center gap-2">
                <span id={kindLabelId} className="text-field-label font-medium text-text-secondary">
                  {t("form.fields.kind")}
                </span>
                {(mode === "edit" || isUsed) && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
              </div>
              <div
                className="grid grid-cols-1 gap-3 @md:grid-cols-2"
                role="radiogroup"
                aria-labelledby={kindLabelId}
                aria-describedby={isUsed ? inUseBannerId : undefined}
              >
                {KIND_OPTIONS.map((option, index) => {
                  const selected = values.kind === option;
                  const kindDisabled = mode === "edit";
                  return (
                    <button
                      key={option}
                      type="button"
                      role="radio"
                      aria-checked={selected}
                      tabIndex={selected ? 0 : -1}
                      disabled={kindDisabled}
                      aria-describedby={isUsed ? inUseBannerId : undefined}
                      onClick={() => {
                        if (!kindDisabled) {
                          setValue("kind", option, { shouldDirty: true, shouldValidate: true });
                        }
                      }}
                      onKeyDown={(event) => {
                        if (!kindDisabled) {
                          handleRadioGroupKeyDown(event, KIND_OPTIONS, index, (next) =>
                            setValue("kind", next, { shouldDirty: true, shouldValidate: true }),
                          );
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

            <FormField label={t("form.fields.name")} required error={renderFieldError(errors.name?.message)}>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  maxLength={PROMOTION_NAME_MAX_LENGTH}
                  placeholder={t("form.fields.namePlaceholder")}
                  {...register("name")}
                />
              )}
            </FormField>

            {values.kind === "VOUCHER" && (
              <div className="flex flex-col gap-1.5">
                <div className="flex flex-wrap items-center gap-2">
                  <Label htmlFor={codeFieldId} required>
                    {t("form.fields.code")}
                  </Label>
                  {isUsed && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
                </div>
                <div className="flex flex-col gap-2 @sm:flex-row">
                  <Controller
                    control={control}
                    name="code"
                    render={({ field }) => (
                      <Input
                        id={codeFieldId}
                        aria-required="true"
                        aria-invalid={codeErrorText ? true : undefined}
                        aria-describedby={mergeDescribedBy(
                          codeErrorText && codeErrorId,
                          codeHintId,
                          isUsed && inUseBannerId,
                        )}
                        disabled={isUsed}
                        maxLength={PROMOTION_CODE_MAX_LENGTH}
                        autoComplete="off"
                        autoCapitalize="characters"
                        spellCheck={false}
                        placeholder={t("form.fields.codePlaceholder")}
                        className="font-mono uppercase"
                        name={field.name}
                        ref={field.ref}
                        value={field.value}
                        onBlur={field.onBlur}
                        onChange={(event) => field.onChange(event.target.value.toUpperCase())}
                      />
                    )}
                  />
                  <Button
                    type="button"
                    variant="secondary"
                    loading={suggestingCode}
                    disabled={isUsed}
                    aria-describedby={isUsed ? inUseBannerId : undefined}
                    onClick={() => void handleSuggestCode()}
                    className="shrink-0"
                  >
                    <Sparkles />
                    <span>{t("actions.suggestCode")}</span>
                  </Button>
                </div>
                <p id={codeHintId} className="text-caption text-text-muted">
                  {t("form.fields.codeHint")}
                </p>
                {codeErrorText && (
                  <p id={codeErrorId} role="alert" className="text-caption text-danger">
                    {codeErrorText}
                  </p>
                )}
              </div>
            )}

            <FormField
              label={t("form.fields.description")}
              error={renderFieldError(errors.description?.message)}
            >
              {(controlProps) => (
                <Textarea
                  {...controlProps}
                  rows={3}
                  maxLength={PROMOTION_DESCRIPTION_MAX_LENGTH}
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
            <div className="grid grid-cols-1 gap-4 @md:grid-cols-2">
              <FormField
                label={t("form.fields.startsAt")}
                required={!isUsed}
                error={renderFieldError(errors.startsAt?.message)}
              >
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    aria-describedby={mergeDescribedBy(
                      controlProps["aria-describedby"],
                      isUsed && inUseBannerId,
                    )}
                    type="datetime-local"
                    disabled={isUsed}
                    {...register("startsAt")}
                  />
                )}
              </FormField>

              <FormField
                label={t("form.fields.endsAt")}
                required
                error={renderFieldError(errors.endsAt?.message)}
              >
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

            <div className="grid grid-cols-1 gap-4 @md:grid-cols-2">
              <FormField label={t("form.fields.discountType")}>
                {(controlProps) => (
                  <Controller
                    control={control}
                    name="discountType"
                    render={({ field }) => (
                      <Select
                        {...controlProps}
                        aria-describedby={mergeDescribedBy(
                          controlProps["aria-describedby"],
                          isUsed && inUseBannerId,
                        )}
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
                error={renderFieldError(errors.discountValue?.message)}
              >
                {(controlProps) => (
                  <Controller
                    control={control}
                    name="discountValue"
                    render={({ field }) => (
                      <Input
                        {...controlProps}
                        aria-describedby={mergeDescribedBy(
                          controlProps["aria-describedby"],
                          isUsed && inUseBannerId,
                        )}
                        type="text"
                        inputMode="numeric"
                        disabled={isUsed}
                        name={field.name}
                        ref={field.ref}
                        value={field.value}
                        onBlur={field.onBlur}
                        onChange={(event) => field.onChange(formatPositiveInteger(event.target.value))}
                      />
                    )}
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
                error={renderFieldError(errors.maxDiscountVnd?.message)}
              >
                {(controlProps) => (
                  <Controller
                    control={control}
                    name="maxDiscountVnd"
                    render={({ field }) => (
                      <Input
                        {...controlProps}
                        aria-describedby={mergeDescribedBy(
                          controlProps["aria-describedby"],
                          isUsed && inUseBannerId,
                        )}
                        type="text"
                        inputMode="numeric"
                        disabled={isUsed}
                        placeholder={t("form.fields.maxDiscountVndPlaceholder")}
                        name={field.name}
                        ref={field.ref}
                        value={field.value}
                        onBlur={field.onBlur}
                        onChange={(event) => field.onChange(formatPositiveInteger(event.target.value))}
                      />
                    )}
                  />
                )}
              </FormField>
            )}

            <div className="grid grid-cols-1 gap-4 @md:grid-cols-2">
              <FormField
                label={t("form.fields.totalLimit")}
                hint={
                  usedCount > 0
                    ? t("form.fields.totalLimitHint", { used: usedCount })
                    : t("form.fields.totalLimitHintUnlimited")
                }
                error={renderFieldError(errors.totalLimit?.message)}
              >
                {(controlProps) => (
                  <Controller
                    control={control}
                    name="totalLimit"
                    render={({ field }) => (
                      <Input
                        {...controlProps}
                        type="text"
                        inputMode="numeric"
                        maxLength={10}
                        placeholder={t("form.fields.totalLimitPlaceholder")}
                        name={field.name}
                        ref={field.ref}
                        value={field.value}
                        onBlur={field.onBlur}
                        onChange={(event) => field.onChange(formatPositiveInteger(event.target.value))}
                      />
                    )}
                  />
                )}
              </FormField>

              {values.kind === "VOUCHER" && (
                <FormField
                  label={t("form.fields.perUserLimit")}
                  hint={t("form.fields.perUserLimitHint")}
                  error={renderFieldError(errors.perUserLimit?.message)}
                >
                  {(controlProps) => (
                    <Controller
                      control={control}
                      name="perUserLimit"
                      render={({ field }) => (
                        <Input
                          {...controlProps}
                          aria-describedby={mergeDescribedBy(
                            controlProps["aria-describedby"],
                            isUsed && inUseBannerId,
                          )}
                          type="text"
                          inputMode="numeric"
                          maxLength={10}
                          disabled={isUsed}
                          placeholder={t("common.unlimited")}
                          name={field.name}
                          ref={field.ref}
                          value={field.value}
                          onBlur={field.onBlur}
                          onChange={(event) => field.onChange(formatPositiveInteger(event.target.value))}
                        />
                      )}
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
                        aria-describedby={isUsed ? inUseBannerId : undefined}
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
          <Card id={scopeSectionId} tabIndex={-1} className="flex flex-col gap-4 p-5">
            <div className="flex items-center gap-2">
              <h2 className="text-title font-semibold text-text">{t("form.sections.scope")}</h2>
              {scopeDisabled && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
            </div>

            {isBundleScope && (
              <div
                id={bundleNoticeId}
                className="flex flex-col gap-2 rounded-md border border-border bg-subtle p-3"
              >
                <p className="flex items-center gap-1.5 text-caption font-medium text-text-secondary">
                  <Lock aria-hidden className="size-3.5 shrink-0 text-text-muted" />
                  <span>{t("form.fields.scopeBundleNotice")}</span>
                </p>
                {initialDetail?.scopeItems && initialDetail.scopeItems.length > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {initialDetail.scopeItems.map((item) => (
                      <Chip
                        key={item.id}
                        variant="tag"
                        className="h-auto max-w-full whitespace-normal py-1"
                      >
                        <span data-exam-content className="wrap-anywhere">
                          {item.name ?? item.code ?? item.id.slice(0, 8)}
                        </span>
                      </Chip>
                    ))}
                  </div>
                )}
              </div>
            )}

            <div
              className="grid grid-cols-1 gap-3 @lg:grid-cols-3"
              role="radiogroup"
              aria-label={t("form.fields.scope")}
              aria-describedby={mergeDescribedBy(
                isUsed && inUseBannerId,
                isBundleScope && bundleNoticeId,
              )}
            >
              {SCOPE_OPTIONS.map((option, index) => {
                const selected = values.scope === option;
                return (
                  <button
                    key={option}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    tabIndex={selected ? 0 : -1}
                    disabled={scopeDisabled}
                    aria-describedby={mergeDescribedBy(
                      isUsed && inUseBannerId,
                      isBundleScope && bundleNoticeId,
                    )}
                    onClick={() => {
                      if (!scopeDisabled) {
                        setValue("scope", option, { shouldDirty: true, shouldValidate: true });
                        clearErrors(["examKindIds", "examIds"]);
                      }
                    }}
                    onKeyDown={(event) => {
                      if (!scopeDisabled) {
                        handleRadioGroupKeyDown(event, SCOPE_OPTIONS, index, (next) => {
                          setValue("scope", next, { shouldDirty: true, shouldValidate: true });
                          clearErrors(["examKindIds", "examIds"]);
                        });
                      }
                    }}
                    className={cn(
                      "flex flex-col items-start gap-1 rounded-lg border p-3 text-left transition-colors duration-150 motion-reduce:transition-none",
                      selected ? "border-primary-600 bg-primary-50" : "border-border bg-surface hover:bg-subtle",
                      scopeDisabled && "cursor-not-allowed opacity-70",
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
              <div id={scopePickerId} tabIndex={-1} className="outline-hidden">
                <Controller
                  control={control}
                  name="examKindIds"
                  render={({ field }) => (
                    <ExamKindPicker
                      selectedIds={field.value}
                      onChange={field.onChange}
                      disabled={isUsed}
                      initialScopeItems={initialDetail?.scopeItems}
                      error={renderFieldError(errors.examKindIds?.message)}
                      inUseDescriptionId={isUsed ? inUseBannerId : undefined}
                    />
                  )}
                />
              </div>
            )}

            {values.scope === "EXAM" && (
              <div id={scopePickerId} tabIndex={-1} className="outline-hidden">
                <Controller
                  control={control}
                  name="examIds"
                  render={({ field }) => (
                    <ExamScopePicker
                      selectedIds={field.value}
                      onChange={field.onChange}
                      disabled={isUsed}
                      initialScopeItems={initialDetail?.scopeItems}
                      error={renderFieldError(errors.examIds?.message)}
                      inUseDescriptionId={isUsed ? inUseBannerId : undefined}
                    />
                  )}
                />
              </div>
            )}
          </Card>

          {/* 5. Audience (VOUCHER only) */}
          {values.kind === "VOUCHER" && (
            <Card id={audienceSectionId} tabIndex={-1} className="flex flex-col gap-4 p-5">
              <div className="flex items-center gap-2">
                <h2 className="text-title font-semibold text-text">{t("form.sections.audience")}</h2>
                {isUsed && <Chip variant="tag">{t("form.lockedChip")}</Chip>}
              </div>

              <div
                className="grid grid-cols-1 gap-3 @lg:grid-cols-3"
                role="radiogroup"
                aria-label={t("form.fields.audience")}
                aria-describedby={isUsed ? inUseBannerId : undefined}
              >
                {AUDIENCE_OPTIONS.map((option, index) => {
                  const selected = values.audience === option;
                  return (
                    <button
                      key={option}
                      type="button"
                      role="radio"
                      aria-checked={selected}
                      tabIndex={selected ? 0 : -1}
                      disabled={isUsed}
                      aria-describedby={isUsed ? inUseBannerId : undefined}
                      onClick={() => {
                        if (!isUsed) {
                          setValue("audience", option, { shouldDirty: true, shouldValidate: true });
                          clearErrors(["audienceClassIds", "audienceUserIds"]);
                        }
                      }}
                      onKeyDown={(event) => {
                        if (!isUsed) {
                          handleRadioGroupKeyDown(event, AUDIENCE_OPTIONS, index, (next) => {
                            setValue("audience", next, { shouldDirty: true, shouldValidate: true });
                            clearErrors(["audienceClassIds", "audienceUserIds"]);
                          });
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
                <div id={audiencePickerId} tabIndex={-1} className="outline-hidden">
                  <AudienceTargetsPicker
                    selectedClassIds={values.audienceClassIds}
                    selectedUserIds={values.audienceUserIds}
                    onChangeClasses={(next) => {
                      setValue("audienceClassIds", next, { shouldDirty: true, shouldValidate: true });
                      clearErrors(["audienceClassIds", "audienceUserIds"]);
                    }}
                    onChangeUsers={(next) => {
                      setValue("audienceUserIds", next, { shouldDirty: true, shouldValidate: true });
                      clearErrors(["audienceClassIds", "audienceUserIds"]);
                    }}
                    disabled={isUsed}
                    initialClasses={initialDetail?.audienceClasses}
                    initialUsers={initialDetail?.audienceUsers}
                    error={renderFieldError(
                      errors.audienceClassIds?.message ?? errors.audienceUserIds?.message,
                    )}
                    inUseDescriptionId={isUsed ? inUseBannerId : undefined}
                  />
                </div>
              )}
            </Card>
          )}
        </form>

        {/* Sticky Sidebar Aside */}
        <aside className="flex flex-col gap-4 @4xl:sticky @4xl:top-4">
          <Card className="flex flex-col gap-3 p-4">
            <h2 className="text-body font-semibold text-text">{t("form.aside.summaryTitle")}</h2>
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
                loading={pending}
                className="w-full"
              >
                {mode === "create" ? t("actions.create") : t("actions.save")}
              </Button>
              <Button type="button" variant="secondary" asChild className="w-full">
                <Link href={initialDetail ? `/promotions/${initialDetail.id}` : "/promotions"}>
                  {t("actions.cancel")}
                </Link>
              </Button>
            </div>
          </Card>

          <Card className="flex flex-col gap-2.5 p-4">
            <h2 className="text-body font-semibold text-text">
              {todoItems.length === 0
                ? t("form.aside.todoTitle")
                : t("form.aside.todoRemaining", { count: todoItems.length })}
            </h2>
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
                        } else if (item.focusContainerId) {
                          event.preventDefault();
                          focusSectionControl(item.focusContainerId);
                        }
                      }}
                      className={cn(
                        "relative inline-block text-primary-text hover:underline pointer-coarse:after:absolute pointer-coarse:after:-inset-2",
                        focusRing,
                      )}
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
