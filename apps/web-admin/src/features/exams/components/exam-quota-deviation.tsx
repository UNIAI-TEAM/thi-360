"use client";

import { Button, cn, focusRing } from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import type { ReactNode } from "react";
import { canRequireQuota, findQuotaSections, quotaScope, type QuotaSectionTarget } from "../exam-overview";
import type { CheckIssue } from "../review-types";
import type { ExamPart, ExamQuota, StructureCombination } from "../types";
import { ExamName } from "./exam-review-shared";

export function ExamQuotaScopePrefix({
  quota,
  parts,
  combinations,
  subjectName,
}: {
  quota: ExamQuota | null;
  parts?: readonly ExamPart[];
  combinations?: readonly StructureCombination[];
  subjectName: (code: string) => string;
}): ReactNode {
  if (!quota) return null;
  const sections = parts ? findQuotaSections(parts, quota) : [];
  const subjectCodes =
    sections.length > 0
      ? sections.map((item) => item.subjectCode)
      : quota.subjectCodes?.length
        ? quota.subjectCodes.filter((code) => !(quota.excludeSubjectCodes ?? []).includes(code))
        : quotaScope(parts ?? [], quota, combinations).subjectCodes;
  const subjectsText = subjectCodes.map(subjectName).filter(Boolean).join(", ");
  const partNames = [...new Set(sections.map((item) => item.partName.trim()).filter(Boolean))];
  const singlePartName = partNames.length === 1 ? partNames[0]! : null;
  const quotaName = quota.name.trim() || null;

  let label: ReactNode = null;
  if (quotaName && subjectsText) {
    label = (
      <>
        <ExamName>{quotaName}</ExamName> ({subjectsText})
      </>
    );
  } else if (quotaName) {
    label = <ExamName>{quotaName}</ExamName>;
  } else if (singlePartName && subjectsText) {
    label = (
      <>
        <ExamName>{singlePartName}</ExamName> ({subjectsText})
      </>
    );
  } else if (subjectsText) {
    label = subjectsText;
  }

  if (!label) return null;
  return <span className="font-medium text-text">{label} · </span>;
}

export function ExamQuotaDeviationHint({
  issue,
  quota,
  parts,
  subjectName,
  onSelectSection,
  examId,
}: {
  issue: Pick<CheckIssue, "dimension">;
  quota: ExamQuota | null;
  parts?: readonly ExamPart[];
  subjectName: (code: string) => string;
  onSelectSection?: (sectionId: string) => void;
  examId?: string;
}): ReactNode {
  const t = useTranslations("exams.examQuota");
  if (!quota) return null;
  const single = canRequireQuota(quota);
  const dimension = quota.dimension ?? (issue.dimension === "DIFFICULTY" ? "DIFFICULTY" : "GRADE");
  const sections: QuotaSectionTarget[] = !single && parts ? findQuotaSections(parts, quota) : [];

  return (
    <div data-testid="exam-quota-deviation-hint" className="mt-1 flex flex-col gap-1 text-caption text-text-muted">
      <span>{single ? t("deviationHintSingle") : t("deviationHintMulti", { dimension })}</span>
      {sections.length > 0 && (onSelectSection || examId) && (
        <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span>{t("jumpToSubject")}</span>
          {sections.map((target) => {
            const label = subjectName(target.subjectCode);
            if (onSelectSection) {
              return (
                <Button
                  key={target.sectionId}
                  type="button"
                  variant="link"
                  size="sm"
                  className="h-auto p-0 text-caption"
                  aria-label={t("openSubjectAria", { subject: label })}
                  onClick={() => onSelectSection(target.sectionId)}
                >
                  {label}
                </Button>
              );
            }
            return (
              <Link
                key={target.sectionId}
                href={`/exams/${encodeURIComponent(examId!)}/structure?section=${encodeURIComponent(target.sectionId)}`}
                aria-label={t("openSubjectAria", { subject: label })}
                className={cn("rounded-sm text-caption font-medium text-primary-text hover:underline", focusRing)}
              >
                {label}
              </Link>
            );
          })}
        </span>
      )}
    </div>
  );
}
