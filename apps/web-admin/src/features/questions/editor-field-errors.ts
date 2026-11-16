import type { FieldError } from "@thi360/api-client";
import type { UseFormSetError } from "react-hook-form";
import type { QuestionEditorValues } from "./editor-form";

/**
 * Lỗi 422 khi lưu câu hỏi (spec 4.3, 4.5): `fieldErrors[]` có `field` là đường dẫn trong yêu cầu
 * (`body.options[0].content`, `body.children[1].body.blanks[0].answers`, `content`) và `code` là mã lỗi. Đường dẫn đổi
 * sang tên trường của biểu mẫu rồi đặt lỗi lên đúng ô; chữ lấy từ `questions.editor.errors.server.<mã>`.
 */

/** Mã lỗi lưu có chữ riêng; mã khác dùng `UNKNOWN`. */
export const SERVER_FIELD_ERROR_CODES = [
  "QUESTION_CONTENT_UNSAFE",
  "QUESTION_BODY_INVALID",
  "QUESTION_BODY_TYPE_MISMATCH",
  "QUESTION_TYPE_MISSING",
  "QUESTION_DUPLICATE_ID",
  "QUESTION_OPTIONS_TOO_MANY",
  "QUESTION_STATEMENTS_TOO_MANY",
  "QUESTION_BLANKS_TOO_MANY",
  "QUESTION_ANSWERS_TOO_MANY",
  "QUESTION_CHILDREN_TOO_MANY",
  "QUESTION_CHILD_TYPE_NOT_ALLOWED",
  "QUESTION_BLANK_MARKER_MISMATCH",
  "QUESTION_PAIR_UNKNOWN_LEFT",
  "QUESTION_PAIR_UNKNOWN_RIGHT",
  "QUESTION_PAIR_DUPLICATE_LEFT",
  "QUESTION_ATTACHMENT_UNKNOWN",
  "QUESTION_WORD_LIMIT_INVALID",
  "QUESTION_SCORING_METHOD_MISSING",
  "QUESTION_SCORING_RULE_MISSING",
  "POINTS_NOT_POSITIVE",
  "POINTS_TOO_MANY_DECIMALS",
  "POINTS_TOO_LARGE",
  "SCORE_TABLE_INVALID",
  "MATH_UNBALANCED",
  "CATALOG_VALUE_UNKNOWN",
] as const;

const KNOWN = new Set<string>(SERVER_FIELD_ERROR_CODES);

/** `body.children[1].body.options[0].content` -> `children.1.body.options.0.content`. */
export function toFormPath(path: string): string {
  const dotted = path.replace(/\[(\d+)\]/g, ".$1");
  const child = /^body\.children\.(\d+)(.*)$/.exec(dotted);
  const local = child ? `children.${child[1]}${child[2]}` : dotted;
  // Lỗi sai thường gặp và tệp đính kèm là một ô, không phải mảng, ở biểu mẫu.
  return local.replace(/^(commonMistakes|attachmentIds)\.\d+$/, "$1").replace(/^body\.type$/, "body");
}

/** Khóa chữ của lỗi trong `questions.editor.errors`. */
export function serverErrorKey(code: string): string {
  return `server.${KNOWN.has(code) ? code : "UNKNOWN"}`;
}

/** Đặt lỗi của máy chủ lên các ô của biểu mẫu; trả số lỗi đã đặt. */
export function applyServerFieldErrors(
  fieldErrors: readonly FieldError[],
  setError: UseFormSetError<QuestionEditorValues>,
): number {
  let applied = 0;
  for (const error of fieldErrors) {
    if (!error.field) continue;
    setError(toFormPath(error.field) as never, { type: "server", message: serverErrorKey(error.code) });
    applied += 1;
  }
  return applied;
}
