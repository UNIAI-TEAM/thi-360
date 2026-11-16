"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Button, Card, cn, EmptyState, focusRing, LiveStatus, notify, Skeleton, StatusChip } from "@thi360/ui";
import { ArrowLeft, CircleCheckBig, ClipboardCheck, Save, TriangleAlert } from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useId, useMemo, useRef, useState, type Ref } from "react";
import { FormProvider, useForm, useFormState, useWatch, type FieldErrors, type FieldPath } from "react-hook-form";
import { MockDataBanner } from "@/components/mock-data-banner";
import { useContentReference } from "@/features/content/queries";
import { getErrorStatus } from "@/lib/api/query-client";
import { MockApiError } from "@/lib/mock/content-mock";
import { defaultGradingApiDeps, GRADING_ERROR_CODES, type GradingApiDeps, type GradingErrorCode } from "../api";
import {
  essayInputTotal,
  findMissingScores,
  gradingSchema,
  scoreFieldName,
  toFormValues,
  toSaveRequest,
  type GradingFormValues,
} from "../grading-form";
import { useFinishGrading, useGradingAttempt, useSaveGradingDraft } from "../queries";
import { roundScore, sumScores } from "../scoring";
import { GRADING_STATUS_TONE } from "../status";
import type { GradingAttemptDetail } from "../types";
import {
  EssayNavigator,
  EssayQuestionPanel,
  EssayRubricForm,
  EssayRubricResult,
  type EssayNavigatorItem,
} from "./grading-essay-panels";

/** Tham số URL giữ câu đang mở, để tải lại trang hay gửi đường dẫn vẫn mở đúng câu. */
const QUESTION_PARAM = "question";

function decimalSeparatorOf(locale: string): string {
  return new Intl.NumberFormat(locale).formatToParts(1.5).find((part) => part.type === "decimal")?.value ?? ",";
}

function errorCodeOf(error: unknown): GradingErrorCode | "generic" {
  const code = error instanceof MockApiError ? error.code : undefined;
  return (GRADING_ERROR_CODES as readonly string[]).includes(code ?? "") ? (code as GradingErrorCode) : "generic";
}

export interface GradingAttemptViewProps {
  attemptId: string;
  deps?: Partial<GradingApiDeps>;
}

/**
 * Màn chấm một lượt thi `/grading/[attemptId]`: thẻ tiêu đề (học viên, bài thi, trạng thái, điểm), thanh chọn câu tự
 * luận, đề và bài viết bên trái, tiêu chí chấm bên phải (xếp chồng khi khung hẹp), khối điểm của lượt với Lưu nháp và
 * Hoàn tất chấm (xác nhận ngay trong trang). Lượt đã có điểm thì chỉ để đọc.
 */
export function GradingAttemptView({ attemptId, deps }: GradingAttemptViewProps) {
  const t = useTranslations("grading.detail");
  const format = useFormatter();
  const api = useMemo(() => ({ ...defaultGradingApiDeps, ...deps }), [deps]);
  const attemptQuery = useGradingAttempt(attemptId, api);
  const [announcement, setAnnouncement] = useState("");
  const [justFinished, setJustFinished] = useState(false);

  let body;
  if (attemptQuery.isPending) {
    body = (
      <div className="flex flex-col gap-4" aria-busy="true" aria-label={t("loading")}>
        <Skeleton className="h-36" />
        <Skeleton className="h-14 w-full max-w-xl" />
        <div className="grid gap-4 @4xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
          <Skeleton className="h-96" />
          <Skeleton className="h-96" />
        </div>
      </div>
    );
  } else if (attemptQuery.isError) {
    const notFound = getErrorStatus(attemptQuery.error) === 404;
    body = (
      <div className="flex flex-col gap-5">
        <h1 className="text-page-title font-bold text-text">{t("title")}</h1>
        <MockDataBanner />
        <Card>
          <EmptyState
            icon={<TriangleAlert />}
            title={notFound ? t("notFoundTitle") : t("errorTitle")}
            description={notFound ? t("notFoundDescription") : t("errorDescription")}
            headingLevel={2}
            action={
              notFound ? (
                <Button asChild variant="secondary">
                  <Link href="/grading">{t("backToList")}</Link>
                </Button>
              ) : (
                <Button variant="secondary" onClick={() => void attemptQuery.refetch()}>
                  {t("retry")}
                </Button>
              )
            }
          />
        </Card>
      </div>
    );
  } else {
    const attempt = attemptQuery.data;
    body = (
      <div className="flex flex-col gap-5">
        <AttemptHeader attempt={attempt} />
        <MockDataBanner />
        {attempt.essays.length === 0 ? (
          <Card>
            <EmptyState
              icon={<ClipboardCheck />}
              title={t("emptyTitle")}
              description={t("emptyDescription")}
              headingLevel={2}
              action={
                <Button asChild variant="secondary">
                  <Link href="/grading">{t("backToList")}</Link>
                </Button>
              }
            />
          </Card>
        ) : (
          <GradingWorkspace
            // Hoàn tất chấm đổi sang chế độ chỉ đọc: dựng lại khu vực chấm.
            key={`${attempt.id}-${attempt.status}`}
            attempt={attempt}
            api={api}
            announce={setAnnouncement}
            focusResult={justFinished}
            onFinished={(updated) => {
              setJustFinished(true);
              const message = t("finish.done", {
                name: updated.learner.name,
                score: format.number(updated.totalScore ?? 0),
                max: format.number(updated.objectiveMaxScore + updated.essayMaxScore),
              });
              setAnnouncement(message);
              notify.success(message);
            }}
          />
        )}
      </div>
    );
  }

  return (
    <div className="@container">
      <LiveStatus message={announcement} />
      {body}
    </div>
  );
}

function BackLink() {
  const t = useTranslations("grading.detail");
  return (
    <Link
      href="/grading"
      className={cn(
        "inline-flex w-fit items-center gap-1.5 rounded-sm text-label font-medium text-text-secondary hover:text-text",
        focusRing,
      )}
    >
      <ArrowLeft aria-hidden className="size-4" />
      {t("backToList")}
    </Link>
  );
}

/** Thẻ tiêu đề: học viên, chip trạng thái, bài thi, và dải tóm tắt `<dl>` (kỳ thi, nộp lúc, người chấm, điểm). */
function AttemptHeader({ attempt }: { attempt: GradingAttemptDetail }) {
  const t = useTranslations("grading");
  const format = useFormatter();
  const referenceQuery = useContentReference();
  const kindName =
    referenceQuery.data?.examKinds.find((kind) => kind.code === attempt.exam.kindCode)?.name ?? attempt.exam.kindCode;

  return (
    <Card padding="none" className="flex flex-col gap-4 p-4 sm:p-5">
      <BackLink />
      <div className="flex min-w-0 flex-col gap-2">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
          <h1 className="text-page-title font-bold wrap-anywhere text-text">
            {t("detail.heading", { name: attempt.learner.name })}
          </h1>
          <StatusChip tone={GRADING_STATUS_TONE[attempt.status]}>{t(`status.${attempt.status}`)}</StatusChip>
        </div>
        <p className="text-body text-text-muted">
          {t("detail.subheading", { code: attempt.learner.code, examCode: attempt.exam.code, examName: attempt.exam.name })}
        </p>
      </div>
      <dl className="m-0 flex flex-wrap gap-x-6 gap-y-2 text-body">
        <SummaryItem label={t("detail.summary.kind")} value={kindName} />
        <SummaryItem
          label={t("detail.summary.submittedAt")}
          value={format.dateTime(new Date(attempt.submittedAt), "dateTime")}
        />
        <SummaryItem label={t("detail.summary.grader")} value={attempt.grader?.name ?? t("list.noGrader")} />
        {attempt.gradedAt && (
          <SummaryItem
            label={t("detail.summary.gradedAt")}
            value={format.dateTime(new Date(attempt.gradedAt), "dateTime")}
          />
        )}
        <SummaryItem
          label={t("detail.summary.essays")}
          value={t("list.progress", { graded: attempt.gradedEssayCount, count: attempt.essayCount })}
        />
      </dl>
    </Card>
  );
}

function SummaryItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline gap-1.5">
      <dt className="text-text-muted">{label}</dt>
      <dd className="m-0 font-semibold text-text">{value}</dd>
    </div>
  );
}

/** Ô có lỗi đầu tiên theo thứ tự câu rồi tiêu chí, rồi nhận xét. */
function firstErrorField(errors: FieldErrors<GradingFormValues>, essayCount: number) {
  for (let essayIndex = 0; essayIndex < essayCount; essayIndex += 1) {
    const essayErrors = errors.essays?.[essayIndex];
    if (!essayErrors) continue;
    const scoreErrors = essayErrors.scores;
    if (Array.isArray(scoreErrors)) {
      const criterionIndex = scoreErrors.findIndex(Boolean);
      if (criterionIndex >= 0) return { essayIndex, field: scoreFieldName({ essayIndex, criterionIndex }) };
    }
    if (essayErrors.comment) return { essayIndex, field: `essays.${essayIndex}.comment` as const };
  }
  return null;
}

interface GradingWorkspaceProps {
  attempt: GradingAttemptDetail;
  api: GradingApiDeps;
  announce: (message: string) => void;
  /** Vừa hoàn tất chấm: đưa focus tới khối kết quả (nút Hoàn tất đã biến mất). */
  focusResult: boolean;
  onFinished: (attempt: GradingAttemptDetail) => void;
}

function GradingWorkspace({ attempt, api, announce, focusResult, onFinished }: GradingWorkspaceProps) {
  const t = useTranslations("grading.detail");
  const locale = useLocale();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const readOnly = attempt.status === "SCORED";
  const decimalSeparator = useMemo(() => decimalSeparatorOf(locale), [locale]);
  const schema = useMemo(() => gradingSchema(attempt.essays), [attempt.essays]);
  const form = useForm<GradingFormValues>({
    resolver: zodResolver(schema),
    mode: "onTouched",
    defaultValues: toFormValues(attempt, decimalSeparator),
    shouldFocusError: false,
  });
  const { handleSubmit, setError, setFocus, reset, formState } = form;
  const saveDraft = useSaveGradingDraft(api);
  const finish = useFinishGrading(api);

  const [activeIndex, setActiveIndex] = useState(() => {
    const index = attempt.essays.findIndex((essay) => essay.questionId === searchParams.get(QUESTION_PARAM));
    return index >= 0 ? index : 0;
  });
  // Đích focus chờ câu mới dựng xong; `focusRequest` đổi để effect chạy lại.
  const pendingFocusRef = useRef<FieldPath<GradingFormValues> | "heading" | null>(null);
  const [focusRequest, setFocusRequest] = useState(0);
  const [confirming, setConfirming] = useState(false);
  const [serverError, setServerError] = useState<GradingErrorCode | "generic" | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const confirmRef = useRef<HTMLDivElement>(null);
  const finishButtonRef = useRef<HTMLButtonElement>(null);

  const requestFocus = useCallback((target: FieldPath<GradingFormValues> | "heading") => {
    pendingFocusRef.current = target;
    setFocusRequest((count) => count + 1);
  }, []);

  const selectEssay = useCallback(
    (index: number, focus: "heading" | null = null) => {
      setActiveIndex(index);
      if (focus) requestFocus(focus);
      const questionId = attempt.essays[index]?.questionId;
      if (typeof window !== "undefined" && questionId) {
        const params = new URLSearchParams(window.location.search);
        if (index === 0) params.delete(QUESTION_PARAM);
        else params.set(QUESTION_PARAM, questionId);
        const query = params.toString();
        window.history.replaceState(null, "", query ? `${pathname}?${query}` : pathname);
      }
    },
    [attempt.essays, pathname, requestFocus],
  );

  // Focus sau khi câu mới đã dựng xong (ô lỗi chỉ có trong DOM khi câu của nó đang mở).
  useEffect(() => {
    const target = pendingFocusRef.current;
    if (!target) return;
    pendingFocusRef.current = null;
    if (target === "heading") headingRef.current?.focus();
    else setFocus(target);
  }, [focusRequest, activeIndex, setFocus]);

  useEffect(() => {
    if (confirming) confirmRef.current?.focus();
  }, [confirming]);

  // Rời trang khi còn điểm chưa lưu: trình duyệt hỏi lại.
  useEffect(() => {
    if (readOnly || !formState.isDirty) return;
    const handler = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [readOnly, formState.isDirty]);

  function focusField(target: { essayIndex: number; field: FieldPath<GradingFormValues> }) {
    selectEssay(target.essayIndex);
    requestFocus(target.field);
  }

  const onInvalid = (errors: FieldErrors<GradingFormValues>) => {
    setConfirming(false);
    const target = firstErrorField(errors, attempt.essays.length);
    if (target) focusField(target);
    announce(t("validation.summary"));
  };

  // handleSubmit gọi trong trình xử lý sự kiện (không trong lúc render) vì các nhánh lỗi ghi ref đích focus.
  const onSaveDraft = () => handleSubmit(async (values) => {
    setServerError(null);
    setConfirming(false);
    try {
      const updated = await saveDraft.mutateAsync(toSaveRequest(attempt, values));
      reset(toFormValues(updated, decimalSeparator));
      announce(t("draft.saved"));
      notify.success(t("draft.saved"));
    } catch (error) {
      setServerError(errorCodeOf(error));
    }
  }, onInvalid)();

  /** Hoàn tất bước 1: kiểm tra đủ điểm mọi tiêu chí, rồi hiện khối xác nhận ngay trong trang. */
  const onRequestFinish = () => handleSubmit((values) => {
    setServerError(null);
    const missing = findMissingScores(attempt.essays, values);
    if (missing.length > 0) {
      for (const location of missing) {
        setError(scoreFieldName(location), { type: "required", message: "required" });
      }
      setConfirming(false);
      focusField({ essayIndex: missing[0]!.essayIndex, field: scoreFieldName(missing[0]!) });
      announce(t("validation.missing", { count: missing.length }));
      return;
    }
    setConfirming(true);
  }, onInvalid)();

  /** Hoàn tất bước 2: gửi điểm, lượt chuyển sang "Đã có điểm". */
  const onConfirmFinish = () => handleSubmit(async (values) => {
    setServerError(null);
    if (findMissingScores(attempt.essays, values).length > 0) {
      setConfirming(false);
      return;
    }
    try {
      const updated = await finish.mutateAsync(toSaveRequest(attempt, values));
      onFinished(updated);
    } catch (error) {
      setServerError(errorCodeOf(error));
    }
  }, onInvalid)();

  function cancelConfirm() {
    setConfirming(false);
    requestAnimationFrame(() => finishButtonRef.current?.focus());
  }

  const essay = attempt.essays[activeIndex] ?? attempt.essays[0]!;
  const essayIndex = attempt.essays.indexOf(essay);
  const onPrevious = essayIndex > 0 ? () => selectEssay(essayIndex - 1, "heading") : null;
  const onNext = essayIndex < attempt.essays.length - 1 ? () => selectEssay(essayIndex + 1, "heading") : null;

  if (readOnly) {
    const items: EssayNavigatorItem[] = attempt.essays.map((item) => ({
      essay: item,
      scores: item.scores.map((score) => (score.score === null ? "" : String(score.score))),
      hasError: false,
    }));
    return (
      <div className="flex flex-col gap-4">
        <AttemptResult attempt={attempt} focusOnMount={focusResult} />
        <EssayNavigator items={items} activeIndex={essayIndex} onSelect={(index) => selectEssay(index)} />
        <div className="grid items-start gap-4 @4xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
          <EssayQuestionPanel essay={essay} headingRef={headingRef} />
          <EssayRubricResult essay={essay} onPrevious={onPrevious} onNext={onNext} />
        </div>
      </div>
    );
  }

  return (
    <FormProvider {...form}>
      <form
        noValidate
        aria-label={t("formLabel", { name: attempt.learner.name })}
        onSubmit={(event) => {
          event.preventDefault();
          void onRequestFinish();
        }}
        className="flex flex-col gap-4"
      >
        <WorkspaceNavigator attempt={attempt} activeIndex={essayIndex} onSelect={(index) => selectEssay(index)} />
        <div className="grid items-start gap-4 @4xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
          <EssayQuestionPanel essay={essay} headingRef={headingRef} />
          <EssayRubricForm
            key={essay.questionId}
            essay={essay}
            essayIndex={essayIndex}
            onPrevious={onPrevious}
            onNext={onNext}
          />
        </div>

        <Card padding="none" className="flex flex-col gap-4 p-4 sm:p-5">
          <AttemptTotals attempt={attempt} />
          {serverError && (
            <p role="alert" className="rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger">
              {t(`errors.${serverError}`)}
            </p>
          )}
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-caption text-text-muted">
              {formState.isDirty ? t("draft.unsaved") : t("draft.hint")}
            </p>
            <div className="flex flex-wrap justify-end gap-2">
              <Button
                type="button"
                variant="secondary"
                loading={saveDraft.isPending}
                disabled={finish.isPending}
                onClick={() => void onSaveDraft()}
              >
                <Save />
                <span>{t("draft.save")}</span>
              </Button>
              <Button ref={finishButtonRef} type="submit" disabled={saveDraft.isPending || finish.isPending}>
                <CircleCheckBig />
                <span>{t("finish.action")}</span>
              </Button>
            </div>
          </div>
          {confirming && (
            <FinishConfirm
              ref={confirmRef}
              attempt={attempt}
              loading={finish.isPending}
              onCancel={cancelConfirm}
              onConfirm={() => void onConfirmFinish()}
            />
          )}
        </Card>
      </form>
    </FormProvider>
  );
}

/** Thanh chọn câu khi đang chấm: điểm đang nhập và lỗi của từng câu lấy từ biểu mẫu. */
function WorkspaceNavigator({
  attempt,
  activeIndex,
  onSelect,
}: {
  attempt: GradingAttemptDetail;
  activeIndex: number;
  onSelect: (index: number) => void;
}) {
  const watched = useWatch<GradingFormValues, "essays">({ name: "essays" });
  const { errors } = useFormState<GradingFormValues>({ name: "essays" });
  const items: EssayNavigatorItem[] = attempt.essays.map((essay, index) => ({
    essay,
    scores: watched?.[index]?.scores,
    hasError: Boolean(errors.essays?.[index]),
  }));
  return <EssayNavigator items={items} activeIndex={activeIndex} onSelect={onSelect} />;
}

/** Điểm trắc nghiệm, điểm tự luận đang nhập (tạm tính) và tổng tạm tính của lượt. */
function AttemptTotals({ attempt }: { attempt: GradingAttemptDetail }) {
  const t = useTranslations("grading.detail.totals");
  const format = useFormatter();
  const watched = useWatch<GradingFormValues, "essays">({ name: "essays" });
  const essayScore = sumScores(attempt.essays.map((_essay, index) => essayInputTotal(watched?.[index]?.scores)));
  const total = roundScore(attempt.objectiveScore + essayScore);
  const max = attempt.objectiveMaxScore + attempt.essayMaxScore;
  return (
    <dl className="m-0 grid grid-cols-[repeat(auto-fit,minmax(min(11rem,100%),1fr))] gap-3">
      <TotalItem
        label={t("objective")}
        value={
          attempt.objectiveMaxScore > 0
            ? t("value", { score: format.number(attempt.objectiveScore), max: format.number(attempt.objectiveMaxScore) })
            : t("noObjective")
        }
      />
      <TotalItem
        label={t("essayDraft")}
        value={t("value", { score: format.number(essayScore), max: format.number(attempt.essayMaxScore) })}
      />
      <TotalItem
        label={t("totalDraft")}
        value={t("value", { score: format.number(total), max: format.number(max) })}
        strong
      />
    </dl>
  );
}

function TotalItem({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex flex-col gap-0.5 rounded-lg border border-border px-3 py-2">
      <dt className="text-caption text-text-muted">{label}</dt>
      <dd className={cn("m-0 text-body text-text tabular-nums", strong ? "font-bold" : "font-semibold")}>{value}</dd>
    </div>
  );
}

/** Xác nhận hoàn tất ngay trong trang (DESIGN.md mục 5.14): nêu điểm tự luận và tổng điểm sẽ ghi cho lượt. */
function FinishConfirm({
  ref,
  attempt,
  loading,
  onCancel,
  onConfirm,
}: {
  ref: Ref<HTMLDivElement>;
  attempt: GradingAttemptDetail;
  loading: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const t = useTranslations("grading.detail.finish");
  const format = useFormatter();
  const watched = useWatch<GradingFormValues, "essays">({ name: "essays" });
  const essayScore = sumScores(attempt.essays.map((_essay, index) => essayInputTotal(watched?.[index]?.scores)));
  const total = roundScore(attempt.objectiveScore + essayScore);
  const max = attempt.objectiveMaxScore + attempt.essayMaxScore;
  const titleId = useId();
  return (
    <div
      ref={ref}
      tabIndex={-1}
      role="region"
      aria-labelledby={titleId}
      className={cn("flex flex-col gap-3 rounded-lg border border-warning/40 bg-warning-bg p-4", focusRing)}
    >
      <div className="flex items-start gap-2.5">
        <CircleCheckBig aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
        <div className="flex flex-col gap-1">
          <p id={titleId} className="font-semibold text-text">
            {t("confirmTitle", { name: attempt.learner.name })}
          </p>
          <p className="text-body text-text-secondary">
            {attempt.objectiveMaxScore > 0
              ? t("confirmDescription", {
                  objective: format.number(attempt.objectiveScore),
                  essay: format.number(essayScore),
                  total: format.number(total),
                  max: format.number(max),
                })
              : t("confirmDescriptionEssayOnly", { total: format.number(total), max: format.number(max) })}
          </p>
        </div>
      </div>
      <div className="flex flex-wrap justify-end gap-2">
        <Button type="button" variant="secondary" size="sm" disabled={loading} onClick={onCancel}>
          {t("cancel")}
        </Button>
        <Button type="button" size="sm" loading={loading} onClick={onConfirm}>
          {t("confirm")}
        </Button>
      </div>
    </div>
  );
}

/** Kết quả của lượt đã có điểm: tổng = trắc nghiệm + tự luận. */
function AttemptResult({ attempt, focusOnMount }: { attempt: GradingAttemptDetail; focusOnMount: boolean }) {
  const t = useTranslations("grading.detail.result");
  const totalsT = useTranslations("grading.detail.totals");
  const format = useFormatter();
  const headingRef = useRef<HTMLHeadingElement>(null);
  const max = attempt.objectiveMaxScore + attempt.essayMaxScore;

  useEffect(() => {
    if (focusOnMount) headingRef.current?.focus();
  }, [focusOnMount]);

  return (
    <Card padding="none" className="flex flex-col gap-3 border-success/40 p-4 sm:p-5">
      <h2
        ref={headingRef}
        tabIndex={-1}
        className={cn("flex w-fit items-center gap-2 rounded-sm text-h3 font-bold text-text", focusRing)}
      >
        <CircleCheckBig aria-hidden className="size-5 shrink-0 text-success" />
        {t("title")}
      </h2>
      <dl className="m-0 grid grid-cols-[repeat(auto-fit,minmax(min(11rem,100%),1fr))] gap-3">
        <TotalItem
          label={totalsT("objective")}
          value={
            attempt.objectiveMaxScore > 0
              ? totalsT("value", {
                  score: format.number(attempt.objectiveScore),
                  max: format.number(attempt.objectiveMaxScore),
                })
              : totalsT("noObjective")
          }
        />
        <TotalItem
          label={t("essay")}
          value={totalsT("value", {
            score: format.number(attempt.essayScore ?? 0),
            max: format.number(attempt.essayMaxScore),
          })}
        />
        <TotalItem
          label={t("total")}
          value={totalsT("value", { score: format.number(attempt.totalScore ?? 0), max: format.number(max) })}
          strong
        />
      </dl>
    </Card>
  );
}
