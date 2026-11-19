"use client";

import { useTranslations } from "next-intl";
import { useMemo, useSyncExternalStore, type ComponentType } from "react";
import { sectionCaseCounts } from "../subject-matrix";
import type { SubjectFormRendererProps } from "./form-renderers";
import type { MatrixBlockProps } from "./matrix-block-props";
import { MatrixDistributionBlock } from "./matrix-distribution-block";
import { structureOf } from "./matrix-form-model";
import { MatrixScopeBlock } from "./matrix-scope-block";
import { MatrixStructureBlock } from "./matrix-structure-block";
import { subjectFormFor } from "./registry";
import { SubjectFormHeader } from "./subject-form-header";

/**
 * Chỗ gắn phần riêng theo kỳ của MT (CR-2H, HSA). Màn vẽ phần này sau Cấu trúc câu, trước khối lớp và độ khó, với cùng dữ liệu của các khối
 * khác. Tra theo mã kỳ; kỳ không có phần riêng (QDA, BCA, V-ACT) không vẽ gì, và HSA chưa vẽ gì cho tới khi CR-2H đăng ký phần của mình.
 * Phần gắn vào không đổi hành vi của QDA, BCA, V-ACT: nó chỉ được vẽ ở kỳ của mình.
 *
 * Hợp đồng của phần gắn: ghi vào bản nháp qua `onChange` của môn; đặt `data-form-field` / `data-form-block` lên ô của mình để `navigation.ts`
 * đưa focus tới; không tự thêm hay xóa nhóm (R-3); nhập thô sai ghi vào kho nhập thô qua `raw`.
 */
export type MatrixKindSlot = ComponentType<MatrixBlockProps>;

const kindSlots: Record<string, MatrixKindSlot> = {};

/** Đăng ký phần riêng của một kỳ (CR-2H); trả hàm gỡ dùng khi kiểm thử. */
export function registerMatrixKindSlot(kindCode: string, slot: MatrixKindSlot): () => void {
  const previous = kindSlots[kindCode];
  kindSlots[kindCode] = slot;
  return () => {
    if (previous) kindSlots[kindCode] = previous;
    else delete kindSlots[kindCode];
  };
}

/**
 * Biểu mẫu Ma trận môn (FTM R-3, R-4, R-6, R-8, R-9, R-15) của QDA, BCA, V-ACT: đầu môn, Cấu trúc câu (kèm Nguồn chùm), khối lớp và độ khó,
 * phạm vi và chỉ tiêu chủ đề. Không có bảng dạng trả lời, danh sách nhóm, Khai báo/Bỏ, kết hợp, đơn vị đếm (R-13). Mọi thay đổi đi qua
 * `matrix-form-model.ts`; lỗi nhập thô nằm ở kho nhập thô của view nên môn đóng hay chuyển môn vẫn chặn lưu.
 */
export function MatrixForm(props: SubjectFormRendererProps) {
  const { context, subjectName, axes, labels, reference, onChange, headerAction, examId, templateLocked = false, topicError = null, examKind, rawInputs } = props;
  const { section, option } = context;
  const t = useTranslations("exams.structure.subject");
  const rawEntries = useSyncExternalStore(rawInputs.subscribe, rawInputs.getSnapshot, rawInputs.getSnapshot);
  const raw = useMemo<MatrixBlockProps["raw"]>(
    () => ({
      get: (field) => {
        const entry = rawEntries.find((item) => item.sectionId === section.id && item.blockId === null && item.field === field);
        return entry ? { text: entry.text, code: entry.code } : null;
      },
      set: (field, text, code) => rawInputs.set({ sectionId: section.id, blockId: null, field, text, code }),
      clear: (field) => rawInputs.clear(section.id, null, field),
    }),
    [rawEntries, rawInputs, section.id],
  );

  const kindCode = examKind ?? "";
  const entry = subjectFormFor(kindCode, section.subjectCode);
  if (!entry) return null;
  const cases = sectionCaseCounts(section, option);
  const total = section.questionCount ?? cases[0] ?? null;
  const blockProps: MatrixBlockProps = { section, entry, kindCode, subjectName, total, axes, labels, reference, onChange, raw };
  const KindSlot = kindSlots[kindCode];

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
      <div data-form-kind="MATRIX" className="flex flex-col gap-6">
        <MatrixStructureBlock {...blockProps} />
        {KindSlot && <KindSlot {...blockProps} />}
        <MatrixDistributionBlock {...blockProps} />
        {/* QDA, BCA, V-ACT dùng Phạm vi và Chỉ tiêu chủ đề (R-6); HSA dùng Quy tắc MAPID của HSA-D (R-5) qua chỗ gắn. */}
        {entry.mapidFlow === "SCOPE_AND_QUOTA" && <MatrixScopeBlock {...blockProps} topicError={topicError} />}
      </div>
    </SubjectFormHeader>
  );
}
