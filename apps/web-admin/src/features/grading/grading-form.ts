import { z } from "zod";
import { checkScoreInput, COMMENT_MAX, formatScoreInput, parseScore, sumScores, type ScoreIssue } from "./scoring";
import type { GradingAttemptDetail, GradingEssay, SaveGradingRequest } from "./types";

/**
 * Biểu mẫu chấm một lượt thi: mỗi câu tự luận một mục, mỗi tiêu chí một ô điểm (chuỗi để nhận cả "1,5" và "1.5"), một
 * ô nhận xét. Không phụ thuộc React để kiểm thử riêng; lỗi lưu dưới dạng khóa dịch (`grading.detail.validation.*`).
 */

export interface EssayFormValues {
  questionId: string;
  /** Cùng thứ tự với `rubric` của câu. */
  scores: string[];
  comment: string;
}

export interface GradingFormValues {
  essays: EssayFormValues[];
}

export type GradingValidationKey = ScoreIssue | "commentMax";

export function toFormValues(attempt: GradingAttemptDetail, decimalSeparator: string): GradingFormValues {
  return {
    essays: attempt.essays.map((essay) => ({
      questionId: essay.questionId,
      scores: essay.scores.map((item) => formatScoreInput(item.score, decimalSeparator)),
      comment: essay.comment,
    })),
  };
}

/** Ô trống gửi `null` (chưa chấm). Chỉ gọi sau khi biểu mẫu đã hợp lệ. */
export function toSaveRequest(attempt: GradingAttemptDetail, values: GradingFormValues): SaveGradingRequest {
  return {
    attemptId: attempt.id,
    essays: attempt.essays.map((essay, index) => {
      const input = values.essays[index];
      return {
        questionId: essay.questionId,
        scores: essay.rubric.map((criterion, criterionIndex) => {
          const raw = input?.scores[criterionIndex] ?? "";
          return { criterionId: criterion.id, score: raw.trim() === "" ? null : parseScore(raw) };
        }),
        comment: input?.comment ?? "",
      };
    }),
  };
}

/**
 * Lưu nháp: ô điểm được để trống; ô có giá trị phải là số không âm, tối đa hai chữ số thập phân và không vượt điểm của
 * tiêu chí. Nhận xét tối đa `COMMENT_MAX` ký tự. Hoàn tất chấm kiểm thêm ô trống bằng `findMissingScores`.
 */
export function gradingSchema(essays: readonly GradingEssay[]) {
  return z
    .object({
      essays: z.array(
        z.object({
          questionId: z.string(),
          scores: z.array(z.string()),
          comment: z.string(),
        }),
      ),
    })
    .superRefine((values, ctx) => {
      values.essays.forEach((input, essayIndex) => {
        const essay = essays[essayIndex];
        if (!essay) return;
        essay.rubric.forEach((criterion, criterionIndex) => {
          const issue = checkScoreInput(input.scores[criterionIndex] ?? "", criterion.points, false);
          if (issue) {
            ctx.addIssue({ code: "custom", path: ["essays", essayIndex, "scores", criterionIndex], message: issue });
          }
        });
        if (input.comment.length > COMMENT_MAX) {
          ctx.addIssue({ code: "custom", path: ["essays", essayIndex, "comment"], message: "commentMax" });
        }
      });
    });
}

export interface ScoreLocation {
  essayIndex: number;
  criterionIndex: number;
}

/** Ô điểm còn trống, theo thứ tự câu rồi tiêu chí (hoàn tất chấm cần đủ điểm mọi tiêu chí). */
export function findMissingScores(essays: readonly GradingEssay[], values: GradingFormValues): ScoreLocation[] {
  const missing: ScoreLocation[] = [];
  essays.forEach((essay, essayIndex) => {
    essay.rubric.forEach((_criterion, criterionIndex) => {
      const raw = values.essays[essayIndex]?.scores[criterionIndex] ?? "";
      if (raw.trim() === "") missing.push({ essayIndex, criterionIndex });
    });
  });
  return missing;
}

export function scoreFieldName(location: ScoreLocation): `essays.${number}.scores.${number}` {
  return `essays.${location.essayIndex}.scores.${location.criterionIndex}`;
}

/** Tổng điểm đang nhập của một câu (bỏ qua ô trống hoặc sai). */
export function essayInputTotal(scores: readonly string[] | undefined): number {
  return sumScores(
    (scores ?? []).map((raw) => {
      const value = parseScore(raw);
      return Number.isFinite(value) ? value : null;
    }),
  );
}

export type EssayProgress = "complete" | "partial" | "empty";

/** Tình trạng nhập điểm của một câu, để thanh chọn câu nêu câu nào còn thiếu. */
export function essayProgress(essay: GradingEssay, scores: readonly string[] | undefined): EssayProgress {
  const filled = essay.rubric.filter((criterion, index) =>
    checkScoreInput(scores?.[index] ?? "", criterion.points, true) === null,
  ).length;
  if (filled === 0) return "empty";
  return filled === essay.rubric.length ? "complete" : "partial";
}
