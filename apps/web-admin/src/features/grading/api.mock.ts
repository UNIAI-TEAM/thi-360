import { questionStore } from "./mock/question-store";
import type { QuestionDetail } from "@/features/questions/types";
import { MockApiError, mockRequest, paginate, type MockPage } from "@/lib/mock/content-mock";
import { DEFAULT_GRADING_SORT } from "./keys";
import {
  attemptStore,
  ESSAY_SNAPSHOTS,
  MOCK_CURRENT_GRADER,
  MOCK_GRADERS,
  PASSAGE_SNAPSHOTS,
  type AttemptEssayRecord,
  type AttemptRecord,
} from "./mock/store";
import { isEssayComplete, roundScore, sumScores } from "./scoring";
import {
  UNASSIGNED_GRADER,
  type CriterionScore,
  type GradingAttemptDetail,
  type GradingAttemptSummary,
  type GradingEssay,
  type GradingExamRef,
  type GradingFilterOptions,
  type GradingStatusCounts,
  type ListGradingAttemptsQuery,
  type SaveGradingRequest,
} from "./types";

/**
 * API của hàng chờ chấm tự luận. Hiện trả dữ liệu mẫu từ `mock/store.ts`; đề và tiêu chí chấm đọc từ `questionStore`
 * khi câu còn trong ngân hàng câu hỏi, không thì từ bản chụp lúc thi. Khi nối backend: thay thân từng hàm bằng lời gọi
 * `@thi360/api-client`, giữ nguyên chữ ký; hook trong `queries.ts` và giao diện không đổi.
 */

export type PageResponseGradingAttempt = MockPage<GradingAttemptSummary>;

// ---------------------------------------------------------------- Đề và tiêu chí của câu tự luận

type ResolvedQuestion = Pick<
  GradingEssay,
  "questionCode" | "subjectCode" | "content" | "passage" | "maxPoints" | "rubric" | "sampleAnswer" | "wordLimit" | "source"
>;

/** Tìm câu trong ngân hàng, kể cả câu con của câu chùm; trả kèm câu chùm cha. */
function findBankQuestion(id: string): { question: QuestionDetail; parent: QuestionDetail | null } | null {
  const direct = questionStore.get(id);
  if (direct) return { question: direct, parent: null };
  for (const parent of questionStore.all()) {
    const child = parent.children.find((item) => item.id === id);
    if (child) return { question: child, parent };
  }
  return null;
}

/**
 * Đề, tiêu chí chấm và đoạn đọc hiểu của một câu tự luận. Ưu tiên câu Tự luận trong ngân hàng (`questionStore`), không
 * có thì lấy bản chụp lúc thi. Khi có backend: đề và tiêu chí theo đúng phiên bản câu học viên đã làm.
 */
function resolveEssayQuestion(questionId: string): ResolvedQuestion {
  const bank = findBankQuestion(questionId);
  if (bank && bank.question.body.type === "ESSAY") {
    const { question, parent } = bank;
    const body = bank.question.body;
    const passage =
      parent && parent.body.type === "QUESTION_SET"
        ? { questionId: parent.id, code: parent.code, passage: parent.body.passage || parent.content }
        : null;
    return {
      questionCode: question.code,
      subjectCode: question.subjectCode,
      content: question.content,
      passage,
      maxPoints: question.points,
      rubric: body.rubric,
      sampleAnswer: body.sampleAnswer,
      wordLimit: body.wordLimit,
      source: "BANK",
    };
  }
  const snapshot = ESSAY_SNAPSHOTS.get(questionId);
  if (!snapshot) throw new MockApiError(422, "UNKNOWN_QUESTION");
  const passage = snapshot.passage ? PASSAGE_SNAPSHOTS.get(snapshot.passage.questionId) ?? null : null;
  return {
    questionCode: snapshot.code,
    subjectCode: snapshot.subjectCode,
    content: snapshot.content,
    passage: passage ? { ...passage } : null,
    maxPoints: snapshot.maxPoints,
    rubric: snapshot.rubric,
    sampleAnswer: snapshot.sampleAnswer,
    wordLimit: snapshot.wordLimit,
    source: "ATTEMPT",
  };
}

/** Điểm đã lưu theo đúng thứ tự tiêu chí hiện hành; tiêu chí chưa có điểm là `null`. */
function alignScores(rubric: GradingEssay["rubric"], stored: readonly CriterionScore[]): CriterionScore[] {
  return rubric.map((criterion) => ({
    criterionId: criterion.id,
    score: stored.find((item) => item.criterionId === criterion.id)?.score ?? null,
  }));
}

function toEssay(record: AttemptEssayRecord): GradingEssay {
  const question = resolveEssayQuestion(record.questionId);
  return {
    questionId: record.questionId,
    partName: record.partName,
    number: record.number,
    answer: record.answer,
    scores: alignScores(question.rubric, record.scores),
    comment: record.comment,
    ...question,
  };
}

function essayPoints(essay: GradingEssay): number {
  return sumScores(essay.scores.map((item) => item.score));
}

function toDetail(record: AttemptRecord): GradingAttemptDetail {
  const essays = record.essays.map(toEssay);
  const scored = record.status === "SCORED";
  const essayScore = scored ? sumScores(essays.map(essayPoints)) : null;
  return {
    id: record.id,
    learner: record.learner,
    exam: record.exam,
    submittedAt: record.submittedAt,
    status: record.status,
    objectiveScore: record.objectiveScore,
    objectiveMaxScore: record.objectiveMaxScore,
    essayMaxScore: sumScores(essays.map((essay) => essay.maxPoints)),
    essayScore,
    totalScore: essayScore === null ? null : roundScore(record.objectiveScore + essayScore),
    essayCount: essays.length,
    gradedEssayCount: essays.filter((essay) => isEssayComplete(essay.scores)).length,
    grader: record.grader,
    gradedAt: record.gradedAt,
    essays,
  };
}

function toSummary(record: AttemptRecord): GradingAttemptSummary {
  const detail: Partial<GradingAttemptDetail> = toDetail(record);
  delete detail.essays;
  return detail as GradingAttemptSummary;
}

// ---------------------------------------------------------------- Danh sách

function matchesQuery(item: GradingAttemptSummary, query: ListGradingAttemptsQuery, withStatus: boolean): boolean {
  const text = query.q?.trim().toLocaleLowerCase("vi");
  if (
    text &&
    !item.learner.name.toLocaleLowerCase("vi").includes(text) &&
    !item.learner.code.toLocaleLowerCase("vi").includes(text) &&
    !item.exam.code.toLocaleLowerCase("vi").includes(text)
  ) {
    return false;
  }
  if (withStatus && query.status && item.status !== query.status) return false;
  if (query.examKindCode && item.exam.kindCode !== query.examKindCode) return false;
  if (query.examId && item.exam.id !== query.examId) return false;
  if (query.graderId === UNASSIGNED_GRADER && item.grader !== null) return false;
  if (query.graderId && query.graderId !== UNASSIGNED_GRADER && item.grader?.id !== query.graderId) return false;
  return true;
}

function compareBy(sort: string | undefined): (a: GradingAttemptSummary, b: GradingAttemptSummary) => number {
  const [field = "submittedAt", direction = "asc"] = (sort ?? DEFAULT_GRADING_SORT).split(",");
  const sign = direction === "desc" ? -1 : 1;
  return (a, b) => {
    const result =
      field === "learner"
        ? a.learner.name.localeCompare(b.learner.name, "vi")
        : a.submittedAt.localeCompare(b.submittedAt);
    return result === 0 ? a.id.localeCompare(b.id) : result * sign;
  };
}

export async function listGradingAttempts(query: ListGradingAttemptsQuery): Promise<PageResponseGradingAttempt> {
  return mockRequest(
    () => {
      const rows = attemptStore
        .all()
        .map(toSummary)
        .filter((item) => matchesQuery(item, query, true))
        .sort(compareBy(query.sort));
      return paginate(rows, query.page ?? 0, query.size ?? 20);
    },
    { empty: () => paginate<GradingAttemptSummary>([], 0, query.size ?? 20) },
  );
}

/** Số lượt theo trạng thái với cùng bộ lọc, bỏ điều kiện trạng thái (dải trạng thái của danh sách). */
export async function getGradingStatusCounts(query: ListGradingAttemptsQuery): Promise<GradingStatusCounts> {
  return mockRequest(
    () => {
      const rows = attemptStore
        .all()
        .map(toSummary)
        .filter((item) => matchesQuery(item, query, false));
      return {
        total: rows.length,
        byStatus: {
          GRADING: rows.filter((item) => item.status === "GRADING").length,
          SCORED: rows.filter((item) => item.status === "SCORED").length,
        },
      };
    },
    { empty: () => ({ total: 0, byStatus: { GRADING: 0, SCORED: 0 } }) },
  );
}

/** Bài thi có lượt cần chấm (một lần mỗi bài) và danh sách người chấm. */
export async function getGradingFilterOptions(): Promise<GradingFilterOptions> {
  return mockRequest(() => {
    const exams = new Map<string, GradingExamRef>();
    for (const record of attemptStore.all()) exams.set(record.exam.id, record.exam);
    return {
      exams: [...exams.values()].sort((a, b) => a.code.localeCompare(b.code)),
      graders: [...MOCK_GRADERS],
    };
  });
}

/** `?mockState=empty`: lượt thi không còn câu tự luận nào (ví dụ đề đã bỏ câu), màn hiện trạng thái rỗng. */
export async function getGradingAttempt(id: string): Promise<GradingAttemptDetail> {
  return mockRequest(() => toDetail(attemptStore.require(id)), {
    empty: () => ({
      ...toDetail(attemptStore.require(id)),
      essays: [],
      essayCount: 0,
      gradedEssayCount: 0,
      essayMaxScore: 0,
    }),
  });
}

// ---------------------------------------------------------------- Lưu nháp, hoàn tất

/** Áp điểm và nhận xét vào bản ghi; kiểm tra câu thuộc lượt và điểm trong khoảng của tiêu chí. */
function applyGrades(record: AttemptRecord, request: SaveGradingRequest, requireComplete: boolean): AttemptRecord {
  if (record.status === "SCORED") throw new MockApiError(409, "ATTEMPT_ALREADY_SCORED");
  const inputs = new Map(request.essays.map((essay) => [essay.questionId, essay]));
  for (const questionId of inputs.keys()) {
    if (!record.essays.some((essay) => essay.questionId === questionId)) {
      throw new MockApiError(422, "UNKNOWN_QUESTION");
    }
  }
  const essays = record.essays.map((essay) => {
    const input = inputs.get(essay.questionId);
    if (!input) return essay;
    const { rubric } = resolveEssayQuestion(essay.questionId);
    for (const item of input.scores) {
      const criterion = rubric.find((entry) => entry.id === item.criterionId);
      if (!criterion) throw new MockApiError(422, "UNKNOWN_QUESTION");
      if (item.score !== null && (item.score < 0 || item.score > criterion.points)) {
        throw new MockApiError(422, "SCORE_OUT_OF_RANGE");
      }
    }
    return { ...essay, scores: alignScores(rubric, input.scores), comment: input.comment.trim() };
  });
  if (requireComplete && !essays.every((essay) => isEssayComplete(essay.scores))) {
    throw new MockApiError(422, "GRADING_INCOMPLETE");
  }
  return { ...record, essays, grader: record.grader ?? MOCK_CURRENT_GRADER };
}

/** Lưu nháp: giữ điểm đã nhập (được để trống), lượt vẫn "Chờ chấm". Người chấm chưa có thì nhận lượt. */
export async function saveGradingDraft(request: SaveGradingRequest): Promise<GradingAttemptDetail> {
  return mockRequest(
    () => {
      const next = applyGrades(attemptStore.require(request.attemptId), request, false);
      return toDetail(attemptStore.update(request.attemptId, () => next));
    },
    { ignoreScenario: true },
  );
}

/** Hoàn tất chấm: mọi tiêu chí phải có điểm; lượt chuyển sang "Đã có điểm" và có tổng điểm. */
export async function finishGrading(request: SaveGradingRequest): Promise<GradingAttemptDetail> {
  return mockRequest(
    () => {
      const next = applyGrades(attemptStore.require(request.attemptId), request, true);
      const scored: AttemptRecord = { ...next, status: "SCORED", gradedAt: new Date().toISOString() };
      return toDetail(attemptStore.update(request.attemptId, () => scored));
    },
    { ignoreScenario: true },
  );
}
