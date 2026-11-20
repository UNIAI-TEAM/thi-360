"use client";

import {
  Button,
  cn,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  focusRing,
} from "@thi360/ui";
import { ChevronRight, Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import type { ContentReference } from "@/features/content/types";
import { AllocationGap } from "../components/allocation-gap";
import type { MatrixLabels } from "../components/block-matrix-editor";
import { ChoiceCardGroup, OptionalNumberInput } from "../components/create-exam-fields";
import { ScrollableTable } from "../components/scrollable-table";
import { TopicQuotaTable, type TopicQuotaServerError } from "../components/topic-quota-table";
import { type BlockIssue, type MatrixAxes, validateMatrix } from "../matrix";
import type { BlockMatrix, ExamQuota, ExamSection, MatrixUnit, TopicQuota } from "../types";
import { CombinedPreview, type CombinedDirection, CombinedTable } from "./reference-combined";
import { referenceQuotaEntries, ReferenceQuotas } from "./reference-quotas";
import { EnforcementControl } from "./enforcement-control";
import {
  blankMatrix,
  convertReferenceUnit,
  dimensionCell,
  dimensionDeclared,
  dimensionEnforcement,
  enforcementModeOf,
  gradeVisible,
  type MatrixDimensionKey,
  pruneMatrix,
  resolvedDimension,
  setDimensionCell,
  setDimensionEnforcement,
} from "./row-list-model";
import type { SubjectFormEntry } from "./registry";

type PercentIssue = Extract<BlockIssue, { code: "PERCENT_TOTAL" }>;

export interface ReferenceBlockProps {
  section: ExamSection;
  entry: SubjectFormEntry;
  axes: MatrixAxes;
  labels: MatrixLabels;
  subjectName: string;
  /** Số câu của mục: mẫu số của "Đã khai x/y" và của tỷ lệ (R-9). */
  count: number | null;
  reference: ContentReference;
  /** Chỉ tiêu cấp bài của bài: phân bố cả môn của môn này (R-8). */
  examQuotas: readonly ExamQuota[];
  onExamQuotas?: (examQuotas: ExamQuota[]) => void;
  topicError: TopicQuotaServerError;
  onChange: (update: (section: ExamSection) => ExamSection) => void;
}

/** Chỉ tiêu mạch theo lệnh (V-SAT, chỉ đối chiếu): màn vẽ riêng ở khung (bảng "Mạch đối chiếu theo lệnh"), không nằm trong bảng chỉ tiêu chủ đề của mục. */
const isStrand = (quota: TopicQuota) => quota.basis === "PROMPT" && quota.enforcement === "REFERENCE";

/**
 * Phân bổ đối chiếu của mục (FTM R-7 đến R-11): khối gấp sẵn ở cuối Danh sách dòng. Phân bố lớp và độ khó của mục với mức áp dụng theo
 * kỳ (`EnforcementControl`, không có nút Bắt buộc/Tham khảo chung; công tắc "Áp dụng khi sinh đề" ở kỳ mặc định Chỉ đối chiếu), phân bố
 * cấp bài của môn, và ở kỳ cho nhập % thì chọn Số câu / Tỷ lệ (ghi `unit: PERCENT`; tổng chưa đủ 100% giữ guard `MATRIX_PERCENT`, khối mở
 * sẵn để thấy lỗi). Khối lớp không hiện ở môn bị ẩn nhưng dữ liệu lớp giữ nguyên khi lưu. Ô để trống khác ô nhập 0; chiều không còn ô
 * nào ghi mảng rỗng. V-SAT mở bảng kết hợp Lớp × Độ khó từ "Thêm điều kiện" ở cấp phần, có xem trước chuyển đổi (R-10).
 */
export function ReferenceBlock({ section, entry, axes, labels, subjectName, count, reference, examQuotas, onExamQuotas, topicError, onChange }: ReferenceBlockProps) {
  const t = useTranslations("exams.subjectForm.rows.reference");
  const headingId = useId();
  const [userOpen, setUserOpen] = useState(false);
  // Chưa có ma trận thì chọn Số câu / Tỷ lệ chỉ là lựa chọn của màn; ma trận được tạo khi nhập ô đầu tiên (không ghi ma trận trống vào bản nháp).
  const [pendingUnit, setPendingUnit] = useState<MatrixUnit | null>(null);
  const [preview, setPreview] = useState<CombinedDirection | null>(null);
  const showGrade = gradeVisible(entry);
  const dimensions: MatrixDimensionKey[] = showGrade ? ["GRADE", "DIFFICULTY"] : ["DIFFICULTY"];
  const matrix = section.matrix;
  const combined = matrix?.mode === "COMBINED";
  const percentable = entry.percentEntry === "COUNT_OR_PERCENT";
  const unit: MatrixUnit = matrix?.unit ?? pendingUnit ?? "COUNT";
  const questionBasis = (matrix?.basis ?? "QUESTION") === "QUESTION";
  const applicable = entry.enforcement === "REFERENCE_WITH_APPLY";
  const combinedDeclared = matrix?.cells.some((cell) => cell.value > 0) ?? false;
  const percentIssues = validateMatrix(matrix, count, axes).filter((issue): issue is PercentIssue => issue.code === "PERCENT_TOTAL");
  const visiblePercentIssues = percentIssues.filter((issue) => (combined ? issue.dimension === "COMBINED" : (issue.dimension === "GRADE" || issue.dimension === "DIFFICULTY") && dimensions.includes(issue.dimension)));
  const quotaEntries = entry.enforcement === "REFERENCE_WITH_APPLY" ? referenceQuotaEntries(section.subjectCode, examQuotas, showGrade) : [];
  const topicQuotas = section.topicQuotas ?? [];

  const setMatrix = (next: BlockMatrix | null) => onChange((current) => ({ ...current, matrix: next }));
  const setUnit = (next: MatrixUnit) => {
    if (!matrix) setPendingUnit(next);
    else {
      const hidden: MatrixDimensionKey[] = showGrade ? [] : ["GRADE"];
      onChange((current) => ({ ...current, matrix: current.matrix ? convertReferenceUnit(current.matrix, next, count ?? 0, axes, hidden) : current.matrix }));
    }
  };
  const applyQuota = (quotaId: string, value: ExamQuota["enforcement"]) => onExamQuotas?.(examQuotas.map((quota) => (quota.id === quotaId ? { ...quota, enforcement: value } : quota)));

  return (
    <details
      className="group rounded-md border border-border"
      data-reference-block
      aria-labelledby={headingId}
      open={userOpen || visiblePercentIssues.length > 0}
      onToggle={(event) => setUserOpen(event.currentTarget.open)}
    >
      <summary className={cn("flex cursor-pointer list-none flex-wrap items-center gap-2 rounded-md px-4 py-3 text-label font-semibold text-text", focusRing)}>
        <ChevronRight aria-hidden className="size-4 transition-transform group-open:rotate-90 motion-reduce:transition-none" />
        <span id={headingId}>{t("title")}</span>
        <span className="text-caption font-normal text-text-muted">{t("hint")}</span>
      </summary>
      <div className="flex flex-col gap-4 border-t border-border p-4">
        {(percentable || entry.combinedMatrix === "PART_LEVEL") && (
          <div className="flex flex-wrap items-end justify-between gap-3">
            {percentable && questionBasis ? (
              <ChoiceCardGroup
                variant="pill"
                legend={t("unit.legend")}
                name={`${section.id}-reference-unit`}
                value={unit}
                disabled={count === null && matrix !== null}
                onChange={setUnit}
                options={[
                  { value: "COUNT", label: t("unit.count") },
                  { value: "PERCENT", label: t("unit.percent") },
                ]}
              />
            ) : (
              <span />
            )}
            {entry.combinedMatrix === "PART_LEVEL" && showGrade && (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button type="button" variant="secondary" size="sm" aria-label={t("conditions.addAria")}>
                    <Plus />
                    <span>{t("conditions.add")}</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent aria-label={t("conditions.menuLabel")} className="w-80 max-w-[calc(100vw-2rem)]">
                  <DropdownMenuItem disabled={combined || preview !== null} className="h-auto min-h-10 flex-col items-start gap-0 py-1.5" onSelect={() => setPreview("TO_COMBINED")}>
                    <span>{t("conditions.combined.label")}</span>
                    <span className="text-caption font-normal text-text-muted">{combined ? t("conditions.added") : t("conditions.combined.hint")}</span>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            )}
          </div>
        )}
        {preview && (
          <CombinedPreview
            direction={preview}
            matrix={matrix}
            axes={axes}
            labels={labels}
            onConfirm={(next) => {
              setMatrix(next);
              setPreview(null);
            }}
            onCancel={() => setPreview(null)}
          />
        )}
        {combined && matrix ? (
          <section className="flex flex-col gap-2" data-reference-dimension="COMBINED">
            <EnforcementControl
              mode={applicable && questionBasis && !combinedDeclared ? "DECLARED_REQUIRED" : entry.enforcement}
              name={t("conditions.combined.label")}
              value={combinedDeclared ? dimensionEnforcement(matrix, "GRADE") : "REFERENCE"}
              declared={combinedDeclared}
              lockReason={questionBasis ? undefined : "promptBasis"}
              onChange={applicable && questionBasis && combinedDeclared ? (value) => setMatrix(setDimensionEnforcement(matrix, "GRADE", value)) : undefined}
            />
            <CombinedTable
              matrix={matrix}
              axes={axes}
              labels={labels}
              count={count}
              percentError={visiblePercentIssues.find((issue) => issue.dimension === "COMBINED")?.actual ?? null}
              onChange={setMatrix}
              onBack={() => setPreview("TO_SEPARATE")}
            />
          </section>
        ) : (
          dimensions.map((dimension) => (
            <DimensionBlock
              key={dimension}
              dimension={dimension}
              section={section}
              entry={entry}
              axes={axes}
              labels={labels}
              subjectName={subjectName}
              count={count}
              unit={unit}
              percentTotal={visiblePercentIssues.find((issue) => issue.dimension === dimension)}
              onPruneUnit={setPendingUnit}
              onChange={onChange}
            />
          ))
        )}
        <ReferenceQuotas entries={quotaEntries} labels={labels} subjectName={subjectName} onApply={applyQuota} />
        {percentable && (
          <TopicQuotaTable
            id={`${section.id}-reference`}
            quotas={topicQuotas.filter((quota) => !isStrand(quota))}
            onChange={(next) => onChange((current) => ({ ...current, topicQuotas: [...(current.topicQuotas ?? []).filter(isStrand), ...next] }))}
            total={count ?? 0}
            levelName={subjectName}
            subjectCode={section.subjectCode}
            reference={reference}
            axes={axes}
            labels={labels}
            serverError={topicError}
            hideGrade={!showGrade}
          />
        )}
      </div>
    </details>
  );
}

interface DimensionBlockProps {
  dimension: MatrixDimensionKey;
  section: ExamSection;
  entry: SubjectFormEntry;
  axes: MatrixAxes;
  labels: MatrixLabels;
  subjectName: string;
  count: number | null;
  unit: MatrixUnit;
  /** Lỗi tổng tỷ lệ của chiều này (chưa đủ 100%): chặn lưu ở `saveBlockers`, hiện tại bảng. */
  percentTotal?: PercentIssue;
  onPruneUnit: (unit: MatrixUnit) => void;
  onChange: (update: (section: ExamSection) => ExamSection) => void;
}

function DimensionBlock({ dimension, section, entry, axes, labels, subjectName, count, unit, percentTotal, onPruneUnit, onChange }: DimensionBlockProps) {
  const t = useTranslations("exams.subjectForm.rows.reference");
  const captionId = useId();
  const grade = dimension === "GRADE";
  const codes = grade ? axes.grades : axes.difficulties;
  const label = grade ? labels.grade : labels.difficulty;
  const title = t(grade ? "gradeTitle" : "difficultyTitle");
  const matrix = section.matrix;
  const percent = unit === "PERCENT";
  const declared = dimensionDeclared(matrix, dimension);
  const missing = count === null ? null : count - declared.total;
  const resolved = resolvedDimension(matrix, dimension, count ?? 0);
  const promptBasis = (matrix?.basis ?? "QUESTION") !== "QUESTION";
  const applicable = entry.enforcement === "REFERENCE_WITH_APPLY";
  const dimensionItems = (grade ? matrix?.byGrade : matrix?.byDifficulty) ?? [];
  const resolvedByCode = new Map(dimensionItems.map((item, position) => [item.code, resolved?.[position] ?? 0] as const));
  // Phân bố chưa khai ở kỳ mặc định Chỉ đối chiếu hiện Chỉ đối chiếu (không có gì để áp dụng), không phải mức Bắt buộc của trường thiếu.
  const shownEnforcement = applicable && declared.cells === 0 ? "REFERENCE" : dimensionEnforcement(matrix, dimension);

  const setCell = (code: string, value: number | null) => {
    const preview = setDimensionCell(matrix ?? { ...blankMatrix(), unit, basis: "QUESTION" }, dimension, code, value, axes, entry.enforcement);
    if (pruneMatrix(preview) === null) onPruneUnit(preview.unit);
    onChange((current) => ({
      ...current,
      matrix: pruneMatrix(setDimensionCell(current.matrix ?? { ...blankMatrix(), unit, basis: "QUESTION" }, dimension, code, value, axes, entry.enforcement)),
    }));
  };

  return (
    <section className="flex min-w-0 flex-col gap-2" aria-labelledby={captionId} data-reference-dimension={dimension}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h4 id={captionId} className="text-label font-semibold text-text">
          {t("dimensionTitle", { subject: subjectName, dimension: title })}
        </h4>
        <EnforcementControl
          mode={applicable && !promptBasis && declared.cells === 0 ? "DECLARED_REQUIRED" : enforcementModeOf(entry, matrix, dimension)}
          name={title}
          value={shownEnforcement}
          declared={declared.cells > 0}
          lockReason={applicable && promptBasis ? "promptBasis" : undefined}
          onChange={
            applicable && matrix && declared.cells > 0
              ? (value) => onChange((current) => ({ ...current, matrix: current.matrix ? setDimensionEnforcement(current.matrix, dimension, value) : current.matrix }))
              : undefined
          }
        />
      </div>
      <ScrollableTable labelledBy={captionId} className="rounded-md border border-border">
        <table className="w-full border-separate border-spacing-0 text-body tabular-nums">
          <caption className="sr-only">{t("caption", { subject: subjectName, dimension: title })}</caption>
          <thead>
            <tr className="text-left text-caption text-text-muted">
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{title}</th>
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold">{t(percent ? "percent" : "questions")}</th>
              {percent && <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold">{t("resolved")}</th>}
            </tr>
          </thead>
          <tbody>
            {codes.map((code) => (
              <tr key={code}>
                <th scope="row" className="border-b border-border px-3 py-2 text-left font-medium text-text">{label(code)}</th>
                <td className="border-b border-border px-3 py-1.5 text-right">
                  <OptionalNumberInput
                    className="ml-auto h-9 w-20"
                    aria-label={t(percent ? "cellPercentAria" : "cellAria", { dimension: title, label: label(code) })}
                    value={dimensionCell(matrix, dimension, code)}
                    onValueChange={(value) => setCell(code, value === null ? null : Math.max(0, percent ? value : Math.trunc(value)))}
                  />
                </td>
                {percent && (
                  <td className="border-b border-border px-3 py-2 text-right text-text-secondary">
                    {resolved === null || dimensionCell(matrix, dimension, code) === null ? "—" : t("resolvedValue", { count: resolvedByCode.get(code) ?? 0 })}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </ScrollableTable>
      {declared.cells > 0 && (
        <p className={cn("flex flex-wrap items-center gap-2 text-caption", !percent && missing !== 0 ? "text-text-secondary" : "text-text-muted")} role="status">
          {percent ? (
            <output data-reference-declared={dimension}>{t("declaredPercent", { total: declared.total })}</output>
          ) : (
            <output data-reference-declared={dimension}>{count === null ? t("declaredNoTotal", { total: declared.total }) : t("declared", { total: declared.total, count })}</output>
          )}
          {!percent && missing !== null && missing !== 0 && <AllocationGap missing={missing} />}
        </p>
      )}
      {percent && percentTotal && (
        <p role="alert" className="text-caption text-danger" data-reference-percent-error={dimension}>
          {t("percentTotal", { total: percentTotal.actual })}
        </p>
      )}
    </section>
  );
}
