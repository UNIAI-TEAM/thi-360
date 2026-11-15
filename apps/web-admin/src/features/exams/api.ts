import referenceFixture from "@/features/content/mock/reference.json";
import type { ContentReference, KnowledgeNode } from "@/features/content/types";
import { MOCK_CURRENT_AUTHOR, questionStore } from "@/features/questions/mock/store";
import type { QuestionDetail } from "@/features/questions/types";
import { mockId, mockRequest, MockApiError, paginate, type MockPage } from "@/lib/mock/content-mock";
import { combinationsForKind, initialParts } from "./create-exam-schema";
import { splitEvenly, sum } from "./matrix";
import examsFixture from "./mock/exams.json";
import { examStore, MOCK_EXAM_TEMPLATES } from "./mock/store";
import type {
  AvailableQuestionCounts,
  AvailableQuestionsQuery,
  CreateExamPartInput,
  CreateExamRequest,
  DuplicateExamRequest,
  ExamDetail,
  ExamFormat,
  ExamKind,
  ExamPart,
  ExamPartOption,
  ExamSection,
  ExamStructure,
  ExamSummary,
  ExamTemplate,
  ListExamsQuery,
  QuestionBlock,
  SuggestExamCodeQuery,
  UpdateExamInfoRequest,
  UpdateExamStructureRequest,
} from "./types";

/**
 * API của Bài thi (danh sách, chi tiết, mẫu cấu trúc, tạo, sửa thông tin, sửa cấu trúc, sao chép, ngừng bán, đếm nguồn).
 * Hiện trả dữ liệu mẫu từ `mock/store.ts`. Khi nối backend: thay phần thân từng hàm bằng `createBrowserApiClient()`,
 * giữ nguyên chữ ký. Kiểm tra, xem trước, phiên bản và công bố nằm ở `review-api.ts`.
 *
 * Lỗi nghiệp vụ (giống backend): 409 `EXAM_CODE_TAKEN`, 409 `EXAM_SCOPE_LOCKED` (đổi kỳ thi/hình thức của bài đã công
 * bố), 409 `EXAM_NOT_PUBLISHED` (ngừng bán bài chưa phục vụ), 422 `EXAM_TEMPLATE_MISMATCH`, 422 `EXAM_SUBJECT_REQUIRED`,
 * 422 `EXAM_STRUCTURE_INVALID` (cấu trúc khởi tạo sai: số câu, thời lượng, tổng số câu các môn của tổ hợp).
 */

export type PageResponseExamSummary = MockPage<ExamSummary>;

export const EXAMS_QUERY_KEYS = {
  all: ["exams"] as const,
  lists: () => ["exams", "list"] as const,
  list: (query: ListExamsQuery) => ["exams", "list", query] as const,
  detail: (id: string) => ["exams", "detail", id] as const,
  structure: (id: string) => ["exams", "structure", id] as const,
  templates: () => ["exams", "templates"] as const,
  suggestedCode: (query: SuggestExamCodeQuery) => ["exams", "suggested-code", query] as const,
  available: (query: AvailableQuestionsQuery) => ["exams", "available-questions", query] as const,
};

const SUMMARY_KEYS = [
  "id", "code", "name", "kind", "format", "status", "servingVersion", "hasUnpublishedChanges", "totalQuestions",
  "durationMinutes", "englishOption", "combinationCount", "singleSubjectCode", "check", "attemptCount", "updatedAt",
  "updatedBy",
] as const satisfies readonly (keyof ExamSummary)[];

export function toExamSummary(exam: ExamDetail): ExamSummary {
  return Object.fromEntries(SUMMARY_KEYS.map((key) => [key, exam[key]])) as unknown as ExamSummary;
}

function matches(exam: ExamDetail, query: ListExamsQuery): boolean {
  const text = query.q?.trim().toLocaleLowerCase("vi");
  if (text && !exam.name.toLocaleLowerCase("vi").includes(text) && !exam.code.toLocaleLowerCase("vi").includes(text)) {
    return false;
  }
  if (query.kind && exam.kind !== query.kind) return false;
  if (query.format && exam.format !== query.format) return false;
  if (query.status && exam.status !== query.status) return false;
  if (query.checkStatus && exam.check.status !== query.checkStatus) return false;
  if (query.hasUnpublishedChanges && !exam.hasUnpublishedChanges) return false;
  return true;
}

export async function listExams(query: ListExamsQuery): Promise<PageResponseExamSummary> {
  return mockRequest(
    () => {
      const rows = examStore
        .all()
        .filter((exam) => matches(exam, query))
        .map(toExamSummary)
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
      return paginate(rows, query.page ?? 0, query.size ?? 20);
    },
    { empty: () => paginate<ExamSummary>([], 0, query.size ?? 20) },
  );
}

export async function getExam(id: string): Promise<ExamDetail> {
  return mockRequest(() => examStore.require(id));
}

export async function listExamTemplates(): Promise<ExamTemplate[]> {
  return mockRequest(() => [...MOCK_EXAM_TEMPLATES], { ignoreScenario: true });
}

/** Bản làm việc của cấu trúc. `?mockState=empty` trả cấu trúc rỗng để xem trạng thái chưa có phần thi. */
export async function getExamStructure(id: string): Promise<ExamStructure> {
  return mockRequest(
    () => {
      const exam = examStore.require(id);
      return { examId: exam.id, parts: exam.parts };
    },
    { empty: () => ({ examId: id, parts: [] }) },
  );
}

// ---------------------------------------------------------------- dữ liệu mẫu cho phần sinh cấu trúc

const reference = referenceFixture as ContentReference;

const ENGLISH = "ENGLISH";

function subjectName(code: string): string {
  return reference.subjects.find((subject) => subject.code === code)?.name ?? code;
}

function emptySections(subjectCodes: readonly string[], questionCount: number): ExamSection[] {
  const counts = splitEvenly(questionCount, subjectCodes.length);
  return subjectCodes.map((subjectCode, index) => ({
    id: mockId("sec"),
    subjectCode,
    questionCount: counts[index] ?? 0,
    blocks: [],
  }));
}

/** Một lựa chọn của phần tự chọn: một môn (Tiếng Anh) hoặc một tổ hợp, số câu chia đều theo phần dư lớn nhất. */
function buildPartOption(code: string, questionCount: number): ExamPartOption {
  const combination = reference.combinations.find((item) => item.code === code);
  return {
    id: mockId("opt"),
    kind: combination ? "COMBINATION" : "SUBJECT",
    code,
    sections: emptySections(combination ? combination.subjectCodes : [code], questionCount),
  };
}

/** Cấu trúc khởi tạo người tạo đã chỉnh ở trang Tạo bài thi: phần cố định một môn, phần tự chọn Tiếng Anh và tổ hợp. */
function partsFromInput(inputs: readonly CreateExamPartInput[]): ExamPart[] {
  return inputs.map((input, index) => {
    const choice = input.mode === "CHOICE";
    const options: ExamPartOption[] = choice
      ? [
          ...(input.englishOption ? [buildPartOption(ENGLISH, input.questionCount)] : []),
          ...input.combinations.map((item) => {
            const option = buildPartOption(item.code, input.questionCount);
            return {
              ...option,
              sections: option.sections.map((section, sectionIndex) => ({
                ...section,
                questionCount: item.subjectCounts[sectionIndex] ?? section.questionCount,
              })),
            };
          }),
        ]
      : [];
    return {
      id: mockId("part"),
      order: index + 1,
      name: input.name.trim(),
      durationMinutes: input.durationMinutes,
      questionCount: input.questionCount,
      mode: input.mode,
      sections: choice ? [] : emptySections([input.subjectCode ?? ""], input.questionCount),
      options,
    };
  });
}

/** Kiểm tra cấu trúc khởi tạo như backend: số câu, thời lượng dương; tổ hợp đủ số câu; phần tự chọn có lựa chọn. */
function assertInputParts(inputs: readonly CreateExamPartInput[]): void {
  const positive = (value: number) => Number.isInteger(value) && value > 0;
  const invalid =
    inputs.length === 0 ||
    inputs.some((input) => {
      if (!input.name.trim() || !positive(input.questionCount) || !positive(input.durationMinutes)) return true;
      if (input.mode === "FIXED") return !input.subjectCode;
      if (!input.englishOption && input.combinations.length === 0) return true;
      return input.combinations.some((item) => {
        const combination = reference.combinations.find((entry) => entry.code === item.code);
        return (
          !combination ||
          combination.subjectCodes.length !== item.subjectCounts.length ||
          !item.subjectCounts.every(positive) ||
          sum(item.subjectCounts) !== input.questionCount
        );
      });
    });
  if (invalid) throw new MockApiError(422, "EXAM_STRUCTURE_INVALID");
}

/** Tên phần/môn của bài lẻ (Toán, Văn, Khoa học, Tiếng Anh) trong dữ liệu mẫu. */
const SINGLE_SCOPE_NAMES = (examsFixture as { singleScopeNames: Record<string, string> }).singleScopeNames;

/**
 * Cấu trúc nạp theo mẫu khi không có cấu trúc khởi tạo kèm theo (đổi phạm vi ở Thông tin chung): cùng cách dựng với
 * trang Tạo bài thi.
 */
function buildParts(request: { kind: ExamKind; format: ExamFormat; singleSubjectCode: string | null }): ExamPart[] {
  const inputs = initialParts({
    templates: MOCK_EXAM_TEMPLATES,
    kind: request.kind,
    format: request.format,
    singleScope: request.singleSubjectCode ?? "",
    combinations: combinationsForKind(reference.combinations, request.kind),
    scopeName: (scope) => SINGLE_SCOPE_NAMES[scope] ?? subjectName(scope),
  });
  return partsFromInput(
    inputs.map((part) => ({
      name: part.name,
      questionCount: part.questionCount,
      durationMinutes: part.durationMinutes,
      mode: part.mode,
      subjectCode: part.mode === "FIXED" ? part.subjectCode : null,
      englishOption: part.englishOption,
      combinations: part.combinations,
    })),
  );
}

/** Số liệu tóm tắt của bài tính lại từ cấu trúc (số câu, thời lượng, phần tự chọn). */
function summaryFromParts(parts: readonly ExamPart[]) {
  const options = parts.filter((part) => part.mode === "CHOICE").flatMap((part) => part.options);
  return {
    totalQuestions: sum(parts.map((part) => part.questionCount)),
    durationMinutes: sum(parts.map((part) => part.durationMinutes)),
    englishOption: options.some((option) => option.kind === "SUBJECT" && option.code === ENGLISH),
    combinationCount: options.filter((option) => option.kind === "COMBINATION").length,
  };
}

function nowIso(): string {
  return new Date().toISOString();
}

/** Sửa sau công bố tạo bản làm việc (UniNote PBI-04 mục 11.2): đánh dấu chưa công bố và thêm dòng WORKING nếu chưa có. */
function markWorking(exam: ExamDetail): Pick<ExamDetail, "hasUnpublishedChanges" | "versions" | "updatedAt" | "updatedBy"> {
  const hasWorking = exam.versions.some((version) => version.state === "WORKING");
  return {
    hasUnpublishedChanges: true,
    versions: hasWorking
      ? exam.versions
      : [{ version: null, state: "WORKING", publishedAt: null, publishedBy: null, attemptCount: 0, note: null }, ...exam.versions],
    updatedAt: nowIso(),
    updatedBy: MOCK_CURRENT_AUTHOR,
  };
}

/** Đã kiểm tra rồi mà cấu trúc đổi thì cần kiểm tra lại (mục 11.3). */
function recheck(exam: ExamDetail): ExamDetail["check"] {
  return exam.check.status === "UNCHECKED" ? exam.check : { ...exam.check, status: "NEEDS_RECHECK" };
}

function normalizeCode(code: string): string {
  return code.trim().toUpperCase();
}

function assertCodeFree(code: string, exceptId?: string): void {
  const taken = examStore.all().some((exam) => exam.id !== exceptId && exam.code.toUpperCase() === code);
  if (taken) throw new MockApiError(409, "EXAM_CODE_TAKEN");
}

function assertScope(request: { kind: ExamKind; format: ExamFormat; templateCode: string | null; singleSubjectCode: string | null }) {
  if (request.format === "SINGLE" && !request.singleSubjectCode) throw new MockApiError(422, "EXAM_SUBJECT_REQUIRED");
  if (request.templateCode) {
    const template = MOCK_EXAM_TEMPLATES.find((item) => item.code === request.templateCode);
    if (!template || template.kind !== request.kind || template.format !== request.format) {
      throw new MockApiError(422, "EXAM_TEMPLATE_MISMATCH");
    }
  } else if (request.format === "FULL") {
    throw new MockApiError(422, "EXAM_TEMPLATE_MISMATCH");
  }
}

// ---------------------------------------------------------------- ghi

/** Đoạn môn trong mã bài lẻ (bản vẽ TaoBaiThi: `HSA-L-TOAN-002`). */
const SINGLE_SCOPE_SLUGS: Record<string, string> = { MATH: "TOAN", LITERATURE: "VAN", SCIENCE: "KH", ENGLISH: "TA" };

/** Mã gợi ý `HSA-F-003`, `HSA-L-TOAN-002`: tiền tố theo kỳ thi, hình thức, môn bài lẻ; số tiếp theo chưa dùng. */
export async function suggestExamCode(query: SuggestExamCodeQuery): Promise<string> {
  return mockRequest(
    () => {
      const slug = query.format === "SINGLE" && query.singleScope ? SINGLE_SCOPE_SLUGS[query.singleScope] : undefined;
      const prefix = `${query.kind}-${query.format === "FULL" ? "F" : "L"}-${slug ? `${slug}-` : ""}`;
      const used = new Set(examStore.all().map((exam) => exam.code.toUpperCase()));
      let next = 1;
      while (used.has(`${prefix}${String(next).padStart(3, "0")}`)) next += 1;
      return `${prefix}${String(next).padStart(3, "0")}`;
    },
    { ignoreScenario: true },
  );
}

/** Tạo bài thi Nháp, nạp cấu trúc theo mẫu (khối câu hỏi để trống). */
export async function createExam(request: CreateExamRequest): Promise<ExamDetail> {
  return mockRequest(
    () => {
      const code = normalizeCode(request.code);
      assertCodeFree(code);
      assertScope(request);
      if (request.parts) assertInputParts(request.parts);
      const parts = request.parts ? partsFromInput(request.parts) : buildParts(request);
      const exam: ExamDetail = {
        id: mockId("ex"),
        code,
        name: request.name.trim(),
        kind: request.kind,
        format: request.format,
        status: "DRAFT",
        servingVersion: null,
        hasUnpublishedChanges: true,
        ...summaryFromParts(parts),
        singleSubjectCode: request.format === "SINGLE" ? request.singleSubjectCode : null,
        check: { status: "UNCHECKED", issueCount: 0, checkedAt: null },
        attemptCount: 0,
        updatedAt: nowIso(),
        updatedBy: MOCK_CURRENT_AUTHOR,
        description: request.description?.trim() || null,
        templateCode: request.templateCode,
        parts,
        versions: [{ version: null, state: "WORKING", publishedAt: null, publishedBy: null, attemptCount: 0, note: null }],
      };
      return examStore.insert(exam);
    },
    { ignoreScenario: true },
  );
}

/**
 * Sửa Thông tin chung. Đổi kỳ thi, hình thức, mẫu hoặc môn của bài lẻ thì nạp lại cấu trúc theo mẫu (giao diện hỏi
 * xác nhận trước); chỉ làm được khi bài chưa công bố lần nào.
 */
export async function updateExamInfo(id: string, request: UpdateExamInfoRequest): Promise<ExamDetail> {
  return mockRequest(
    () => {
      const current = examStore.require(id);
      const code = normalizeCode(request.code);
      assertCodeFree(code, id);
      const scopeChanged =
        current.kind !== request.kind ||
        current.format !== request.format ||
        current.templateCode !== request.templateCode ||
        (request.format === "SINGLE" && current.singleSubjectCode !== request.singleSubjectCode);
      if (scopeChanged) {
        if (current.servingVersion !== null) throw new MockApiError(409, "EXAM_SCOPE_LOCKED");
        assertScope(request);
      }
      return examStore.update(id, (exam) => {
        const parts = scopeChanged ? buildParts(request) : exam.parts;
        return {
          ...exam,
          ...markWorking(exam),
          code,
          name: request.name.trim(),
          description: request.description?.trim() || null,
          kind: request.kind,
          format: request.format,
          templateCode: request.templateCode,
          singleSubjectCode: request.format === "SINGLE" ? request.singleSubjectCode : null,
          parts,
          ...summaryFromParts(parts),
          check: scopeChanged ? recheck(exam) : exam.check,
        };
      });
    },
    { ignoreScenario: true },
  );
}

/** Lưu bản làm việc của cấu trúc: đánh dấu có bản sửa chưa công bố, kết quả kiểm tra cũ thành "Cần kiểm tra lại". */
export async function updateExamStructure(id: string, request: UpdateExamStructureRequest): Promise<ExamDetail> {
  return mockRequest(
    () =>
      examStore.update(id, (exam) => ({
        ...exam,
        ...markWorking(exam),
        parts: structuredClone(request.parts),
        ...summaryFromParts(request.parts),
        check: recheck(exam),
      })),
    { ignoreScenario: true },
  );
}

function cloneWithNewIds(parts: readonly ExamPart[]): ExamPart[] {
  const cloneBlocks = (blocks: readonly QuestionBlock[]) => blocks.map((block) => ({ ...structuredClone(block), id: mockId("blk") }));
  const cloneSections = (sections: readonly ExamSection[]) =>
    sections.map((section) => ({ ...section, id: mockId("sec"), blocks: cloneBlocks(section.blocks) }));
  return parts.map((part) => ({
    ...part,
    id: mockId("part"),
    sections: cloneSections(part.sections),
    options: part.options.map((option) => ({ ...option, id: mockId("opt"), sections: cloneSections(option.sections) })),
  }));
}

/** Sao chép thành bài Nháp mới (mã tự sinh `<mã>-COPY`, `<mã>-COPY-2`…), giữ cấu trúc và ma trận. */
export async function duplicateExam(id: string, request: DuplicateExamRequest): Promise<ExamDetail> {
  return mockRequest(
    () => {
      const source = examStore.require(id);
      const used = new Set(examStore.all().map((exam) => exam.code.toUpperCase()));
      let code = `${source.code}-COPY`;
      for (let index = 2; used.has(code); index += 1) code = `${source.code}-COPY-${index}`;
      const exam: ExamDetail = {
        ...structuredClone(source),
        id: mockId("ex"),
        code,
        name: request.name.trim(),
        status: "DRAFT",
        servingVersion: null,
        hasUnpublishedChanges: true,
        check: { status: "UNCHECKED", issueCount: 0, checkedAt: null },
        attemptCount: 0,
        updatedAt: nowIso(),
        updatedBy: MOCK_CURRENT_AUTHOR,
        parts: cloneWithNewIds(source.parts),
        versions: [{ version: null, state: "WORKING", publishedAt: null, publishedBy: null, attemptCount: 0, note: null }],
      };
      return examStore.insert(exam);
    },
    { ignoreScenario: true },
  );
}

/** Ngừng bán: không bán thêm, học viên đã mua vẫn làm được phiên bản đang phục vụ. */
export async function discontinueExam(id: string): Promise<ExamDetail> {
  return mockRequest(
    () => {
      const current = examStore.require(id);
      if (current.status !== "PUBLISHED") throw new MockApiError(409, "EXAM_NOT_PUBLISHED");
      return examStore.update(id, (exam) => ({ ...exam, status: "DISCONTINUED", updatedAt: nowIso(), updatedBy: MOCK_CURRENT_AUTHOR }));
    },
    { ignoreScenario: true },
  );
}

// ---------------------------------------------------------------- nguồn câu hỏi

function indexDescendants(nodes: readonly KnowledgeNode[]): Map<string, Set<string>> {
  const index = new Map<string, Set<string>>();
  const visit = (node: KnowledgeNode): Set<string> => {
    const ids = new Set<string>([node.id]);
    for (const child of node.children) for (const id of visit(child)) ids.add(id);
    index.set(node.id, ids);
    return ids;
  };
  nodes.forEach(visit);
  return index;
}

let descendantsIndex: Map<string, Set<string>> | null = null;

function matchesScope(question: QuestionDetail, scope: AvailableQuestionsQuery["knowledgeScope"]): boolean {
  if (scope.nodeIds.length === 0) return true;
  descendantsIndex ??= indexDescendants(reference.knowledgeTree);
  const attached = new Set(question.knowledge.map((ref) => ref.nodeId));
  const covers = (nodeId: string) => {
    const targets = scope.includeDescendants ? (descendantsIndex?.get(nodeId) ?? new Set([nodeId])) : new Set([nodeId]);
    for (const id of attached) if (targets.has(id)) return true;
    return false;
  };
  return scope.match === "ALL" ? scope.nodeIds.every(covers) : scope.nodeIds.some(covers);
}

/**
 * Đếm nguồn Sẵn sàng khớp bộ lọc của khối (môn, dạng câu, phạm vi kiến thức), theo từng ô Lớp × Độ khó. Câu chùm đếm
 * theo câu con. Chỉ là gợi ý từng ô; kết luận đủ nguồn cho cả đề thuộc bước Kiểm tra cấu hình.
 */
export async function countAvailableQuestions(query: AvailableQuestionsQuery): Promise<AvailableQuestionCounts> {
  return mockRequest(
    () => {
      const counted = new Map<string, number>();
      let total = 0;
      for (const question of questionStore.all()) {
        if (question.status !== "READY" || question.subjectCode !== query.subjectCode) continue;
        if (question.type !== query.questionType || !matchesScope(question, query.knowledgeScope)) continue;
        const units = question.type === "QUESTION_SET" && question.children.length ? question.children : [question];
        for (const unit of units) {
          const key = `${unit.gradeCode}|${unit.difficultyCode}`;
          counted.set(key, (counted.get(key) ?? 0) + 1);
          total += 1;
        }
      }
      return {
        total,
        cells: [...counted].map(([key, value]) => {
          const [gradeCode = "", difficultyCode = ""] = key.split("|");
          return { gradeCode, difficultyCode, value };
        }),
      };
    },
    { empty: () => ({ total: 0, cells: [] }) },
  );
}

export interface ExamsApiDeps {
  listExams: typeof listExams;
  getExam: typeof getExam;
  listExamTemplates: typeof listExamTemplates;
  getExamStructure: typeof getExamStructure;
  suggestExamCode: typeof suggestExamCode;
  createExam: typeof createExam;
  updateExamInfo: typeof updateExamInfo;
  updateExamStructure: typeof updateExamStructure;
  duplicateExam: typeof duplicateExam;
  discontinueExam: typeof discontinueExam;
  countAvailableQuestions: typeof countAvailableQuestions;
}

export const defaultExamsApiDeps: ExamsApiDeps = {
  listExams,
  getExam,
  listExamTemplates,
  getExamStructure,
  suggestExamCode,
  createExam,
  updateExamInfo,
  updateExamStructure,
  duplicateExam,
  discontinueExam,
  countAvailableQuestions,
};

