import { examStore } from "@/features/exams/mock/store";
import { createMockStore, MockApiError, mockId, mockRequest } from "@/lib/mock/content-mock";
import { assertQuestionRevision } from "./api.mock";
import { isChildQuestionType } from "./editor-types";
import type {
  QuestionSnapshot,
  QuestionUsage,
  QuestionVersionList,
  RestoreQuestionVersionRequest,
  SaveChildQuestionRequest,
  SaveQuestionRequest,
  UpdateQuestionRequest,
  UpdateQuestionResponse,
} from "./editor-types";
import { attemptsPerVersion, deriveQuestionUsage } from "./editor-usage";
import { deriveVersionStates, editCreatesNewVersion, toSnapshot, type QuestionHistoryEntry } from "./editor-versioning";
import { MOCK_CURRENT_AUTHOR, questionStore } from "./mock/store";
import versionsFixture from "./mock/versions.json";
import type { QuestionDetail } from "./types";

/**
 * API soạn câu hỏi, nhân bản, lịch sử phiên bản và nơi đang dùng (UNI-2593). Hiện ghi vào kho mẫu `questionStore`.
 * Khi nối backend: thay phần thân từng hàm bằng `createBrowserApiClient()` (POST/PUT `/questions`,
 * POST `/questions/{id}/duplicate`, GET `/questions/{id}/versions`, GET `/questions/{id}/usage`,
 * POST `/questions/{id}/versions/{version}/restore`), giữ nguyên chữ ký; hook và giao diện không đổi.
 */

interface QuestionHistory {
  id: string;
  entries: QuestionHistoryEntry[];
}

interface HistorySeed {
  questionId: string;
  entries: Array<Omit<QuestionHistoryEntry, "snapshot"> & { patch: Partial<QuestionSnapshot> }>;
}

const historySeeds = (versionsFixture as { histories: HistorySeed[] }).histories;

/** Lịch sử phiên bản trong phiên; dựng lần đầu khi đọc hoặc ghi một câu. */
const historyStore = createMockStore<QuestionHistory>([]);

function synthesizeHistory(question: QuestionDetail): QuestionHistoryEntry[] {
  const seed = historySeeds.find((entry) => entry.questionId === question.id);
  const current = toSnapshot(question);
  if (seed) {
    return seed.entries.map(({ patch, ...meta }) => ({ ...meta, snapshot: { ...structuredClone(current), ...patch } }));
  }
  // Câu không có lịch sử mẫu: mỗi phiên bản cùng nội dung hiện tại, ngày lưu rải từ lúc tạo tới lần sửa cuối.
  const created = Date.parse(question.createdAt);
  const updated = Date.parse(question.updatedAt);
  return Array.from({ length: question.version }, (_, index) => {
    const version = index + 1;
    const at = question.version === 1 ? updated : created + ((updated - created) * index) / (question.version - 1);
    const latest = version === question.version;
    return {
      version,
      savedAt: new Date(at).toISOString(),
      savedBy: question.createdBy,
      readyAt: latest && question.status === "DRAFT" ? null : new Date(at + 3_600_000).toISOString(),
      snapshot: structuredClone(current),
    };
  });
}

function historyOf(question: QuestionDetail): QuestionHistory {
  return historyStore.get(question.id) ?? historyStore.insert({ id: question.id, entries: synthesizeHistory(question) });
}

/** Phiên bản đã từng Sẵn sàng (dùng được cho lượt thi), mới trước. */
function readyVersions(question: QuestionDetail, history: QuestionHistory): number[] {
  return history.entries
    .filter((entry) => entry.readyAt !== null || (entry.version === question.version && question.status !== "DRAFT"))
    .map((entry) => entry.version)
    .sort((a, b) => b - a);
}

function usageOf(question: QuestionDetail): QuestionUsage {
  return deriveQuestionUsage(question, examStore.all(), readyVersions(question, historyOf(question)));
}

function nextQuestionCode(): string {
  const max = questionStore
    .all()
    .reduce((highest, question) => Math.max(highest, Number(/^CH-(\d+)$/.exec(question.code)?.[1] ?? 0)), 0);
  return `CH-${String(max + 1).padStart(6, "0")}`;
}

function previewOf(content: string): string {
  const text = content.replace(/\s+/g, " ").trim();
  return text.length > 160 ? `${text.slice(0, 159)}…` : text;
}

function assertSaveRequest(request: SaveQuestionRequest): void {
  if (request.body.type !== request.type) throw new MockApiError(422, "QUESTION_BODY_TYPE_MISMATCH");
  if (request.type === "QUESTION_SET" && request.children.length < 2) {
    throw new MockApiError(422, "QUESTION_SET_CHILDREN_MIN");
  }
  // D19: Bộ câu hỏi và Điền khuyết không làm câu con.
  if (request.children.some((child) => !isChildQuestionType(child.type))) {
    throw new MockApiError(422, "QUESTION_BODY_INVALID");
  }
}

/** Dựng các trường nội dung của câu từ yêu cầu; câu con thừa hưởng phân loại của câu cha. */
function applyRequest(
  base: QuestionDetail,
  request: SaveQuestionRequest,
  now: string,
): QuestionDetail {
  const buildChild = (child: SaveChildQuestionRequest, index: number): QuestionDetail => {
    const id = child.id ?? mockId(`${base.id}-c`);
    const previous = base.children.find((existing) => existing.id === child.id);
    return {
      ...(previous ?? base),
      id,
      code: `${base.code}-${index + 1}`,
      type: child.type,
      status: base.status,
      contentPreview: previewOf(child.content),
      subjectCode: request.subjectCode,
      gradeCode: child.gradeCode ?? request.gradeCode,
      difficultyCode: child.difficultyCode ?? request.difficultyCode,
      competencyCode: request.competencyCode ?? null,
      taskTypeCode: request.taskTypeCode ?? null,
      points: child.points,
      knowledge: structuredClone(request.knowledge),
      tagCodes: [...request.tagCodes],
      hasSolution: Boolean(child.solution),
      version: base.version,
      revision: base.revision,
      content: child.content,
      body: structuredClone(child.body),
      solution: child.solution,
      commonMistakes: null,
      attachmentIds: [],
      children: [],
      createdAt: previous?.createdAt ?? now,
      updatedAt: now,
    };
  };
  const children = request.type === "QUESTION_SET" ? request.children.map(buildChild) : [];
  const body =
    request.body.type === "QUESTION_SET"
      ? { type: "QUESTION_SET" as const, passage: request.content, childIds: children.map((child) => child.id) }
      : structuredClone(request.body);
  const points =
    request.type === "QUESTION_SET"
      ? children.reduce((sum, child) => sum + child.points, 0)
      : request.body.type === "CLOZE"
        ? request.body.blanks.reduce((sum, blank) => sum + blank.points, 0)
        : request.points;
  return {
    ...base,
    type: request.type,
    contentPreview: previewOf(request.content),
    subjectCode: request.subjectCode,
    gradeCode: request.gradeCode,
    difficultyCode: request.difficultyCode,
    competencyCode: request.competencyCode ?? null,
    taskTypeCode: request.taskTypeCode ?? null,
    points,
    knowledge: structuredClone(request.knowledge),
    tagCodes: [...request.tagCodes],
    hasSolution: Boolean(request.solution) || children.some((child) => child.hasSolution),
    content: request.content,
    body,
    solution: request.solution,
    commonMistakes: request.commonMistakes,
    attachmentIds: [...request.attachmentIds],
    children,
    revision: base.revision + 1,
    updatedAt: now,
  };
}

/** Tạo câu hỏi mới, luôn ở trạng thái Nháp, phiên bản 1. */
export async function createQuestion(request: SaveQuestionRequest): Promise<QuestionDetail> {
  return mockRequest(
    () => {
      assertSaveRequest(request);
      const now = new Date().toISOString();
      const id = mockId("q");
      const blankQuestion: QuestionDetail = {
        id,
        code: nextQuestionCode(),
        type: request.type,
        status: "DRAFT",
        contentPreview: "",
        subjectCode: "",
        gradeCode: "",
        difficultyCode: "",
        taskTypeCode: null,
        points: 0,
        knowledge: [],
        tagCodes: [],
        hasSolution: false,
        version: 1,
        revision: 0,
        usedInExamCount: 0,
        sourceCode: null,
        createdBy: MOCK_CURRENT_AUTHOR,
        createdAt: now,
        updatedAt: now,
        content: "",
        body: request.body,
        solution: null,
        commonMistakes: null,
        attachmentIds: [],
        children: [],
      };
      const question = questionStore.insert(applyRequest(blankQuestion, request, now));
      historyStore.insert({
        id,
        entries: [{ version: 1, savedAt: now, savedBy: MOCK_CURRENT_AUTHOR, readyAt: null, snapshot: toSnapshot(question) }],
      });
      return question;
    },
    { ignoreScenario: true },
  );
}

/**
 * Sửa câu hỏi. Câu Nháp sửa tại chỗ; câu Sẵn sàng hoặc Ngừng sử dụng tạo phiên bản mới ở trạng thái Nháp
 * (UniNote PBI-03 mục 7.3), phiên bản Sẵn sàng trước đó vẫn phục vụ lượt thi mới.
 */
export async function updateQuestion(id: string, request: UpdateQuestionRequest): Promise<UpdateQuestionResponse> {
  return mockRequest(
    () => {
      const current = questionStore.require(id);
      assertQuestionRevision(current, request.expectedRevision);
      if (request.type !== current.type) throw new MockApiError(422, "QUESTION_TYPE_IMMUTABLE");
      assertSaveRequest(request);
      const history = historyOf(current);
      const now = new Date().toISOString();
      const versionCreated = editCreatesNewVersion(current);
      const nextVersion = versionCreated ? current.version + 1 : current.version;
      const question = questionStore.update(id, (existing) => ({
        ...applyRequest({ ...existing, version: nextVersion, status: "DRAFT" }, request, now),
      }));
      const entry: QuestionHistoryEntry = {
        version: nextVersion,
        savedAt: now,
        savedBy: MOCK_CURRENT_AUTHOR,
        readyAt: null,
        snapshot: toSnapshot(question),
      };
      historyStore.update(id, (existing) => ({
        ...existing,
        entries: versionCreated
          ? [
              ...history.entries.map((item) =>
                item.version === current.version && current.status === "READY" && item.readyAt === null
                  ? { ...item, readyAt: current.updatedAt }
                  : item,
              ),
              entry,
            ]
          : history.entries.map((item) => (item.version === nextVersion ? entry : item)),
      }));
      return { question, versionCreated };
    },
    { ignoreScenario: true },
  );
}

/** Nhân bản thành câu mới ở trạng thái Nháp, phiên bản 1, chưa dùng ở bài thi nào. */
export async function duplicateQuestion(id: string): Promise<QuestionDetail> {
  return mockRequest(
    () => {
      const source = questionStore.require(id);
      const now = new Date().toISOString();
      const newId = mockId("q");
      const code = nextQuestionCode();
      const children = source.children.map((child, index) => ({
        ...structuredClone(child),
        id: `${newId}-c${index + 1}`,
        code: `${code}-${index + 1}`,
        status: "DRAFT" as const,
        version: 1,
        revision: 0,
        usedInExamCount: 0,
        createdBy: MOCK_CURRENT_AUTHOR,
        createdAt: now,
        updatedAt: now,
      }));
      const copy: QuestionDetail = {
        ...structuredClone(source),
        id: newId,
        code,
        status: "DRAFT",
        version: 1,
        revision: 0,
        usedInExamCount: 0,
        sourceCode: null,
        createdBy: MOCK_CURRENT_AUTHOR,
        createdAt: now,
        updatedAt: now,
        body:
          source.body.type === "QUESTION_SET"
            ? { ...source.body, childIds: children.map((child) => child.id) }
            : structuredClone(source.body),
        children,
      };
      const question = questionStore.insert(copy);
      historyStore.insert({
        id: newId,
        entries: [{ version: 1, savedAt: now, savedBy: MOCK_CURRENT_AUTHOR, readyAt: null, snapshot: toSnapshot(question) }],
      });
      return question;
    },
    { ignoreScenario: true },
  );
}

export async function listQuestionVersions(id: string): Promise<QuestionVersionList> {
  return mockRequest(() => {
    const question = questionStore.require(id);
    const history = historyOf(question);
    const states = deriveVersionStates(history.entries, question);
    const usage = usageOf(question);
    const attempts = attemptsPerVersion(usage);
    return {
      questionId: question.id,
      latestVersion: question.version,
      status: question.status,
      examCount: usage.examCount,
      attemptCount: usage.attemptCount,
      versions: [...history.entries]
        .sort((a, b) => b.version - a.version)
        .map((entry) => ({
          version: entry.version,
          state: states.get(entry.version) ?? "SUPERSEDED",
          savedAt: entry.savedAt,
          savedBy: entry.savedBy,
          attemptCount: attempts.get(entry.version) ?? 0,
          snapshot: entry.snapshot,
        })),
    };
  });
}

export async function getQuestionUsage(id: string): Promise<QuestionUsage> {
  return mockRequest(() => usageOf(questionStore.require(id)), {
    empty: () => ({ questionId: id, examCount: 0, attemptCount: 0, exams: [] }),
  });
}

/**
 * Chép nội dung một phiên bản cũ thành bản đang sửa: như một lần lưu (câu Nháp ghi đè bản nháp, câu Sẵn sàng tạo
 * phiên bản mới). Phiên bản cũ và lượt thi đã dùng nó giữ nguyên.
 */
export async function restoreQuestionVersion(
  id: string,
  request: RestoreQuestionVersionRequest,
): Promise<UpdateQuestionResponse> {
  const question = questionStore.require(id);
  const entry = historyOf(question).entries.find((item) => item.version === request.version);
  if (!entry) throw new MockApiError(404, "QUESTION_VERSION_NOT_FOUND");
  const { snapshot } = entry;
  return updateQuestion(id, {
    type: question.type,
    subjectCode: snapshot.subjectCode,
    gradeCode: snapshot.gradeCode,
    difficultyCode: snapshot.difficultyCode,
    competencyCode: snapshot.competencyCode ?? null,
    taskTypeCode: snapshot.taskTypeCode ?? null,
    points: snapshot.points,
    knowledge: snapshot.knowledge,
    tagCodes: snapshot.tagCodes,
    content: snapshot.content,
    solution: snapshot.solution,
    commonMistakes: snapshot.commonMistakes ?? null,
    attachmentIds: snapshot.attachmentIds ?? [],
    body: snapshot.body,
    children: snapshot.children.map((child) => ({
      id: child.id,
      type: isChildQuestionType(child.type) ? child.type : "SINGLE_CHOICE",
      content: child.content,
      body: child.body,
      solution: child.solution,
      points: child.points,
      gradeCode: child.gradeCode,
      difficultyCode: child.difficultyCode,
      knowledgeIds: child.knowledge.map((ref) => ref.nodeId),
    })),
    expectedRevision: request.expectedRevision,
  });
}

/** Chỉ dùng trong test: đưa lịch sử phiên bản về dữ liệu mẫu. */
export function resetQuestionHistoryForTests(): void {
  historyStore.reset();
}
