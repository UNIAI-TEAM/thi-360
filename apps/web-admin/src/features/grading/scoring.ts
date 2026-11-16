import type { CriterionScore } from "./types";

/**
 * Quy tắc điểm của màn chấm tự luận, không phụ thuộc React để kiểm thử riêng.
 *
 * - Điểm một tiêu chí nhập bằng dấu phẩy hoặc dấu chấm ("1,5" hoặc "1.5"), không âm, tối đa hai chữ số thập phân,
 *   không vượt điểm tối đa của tiêu chí.
 * - Điểm câu = tổng điểm các tiêu chí; điểm tự luận của lượt = tổng điểm các câu; tổng điểm = trắc nghiệm + tự luận.
 */

/** Số chữ số thập phân tối đa của điểm tiêu chí (các tiêu chí mẫu chia theo 0,25). */
export const SCORE_DECIMALS = 2;

/** Độ dài tối đa của nhận xét một câu. */
export const COMMENT_MAX = 2000;

const SCORE_PATTERN = new RegExp(`^\\d+([.,]\\d{1,${SCORE_DECIMALS}})?$`);

/** "1,5" hoặc "1.5" → 1.5; ô trống hoặc không phải số hợp lệ → `NaN`. */
export function parseScore(value: string): number {
  const trimmed = value.trim();
  if (!SCORE_PATTERN.test(trimmed)) return Number.NaN;
  return Number(trimmed.replace(",", "."));
}

/** Làm tròn hai chữ số để cộng điểm thập phân không lệch (0,1 + 0,2). */
export function roundScore(value: number): number {
  return Math.round(value * 10 ** SCORE_DECIMALS) / 10 ** SCORE_DECIMALS;
}

export function sumScores(values: ReadonlyArray<number | null | undefined>): number {
  return roundScore(values.reduce<number>((total, value) => total + (value ?? 0), 0));
}

/** Câu đã có điểm mọi tiêu chí. Câu không có tiêu chí nào chưa chấm được. */
export function isEssayComplete(scores: readonly CriterionScore[]): boolean {
  return scores.length > 0 && scores.every((score) => score.score !== null);
}

export type ScoreIssue = "invalid" | "tooHigh" | "required";

/**
 * Kiểm tra một ô điểm. `required` chỉ bật khi hoàn tất chấm; lưu nháp cho phép để trống.
 * Trả `null` khi hợp lệ.
 */
export function checkScoreInput(value: string, max: number, required: boolean): ScoreIssue | null {
  if (value.trim() === "") return required ? "required" : null;
  const score = parseScore(value);
  if (!Number.isFinite(score)) return "invalid";
  if (score > max) return "tooHigh";
  return null;
}

/** Điểm hiện trong ô nhập, theo dấu thập phân của ngôn ngữ đang dùng. */
export function formatScoreInput(score: number | null, decimalSeparator: string): string {
  if (score === null) return "";
  return String(score).replace(".", decimalSeparator);
}

/** Đếm số chữ của bài viết (tách theo khoảng trắng), để so với số chữ gợi ý của câu. */
export function countWords(text: string): number {
  const trimmed = text.trim();
  return trimmed === "" ? 0 : trimmed.split(/\s+/u).length;
}
