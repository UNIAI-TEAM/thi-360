import referenceFixture from "@/features/content/mock/reference.json";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import type { ContentReference } from "@/features/content/types";
import { questionStore } from "@/features/questions/mock/store";
import type { QuestionDetail } from "@/features/questions/types";
import { cellKey, enumeratePaths, type ChoiceNameResolver } from "../check";
import type {
  BlockAvailability,
  ExamAvailability,
  ExamChange,
  ExamCheckResult,
  SourceOverlap,
} from "../review-types";
import type { ExamDetail, ExamPart, KnowledgeScope, QuestionBlock } from "../types";
import fixture from "./review.json";

/**
 * Dữ liệu mẫu của Kiểm tra, Xem trước, Phiên bản: nguồn câu hỏi giả lập, kết quả kiểm tra gần nhất, bản chụp các
 * phiên bản đã công bố. Xóa khi nối backend.
 *
 * Nguồn của một ô = số câu Sẵn sàng khớp khối trong `questionStore` + `baseline.perCell` (phần ngân hàng không nạp
 * vào dữ liệu mẫu); `review.json` ghi đè số tuyệt đối cho vài ô để tái hiện hai vấn đề của bản vẽ (KH-2 thiếu câu
 * Hóa học Lớp 12/Vận dụng, KH-3 nguồn giao nhau chưa xác nhận được).
 */

interface BlockOverride {
  cells?: Record<string, number>;
  sets?: number;
  overlap?: SourceOverlap;
  /** Số câu tuyệt đối theo năng lực (chiều năng lực, V-ACT). */
  competencies?: Record<string, number>;
}

type FixtureChange = Omit<ExamChange, "warning"> & { optionId: string | null };

interface ExamFixture {
  blocks?: Record<string, BlockOverride>;
  versionPaths?: Record<string, string[]>;
  changes?: FixtureChange[];
}

interface ReviewFixture {
  baseline: { perCell: number; sets: number };
  exams: Record<string, ExamFixture>;
}

const data = fixture as ReviewFixture;
const reference = referenceFixture as ContentReference;
const knowledgeIndex = indexKnowledge(reference.knowledgeTree);

export const MOCK_GRADE_CODES = reference.grades.map((grade) => grade.code);
export const MOCK_DIFFICULTY_CODES = reference.difficulties.map((difficulty) => difficulty.code);

/** Tên lựa chọn theo danh mục: môn (Tiếng Anh) hoặc tổ hợp (Vật lí – Hóa học – Sinh học). */
export const mockChoiceName: ChoiceNameResolver = (kind, code) =>
  (kind === "COMBINATION"
    ? reference.combinations.find((item) => item.code === code)?.name
    : reference.subjects.find((item) => item.code === code)?.name) ?? code;

export function mockKnowledgeNode(nodeId: string) {
  const entry = knowledgeIndex.get(nodeId);
  if (!entry) return null;
  return { nodeId: entry.node.id, code: entry.node.code, name: entry.node.name };
}

/** Chương chứa nút (hoặc chính nút nếu là chương), để thống kê theo kiến thức. */
export function mockKnowledgeChapter(nodeId: string) {
  const entry = knowledgeIndex.get(nodeId);
  if (!entry) return null;
  const chapter = entry.node.level === "CHAPTER" ? entry.node : entry.ancestors.find((node) => node.level === "CHAPTER");
  if (!chapter) return null;
  return { nodeId: chapter.id, code: chapter.code, name: chapter.name, subjectCode: chapter.subjectCode };
}

/* ---------- Nguồn câu hỏi ---------- */

function coversScope(nodeIds: readonly string[], scope: KnowledgeScope): boolean {
  if (scope.nodeIds.length === 0) return true;
  const covers = (nodeId: string, scopeId: string) =>
    nodeId === scopeId ||
    (scope.includeDescendants && (knowledgeIndex.get(nodeId)?.ancestors.some((node) => node.id === scopeId) ?? false));
  const hit = (scopeId: string) => nodeIds.some((nodeId) => covers(nodeId, scopeId));
  return scope.match === "ALL" ? scope.nodeIds.every(hit) : scope.nodeIds.some(hit);
}

/** Câu Sẵn sàng trong kho khớp khối: môn, dạng, nhãn, năng lực, phạm vi MAPID (câu chùm xét cả câu con). */
export function mockBlockCandidates(block: QuestionBlock, subjectCode: string): QuestionDetail[] {
  const competencies = block.knowledgeScope.competencyCodes ?? [];
  return questionStore.all().filter((question) => {
    if (question.status !== "READY" || question.subjectCode !== subjectCode) return false;
    if (question.type !== block.questionType) return false;
    if (!block.tagCodes.every((tag) => question.tagCodes.includes(tag))) return false;
    if (competencies.length && !competencies.includes(question.competencyCode ?? "")) return false;
    const nodeIds = [question, ...question.children].flatMap((item) => item.knowledge.map((ref) => ref.nodeId));
    return coversScope(nodeIds, block.knowledgeScope);
  });
}

function sameTable(a: readonly number[], b: readonly number[]): boolean {
  return a.length === b.length && a.every((value, index) => Math.abs(value - (b[index] ?? 0)) < 1e-6);
}

/**
 * Câu có quy tắc chấm khác quy tắc mong đợi của khối: bảng điểm Đúng/Sai khác (so theo tỉ lệ với điểm câu, vì bảng của
 * kỳ thi tính trên điểm tối đa 1) hoặc quy tắc nhiều đáp án khác.
 */
export function scoringDiffers(question: QuestionDetail, block: QuestionBlock): boolean {
  const expected = block.expectedScoring;
  if (!expected) return false;
  const body = question.body;
  if (body.type === "TRUE_FALSE" && expected.trueFalseTable) {
    const max = question.points || 1;
    const expectedMax = expected.trueFalseTable.at(-1) || 1;
    return !sameTable(
      body.scoreTable.map((value) => value / max),
      expected.trueFalseTable.map((value) => value / expectedMax),
    );
  }
  if (body.type === "MULTIPLE_CHOICE" && expected.multipleChoiceRule) return body.scoringRule !== expected.multipleChoiceRule;
  return false;
}

/** Câu Tự luận chưa có tiêu chí chấm (chấm tay cần hướng dẫn chấm). */
function lacksRubric(question: QuestionDetail): boolean {
  return question.body.type === "ESSAY" && question.body.rubric.length === 0;
}

function forEachBlock(parts: readonly ExamPart[], visit: (block: QuestionBlock, subjectCode: string) => void) {
  for (const part of parts) {
    for (const section of [...part.sections, ...part.options.flatMap((option) => option.sections)]) {
      for (const block of section.blocks) visit(block, section.subjectCode);
    }
  }
}

export function mockAvailability(examId: string, parts: readonly ExamPart[]): ExamAvailability {
  const overrides = data.exams[examId]?.blocks ?? {};
  const blocks: Record<string, BlockAvailability> = {};
  forEachBlock(parts, (block, subjectCode) => {
    const override = overrides[block.id];
    const candidates = mockBlockCandidates(block, subjectCode);
    const isSet = block.questionType === "QUESTION_SET";
    const units = isSet ? candidates.flatMap((set) => set.children) : candidates;
    const cells = MOCK_GRADE_CODES.flatMap((gradeCode) =>
      MOCK_DIFFICULTY_CODES.map((difficultyCode) => {
        const key = cellKey(gradeCode, difficultyCode);
        const inStore = units.filter((q) => q.gradeCode === gradeCode && q.difficultyCode === difficultyCode).length;
        return { gradeCode, difficultyCode, count: override?.cells?.[key] ?? inStore + data.baseline.perCell };
      }),
    );
    const byCompetency = block.matrix.byCompetency ?? [];
    blocks[block.id] = {
      blockId: block.id,
      cells,
      setCount: isSet ? (override?.sets ?? candidates.length + data.baseline.sets) : null,
      overlap: override?.overlap ?? null,
      ...(byCompetency.length
        ? {
            competencies: byCompetency.map(({ code }) => ({
              code,
              count:
                override?.competencies?.[code] ??
                units.filter((q) => q.competencyCode === code).length +
                  data.baseline.perCell * MOCK_DIFFICULTY_CODES.length,
            })),
          }
        : {}),
      ...(block.expectedScoring
        ? { scoringMismatch: units.filter((q) => scoringDiffers(q, block)).map((q) => q.code) }
        : {}),
      ...(block.questionType === "ESSAY" || units.some((q) => q.type === "ESSAY")
        ? { missingRubric: units.filter(lacksRubric).map((q) => q.code) }
        : {}),
    };
  });
  return { blocks };
}

/** Thời gian chạy giả lập: 1 giây + thời gian bộ giải dành cho các nguồn giao nhau (tối đa bằng giới hạn). */
export function mockCheckDuration(availability: ExamAvailability, timeLimitSeconds: number): number {
  return Object.values(availability.blocks).reduce(
    (sum, block) => sum + (block.overlap ? Math.min(block.overlap.solveSeconds, timeLimitSeconds) : 0),
    1,
  );
}

/* ---------- Kết quả kiểm tra và phiên bản ---------- */

export interface MockVersionState {
  version: number;
  parts: ExamPart[];
  publishedPathIds: string[];
  servingPathIds: string[];
}

export interface ExamReviewState {
  versions: MockVersionState[];
  /**
   * Đường đã công bố trong phiên. Khác rỗng thì danh sách thay đổi mẫu của bản sửa không còn đúng; đường của bản làm
   * việc không nằm ở đây là đường còn chờ công bố.
   */
  publishedInSession: string[];
}

const checks = new Map<string, ExamCheckResult>();
const states = new Map<string, ExamReviewState>();

/** Giữ lại các lựa chọn nằm trong `pathIds` (bản chụp của phiên bản công bố một phần). */
export function restrictParts(parts: readonly ExamPart[], pathIds: readonly string[]): ExamPart[] {
  const optionIds = new Set(pathIds.flatMap((pathId) => pathId.split("+")));
  return structuredClone(
    parts.map((part) =>
      part.mode === "CHOICE" ? { ...part, options: part.options.filter((option) => optionIds.has(option.id)) } : part,
    ),
  );
}

function seedState(exam: ExamDetail): ExamReviewState {
  const versionPaths = data.exams[exam.id]?.versionPaths ?? {};
  const allPathIds = enumeratePaths(exam.parts).map((path) => path.pathId);
  const versions = exam.versions
    .filter((item) => item.version !== null && item.state !== "WORKING")
    .map((item) => {
      const version = item.version!;
      const publishedPathIds = versionPaths[String(version)] ?? allPathIds;
      return {
        version,
        parts: restrictParts(exam.parts, publishedPathIds),
        publishedPathIds,
        servingPathIds: item.state === "SERVING" ? publishedPathIds : [],
      };
    });
  return { versions, publishedInSession: [] };
}

export const reviewStore = {
  getCheck: (examId: string): ExamCheckResult | undefined => checks.get(examId),
  setCheck(result: ExamCheckResult): void {
    checks.set(result.examId, result);
  },
  state(exam: ExamDetail): ExamReviewState {
    let state = states.get(exam.id);
    if (!state) {
      state = seedState(exam);
      states.set(exam.id, state);
    }
    return state;
  },
  setState(examId: string, state: ExamReviewState): void {
    states.set(examId, state);
  },
  fixtureChanges(examId: string): FixtureChange[] {
    return data.exams[examId]?.changes ?? [];
  },
  reset(): void {
    checks.clear();
    states.clear();
  },
};
