"use client";

import { Button, Checkbox, cn, focusRing, IconButton, Input, StatusChip } from "@thi360/ui";
import { CircleMinus, ListTree, Plus, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId, useMemo, useRef, useState } from "react";
import { KnowledgePickerDialog } from "@/features/content/components/knowledge-picker-dialog";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import type { ContentReference, KnowledgeRef } from "@/features/content/types";
import {
  blockingMappingRows,
  convertTopicEntry,
  countFromPercent,
  type EntryMode,
  exactPercent,
  isMappingPending,
  MAX_TOPIC_QUOTAS,
  newTopicQuota,
  topicEntryMode,
  topicQuotaView,
} from "../allocation";
import type { MatrixAxes } from "../matrix";
import type { TopicQuota } from "../types";
import { BlockNumberInput } from "./block-number-input";
import type { MatrixLabels } from "./block-matrix-editor";
import { ChoiceCardGroup } from "./create-exam-fields";
import { EnforcementControl } from "../subject-forms/enforcement-control";
import { draftId } from "./exam-structure-draft";

/** Lỗi do máy chủ trả về cho khối chỉ tiêu (422 `EXAM_STRUCTURE_INVALID`): quá 8 chỉ tiêu (H20) hoặc tỷ lệ ngoài 0–100. */
export type TopicQuotaServerError = "MAX_TOPIC_QUOTAS" | "PERCENT_OUT_OF_RANGE" | null;

interface TopicQuotaTableProps {
  /** Tiền tố id và tên nhóm radio: id của môn hoặc nhóm. */
  id: string;
  quotas: readonly TopicQuota[];
  onChange: (quotas: TopicQuota[]) => void;
  /** Mẫu số: số câu của mục hoặc của nhóm; 0 khi chưa biết. */
  total: number;
  /** Tên mục hoặc nhóm làm mẫu số, ví dụ "Toán học". */
  levelName: string;
  subjectCode: string;
  reference: ContentReference;
  axes: MatrixAxes;
  labels: MatrixLabels;
  serverError?: TopicQuotaServerError;
  readOnly?: boolean;
  /** `COUNT_ONLY` ẩn nút Số câu / Tỷ lệ (R-9: HSA, QDA, BCA, THPTQG không nhập %); mặc định giữ hành vi MD, MT. */
  entry?: "COUNT_ONLY";
  /** Ẩn cột Giới hạn khối lớp (R-11, AD-118); dữ liệu `gradeCodes` đã có giữ nguyên. Mặc định `false` (MD, MT). */
  hideGrade?: boolean;
}

/**
 * Chỉ tiêu chủ đề của một mục hoặc một nhóm (2B 15.3, H8, H20, H30, H31; bản vẽ `ChiTieuCapBai2B`, `PhanBoVact2B`).
 * Số câu là số **ít nhất** (K6). Nhập theo số câu hoặc theo tỷ lệ; dòng Phần còn lại do màn tính, không lưu. Dòng chưa gắn
 * MAPID lưu được và ghi "Chờ mapping"; gắn mã sau thì giữ nguyên số câu. Tối đa 8 chỉ tiêu một cấp, lỗi hiện ngay ở khối.
 */
export function TopicQuotaTable({
  id,
  quotas,
  onChange,
  total,
  levelName,
  subjectCode,
  reference,
  axes,
  labels,
  serverError = null,
  readOnly = false,
  entry: entryRestriction,
  hideGrade = false,
}: TopicQuotaTableProps) {
  const t = useTranslations("exams.topicQuota");
  const matrixText = useTranslations("exams.matrix");
  const format = useFormatter();
  const headingId = useId();
  const [entry, setEntry] = useState<EntryMode>(() => topicEntryMode(quotas));
  const [picking, setPicking] = useState<string | null>(null);
  const countOnly = entryRestriction === "COUNT_ONLY";
  const mode: EntryMode = countOnly ? "COUNT" : quotas.length === 0 ? entry : topicEntryMode(quotas);
  const percentMode = mode === "PERCENT";
  const view = topicQuotaView(quotas, total);
  const knowledgeIndex = useMemo(() => indexKnowledge(reference.knowledgeTree), [reference]);
  const full = quotas.length >= MAX_TOPIC_QUOTAS;
  const overLimit = quotas.length > MAX_TOPIC_QUOTAS;
  const pendingRequired = blockingMappingRows(quotas);
  const rate = (value: number | null) => (value === null ? "—" : t("percent", { value: format.number(value, { maximumFractionDigits: 1 }) }));

  const patch = (quotaId: string, change: Partial<TopicQuota>) =>
    onChange(quotas.map((quota) => (quota.id === quotaId ? { ...quota, ...change } : quota)));
  // Bảng lẫn dòng nhập theo tỷ lệ (nạp từ máy chủ) và dòng nhập theo số câu vẫn là bảng "theo số câu": sửa số câu của dòng
  // còn mang `percent` thì bỏ `percent`, nếu không `topicCountFor` đọc lại từ tỷ lệ cũ và số hiện trên màn không đổi.
  const setCount = (quotaId: string, count: number) =>
    onChange(
      quotas.map((quota) => {
        if (quota.id !== quotaId) return quota;
        const next: TopicQuota = { ...quota, count };
        delete next.percent;
        return next;
      }),
    );
  const setEntryMode = (next: EntryMode) => {
    setEntry(next);
    onChange(convertTopicEntry(quotas, next, total));
  };
  // Dòng bị xóa kéo theo nút đang giữ tiêu điểm: đưa tiêu điểm về nút "Thêm chỉ tiêu chủ đề" của bảng (R1-26).
  const rootRef = useRef<HTMLElement>(null);
  const remove = (quotaId: string) => {
    onChange(quotas.filter((item) => item.id !== quotaId));
    requestAnimationFrame(() => rootRef.current?.querySelector<HTMLElement>("[data-topic-add]")?.focus());
  };
  const add = () => {
    const base = newTopicQuota(draftId("tq"));
    onChange([...quotas, percentMode ? { ...base, percent: exactPercent(base.count, total) } : base]);
  };
  const nodesOf = (quota: TopicQuota): KnowledgeRef[] =>
    quota.nodeIds.map((nodeId) => {
      const node = knowledgeIndex.get(nodeId)?.node;
      return { nodeId, code: node?.code ?? nodeId, name: node?.name ?? "", primary: false };
    });
  const picked = quotas.find((quota) => quota.id === picking);
  const maxError = overLimit || serverError === "MAX_TOPIC_QUOTAS";
  const percentErrors = quotas.some((quota) => (quota.percent ?? 0) > 100) || serverError === "PERCENT_OUT_OF_RANGE";

  return (
    <section ref={rootRef} aria-labelledby={headingId} className="flex flex-col gap-3 rounded-md border border-border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h4 id={headingId} className="text-label font-semibold text-text">
            {t("title")}
          </h4>
          <p className="text-caption text-text-muted">
            {t("counter", { count: quotas.length, max: MAX_TOPIC_QUOTAS })}
            {total > 0 && ` · ${t("denominator", { total, level: levelName })}`}
          </p>
        </div>
        {!readOnly && (
          <div className="flex flex-wrap items-end gap-3">
            {total > 0 && !countOnly && (
              <ChoiceCardGroup
                variant="pill"
                legend={t("entry")}
                name={`${id}-topic-entry`}
                value={mode}
                onChange={setEntryMode}
                options={[
                  { value: "COUNT", label: t("entryCount") },
                  { value: "PERCENT", label: t("entryPercent") },
                ]}
              />
            )}
            <Button type="button" variant="secondary" size="sm" disabled={full} onClick={add} data-topic-add="">
              <Plus />
              <span>{t("add")}</span>
            </Button>
          </div>
        )}
      </div>
      {full && !overLimit && <p className="text-caption text-text-muted">{t("full", { max: MAX_TOPIC_QUOTAS })}</p>}

      {quotas.length === 0 ? (
        <p className="text-body text-text-muted">{t("empty")}</p>
      ) : (
        <div role="region" tabIndex={0} aria-label={t("caption", { level: levelName })} className={cn("relative overflow-x-auto rounded-md border border-border", focusRing)}>
          <table className="w-full min-w-176 border-separate border-spacing-0 text-body tabular-nums">
            <caption className="sr-only">{t("caption", { level: levelName })}</caption>
            <thead>
              <tr className="text-left text-caption text-text-muted">
                <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{t("topic")}</th>
                <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("count")}</th>
                <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("rate")}</th>
                {!hideGrade && <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("gradeLimit")}</th>}
                <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("difficultyLimit")}</th>
                <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("level")}</th>
                <th scope="col" className="sticky right-0 z-1 border-b border-l border-border bg-subtle px-3 py-2 text-right font-semibold"><span className="sr-only">{t("actions")}</span></th>
              </tr>
            </thead>
            <tbody>
              {quotas.map((quota, index) => {
                const pending = isMappingPending(quota);
                const nodes = nodesOf(quota);
                const label = quota.name || t("untitled", { position: index + 1 });
                const count = view.counts[index] ?? 0;
                return (
                  <tr key={quota.id}>
                    <th scope="row" className="border-b border-border px-3 py-2 text-left align-top font-normal">
                      <div className="flex min-w-44 flex-col gap-1.5">
                        <Input
                          value={quota.name}
                          autoComplete="off"
                          maxLength={200}
                          readOnly={readOnly}
                          placeholder={t("namePlaceholder")}
                          aria-label={t("nameAria", { position: index + 1 })}
                          onChange={(event) => patch(quota.id, { name: event.target.value })}
                        />
                        {pending ? (
                          <span className="flex flex-wrap items-center gap-2">
                            <StatusChip tone="warning">{t("pending")}</StatusChip>
                            {!readOnly && (
                              <Button type="button" variant="secondary" size="sm" aria-label={t("attachAria", { name: label })} onClick={() => setPicking(quota.id)}>
                                <ListTree />
                                <span>{t("attach")}</span>
                              </Button>
                            )}
                          </span>
                        ) : (
                          <span className="flex flex-wrap items-center gap-1.5">
                            {nodes.map((node) => (
                              <span key={node.nodeId} className="inline-flex max-w-full rounded-full border border-border bg-subtle px-2.5 py-0.5 text-caption text-text">
                                <span className="min-w-0 wrap-anywhere" data-exam-content>
                                  <span className="font-semibold">{node.code}</span> {node.name}
                                </span>
                              </span>
                            ))}
                            {!readOnly && (
                              <>
                                <Button type="button" variant="link" size="sm" aria-label={t("editMappingAria", { name: label })} onClick={() => setPicking(quota.id)}>
                                  {t("editMapping")}
                                </Button>
                                <Button type="button" variant="link" size="sm" aria-label={t("detachAria", { name: label })} onClick={() => patch(quota.id, { nodeIds: [] })}>
                                  {t("detach")}
                                </Button>
                              </>
                            )}
                          </span>
                        )}
                        {pending && quota.enforcement === "REFERENCE" && (
                          <span className="text-caption text-text-muted">{t("noReferenceData")}</span>
                        )}
                      </div>
                    </th>
                    <td className="border-b border-border px-2 py-2 align-top">
                      {percentMode ? (
                        <span className="flex items-center gap-1.5 whitespace-nowrap">
                          <span className="text-text-muted">{t("atLeast")}</span>
                          <strong className="font-semibold text-text">{t("countValue", { count })}</strong>
                        </span>
                      ) : (
                        <span className="flex items-center gap-1.5 whitespace-nowrap">
                          <span className="text-text-muted">{t("atLeast")}</span>
                          <BlockNumberInput
                            className="h-9 w-16 px-2"
                            value={quota.count}
                            readOnly={readOnly}
                            aria-label={t("countAria", { name: label })}
                            onValueChange={(value) => setCount(quota.id, value)}
                          />
                          <span className="text-text-muted">{t("countUnit")}</span>
                        </span>
                      )}
                    </td>
                    <td className="border-b border-border px-2 py-2 align-top">
                      {percentMode ? (
                        <span className="flex flex-col gap-0.5">
                          <span className="flex items-center gap-1">
                            <BlockNumberInput
                              className="h-9 w-20 px-2"
                              allowDecimal
                              value={quota.percent ?? 0}
                              readOnly={readOnly}
                              aria-label={t("percentAria", { name: label })}
                              aria-invalid={(quota.percent ?? 0) > 100 ? true : undefined}
                              onValueChange={(value) => patch(quota.id, { percent: value, count: countFromPercent(value, total) })}
                            />
                            <span aria-hidden className="text-text-muted">%</span>
                          </span>
                          <span className="text-caption text-text-muted">{t("realRate", { value: rate(view.rates[index] ?? null) })}</span>
                        </span>
                      ) : (
                        <span className="inline-block min-h-9 py-1.5 whitespace-nowrap text-text">{rate(view.rates[index] ?? null)}</span>
                      )}
                    </td>
                    {!hideGrade && (
                      <td className="border-b border-border px-2 py-2 align-top">
                        <CodeFilter
                          name={t("gradeLimit")}
                          allLabel={t("allGrades")}
                          value={quota.gradeCodes ?? []}
                          codes={axes.grades}
                          label={labels.grade}
                          disabled={readOnly}
                          onChange={(gradeCodes) => patch(quota.id, { gradeCodes })}
                        />
                      </td>
                    )}
                    <td className="border-b border-border px-2 py-2 align-top">
                      <CodeFilter
                        name={t("difficultyLimit")}
                        allLabel={t("allDifficulties")}
                        value={quota.difficultyCodes ?? []}
                        codes={axes.difficulties}
                        label={labels.difficulty}
                        disabled={readOnly}
                        onChange={(difficultyCodes) => patch(quota.id, { difficultyCodes })}
                      />
                    </td>
                    <td className="border-b border-border px-2 py-2 align-top">
                      <EnforcementControl
                        mode="TOGGLE"
                        legend={label}
                        name={`${id}-${quota.id}-enforcement`}
                        value={quota.enforcement}
                        basis={quota.basis}
                        disabled={readOnly}
                        onChange={(enforcement) => patch(quota.id, { enforcement })}
                      />
                    </td>
                    <td className="sticky right-0 z-1 border-b border-l border-border bg-surface px-3 py-1 text-right align-top">
                      {!readOnly && (
                        <IconButton
                          icon={<CircleMinus />}
                          tone="danger"
                          aria-label={t("removeAria", { name: label })}
                          onClick={() => remove(quota.id)}
                        />
                      )}
                    </td>
                  </tr>
                );
              })}
              <tr>
                <th scope="row" className="border-b border-border px-3 py-2 text-left font-medium text-text">{t("rest")}</th>
                <td className="border-b border-border px-2 py-2 text-text">{t("countValue", { count: view.rest })}</td>
                <td className="border-b border-border px-2 py-2 text-text">{rate(view.restRate)}</td>
                <td colSpan={hideGrade ? 3 : 4} className="border-b border-border px-2 py-2 text-caption text-text-muted">{t("restNote")}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr className="font-semibold text-text">
                <th scope="row" className="px-3 py-2 text-left">{t("total")}</th>
                <td className={cn("px-2 py-2", view.over > 0 && "text-danger")} colSpan={2}>
                  <div className="flex flex-wrap items-center gap-x-2">
                    <output>
                      {t("totalValue", { total: view.grandCount, count: total })} {rate(view.grandRate)}
                    </output>
                    {view.over > 0 && (
                      <span className="flex items-center gap-1">
                        <TriangleAlert aria-hidden className="size-4 shrink-0" />
                        {matrixText("over", { count: view.over })}
                      </span>
                    )}
                  </div>
                </td>
                <td colSpan={hideGrade ? 3 : 4} />
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      {view.over > 0 && (
        <p role="alert" className="flex items-start gap-1.5 text-body text-danger">
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          {t("over", { allocated: view.sumCount, count: total, over: view.over })}
        </p>
      )}
      {maxError && (
        <p role="alert" className="flex items-start gap-1.5 text-body text-danger">
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          {t("maxError", { max: MAX_TOPIC_QUOTAS, level: levelName, count: quotas.length, known: quotas.length > MAX_TOPIC_QUOTAS ? "yes" : "no" })}
        </p>
      )}
      {percentErrors && (
        <p role="alert" className="flex items-start gap-1.5 text-body text-danger">
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          {t("percentRange")}
        </p>
      )}
      {pendingRequired.length > 0 && (
        <p role="status" className="flex items-start gap-1.5 text-body text-warning">
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          {t("pendingRequired", { names: pendingRequired.map((quota) => quota.name || t("untitledShort")).join(", "), count: pendingRequired.length })}
        </p>
      )}
      {quotas.length > 0 && <p className="text-caption text-text-muted">{t("hint")}</p>}

      <KnowledgePickerDialog
        open={picking !== null}
        onOpenChange={(open) => {
          if (!open) setPicking(null);
        }}
        value={picked ? nodesOf(picked) : []}
        selectableLevels={["CHAPTER", "LESSON", "KNOWLEDGE"]}
        withPrimary={false}
        defaultSubjectCode={subjectCode}
        description={t("pickerDescription", { level: levelName })}
        onApply={(refs) => {
          if (picked) {
            const first = refs[0];
            // Dòng chưa có tên lấy tên của chủ đề đầu tiên; gắn mã không đổi số câu, giới hạn lớp và độ khó của dòng.
            patch(picked.id, { nodeIds: refs.map((ref) => ref.nodeId), ...(picked.name.trim() === "" && first ? { name: first.name } : {}) });
          }
          setPicking(null);
        }}
      />
    </section>
  );
}

/** Giới hạn lớp hoặc độ khó của một chỉ tiêu: danh sách tick trong khung mở; không tick mã nào là mọi lớp (mọi độ khó). */
function CodeFilter({
  name,
  allLabel,
  value,
  codes,
  label,
  disabled,
  onChange,
}: {
  name: string;
  allLabel: string;
  value: readonly string[];
  codes: readonly string[];
  label: (code: string) => string;
  disabled: boolean;
  onChange: (codes: string[]) => void;
}) {
  const baseId = useId();
  const summary = value.length === 0 ? allLabel : value.map(label).join(", ");
  return (
    <details className="group min-w-32">
      <summary className={cn("relative flex min-h-9 cursor-pointer list-none items-center rounded-md border border-border px-2.5 text-label text-text", "pointer-coarse:after:absolute pointer-coarse:after:-inset-y-1 pointer-coarse:after:inset-x-0", focusRing)} aria-label={`${name}: ${summary}`}>
        <span className="min-w-0 wrap-anywhere">{summary}</span>
      </summary>
      <ul className="mt-1 flex flex-col gap-0.5 rounded-md border border-border p-1">
        {codes.map((code) => {
          const checkboxId = `${baseId}-${code}`;
          return (
            <li key={code} className="flex min-h-9 items-center gap-2.5 rounded-sm px-2 hover:bg-subtle">
              <Checkbox
                id={checkboxId}
                checked={value.includes(code)}
                disabled={disabled}
                onCheckedChange={(checked) => onChange(checked === true ? [...value, code] : value.filter((item) => item !== code))}
              />
              <label htmlFor={checkboxId} className="relative flex-1 cursor-pointer py-1.5 text-body text-text pointer-coarse:after:absolute pointer-coarse:after:-inset-y-1.5 pointer-coarse:after:inset-x-0">
                {label(code)}
              </label>
            </li>
          );
        })}
      </ul>
    </details>
  );
}
