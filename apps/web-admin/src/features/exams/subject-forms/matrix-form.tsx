"use client";

import { useTranslations } from "next-intl";
import { useMemo, useSyncExternalStore, type ComponentType } from "react";
import { recomputeMapidRules } from "../mapid-rules";
import { sectionCaseCounts } from "../subject-matrix";
import type { SubjectFormRendererProps } from "./form-renderers";
import type { MatrixBlockProps, MatrixKindSlotOptionsResolver } from "./matrix-block-props";
import { MatrixDistributionBlock } from "./matrix-distribution-block";
import { HsaRoleScopeFooter, hsaMatrixSlotOptions, MatrixFormHsa } from "./matrix-form-hsa";
import { resolveMatrixDefaultSnapshot, structureOf } from "./matrix-form-model";
import { MatrixScopeBlock } from "./matrix-scope-block";
import { MatrixStructureBlock } from "./matrix-structure-block";
import { subjectFormFor } from "./registry";
import { SubjectFormHeader } from "./subject-form-header";

type MatrixKindSlot = ComponentType<MatrixBlockProps>;

const kindSlots: Record<string, { slot: MatrixKindSlot; options: MatrixKindSlotOptionsResolver }> = {
  HSA: { slot: MatrixFormHsa, options: hsaMatrixSlotOptions },
};

export function registerMatrixKindSlot(kindCode: string, slot: MatrixKindSlot, options: MatrixKindSlotOptionsResolver = {}): () => void {
  const previous = kindSlots[kindCode];
  kindSlots[kindCode] = { slot, options };
  return () => {
    if (previous) kindSlots[kindCode] = previous;
    else delete kindSlots[kindCode];
  };
}

export function MatrixForm(props: SubjectFormRendererProps) {
  const {
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
    examKind,
    rawInputs,
    template = null,
    templates,
    baselineParts,
  } = props;
  const { section, option } = context;
  const t = useTranslations("exams.structure.subject");
  const rawEntries = useSyncExternalStore(rawInputs.subscribe, rawInputs.getSnapshot, rawInputs.getSnapshot);
  const raw = useMemo<MatrixBlockProps["raw"]>(
    () => ({
      get: (field) => {
        const entry = rawEntries.find((item) => item.sectionId === section.id && item.blockId === null && item.field === field);
        return entry ? { text: entry.text, code: entry.code } : null;
      },
      hasPrefix: (prefix) => rawEntries.some((item) => item.sectionId === section.id && item.blockId === null && item.field.startsWith(prefix)),
      set: (field, text, code) => rawInputs.set({ sectionId: section.id, blockId: null, field, text, code }),
      clear: (field) => rawInputs.clear(section.id, null, field),
      clearPrefix: (prefix) => {
        for (const item of rawEntries) {
          if (item.sectionId === section.id && item.blockId === null && item.field.startsWith(prefix)) {
            rawInputs.clear(section.id, null, item.field);
          }
        }
      },
    }),
    [rawEntries, rawInputs, section.id],
  );

  const kindCode = examKind ?? "";
  const entry = subjectFormFor(kindCode, section.subjectCode);
  const defaultSnapshot = useMemo(
    () => resolveMatrixDefaultSnapshot(section, kindCode, template, templates, baselineParts),
    [section, kindCode, template, templates, baselineParts],
  );
  if (!entry) return null;
  const cases = sectionCaseCounts(section, option);
  const total = section.questionCount ?? cases[0] ?? null;
  // Khi sửa Cấu trúc câu hay bất kỳ phần nào của HSA MT, tính lại các quy tắc MAPID theo % (R-5, AC-8) nhưng không đụng lúc mới mở.
  // Với môn MT ẩn khối lớp (HSA/QDA Sử, Địa), xóa `byGrade` cũ khi người dùng chỉnh sửa để không phát sinh lỗi ngầm lệch tổng khối lớp.
  const handleChange: MatrixBlockProps["onChange"] = (update) =>
    onChange((current) => {
      let next = update(current);
      if (entry.mapidFlow === "HSA_RULES") next = recomputeMapidRules(next);
      if (entry.hideGrade && next.matrix && next.matrix.byGrade.length > 0) {
        next = { ...next, matrix: { ...next.matrix, byGrade: [] } };
      }
      return next;
    });
  const blockProps: MatrixBlockProps = {
    section,
    entry,
    kindCode,
    subjectName,
    total,
    axes,
    labels,
    reference,
    onChange: handleChange,
    templateLocked,
    raw,
    topicError,
    defaultSnapshot,
  };
  const kindSlot = kindSlots[kindCode];
  const slotOptions = kindSlot ? (typeof kindSlot.options === "function" ? kindSlot.options(section) : kindSlot.options) : {};
  const beforeStructure = slotOptions.placement === "before-structure";

  return (
    <SubjectFormHeader
      subjectName={subjectName}
      choiceGroupLabel={section.choiceGroup ? t(`choiceGroup.${section.choiceGroup}`) : null}
      count={total}
      allocated={structureOf(section).total}
      examId={examId}
      templateLocked={templateLocked}
      action={headerAction}
    >
      <div data-form-kind="MATRIX" className="flex flex-col gap-5">
        {beforeStructure && kindSlot && <kindSlot.slot {...blockProps} />}
        <MatrixStructureBlock
          {...blockProps}
          compact={slotOptions.structureVariant === "compact"}
          renderRoleFooter={entry.mapidFlow === "HSA_RULES" ? (block) => <HsaRoleScopeFooter section={section} block={block} reference={reference} /> : undefined}
        />
        {!beforeStructure && kindSlot && <kindSlot.slot {...blockProps} />}
        <MatrixDistributionBlock {...blockProps} />
        {/* QDA, BCA, V-ACT dùng Phạm vi và Chỉ tiêu chủ đề (R-6); HSA dùng Quy tắc MAPID của HSA-D (R-5) qua chỗ gắn. */}
        {entry.mapidFlow === "SCOPE_AND_QUOTA" && <MatrixScopeBlock {...blockProps} topicError={topicError} />}
      </div>
    </SubjectFormHeader>
  );
}
