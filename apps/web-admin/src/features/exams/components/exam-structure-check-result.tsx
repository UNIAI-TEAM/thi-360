"use client";

import { Button, StatusChip } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useMemo } from "react";
import { focusMapidRuleWhenReady } from "../mapid-rules";
import type { CheckIssue, ExamCheckResult } from "../review-types";
import type { ExamPart } from "../types";
import type { StructureSelection } from "./exam-structure-draft";
import { ExamContentText } from "./exam-content-text";
import { useIssueText } from "./exam-review-shared";

export interface CheckResultNames {
  subject: (code: string) => string;
}

export interface ExamStructureCheckResultProps {
  result: ExamCheckResult;
  names: CheckResultNames;
  /** Cấu trúc đang sửa: để gọi quy tắc MAPID của vấn đề bằng câu mô tả của nó. */
  parts?: ExamPart[];
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

/**
 * Kết quả kiểm tra gần nhất ở vùng tổng hợp (spec 15.2): lỗi và cảnh báo tách riêng, gom theo môn, kèm các mã bài thi bị
 * ảnh hưởng (`affectedCodes`, R-24). Chi tiết đầy đủ nằm ở tab Kiểm tra và xem trước.
 */
export function ExamStructureCheckResult({ result, names, parts, onSelect }: ExamStructureCheckResultProps) {
  const t = useTranslations("exams.structure.check");
  const structureT = useTranslations("exams.structure");
  const tMapid = useTranslations("exams.mapidRules.issue");
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
      {groupBySubject(list).map((group) => (
        <li key={group.key} className="flex flex-col gap-1">
          <span className="text-label font-semibold text-text">
            {group.subjectCode ? names.subject(group.subjectCode) : t("wholeExam")}
          </span>
          <ul className="flex flex-col gap-1.5 border-l border-border pl-3">
            {group.issues.map((issue) => {
              const target = checkIssueTarget(issue);
              const text = issueText(issue);
              const ruleId = issue.ruleId ?? null;
              const sectionId = issue.sectionId ?? null;
              return (
                <li key={issue.key} className="flex flex-wrap items-start justify-between gap-2 text-body text-text">
                  <span className="min-w-0 flex-1">
                    <ExamContentText text={issueContent(issue)} />
                    {issue.affectedCodes && issue.affectedCodes.length > 0 && (
                      <span className="ml-1.5 text-caption text-text-muted">
                        {t("codes", { codes: issue.affectedCodes.join(", ") })}
                      </span>
                    )}
                  </span>
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
                  ) : (
                    target && (
                      <Button type="button" variant="link" size="sm" onClick={() => onSelect(target)}>
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
