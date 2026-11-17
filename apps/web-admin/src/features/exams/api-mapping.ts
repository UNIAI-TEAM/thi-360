import type { components } from "@thi360/api-client";
import type { ExamScope, ExamTiming, PracticeScoring } from "@/features/content/types";
import type {
  AnswerQuotas,
  AvailableQuestionCounts,
  BlockExpectedScoring,
  BlockMatrix,
  ExamCheckStatus,
  ExamDetail,
  ExamPart,
  ExamPartOption,
  ExamSection,
  ExamStatus,
  ExamSummary,
  ExamTemplate,
  ExamTemplateBlock,
  ExamTemplatePart,
  ExamTemplateSection,
  ExamVersion,
  KnowledgeScope,
  MatrixDimensionValue,
  QuestionBlock,
} from "./types";

/**
 * Chuyển đổi giữa kiểu sinh từ OpenAPI (`packages/api-client`) và kiểu của màn (`types.ts`). Máy chủ bỏ trường `null`
 * (`NON_NULL`), màn dùng `null` cho "không có": đọc thì điền mặc định, ghi thì bỏ `null` (`toApiParts`).
 */

type Schemas = components["schemas"];
type ApiPart = Schemas["ExamPart"];
type ApiSection = Schemas["ExamSection"];
type ApiOption = Schemas["ExamPartOption"];
type ApiBlock = Schemas["QuestionBlock"];
type ApiMatrix = Schemas["BlockMatrix"];
type ApiScope = Schemas["KnowledgeScope"];

const nullable = <T>(value: T | null | undefined): T | null => value ?? null;

function mapScope(scope: ApiScope | null | undefined): KnowledgeScope | null {
  if (!scope) return null;
  return {
    nodeIds: scope.nodeIds ?? [],
    ...(scope.competencyCodes?.length ? { competencyCodes: scope.competencyCodes } : {}),
  };
}

function mapDimension(values: Schemas["MatrixDimensionValue"][] | undefined): MatrixDimensionValue[] {
  return (values ?? []).map((item) => ({ code: item.code ?? "", value: item.value ?? 0 }));
}

function mapMatrix(matrix: ApiMatrix | null | undefined): BlockMatrix | null {
  if (!matrix) return null;
  return {
    mode: matrix.mode ?? "COMBINED",
    unit: matrix.unit ?? "COUNT",
    cells: (matrix.cells ?? []).map((cell) => ({
      gradeCode: cell.gradeCode ?? "",
      difficultyCode: cell.difficultyCode ?? "",
      value: cell.value ?? 0,
    })),
    byGrade: mapDimension(matrix.byGrade),
    byDifficulty: mapDimension(matrix.byDifficulty),
    ...(matrix.byCompetency?.length ? { byCompetency: mapDimension(matrix.byCompetency) } : {}),
  };
}

function mapQuotas(quotas: Schemas["AnswerQuotas"] | null | undefined): AnswerQuotas | null {
  if (!quotas) return null;
  return {
    unit: quotas.unit ?? "COUNT",
    items: (quotas.items ?? []).flatMap((item) =>
      item.answerType ? [{ answerType: item.answerType, value: item.value ?? 0 }] : [],
    ),
  };
}

function mapExpectedScoring(scoring: Schemas["BlockExpectedScoring"] | null | undefined): BlockExpectedScoring | undefined {
  if (!scoring) return undefined;
  return {
    ...(scoring.trueFalseMethod ? { trueFalseMethod: scoring.trueFalseMethod as BlockExpectedScoring["trueFalseMethod"] } : {}),
    ...(scoring.trueFalseTable ? { trueFalseTable: scoring.trueFalseTable } : {}),
    ...(scoring.multipleChoiceRule ? { multipleChoiceRule: scoring.multipleChoiceRule as BlockExpectedScoring["multipleChoiceRule"] } : {}),
  };
}

export function mapBlock(block: ApiBlock): QuestionBlock {
  const expectedScoring = mapExpectedScoring(block.expectedScoring);
  return {
    id: block.id ?? "",
    name: block.name ?? "",
    questionType: nullable(block.questionType),
    questionCount: nullable(block.questionCount),
    setCount: nullable(block.setCount),
    setSize: nullable(block.setSize),
    requiredPointsPerQuestion: nullable(block.requiredPointsPerQuestion),
    knowledgeScope: mapScope(block.knowledgeScope),
    tagCodes: block.tagCodes ?? [],
    matrix: mapMatrix(block.matrix),
    childAnswerType: nullable(block.childAnswerType),
    templateGroupCode: nullable(block.templateGroupCode),
    answerTypeLocked: block.answerTypeLocked ?? false,
    ...(expectedScoring ? { expectedScoring } : {}),
  };
}

export function mapSection(section: ApiSection): ExamSection {
  return {
    id: section.id ?? "",
    subjectCode: section.subjectCode ?? "",
    questionCount: nullable(section.questionCount),
    knowledgeScope: mapScope(section.knowledgeScope),
    answerQuotas: mapQuotas(section.answerQuotas),
    matrix: mapMatrix(section.matrix),
    choiceGroup: nullable(section.choiceGroup),
    blocks: (section.blocks ?? []).map(mapBlock),
  };
}

export function mapOption(option: ApiOption): ExamPartOption {
  return {
    id: option.id ?? "",
    kind: option.kind ?? "SUBJECT",
    code: option.code ?? "",
    name: nullable(option.name),
    sections: (option.sections ?? []).map(mapSection),
    chooseSubjects: nullable(option.chooseSubjects),
    countsByPick: option.countsByPick ?? null,
    // `null` = chưa cần xác nhận; chỉ `false` làm kiểm tra cấu hình báo lỗi.
    ...(option.expertConfirmed === undefined || option.expertConfirmed === null ? {} : { expertConfirmed: option.expertConfirmed }),
    ...(option.blocks?.length ? { blocks: option.blocks.map(mapBlock) } : {}),
  };
}

export function mapPart(part: ApiPart, index: number): ExamPart {
  const choice = part.participation?.mode === "CHOICE";
  const questionCount = part.questionCount ?? 0;
  return {
    id: part.id ?? "",
    order: part.order ?? index + 1,
    name: part.name ?? "",
    durationMinutes: nullable(part.durationMinutes),
    questionCount,
    questionCountOnPaper: part.questionCountOnPaper ?? questionCount,
    questionCountRequired: part.questionCountRequired ?? questionCount,
    participation: { mode: choice ? "CHOICE" : "ALL", choose: part.participation?.choose ?? null },
    maxPoints: nullable(part.maxPoints),
    mode: choice ? "CHOICE" : "FIXED",
    sections: (part.sections ?? []).map(mapSection),
    options: (part.options ?? []).map(mapOption),
  };
}

export function mapParts(parts: readonly ApiPart[] | undefined): ExamPart[] {
  return (parts ?? []).map(mapPart);
}

/**
 * Cấu trúc gửi lên: bỏ `null` (máy chủ coi trường thiếu là `null`) và `mode` của phần (suy ra từ `participation`);
 * trường của mẫu (`answerTypeLocked`, `templateGroupCode`) máy chủ giữ theo mẫu.
 */
export function toApiParts(parts: readonly ExamPart[]): ApiPart[] {
  const strip = (value: unknown): unknown => {
    if (Array.isArray(value)) return value.map(strip);
    if (value && typeof value === "object") {
      return Object.fromEntries(
        Object.entries(value as Record<string, unknown>)
          .filter(([, item]) => item !== null && item !== undefined)
          .map(([key, item]) => [key, strip(item)]),
      );
    }
    return value;
  };
  return parts.map((part) => {
    const rest: Partial<ExamPart> = { ...part };
    delete rest.mode;
    return strip(rest) as ApiPart;
  });
}

// ---------------------------------------------------------------- bài thi

function branchCountOf(parts: readonly ExamPart[]): number {
  return parts.flatMap((part) => (part.mode === "CHOICE" ? part.options : [])).filter((option) => option.kind === "BRANCH").length;
}

function sumDurations(parts: readonly ExamPart[]): number {
  return parts.reduce((total, part) => total + (part.durationMinutes ?? 0), 0);
}

const person = (value: Schemas["PersonRef"] | undefined) => value ?? { id: "", name: "" };

/** Dòng danh sách: máy chủ trả đủ `revision`, `timing`, `partCode`, `subjectCode`, `referenceMax`, `practiceScoring`. */
export function mapExamSummary(row: Schemas["ExamSummaryResponse"]): ExamSummary {
  return {
    id: row.id,
    code: row.code,
    name: row.name,
    kind: row.kind,
    scope: row.scope as ExamScope,
    status: row.status as ExamStatus,
    revision: row.revision,
    servingVersion: nullable(row.servingVersion),
    hasUnpublishedChanges: row.hasUnpublishedChanges,
    totalQuestions: row.questionCount,
    durationMinutes: row.durationMinutes ?? 0,
    englishOption: row.hasEnglishOption,
    combinationCount: row.combinationCount,
    partCode: nullable(row.partCode),
    subjectCode: nullable(row.subjectCode),
    timing: row.timing as ExamTiming,
    maxScore: nullable(row.maxScore),
    referenceMax: nullable(row.referenceMax),
    practiceScoring: row.practiceScoring as PracticeScoring,
    hasEssay: row.hasEssay,
    check: { status: row.check.status as ExamCheckStatus, issueCount: row.check.issueCount, checkedAt: nullable(row.check.checkedAt) },
    attemptCount: row.attemptCount,
    updatedAt: row.updatedAt,
    updatedBy: person(row.updatedBy),
  };
}

function mapVersion(version: Schemas["ExamVersionResponse"]): ExamVersion {
  return {
    version: nullable(version.version),
    state: version.state as ExamVersion["state"],
    publishedAt: nullable(version.publishedAt),
    publishedBy: version.publishedBy ?? null,
    attemptCount: version.attemptCount,
    note: nullable(version.note),
  };
}

export function mapExamDetail(response: Schemas["ExamDetailResponse"]): ExamDetail {
  const parts = mapParts(response.parts);
  return {
    id: response.id,
    code: response.code,
    name: response.name,
    kind: response.kind,
    scope: response.scope as ExamScope,
    status: response.status as ExamStatus,
    revision: response.revision,
    servingVersion: nullable(response.servingVersion),
    hasUnpublishedChanges: response.hasUnpublishedChanges,
    totalQuestions: response.questionCount,
    // F-4: thời lượng chung chỉ có nghĩa khi `timing = SHARED`; thời gian theo phần thì tổng các phần (khớp xem trước và
    // kiểm tra cấu hình), không lấy `durationMinutes` lưu trên bài (có thể là số của mẫu).
    durationMinutes: response.timing === "SHARED" ? (response.durationMinutes ?? sumDurations(parts)) : sumDurations(parts),
    englishOption: response.hasEnglishOption,
    combinationCount: response.combinationCount,
    partCode: nullable(response.partCode),
    subjectCode: nullable(response.subjectCode),
    timing: response.timing as ExamTiming,
    maxScore: nullable(response.maxScore),
    referenceMax: nullable(response.referenceMax),
    practiceScoring: response.practiceScoring as PracticeScoring,
    hasEssay: response.hasEssay,
    branchCount: branchCountOf(parts),
    check: {
      status: response.check.status as ExamCheckStatus,
      issueCount: response.check.issueCount,
      checkedAt: nullable(response.check.checkedAt),
    },
    attemptCount: response.attemptCount,
    updatedAt: response.updatedAt,
    updatedBy: person(response.updatedBy),
    description: nullable(response.description),
    instructions: nullable(response.instructions),
    templateCode: nullable(response.templateCode),
    structureYear: nullable(response.structureYear),
    matrixYear: nullable(response.matrixYear),
    yearsConfirmed: response.yearsConfirmed,
    parts,
    versions: response.versions.map(mapVersion),
  };
}

export function mapAvailableCounts(response: Schemas["AvailableQuestionsResponse"]): AvailableQuestionCounts {
  return {
    total: response.total ?? 0,
    cells: (response.cells ?? []).map((cell) => ({
      gradeCode: cell.gradeCode ?? "",
      difficultyCode: cell.difficultyCode ?? "",
      value: cell.value ?? 0,
    })),
    byAnswerType: (response.byAnswerType ?? []).flatMap((item) =>
      item.answerType ? [{ answerType: item.answerType, value: item.value ?? 0 }] : [],
    ),
    ...(response.byCompetency?.length ? { byCompetency: mapDimension(response.byCompetency) } : {}),
  };
}

// ---------------------------------------------------------------- mẫu

function templateBlock(block: QuestionBlock): ExamTemplateBlock {
  return {
    name: block.name,
    questionType: block.questionType,
    questionCount: block.questionCount,
    requiredPointsPerQuestion: block.requiredPointsPerQuestion,
    setCount: block.setCount,
    setSize: block.setSize,
    ...(block.expectedScoring ? { expectedScoring: block.expectedScoring } : {}),
    childAnswerType: block.childAnswerType,
    templateGroupCode: block.templateGroupCode,
    answerTypeLocked: block.answerTypeLocked,
    knowledgeScope: block.knowledgeScope,
    matrix: block.matrix,
  };
}

function templateSection(section: ExamSection): ExamTemplateSection {
  return {
    subjectCode: section.subjectCode,
    questionCount: section.questionCount,
    blocks: section.blocks.map(templateBlock),
    knowledgeScope: section.knowledgeScope,
    answerQuotas: section.answerQuotas,
    matrix: section.matrix,
    choiceGroup: section.choiceGroup,
  };
}

type TemplateOption = NonNullable<ExamTemplatePart["options"]>[number];

function templateOption(option: ExamPartOption): TemplateOption {
  const base = {
    kind: option.kind,
    code: option.code,
    name: option.name ?? null,
    ...(option.expertConfirmed !== undefined ? { expertConfirmed: option.expertConfirmed } : {}),
  };
  if (option.kind === "SUBJECT_CHOICE") {
    return {
      ...base,
      subjectChoice: {
        sections: option.sections.map(templateSection),
        chooseSubjects: option.chooseSubjects ?? 0,
        countsByPick: option.countsByPick ?? [],
      },
    };
  }
  // Khối gợi ý của lựa chọn (tổ hợp một môn, nhánh) nằm ở môn đầu tiên hoặc ở `blocks` của lựa chọn.
  const blocks = [...option.sections.slice(0, 1).flatMap((section) => section.blocks), ...(option.blocks ?? [])];
  return {
    ...base,
    subjectCounts: option.sections.length ? option.sections.map((section) => section.questionCount ?? 0) : (option.countsByPick ?? []),
    ...(blocks.length ? { blocks: blocks.map(templateBlock) } : {}),
  };
}

function templatePart(part: ExamPart): ExamTemplatePart {
  return {
    name: part.name,
    questionCount: part.questionCount,
    durationMinutes: part.durationMinutes,
    mode: part.mode,
    subjectCode: null,
    points: part.maxPoints ?? null,
    ...(part.sections.length ? { sections: part.sections.map(templateSection) } : {}),
    ...(part.mode === "CHOICE" ? { options: part.options.map(templateOption) } : {}),
  };
}

/** Mẫu cấu trúc; máy chủ không trả tên nên `name` để trống, giao diện dựng tên theo ngôn ngữ (`useExamTemplates`). */
export function mapExamTemplate(response: Schemas["ExamTemplateResponse"]): ExamTemplate {
  const document = (response.structure ?? {}) as { parts?: ApiPart[] };
  return {
    code: response.code,
    kind: response.kindCode,
    scope: response.scope as ExamScope,
    name: "",
    partCode: nullable(response.partCode),
    subjectCode: nullable(response.subjectCode),
    structureYear: nullable(response.structureYear),
    matrixYear: nullable(response.matrixYear),
    timing: response.timing as ExamTiming,
    durationMinutes: nullable(response.durationMinutes),
    maxScore: nullable(response.maxScore),
    expertNote: nullable(response.expertNote),
    parts: mapParts(document.parts).map(templatePart),
  };
}
