import type { PersonRef } from "@/features/content/types";
import type { EssayCriterion } from "@/features/questions/types";

/**
 * Hàng chờ chấm tự luận (UNI-2604, spec `docs/specs/2026-11-16-mockup-7-ky-thi.md` mục 2.1), dữ liệu mẫu. Khi có
 * OpenAPI của backend, đổi các kiểu dưới đây thành bí danh của `components["schemas"][...]` và sửa chỗ lệch ở `api.ts`.
 *
 * Phần trắc nghiệm được chấm ngay khi nộp; phần tự luận chấm tay theo tiêu chí của câu. Lượt thi ở trạng thái
 * `GRADING` ("Chờ chấm") tới khi người chấm hoàn tất, sau đó là `SCORED` ("Đã có điểm") và có tổng điểm.
 */

export const GRADING_STATUSES = ["GRADING", "SCORED"] as const;
export type GradingStatus = (typeof GRADING_STATUSES)[number];

export interface GradingLearner {
  id: string;
  /** Mã học viên hiển thị, ví dụ `HV-000123`. */
  code: string;
  name: string;
}

/** Bài thi của lượt thi, chụp lại lúc nộp (tên bài có thể đổi sau đó). */
export interface GradingExamRef {
  id: string;
  code: string;
  name: string;
  /** Mã kỳ thi trong `ContentReference.examKinds`. */
  kindCode: string;
  /** Môn của bài với kỳ thi có mẫu theo môn; `null` với bài nhiều môn. */
  subjectCode: string | null;
  /** Thang điểm của bài; `null` khi kỳ thi không có thang cố định. */
  maxScore: number | null;
}

/** Một dòng của hàng chờ chấm. */
export interface GradingAttemptSummary {
  id: string;
  learner: GradingLearner;
  exam: GradingExamRef;
  submittedAt: string;
  status: GradingStatus;
  /** Điểm phần trắc nghiệm, đã chấm khi nộp. */
  objectiveScore: number;
  /** Điểm tối đa phần trắc nghiệm; 0 khi đề chỉ có tự luận (THPTQG Ngữ văn). */
  objectiveMaxScore: number;
  /** Tổng điểm tối đa các câu tự luận. */
  essayMaxScore: number;
  /** Điểm tự luận; `null` tới khi hoàn tất chấm. */
  essayScore: number | null;
  /** Trắc nghiệm + tự luận; `null` tới khi hoàn tất chấm. */
  totalScore: number | null;
  essayCount: number;
  /** Số câu tự luận đã nhập đủ điểm mọi tiêu chí (kể cả bản nháp). */
  gradedEssayCount: number;
  /** Người chấm đang giữ lượt; `null` khi chưa ai nhận. */
  grader: PersonRef | null;
  gradedAt: string | null;
}

/** Điểm của một tiêu chí; `null` khi chưa nhập. */
export interface CriterionScore {
  criterionId: string;
  score: number | null;
}

/** Đoạn đọc hiểu của câu chùm khi câu tự luận là câu con. */
export interface GradingPassage {
  questionId: string;
  code: string;
  passage: string;
}

/** Một câu tự luận của lượt thi: đề, tiêu chí chấm, bài viết của học viên và điểm đã nhập. */
export interface GradingEssay {
  questionId: string;
  questionCode: string;
  /** Tên phần trong đề ("Phần 1", "Đọc hiểu", "Viết"). */
  partName: string;
  /** Số thứ tự câu trong phần hoặc trong đề. */
  number: number;
  subjectCode: string;
  content: string;
  passage: GradingPassage | null;
  maxPoints: number;
  rubric: EssayCriterion[];
  sampleAnswer: string | null;
  wordLimit: number | null;
  /** Bài viết của học viên; chuỗi rỗng khi bỏ trống. */
  answer: string;
  /** Cùng thứ tự với `rubric`. */
  scores: CriterionScore[];
  comment: string;
  /**
   * Nguồn của đề và tiêu chí: `BANK` khi câu còn trong ngân hàng câu hỏi, `ATTEMPT` khi lấy từ bản chụp lúc thi.
   * Khi có backend, đề và tiêu chí đi theo phiên bản câu hỏi học viên đã làm.
   */
  source: "BANK" | "ATTEMPT";
}

export interface GradingAttemptDetail extends GradingAttemptSummary {
  essays: GradingEssay[];
}

export interface ListGradingAttemptsQuery {
  /** Tìm theo tên, mã học viên hoặc mã bài thi. */
  q?: string;
  status?: GradingStatus;
  examKindCode?: string;
  examId?: string;
  /** Id người chấm; `UNASSIGNED_GRADER` cho lượt chưa ai nhận. */
  graderId?: string;
  page?: number;
  size?: number;
  /** `field,asc|desc`; mặc định `submittedAt,asc` (nộp trước chấm trước). */
  sort?: string;
}

/** Giá trị lọc "Chưa có người chấm". */
export const UNASSIGNED_GRADER = "UNASSIGNED";

export interface GradingStatusCounts {
  total: number;
  byStatus: Record<GradingStatus, number>;
}

/** Lựa chọn của bộ lọc: các bài thi có lượt cần chấm và người chấm. */
export interface GradingFilterOptions {
  exams: GradingExamRef[];
  graders: PersonRef[];
}

export interface EssayGradeInput {
  questionId: string;
  scores: CriterionScore[];
  comment: string;
}

export interface SaveGradingRequest {
  attemptId: string;
  essays: EssayGradeInput[];
}
