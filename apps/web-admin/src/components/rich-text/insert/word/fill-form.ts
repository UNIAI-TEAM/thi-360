/**
 * "Đưa vào biểu mẫu" (spec 4.10): từ kết quả của hộp Dán từ Word tính ra các ô của biểu mẫu sẽ được ghi và ô nào đang có
 * nội dung (cần hỏi ghi đè). Thuần dữ liệu: nơi gọi (`question-editor-form.tsx`) ghi vào react-hook-form.
 */
import type { WordPasteResult } from "./word-paste-dialog";
import type { OptionLabel } from "./types";

export type FillTarget = { field: "content" } | { field: "option"; index: number; label: OptionLabel } | { field: "solution" };

export interface FillWrite {
  target: FillTarget;
  /** Đường dẫn trường của biểu mẫu (`content`, `body.options.0.content`, `solution`). */
  path: string;
  html: string;
}

export interface FillOptions {
  /** Dạng câu có phương án chọn (Trắc nghiệm 1 đáp án, Nhiều đáp án): chỉ khi đó phương án A–D vào ô phương án. */
  choice: boolean;
  /** Dạng câu có ô Lời giải ở biểu mẫu. */
  hasSolution: boolean;
}

const CONTENT_ELEMENTS = "img, span.math-inline, span.math-block, span.blank, figure, table, blockquote, ul, ol, hr";

/** HTML của một ô có coi là rỗng không: không chữ, không công thức/ảnh/bảng/danh sách (kể cả `<p></p>` của trình soạn). */
export function isEmptyContent(html: string | null | undefined): boolean {
  if (!html) return true;
  const body = new DOMParser().parseFromString(html, "text/html").body;
  return (body.textContent ?? "").trim() === "" && body.querySelector(CONTENT_ELEMENTS) === null;
}

/**
 * Các ô được ghi. Khi tách theo nhãn: đề bài → `content`, phương án A–D → ô phương án cùng thứ tự (chỉ dạng có phương án),
 * lời giải → `solution`; ô nào không có nội dung dán thì không ghi (không xóa nội dung cũ). Không tách (hoặc dạng khác):
 * toàn bộ vào `content`, ô đang dán.
 */
export function planWordFill({ fields, split }: Pick<WordPasteResult, "fields" | "split">, { choice, hasSolution }: FillOptions): FillWrite[] {
  const writes: FillWrite[] = [];
  if (!isEmptyContent(fields.stem)) writes.push({ target: { field: "content" }, path: "content", html: fields.stem });
  if (!split) return writes;

  if (choice) {
    fields.options.forEach((option, index) => {
      if (isEmptyContent(option.html)) return;
      writes.push({ target: { field: "option", index, label: option.label }, path: `body.options.${index}.content`, html: option.html });
    });
  }
  if (hasSolution && !isEmptyContent(fields.solution)) writes.push({ target: { field: "solution" }, path: "solution", html: fields.solution });
  return writes;
}
