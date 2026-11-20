"use client";

import { Badge, Button, Card } from "@thi360/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Lock } from "lucide-react";
import { useTranslations } from "next-intl";
import { Fragment, useEffect, useEffectEvent, useRef, useState } from "react";
import type { QuestionType } from "@/features/content/types";
import { topicCountFor, totalChangePending } from "../allocation";
import { AudioChip, sectionAudio } from "../components/block-row-metrics";
import { ScrollableTable } from "../components/scrollable-table";
import { TotalChangeNotice } from "../components/subject-allocation-extras";
import { EXAMS_QUERY_KEYS } from "../keys";
import { apportionByPercent, computeAllocation, isFlexibleSet, isSetBlock } from "../matrix";
import { sectionCaseCounts, subjectAllocation } from "../subject-matrix";
import type { ExamDetail, ExamTemplate, QuestionBlock } from "../types";
import type { SubjectFormRendererProps } from "./form-renderers";
import { ROW_TARGET_EVENT } from "./navigation";
import { LockReason } from "./lock-reason";
import { ReferenceBlock } from "./reference-block";
import { RowEditor } from "./row-editor";
import { applyNewTotal, conditionSummary, gradeVisible, keepOldTotal, lockReasonsOf, readRow, rowAnswerType, rowFigures, rowLocks, rowsLockedOf, writeRow, type RowContext } from "./row-list-model";
import { replaceWithSplit, splitBlock, splitState } from "./row-split";
import { subjectFormFor } from "./registry";
import { SubjectFormHeader } from "./subject-form-header";

/**
 * Danh sách dòng lấy câu (DS, FTM R-7, lát CR-3a): đầu môn, bảng dòng một mục theo thứ tự của mẫu (tiêu đề phân đoạn khi có
 * `displaySegments`, chip audio, chỉ số chỉ đọc, tóm tắt điều kiện), màn sửa dòng gọn và khối Phân bổ đối chiếu gấp sẵn. Không có nút
 * thêm, xóa, lên, xuống dòng (R-13). Số lượng của dòng nhập theo số câu hoặc tỷ lệ ở kỳ cho nhập % (R-9), tách dòng ở cặp đăng ký (R-7);
 * Phân bổ đối chiếu có Số câu / Tỷ lệ, công tắc áp dụng và bảng kết hợp của V-SAT (CR-3b). Mỗi dòng gắn `data-form-row="<blockId>"`; điều
 * hướng (`?block=`, bấm lỗi, bấm tóm tắt) báo dòng đích bằng `ROW_TARGET_EVENT` và màn mở đúng màn sửa dòng.
 */
export function RowListForm({
  context,
  subjectName,
  axes,
  labels,
  reference,
  onChange,
  headerAction,
  examId,
  templateLocked = false,
  topicError = null,
  examKind = null,
  examQuotas = [],
  onExamQuotas,
  confirmedCount = null,
  onConfirmCount,
  rawInputs,
}: SubjectFormRendererProps) {
  const t = useTranslations("exams.subjectForm.rows");
  const tSubject = useTranslations("exams.structure.subject");
  const tContent = useTranslations("content");
  const tForm = useTranslations("exams.structure.blockForm");
  const tBlock = useTranslations("exams.structure.block");
  const queryClient = useQueryClient();
  const rootRef = useRef<HTMLDivElement>(null);
  const closeFocusRafRef = useRef<number | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const [focusNonce, setFocusNonce] = useState(0);
  const [focusField, setFocusField] = useState<string | null>(null);
  const { section, part, option } = context;
  const kindCode = examKind ?? "";
  const entry = subjectFormFor(kindCode, section.subjectCode);

  const cancelCloseFocus = () => {
    if (closeFocusRafRef.current !== null) {
      cancelAnimationFrame(closeFocusRafRef.current);
      closeFocusRafRef.current = null;
    }
  };

  // Bài và mẫu đã nằm trong bộ nhớ đệm của khung (view tải trước khi vẽ môn): đọc thẳng, không đăng ký truy vấn nên mở môn không thêm request.
  const exam = queryClient.getQueryData<ExamDetail>(EXAMS_QUERY_KEYS.detail(examId));
  const template = queryClient.getQueryData<ExamTemplate[]>(EXAMS_QUERY_KEYS.templates())?.find((item) => item.code === exam?.templateCode) ?? null;
  const rowContext: RowContext = { scope: exam?.scope ?? "FULL", reviewMode: exam?.buildMode === "REVIEW", template };

  const openRow = useEffectEvent((blockId: string, field: string | null) => {
    if (!readRow(section, blockId)) return false;
    cancelCloseFocus();
    setOpenId(blockId);
    setFocusField(field);
    setFocusNonce((nonce) => nonce + 1);
    return true;
  });
  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    const onTarget = (event: Event) => {
      const { blockId, field } = (event as CustomEvent<{ blockId: string; field: string | null }>).detail;
      if (openRow(blockId, field)) event.preventDefault();
    };
    root.addEventListener(ROW_TARGET_EVENT, onTarget);
    return () => {
      root.removeEventListener(ROW_TARGET_EVENT, onTarget);
      if (closeFocusRafRef.current !== null) {
        cancelAnimationFrame(closeFocusRafRef.current);
        closeFocusRafRef.current = null;
      }
    };
  }, []);

  if (!entry) return null;

  const cases = sectionCaseCounts(section, option);
  const count = cases[0] ?? null;
  const allocation = subjectAllocation(section, axes, count);
  const audio = sectionAudio(section);
  const readOnlyNotice = entry.readOnlyNotice !== undefined;
  const rowsLocked = rowsLockedOf(rowContext);
  const typeLabel = (type: QuestionType) => tContent(`questionType.${type}`);
  const tagName = (code: string) => reference.tags.find((tag) => tag.code === code)?.name ?? code;
  const segments = part.displaySegments ?? [];
  const editing = openId === null ? null : readRow(section, openId);
  const editingIndex = editing ? section.blocks.findIndex((block) => block.id === editing.id) : -1;

  const openFromRow = (blockId: string) => {
    cancelCloseFocus();
    setOpenId(blockId);
    setFocusField(null);
  };

  const closeEditor = () => {
    const id = openId;
    cancelCloseFocus();
    setOpenId(null);
    setFocusField(null);
    if (id) {
      closeFocusRafRef.current = requestAnimationFrame(() => {
        closeFocusRafRef.current = null;
        rootRef.current?.querySelector<HTMLElement>(`[data-form-row="${CSS.escape(id)}"] button`)?.focus();
      });
    }
  };

  const kindText = (block: QuestionBlock) => {
    const type = rowAnswerType(block);
    const kind = isSetBlock(block) ? "set" : "single";
    return type ? t(`table.kind.${kind}Of`, { type: typeLabel(type) }) : t(`table.kind.${kind}`);
  };
  const quantityText = (block: QuestionBlock) => {
    const questions = block.questionCount ?? 0;
    if (!isSetBlock(block)) return t("table.quantity.single", { count: questions });
    if (isFlexibleSet(block)) return t("table.quantity.flexible", { count: questions });
    return t("table.quantity.sets", { sets: block.setCount ?? 0, size: block.setSize ?? 0, count: questions });
  };
  const figuresText = (block: QuestionBlock) => {
    const figures = rowFigures(block);
    if (figures.trueFalse) return t("table.figures.trueFalse", { count: figures.questions, statements: figures.statements ?? 0, hasStatements: figures.statements === null ? "no" : "yes", points: figures.points ?? 0, hasPoints: figures.points === null ? "no" : "yes" });
    return figures.points === null ? null : t("table.figures.plainPoints", { count: figures.questions, points: figures.points });
  };
  const conditionsText = (block: QuestionBlock) => {
    const summary = conditionSummary(block);
    return summary.length === 0 ? t("table.conditions.none") : summary.map((item) => t(`table.conditions.${item.key}`, { count: item.count ?? 0 })).join(" · ");
  };
  const segmentOf = (block: QuestionBlock) => {
    const from = block.positionRange?.from;
    return from === undefined ? null : (segments.find((segment) => from >= segment.fromPosition && from <= segment.toPosition) ?? null);
  };

  // Đổi tổng (R-9, R-122 dòng 6): chỗ nhập theo % giữ % và tính lại số, chỗ nhập theo số giữ số; hộp xác nhận chỉ hiện khi có bảng nhập theo %.
  const sectionCount = section.questionCount;
  const totalChanged = totalChangePending(section, confirmedCount);
  const noticeLines: string[] = [];
  if (totalChanged && sectionCount !== null) {
    if (section.matrix?.unit === "PERCENT") {
      const next = computeAllocation({ questionCount: sectionCount, matrix: section.matrix }, axes);
      if (gradeVisible(entry)) noticeLines.push(tSubject("totalChange.grades", { values: axes.grades.map((code, index) => `${labels.grade(code)} ${next.byGrade[index] ?? 0}`).join(" · ") }));
      noticeLines.push(tSubject("totalChange.difficulties", { values: axes.difficulties.map((code, index) => `${labels.difficulty(code)} ${next.byDifficulty[index] ?? 0}`).join(" · ") }));
    }
    if (section.answerQuotas?.unit === "PERCENT") {
      const resolved = apportionByPercent(section.answerQuotas.items.map((item) => item.value), sectionCount).results;
      noticeLines.push(tSubject("totalChange.answerTypes", { values: section.answerQuotas.items.map((item, index) => `${typeLabel(item.answerType)} ${resolved[index] ?? 0}`).join(" · ") }));
    }
    const topics = section.topicQuotas ?? [];
    if (topics.some((quota) => quota.percent !== undefined && quota.percent !== null)) {
      noticeLines.push(tSubject("totalChange.topics", { values: topics.map((quota) => `${quota.name || tSubject("totalChange.untitled")} ${topicCountFor(quota, sectionCount)}`).join(" · ") }));
    }
  }
  const columns = readOnlyNotice ? 4 : 5;

  return (
    <div ref={rootRef} className="flex flex-col gap-4" data-subject-form="ROWS">
      <SubjectFormHeader
        subjectName={subjectName}
        choiceGroupLabel={section.choiceGroup ? tSubject(`choiceGroup.${section.choiceGroup}`) : null}
        count={count}
        allocated={allocation.allocated}
        examId={examId}
        templateLocked={templateLocked}
        action={headerAction}
      />

      {totalChanged && sectionCount !== null && confirmedCount !== null && (
        <TotalChangeNotice
          oldTotal={confirmedCount}
          newTotal={sectionCount}
          lines={noticeLines}
          onApply={() => {
            onChange((current) => applyNewTotal(current, sectionCount));
            onConfirmCount?.(sectionCount);
          }}
          onKeep={() => {
            onChange((current) => keepOldTotal(current, confirmedCount, axes));
            onConfirmCount?.(sectionCount);
          }}
        />
      )}

      <Card padding="md" className="flex flex-col gap-3 sm:p-6" data-rows-card>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-title font-semibold text-text">{t("table.title")}</h3>
          {audio && <AudioChip audio={audio} />}
        </div>
        {readOnlyNotice && <p className="rounded-md border border-border bg-subtle px-3 py-2 text-body text-text-secondary" data-read-only-notice>{t("notice.ESSAY_SKIPPED")}</p>}
        {rowsLocked && <LockReason code="rowCounts" />}
        {section.blocks.length === 0 ? (
          <p className="text-body text-text-muted">{t("table.empty")}</p>
        ) : (
          <ScrollableTable label={t("table.caption", { subject: subjectName })} className="rounded-md border border-border">
            <table className="w-full table-auto border-separate border-spacing-0 text-body">
              <caption className="sr-only">{t("table.caption", { subject: subjectName })}</caption>
              <thead>
                <tr className="text-left text-caption text-text-muted">
                  <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold whitespace-nowrap">
                    {t("table.columns.order")}
                  </th>
                  <th scope="col" className="min-w-44 border-b border-border bg-subtle px-2 py-2 font-semibold">
                    {t("table.columns.name")}
                  </th>
                  <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold whitespace-nowrap">
                    {t("table.columns.quantity")}
                  </th>
                  <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold whitespace-nowrap">
                    {t("table.columns.conditions")}
                  </th>
                  {!readOnlyNotice && (
                    <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold whitespace-nowrap">
                      <span className="sr-only">{t("table.columns.actions")}</span>
                    </th>
                  )}
                </tr>
              </thead>
              <tbody>
                {section.blocks.map((block, index) => {
                  const segment = segmentOf(block);
                  const previous = index > 0 ? segmentOf(section.blocks[index - 1]!) : null;
                  const showSegment = segment !== null && segment.code !== previous?.code;
                  const locks = rowLocks(block, rowContext);
                  const rowReasons = lockReasonsOf({ ...locks, counts: false });
                  const slots = (block.slots ?? []).filter((slot) => (slot.tagCodes?.length ?? 0) > 0 || (slot.knowledgeScope?.nodeIds.length ?? 0) > 0);
                  const name = block.name || t("table.untitled");
                  const figures = figuresText(block);
                  return (
                    <Fragment key={block.id}>
                      {showSegment && (
                        <tr data-form-segment={segment.code}>
                          <th scope="colgroup" colSpan={columns} className="border-b border-border bg-subtle/60 px-2 py-1.5 text-left text-label font-semibold text-text">
                            {segment.name}
                          </th>
                        </tr>
                      )}
                      <tr data-form-row={block.id} className={openId === block.id ? "bg-primary-50" : undefined}>
                        <td className="border-b border-border px-2 py-2 align-top text-caption whitespace-nowrap tabular-nums text-text-secondary">
                          {block.positionRange ? t("table.range", { from: block.positionRange.from, to: block.positionRange.to, same: block.positionRange.from === block.positionRange.to ? "yes" : "no" }) : index + 1}
                        </td>
                        <th scope="row" className="min-w-44 border-b border-border px-2 py-2 align-top text-left font-medium text-text">
                          <span className="flex flex-wrap items-center gap-1.5">
                            <span data-exam-content>{name}</span>
                            {block.questionType === "ESSAY" && <Badge className="font-medium">{tBlock("manualGrading")}</Badge>}
                            {block.omitted === true && <Badge className="font-medium">{tForm("omittedBadge")}</Badge>}
                            {audio && <AudioChip audio={audio} />}
                          </span>
                          <div className="mt-0.5 text-caption font-normal text-text-muted">{kindText(block)}</div>
                          {rowReasons.map((code) => (
                            <LockReason key={code} code={code} className="mt-1 flex" />
                          ))}
                        </th>
                        <td className="border-b border-border px-2 py-2 align-top tabular-nums text-text">
                          <span className="inline-flex flex-wrap items-center gap-1.5">
                            <span>{quantityText(block)}</span>
                            {locks.counts && (
                              <>
                                <Lock aria-hidden className="size-3.5 shrink-0 text-text-muted" />
                                <span className="sr-only">{t("table.countsLocked")}</span>
                              </>
                            )}
                          </span>
                          {figures && (
                            <div data-row-figures className="mt-0.5 text-caption text-text-muted">
                              {figures}
                            </div>
                          )}
                        </td>
                        <td className="border-b border-border px-2 py-2 align-top text-caption text-text-secondary">{conditionsText(block)}</td>
                        {!readOnlyNotice && (
                          <td className="border-b border-border px-2 py-1.5 align-top text-right whitespace-nowrap">
                            <Button type="button" variant="secondary" size="sm" aria-label={t("table.editAria", { name })} aria-expanded={openId === block.id} onClick={() => openFromRow(block.id)}>
                              {t("table.edit")}
                            </Button>
                          </td>
                        )}
                      </tr>
                      {slots.length > 0 && (
                        <tr data-form-slots={block.id}>
                          <td className="border-b border-border" />
                          <td colSpan={columns - 1} className="border-b border-border px-2 py-2">
                            <ul className="flex flex-wrap gap-x-4 gap-y-1 text-caption text-text-secondary" aria-label={t("table.slotsLabel", { name })}>
                              {slots.map((slot) => (
                                <li key={slot.index} data-form-slot={slot.index} className="min-w-0 wrap-break-word">
                                  {t("table.slot", {
                                    index: slot.index,
                                    tags: (slot.tagCodes ?? []).map(tagName).join(", "),
                                    hasTags: (slot.tagCodes?.length ?? 0) > 0 ? "yes" : "no",
                                    nodes: slot.knowledgeScope?.nodeIds.length ?? 0,
                                  })}
                                </li>
                              ))}
                            </ul>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </ScrollableTable>
        )}
      </Card>

      {editing && !readOnlyNotice && (
        <RowEditor
          key={editing.id}
          block={editing}
          section={section}
          kindCode={kindCode}
          entry={entry}
          position={editingIndex + 1}
          total={section.blocks.length}
          context={rowContext}
          template={template}
          axes={axes}
          labels={labels}
          reference={reference}
          topicError={topicError}
          onChange={(patch) => onChange((current) => writeRow(current, editing.id, patch))}
          onClose={closeEditor}
          focusNonce={focusNonce}
          focusField={focusField}
          sectionCount={count}
          rawInputs={rawInputs}
          split={splitState(editing, section, entry, rowContext)}
          onSplit={(parts) =>
            onChange((current) => {
              const parent = readRow(current, editing.id);
              return parent ? replaceWithSplit(current, parent.id, splitBlock(parent, parts)) : current;
            })
          }
        />
      )}

      <ReferenceBlock
        section={section}
        entry={entry}
        axes={axes}
        labels={labels}
        subjectName={subjectName}
        count={count}
        reference={reference}
        examQuotas={examQuotas}
        onExamQuotas={onExamQuotas}
        topicError={topicError}
        onChange={onChange}
      />
    </div>
  );
}
