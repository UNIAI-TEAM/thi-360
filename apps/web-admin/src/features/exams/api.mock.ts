import referenceFixture from "@/features/content/mock/reference.json";
import type { ContentReference, ExamKindOption, KnowledgeNode } from "@/features/content/types";
import { MOCK_CURRENT_AUTHOR, questionStore } from "@/features/questions/mock/store";
import type { QuestionDetail } from "@/features/questions/types";
import { mockId, mockRequest, MockApiError, paginate, type MockPage } from "@/lib/mock/content-mock";
import {
  combinationsForKind,
  findTemplate,
  formatOfScope,
  initialParts,
  partInputsFromValues,
  yearsDiffer,
} from "./create-exam-schema";
import { splitEvenly, sum } from "./matrix";
import examsFixture from "./mock/exams.json";
import { toExamSummary } from "./exam-summary";
import { examStore, MOCK_EXAM_TEMPLATES } from "./mock/store";
import type {
  ApplyExamTemplateRequest,
  AvailableQuestionCounts,
  AvailableQuestionsQuery,
  CreateExamPartInput,
  CreateExamRequest,
  DuplicateExamRequest,
  ExamDetail,
  ExamKind,
  ExamPart,
  ExamPartOption,
  ExamScope,
  ExamSection,
  ExamStructure,
  ExamSummary,
  ExamTemplate,
  ExamTemplateBlock,
  ExamTiming,
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
 * bố), 409 `EXAM_NOT_PUBLISHED` (ngừng bán bài chưa phục vụ), 422 `EXAM_KIND_UNKNOWN`, 422 `EXAM_FORMAT_NOT_ALLOWED`
 * (hình thức không có trong `formats` của kỳ thi), 422 `EXAM_TEMPLATE_MISMATCH`, 422 `EXAM_SUBJECT_REQUIRED` (bài lẻ
 * chưa chọn phần/môn, kỳ thi theo môn chưa chọn môn), 422 `EXAM_MAX_SCORE_INVALID` (thang điểm không dương, hoặc để
 * trống khi kỳ thi có thang), 422 `EXAM_STRUCTURE_INVALID` (cấu trúc khởi tạo sai: số câu, thời lượng, điểm phần, tổng
 * số câu các môn, phần chung và lựa chọn; thời lượng chung không dương).
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

/** Lọc danh sách: chữ trong tên hoặc mã; kỳ thi; hình thức; môn của bài (môn theo mẫu hoặc môn của bài lẻ); trạng thái. */
function matches(exam: ExamDetail, query: ListExamsQuery): boolean {
  const text = query.q?.trim().toLocaleLowerCase("vi");
  if (text && !exam.name.toLocaleLowerCase("vi").includes(text) && !exam.code.toLocaleLowerCase("vi").includes(text)) {
    return false;
  }
  if (query.kind && exam.kind !== query.kind) return false;
  if (query.scope && exam.scope !== query.scope) return false;
  if (query.subjectCode && exam.subjectCode !== query.subjectCode && exam.partCode !== query.subjectCode) {
    return false;
  }
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
      return { examId: exam.id, revision: exam.revision, parts: exam.parts };
    },
    { empty: () => ({ examId: id, revision: 0, parts: [] }) },
  );
}

// ---------------------------------------------------------------- dữ liệu mẫu cho phần sinh cấu trúc

const reference = referenceFixture as ContentReference;

const ENGLISH = "ENGLISH";

function kindOption(kind: ExamKind): ExamKindOption | undefined {
  return reference.examKinds.find((option) => option.code === kind);
}

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

/**
 * Khối câu hỏi từ khối gợi ý của mẫu: dạng câu, số câu, điểm mỗi câu, câu chùm, năng lực (lọc nguồn theo năng lực)
 * và quy tắc chấm mong đợi. Phạm vi kiến thức và ma trận để trống, soạn ở Cấu trúc và ma trận.
 */
function blockFromTemplate(block: ExamTemplateBlock): QuestionBlock {
  return {
    id: mockId("blk"),
    name: block.name,
    questionType: block.questionType,
    questionCount: block.questionCount,
    setCount: block.setCount ?? null,
    setSize: block.setSize ?? null,
    pointsPerQuestion: block.pointsPerQuestion,
    knowledgeScope: {
      nodeIds: [],
      match: "ANY",
      includeDescendants: false,
      ...(block.competencyCode ? { competencyCodes: [block.competencyCode] } : {}),
    },
    tagCodes: [],
    matrix: { mode: "COMBINED", unit: "COUNT", cells: [], byGrade: [], byDifficulty: [] },
    shuffle: "SHUFFLE",
    ...(block.expectedScoring ? { expectedScoring: structuredClone(block.expectedScoring) } : {}),
  };
}

function blocksFromTemplate(blocks: readonly ExamTemplateBlock[] | undefined): QuestionBlock[] {
  return (blocks ?? []).map(blockFromTemplate);
}

/**
 * Một lựa chọn của phần tự chọn: một môn (Tiếng Anh) hoặc một tổ hợp, số câu chia đều theo phần dư lớn nhất. Khối
 * gợi ý của mẫu (tổ hợp một môn CA1–CA4) gắn vào môn đầu tiên; `expertConfirmed` giữ theo mẫu.
 */
function buildPartOption(
  code: string,
  questionCount: number,
  extra: { blocks?: readonly ExamTemplateBlock[]; expertConfirmed?: boolean } = {},
): ExamPartOption {
  const combination = reference.combinations.find((item) => item.code === code);
  const sections = emptySections(combination ? combination.subjectCodes : [code], questionCount).map((section, index) =>
    index === 0 ? { ...section, blocks: blocksFromTemplate(extra.blocks) } : section,
  );
  return {
    id: mockId("opt"),
    kind: combination ? "COMBINATION" : "SUBJECT",
    code,
    sections,
    ...(extra.expertConfirmed !== undefined ? { expertConfirmed: extra.expertConfirmed } : {}),
  };
}

/**
 * Cấu trúc khởi tạo người tạo đã chỉnh ở trang Tạo bài thi: phần cố định một môn (kèm khối gợi ý) hoặc nhiều môn;
 * phần tự chọn gồm phần chung (nếu có) cộng một lựa chọn: Tiếng Anh, tổ hợp, nhánh nội dung. Môn của nhánh là môn của
 * phần chung, không có thì môn của bài.
 */
function partsFromInput(inputs: readonly CreateExamPartInput[], examSubjectCode: string | null = null): ExamPart[] {
  return inputs.map((input, index) => {
    const choice = input.mode === "CHOICE";
    const common: ExamSection[] = (input.sections ?? []).map((section) => ({
      id: mockId("sec"),
      subjectCode: section.subjectCode,
      questionCount: section.questionCount,
      blocks: blocksFromTemplate(section.blocks),
    }));
    const target = input.questionCount - sum(common.map((section) => section.questionCount));
    const branchSubject = common[0]?.subjectCode ?? examSubjectCode ?? "";
    const options: ExamPartOption[] = choice
      ? [
          ...(input.englishOption ? [buildPartOption(ENGLISH, target)] : []),
          ...input.combinations.map((item) => {
            const option = buildPartOption(item.code, target, item);
            return {
              ...option,
              sections: option.sections.map((section, sectionIndex) => ({
                ...section,
                questionCount: item.subjectCounts[sectionIndex] ?? section.questionCount,
              })),
            };
          }),
          ...(input.branches ?? []).map(
            (branch): ExamPartOption => ({
              id: mockId("opt"),
              kind: "BRANCH",
              code: branch.code,
              name: branch.name.trim(),
              sections: [
                {
                  id: mockId("sec"),
                  subjectCode: branchSubject,
                  questionCount: branch.questionCount,
                  blocks: blocksFromTemplate(branch.blocks),
                },
              ],
            }),
          ),
        ]
      : [];
    const singleSubject: ExamSection = {
      id: mockId("sec"),
      subjectCode: input.subjectCode ?? "",
      questionCount: input.questionCount,
      blocks: blocksFromTemplate(input.blocks),
    };
    return {
      id: mockId("part"),
      order: index + 1,
      name: input.name.trim(),
      durationMinutes: input.durationMinutes,
      questionCount: input.questionCount,
      points: input.points ?? null,
      mode: input.mode,
      sections: choice || common.length > 0 ? common : [singleSubject],
      options,
    };
  });
}

/**
 * Kiểm tra cấu trúc khởi tạo như backend: số câu nguyên dương; thời lượng dương khi tính theo phần (`null` khi dùng
 * thời lượng chung); điểm phần dương nếu có; phần nhiều môn có tổng bằng số câu của phần; phần tự chọn có ít nhất một
 * lựa chọn và mỗi lựa chọn đủ số câu của phần trừ phần chung.
 */
function assertInputParts(inputs: readonly CreateExamPartInput[], timing: ExamTiming = "PER_PART"): void {
  const positive = (value: number) => Number.isInteger(value) && value > 0;
  const invalid =
    inputs.length === 0 ||
    inputs.some((input) => {
      const durationOk =
        timing === "SHARED"
          ? input.durationMinutes === null
          : input.durationMinutes === null || positive(input.durationMinutes);
      const pointsOk =
        input.points === undefined || input.points === null || (Number.isFinite(input.points) && input.points > 0);
      if (!input.name.trim() || !positive(input.questionCount) || !durationOk || !pointsOk) return true;
      const sections = input.sections ?? [];
      if (!sections.every((section) => section.subjectCode && positive(section.questionCount))) return true;
      const common = sum(sections.map((section) => section.questionCount));
      if (input.mode === "FIXED") return sections.length > 0 ? common !== input.questionCount : !input.subjectCode;
      const target = input.questionCount - common;
      const branches = input.branches ?? [];
      if (target <= 0) return true;
      if (!input.englishOption && input.combinations.length === 0 && branches.length === 0) return true;
      if (branches.some((branch) => !branch.code || !branch.name.trim() || branch.questionCount !== target)) return true;
      return input.combinations.some((item) => {
        const combination = reference.combinations.find((entry) => entry.code === item.code);
        return (
          !combination ||
          combination.subjectCodes.length !== item.subjectCounts.length ||
          !item.subjectCounts.every(positive) ||
          sum(item.subjectCounts) !== target
        );
      });
    });
  if (invalid) throw new MockApiError(422, "EXAM_STRUCTURE_INVALID");
}

/** Tên phần của bài Theo phần (Toán, Văn, Khoa học, Tiếng Anh) trong dữ liệu mẫu. */
const PART_NAMES = (examsFixture as { partNames: Record<string, string> }).partNames;

interface ScopeRequest {
  kind: ExamKind;
  scope: ExamScope;
  partCode?: string | null;
  subjectCode?: string | null;
}

/** Mẫu mới nhất khớp kỳ thi, phạm vi, phần hoặc môn (bước 1 không chọn mẫu hay năm, spec 5.2). */
function latestTemplate(request: ScopeRequest): ExamTemplate | undefined {
  return MOCK_EXAM_TEMPLATES.filter(
    (template) =>
      template.kind === request.kind &&
      template.scope === request.scope &&
      (request.scope !== "PART" || template.partCode === request.partCode) &&
      (request.scope !== "SUBJECT" || template.subjectCode === request.subjectCode),
  ).sort((a, b) => (b.structureYear ?? 0) - (a.structureYear ?? 0))[0];
}

/** Mẫu của phạm vi (mẫu đã chọn nếu có, không thì mẫu mới nhất) và cách tính thời lượng: theo mẫu, không có thì theo kỳ thi. */
function scopeTemplate(request: ScopeRequest, templateCode?: string | null): {
  template: ExamTemplate | undefined;
  timing: ExamTiming;
} {
  const template = templateCode ? findTemplate(MOCK_EXAM_TEMPLATES, templateCode) : latestTemplate(request);
  return { template, timing: template?.timing ?? kindOption(request.kind)?.timing ?? "PER_PART" };
}

/**
 * Cấu trúc nạp theo mẫu khi không có cấu trúc khởi tạo kèm theo (đổi phạm vi ở Thông tin chung): cùng cách dựng với
 * trang Tạo bài thi, gồm khối gợi ý của mẫu.
 */
function buildParts(request: ScopeRequest, template: ExamTemplate | undefined, timing: ExamTiming): ExamPart[] {
  const format = formatOfScope(request.scope);
  const inputs = initialParts({
    templates: MOCK_EXAM_TEMPLATES,
    kind: request.kind,
    format,
    singleScope: request.partCode ?? "",
    combinations: combinationsForKind(reference.combinations, request.kind),
    scopeName: (scope) => PART_NAMES[scope] ?? subjectName(scope),
    subjectName,
    template: format === "FULL" ? (template ?? null) : undefined,
    subjectCode: request.subjectCode ?? "",
  });
  const pointsByPart = inputs.some((part) => part.points !== null);
  return partsFromInput(partInputsFromValues(inputs, { timing, pointsByPart }), request.subjectCode ?? null);
}

/**
 * Kỳ thi phải có trong danh mục và cho phép phạm vi (422 `EXAM_SCOPE_NOT_ALLOWED`); Theo phần cần phần, Theo môn cần
 * môn (422 `EXAM_PART_OR_SUBJECT_REQUIRED`).
 */
function assertScope(request: ScopeRequest): void {
  const kind = kindOption(request.kind);
  if (!kind) throw new MockApiError(422, "EXAM_KIND_UNKNOWN");
  if (!kind.scopes.includes(request.scope)) throw new MockApiError(422, "EXAM_SCOPE_NOT_ALLOWED");
  if (request.scope === "PART" && !request.partCode) throw new MockApiError(422, "EXAM_PART_OR_SUBJECT_REQUIRED");
  if (request.scope === "SUBJECT" && !request.subjectCode) throw new MockApiError(422, "EXAM_PART_OR_SUBJECT_REQUIRED");
}

/** Khóa lạc quan (D13): lệch `revision` thì 412 `EXAM_REVISION_CONFLICT`. */
export function assertExamRevision(exam: Pick<ExamDetail, "revision">, expectedRevision: number | undefined): void {
  if (expectedRevision !== undefined && expectedRevision !== exam.revision) {
    throw new MockApiError(412, "EXAM_REVISION_CONFLICT");
  }
}

/** Bài đã sửa cấu trúc tay trong phiên: nạp lại mẫu phải có `confirmReplace` (409 `EXAM_STRUCTURE_REPLACE_CONFIRM`). */
const structureEdited = new Set<string>();

/** Thang điểm dương; để trống chỉ khi kỳ thi không có thang cố định (V-ACT). */
function assertMaxScore(maxScore: number | null, kind: ExamKind): void {
  const invalid =
    maxScore === null ? (kindOption(kind)?.maxScore ?? null) !== null : !(Number.isFinite(maxScore) && maxScore > 0);
  if (invalid) throw new MockApiError(422, "EXAM_MAX_SCORE_INVALID");
}

/** Thời lượng chung của bài dùng thời lượng chung phải là số phút nguyên dương. */
function assertSharedDuration(minutes: number | null): asserts minutes is number {
  if (minutes === null || !Number.isInteger(minutes) || minutes <= 0) throw new MockApiError(422, "EXAM_STRUCTURE_INVALID");
}

/** Số câu Tự luận trong cấu trúc có lớn hơn 0 không (khối dạng `ESSAY`). */
function partsHaveEssay(parts: readonly ExamPart[]): boolean {
  return parts.some((part) =>
    [...part.sections, ...part.options.flatMap((option) => option.sections)].some((section) =>
      section.blocks.some((block) => block.questionType === "ESSAY" && block.questionCount > 0),
    ),
  );
}

/**
 * Số liệu tóm tắt của bài tính lại từ cấu trúc (số câu, thời lượng, phần tự chọn). Bài dùng thời lượng chung
 * (`sharedDuration` khác `null`) giữ thời lượng chung thay vì cộng các phần.
 */
function summaryFromParts(parts: readonly ExamPart[], sharedDuration: number | null = null) {
  const options = parts.filter((part) => part.mode === "CHOICE").flatMap((part) => part.options);
  return {
    totalQuestions: sum(parts.map((part) => part.questionCount)),
    durationMinutes: sharedDuration ?? sum(parts.map((part) => part.durationMinutes ?? 0)),
    hasEssay: partsHaveEssay(parts),
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

// ---------------------------------------------------------------- ghi

/**
 * Đoạn môn trong mã gợi ý: bài lẻ theo phần/môn (bản vẽ TaoBaiThi: `HSA-L-TOAN-002`), bài theo môn theo môn của bài
 * (`THPTQG-F-TOAN-001`). Môn không có trong bảng dùng mã môn bỏ dấu gạch dưới.
 */
const SUBJECT_SLUGS: Record<string, string> = {
  MATH: "TOAN",
  LITERATURE: "VAN",
  SCIENCE: "KH",
  ENGLISH: "TA",
  PHYSICS: "LI",
  CHEMISTRY: "HOA",
  BIOLOGY: "SINH",
  HISTORY: "SU",
  GEOGRAPHY: "DIA",
  VIETNAMESE: "TV",
  ECON_LAW: "KTPL",
  INFORMATICS: "TIN",
  TECH_INDUSTRY: "CNCN",
  TECH_AGRICULTURE: "CNNN",
  SCIENTIFIC_THINKING: "TDKH",
};

function subjectSlug(code: string | null | undefined): string | null {
  if (!code) return null;
  return SUBJECT_SLUGS[code] ?? code.replace(/[^A-Z0-9]/gi, "").toUpperCase();
}

/**
 * Mã gợi ý `HSA-F-003`, `HSA-P-TOAN-002`, `THPTQG-S-TOAN-001`: tiền tố theo kỳ thi, phạm vi, phần của bài Theo phần
 * hoặc môn của bài Theo môn; số tiếp theo chưa dùng.
 */
export async function suggestExamCode(query: SuggestExamCodeQuery): Promise<string> {
  return mockRequest(
    () => {
      const slug = subjectSlug(query.scope === "PART" ? query.partCode : query.subjectCode);
      const prefix = `${query.kind}-${query.scope === "FULL" ? "F" : query.scope === "PART" ? "P" : "S"}-${slug ? `${slug}-` : ""}`;
      const used = new Set(examStore.all().map((exam) => exam.code.toUpperCase()));
      let next = 1;
      while (used.has(`${prefix}${String(next).padStart(3, "0")}`)) next += 1;
      return `${prefix}${String(next).padStart(3, "0")}`;
    },
    { ignoreScenario: true },
  );
}

/**
 * Tạo bài thi Nháp (bước 1, PBI-04 mục 4): hệ thống nạp mẫu mới nhất khớp kỳ thi, phạm vi, phần hoặc môn (không có mẫu
 * thì cấu trúc trống); cấu trúc khởi tạo kèm theo (trang Tạo bài thi hiện tại) thay cho mẫu. Thời lượng chung, thang
 * điểm, năm cấu trúc và năm ma trận theo mẫu; năm khác nhau thì chưa xác nhận.
 */
export async function createExam(request: CreateExamRequest): Promise<ExamDetail> {
  return mockRequest(
    () => {
      const code = normalizeCode(request.code);
      assertCodeFree(code);
      const kind = kindOption(request.kind);
      const subjectCode = request.scope === "SUBJECT" ? (request.subjectCode ?? null) : null;
      const partCode = request.scope === "PART" ? (request.partCode ?? null) : null;
      const scopeRequest: ScopeRequest = { kind: request.kind, scope: request.scope, partCode, subjectCode };
      assertScope(scopeRequest);
      const { template, timing } = scopeTemplate(scopeRequest);
      const shared = timing === "SHARED" ? (request.durationMinutes ?? template?.durationMinutes ?? null) : null;
      if (timing === "SHARED") assertSharedDuration(shared);
      const maxScore = request.maxScore !== undefined ? request.maxScore : (template?.maxScore ?? kind?.maxScore ?? null);
      assertMaxScore(maxScore, request.kind);
      const inputs = request.parts?.map((part) => (timing === "SHARED" ? { ...part, durationMinutes: null } : part));
      if (inputs) assertInputParts(inputs, timing);
      const parts = inputs ? partsFromInput(inputs, subjectCode) : buildParts(scopeRequest, template, timing);
      const exam: ExamDetail = {
        id: mockId("ex"),
        code,
        name: request.name.trim(),
        kind: request.kind,
        scope: request.scope,
        revision: 0,
        status: "DRAFT",
        servingVersion: null,
        hasUnpublishedChanges: true,
        ...summaryFromParts(parts, shared),
        partCode,
        subjectCode,
        timing,
        maxScore,
        referenceMax: kind?.referenceMax ?? null,
        practiceScoring: kind?.practiceScoring ?? "RAW",
        structureYear: template?.structureYear ?? null,
        matrixYear: template?.matrixYear ?? null,
        yearsConfirmed: !yearsDiffer(template),
        check: { status: "UNCHECKED", issueCount: 0, checkedAt: null },
        attemptCount: 0,
        updatedAt: nowIso(),
        updatedBy: MOCK_CURRENT_AUTHOR,
        description: request.description?.trim() || null,
        instructions: request.instructions?.trim() || null,
        templateCode: template?.code ?? null,
        parts,
        versions: [{ version: null, state: "WORKING", publishedAt: null, publishedBy: null, attemptCount: 0, note: null }],
      };
      if (inputs) structureEdited.add(exam.id);
      return examStore.insert(exam);
    },
    { ignoreScenario: true },
  );
}

/**
 * Sửa Thông tin chung. Đổi kỳ thi, phạm vi, phần hoặc môn thì nạp lại cấu trúc theo mẫu mới nhất; cấu trúc đã sửa thì
 * cần `confirmReplace` (409 `EXAM_STRUCTURE_REPLACE_CONFIRM`); bài đã công bố không đổi được (409 `EXAM_SCOPE_LOCKED`).
 * Lệch `expectedRevision` thì 412 `EXAM_REVISION_CONFLICT`. Thời lượng chung, thang điểm và xác nhận năm sửa được bất
 * cứ lúc nào.
 */
export async function updateExamInfo(id: string, request: UpdateExamInfoRequest): Promise<ExamDetail> {
  return mockRequest(
    () => {
      const current = examStore.require(id);
      assertExamRevision(current, request.expectedRevision);
      const code = normalizeCode(request.code);
      assertCodeFree(code, id);
      const kind = kindOption(request.kind);
      const subjectCode = request.scope === "SUBJECT" ? (request.subjectCode ?? null) : null;
      const partCode = request.scope === "PART" ? (request.partCode ?? null) : null;
      const scopeRequest: ScopeRequest = { kind: request.kind, scope: request.scope, partCode, subjectCode };
      const scopeChanged =
        current.kind !== request.kind ||
        current.scope !== request.scope ||
        current.partCode !== partCode ||
        current.subjectCode !== subjectCode;
      if (scopeChanged) {
        if (current.servingVersion !== null) throw new MockApiError(409, "EXAM_SCOPE_LOCKED");
        assertScope(scopeRequest);
        if (structureEdited.has(id) && !request.confirmReplace) {
          throw new MockApiError(409, "EXAM_STRUCTURE_REPLACE_CONFIRM");
        }
      }
      const { template, timing } = scopeChanged
        ? scopeTemplate(scopeRequest)
        : { template: findTemplate(MOCK_EXAM_TEMPLATES, current.templateCode), timing: current.timing };
      const structureYear = scopeChanged ? (template?.structureYear ?? null) : current.structureYear;
      const matrixYear = scopeChanged ? (template?.matrixYear ?? null) : current.matrixYear;
      const differ = yearsDiffer({ structureYear, matrixYear });
      const yearsConfirmed = !differ || (request.yearsConfirmed ?? (scopeChanged ? false : current.yearsConfirmed));
      const fallbackShared = scopeChanged ? (template?.durationMinutes ?? null) : current.durationMinutes;
      const shared = timing === "SHARED" ? (request.durationMinutes ?? fallbackShared) : null;
      if (timing === "SHARED") assertSharedDuration(shared);
      const fallbackScore = scopeChanged ? (template?.maxScore ?? kind?.maxScore ?? null) : current.maxScore;
      const maxScore = request.maxScore === undefined ? fallbackScore : request.maxScore;
      assertMaxScore(maxScore, request.kind);
      if (scopeChanged) structureEdited.delete(id);
      return examStore.update(id, (exam) => {
        const parts = scopeChanged ? buildParts(scopeRequest, template, timing) : exam.parts;
        return {
          ...exam,
          ...markWorking(exam),
          revision: exam.revision + 1,
          code,
          name: request.name.trim(),
          description: request.description?.trim() || null,
          instructions: request.instructions === undefined ? exam.instructions : request.instructions?.trim() || null,
          kind: request.kind,
          scope: request.scope,
          templateCode: scopeChanged ? (template?.code ?? null) : exam.templateCode,
          partCode,
          subjectCode,
          timing,
          maxScore,
          referenceMax: kind?.referenceMax ?? null,
          practiceScoring: kind?.practiceScoring ?? "RAW",
          structureYear,
          matrixYear,
          yearsConfirmed,
          parts,
          ...summaryFromParts(parts, shared),
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
    () => {
      assertExamRevision(examStore.require(id), request.expectedRevision);
      structureEdited.add(id);
      return examStore.update(id, (exam) => ({
        ...exam,
        ...markWorking(exam),
        revision: exam.revision + 1,
        parts: structuredClone(request.parts),
        ...summaryFromParts(request.parts, exam.timing === "SHARED" ? exam.durationMinutes : null),
        check: recheck(exam),
      }));
    },
    { ignoreScenario: true },
  );
}

/**
 * Nạp lại cấu trúc theo một mẫu (chọn phiên bản năm ở bước Cấu trúc). Mẫu phải khớp kỳ thi, phạm vi, phần hoặc môn của
 * bài (422 `EXAM_TEMPLATE_MISMATCH`); cấu trúc đã sửa thì cần `confirmReplace` (409 `EXAM_STRUCTURE_REPLACE_CONFIRM`).
 */
export async function applyExamTemplate(id: string, request: ApplyExamTemplateRequest): Promise<ExamDetail> {
  return mockRequest(
    () => {
      const current = examStore.require(id);
      assertExamRevision(current, request.expectedRevision);
      const template = findTemplate(MOCK_EXAM_TEMPLATES, request.templateCode);
      const matches =
        template &&
        template.kind === current.kind &&
        template.scope === current.scope &&
        (current.scope !== "PART" || template.partCode === current.partCode) &&
        (current.scope !== "SUBJECT" || template.subjectCode === current.subjectCode);
      if (!template || !matches) throw new MockApiError(422, "EXAM_TEMPLATE_MISMATCH");
      if (structureEdited.has(id) && !request.confirmReplace) {
        throw new MockApiError(409, "EXAM_STRUCTURE_REPLACE_CONFIRM");
      }
      structureEdited.delete(id);
      const scopeRequest: ScopeRequest = {
        kind: current.kind,
        scope: current.scope,
        partCode: current.partCode,
        subjectCode: current.subjectCode,
      };
      const timing = template.timing;
      const shared = timing === "SHARED" ? (template.durationMinutes ?? current.durationMinutes) : null;
      const parts = buildParts(scopeRequest, template, timing);
      return examStore.update(id, (exam) => ({
        ...exam,
        ...markWorking(exam),
        revision: exam.revision + 1,
        templateCode: template.code,
        timing,
        maxScore: template.maxScore ?? exam.maxScore,
        structureYear: template.structureYear ?? null,
        matrixYear: template.matrixYear ?? null,
        yearsConfirmed: !yearsDiffer(template),
        parts,
        ...summaryFromParts(parts, shared),
        check: recheck(exam),
      }));
    },
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
        revision: 0,
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
export async function discontinueExam(id: string, expectedRevision?: number): Promise<ExamDetail> {
  return mockRequest(
    () => {
      const current = examStore.require(id);
      assertExamRevision(current, expectedRevision);
      if (current.status !== "PUBLISHED") throw new MockApiError(409, "EXAM_NOT_PUBLISHED");
      return examStore.update(id, (exam) => ({
        ...exam,
        revision: exam.revision + 1,
        status: "DISCONTINUED",
        updatedAt: nowIso(),
        updatedBy: MOCK_CURRENT_AUTHOR,
      }));
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
      const byCompetency = new Map<string, number>();
      const competencies = query.knowledgeScope.competencyCodes ?? [];
      let total = 0;
      for (const question of questionStore.all()) {
        if (question.status !== "READY" || question.subjectCode !== query.subjectCode) continue;
        if (question.type !== query.questionType || !matchesScope(question, query.knowledgeScope)) continue;
        if (competencies.length && !competencies.includes(question.competencyCode ?? "")) continue;
        const units = question.type === "QUESTION_SET" && question.children.length ? question.children : [question];
        for (const unit of units) {
          const key = `${unit.gradeCode}|${unit.difficultyCode}`;
          counted.set(key, (counted.get(key) ?? 0) + 1);
          const competency = unit.competencyCode ?? question.competencyCode;
          if (competency) byCompetency.set(competency, (byCompetency.get(competency) ?? 0) + 1);
          total += 1;
        }
      }
      return {
        total,
        cells: [...counted].map(([key, value]) => {
          const [gradeCode = "", difficultyCode = ""] = key.split("|");
          return { gradeCode, difficultyCode, value };
        }),
        ...(byCompetency.size ? { byCompetency: [...byCompetency].map(([code, value]) => ({ code, value })) } : {}),
      };
    },
    { empty: () => ({ total: 0, cells: [] }) },
  );
}
