import type { ReactElement } from "react";
import type { QuestionType } from "@/features/content/types";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { getQuestion } from "../api";
import { createQuestion } from "../editor-api";
import { emptyEditorValues, toEditorValues, toSaveRequest, type QuestionEditorValues } from "../editor-form";
import { questionStore } from "../mock/store";

/** Dựng chung cho test của từng trình soạn dạng câu (`question-editor-<dạng>.test.tsx`). */

export function renderEn(ui: ReactElement) {
  return renderWithProviders(ui, { locale: "en", messages: en });
}

/**
 * Tạo sẵn một câu Nháp hợp lệ về phân loại (môn, khối, mức độ, kiến thức lấy từ câu mẫu q-0001) để mở ở chế độ sửa;
 * `fill` điền phần riêng của dạng câu. Trả về id câu.
 */
export async function seedQuestion(type: QuestionType, fill: (values: QuestionEditorValues) => void): Promise<string> {
  const base = toEditorValues(await getQuestion("q-0001"));
  const values = emptyEditorValues(type);
  Object.assign(values, {
    subjectCode: base.subjectCode,
    gradeCode: base.gradeCode,
    difficultyCode: base.difficultyCode,
    knowledge: base.knowledge,
    content: "De bai",
  });
  fill(values);
  return (await createQuestion(toSaveRequest(values))).id;
}

/** Câu đang lưu trong kho dữ liệu mẫu sau khi bấm Lưu. */
export function storedQuestion(id: string) {
  return questionStore.require(id);
}
