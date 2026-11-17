import { act, fireEvent, waitFor } from "@testing-library/react";
import { expect, vi } from "vitest";
import type { Editor } from "@tiptap/react";
import type { ReactElement } from "react";
import type { QuestionType } from "@/features/content/types";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { getQuestion } from "../api";
import { createQuestion } from "../editor-api";
import { emptyEditorValues, toEditorValues, toSaveRequest, type QuestionEditorValues } from "../editor-form";
import { apiReferenceFromFixture, stubContentReference, withQuestionsServer } from "./api-fixtures";

/** Kho đọc/ghi vào "máy chủ" câu hỏi giả của file test (nạp lại bộ câu mẫu trước mỗi test). */
export const fakeQuestions = withQuestionsServer();

// Mỗi ô nội dung HTML là một trình soạn TipTap; biểu mẫu nhiều ô (phương án, cặp ghép, ý đúng/sai) dựng chậm trong jsdom.
vi.setConfig({ testTimeout: 20_000 });
// jsdom không có `elementFromPoint` mà ProseMirror gọi khi focus/cuộn tới con trỏ.
if (typeof document.elementFromPoint !== "function") document.elementFromPoint = () => null;

/** Dựng chung cho test của từng trình soạn dạng câu (`question-editor-<dạng>.test.tsx`). */

/** Giao diện tiếng Anh: máy chủ trả tên danh mục tiếng Anh (như `Accept-Language: en` qua proxy). */
export function renderEn(ui: ReactElement, reference = apiReferenceFromFixture("en")) {
  stubContentReference(reference);
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
  return fakeQuestions.require(id);
}

/**
 * Tay cầm tới trình soạn HTML (TipTap) sau một phần tử `textbox`: jsdom không gõ được vào ProseMirror như trình duyệt,
 * nên test đọc chữ thuần/HTML và đặt nội dung qua chính editor (đi qua `onUpdate` như khi gõ).
 */
export function rich(element: HTMLElement) {
  const editor = (element as HTMLElement & { editor?: Editor }).editor;
  if (!editor) throw new Error("This element is not an HTML editor");
  return {
    get text() {
      return editor.getText();
    },
    get html() {
      return editor.getHTML();
    },
    set(html: string) {
      act(() => {
        editor.commands.setContent(html, { emitUpdate: true });
      });
    },
    /**
     * Đặt con trỏ ngay sau đoạn chữ `fragment` (như người dùng bấm vào đó), kèm focus của vùng soạn.
     * `chain().focus()` của TipTap gọi `view.focus()` trong requestAnimationFrame nên sự kiện focus (mà `RichTextEditor`
     * dùng để biết "đã có con trỏ người dùng") đến muộn; chờ nó, nếu không nút bấm ngay sau đó rơi vào nhánh "chưa từng focus".
     */
    async placeCaretAfter(fragment: string) {
      let position = -1;
      editor.state.doc.descendants((node, offset) => {
        const index = position === -1 && node.isText ? (node.text ?? "").indexOf(fragment) : -1;
        if (index !== -1) position = offset + index + fragment.length;
      });
      if (position === -1) throw new Error(`Text not found in the editor: ${fragment}`);
      act(() => {
        editor.chain().focus().setTextSelection(position).run();
      });
      await waitFor(() => expect(editor.view.hasFocus()).toBe(true));
    },
  };
}

/**
 * Dán một tệp ảnh thuần (không có `text/html`) vào vùng soạn: đường dán/thả ảnh của trình soạn tải ảnh lên thẳng, không
 * qua hộp Hình (hộp chỉ mở từ nút Hình của thanh công cụ).
 */
export function pasteImageFile(target: HTMLElement, file: File) {
  fireEvent.paste(target, {
    clipboardData: {
      getData: () => "",
      types: ["Files"],
      files: [file],
      items: [{ kind: "file", type: file.type, getAsFile: () => file }],
    },
  });
}
