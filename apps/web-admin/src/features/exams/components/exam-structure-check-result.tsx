"use client";

import { Button, StatusChip } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useId, useMemo } from "react";
import { issueBlockField } from "../check";
import { extractExamQuotaId, findExamQuotaForIssue, focusExamQuotaWhenReady } from "../exam-overview";
import { focusMapidRuleWhenReady } from "../mapid-rules";
import type { CheckIssue, ExamCheckResult } from "../review-types";
import type { ExamPart, ExamQuota, StructureCombination } from "../types";
import { requestBlockFieldFocus } from "./block-field-focus";
import type { StructureSelection } from "./exam-structure-draft";
import { ExamContentText } from "./exam-content-text";
import { ExamQuotaDeviationHint, ExamQuotaScopePrefix } from "./exam-quota-deviation";
import { labelBeforeName, useIssueText } from "./exam-review-shared";

export interface CheckResultNames {
  subject: (code: string) => string;
}

export interface ExamStructureCheckResultProps {
  result: ExamCheckResult;
  names: CheckResultNames;
  /** Cấu trúc đang sửa: để gọi quy tắc MAPID của vấn đề bằng câu mô tả của nó. */
  parts?: ExamPart[];
  quotas?: readonly ExamQuota[];
  combinations?: readonly StructureCombination[];
  onSelect: (selection: StructureSelection) => void;
}

/** Nơi sửa một lỗi kiểm tra: nhóm, môn hoặc phần chứa lỗi; lỗi cả bài không có chỗ sửa riêng (`null`). */
export function checkIssueTarget(issue: CheckIssue): StructureSelection | null {
  if (issue.blockId) return { kind: "block", blockId: issue.blockId };
  if (issue.sectionId) return { kind: "section", sectionId: issue.sectionId };
  if (issue.partId) return { kind: "part", partId: issue.partId };
  return null;
}

interface IssueGroup {
  key: string;
  subjectCode: string | null;
  issues: CheckIssue[];
}

/** Gom theo môn (lỗi không gắn môn vào nhóm "Cả bài"), giữ thứ tự xuất hiện. */
function groupBySubject(issues: readonly CheckIssue[]): IssueGroup[] {
  const groups = new Map<string, IssueGroup>();
  for (const issue of issues) {
    const key = issue.subjectCode ?? "";
    const group = groups.get(key) ?? { key, subjectCode: issue.subjectCode, issues: [] };
    group.issues.push(issue);
    groups.set(key, group);
  }
  return [...groups.values()];
}

export function ExamStructureCheckResult({
  result,
  names,
  parts,
  quotas,
  combinations,
  onSelect,
}: ExamStructureCheckResultProps) {
  const t = useTranslations("exams.structure.check");
  const structureT = useTranslations("exams.structure");
  const tMapid = useTranslations("exams.mapidRules.issue");
  const tQuota = useTranslations("exams.examQuota");
  const idPrefix = useId();
  const exam = useMemo(() => (parts ? { parts } : undefined), [parts]);
  const issueText = useIssueText(exam);
  const issueContent = useIssueText(exam, true);

  const seen = new Set<string>();
  const issues = result.groups
    .flatMap((group) => group.issues)
    .filter((issue) => (seen.has(issue.key) ? false : (seen.add(issue.key), true)));
  const errors = issues.filter((issue) => issue.severity === "ERROR");
  const warnings = issues.filter((issue) => issue.severity !== "ERROR");

  const renderList = (list: readonly CheckIssue[], testId: string) => (
    <ul data-testid={testId} className="flex flex-col gap-2">
      {groupBySubject(list).map((group, groupIndex) => (
        <li key={group.key} className="flex flex-col gap-1">
          <span className="text-label font-semibold text-text">
            {group.subjectCode ? names.subject(group.subjectCode) : t("wholeExam")}
          </span>
          <ul className="flex flex-col gap-1.5 border-l border-border pl-3">
            {group.issues.map((issue, issueIndex) => {
              const target = checkIssueTarget(issue);
              const text = issueText(issue);
              const ruleId = issue.ruleId ?? null;
              const sectionId = issue.sectionId ?? null;
              const isQuotaDeviation = issue.code === "EXAM_QUOTA_DEVIATION";
              const matchedQuota = isQuotaDeviation ? findExamQuotaForIssue(issue, quotas) : null;
              const quotaId = isQuotaDeviation ? (matchedQuota?.id ?? extractExamQuotaId(issue)) : null;
              const messageId = `${idPrefix}-${testId}-${groupIndex}-${issueIndex}-msg`;
              const actionId = `${idPrefix}-${testId}-${groupIndex}-${issueIndex}-go`;
              return (
                <li key={issue.key} className="flex flex-wrap items-start justify-between gap-2 text-body text-text">
                  <div className="min-w-0 flex-1">
                    <span id={messageId}>
                      {isQuotaDeviation && (
                        <ExamQuotaScopePrefix
                          quota={matchedQuota}
                          parts={parts}
                          combinations={combinations}
                          subjectName={names.subject}
                        />
                      )}
                      <ExamContentText text={issueContent(issue)} />
                    </span>
                    {issue.affectedCodes && issue.affectedCodes.length > 0 && (
                      <span className="ml-1.5 text-caption text-text-muted">
                        {t("codes", { codes: issue.affectedCodes.join(", ") })}
                      </span>
                    )}
                    {isQuotaDeviation && (
                      <ExamQuotaDeviationHint
                        issue={issue}
                        quota={matchedQuota}
                        parts={parts}
                        subjectName={names.subject}
                        onSelectSection={(targetSectionId) => onSelect({ kind: "section", sectionId: targetSectionId })}
                      />
                    )}
                  </div>
                  {ruleId && sectionId ? (
                    <Button
                      type="button"
                      variant="link"
                      size="sm"
                      aria-label={tMapid("goToRuleAria", { issue: text })}
                      onClick={() => {
                        onSelect({ kind: "section", sectionId });
                        void focusMapidRuleWhenReady(ruleId);
                      }}
                    >
                      {tMapid("goToRule")}
                    </Button>
                  ) : isQuotaDeviation ? (
                    <Button
                      id={actionId}
                      type="button"
                      variant="link"
                      size="sm"
                      aria-label={labelBeforeName(tQuota, "goToQuotaAria", "issue")}
                      aria-labelledby={`${actionId} ${messageId}`}
                      onClick={() => {
                        onSelect({ kind: "exam" });
                        if (quotaId) void focusExamQuotaWhenReady(quotaId);
                      }}
                    >
                      {tQuota("goToQuota")}
                    </Button>
                  ) : (
                    target && (
                      <Button
                        type="button"
                        variant="link"
                        size="sm"
                        onClick={() => {
                          const field = issue.blockId ? issueBlockField(issue) : null;
                          if (issue.blockId && field) requestBlockFieldFocus(issue.blockId, field);
                          onSelect(target);
                        }}
                      >
                        {structureT("issueGo")}
                      </Button>
                    )
                  )}
                </li>
              );
            })}
          </ul>
        </li>
      ))}
    </ul>
  );

  return (
    <section aria-labelledby="structure-check-title" data-testid="structure-check-result" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <h3 id="structure-check-title" className="text-label font-semibold text-text">
          {t("resultTitle")}
        </h3>
        {errors.length === 0 ? (
          <StatusChip tone="success">{t("pass")}</StatusChip>
        ) : (
          <StatusChip tone="danger">{t("errorsChip", { count: errors.length })}</StatusChip>
        )}
        {warnings.length > 0 && <StatusChip tone="warning">{t("warningsChip", { count: warnings.length })}</StatusChip>}
      </div>
      {errors.length > 0 && (
        <div className="flex flex-col gap-2">
          <h4 className="text-caption font-semibold text-danger">{t("errorsTitle", { count: errors.length })}</h4>
          {renderList(errors, "structure-check-errors")}
        </div>
      )}
      {warnings.length > 0 && (
        <div className="flex flex-col gap-2">
          <h4 className="text-caption font-semibold text-warning">{t("warningsTitle", { count: warnings.length })}</h4>
          {renderList(warnings, "structure-check-warnings")}
        </div>
      )}
    </section>
  );
}
