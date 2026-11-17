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
/** Dấu chỗ công thức trong tóm tắt dòng: ký hiệu hàm số trung lập ngôn ngữ, không nằm trong bảng chữ có dấu cấm của test (F-06). */
const FORMULA_PLACEHOLDER = "ƒ(x)";

/**
 * Tóm tắt thuần của `contentPreview` do API trả (F-06): chỗ trống `[[id]]` thành `___`, và công thức
 * `\( … \)`/`\[ … \]` cân nhau thành `ƒ(x)` — hiển thị nguyên mã LaTeX trong dòng tóm tắt không đọc được.
 */
export function plainQuestionPreview(preview: string): string {
  const text = preview.replace(CLOZE_ID_RE, "___");
  let out = "";
  const stack: string[] = [];
  const depth: number[] = [];
  let i = 0;
  while (i < text.length) {
    const char = text[i]!;
    if (char === "\\" && i + 1 < text.length) {
      const next = text[i + 1]!;
      if (next === "(" || next === "[") {
        stack.push(next === "(" ? ")" : "]");
        depth.push(out.length);
        i += 2;
        continue;
      }
      if ((next === ")" || next === "]") && stack.at(-1) === next) {
        stack.pop();
        const start = depth.pop()!;
        out = `${out.slice(0, start)}${FORMULA_PLACEHOLDER}`;
        i += 2;
        continue;
      }
    }
    out += char;
    i += 1;
  }
  return out.replace(/ {2,}/g, " ").trim();
}
