"use client";

import { Chip } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useId } from "react";
import type { MatrixLabels } from "../components/block-matrix-editor";
import { ExamName } from "../components/exam-review-shared";
import { quotaUnitOf } from "../exam-overview";
import { sum } from "../matrix";
import type { ExamQuota, QuotaEnforcement } from "../types";
import { EnforcementControl } from "./enforcement-control";
import type { LockReasonCode } from "./lock-reason";

/** Mã mức "Chưa phân loại" nếu chỉ tiêu cấp bài có mức này (câu thiếu nhãn dồn về đây). */
const UNCLASSIFIED_BUCKET = "UNCLASSIFIED";

interface ReferenceQuotaEntry {
  quota: ExamQuota;
  /** Khóa nút "Áp dụng khi sinh đề" kèm lý do (R-8): phân bố theo lệnh hỏi, hoặc chỉ tiêu nhiều môn; `null`: bật được. */
  lock: Extract<LockReasonCode, "promptBasis" | "multiSubject"> | null;
  /** Tổng các mức (số câu, lệnh hoặc %). */
  total: number;
  /** Số lệnh chưa phân loại: mẫu số của môn (tổng lớn nhất trong các phân bố theo lệnh của môn) trừ tổng của chiều này; 0 khi không thiếu. */
  unclassified: number;
}

const bucketValue = (bucket: ExamQuota["buckets"][number]): number => bucket.percent ?? bucket.count ?? 0;

/**
 * Phân bố cấp bài (cả môn) áp cho môn của mục (R-7, R-8): chỉ tiêu của riêng môn này, hoặc trải nhiều môn có môn này (chỉ đối chiếu,
 * khóa kèm lý do). Chiều lớp không hiện ở môn bị ẩn lớp (R-11). Phân bố theo lệnh hỏi giữ đơn vị và mẫu số của mẫu; chiều có tổng
 * thấp hơn mẫu số của môn hiện dòng "Chưa phân loại" cho số lệnh còn thiếu (câu thiếu nhãn, H44, R-139).
 */
export function referenceQuotaEntries(subjectCode: string, examQuotas: readonly ExamQuota[], showGrade: boolean): ReferenceQuotaEntry[] {
  const applies = (quota: ExamQuota) =>
    !(quota.excludeSubjectCodes ?? []).includes(subjectCode) && ((quota.subjectCodes?.length ?? 0) === 0 || (quota.subjectCodes ?? []).includes(subjectCode));
  const mineAll = examQuotas.filter(applies);
  const single = (quota: ExamQuota) => quota.subjectCodes?.length === 1;
  const totalOf = (quota: ExamQuota) => sum(quota.buckets.map(bucketValue));
  const denominator = Math.max(0, ...mineAll.filter((quota) => single(quota) && quota.basis === "PROMPT" && quotaUnitOf(quota) === "COUNT").map(totalOf));
  return mineAll
    .filter((quota) => showGrade || quota.dimension !== "GRADE")
    .map((quota) => {
      const total = totalOf(quota);
      const lock = !single(quota) ? "multiSubject" : quota.basis !== "QUESTION" ? "promptBasis" : null;
      const hasUnclassifiedBucket = quota.buckets.some((bucket) => bucket.codes.includes(UNCLASSIFIED_BUCKET));
      return { quota, lock, total, unclassified: single(quota) && quota.basis === "PROMPT" && quotaUnitOf(quota) === "COUNT" && !hasUnclassifiedBucket ? Math.max(0, denominator - total) : 0 };
    });
}

/** Mẫu số theo lệnh của môn: tổng lớn nhất trong các phân bố theo lệnh của riêng môn (ví dụ Toán 67 lệnh); `null` khi không có. */
export function commandDenominator(entries: readonly ReferenceQuotaEntry[]): number | null {
  const totals = entries.filter((entry) => entry.lock !== "multiSubject" && entry.quota.basis === "PROMPT" && quotaUnitOf(entry.quota) === "COUNT").map((entry) => entry.total + entry.unclassified);
  return totals.length > 0 ? Math.max(...totals) : null;
}

interface ReferenceQuotasProps {
  entries: readonly ReferenceQuotaEntry[];
  labels: MatrixLabels;
  subjectName: string;
  onApply: (quotaId: string, value: QuotaEnforcement) => void;
}

/** Danh sách phân bố cấp bài của môn trong Phân bổ đối chiếu: số, đơn vị, mẫu số và công tắc "Áp dụng khi sinh đề" (khóa kèm lý do khi theo lệnh hỏi hoặc nhiều môn). */
export function ReferenceQuotas({ entries, labels, subjectName, onApply }: ReferenceQuotasProps) {
  const t = useTranslations("exams.subjectForm.rows.reference.quotas");
  const headingId = useId();
  if (entries.length === 0) return null;
  const denominator = commandDenominator(entries);

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-2" data-reference-quotas>
      <div className="flex flex-col gap-0.5">
        <h4 id={headingId} className="text-label font-semibold text-text">
          {t("title", { subject: subjectName })}
        </h4>
        {denominator !== null && <p className="text-caption text-text-muted" data-command-denominator>{t("denominator", { subject: subjectName, count: denominator })}</p>}
      </div>
      <ul className="flex flex-col gap-3">
        {entries.map(({ quota, lock, unclassified }, index) => {
          const label = quota.dimension === "GRADE" ? labels.grade : labels.difficulty;
          const percent = quotaUnitOf(quota) === "PERCENT";
          const dimensionLabel = t("entryTitle", { dimension: t(quota.dimension === "GRADE" ? "grade" : "difficulty") });
          const quotaNameId = `${headingId}-quota-${index}`;
          const values = quota.buckets.map((bucket) => ({ key: bucket.codes.join("+"), text: t(percent ? "percentValue" : "value", { label: bucket.codes.map((code) => (code === UNCLASSIFIED_BUCKET ? t("unclassified") : label(code))).join(" + "), value: bucketValue(bucket) }) }));
          return (
            <li key={quota.id} className="flex flex-col gap-2 rounded-md bg-subtle px-3 py-3" data-reference-quota={quota.id}>
              <div className="flex flex-wrap items-center gap-2">
                <span id={quotaNameId} className="text-body font-semibold text-text">
                  {quota.name ? <ExamName>{quota.name}</ExamName> : dimensionLabel}
                </span>
                <Chip variant="tag">{t(`basis.${quota.basis}`)}</Chip>
              </div>
              <ul className="flex flex-wrap gap-x-4 gap-y-1 text-body text-text-secondary" aria-labelledby={quotaNameId}>
                {values.map((value) => (
                  <li key={value.key}>{value.text}</li>
                ))}
                {unclassified > 0 && <li data-unclassified>{t("unclassifiedValue", { count: unclassified })}</li>}
              </ul>
              <EnforcementControl mode="REFERENCE_WITH_APPLY" name={dimensionLabel} value={quota.enforcement} lockReason={lock ?? undefined} onChange={(value) => onApply(quota.id, value)} />
            </li>
          );
        })}
      </ul>
    </section>
  );
}
