import type { QuestionDetail, QuestionSummary } from "./types";

const SUMMARY_KEYS = [
  "id", "code", "type", "status", "contentPreview", "subjectCode", "gradeCode", "difficultyCode", "points",
  "knowledge", "competencyCode", "taskTypeCode", "tagCodes", "hasSolution", "version", "revision", "usedInExamCount",
  "sourceCode", "createdBy", "createdAt", "updatedAt",
] as const satisfies readonly (keyof QuestionSummary)[];

/** Dòng tóm tắt của một câu hỏi đầy đủ (danh sách, kết quả đổi trạng thái). */
export function toQuestionSummary(question: QuestionDetail): QuestionSummary {
  return Object.fromEntries(SUMMARY_KEYS.map((key) => [key, question[key]])) as unknown as QuestionSummary;
}

const CLOZE_ID_RE = /\[\[[^\]]+\]\]/g;

/**
 * Tóm tắt thuần của `contentPreview` do API trả (chữ thuần còn sót dấu công thức và mã ô trống, F-06):
 * chỗ trống `[[id]]` thành `___` và bỏ cặp dấu `\( … \)`/`\[ … \]` cân nhau (giữ nội dung bên trong).
 */
export function plainQuestionPreview(preview: string): string {
  const text = preview.replace(CLOZE_ID_RE, "___");
  let out = "";
  const stack: string[] = [];
  for (let i = 0; i < text.length; i++) {
    const char = text[i]!;
    if (char === "\\" && i + 1 < text.length) {
      const next = text[i + 1]!;
      if (next === "(" || next === "[") {
        stack.push(next === "(" ? ")" : "]");
        i++;
        continue;
      }
      if ((next === ")" || next === "]") && stack.at(-1) === next) {
        stack.pop();
        i++;
        continue;
      }
    }
    out += char;
  }
  return out.replace(/ {2,}/g, " ").trim();
}
