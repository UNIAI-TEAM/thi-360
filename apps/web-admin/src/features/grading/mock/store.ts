import type { PersonRef } from "@/features/content/types";
import type { EssayCriterion } from "@/features/questions/types";
import { createMockStore } from "@/lib/mock/content-mock";
import type { CriterionScore, GradingExamRef, GradingLearner, GradingStatus } from "../types";
import fixture from "./grading.json";

/**
 * Kho lượt thi mẫu của hàng chờ chấm tự luận. Lưu nháp và hoàn tất chấm ghi vào kho này để danh sách và màn chấm thấy
 * cùng dữ liệu tới khi tải lại trang. Xóa khi nối backend.
 */

/** Bản chụp câu tự luận lúc thi, dùng khi câu không còn (hoặc chưa có) trong `questionStore`. */
export interface EssayQuestionSnapshot {
  id: string;
  code: string;
  subjectCode: string;
  content: string;
  maxPoints: number;
  rubric: EssayCriterion[];
  sampleAnswer: string | null;
  wordLimit: number | null;
  /** Câu chùm chứa câu này (đọc hiểu); `null` với câu đơn. */
  passage: { questionId: string; code: string } | null;
}

export interface PassageSnapshot {
  questionId: string;
  code: string;
  passage: string;
}

export interface AttemptEssayRecord {
  questionId: string;
  partName: string;
  number: number;
  answer: string;
  scores: CriterionScore[];
  comment: string;
}

export interface AttemptRecord {
  id: string;
  learner: GradingLearner;
  exam: GradingExamRef;
  submittedAt: string;
  status: GradingStatus;
  objectiveScore: number;
  objectiveMaxScore: number;
  grader: PersonRef | null;
  gradedAt: string | null;
  essays: AttemptEssayRecord[];
}

const data = fixture as {
  graders: PersonRef[];
  currentGraderId: string;
  questions: EssayQuestionSnapshot[];
  passages: PassageSnapshot[];
  attempts: AttemptRecord[];
};

export const attemptStore = createMockStore<AttemptRecord>(data.attempts);

export const ESSAY_SNAPSHOTS: ReadonlyMap<string, EssayQuestionSnapshot> = new Map(
  data.questions.map((question) => [question.id, question]),
);

export const PASSAGE_SNAPSHOTS: ReadonlyMap<string, PassageSnapshot> = new Map(
  data.passages.map((passage) => [passage.questionId, passage]),
);

export const MOCK_GRADERS: readonly PersonRef[] = data.graders;

/** Người chấm đang đăng nhập trong dữ liệu mẫu (nhận lượt khi lưu nháp). */
export const MOCK_CURRENT_GRADER: PersonRef =
  data.graders.find((grader) => grader.id === data.currentGraderId) ?? data.graders[0]!;
