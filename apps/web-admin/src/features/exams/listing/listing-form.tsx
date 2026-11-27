"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Button, Card, Checkbox, FormField, Input, notify, Textarea } from "@thi360/ui";
import { ArrowLeft, ArrowRight, CircleAlert, Lock, Save } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useId, useMemo, useState, type ReactNode } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { useUnsavedChangesGuard } from "@/features/shell/unsaved-changes-guard";
import { getErrorStatus } from "@/lib/api/query-client";
import { ChoiceCardGroup } from "../components/choice-card-group";
import { EditorStepToolbar } from "../components/editor-step-toolbar";
import { ExamDraftLeaveDialog } from "../components/exam-draft-leave-dialog";
import { reviewHref } from "../components/exam-review-shared";
import { examTabHref, isFromReview, reviewReturnHref } from "../editor-tabs";
import { ExamsApiError } from "../errors";
import type { ExamDetail } from "../types";
import {
  FORM_FIELD_OF,
  formValuesOf,
  integerOf,
  LISTING_LIMITS,
  listingSchema,
  priceDigits,
  termsOfValues,
  toListingRequest,
  type ListingFormValues,
} from "./form";
import { ListingCover, useCoverField } from "./listing-cover";
import { ListingCardPreview, ListingCheckSummary } from "./listing-aside";
import { useSaveExamListing } from "./queries";
import { RESULT_VIEWS, type ExamListing } from "./types";

interface ListingFormProps {
  exam: ExamDetail;
  listing: ExamListing;
  /** Có `EXAM.MODIFY`; không thì tab chỉ xem (AC-8). */
  canEdit: boolean;
  /** Máy chủ trả 412: cha nạp lại dữ liệu mới và giữ thông báo. */
  onConflict: () => void;
  /** Lưu xong: cha bỏ thông báo xung đột. */
  onSaved: () => void;
}

const BENEFITS = ["benefitAiExplanation", "benefitStrengthWeakness", "benefitPractice"] as const;

const FIELD_PARAM_TARGET: Readonly<Record<string, string>> = {
  listPriceVnd: "price",
  price: "price",
  attemptsGranted: "attempts",
  attempts: "attempts",
  description: "description",
  coverAssetId: "cover",
  cover: "cover",
};

type ListingSubmitIntent = "continue" | "review" | "exit" | "leave";

export function ListingForm({ exam, listing, canEdit, onConflict, onSaved }: ListingFormProps) {
  const t = useTranslations("exams.listing");
  const tv = useTranslations("exams.listing.validation");
  const format = useFormatter();
  const formId = useId();
  const [fromReview] = useState(() => (typeof window !== "undefined" ? isFromReview(window.location.search) : false));
  const [activeSubmitIntent, setActiveSubmitIntent] = useState<ListingSubmitIntent>(fromReview ? "review" : "continue");
  const [leaveError, setLeaveError] = useState<string | null>(null);
  const save = useSaveExamListing(exam.id, {
    onSaved: () => {
      notify.success(t("saved"));
      onSaved();
    },
  });
  const cover = useCoverField(exam.id, listing.coverAssetId && listing.coverUrl ? { assetId: listing.coverAssetId, url: listing.coverUrl } : null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const schema = useMemo(
    () =>
      listingSchema({
        summaryMax: tv("summaryMax", { max: LISTING_LIMITS.summary }),
        descriptionMax: tv("descriptionMax", { max: LISTING_LIMITS.description }),
        objectivesMax: tv("objectivesMax", { max: LISTING_LIMITS.objectives }),
        audienceMax: tv("audienceMax", { max: LISTING_LIMITS.audience }),
        benefitNoteMax: tv("benefitNoteMax", { max: LISTING_LIMITS.benefitNote }),
        priceInvalid: tv("priceInvalid"),
        attemptsRange: tv("attemptsRange", { min: LISTING_LIMITS.attemptsMin, max: LISTING_LIMITS.attemptsMax }),
      }),
    [tv],
  );
  const form = useForm<ListingFormValues>({
    resolver: zodResolver(schema),
    defaultValues: formValuesOf(listing),
    mode: "onTouched",
    shouldFocusError: true,
  });
  const { control, register, handleSubmit, setError, reset, formState } = form;
  const values = useWatch({ control });
  const live = { ...formValuesOf(listing), ...values } as ListingFormValues;
  const terms = termsOfValues(live, cover.cover?.assetId ?? null);
  const coverChanged = (cover.cover?.assetId ?? null) !== listing.coverAssetId;
  const unchanged = !formState.isDirty && !coverChanged;
  const hasUnsavedChanges = canEdit && !unchanged;
  const guard = useUnsavedChangesGuard(hasUnsavedChanges);
  const disabled = !canEdit;
  const minPrice = listing.minPriceVnd;

  // Mở từ danh sách lỗi ở bước 4 (`?field=listPriceVnd&from=review`): cuộn và đưa tiêu điểm vào đúng ô cần sửa.
  useEffect(() => {
    if (typeof window === "undefined") return;
    const rawField = new URLSearchParams(window.location.search).get("field");
    if (!rawField) return;
    const target = FIELD_PARAM_TARGET[rawField] ?? rawField;
    const frame = requestAnimationFrame(() => {
      const element = document.querySelector<HTMLElement>(`[data-listing-field="${CSS.escape(target)}"]`);
      if (!element) return;
      element.scrollIntoView?.({ block: "center" });
      element.focus();
    });
    return () => cancelAnimationFrame(frame);
  }, []);

  /** Chữ lỗi theo mã của máy chủ (`EXAM_LISTING_INVALID`); chữ của máy chủ không in ra. */
  const serverMessage = (field: keyof ListingFormValues, code: string): string => {
    if (code === "TOO_LONG") return tv("tooLong", { max: LISTING_LIMITS[field as keyof typeof LISTING_LIMITS] ?? 0 });
    if (code === "NEGATIVE") return tv("priceInvalid");
    if (code === "OUT_OF_RANGE") {
      return field === "attempts"
        ? tv("attemptsRange", { min: LISTING_LIMITS.attemptsMin, max: LISTING_LIMITS.attemptsMax })
        : tv("priceInvalid");
    }
    return tv("fieldInvalid");
  };

  const navigateForIntent = (intent: ListingSubmitIntent, savedChanges: boolean, leaveHref?: string | null) => {
    setLeaveError(null);
    if (intent === "leave") {
      if (leaveHref) {
        guard.leaveTo(leaveHref);
      } else {
        guard.leave();
      }
      return;
    }
    if (intent === "exit") {
      guard.leaveTo("/exams");
      return;
    }
    if (intent === "review") {
      guard.leaveTo(savedChanges ? reviewReturnHref(exam.id) : reviewHref(exam.id));
      return;
    }
    guard.leaveTo(examTabHref(exam.id, "review"));
  };

  const submitWithIntent = (intent: ListingSubmitIntent) => {
    setActiveSubmitIntent(intent);
    const pendingLeaveHref = intent === "leave" ? guard.pendingHref : null;
    return handleSubmit(
      async (submitted) => {
        setSubmitError(null);
        setLeaveError(null);
        if (unchanged) {
          navigateForIntent(intent, false, pendingLeaveHref);
          return;
        }
        try {
          await save.mutateAsync(toListingRequest(submitted, cover.cover?.assetId ?? null, listing.revision));
          // Chạy điều hướng sau khi promise giải quyết để không bị bỏ qua khi `ListingTab` dựng lại form theo `listing.revision` mới.
          navigateForIntent(intent, true, pendingLeaveHref);
        } catch (error) {
          if (getErrorStatus(error) === 412) {
            onConflict();
            if (intent === "leave") setLeaveError(t("conflict.title"));
            return;
          }
          const fieldErrors = error instanceof ExamsApiError && getErrorStatus(error) === 422 ? error.fieldErrors : [];
          const mapped = fieldErrors.flatMap((item) => {
            const field = FORM_FIELD_OF[item.field ?? ""];
            return field ? [{ field, code: item.code ?? "" }] : [];
          });
          mapped.forEach(({ field, code }, index) => {
            setError(field, { message: serverMessage(field, code) }, { shouldFocus: index === 0 });
          });
          let message: string | null = null;
          if (fieldErrors.some((item) => item.field === "coverAssetId")) {
            message = t("errors.coverNotFound");
            setSubmitError(message);
          } else if (mapped.length === 0) {
            message = t(getErrorStatus(error) === 422 ? "errors.invalid" : "errors.saveFailed");
            setSubmitError(message);
          }
          if (intent === "leave") {
            setLeaveError(message ?? t("errors.invalid"));
          }
        }
      },
      () => {
        if (intent === "leave") {
          setLeaveError(t("errors.invalid"));
        }
      },
    );
  };

  const handleSaveDraftAndExit = () => {
    if (unchanged) {
      guard.leaveTo("/exams");
      return;
    }
    void submitWithIntent("exit")();
  };

  const handleBack = () => {
    guard.navigate(examTabHref(exam.id, "structure"));
  };

  const handleLeaveSaveAndGo = () => {
    setLeaveError(null);
    void submitWithIntent("leave")();
  };

  const handleLeaveDiscardAndGo = () => {
    reset(formValuesOf(listing));
    cover.reset();
    setSubmitError(null);
    setLeaveError(null);
    guard.leave();
  };

  const handleLeaveStay = () => {
    setLeaveError(null);
    guard.stay();
  };

  const counter = (field: "summary" | "description" | "objectives" | "audience" | "benefitNote", extra?: string) => {
    const text = t("counter", { count: live[field].length, max: LISTING_LIMITS[field] });
    return extra ? `${text} ${extra}` : text;
  };

  return (
    <div className="@container">
      <div className="grid grid-cols-1 items-start gap-6 @5xl:grid-cols-[minmax(0,1fr)_22rem]">
        <form
          id={formId}
          aria-label={t("formLabel")}
          noValidate
          onSubmit={(event) => void submitWithIntent(fromReview ? "review" : "continue")(event)}
          className="flex min-w-0 flex-col gap-4"
        >
          <EditorStepToolbar
            testId="listing-toolbar"
            start={
              <p className="flex flex-wrap gap-x-3 gap-y-1 text-caption text-text-muted">
                {listing.updatedAt && <span>{t("updatedAt", { time: format.dateTime(new Date(listing.updatedAt), "dateTime") })}</span>}
                <span>{t("effectNote")}</span>
              </p>
            }
          >
            <Button type="button" variant="secondary" size="sm" disabled={save.isPending} onClick={handleBack}>
              <ArrowLeft aria-hidden />
              {t("back")}
            </Button>
            {canEdit ? (
              <>
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  loading={save.isPending && activeSubmitIntent === "exit"}
                  disabled={save.isPending || cover.uploading}
                  onClick={handleSaveDraftAndExit}
                >
                  <Save aria-hidden />
                  {t("saveDraftAndExit")}
                </Button>
                <Button
                  type="submit"
                  size="sm"
                  loading={save.isPending && activeSubmitIntent !== "exit"}
                  disabled={cover.uploading}
                >
                  {fromReview ? t("saveAndReturnReview") : t("continueToReview")}
                  <ArrowRight aria-hidden />
                </Button>
              </>
            ) : (
              <Button asChild size="sm">
                <Link href={examTabHref(exam.id, "review")}>
                  {t("continueToReview")}
                  <ArrowRight aria-hidden />
                </Link>
              </Button>
            )}
          </EditorStepToolbar>
          {!canEdit && (
            <p role="note" className="flex items-start gap-2 rounded-lg border border-border bg-subtle px-3.5 py-2.5 text-body text-text-secondary">
              <Lock aria-hidden className="mt-0.5 size-4 shrink-0" />
              {t("readOnly")}
            </p>
          )}
          {submitError && (
            <p role="alert" className="flex items-start gap-2 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-2.5 text-body text-danger">
              <CircleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
              {submitError}
            </p>
          )}

          <Section id="intro" index={1} title={t("sections.intro")}>
            <ListingCover value={cover} fallbackLabel={exam.code} readOnly={!canEdit} />
            <FormField label={t("fields.summary")} error={formState.errors.summary?.message} hint={counter("summary")}>
              {(controlProps) => <Textarea {...controlProps} rows={2} maxLength={LISTING_LIMITS.summary} disabled={disabled} {...register("summary")} />}
            </FormField>
            <FormField
              label={t("fields.description")}
              required
              error={formState.errors.description?.message}
              hint={counter("description", t("hints.description"))}
            >
              {(controlProps) => <Textarea {...controlProps} data-listing-field="description" rows={5} maxLength={LISTING_LIMITS.description} disabled={disabled} {...register("description")} />}
            </FormField>
            <div className="grid grid-cols-[repeat(auto-fit,minmax(min(18.75rem,100%),1fr))] gap-4">
              <FormField label={t("fields.objectives")} error={formState.errors.objectives?.message} hint={counter("objectives")}>
                {(controlProps) => <Textarea {...controlProps} rows={5} maxLength={LISTING_LIMITS.objectives} disabled={disabled} {...register("objectives")} />}
              </FormField>
              <FormField label={t("fields.audience")} error={formState.errors.audience?.message} hint={counter("audience")}>
                {(controlProps) => <Textarea {...controlProps} rows={5} maxLength={LISTING_LIMITS.audience} disabled={disabled} {...register("audience")} />}
              </FormField>
            </div>
          </Section>

          <Section id="price" index={2} title={t("sections.price")}>
            <div className="grid grid-cols-[repeat(auto-fit,minmax(min(16.25rem,100%),1fr))] gap-4">
              <FormField
                label={t("fields.price")}
                required
                error={formState.errors.price?.message}
                hint={t("hints.price", { hasMin: minPrice === null ? "no" : "yes", min: minPrice === null ? "" : format.number(minPrice) })}
              >
                {(controlProps) => (
                  <Controller
                    control={control}
                    name="price"
                    render={({ field }) => {
                      const amountId = `${controlProps.id}-amount`;
                      const amount = integerOf(field.value);
                      return (
                        <div className="flex flex-col gap-1">
                          <div className="flex items-center gap-2">
                            {/* Keep raw digits in input so focus/select/paste does not shift cursor or break Playwright fill(). */}
                            <Input
                              {...controlProps}
                              data-listing-field="price"
                              aria-describedby={[controlProps["aria-describedby"], amount !== null ? amountId : ""].filter(Boolean).join(" ") || undefined}
                              name={field.name}
                              ref={field.ref}
                              type="text"
                              inputMode="numeric"
                              autoComplete="off"
                              disabled={disabled}
                              className="text-right tabular-nums"
                              value={field.value}
                              onChange={(event) => field.onChange(priceDigits(event.target.value))}
                              onBlur={field.onBlur}
                            />
                            <span aria-hidden className="text-text-secondary">
                              {t("currency")}
                            </span>
                          </div>
                          {amount !== null && (
                            <p id={amountId} className="text-caption text-text-muted">
                              {t("preview.money", { amount: format.number(amount) })}
                            </p>
                          )}
                        </div>
                      );
                    }}
                  />
                )}
              </FormField>
              <FormField
                label={t("fields.attempts")}
                required
                error={formState.errors.attempts?.message}
                hint={t("hints.attempts", { min: LISTING_LIMITS.attemptsMin, max: LISTING_LIMITS.attemptsMax })}
              >
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    data-listing-field="attempts"
                    type="number"
                    min={LISTING_LIMITS.attemptsMin}
                    max={LISTING_LIMITS.attemptsMax}
                    step={1}
                    disabled={disabled}
                    className="tabular-nums"
                    {...register("attempts")}
                  />
                )}
              </FormField>
            </div>
          </Section>

          <Section id="benefit" index={3} title={t("sections.benefit")}>
            <fieldset className="flex min-w-0 flex-col gap-1" disabled={disabled}>
              <legend className="mb-1.5 text-field-label font-medium text-text-secondary">{t("benefit.legend")}</legend>
              {BENEFITS.map((name) => (
                <Controller
                  key={name}
                  control={control}
                  name={name}
                  render={({ field }) => (
                    <label className="flex min-h-11 cursor-pointer items-start gap-2.5 py-2">
                      <Checkbox
                        checked={field.value}
                        onCheckedChange={(checked) => field.onChange(checked === true)}
                        onBlur={field.onBlur}
                        ref={field.ref}
                        disabled={disabled}
                        className="mt-0.5"
                      />
                      <span className="flex flex-col">
                        <span className="font-medium text-text">{t(`benefit.items.${name}.label`)}</span>
                        <span className="text-caption text-text-muted">{t(`benefit.items.${name}.description`)}</span>
                      </span>
                    </label>
                  )}
                />
              ))}
            </fieldset>
            <FormField label={t("fields.benefitNote")} error={formState.errors.benefitNote?.message} hint={counter("benefitNote")}>
              {(controlProps) => <Textarea {...controlProps} rows={2} maxLength={LISTING_LIMITS.benefitNote} disabled={disabled} {...register("benefitNote")} />}
            </FormField>
          </Section>

          <Section id="result" index={4} title={t("sections.result")}>
            <Controller
              control={control}
              name="resultView"
              render={({ field }) => (
                <ChoiceCardGroup
                  variant="pill"
                  legend={t("result.legend")}
                  value={field.value}
                  disabled={disabled}
                  onChange={field.onChange}
                  options={RESULT_VIEWS.map((value) => ({
                    value,
                    label: t(`result.options.${value}.label`),
                    hint: t(`result.options.${value}.description`),
                  }))}
                />
              )}
            />
            <p className="text-caption text-text-muted">{t("result.note")}</p>
          </Section>
        </form>

        <aside aria-labelledby="listing-preview-title" className="flex min-w-0 flex-col gap-3 @5xl:sticky @5xl:top-4">
          <ListingCardPreview exam={exam} terms={terms} coverUrl={cover.cover?.url ?? null} listing={listing} />
          <ListingCheckSummary examId={exam.id} issues={listing.checkIssues} />
        </aside>
      </div>

      {canEdit && (
        <ExamDraftLeaveDialog
          open={guard.pendingHref !== null}
          saving={save.isPending}
          error={leaveError}
          examId={exam.id}
          pendingHref={guard.pendingHref}
          onSaveAndGo={handleLeaveSaveAndGo}
          onDiscardAndGo={handleLeaveDiscardAndGo}
          onStay={handleLeaveStay}
        />
      )}
    </div>
  );
}

function Section({ id, index, title, children }: { id: string; index: number; title: string; children: ReactNode }) {
  const headingId = `listing-section-${id}`;
  return (
    <Card padding="lg" className="flex flex-col gap-4" role="group" aria-labelledby={headingId}>
      <h2 id={headingId} className="flex items-center gap-2.5 text-title font-bold text-text">
        <span aria-hidden className="inline-flex size-6.5 items-center justify-center rounded-full bg-primary-50 text-label text-primary-text">
          {index}
        </span>
        {title}
      </h2>
      {children}
    </Card>
  );
}
