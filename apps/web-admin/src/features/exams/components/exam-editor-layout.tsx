"use client";

import { Button, Card, cn, DetailHeader, EmptyState, NavTab, NavTabs, Skeleton, StatusChip } from "@thi360/ui";
import { ArrowLeft, Lock, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import { useSetBreadcrumbLeaf } from "@/features/shell/breadcrumb-leaf";
import { getErrorStatus } from "@/lib/api/query-client";
import { EXAM_EDITOR_TABS, examTabFromPathname, examTabHref } from "../editor-tabs";
import { useExam, useExamStructure } from "../queries";
import { EXAM_CHECK_TONE, EXAM_STATUS_TONE } from "../status";
import type { ExamDetail } from "../types";
import { useKindLabel } from "./create-exam-fields";
import { ExamEditorActionsSlot } from "./exam-editor-actions";
import { draftTotals } from "./exam-structure-draft";

const DISPLAY_NUMBER_FORMAT = { maximumFractionDigits: 2 } as const;

/**
 * Chỗ đặt nút của trang con (`ExamEditorActions` gắn vào bằng portal). Khi trang con chưa gắn nút, ẩn luôn khung nút của
 * `DetailHeader` để màn hẹp không thừa một khoảng trống dưới tiêu đề.
 */
const ACTIONS_SLOT_HIDE_EMPTY = "[&_[data-slot=page-actions]:has(>[data-exam-editor-actions]:empty)]:hidden";

function ActionsSlot({ slotRef }: { slotRef: (node: HTMLDivElement | null) => void }) {
  return <div ref={slotRef} data-exam-editor-actions="" className="contents" />;
}

export function ExamEditorLayout({ examId, children }: { examId?: string; children: ReactNode }) {
  if (!examId) {
    return <ExamEditorCreateLayout>{children}</ExamEditorCreateLayout>;
  }
  return <ExamEditorExistingLayout examId={examId}>{children}</ExamEditorExistingLayout>;
}

function ExamEditorCreateLayout({ children }: { children: ReactNode }) {
  const t = useTranslations("exams");
  const tCommon = useTranslations("common");
  const [actionsSlot, setActionsSlot] = useState<HTMLElement | null>(null);

  return (
    <div className="flex flex-col gap-5">
      <DetailHeader
        className={ACTIONS_SLOT_HIDE_EMPTY}
        back={{ href: "/exams", label: tCommon("backTo.exams") }}
        linkComponent={Link}
        title={t("create.title")}
        description={t("create.description")}
        actions={<ActionsSlot slotRef={setActionsSlot} />}
      />
      <NavTabs aria-label={t("editor.tabsLabel")}>
        {EXAM_EDITOR_TABS.map((tab) =>
          tab === "general" ? (
            <NavTab key={tab} asChild current>
              <Link href="/exams/new">{t(`editor.tabs.${tab}`)}</Link>
            </NavTab>
          ) : (
            <NavTab key={tab} disabled>
              {t(`editor.tabs.${tab}`)}
            </NavTab>
          ),
        )}
      </NavTabs>
      <ExamEditorActionsSlot.Provider value={actionsSlot}>
        <div>{children}</div>
      </ExamEditorActionsSlot.Provider>
    </div>
  );
}

function ExamEditorExistingLayout({ examId, children }: { examId: string; children: ReactNode }) {
  const t = useTranslations("exams");
  const tForbidden = useTranslations("forbidden");
  const pathname = usePathname();
  const activeTab = examTabFromPathname(pathname);
  const examQuery = useExam(examId);
  const [actionsSlot, setActionsSlot] = useState<HTMLElement | null>(null);
  const [retriedError, setRetriedError] = useState<unknown>(null);

  const hasExamData = Boolean(examQuery.data);
  const liveError = hasExamData ? null : examQuery.isError ? examQuery.error : null;
  const examError = hasExamData ? null : (liveError ?? (examQuery.isFetching ? retriedError : null));

  if (examError) {
    const status = getErrorStatus(examError);
    const forbidden = status === 403;
    const notFound = status === 404;
    return (
      <div className="flex flex-col gap-4">
        <Link
          href="/exams"
          className="inline-flex w-fit items-center gap-1.5 rounded-sm text-label font-medium text-text-secondary hover:text-text"
        >
          <ArrowLeft aria-hidden className="size-4" />
          {t("editor.backToList")}
        </Link>
        <Card>
          <EmptyState
            icon={forbidden ? <Lock /> : <TriangleAlert />}
            title={forbidden ? tForbidden("title") : notFound ? t("editor.notFoundTitle") : t("editor.errorTitle")}
            description={
              forbidden ? tForbidden("descriptionAnonymous") : notFound ? t("editor.notFoundDescription") : t("editor.errorDescription")
            }
            action={
              forbidden || notFound ? (
                <Button asChild variant="secondary">
                  <Link href="/exams">{t("editor.backToList")}</Link>
                </Button>
              ) : (
                <Button
                  variant="secondary"
                  loading={examQuery.isFetching}
                  onClick={() => {
                    setRetriedError(examError);
                    void examQuery.refetch();
                  }}
                >
                  {t("editor.retry")}
                </Button>
              )
            }
          />
        </Card>
      </div>
    );
  }

  if (!examQuery.data) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-8 w-80 max-w-full" />
        <Skeleton className="h-16" />
        <Skeleton className="h-10 w-full max-w-xl" />
        <Skeleton className="h-96" />
      </div>
    );
  }

  const exam = examQuery.data;
  return (
    <div className="flex flex-col gap-5">
      <ExamEditorHeader exam={exam} actionsRef={setActionsSlot} />
      <NavTabs aria-label={t("editor.tabsLabel")}>
        {EXAM_EDITOR_TABS.map((tab) => (
          <NavTab key={tab} asChild current={tab === activeTab}>
            <Link href={examTabHref(exam.id, tab)}>{t(`editor.tabs.${tab}`)}</Link>
          </NavTab>
        ))}
      </NavTabs>
      <ExamEditorActionsSlot.Provider value={actionsSlot}>
        <div key={activeTab}>{children}</div>
      </ExamEditorActionsSlot.Provider>
    </div>
  );
}

function ExamEditorHeader({
  exam,
  actionsRef,
}: {
  exam: ExamDetail;
  actionsRef: (node: HTMLDivElement | null) => void;
}) {
  const t = useTranslations("exams");
  const tCommon = useTranslations("common");
  const format = useFormatter();
  const kindLabel = useKindLabel();
  useSetBreadcrumbLeaf(exam.code);
  const structureQuery = useExamStructure(exam.id);
  const structure = structureQuery.data;

  const partsSource = structure?.parts ?? exam.parts;
  const hasParts = partsSource.length > 0;
  const totals = draftTotals(partsSource, {
    sharedDuration: exam.timing === "SHARED" ? exam.durationMinutes : null,
    combinations: structure?.combinations ?? [],
  });

  const totalQuestions = hasParts ? totals.totalQuestions : exam.totalQuestions;
  const questionsValue =
    hasParts && totals.printedQuestions !== totals.totalQuestions
      ? `${format.number(totalQuestions, DISPLAY_NUMBER_FORMAT)} ${t("editor.printedQuestionsSuffix", {
          printed: format.number(totals.printedQuestions, DISPLAY_NUMBER_FORMAT),
        })}`
      : format.number(totalQuestions, DISPLAY_NUMBER_FORMAT);

  const durationMinutes = structure && totals.totalMinutes > 0 ? totals.totalMinutes : exam.durationMinutes;
  const hasCodedParts = exam.parts.some((part) => Boolean(part.codeSource));
  const showScoreMismatch =
    exam.buildMode !== "REVIEW" &&
    (!hasCodedParts || Boolean(structure)) &&
    exam.maxScore !== null &&
    totals.totalPoints > 0 &&
    Math.abs(totals.totalPoints - exam.maxScore) > 1e-6;

  return (
    <DetailHeader
      className={ACTIONS_SLOT_HIDE_EMPTY}
      back={{ href: "/exams", label: tCommon("backTo.exams") }}
      linkComponent={Link}
      title={exam.name}
      titleProps={{ "data-exam-content": "" }}
      chips={
        <>
          <StatusChip tone={EXAM_STATUS_TONE[exam.status]}>
            {exam.status === "PUBLISHED" && exam.servingVersion !== null
              ? t("status.servingVersion", { version: exam.servingVersion })
              : t(`status.${exam.status}`)}
          </StatusChip>
          {exam.hasUnpublishedChanges && <StatusChip tone="warning">{t("status.unpublishedChanges")}</StatusChip>}
          <StatusChip tone={EXAM_CHECK_TONE[exam.check.status]}>
            {exam.check.status === "INVALID"
              ? t("checkStatus.issues", { count: exam.check.issueCount })
              : t(`checkStatus.${exam.check.status}`)}
          </StatusChip>
          {showScoreMismatch && exam.maxScore !== null && (
            <StatusChip tone="warning">
              {t("editor.scoreMismatchWarning", {
                actual: format.number(totals.totalPoints, DISPLAY_NUMBER_FORMAT),
                expected: format.number(exam.maxScore, DISPLAY_NUMBER_FORMAT),
              })}
            </StatusChip>
          )}
        </>
      }
      actions={<ActionsSlot slotRef={actionsRef} />}
    >
      <dl
        aria-label={t("editor.structureSummaryLabel")}
        className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border border-border bg-surface px-4 py-2.5 text-body"
      >
        <SummaryItem label={t("summary.code")} value={exam.code} />
        <SummaryItem label={t("summary.kind")} value={kindLabel(exam.kind)} />
        <SummaryItem label={t("summary.format")} value={t(`scope.${exam.scope}`)} />
        <SummaryItem label={t("summary.questions")} value={questionsValue} numeric />
        {durationMinutes > 0 && (
          <SummaryItem label={t("summary.duration")} value={t("summary.minutes", { count: durationMinutes })} numeric />
        )}
        {exam.maxScore !== null && (
          <SummaryItem
            label={t("general.facts.maxScore")}
            value={t("general.facts.maxScoreValue", { count: exam.maxScore })}
            numeric
          />
        )}
        <SummaryItem label={t("summary.attempts")} value={format.number(exam.attemptCount)} numeric />
      </dl>
    </DetailHeader>
  );
}

function SummaryItem({ label, value, numeric = false }: { label: string; value: string; numeric?: boolean }) {
  return (
    <div className="flex items-baseline gap-1.5 not-last:sm:border-r not-last:sm:border-border not-last:sm:pr-4">
      <dt className="text-caption text-text-muted">{label}</dt>
      <dd className={cn("m-0 font-semibold text-text", numeric && "tabular-nums")}>{value}</dd>
    </div>
  );
}
