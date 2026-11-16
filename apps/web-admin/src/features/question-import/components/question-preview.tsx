"use client";

import { Card, StatusChip, type StatusTone } from "@thi360/ui";
import { Check, CircleX, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { type RefObject, useId } from "react";
import { useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import type { IssueText } from "../error-report";
import type { ImportRowIssue, ImportRowResult, ImportRowStatus } from "../types";

export const ROW_STATUS_TONE: Record<ImportRowStatus, StatusTone> = {
  VALID: "success",
  ERROR: "danger",
  DUPLICATE: "warning",
};

export interface QuestionPreviewProps {
  row: ImportRowResult | null;
  rows: readonly ImportRowResult[];
  reference: ContentReference | undefined;
  headingRef: RefObject<HTMLHeadingElement | null>;
  describe: (issue: ImportRowIssue) => IssueText;
}

/** Xem trước một dòng như câu hỏi học viên sẽ thấy, kèm phân loại và lỗi của dòng (bản vẽ `NhapCauHoiKiemTra`). */
export function QuestionPreview({ row, rows, reference, headingRef, describe }: QuestionPreviewProps) {
  const t = useTranslations("questionImport");
  const tc = useTranslations("content");
  const format = useFormatter();
  const lookup = useReferenceLookup(reference);
  const headingId = useId();

  if (!row) {
    return (
      <Card padding="md">
        <h2 ref={headingRef} tabIndex={-1} className="text-body text-text-muted outline-hidden">
          {t("preview.empty")}
        </h2>
      </Card>
    );
  }

  const { draft } = row;
  const typeLabel = draft.type ? tc(`questionType.${draft.type}`) : t("preview.unknownType");
  const children = row.isSetPassage ? rows.filter((child) => child.parentRowNumber === row.rowNumber) : [];
  const none = t("preview.none");
  const subjectGrade = [draft.subjectCode && lookup.subject(draft.subjectCode), draft.gradeCode && lookup.grade(draft.gradeCode)]
    .filter(Boolean)
    .join(" · ");
  const errors = row.issues.filter((issue) => issue.severity === "error");

  return (
    <Card padding="none" className="overflow-hidden">
      <section aria-labelledby={headingId}>
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-5 py-4">
          <h2 ref={headingRef} id={headingId} tabIndex={-1} className="text-title font-semibold text-text outline-hidden">
            {t("check.previewRow", { row: row.rowNumber })}
          </h2>
          <StatusChip tone={ROW_STATUS_TONE[row.status]}>{t(`preview.status.${row.status}`)}</StatusChip>
        </div>

        <div className="flex flex-col gap-4 px-5 py-4">
          <div className="flex flex-col gap-3 rounded-lg border border-border bg-subtle p-4">
            <p className="text-caption font-semibold text-text-muted">{t("preview.asLearner", { type: typeLabel })}</p>
            {row.parentRowNumber !== null && (
              <p className="text-caption text-text-muted">{t("check.setChild", { row: row.parentRowNumber })}</p>
            )}
            <p className="text-body whitespace-pre-wrap wrap-anywhere text-text">{draft.content || none}</p>

            {draft.options.length > 0 && (
              <ul aria-label={t("preview.optionsLabel")} className="flex flex-col gap-2">
                {draft.options.map((option) => (
                  <li
                    key={option.letter}
                    className="flex items-start gap-2.5 rounded-md border border-border bg-surface px-3 py-2 text-body text-text"
                  >
                    <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-subtle text-caption font-semibold">
                      {option.letter}
                    </span>
                    <span className="min-w-0 flex-1 wrap-anywhere">{option.content}</span>
                    {option.correct && (
                      <span className="flex shrink-0 items-center gap-1 text-caption font-semibold text-success">
                        <Check aria-hidden className="size-4" />
                        {t("preview.correct")}
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            )}

            {draft.statements.length > 0 && (
              <ul aria-label={t("preview.statementsLabel")} className="flex flex-col gap-2">
                {draft.statements.map((statement) => (
                  <li
                    key={statement.letter}
                    className="flex items-start gap-2.5 rounded-md border border-border bg-surface px-3 py-2 text-body text-text"
                  >
                    <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-subtle text-caption font-semibold">
                      {statement.letter.toLowerCase()}
                    </span>
                    <span className="min-w-0 flex-1 wrap-anywhere">{statement.content}</span>
                    <span className="shrink-0 text-caption font-semibold text-text-secondary">
                      {statement.answer === null
                        ? t("preview.noAnswer")
                        : statement.answer
                          ? t("preview.answerTrue")
                          : t("preview.answerFalse")}
                    </span>
                  </li>
                ))}
              </ul>
            )}

            {draft.type === "MULTIPLE_CHOICE" && (
              <p className="text-caption text-text-secondary">
                <span className="font-semibold">{t("preview.scoringLabel")}: </span>
                {t(`preview.scoringRules.${draft.scoringRule ?? "ALL_OR_NOTHING"}`)}
              </p>
            )}

            {draft.type === "TRUE_FALSE" && (
              <p className="text-caption text-text-secondary">
                <span className="font-semibold">{t("preview.scoringLabel")}: </span>
                {draft.trueFalseScoring
                  ? t(`preview.trueFalseScoring.${draft.trueFalseScoring.method}`, {
                      points: draft.trueFalseScoring.points.map((point) => format.number(point)).join(" / "),
                    })
                  : t("preview.trueFalseScoring.EVEN")}
              </p>
            )}

            {draft.type === "SHORT_ANSWER" && (
              <p className="text-body text-text">
                <span className="font-semibold">{t("preview.acceptedAnswers")}: </span>
                {draft.acceptedAnswers.length > 0 ? draft.acceptedAnswers.join(" | ") : none}
              </p>
            )}

            {draft.type === "CLOZE" && (
              <ul aria-label={t("preview.blanksLabel")} className="flex flex-col gap-2">
                {draft.blanks.map((blank) => (
                  <li
                    key={blank.id}
                    className="flex flex-wrap items-start gap-2.5 rounded-md border border-border bg-surface px-3 py-2 text-body text-text"
                  >
                    <span className="shrink-0 rounded-full bg-subtle px-2 text-caption font-semibold">{blank.id}</span>
                    <span className="min-w-0 flex-1 wrap-anywhere">
                      {blank.answers.length > 0 ? blank.answers.join(" | ") : t("preview.noAnswer")}
                    </span>
                    <span className="shrink-0 text-caption font-semibold text-text-secondary">
                      {t("preview.criterionPoints", { points: blank.points ?? 1 })}
                    </span>
                  </li>
                ))}
              </ul>
            )}

            {draft.matching && (
              <div className="flex flex-col gap-2">
                <ul aria-label={t("preview.pairsLabel")} className="flex flex-col gap-2">
                  {draft.matching.left.map((item) => {
                    const pair = draft.matching?.pairs.find((candidate) => candidate.leftKey === item.key);
                    const right = draft.matching?.right.find((candidate) => candidate.key === pair?.rightKey);
                    return (
                      <li
                        key={item.key}
                        className="flex flex-wrap items-start gap-2.5 rounded-md border border-border bg-surface px-3 py-2 text-body text-text"
                      >
                        <span className="shrink-0 rounded-full bg-subtle px-2 text-caption font-semibold">{item.key}</span>
                        <span className="min-w-0 flex-1 wrap-anywhere">{item.content}</span>
                        <span className="min-w-0 text-text-secondary wrap-anywhere">
                          {right ? t("preview.pairWith", { key: right.key, content: right.content }) : t("preview.noAnswer")}
                        </span>
                      </li>
                    );
                  })}
                </ul>
                <p className="text-caption text-text-secondary">
                  {t("preview.rightChoices", {
                    items: draft.matching.right.map((item) => `${item.key}. ${item.content}`).join("; "),
                  })}
                  {" · "}
                  {draft.matching.reuseRight ? t("preview.matchReuse") : t("preview.matchOnce")}
                </p>
              </div>
            )}

            {draft.type === "ESSAY" && (
              <div className="flex flex-col gap-2">
                <p className="text-caption font-semibold text-text-muted">{t("preview.rubric")}</p>
                {draft.rubric.length > 0 ? (
                  <ol aria-label={t("preview.rubric")} className="flex flex-col gap-2">
                    {draft.rubric.map((criterion, index) => (
                      <li
                        key={index}
                        className="flex items-start gap-2.5 rounded-md border border-border bg-surface px-3 py-2 text-body text-text"
                      >
                        <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-subtle text-caption font-semibold">
                          {index + 1}
                        </span>
                        <span className="min-w-0 flex-1 wrap-anywhere">{criterion.description}</span>
                        <span className="shrink-0 text-caption font-semibold text-text-secondary">
                          {t("preview.criterionPoints", { points: criterion.points })}
                        </span>
                      </li>
                    ))}
                  </ol>
                ) : (
                  <p className="text-body text-text-muted">{t("preview.noRubric")}</p>
                )}
                <p className="text-body text-text">
                  <span className="font-semibold">{t("preview.sampleAnswer")}: </span>
                  {draft.sampleAnswer ?? none}
                </p>
              </div>
            )}

            {row.isSetPassage && (
              <div className="flex flex-col gap-2">
                <p className="text-caption font-semibold text-text-muted">
                  {t("preview.children", { count: children.length })}
                </p>
                <ul className="flex flex-col gap-2">
                  {children.map((child) => (
                    <li
                      key={child.rowNumber}
                      className="flex flex-wrap items-center gap-2 rounded-md border border-border bg-surface px-3 py-2 text-body"
                    >
                      <span className="text-caption text-text-muted">{t("preview.childRow", { row: child.rowNumber })}</span>
                      <span className="min-w-0 flex-1 wrap-anywhere text-text">{child.draft.content || none}</span>
                      <StatusChip tone={ROW_STATUS_TONE[child.status]}>{t(`preview.status.${child.status}`)}</StatusChip>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-body">
            <dt className="text-text-muted">{t("preview.sourceCode")}</dt>
            <dd className="wrap-anywhere text-text">{row.sourceCode ?? none}</dd>
            <dt className="text-text-muted">{t("preview.subjectGrade")}</dt>
            <dd className="text-text">{subjectGrade || none}</dd>
            <dt className="text-text-muted">{t("preview.difficulty")}</dt>
            <dd className="text-text">{draft.difficultyCode ? lookup.difficulty(draft.difficultyCode) : none}</dd>
            <dt className="text-text-muted">{t("preview.knowledge")}</dt>
            <dd className="text-text">
              {draft.knowledge.length > 0
                ? draft.knowledge
                    .map((ref) => `${ref.code} · ${ref.name}${ref.primary ? ` (${t("preview.primary")})` : ""}`)
                    .join("; ")
                : none}
            </dd>
            <dt className="text-text-muted">{t("preview.tags")}</dt>
            <dd className="text-text">{draft.tagCodes.length > 0 ? draft.tagCodes.map(lookup.tag).join(", ") : none}</dd>
            <dt className="text-text-muted">{t("preview.points")}</dt>
            <dd className="text-text">{draft.points === null ? t("preview.pointsDefault") : format.number(draft.points)}</dd>
            <dt className="text-text-muted">{t("preview.taskType")}</dt>
            <dd className="text-text">
              {draft.taskTypeCode
                ? (reference?.taskTypes.find((option) => option.code === draft.taskTypeCode)?.name ?? draft.taskTypeCode)
                : none}
            </dd>
            <dt className="text-text-muted">{t("preview.solution")}</dt>
            <dd className="wrap-anywhere text-text">{draft.solution ?? none}</dd>
            <dt className="text-text-muted">{t("preview.commonMistakes")}</dt>
            <dd className="wrap-anywhere text-text">{draft.commonMistakes ?? none}</dd>
          </dl>

          {row.issues.length > 0 && (
            <div className="flex flex-col gap-2">
              <h3 className="text-label font-semibold text-text">{t("preview.issuesHeading")}</h3>
              <ul className="flex flex-col gap-2">
                {row.issues.map((issue, index) => {
                  const text = describe(issue);
                  const isError = issue.severity === "error";
                  return (
                    <li
                      key={`${issue.code}-${index}`}
                      className={
                        isError
                          ? "flex items-start gap-2 rounded-md border border-danger/30 bg-danger-bg px-3 py-2 text-body text-danger"
                          : "flex items-start gap-2 rounded-md border border-warning/30 bg-warning-bg px-3 py-2 text-body text-warning"
                      }
                    >
                      {isError ? (
                        <CircleX aria-hidden className="mt-0.5 size-4 shrink-0" />
                      ) : (
                        <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
                      )}
                      <span className="min-w-0">
                        <span className="font-semibold">
                          {isError ? t("preview.errorLabel") : t("preview.warningLabel")}:{" "}
                        </span>
                        {text.message}. <span className="text-text-secondary">{text.fix}</span>
                      </span>
                    </li>
                  );
                })}
              </ul>
              {errors.length > 0 && <p className="text-caption text-text-muted">{t("preview.fixHint")}</p>}
            </div>
          )}
        </div>
      </section>
    </Card>
  );
}
