"use client";

import { Button, StatusChip } from "@thi360/ui";
import { Info } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId } from "react";
import type { ReferenceEntry, ReferenceReason } from "../reference-distribution";
import type { MatrixUnit } from "../types";
import type { MatrixLabels } from "./block-matrix-editor";

/**
 * Tổng của mục đổi (R-122 dòng 6): bảng nhập theo tỷ lệ hiện số câu mới và chờ xác nhận; bảng nhập theo số câu giữ số cũ
 * và báo thiếu hoặc vượt ở bảng. "Áp dụng số mới" ghi nhận tổng mới; "Giữ số cũ" đổi các bảng tỷ lệ về số câu của tổng cũ.
 */
export function TotalChangeNotice({
  oldTotal,
  newTotal,
  lines,
  onApply,
  onKeep,
}: {
  oldTotal: number;
  newTotal: number;
  /** Số câu mới của từng bảng nhập theo tỷ lệ, ví dụ "Độ khó: NB 5 · TH 10". */
  lines: readonly string[];
  onApply: () => void;
  onKeep: () => void;
}) {
  const t = useTranslations("exams.allocation.totalChange");
  const headingId = useId();
  return (
    <section aria-labelledby={headingId} role="alert" className="flex flex-col gap-2 rounded-md border border-warning/40 bg-warning-bg px-3 py-3">
      <p id={headingId} className="flex items-start gap-2 text-body font-semibold text-text">
        <Info aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
        {t("title", { total: newTotal, old: oldTotal })}
      </p>
      <ul className="ml-6 list-disc text-body text-text-secondary">
        {lines.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
      <p className="text-caption text-text-muted">{t("hint", { old: oldTotal })}</p>
      <div className="flex flex-wrap gap-2">
        <Button type="button" size="sm" onClick={onApply}>
          {t("apply")}
        </Button>
        <Button type="button" variant="secondary" size="sm" onClick={onKeep}>
          {t("keep")}
        </Button>
      </div>
    </section>
  );
}

const REASON_KEYS: Record<ReferenceReason, string> = {
  BASIS: "reasonBasis",
  NO_VALUES: "reasonNoValues",
  MERGED_BUCKETS: "reasonMerged",
  UNIT_CONFLICT: "reasonUnit",
  IN_MATRIX: "reasonInMatrix",
};

/**
 * Phân bố tham khảo của môn và hai nút chuyển (2B 15.3, H7). Mỗi dòng ghi đơn vị, các mức và số; nút bị khóa kèm lý do
 * (đơn vị lệnh hỏi hoặc điểm, chưa có số, mức gộp nhiều mã).
 */
export function ReferenceDistributions({
  entries,
  labels,
  subjectName,
  onUse,
  onApply,
}: {
  entries: readonly ReferenceEntry[];
  labels: MatrixLabels;
  subjectName: string;
  onUse: (entry: ReferenceEntry) => void;
  onApply: (entry: ReferenceEntry) => void;
}) {
  const t = useTranslations("exams.allocation.reference");
  const format = useFormatter();
  const headingId = useId();
  if (entries.length === 0) return null;
  const number = (value: number) => format.number(value, { maximumFractionDigits: 2 });
  const unitText = (unit: MatrixUnit, value: number) => (unit === "PERCENT" ? t("percentValue", { value: number(value) }) : number(value));

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3 rounded-md border border-border p-4">
      <div className="flex flex-col gap-0.5">
        <h4 id={headingId} className="text-label font-semibold text-text">
          {t("title")}
        </h4>
        <p className="text-caption text-text-muted">{t("hint")}</p>
      </div>
      <ul className="flex flex-col gap-3">
        {entries.map((entry) => {
          const isQuota = entry.kind === "quota";
          const dimension = isQuota ? entry.quota.dimension : entry.dimension;
          const dimensionName = t(dimension === "GRADE" ? "grade" : "difficulty");
          const basis = isQuota ? entry.quota.basis : entry.basis;
          const unit = isQuota ? (entry.quota.buckets.some((bucket) => bucket.percent !== undefined && bucket.percent !== null) ? "PERCENT" : "COUNT") : entry.unit;
          const label = dimension === "GRADE" ? labels.grade : labels.difficulty;
          const parts = isQuota
            ? entry.quota.buckets.map((bucket) => `${bucket.codes.map(label).join(" + ")}: ${unitText(unit, bucket.percent ?? bucket.count ?? 0)}`)
            : entry.values.filter((item) => item.value > 0).map((item) => `${label(item.code)}: ${unitText(unit, item.value)}`);
          const title = isQuota && entry.quota.name ? entry.quota.name : t("entryTitle", { subject: subjectName, dimension: dimensionName });
          const noValues = parts.length === 0;
          const reasonText = (reason: ReferenceReason | null) => (reason ? t(REASON_KEYS[reason], { dimension: dimensionName }) : null);
          const useReason = reasonText(entry.use);
          const applyReason = reasonText(entry.apply);
          return (
            <li key={entry.key} className="flex flex-col gap-2 rounded-md bg-subtle px-3 py-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-body font-semibold text-text">{title}</span>
                <StatusChip tone="info">{t("referenceChip")}</StatusChip>
                <span className="text-caption text-text-muted">{t("basis", { unit: t(`basis${basis}`) })}</span>
                {(noValues || entry.expertPending) && <StatusChip tone="warning">{t("expertPending")}</StatusChip>}
              </div>
              <p className="text-body text-text-secondary">{noValues ? t("noValues") : parts.join(" · ")}</p>
              <div className="flex flex-wrap gap-2">
                <Button type="button" variant="secondary" size="sm" disabled={entry.use !== null} aria-label={t("useAria", { title })} onClick={() => onUse(entry)}>
                  {t("use")}
                </Button>
                <Button type="button" variant="secondary" size="sm" disabled={entry.apply !== null} aria-label={t("applyAria", { title })} onClick={() => onApply(entry)}>
                  {t("apply")}
                </Button>
              </div>
              {(applyReason ?? useReason) && <p className="text-caption text-text-muted">{entry.apply !== null ? applyReason : useReason}</p>}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
