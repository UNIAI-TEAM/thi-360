import type { components } from "@thi360/api-client";
import referenceFixture from "@/features/content/mock/reference.json";
import type { ContentReference, ExamKindOption } from "@/features/content/types";
import {
  combinationsForKind,
  findTemplate,
  initialParts,
  latestTemplate as latestScopeTemplate,
  partInputsFromValues,
  yearsDiffer,
} from "../create-exam-schema";
import { partsFromInput } from "../create-parts";
import { sum } from "../matrix";
import { mapExamDetail, toApiParts } from "../api-mapping";
import type {
  ExamDetail,
  ExamKind,
  ExamPart,
  ExamPartOption,
  ExamScope,
  ExamSection,
  ExamTemplate,
  ExamTemplateBlock,
  ExamTemplatePart,
  ExamTemplateSection,
  ExamTiming,
  ExamVersion,
  QuestionBlock,
} from "../types";
import type { ExamCheckResult } from "../review-types";
import examsFixture from "../mock/exams.json";
import { FakeServer, type Handler, type Recorded, type Reply } from "./fake-exams-server";

/**
 * Máy chủ giả có trạng thái cho nhánh API thật của Bài thi (`CONTENT_MOCK` tắt): giữ bài thi dưới dạng wire
 * (`ExamDetailResponse`) và trả lời mọi đường `/api/v1/admin/exams/**` mà client thật gọi, gồm viết (tạo, sửa,
 * nạp lại mẫu, sao chép, ngừng bán, công bố). Phản hồi đi qua `createApiClient` và các hàm chuyển đổi của sản
 * phẩm như nhánh thật; dữ liệu dựng lại chính xác từ `mock/exams.json` (dùng chung fixture với nhánh mẫu; QA xóa mẫu thì test đổi import sang fixture riêng).
 *
 * Không mô phỏng phần "nghiệp vụ" máy chủ mà client không kiểm (sinh đề xem trước, đếm nguồn, giải kiểm tra):
 * các đường đó nhận đáp số xếp sẵn (`seedCheck`, `queueRun`, `checkReplies`, `versionsList`, `counts`) hoặc lộ
 * qua `install` để test ghi đè handler.
 */

type Schema = components["schemas"];
type WireDetail = Schema["ExamDetailResponse"];
type WirePart = Schema["ExamPart"];
type WireOption = Schema["ExamPartOption"];
type WireSection = Schema["ExamSection"];
type WireBlock = Schema["QuestionBlock"];
type WireVersion = Schema["ExamVersionResponse"];
type WireTemplate = Schema["ExamTemplateResponse"];
type WireSummary = Schema["ExamSummaryResponse"];
type WireCheck = Schema["ExamCheckResult"];
type WireVersionsList = Schema["ExamVersionList"];
type WireCounts = Schema["AvailableQuestionsResponse"];

const data = examsFixture as { exams: ExamDetail[]; templates: ExamTemplate[]; partNames: Record<string, string> };
const reference = referenceFixture as ContentReference;
const PART_NAMES = data.partNames;
const NOW = "2026-11-17T01:00:00Z";
const AUTHOR = { id: "u-test", name: "Test Author" };

/** Bài mẫu theo id, bản sao mới mỗi lần (kiểu của màn). */
export function examFixture(id: string): ExamDetail {
  const exam = data.exams.find((item) => item.id === id);
  if (!exam) throw new Error(`khong co bai mau ${id}`);
  return structuredClone(exam);
}

export const FIXTURE_TEMPLATES: readonly ExamTemplate[] = data.templates;

/* ------------------------------------------------ chuyển kiểu của màn -> wire ------------------------------ */
function wireVersion(version: ExamVersion): WireVersion {
  return {
    version: version.version ?? undefined,
    state: version.state,
    publishedAt: version.publishedAt ?? undefined,
    publishedBy: version.publishedBy ?? undefined,
    attemptCount: version.attemptCount,
    note: version.note ?? undefined,
  };
}

/** `ExamDetail` của màn -> `ExamDetailResponse` như máy chủ trả (bỏ trường `null`). */
export function toWireExam(exam: ExamDetail): WireDetail {
  const parts = toApiParts(exam.parts) as WirePart[];
  return {
    id: exam.id,
    code: exam.code,
    name: exam.name,
    kind: exam.kind,
    scope: exam.scope,
    status: exam.status,
    revision: exam.revision,
    servingVersion: exam.servingVersion ?? undefined,
    hasUnpublishedChanges: exam.hasUnpublishedChanges,
    questionCount: exam.totalQuestions,
    durationMinutes: exam.timing === "SHARED" ? (exam.durationMinutes ?? undefined) : undefined,
    hasEnglishOption: exam.englishOption,
    combinationCount: exam.combinationCount,
    partCode: exam.partCode ?? undefined,
    subjectCode: exam.subjectCode ?? undefined,
    timing: exam.timing,
    maxScore: exam.maxScore ?? undefined,
    referenceMax: exam.referenceMax ?? undefined,
    practiceScoring: exam.practiceScoring,
    hasEssay: exam.hasEssay,
    check: { status: exam.check.status, issueCount: exam.check.issueCount, checkedAt: exam.check.checkedAt ?? undefined },
    attemptCount: exam.attemptCount,
    updatedAt: exam.updatedAt,
    updatedBy: exam.updatedBy,
    description: exam.description ?? undefined,
    instructions: exam.instructions ?? undefined,
    templateCode: exam.templateCode ?? undefined,
    structureYear: exam.structureYear ?? undefined,
    matrixYear: exam.matrixYear ?? undefined,
    yearsConfirmed: exam.yearsConfirmed,
    parts,
    versions: exam.versions.map(wireVersion),
  };
}

function wireScope(scope: QuestionBlock["knowledgeScope"] | undefined): WireBlock["knowledgeScope"] | undefined {
  if (!scope) return undefined;
  return { nodeIds: scope.nodeIds, match: scope.match, competencyCodes: scope.competencyCodes };
}

function wireMatrix(matrix: QuestionBlock["matrix"]): WireBlock["matrix"] | undefined {
  if (!matrix) return undefined;
  return {
    mode: matrix.mode,
    unit: matrix.unit,
    cells: matrix.cells,
    byGrade: matrix.byGrade,
    byDifficulty: matrix.byDifficulty,
    byCompetency: matrix.byCompetency,
  };
}

/** Nhóm lấy câu của mẫu -> wire `QuestionBlock` (máy chủ bỏ `null`; `competencyCode` của mẫu thành năng lực). */
function wireTemplateBlock(block: ExamTemplateBlock): WireBlock {
  return {
    name: block.name,
    questionType: block.questionType ?? undefined,
    questionCount: block.questionCount ?? undefined,
    setCount: block.setCount ?? undefined,
    setSize: block.setSize ?? undefined,
    requiredPointsPerQuestion: block.requiredPointsPerQuestion ?? undefined,
    knowledgeScope:
      wireScope(block.knowledgeScope) ?? (block.competencyCode ? { nodeIds: [], match: "ANY", competencyCodes: [block.competencyCode] } : undefined),
    matrix: wireMatrix(block.matrix ?? null),
    taskTypeCode: block.taskTypeCode ?? undefined,
    childAnswerType: block.childAnswerType ?? undefined,
    templateGroupCode: block.templateGroupCode ?? undefined,
    answerTypeLocked: block.answerTypeLocked,
    shuffle: block.shuffle,
    expectedScoring: block.expectedScoring,
  } as WireBlock;
}

/** Môn của mẫu -> wire `ExamSection` (id `null`: client sinh khi nạp). */
function wireTemplateSection(section: ExamTemplateSection): WireSection {
  return {
    subjectCode: section.subjectCode,
    questionCount: section.questionCount ?? undefined,
    knowledgeScope: wireScope(section.knowledgeScope ?? null),
    answerQuotas: section.answerQuotas ?? undefined,
    matrix: wireMatrix(section.matrix ?? null),
    groupOrder: section.groupOrder ?? undefined,
    choiceGroup: section.choiceGroup ?? undefined,
    blocks: (section.blocks ?? []).map(wireTemplateBlock),
  } as WireSection;
}

function optionSubjects(template: ExamTemplate, part: ExamTemplatePart, option: NonNullable<ExamTemplatePart["options"]>[number]): string[] {
  if (option.kind === "COMBINATION") {
    return reference.combinations.find((item) => item.code === option.code)?.subjectCodes ?? [option.code];
  }
  if (option.kind === "SUBJECT") return [option.code];
  // Nhánh nội dung lấy môn của phần chung, không có thì môn của bài/mẫu.
  return [part.sections?.[0]?.subjectCode ?? template.subjectCode ?? ""];
}

/** Phần của mẫu -> wire `ExamPart` trong `structure` của `ExamTemplateResponse` (id `null`). */
function wireTemplatePart(template: ExamTemplate, part: ExamTemplatePart, index: number): WirePart {
  const sections = (part.sections ?? []).map(wireTemplateSection);
  const blocks = (part.blocks ?? []).map(wireTemplateBlock);
  if (blocks.length && sections.length === 1) sections[0]!.blocks = [...sections[0]!.blocks ?? [], ...blocks];
  const options = (part.options ?? []).map((option): WireOption => {
    if (option.kind === "SUBJECT_CHOICE" && option.subjectChoice) {
      return {
            kind: "SUBJECT_CHOICE",
        code: option.code,
        name: option.name ?? undefined,
        chooseSubjects: option.subjectChoice.chooseSubjects,
        countsByPick: option.subjectChoice.countsByPick,
        sections: option.subjectChoice.sections.map(wireTemplateSection),
        expertConfirmed: option.expertConfirmed,
      } as WireOption;
    }
    const subjects = optionSubjects(template, part, option);
    const counts = option.subjectCounts ?? [];
    return {
        kind: option.kind,
      code: option.code,
      name: option.name ?? undefined,
      sections: subjects.map((subjectCode, order) => ({
        subjectCode,
        questionCount: counts[order] ?? undefined,
        blocks: order === 0 ? (option.blocks ?? []).map(wireTemplateBlock) : [],
      })),
      expertConfirmed: option.expertConfirmed,
    } as WireOption;
  });
  return {
    order: index + 1,
    name: part.name,
    durationMinutes: part.durationMinutes ?? undefined,
    questionCount: part.questionCount,
    questionCountOnPaper: part.questionCount,
    questionCountRequired: part.questionCount,
    participation: part.mode === "CHOICE" ? { mode: "CHOICE", choose: 1 } : { mode: "ALL" },
    maxPoints: part.points ?? undefined,
    // Máy chủ không có `subjectCode` của phần: phần cố định một môn mang môn trong `sections` (kèm khối gợi ý nếu có).
    sections: sections.length
      ? sections
      : part.subjectCode
        ? [{ subjectCode: part.subjectCode, questionCount: part.questionCount, blocks }]
        : blocks.length
          ? [{ subjectCode: "", blocks }]
          : [],
    options,
  } as WirePart;
}

/** Mẫu của màn -> `ExamTemplateResponse` (`structure` là tài liệu JsonNode có `parts`). */
export function toWireTemplate(template: ExamTemplate): WireTemplate {
  return {
    active: true,
    code: template.code,
    kindCode: template.kind,
    scope: template.scope,
    partCode: template.partCode ?? undefined,
    subjectCode: template.subjectCode ?? undefined,
    structureYear: template.structureYear ?? undefined,
    matrixYear: template.matrixYear ?? undefined,
    timing: template.timing,
    durationMinutes: template.durationMinutes ?? undefined,
    maxScore: template.maxScore ?? undefined,
    expertNote: template.expertNote ?? undefined,
    structure: { parts: template.parts.map((part, index) => wireTemplatePart(template, part, index)) } as unknown as WireTemplate["structure"],
  };
}

/* ----------------------------------------------------- nghiệp vụ tối thiểu --------------------------------- */

function kindOption(kind: ExamKind): ExamKindOption | undefined {
  return reference.examKinds.find((option) => option.code === kind);
}

function subjectName(code: string): string {
  return reference.subjects.find((subject) => subject.code === code)?.name ?? code;
}

interface ScopeRequest {
  kind: ExamKind;
  scope: ExamScope;
  partCode: string | null;
  subjectCode: string | null;
}

const err = (status: number, code: string): Reply => ({ status, body: { code, message: code } });
const REVISION_CONFLICT = () => err(412, "EXAM_REVISION_CONFLICT");

/** Số liệu tóm tắt tính lại từ cấu trúc (như backend ghi khi lưu). */
function summaryFromParts(parts: readonly ExamPart[], sharedDuration: number | null = null) {
  const options = parts.filter((part) => part.mode === "CHOICE").flatMap((part) => part.options);
  const hasEssay = parts.some((part) =>
    [...part.sections, ...part.options.flatMap((option) => option.sections)].some((section) =>
      section.blocks.some((block) => block.questionType === "ESSAY"),
    ),
  );
  return {
    totalQuestions: sum(parts.map((part) => part.questionCount)),
    durationMinutes: sharedDuration ?? sum(parts.map((part) => part.durationMinutes ?? 0)),
    hasEssay,
    englishOption: options.some((option) => option.kind === "SUBJECT" && option.code === "ENGLISH"),
    combinationCount: options.filter((option) => option.kind === "COMBINATION").length,
  };
}

const slug = (id: string) => id.replace(/[^a-z0-9]/gi, "").toLowerCase().slice(0, 12);

/** Đổi mọi id của cấu trúc (bản sao, nạp mẫu): hậu tố `-<slug>` để không trùng nguồn. */
function regenerateIds(parts: readonly ExamPart[], suffix: string): ExamPart[] {
  let counter = 0;
  const next = (prefix: string) => `${prefix}-${suffix}-${(counter += 1)}`;
  const cloneBlock = (block: QuestionBlock): QuestionBlock => ({ ...structuredClone(block), id: next("blk") });
  const cloneSection = (section: ExamSection): ExamSection => ({ ...structuredClone(section), id: next("sec"), blocks: section.blocks.map(cloneBlock) });
  const cloneOption = (option: ExamPartOption): ExamPartOption => ({ ...structuredClone(option), id: next("opt"), sections: option.sections.map(cloneSection) });
  return parts.map((part) => ({ ...structuredClone(part), id: next("part"), sections: part.sections.map(cloneSection), options: part.options.map(cloneOption) }));
}

/** Kết quả kiểm tra tối thiểu cho các màn cần một bản ghi (đường `default` hợp lệ). */
function defaultCheck(examId: string): WireCheck {
  return {
    examId,
    status: "VALID",
    stale: false,
    checkedAt: NOW,
    durationSeconds: 2,
    timeLimitSeconds: 30,
    issueCount: 0,
    paths: [{ pathId: "default", result: "VALID", issues: [] }],
  };
}

interface RunReply {
  state: "RUNNING" | "DONE" | "FAILED";
  result?: WireCheck | null;
  errorCode?: string;
}

/** Máy chủ giả có trạng thái của các đường `/api/v1/admin/exams`. Đăng ký handler qua `server.on` trước `install` để ghi đè mặc định. */
export class ExamsBackend {
  readonly server = new FakeServer();
  private readonly exams = new Map<string, WireDetail>();
  private readonly checks = new Map<string, WireCheck>();
  private readonly runs = new Map<string, { examId: string; queue: RunReply[] }>();
  private readonly edits = new Set<string>();
  private readonly seeds = new Map<string, { versions?: WireVersionsList }>();
  /** Đường đã được ít nhất một phiên bản phục vụ (kể cả bản mẫu đầy đủ) — cho `unpublishedPaths`. */
  private readonly servedPaths = new Map<string, Set<string>>();
  private sequence = 0;
  private readonly countsByPath = new Map<string, Reply>();
  /** Mã gợi ý tùy biến theo truy vấn; mặc định `<KIND>-<F|P|S>-[SLUG-]<NNN>` số tiếp theo chưa dùng. */
  suggestCode: ((query: URLSearchParams, used: Set<string>) => string) | null = null;

  constructor() {
    for (const exam of data.exams) this.exams.set(exam.id, toWireExam(exam));
  }

  /* ----- seed / đọc lại cho test ----- */

  /** Bài mẫu đang được phục vụ (wire). Trả undefined khi chưa nạp. */
  wire(id: string): WireDetail | undefined {
    return this.exams.get(id);
  }

  /** Bài mẫu theo mã (viết hoa hay thường); undefined khi chưa nạp. */
  wireByCode(code: string): WireDetail | undefined {
    const normalized = code.trim().toUpperCase();
    return [...this.exams.values()].find((exam) => exam.code === normalized);
  }

  /** Bài mẫu sau ánh xạ của client (`mapExamDetail`), để soạn số liệu cần assert trong test. */
  app(id: string): ExamDetail {
    const wire = this.exams.get(id);
    if (!wire) throw new Error(`khong co bai ${id}`);
    return mapExamDetail(wire);
  }

  /** Bổ sung một bài không có trong mẫu (tạo sẵn theo kiểu của màn). */
  seedExam(exam: ExamDetail): this {
    this.exams.set(exam.id, toWireExam(exam));
    return this;
  }

  /** Một đường của kết quả kiểm tra kèm các lựa chọn (phần, phương án) như máy chủ trả, để màn ghi đúng tên đường. */
  checkPath(examId: string, pathId: string, result: "VALID" | "INVALID" | "UNCONFIRMED" = "VALID"): NonNullable<WireCheck["paths"]>[number] {
    const exam = this.exams.get(examId);
    if (!exam) throw new Error(`khong co bai ${examId}`);
    const { choices } = this.pathRef(exam, pathId);
    return { pathId, result, choices, issues: [] };
  }

  /** Ghi kết quả kiểm tra gần nhất (GET/POST /check trả bản này nếu `checkReplies` rỗng). */
  seedCheck(examId: string, result: WireCheck): this {
    this.checks.set(examId, result);
    return this;
  }

  /** Phản hồi xếp sẵn cho `POST /check` không-deep (kiểm tra thường); mỗi lần gọi lấy một phản hồi. */
  readonly checkReplies = new Map<string, Reply[]>();

  /** Phiên bản tùy biến cho `GET /versions` (mặc định: rút từ `versions` của bài). */
  seedVersions(examId: string, list: WireVersionsList): this {
    this.seeds.set(examId, { versions: list });
    return this;
  }

  /** Đáp số cho đếm nguồn; `path` là `count` hoặc `count-subject`. */
  seedCounts(path: "count" | "count-subject", reply: Reply): this {
    this.countsByPath.set(path, reply);
    return this;
  }

  /** Xếp các trạng thái `getCheckRun` cho một lần chạy (gọi sau khi `startDeepCheck` trả `runId`). */
  queueRun(runId: string, ...replies: RunReply[]): this {
    const current = this.runs.get(runId);
    this.runs.set(runId, { examId: current?.examId ?? "", queue: [...replies] });
    return this;
  }

  /** Lệnh gọi đã ghi của một đường. */
  calls(method: string, path: string): Recorded[] {
    return this.server.calls(method, path);
  }

  private nextId(prefix: string): string {
    this.sequence += 1;
    return `${prefix}-${this.sequence}`;
  }

  private require(id: string): WireDetail | null {
    return this.exams.get(id) ?? null;
  }

  private touch(exam: WireDetail, patch: Partial<WireDetail>) {
    Object.assign(exam, patch, { revision: exam.revision + 1, hasUnpublishedChanges: true, updatedAt: NOW, updatedBy: AUTHOR });
    if (!exam.versions.some((version) => version.state === "WORKING")) {
      exam.versions.unshift({ version: undefined, state: "WORKING", attemptCount: 0 } as WireVersion);
    }
  }

  private recheck(exam: WireDetail): WireDetail["check"] {
    return exam.check.status === "UNCHECKED" ? exam.check : { ...exam.check, status: "NEEDS_RECHECK" };
  }

  /** Mẫu mới nhất khớp yêu cầu phạm vi (bước 1 không chọn mẫu, spec 5.2). */
  private latestTemplate(request: ScopeRequest): ExamTemplate | undefined {
    return latestScopeTemplate(FIXTURE_TEMPLATES, {
      kind: request.kind,
      scope: request.scope,
      partCode: request.partCode ?? "",
      subjectCode: request.subjectCode ?? "",
    });
  }

  /** Cấu trúc nạp theo mẫu (cùng đường dựng của trang Tạo bài thi và `api.mock` trước đây). */
  private buildParts(request: ScopeRequest, template: ExamTemplate | undefined, timing: ExamTiming): ExamPart[] {
    const inputs = initialParts({
      templates: FIXTURE_TEMPLATES,
      kind: request.kind,
      scope: request.scope,
      partCode: request.partCode ?? "",
      combinations: combinationsForKind(reference.combinations, request.kind),
      scopeName: (scope) => PART_NAMES[scope] ?? subjectName(scope),
      subjectName,
      template: request.scope === "PART" ? undefined : (template ?? null),
      subjectCode: request.subjectCode ?? "",
    });
    const pointsByPart = inputs.some((part) => part.points !== null);
    return partsFromInput(partInputsFromValues(inputs, { timing, pointsByPart }), reference.combinations, request.subjectCode);
  }

  private scopeError(request: ScopeRequest): Reply | null {
    const kind = kindOption(request.kind);
    if (!kind) return err(422, "EXAM_KIND_UNKNOWN");
    if (!kind.scopes.includes(request.scope)) return err(422, "EXAM_SCOPE_NOT_ALLOWED");
    if (request.scope === "PART" && !request.partCode) return err(422, "EXAM_PART_OR_SUBJECT_REQUIRED");
    if (request.scope === "SUBJECT" && !request.subjectCode) return err(422, "EXAM_PART_OR_SUBJECT_REQUIRED");
    return null;
  }

  private codeTaken(code: string, exceptId?: string): boolean {
    const normalized = code.trim().toUpperCase();
    return [...this.exams.values()].some((exam) => exam.id !== exceptId && exam.code.toUpperCase() === normalized);
  }

  private suggestedCode(request: URLSearchParams): string {
    const used = new Set([...this.exams.values()].map((exam) => exam.code.toUpperCase()));
    if (this.suggestCode) return this.suggestCode(request, used);
    const kind = request.get("kind") ?? "HSA";
    const scope = request.get("scope") ?? "FULL";
    const subject = request.get(scope === "PART" ? "partCode" : "subjectCode") ?? "";
    const slugMap: Record<string, string> = { MATH: "TOAN", LITERATURE: "VAN", SCIENCE: "KH", ENGLISH: "TA", PHYSICS: "LI", CHEMISTRY: "HOA", BIOLOGY: "SINH", HISTORY: "SU", GEOGRAPHY: "DIA", VIETNAMESE: "TV", ECON_LAW: "KTPL", INFORMATICS: "TIN", TECH_INDUSTRY: "CNCN", TECH_AGRICULTURE: "CNNN", SCIENTIFIC_THINKING: "TDKH" };
    const sub = slugMap[subject] ?? subject.replace(/[^A-Z0-9]/gi, "").toUpperCase();
    const prefix = `${kind}-${scope === "FULL" ? "F" : scope === "PART" ? "P" : "S"}-${sub ? `${sub}-` : ""}`;
    let next = 1;
    while (used.has(`${prefix}${String(next).padStart(3, "0")}`)) next += 1;
    return `${prefix}${String(next).padStart(3, "0")}`;
  }

  /** Đường lựa chọn như máy chủ trả: mã đường kèm các lựa chọn (phần, phương án) tạo nên đường đó. */
  private pathRef(exam: WireDetail, pathId: string): NonNullable<WireVersion["servingPaths"]>[number] {
    for (const part of exam.parts) {
      if (part.participation?.mode !== "CHOICE") continue;
      const option = (part.options ?? []).find((item) => (item.id ?? item.code) === pathId);
      if (option) {
        return { pathId, choices: [{ partId: part.id, optionId: option.id, kind: option.kind, code: option.code, name: option.name }] };
      }
    }
    return { pathId };
  }

  private pathIds(exam: WireDetail): string[] {
    const options = exam.parts.filter((part) => part.participation?.mode === "CHOICE").flatMap((part) => part.options ?? []);
    return options.length ? options.map((option) => option.id ?? option.code ?? "") : ["default"];
  }

  private createExam(body: Record<string, unknown>): Reply {
    const request: ScopeRequest = {
      kind: (body.kind ?? "") as ExamKind,
      scope: (body.scope ?? "") as ExamScope,
      partCode: (body.partCode as string | undefined) ?? null,
      subjectCode: (body.subjectCode as string | undefined) ?? null,
    };
    const scopeErr = this.scopeError(request);
    if (scopeErr) return scopeErr;
    const code = String(body.code ?? "").trim().toUpperCase();
    if (this.codeTaken(code)) return err(409, "EXAM_CODE_TAKEN");
    const template = this.latestTemplate(request);
    const kind = kindOption(request.kind);
    const timing: ExamTiming = template?.timing ?? kind?.timing ?? "PER_PART";
    const shared = timing === "SHARED" ? ((body.durationMinutes as number | undefined) ?? template?.durationMinutes ?? null) : null;
    if (timing === "SHARED" && (shared === null || !Number.isInteger(shared) || shared <= 0)) return err(422, "EXAM_STRUCTURE_INVALID");
    const bodyScore = body.maxScore as number | undefined;
    if (bodyScore !== undefined && bodyScore !== null && (!Number.isFinite(bodyScore) || bodyScore <= 0)) return err(422, "EXAM_MAX_SCORE_INVALID");
    const maxScore = bodyScore ?? template?.maxScore ?? kind?.maxScore ?? null;
    if (maxScore === null) return err(422, "EXAM_MAX_SCORE_INVALID");
    const parts = this.buildParts(request, template, timing);
    const detail: WireDetail = {
      id: this.nextId("ex"),
      code,
      name: String(body.name ?? "").trim(),
      kind: request.kind,
      scope: request.scope,
      status: "DRAFT",
      revision: 1,
      hasUnpublishedChanges: true,
      ...(() => {
        const summary = summaryFromParts(parts, shared);
        return {
          questionCount: summary.totalQuestions,
          durationMinutes: shared ?? undefined,
          hasEnglishOption: summary.englishOption,
          combinationCount: summary.combinationCount,
          hasEssay: summary.hasEssay,
        };
      })(),
      partCode: request.partCode ?? undefined,
      subjectCode: request.subjectCode ?? undefined,
      timing,
      maxScore,
      referenceMax: kind?.referenceMax ?? undefined,
      practiceScoring: kind?.practiceScoring ?? "RAW",
      check: { status: "UNCHECKED", issueCount: 0 },
      attemptCount: 0,
      updatedAt: NOW,
      updatedBy: AUTHOR,
      description: (body.description as string | undefined)?.trim() || undefined,
      instructions: (body.instructions as string | undefined)?.trim() || undefined,
      templateCode: template?.code,
      structureYear: template?.structureYear ?? undefined,
      matrixYear: template?.matrixYear ?? undefined,
      yearsConfirmed: !yearsDiffer(template ?? null),
      parts: toApiParts(regenerateIds(parts, this.nextId("new"))) as WirePart[],
      versions: [{ state: "WORKING", attemptCount: 0 } as WireVersion],
    };
    this.exams.set(detail.id, detail);
    return { status: 201, body: detail };
  }

  private listExams(request: Recorded): Reply {
    const query = request.url.searchParams;
    const text = query.get("q")?.trim().toLocaleLowerCase("vi");
    const rows = [...this.exams.values()].filter((exam) => {
      if (text && !exam.name.toLocaleLowerCase("vi").includes(text) && !exam.code.toLocaleLowerCase("vi").includes(text)) return false;
      if (query.get("kind") && exam.kind !== query.get("kind")) return false;
      if (query.get("scope") && exam.scope !== query.get("scope")) return false;
      if (query.get("subject") && exam.subjectCode !== query.get("subject") && exam.partCode !== query.get("subject")) return false;
      if (query.get("status") && exam.status !== query.get("status")) return false;
      return true;
    });
    rows.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
    const page = Number(query.get("page") ?? 0);
    const size = Number(query.get("size") ?? 20);
    const items = rows.slice(page * size, (page + 1) * size).map(
      (exam): WireSummary => ({
        id: exam.id,
        code: exam.code,
        name: exam.name,
        kind: exam.kind,
        scope: exam.scope,
        status: exam.status,
        servingVersion: exam.servingVersion,
        hasUnpublishedChanges: exam.hasUnpublishedChanges,
        questionCount: exam.questionCount,
        durationMinutes: exam.durationMinutes,
        hasEnglishOption: exam.hasEnglishOption,
        combinationCount: exam.combinationCount,
        maxScore: exam.maxScore,
        hasEssay: exam.hasEssay,
        check: exam.check,
        attemptCount: exam.attemptCount,
        updatedAt: exam.updatedAt,
        updatedBy: exam.updatedBy,
      }),
    );
    return { status: 200, body: { items, page, size, totalItems: rows.length, totalPages: Math.ceil(rows.length / size) } };
  }

  private updateInfo(exam: WireDetail, body: Record<string, unknown>): Reply {
    if (body.expectedRevision !== exam.revision) return REVISION_CONFLICT();
    const code = String(body.code ?? exam.code).trim().toUpperCase();
    if (this.codeTaken(code, exam.id)) return err(409, "EXAM_CODE_TAKEN");
    const request: ScopeRequest = {
      kind: (body.kind ?? exam.kind) as ExamKind,
      scope: (body.scope ?? exam.scope) as ExamScope,
      partCode: (body.partCode as string | undefined) ?? exam.partCode ?? null,
      subjectCode: (body.subjectCode as string | undefined) ?? exam.subjectCode ?? null,
    };
    const scopeChanged = exam.kind !== request.kind || exam.scope !== request.scope || (exam.partCode ?? null) !== request.partCode || (exam.subjectCode ?? null) !== request.subjectCode;
    if (scopeChanged) {
      if (exam.servingVersion !== undefined) return err(409, "EXAM_SCOPE_LOCKED");
      const scopeErr = this.scopeError(request);
      if (scopeErr) return scopeErr;
      if (this.edits.has(exam.id) && body.confirmReplace !== true) return err(409, "EXAM_STRUCTURE_REPLACE_CONFIRM");
    }
    const template = scopeChanged ? this.latestTemplate(request) : undefined;
    const timing: ExamTiming = scopeChanged ? (template?.timing ?? kindOption(request.kind)?.timing ?? "PER_PART") : (exam.timing as ExamTiming);
    const shared = timing === "SHARED" ? ((body.durationMinutes as number | undefined) ?? (scopeChanged ? (template?.durationMinutes ?? null) : (exam.durationMinutes ?? null))) : null;
    if (timing === "SHARED" && (shared === null || !Number.isInteger(shared) || shared <= 0)) return err(422, "EXAM_STRUCTURE_INVALID");
    const bodyScore = body.maxScore as number | undefined;
    if (bodyScore !== undefined && bodyScore !== null && (!Number.isFinite(bodyScore) || bodyScore <= 0)) return err(422, "EXAM_MAX_SCORE_INVALID");
    const appParts = scopeChanged ? regenerateIds(this.buildParts(request, template, timing), slug(exam.id)) : undefined;
    const parts = appParts ? (toApiParts(appParts) as WirePart[]) : exam.parts;
    const summary = summaryFromParts(appParts ?? mapExamDetail(exam).parts, shared);
    this.edits.delete(exam.id);
    this.touch(exam, {
      code,
      name: String(body.name ?? exam.name).trim(),
      description: (body.description as string | undefined)?.trim() || undefined,
      instructions: body.instructions === undefined ? exam.instructions : ((body.instructions as string | undefined)?.trim() || undefined),
      kind: request.kind,
      scope: request.scope,
      templateCode: scopeChanged ? template?.code : exam.templateCode,
      partCode: request.partCode ?? undefined,
      subjectCode: request.subjectCode ?? undefined,
      timing,
      maxScore: bodyScore ?? (scopeChanged ? (template?.maxScore ?? kindOption(request.kind)?.maxScore ?? null) : exam.maxScore) ?? undefined,
      referenceMax: scopeChanged ? (kindOption(request.kind)?.referenceMax ?? undefined) : exam.referenceMax,
      practiceScoring: scopeChanged ? (kindOption(request.kind)?.practiceScoring ?? "RAW") : exam.practiceScoring,
      structureYear: scopeChanged ? (template?.structureYear ?? undefined) : exam.structureYear,
      matrixYear: scopeChanged ? (template?.matrixYear ?? undefined) : exam.matrixYear,
      yearsConfirmed:
        body.yearsConfirmed === true ? true : scopeChanged ? !yearsDiffer(template ?? null) : exam.yearsConfirmed,
      parts,
      questionCount: summary.totalQuestions,
      durationMinutes: shared ?? (timing === "SHARED" ? exam.durationMinutes : undefined),
      hasEnglishOption: summary.englishOption,
      combinationCount: summary.combinationCount,
      hasEssay: summary.hasEssay,
      check: scopeChanged ? this.recheck(exam) : exam.check,
    });
    return { status: 200, body: exam };
  }

  private updateStructure(exam: WireDetail, body: Record<string, unknown>): Reply {
    if (body.expectedRevision !== exam.revision) return REVISION_CONFLICT();
    const parts = (body.parts ?? []) as WirePart[];
    if (!parts.length) return err(422, "EXAM_STRUCTURE_INVALID");
    if (parts.some((part) => !part.name?.trim() || !(part.questionCount && part.questionCount > 0))) return err(422, "EXAM_STRUCTURE_INVALID");
    if (parts.some((part) => part.participation?.mode === "CHOICE" && !(part.options?.length))) return err(422, "EXAM_STRUCTURE_INVALID");
    const appParts = mapExamDetail({ ...exam, parts }).parts;
    const shared = exam.timing === "SHARED" ? (exam.durationMinutes ?? null) : null;
    const summary = summaryFromParts(appParts, shared);
    this.edits.add(exam.id);
    this.touch(exam, {
      parts,
      questionCount: summary.totalQuestions,
      durationMinutes: shared ?? undefined,
      hasEnglishOption: summary.englishOption,
      combinationCount: summary.combinationCount,
      hasEssay: summary.hasEssay,
      check: this.recheck(exam),
    });
    return { status: 200, body: exam };
  }

  private applyTemplate(exam: WireDetail, body: Record<string, unknown>): Reply {
    if (body.expectedRevision !== exam.revision) return REVISION_CONFLICT();
    const template = findTemplate(FIXTURE_TEMPLATES, body.templateCode as string | undefined);
    const matches =
      template &&
      template.kind === exam.kind &&
      template.scope === exam.scope &&
      (exam.scope !== "PART" || template.partCode === exam.partCode) &&
      (exam.scope !== "SUBJECT" || template.subjectCode === exam.subjectCode);
    if (!template || !matches) return err(422, "EXAM_TEMPLATE_MISMATCH");
    if (this.edits.has(exam.id) && body.confirmReplace !== true) return err(409, "EXAM_STRUCTURE_REPLACE_CONFIRM");
    this.edits.delete(exam.id);
    const request: ScopeRequest = { kind: exam.kind as ExamKind, scope: exam.scope as ExamScope, partCode: exam.partCode ?? null, subjectCode: exam.subjectCode ?? null };
    const parts = this.buildParts(request, template, template.timing);
    const shared = template.timing === "SHARED" ? (template.durationMinutes ?? exam.durationMinutes ?? null) : null;
    const summary = summaryFromParts(parts, shared);
    this.touch(exam, {
      templateCode: template.code,
      timing: template.timing,
      maxScore: template.maxScore ?? exam.maxScore,
      structureYear: template.structureYear ?? undefined,
      matrixYear: template.matrixYear ?? undefined,
      yearsConfirmed: !yearsDiffer(template),
      parts: toApiParts(regenerateIds(parts, slug(exam.id))) as WirePart[],
      questionCount: summary.totalQuestions,
      durationMinutes: shared ?? (template.timing === "SHARED" ? exam.durationMinutes : undefined),
      hasEnglishOption: summary.englishOption,
      combinationCount: summary.combinationCount,
      hasEssay: summary.hasEssay,
      check: this.recheck(exam),
    });
    return { status: 200, body: exam };
  }

  private duplicate(exam: WireDetail, body: Record<string, unknown>): Reply {
    const used = new Set([...this.exams.values()].map((item) => item.code.toUpperCase()));
    let code = `${exam.code}-COPY`;
    for (let index = 2; used.has(code); index += 1) code = `${exam.code}-COPY-${index}`;
    const copy: WireDetail = {
      ...structuredClone(exam),
      id: this.nextId("ex"),
      code,
      name: String(body.name ?? "").trim(),
      status: "DRAFT",
      revision: 1,
      servingVersion: undefined,
      hasUnpublishedChanges: true,
      check: { status: "UNCHECKED", issueCount: 0 },
      attemptCount: 0,
      updatedAt: NOW,
      updatedBy: AUTHOR,
      parts: toApiParts(regenerateIds(mapExamDetail(exam).parts, slug(exam.id))) as WirePart[],
      versions: [{ state: "WORKING", attemptCount: 0 } as WireVersion],
    };
    this.exams.set(copy.id, copy);
    return { status: 201, body: copy };
  }

  private discontinue(exam: WireDetail, body: Record<string, unknown>): Reply {
    if (body.expectedRevision !== exam.revision) return REVISION_CONFLICT();
    if (exam.status !== "PUBLISHED") return err(409, "EXAM_NOT_PUBLISHED");
    this.touch(exam, { status: "DISCONTINUED", hasUnpublishedChanges: exam.hasUnpublishedChanges });
    return { status: 200, body: exam };
  }

  private versionsList(exam: WireDetail): Reply {
    const seeded = this.seeds.get(exam.id)?.versions;
    if (seeded) return { status: 200, body: seeded };
    const items = exam.versions.filter((version) => version.version !== undefined);
    const known = this.pathIds(exam);
    const served = this.servedPaths.get(exam.id);
    const unpublished = served ? known.filter((id) => !served.has(id)) : known;
    const working: WireVersionsList["workingCopy"] = exam.hasUnpublishedChanges
      ? { baseVersion: exam.servingVersion ?? items[0]?.version ?? 0, unpublishedPaths: unpublished.map((pathId) => this.pathRef(exam, pathId)), check: exam.check, savedAt: exam.updatedAt, editedBy: exam.updatedBy }
      : undefined;
    return { status: 200, body: { examId: exam.id, items, workingCopy: working, changes: [] } satisfies WireVersionsList };
  }

  private versionDetail(exam: WireDetail, version: number): Reply {
    const row = exam.versions.find((item) => item.version === version);
    if (!row) return err(404, "EXAM_VERSION_NOT_FOUND");
    return { status: 200, body: { examId: exam.id, row, parts: exam.parts } };
  }

  private publish(exam: WireDetail, body: Record<string, unknown>): Reply {
    if (!exam.hasUnpublishedChanges) return err(409, "NOTHING_TO_PUBLISH");
    if (body.expectedRevision !== exam.revision) return REVISION_CONFLICT();
    const pathIds = (body.pathIds ?? []) as string[];
    const known = this.pathIds(exam);
    if (pathIds.some((id) => !known.includes(id))) return err(404, "PATH_NOT_FOUND");
    if (!pathIds.length) return err(422, "NO_PATH_SELECTED");
    const check = this.checks.get(exam.id);
    if (!check || exam.check.status === "NEEDS_RECHECK") return err(409, "CHECK_REQUIRED");
    const results = new Map((check.paths ?? []).map((path) => [path.pathId ?? "", path.result ?? "INVALID"]));
    if (pathIds.some((id) => results.get(id) !== "VALID")) return err(422, "PATH_NOT_VALID");
    const nextVersion = Math.max(exam.servingVersion ?? 0, ...exam.versions.map((version) => version.version ?? 0)) + 1;
    const alreadyServed = this.servedPaths.get(exam.id) ?? new Set<string>();
    const served = new Set([...alreadyServed, ...pathIds]);
    this.servedPaths.set(exam.id, served);
    const all = known.every((id) => served.has(id));
    const kept: Array<{ pathId: string; version: number }> = [];
    for (const version of exam.versions) {
      if (version.state !== "SERVING") continue;
      const remaining = (version.servingPaths ?? known.map((id) => this.pathRef(exam, id))).filter((path) => !pathIds.includes(path.pathId ?? ""));
      version.servingPaths = remaining;
      for (const path of remaining) kept.push({ pathId: path.pathId ?? "", version: version.version ?? 0 });
      if (remaining.length === 0) version.state = "RETIRED";
    }
    const row: WireVersion = {
      version: nextVersion,
      state: "SERVING",
      publishedAt: NOW,
      publishedBy: AUTHOR,
      attemptCount: 0,
      note: typeof body.note === "string" ? body.note.trim() || undefined : undefined,
      publishedPaths: pathIds.map((pathId) => this.pathRef(exam, pathId)),
      servingPaths: pathIds.map((pathId) => this.pathRef(exam, pathId)),
    };
    // Bản làm việc (WORKING) giữ đầu danh sách như chi tiết màn hình; bản mới đứng trước lịch sử đã công bố.
    const workingRows = exam.versions.filter((version) => version.state === "WORKING");
    const history = exam.versions.filter((version) => version.state !== "WORKING" && version.version !== row.version);
    exam.versions = [...workingRows, row, ...history];
    exam.status = "PUBLISHED";
    exam.servingVersion = nextVersion;
    exam.hasUnpublishedChanges = !all;
    exam.versions = exam.versions.filter((version) => version.state !== "WORKING" || exam.hasUnpublishedChanges);
    exam.updatedAt = NOW;
    exam.revision += 1;
    return {
      status: 200,
      body: { examId: exam.id, version: nextVersion, publishedPathIds: pathIds, keptPaths: kept },
    };
  }

  private handleCheckPost(exam: WireDetail, request: Recorded): Reply {
    if (request.url.searchParams.get("deep") === "true") {
      const runId = this.nextId("run");
      this.runs.set(runId, { examId: exam.id, queue: [] });
      return { status: 202, body: { runId } };
    }
    const queued = this.checkReplies.get(exam.id);
    const reply = queued && queued.length > 1 ? queued.shift()! : queued?.[0];
    if (reply) return reply;
    const result = this.checks.get(exam.id) ?? defaultCheck(exam.id);
    this.checks.set(exam.id, result);
    exam.check = { status: result.status ?? "VALID", issueCount: result.issueCount ?? 0, checkedAt: result.checkedAt };
    return { status: 200, body: result };
  }

  private handleRun(request: Recorded): Reply {
    const segments = request.url.pathname.split("/");
    const runId = segments[segments.length - 1]!;
    const examId = segments[segments.indexOf("exams") + 1]!;
    const run = this.runs.get(runId);
    if (!run || run.examId !== examId) return err(404, "CHECK_RUN_NOT_FOUND");
    const next = run.queue.length > 1 ? run.queue.shift()! : run.queue[0] ?? { state: "DONE" as const, result: this.checks.get(examId) ?? defaultCheck(examId) };
    if (next.state === "DONE" && next.result) {
      this.checks.set(examId, next.result);
      const exam = this.exams.get(examId)!;
      exam.check = { status: next.result.status ?? "VALID", issueCount: next.result.issueCount ?? 0, checkedAt: next.result.checkedAt };
    }
    return { status: 200, body: { runId, state: next.state, result: next.result ?? null, errorCode: next.errorCode ?? null } };
  }

  /** Đăng ký mọi đường mặc định sau các handler của test (`server.on` trước `install` thắng). */
  install(): FakeServer {
    const exams = "/api/v1/admin/exams";
    this.server
      .on("GET", `${exams}/suggested-code`, (request) => ({ status: 200, body: { code: this.suggestedCode(request.url.searchParams) } }))
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: FIXTURE_TEMPLATES.map(toWireTemplate) })
      .on("GET", exams, (request) => this.listExams(request))
      .on("POST", exams, (request) => this.createExam(request.body as Record<string, unknown>))
      .on("POST", `${exams}/available-questions/count`, () => this.countsByPath.get("count") ?? { status: 200, body: { total: 0, cells: [], byAnswerType: [] } satisfies WireCounts })
      .on("POST", `${exams}/available-questions/count-subject`, () => this.countsByPath.get("count-subject") ?? { status: 200, body: { total: 0, cells: [], byAnswerType: [] } satisfies WireCounts })
      .on("GET", `${exams}/:id/check/runs/:runId`, (request) => this.handleRun(request))
      .on("GET", `${exams}/:id/check`, (request) => {
        const exam = this.require(request.url.pathname.split("/").at(-2)!);
        if (exam === null) return err(404, "EXAM_NOT_FOUND");
        const check = this.checks.get(exam.id);
        return check ? { status: 200, body: check } : { status: 200, body: null };
      })
      .on("POST", `${exams}/:id/check`, (request) => {
        const exam = this.require(request.url.pathname.split("/").at(-2)!);
        return exam === null ? err(404, "EXAM_NOT_FOUND") : this.handleCheckPost(exam, request);
      })
      .on("GET", `${exams}/:id/versions/:version`, (request) => {
        const exam = this.require(request.url.pathname.split("/").at(-3)!);
        return exam === null ? err(404, "EXAM_NOT_FOUND") : this.versionDetail(exam, Number(request.url.pathname.split("/").at(-1)));
      })
      .on("GET", `${exams}/:id/versions`, (request) => {
        const exam = this.require(request.url.pathname.split("/").at(-2)!);
        return exam === null ? err(404, "EXAM_NOT_FOUND") : this.versionsList(exam);
      })
      .on("GET", `${exams}/:id/structure`, (request) => {
        const exam = this.require(request.url.pathname.split("/").at(-2)!);
        return exam === null ? err(404, "EXAM_NOT_FOUND") : { status: 200, body: { examId: exam.id, revision: exam.revision, parts: exam.parts } };
      })
      .on("PUT", `${exams}/:id/structure`, (request) => {
        const exam = this.require(request.url.pathname.split("/").at(-2)!);
        return exam === null ? err(404, "EXAM_NOT_FOUND") : this.updateStructure(exam, request.body as Record<string, unknown>);
      })
      .on("POST", `${exams}/:id/structure/template`, (request) => {
        const exam = this.require(request.url.pathname.split("/").at(-3)!);
        return exam === null ? err(404, "EXAM_NOT_FOUND") : this.applyTemplate(exam, request.body as Record<string, unknown>);
      })
      .on("POST", `${exams}/:id/duplicate`, (request) => {
        const exam = this.require(request.url.pathname.split("/").at(-2)!);
        return exam === null ? err(404, "EXAM_NOT_FOUND") : this.duplicate(exam, request.body as Record<string, unknown>);
      })
      .on("POST", `${exams}/:id/publish`, (request) => {
        const exam = this.require(request.url.pathname.split("/").at(-2)!);
        return exam === null ? err(404, "EXAM_NOT_FOUND") : this.publish(exam, request.body as Record<string, unknown>);
      })
      .on("POST", `${exams}/:id/discontinue`, (request) => {
        const exam = this.require(request.url.pathname.split("/").at(-2)!);
        return exam === null ? err(404, "EXAM_NOT_FOUND") : this.discontinue(exam, request.body as Record<string, unknown>);
      })
      .on("GET", `${exams}/:id`, (request) => {
        const exam = this.require(request.url.pathname.split("/").at(-1)!);
        return exam === null ? err(404, "EXAM_NOT_FOUND") : { status: 200, body: exam };
      })
      .on("PATCH", `${exams}/:id`, (request) => {
        const exam = this.require(request.url.pathname.split("/").at(-1)!);
        return exam === null ? err(404, "EXAM_NOT_FOUND") : this.updateInfo(exam, request.body as Record<string, unknown>);
      });
    return this.server.install();
  }
}

/** Mẫu của phạm vi (mẫu đã chọn nếu có, không thì mẫu mới nhất) — dùng trong test khi cần dựng kỳ vọng. */
export type { Handler, Recorded, Reply };
export type { WireCheck, WireDetail, WireVersionsList };
export type { ExamCheckResult };
