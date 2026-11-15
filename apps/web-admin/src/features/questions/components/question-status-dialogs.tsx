"use client";

import type { ColumnDef } from "@tanstack/react-table";
import {
  Badge,
  Button,
  cn,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  focusRing,
  ListCard,
  ListCardTitleLink,
  notify,
  Skeleton,
  StatusChip,
  Table,
  type TableCards,
} from "@thi360/ui";
import { Check, CircleAlert, Info, Lock, PowerOff, TriangleAlert, X } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useCallback, useId, useMemo, type ReactNode } from "react";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import { getErrorStatus } from "@/lib/api/query-client";
import { MockApiError } from "@/lib/mock/content-mock";
import { QUESTION_STATUS_ERROR_CODES, type QuestionStatusErrorCode } from "../api";
import { useMarkQuestionReady, useQuestionReadiness, useQuestionRetireImpact, useRetireQuestion } from "../queries";
import {
  readinessFixHref,
  READINESS_RULES,
  type QuestionReadiness,
  type ReadinessIssue,
  type ReadinessRuleResult,
} from "../readiness";
import type { QuestionSummary, QuestionUsageExam } from "../types";

/**
 * Hộp thoại chuyển trạng thái câu hỏi, dùng ở danh sách (UNI-2592) và trang chi tiết (UNI-2593).
 * - `MarkReadyDialog` (canvas `KiemTraSanSang`): kiểm bảy điều kiện của UniNote PBI-03 mục 7.2; đủ thì xác nhận
 *   chuyển, thiếu thì liệt kê điều kiện chưa đạt kèm liên kết tới chỗ cần sửa trên trang soạn câu hỏi.
 * - `RetireQuestionDialog` (canvas `NgungCauHoi`): nêu hệ quả, bài thi đang dùng câu và ảnh hưởng tới nguồn sinh đề.
 *   Câu đã dùng không bao giờ bị xóa hẳn.
 * Bên gọi chỉ truyền câu hỏi và nhận kết quả qua `onDone`.
 */
export interface QuestionStatusDialogProps {
  question: QuestionSummary | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Gọi sau khi đổi trạng thái thành công, với câu hỏi đã cập nhật. */
  onDone?: (question: QuestionSummary) => void;
}

const MAX_ISSUES_PER_RULE = 2;

/** Chữ của một vấn đề chưa đạt (dùng chung cho hộp thoại, gợi ý trên danh sách và kết quả hàng loạt). */
export function useReadinessIssueText() {
  const t = useTranslations("questions.statusDialogs");
  return useCallback(
    (issue: ReadinessIssue) => {
      const text = t(`issues.${issue.code}`, { position: issue.position ?? 0, value: issue.value ?? "" });
      return issue.child !== undefined ? t("childIssue", { child: issue.child, issue: text }) : text;
    },
    [t],
  );
}

function errorCodeOf(error: unknown): QuestionStatusErrorCode | "UNKNOWN" {
  if (error instanceof MockApiError && error.code) {
    const code = error.code;
    if ((QUESTION_STATUS_ERROR_CODES as readonly string[]).includes(code)) return code as QuestionStatusErrorCode;
  }
  return getErrorStatus(error) === 404 ? "NOT_FOUND" : "UNKNOWN";
}

/** "CH-000002 · Trắc nghiệm 1 đáp án · Toán · Lớp 11". */
function useQuestionSummaryLine(question: QuestionSummary) {
  const t = useTranslations("questions.statusDialogs");
  const contentT = useTranslations("content");
  const reference = useContentReference();
  const lookup = useReferenceLookup(reference.data);
  return t("summary", {
    code: question.code,
    type: contentT(`questionType.${question.type}`),
    subject: lookup.subject(question.subjectCode),
    grade: lookup.grade(question.gradeCode),
  });
}

// --- Chuyển sang Sẵn sàng ------------------------------------------------------------------------------------

export function MarkReadyDialog({ question, open, onOpenChange, onDone }: QuestionStatusDialogProps) {
  const t = useTranslations("questions.statusDialogs");
  const mutation = useMarkQuestionReady();
  const close = (next: boolean) => {
    if (mutation.isPending) return;
    if (!next) mutation.reset();
    onOpenChange(next);
  };
  return (
    <Dialog open={open && question !== null} onOpenChange={close}>
      <DialogContent size="lg" closeLabel={t("close")}>
        {open && question && (
          <MarkReadyBody
            question={question}
            pending={mutation.isPending}
            error={mutation.isError ? errorCodeOf(mutation.error) : null}
            onClose={() => close(false)}
            onConfirm={() =>
              mutation.mutate(
                { id: question.id },
                {
                  onSuccess: (updated) => {
                    notify.success(t("readyDone", { code: updated.code }));
                    onDone?.(updated);
                    mutation.reset();
                    onOpenChange(false);
                  },
                },
              )
            }
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function MarkReadyBody({
  question,
  pending,
  error,
  onClose,
  onConfirm,
}: {
  question: QuestionSummary;
  pending: boolean;
  error: QuestionStatusErrorCode | "UNKNOWN" | null;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const t = useTranslations("questions.statusDialogs");
  const contentT = useTranslations("content");
  const readiness = useQuestionReadiness(question.id);
  const summary = useQuestionSummaryLine(question);

  if (readiness.isPending || readiness.isError) {
    return (
      <>
        <DialogHeader>
          <DialogTitle>{t("markReadyTitle")}</DialogTitle>
          <DialogDescription>{summary}</DialogDescription>
        </DialogHeader>
        {readiness.isPending ? (
          <div role="status" aria-label={t("checking")} className="flex flex-col gap-2">
            {READINESS_RULES.map((rule) => (
              <Skeleton key={rule} className="h-12" />
            ))}
          </div>
        ) : (
          <div role="alert" className="flex flex-col items-start gap-3 rounded-lg border border-danger bg-danger-bg p-3.5">
            <p className="text-body text-danger">{t("loadError")}</p>
            <Button variant="secondary" size="sm" onClick={() => void readiness.refetch()}>
              {t("retry")}
            </Button>
          </div>
        )}
        <DialogFooter>
          <Button variant="secondary" onClick={onClose}>
            {t("close")}
          </Button>
        </DialogFooter>
      </>
    );
  }

  const result = readiness.data;
  const total = result.rules.length;

  if (result.ready) {
    return (
      <>
        <DialogHeader>
          <DialogTitle>{t("markReadyTitle")}</DialogTitle>
          <DialogDescription>
            {summary}. {t("allMet", { total })}
          </DialogDescription>
        </DialogHeader>
        <ConditionList questionId={question.id} readiness={result} onNavigate={onClose} />
        {error && (
          <p role="alert" className="rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger">
            {t(`errors.${error}`)}
          </p>
        )}
        <DialogFooter>
          <Button variant="secondary" disabled={pending} onClick={onClose}>
            {t("cancel")}
          </Button>
          <Button loading={pending} onClick={onConfirm}>
            {t("confirmReady")}
          </Button>
        </DialogFooter>
      </>
    );
  }

  return (
    <>
      <DialogHeader>
        <div className="flex items-start gap-3">
          <span
            aria-hidden
            className="grid size-10 shrink-0 place-items-center rounded-full bg-warning-bg text-warning"
          >
            <TriangleAlert className="size-5" />
          </span>
          <div className="flex min-w-0 flex-col gap-1">
            <DialogTitle>{t("notReadyTitle")}</DialogTitle>
            <DialogDescription>
              {summary}. {t.rich("unmet", { failed: result.failedCount, total, strong: (chunks) => <strong className="font-semibold text-text">{chunks}</strong> })}
            </DialogDescription>
          </div>
        </div>
      </DialogHeader>
      <ConditionList questionId={question.id} readiness={result} onNavigate={onClose} />
      {error && error !== "QUESTION_NOT_READY" && (
        <p role="alert" className="rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger">
          {t(`errors.${error}`)}
        </p>
      )}
      <p role="note" className="flex items-start gap-2 rounded-lg bg-subtle px-3.5 py-2.5 text-body text-text-secondary">
        <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
        <span>{t("keptNote", { status: contentT(`questionStatus.${question.status}`) })}</span>
      </p>
      <DialogFooter>
        <Button variant="secondary" onClick={onClose}>
          {t("close")}
        </Button>
        <Button asChild>
          <Link href={`/questions/${encodeURIComponent(question.id)}/edit`} onClick={onClose}>
            {t("editQuestion")}
          </Link>
        </Button>
      </DialogFooter>
    </>
  );
}

function ConditionList({
  questionId,
  readiness,
  onNavigate,
}: {
  questionId: string;
  readiness: QuestionReadiness;
  onNavigate: () => void;
}) {
  const t = useTranslations("questions.statusDialogs");
  const headingId = useId();
  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-2">
      <h3 id={headingId} className="text-label font-semibold text-text-secondary">
        {t("conditionsTitle")}
      </h3>
      <ol className="m-0 flex list-none flex-col overflow-hidden rounded-xl border border-border p-0">
        {readiness.rules.map((result, index) => (
          <ConditionRow
            key={result.rule}
            number={index + 1}
            result={result}
            questionId={questionId}
            onNavigate={onNavigate}
          />
        ))}
      </ol>
    </section>
  );
}

function ConditionRow({
  number,
  result,
  questionId,
  onNavigate,
}: {
  number: number;
  result: ReadinessRuleResult;
  questionId: string;
  onNavigate: () => void;
}) {
  const t = useTranslations("questions.statusDialogs");
  const issueText = useReadinessIssueText();
  const shown = result.issues.slice(0, MAX_ISSUES_PER_RULE);
  const rest = result.issues.length - shown.length;
  const firstIssue = result.issues[0];
  return (
    <li className={cn("flex items-start gap-3 border-border px-4 py-3 not-first:border-t", !result.passed && "bg-danger-bg/40")}>
      <span
        aria-hidden
        className={cn(
          "mt-0.5 grid size-6 shrink-0 place-items-center rounded-full",
          result.passed ? "bg-success-bg text-success" : "bg-danger-bg text-danger",
        )}
      >
        {result.passed ? <Check className="size-3.5" strokeWidth={3} /> : <X className="size-3.5" strokeWidth={3} />}
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="font-medium text-text">
          {number}. {t(`rules.${result.rule}`)}
        </p>
        {shown.length > 0 && (
          <ul className="m-0 flex list-none flex-col gap-0.5 p-0 text-caption text-danger">
            {shown.map((issue, index) => (
              <li key={`${issue.code}-${issue.child ?? 0}-${issue.position ?? 0}-${index}`}>{issueText(issue)}</li>
            ))}
            {rest > 0 && <li>{t("moreIssues", { count: rest })}</li>}
          </ul>
        )}
        {firstIssue && (
          <Link
            href={readinessFixHref(questionId, firstIssue.field)}
            onClick={onNavigate}
            aria-label={t("fixAria", { issue: issueText(firstIssue) })}
            className={cn("w-fit rounded-sm text-label font-semibold text-primary-text underline-offset-2 hover:underline", focusRing)}
          >
            {t("fix")}
          </Link>
        )}
      </div>
      <StatusChip tone={result.passed ? "success" : "danger"} className="shrink-0">
        {result.passed ? t("passed") : t("failed")}
      </StatusChip>
    </li>
  );
}

// --- Ngừng sử dụng -------------------------------------------------------------------------------------------

export function RetireQuestionDialog({ question, open, onOpenChange, onDone }: QuestionStatusDialogProps) {
  const t = useTranslations("questions.statusDialogs");
  const mutation = useRetireQuestion();
  const close = (next: boolean) => {
    if (mutation.isPending) return;
    if (!next) mutation.reset();
    onOpenChange(next);
  };
  return (
    <Dialog open={open && question !== null} onOpenChange={close}>
      <DialogContent size="lg" closeLabel={t("close")}>
        {open && question && (
          <RetireBody
            question={question}
            pending={mutation.isPending}
            error={mutation.isError ? errorCodeOf(mutation.error) : null}
            onClose={() => close(false)}
            onConfirm={() =>
              mutation.mutate(
                { id: question.id },
                {
                  onSuccess: (updated) => {
                    notify.success(t("retireDone", { code: updated.code }));
                    onDone?.(updated);
                    mutation.reset();
                    onOpenChange(false);
                  },
                },
              )
            }
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function RetireBody({
  question,
  pending,
  error,
  onClose,
  onConfirm,
}: {
  question: QuestionSummary;
  pending: boolean;
  error: QuestionStatusErrorCode | "UNKNOWN" | null;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const t = useTranslations("questions.statusDialogs");
  const contentT = useTranslations("content");
  const impact = useQuestionRetireImpact(question.id);
  const reference = useContentReference();
  const lookup = useReferenceLookup(reference.data);
  const effectsId = useId();
  const usageHeadingId = useId();
  const primary = question.knowledge.find((ref) => ref.primary);
  const subtitle = [
    contentT(`questionType.${question.type}`),
    lookup.subject(question.subjectCode),
    lookup.grade(question.gradeCode),
    primary?.name,
  ]
    .filter(Boolean)
    .join(" · ");

  const exams = impact.data?.exams ?? [];
  const shortfall = (exam: QuestionUsageExam) => exam.needed !== null && exam.available < exam.needed;
  const servingAtRisk = exams.filter((exam) => exam.examStatus === "PUBLISHED" && shortfall(exam)).length;

  return (
    <>
      <DialogHeader>
        <div className="flex items-start gap-3">
          <span aria-hidden className="grid size-10 shrink-0 place-items-center rounded-full bg-danger-bg text-danger">
            <PowerOff className="size-5" />
          </span>
          <div className="flex min-w-0 flex-col gap-1">
            <DialogTitle>{t("retireHeading", { code: question.code })}</DialogTitle>
            <DialogDescription>{subtitle}</DialogDescription>
          </div>
        </div>
      </DialogHeader>

      <ul id={effectsId} className="m-0 flex list-disc flex-col gap-1.5 ps-5 text-body text-text-secondary">
        <li>{t("effectNoNew")}</li>
        <li>{t("effectKeepAttempts")}</li>
        <li>{t("effectReversible")}</li>
      </ul>

      <section aria-labelledby={usageHeadingId} className="flex flex-col gap-2">
        <h3 id={usageHeadingId} className="flex items-center gap-2 text-label font-semibold text-text-secondary">
          {t("usageTitle")}
          {impact.isSuccess && <Badge>{t("usageCount", { count: exams.length })}</Badge>}
        </h3>
        {impact.isPending ? (
          <div role="status" aria-label={t("usageLoading")} className="flex flex-col gap-2">
            <Skeleton className="h-12" />
            <Skeleton className="h-12" />
          </div>
        ) : impact.isError ? (
          <div role="alert" className="flex flex-wrap items-center gap-3 rounded-lg border border-danger bg-danger-bg px-3.5 py-2.5">
            <p className="text-body text-danger">{t("usageError")}</p>
            <Button variant="secondary" size="sm" onClick={() => void impact.refetch()}>
              {t("retry")}
            </Button>
          </div>
        ) : exams.length === 0 ? (
          <p className="rounded-lg bg-subtle px-3.5 py-2.5 text-body text-text-secondary">{t("usageNone")}</p>
        ) : (
          <UsageTable question={question} exams={exams} />
        )}
      </section>

      {servingAtRisk > 0 && (
        <p role="note" className="flex items-start gap-2 rounded-lg border border-warning/40 bg-warning-bg px-3.5 py-2.5 text-body text-warning">
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>
            <strong className="font-semibold">{t("servingWarningTitle", { count: servingAtRisk })}</strong>{" "}
            {t("servingWarningBody")}
          </span>
        </p>
      )}

      {impact.data?.hardDeleteBlocked && (
        <p className="flex items-center gap-2 text-caption text-text-muted">
          <Lock aria-hidden className="size-4 shrink-0" />
          <span>{t("noHardDelete")}</span>
        </p>
      )}

      {error && (
        <p role="alert" className="flex items-start gap-2 rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger">
          <CircleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>{t(`errors.${error}`)}</span>
        </p>
      )}

      <DialogFooter>
        {/* Thao tác nguy hiểm: focus đầu tiên vào nút giữ lại (canvas `NgungCauHoi`). */}
        <Button variant="secondary" disabled={pending} onClick={onClose} autoFocus>
          {t("keep")}
        </Button>
        <Button variant="danger" loading={pending} onClick={onConfirm} aria-describedby={effectsId}>
          {t("confirmRetire")}
        </Button>
      </DialogFooter>
    </>
  );
}

function UsageTable({ question, exams }: { question: QuestionSummary; exams: QuestionUsageExam[] }) {
  const t = useTranslations("questions.statusDialogs");
  const contentT = useTranslations("content");
  const reference = useContentReference();
  const lookup = useReferenceLookup(reference.data);

  const examState = useCallback(
    (exam: QuestionUsageExam) =>
      exam.examStatus === "PUBLISHED" && exam.servingVersion !== null
        ? t("servingVersion", { version: exam.servingVersion })
        : exam.examStatus === "DRAFT"
          ? t("examDraft")
          : t("examDiscontinued"),
    [t],
  );
  const blockLabel = useCallback(
    (exam: QuestionUsageExam) =>
      t("blockLabel", { subject: lookup.subject(exam.subjectCode), type: contentT(`questionType.${exam.questionType}`) }),
    [t, contentT, lookup],
  );
  const impactChip = useCallback(
    (exam: QuestionUsageExam): ReactNode =>
      exam.needed !== null && exam.available < exam.needed ? (
        <StatusChip tone="warning" className="h-auto min-h-6 py-0.5 whitespace-normal">
          {t("impactShortfall", {
            needed: exam.needed,
            available: exam.available,
            grade: lookup.grade(question.gradeCode),
            difficulty: lookup.difficulty(question.difficultyCode),
          })}
        </StatusChip>
      ) : (
        <StatusChip tone="success">{t("impactEnough", { available: exam.available })}</StatusChip>
      ),
    [t, lookup, question.gradeCode, question.difficultyCode],
  );

  const columns = useMemo<ColumnDef<QuestionUsageExam>[]>(
    () => [
      {
        id: "exam",
        header: t("usageColumns.exam"),
        cell: ({ row }) => (
          <div className="flex min-w-40 flex-col gap-0.5">
            <Link
              href={`/exams/${encodeURIComponent(row.original.examId)}`}
              className={cn("w-fit rounded-sm font-semibold text-primary-text hover:underline", focusRing)}
            >
              {row.original.examCode}
            </Link>
            <span className="text-caption text-text-muted">{row.original.examName}</span>
            <span className="text-caption text-text-muted">{examState(row.original)}</span>
          </div>
        ),
      },
      {
        id: "block",
        header: t("usageColumns.block"),
        cell: ({ row }) => (
          <div className="flex min-w-32 flex-col gap-0.5">
            <span>{blockLabel(row.original)}</span>
            {row.original.optionCode && (
              <span className="text-caption text-text-muted">{t("optionLabel", { code: row.original.optionCode })}</span>
            )}
          </div>
        ),
      },
      { id: "impact", header: t("usageColumns.impact"), cell: ({ row }) => impactChip(row.original) },
    ],
    [t, examState, blockLabel, impactChip],
  );

  const cards = useMemo<TableCards<QuestionUsageExam>>(
    () => ({
      render: (exam) => (
        <ListCard
          title={
            <ListCardTitleLink linkComponent={Link} href={`/exams/${encodeURIComponent(exam.examId)}`}>
              {exam.examCode}
            </ListCardTitleLink>
          }
          caption={exam.examName}
          status={impactChip(exam)}
          meta={[
            { label: t("usageColumns.block"), value: blockLabel(exam) },
            exam.optionCode ? { label: t("usageColumns.option"), value: exam.optionCode } : false,
            { label: t("usageColumns.state"), value: examState(exam) },
          ]}
        />
      ),
    }),
    [t, impactChip, blockLabel, examState],
  );

  return (
    <div className="overflow-hidden rounded-xl border border-border">
      <Table
        aria-label={t("usageCaption", { code: question.code })}
        columns={columns}
        data={exams}
        getRowId={(exam) => exam.examId}
        cards={cards}
        empty={<p className="p-3.5 text-body text-text-secondary">{t("usageNone")}</p>}
      />
    </div>
  );
}
