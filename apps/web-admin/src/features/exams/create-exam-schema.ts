import { z } from "zod";
import type { CatalogOption, CombinationOption, ExamKindOption } from "@/features/content/types";
import { splitEvenly, sum } from "./matrix";
import type {
  CreateExamPartInput,
  CreateExamRequest,
  ExamKind,
  ExamScope as ApiExamScope,
  ExamTemplate,
  ExamTemplateBlock,
  ExamTemplatePart,
  ExamTemplateSection,
  TemplateSubjectChoice,
  ExamTiming,
} from "./types";

/**
 * Kiểm tra và dựng dữ liệu của trang Tạo bài thi (UniNote PBI-04 mục 4, 5.1–5.3; bảy kỳ thi theo
 * `docs/specs/2026-11-16-mockup-7-ky-thi.md`) và tab Thông tin chung. Không phụ thuộc React để kiểm thử riêng; chữ báo
 * lỗi truyền vào từ `exams.create.validation`. Quy tắc theo kỳ thi đọc từ danh mục (`ExamKindOption`), không so mã.
 */

/**
 * Hình thức của bước 1 hiện tại (giao diện đang làm theo bản UniNote trước): `FULL` là Toàn bài hoặc Theo môn, `SINGLE`
 * là Theo phần. API dùng phạm vi (`ExamScope`); hai hàm dưới đổi qua lại ở ranh giới. FE-3c đổi bước 1 sang phạm vi.
 */
export type ExamFormat = "FULL" | "SINGLE";

export function formatOfScope(scope: ApiExamScope): ExamFormat {
  return scope === "PART" ? "SINGLE" : "FULL";
}

/** Phạm vi gửi lên theo hình thức: bài lẻ là Theo phần; còn lại Theo môn với kỳ thi có mẫu theo môn, không thì Toàn bài. */
export function scopeOfFormat(format: ExamFormat, kinds: readonly ExamKindOption[], kind: ExamKind): ApiExamScope {
  if (format === "SINGLE") return "PART";
  return kindOptionFor(kinds, kind)?.templateBy === "SUBJECT" ? "SUBJECT" : "FULL";
}

export const EXAM_NAME_MAX = 200;
export const EXAM_CODE_MAX = 50;
export const EXAM_DESCRIPTION_MAX = 1000;
export const PART_NAME_MAX = 200;
/** Ảnh giới thiệu: chỉ nhận ảnh, tối đa 5 MB (dữ liệu mẫu không tải lên). */
export const COVER_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
const CODE_PATTERN = /^[A-Z0-9][A-Z0-9-]*$/;

/** Giá trị của ô mẫu khi kỳ thi và hình thức không có mẫu (bài lẻ QDA, môn chưa có mẫu của SPT). */
export const NO_TEMPLATE = "NONE";
const ENGLISH = "ENGLISH";

const positiveInteger = (value: number) => Number.isInteger(value) && value > 0;
/** Cộng điểm có phần thập phân (0,25; 0,1) mà không lệch vì số thực. */
export function sumPoints(values: readonly number[]): number {
  return Math.round(sum(values) * 100) / 100;
}

// ---------------------------------------------------------------- kỳ thi và mẫu

export function kindOptionFor(kinds: readonly ExamKindOption[], kind: ExamKind): ExamKindOption | undefined {
  return kinds.find((option) => option.code === kind);
}

/** Kỳ thi có mẫu theo môn (SPT, SP2E, THPTQG): bài thi phải có môn. */
export function isSubjectKind(kinds: readonly ExamKindOption[], kind: ExamKind): boolean {
  return kindOptionFor(kinds, kind)?.templateBy === "SUBJECT";
}

/** Hình thức kỳ thi cho phép, suy từ phạm vi (HSA, QDA có Toàn bài và bài lẻ; kỳ thi khác chỉ Toàn bài). Không có trong danh mục: cả hai. */
export function formatsForKind(kinds: readonly ExamKindOption[], kind: ExamKind): ExamFormat[] {
  const option = kindOptionFor(kinds, kind);
  if (!option) return ["FULL", "SINGLE"];
  const formats = new Set(option.scopes.map(formatOfScope));
  return (["FULL", "SINGLE"] as const).filter((format) => formats.has(format));
}

export function templateFor(templates: readonly ExamTemplate[], kind: ExamKind, format: ExamFormat): ExamTemplate | undefined {
  return templates.find((template) => template.kind === kind && formatOfScope(template.scope) === format);
}

/** Mẫu theo môn của kỳ thi, năm cấu trúc mới nhất trước. */
export function subjectTemplates(
  templates: readonly ExamTemplate[],
  kind: ExamKind,
  format: ExamFormat,
  subjectCode: string,
): ExamTemplate[] {
  return templates
    .filter((template) => template.kind === kind && formatOfScope(template.scope) === format && template.subjectCode === subjectCode)
    .sort((a, b) => (b.structureYear ?? 0) - (a.structureYear ?? 0));
}

/** Số mẫu theo từng môn của kỳ thi (để ô chọn môn nêu môn nào có mẫu). */
export function templateCountBySubject(
  templates: readonly ExamTemplate[],
  kind: ExamKind,
  format: ExamFormat,
): Map<string, number> {
  const counts = new Map<string, number>();
  for (const template of templates) {
    if (template.kind !== kind || formatOfScope(template.scope) !== format || !template.subjectCode) continue;
    counts.set(template.subjectCode, (counts.get(template.subjectCode) ?? 0) + 1);
  }
  return counts;
}

/** Môn mặc định khi chọn kỳ thi có mẫu theo môn: môn đầu tiên của danh mục có mẫu; không có thì để trống. */
export function defaultSubjectFor(
  templates: readonly ExamTemplate[],
  subjects: readonly CatalogOption[],
  kind: ExamKind,
  format: ExamFormat,
): string {
  const counts = templateCountBySubject(templates, kind, format);
  return subjects.find((subject) => counts.has(subject.code))?.code ?? "";
}

export interface ExamScope {
  kind: ExamKind;
  format: ExamFormat;
  /** Phần/môn của bài lẻ (`SINGLE_SCOPES`); bài FULL để trống. */
  singleScope: string;
  /** Môn của bài với kỳ thi có mẫu theo môn; kỳ thi khác để trống. */
  subjectCode: string;
  /** Mã mẫu đã chọn hoặc `NO_TEMPLATE`. */
  templateCode: string;
}

/**
 * Mẫu mặc định của một phạm vi: kỳ thi theo môn lấy mẫu năm mới nhất của môn; kỳ thi theo kỳ lấy mẫu của hình thức.
 * Trả `NO_TEMPLATE` khi không có mẫu (bài lẻ QDA, môn chưa có mẫu).
 */
export function defaultTemplateCode(
  templates: readonly ExamTemplate[],
  kinds: readonly ExamKindOption[],
  scope: Pick<ExamScope, "kind" | "format" | "subjectCode">,
): string {
  if (isSubjectKind(kinds, scope.kind)) {
    if (!scope.subjectCode) return NO_TEMPLATE;
    return subjectTemplates(templates, scope.kind, scope.format, scope.subjectCode)[0]?.code ?? NO_TEMPLATE;
  }
  return templateFor(templates, scope.kind, scope.format)?.code ?? NO_TEMPLATE;
}

export function findTemplate(templates: readonly ExamTemplate[], code: string | null | undefined): ExamTemplate | undefined {
  return code && code !== NO_TEMPLATE ? templates.find((template) => template.code === code) : undefined;
}

/** Năm cấu trúc khác năm ma trận của mẫu (SP2E Toán 2026 dùng ma trận 2025): cần chuyên gia xác nhận chỉ tiêu. */
export function yearsDiffer(template: Pick<ExamTemplate, "structureYear" | "matrixYear"> | null | undefined): boolean {
  const structureYear = template?.structureYear ?? null;
  const matrixYear = template?.matrixYear ?? null;
  return structureYear !== null && matrixYear !== null && structureYear !== matrixYear;
}

/**
 * Phạm vi mới sau một thay đổi ở Thông tin chung: hình thức không có trong kỳ thi thì về hình thức đầu tiên; bài lẻ
 * giữ phần/môn đang chọn (mặc định Toán); kỳ thi theo môn giữ môn nếu kỳ thi mới có mẫu cho môn đó, không thì lấy môn
 * đầu tiên có mẫu; mẫu chọn tay thì giữ, đổi thứ khác thì về mẫu mặc định.
 */
export function resolveScope(
  templates: readonly ExamTemplate[],
  kinds: readonly ExamKindOption[],
  subjects: readonly CatalogOption[],
  current: ExamScope,
  change: Partial<ExamScope>,
): ExamScope {
  const kind = change.kind ?? current.kind;
  const formats = formatsForKind(kinds, kind);
  const requestedFormat = change.format ?? current.format;
  const format = formats.includes(requestedFormat) ? requestedFormat : (formats[0] ?? "FULL");
  const singleScope = format === "SINGLE" ? (change.singleScope ?? (current.singleScope || SINGLE_SCOPES[0])) : "";
  let subjectCode = "";
  if (isSubjectKind(kinds, kind)) {
    subjectCode = change.subjectCode ?? current.subjectCode;
    const keep = subjectCode && (change.subjectCode !== undefined || templateCountBySubject(templates, kind, format).has(subjectCode));
    if (!keep) subjectCode = defaultSubjectFor(templates, subjects, kind, format);
  }
  const explicit = change.templateCode !== undefined && change.kind === undefined && change.format === undefined;
  const templateCode =
    explicit && change.subjectCode === undefined
      ? (change.templateCode ?? NO_TEMPLATE)
      : defaultTemplateCode(templates, kinds, { kind, format, subjectCode });
  return { kind, format, singleScope, subjectCode, templateCode };
}

/** Thời lượng và thang điểm nạp theo mẫu; không có mẫu thì theo kỳ thi. */
export interface ScopeDefaults {
  timing: ExamTiming;
  /** Thời lượng chung (phút) khi `timing = SHARED`; 0 khi mẫu chưa ghi. */
  sharedDuration: number;
  maxScore: number | null;
  /** Mẫu chia điểm theo phần (BCA 25 / 60 / 15). */
  pointsByPart: boolean;
}

export function scopeDefaults(
  template: ExamTemplate | undefined,
  kind: ExamKindOption | undefined,
  format: ExamFormat,
): ScopeDefaults {
  const timing = template?.timing ?? kind?.timing ?? "PER_PART";
  return {
    timing,
    sharedDuration: timing === "SHARED" ? (template?.durationMinutes ?? 0) : 0,
    // Bài lẻ không có mẫu: thang của cả kỳ thi không dùng được cho một phần, người tạo nhập.
    maxScore: template ? (template.maxScore ?? kind?.maxScore ?? null) : format === "SINGLE" ? null : (kind?.maxScore ?? null),
    pointsByPart: (template?.parts ?? []).some((part) => part.points !== undefined && part.points !== null),
  };
}

/** Tổ hợp dùng được cho kỳ thi (bản vẽ TaoBaiThi): theo kỳ thi của tổ hợp trong danh mục (KH-n, QĐ01–QĐ04, CA1–CA4). */
export function combinationsForKind(combinations: readonly CombinationOption[], kind: ExamKind): CombinationOption[] {
  return combinations.filter((combination) => combination.examKindCode === kind);
}

// ---------------------------------------------------------------- Thông tin chung

export interface ExamInfoFormValues {
  name: string;
  code: string;
  kind: ExamKind;
  format: ExamFormat;
  /** Mẫu theo kỳ thi, hình thức và môn; `NO_TEMPLATE` khi không có mẫu. */
  templateCode: string;
  singleSubjectCode: string;
  /** Môn của bài (kỳ thi có mẫu theo môn); kỳ thi khác để trống. */
  subjectCode: string;
  /** Cách tính thời lượng theo mẫu (không sửa tay). */
  timing: ExamTiming;
  /** Thời lượng chung (phút), chỉ dùng khi `timing = SHARED`. */
  sharedDuration: number;
  /** Thang điểm; `null` khi để trống (kỳ thi không có thang cố định như V-ACT). */
  maxScore: number | null;
  /** Năm cấu trúc khác năm ma trận: chuyên gia đã xác nhận chỉ tiêu. */
  yearsConfirmed: boolean;
  description: string;
}

export interface ExamInfoSchemaMessages {
  nameRequired: string;
  nameMax: string;
  codeRequired: string;
  codeInvalid: string;
  codeMax: string;
  codeTaken: string;
  subjectRequired: string;
  kindSubjectRequired: string;
  sharedDurationPositive: string;
  maxScoreRequired: string;
  maxScorePositive: string;
  descriptionMax: string;
}

/** Mã đã biết là trùng (409 từ máy chủ), so không phân biệt hoa thường. */
export type TakenCodes = ReadonlySet<string>;

const infoShape = (messages: ExamInfoSchemaMessages, takenCodes: TakenCodes) => ({
  name: z.string().trim().min(1, messages.nameRequired).max(EXAM_NAME_MAX, messages.nameMax),
  code: z
    .string()
    .trim()
    .min(1, messages.codeRequired)
    .max(EXAM_CODE_MAX, messages.codeMax)
    .transform((value) => value.toUpperCase())
    .refine((value) => CODE_PATTERN.test(value), messages.codeInvalid)
    .refine((value) => !takenCodes.has(value), messages.codeTaken),
  kind: z.string().min(1),
  format: z.enum(["FULL", "SINGLE"]),
  templateCode: z.string(),
  singleSubjectCode: z.string(),
  subjectCode: z.string(),
  timing: z.enum(["PER_PART", "SHARED"]),
  sharedDuration: z.number(),
  maxScore: z.number().nullable(),
  yearsConfirmed: z.boolean(),
  description: z.string().max(EXAM_DESCRIPTION_MAX, messages.descriptionMax),
});

type InfoValues = Pick<
  ExamInfoFormValues,
  "kind" | "format" | "singleSubjectCode" | "subjectCode" | "timing" | "sharedDuration" | "maxScore"
>;

/**
 * Quy tắc chung của Thông tin chung: bài lẻ chọn phần/môn; kỳ thi theo môn chọn môn; bài dùng thời lượng chung có
 * thời lượng nguyên dương; thang điểm lớn hơn 0, chỉ để trống được khi kỳ thi không có thang cố định (V-ACT).
 */
function refineInfo(
  values: InfoValues,
  context: z.RefinementCtx,
  messages: ExamInfoSchemaMessages,
  kinds: readonly ExamKindOption[],
) {
  const kind = kindOptionFor(kinds, values.kind);
  if (values.format === "SINGLE" && !values.singleSubjectCode) {
    context.addIssue({ code: "custom", path: ["singleSubjectCode"], message: messages.subjectRequired });
  }
  if (kind?.templateBy === "SUBJECT" && !values.subjectCode) {
    context.addIssue({ code: "custom", path: ["subjectCode"], message: messages.kindSubjectRequired });
  }
  if (values.timing === "SHARED" && !positiveInteger(values.sharedDuration)) {
    context.addIssue({ code: "custom", path: ["sharedDuration"], message: messages.sharedDurationPositive });
  }
  if (values.maxScore === null) {
    if (kind && kind.maxScore !== null) {
      context.addIssue({ code: "custom", path: ["maxScore"], message: messages.maxScoreRequired });
    }
  } else if (!(values.maxScore > 0)) {
    context.addIssue({ code: "custom", path: ["maxScore"], message: messages.maxScorePositive });
  }
}

/** Thông tin chung: tên, mã (duy nhất), kỳ thi, hình thức bắt buộc; bài lẻ chọn môn; thời lượng chung, thang điểm. */
export function createExamInfoSchema(
  messages: ExamInfoSchemaMessages,
  takenCodes: TakenCodes = new Set(),
  kinds: readonly ExamKindOption[] = [],
) {
  return z
    .object(infoShape(messages, takenCodes))
    .superRefine((values, context) => refineInfo(values, context, messages, kinds));
}

// ---------------------------------------------------------------- cấu trúc khởi tạo

export interface CreateCombinationValues {
  code: string;
  /** Số câu từng môn theo thứ tự môn của tổ hợp. */
  subjectCounts: number[];
  /** Khối gợi ý của mẫu (tổ hợp một môn CA1–CA4). */
  blocks?: ExamTemplateBlock[];
  /** `false`: số câu từng môn chờ chuyên gia xác nhận (lấy từ mẫu). */
  expertConfirmed?: boolean;
}

/** Một môn của phần nhiều môn (BCA Phần 2) hoặc của phần chung trong phần tự chọn (THPTQG Tin học). */
export interface CreateSectionValues {
  subjectCode: string;
  questionCount: number;
  blocks: ExamTemplateBlock[];
  /** Phạm vi MAPID và chỉ tiêu cấp môn của mẫu (B9); bỏ trống: môn không có chỉ tiêu. Giữ nguyên khi gửi lên. */
  knowledgeScope?: ExamTemplateSection["knowledgeScope"];
  answerQuotas?: ExamTemplateSection["answerQuotas"];
  matrix?: ExamTemplateSection["matrix"];
  groupOrder?: ExamTemplateSection["groupOrder"];
}

/** Phương án chọn môn của mẫu (HSA Khoa học) giữ nguyên từ mẫu; chưa có ô soạn ở trang Tạo bài thi (FE-3c). */
export type CreateSubjectChoiceValues = { code: string; name?: string | null } & TemplateSubjectChoice;

/** Nhánh nội dung của phần tự chọn (THPTQG Tin học: Khoa học máy tính, Tin học ứng dụng). */
export interface CreateBranchValues {
  code: string;
  name: string;
  questionCount: number;
  blocks: ExamTemplateBlock[];
}

export interface CreatePartValues {
  /** Khóa ổn định cho danh sách (không gửi lên máy chủ). */
  key: string;
  name: string;
  mode: "FIXED" | "CHOICE";
  /** Môn của phần cố định một môn; phần nhiều môn và phần tự chọn để trống. */
  subjectCode: string;
  questionCount: number;
  /** Bỏ qua khi bài dùng thời lượng chung. */
  durationMinutes: number;
  /** Điểm của phần khi chia điểm theo phần; `null` khi chưa nhập. */
  points: number | null;
  englishOption: boolean;
  /** Phần tự chọn cho bật nhánh Tiếng Anh (mẫu không gắn sẵn lựa chọn, hoặc gắn sẵn Tiếng Anh). Không gửi lên. */
  englishAvailable: boolean;
  combinations: CreateCombinationValues[];
  /** Phần tự chọn có phương án chọn môn (HSA Khoa học, thay cho 10 tổ hợp KH-n); `null`: không có. */
  subjectChoice: CreateSubjectChoiceValues | null;
  /** Phần cố định nhiều môn, hoặc phần chung của phần tự chọn. */
  sections: CreateSectionValues[];
  branches: CreateBranchValues[];
  /** Khối gợi ý của mẫu cho phần cố định một môn. */
  blocks: ExamTemplateBlock[];
}

export interface CreateExamFormValues extends ExamInfoFormValues {
  /** Chia điểm theo phần (BCA). Tắt thì điểm các phần không gửi lên. */
  pointsByPart: boolean;
  parts: CreatePartValues[];
}

export interface CreateExamSchemaMessages extends ExamInfoSchemaMessages {
  partNameRequired: string;
  partNameMax: string;
  partSubjectRequired: string;
  questionCountPositive: string;
  durationPositive: string;
  pointsPositive: string;
  noOptions: string;
  subjectCountPositive: string;
  /** Có `{actual}` và `{expected}` đã điền sẵn qua hàm. */
  allocationTotal: (actual: number, expected: number) => string;
  /** Phần chung có `{common}` câu, không còn câu cho lựa chọn trong `{total}` câu của phần. */
  commonTooLarge: (common: number, total: number) => string;
  partsRequired: string;
}

/** Số câu của phần chung trong phần tự chọn; phần cố định trả 0. */
export function commonCount(part: Pick<CreatePartValues, "mode" | "sections">): number {
  return part.mode === "CHOICE" ? sum(part.sections.map((section) => section.questionCount)) : 0;
}

/** Số câu mỗi lựa chọn phải có: số câu của phần trừ phần chung. */
export function optionTarget(part: Pick<CreatePartValues, "mode" | "sections" | "questionCount">): number {
  return part.questionCount - commonCount(part);
}

const blockSchema = z.custom<ExamTemplateBlock>((value) => typeof value === "object" && value !== null);

/**
 * Trang Tạo bài thi: Thông tin chung, cấu trúc khởi tạo (số câu nguyên dương, thời lượng lớn hơn 0 khi tính theo phần,
 * điểm phần lớn hơn 0 khi chia điểm theo phần, phần cố định có môn hoặc các môn có tổng bằng số câu của phần), phần tự
 * chọn có ít nhất một lựa chọn và mỗi lựa chọn đủ số câu của phần trừ phần chung (mục 5.3).
 */
export function createExamSchema(
  messages: CreateExamSchemaMessages,
  takenCodes: TakenCodes = new Set(),
  kinds: readonly ExamKindOption[] = [],
) {
  const part = z.object({
    key: z.string(),
    name: z.string().trim().min(1, messages.partNameRequired).max(PART_NAME_MAX, messages.partNameMax),
    mode: z.enum(["FIXED", "CHOICE"]),
    subjectCode: z.string(),
    questionCount: z.number().refine(positiveInteger, messages.questionCountPositive),
    durationMinutes: z.number(),
    points: z.number().nullable(),
    englishOption: z.boolean(),
    englishAvailable: z.boolean(),
    combinations: z.array(
      z.object({
        code: z.string(),
        subjectCounts: z.array(z.number()),
        blocks: z.array(blockSchema).optional(),
        expertConfirmed: z.boolean().optional(),
      }),
    ),
    subjectChoice: z.custom<CreateSubjectChoiceValues>((value) => typeof value === "object").nullable(),
    sections: z.array(
      z.object({
        subjectCode: z.string(),
        questionCount: z.number(),
        blocks: z.array(blockSchema),
        knowledgeScope: z.custom<CreateSectionValues["knowledgeScope"]>().optional(),
        answerQuotas: z.custom<CreateSectionValues["answerQuotas"]>().optional(),
        matrix: z.custom<CreateSectionValues["matrix"]>().optional(),
        groupOrder: z.enum(["FIXED", "SHUFFLE"]).optional(),
      }),
    ),
    branches: z.array(
      z.object({ code: z.string(), name: z.string(), questionCount: z.number(), blocks: z.array(blockSchema) }),
    ),
    blocks: z.array(blockSchema),
  });
  return z
    .object({ ...infoShape(messages, takenCodes), pointsByPart: z.boolean(), parts: z.array(part) })
    .superRefine((values, context) => {
      refineInfo(values, context, messages, kinds);
      const issue = (path: (string | number)[], message: string) => context.addIssue({ code: "custom", path, message });
      if (values.parts.length === 0) issue(["parts"], messages.partsRequired);
      values.parts.forEach((item, partIndex) => {
        const base = ["parts", partIndex];
        if (values.timing === "PER_PART" && !positiveInteger(item.durationMinutes)) {
          issue([...base, "durationMinutes"], messages.durationPositive);
        }
        if (values.pointsByPart && !(item.points !== null && item.points > 0)) {
          issue([...base, "points"], messages.pointsPositive);
        }
        const totalOk = positiveInteger(item.questionCount);
        const common = sum(item.sections.map((section) => section.questionCount));
        let sectionsOk = true;
        if (item.sections.length > 0) {
          if (!item.sections.every((section) => positiveInteger(section.questionCount))) {
            issue([...base, "sections"], messages.subjectCountPositive);
            sectionsOk = false;
          } else if (item.mode === "FIXED" && totalOk && common !== item.questionCount) {
            issue([...base, "sections"], messages.allocationTotal(common, item.questionCount));
          } else if (item.mode === "CHOICE" && totalOk && common >= item.questionCount) {
            issue([...base, "sections"], messages.commonTooLarge(common, item.questionCount));
            sectionsOk = false;
          }
        }
        if (item.mode === "FIXED") {
          if (item.sections.length === 0 && !item.subjectCode) {
            issue([...base, "subjectCode"], messages.partSubjectRequired);
          }
          return;
        }
        if (!item.englishOption && item.combinations.length === 0 && item.branches.length === 0 && !item.subjectChoice) {
          issue([...base, "combinations"], messages.noOptions);
        }
        const expected = item.questionCount - common;
        const checkTotal = totalOk && sectionsOk;
        item.combinations.forEach((combination, combinationIndex) => {
          const path = [...base, "combinations", combinationIndex, "subjectCounts"];
          if (!combination.subjectCounts.every(positiveInteger)) {
            issue(path, messages.subjectCountPositive);
            return;
          }
          const actual = sum(combination.subjectCounts);
          if (checkTotal && actual !== expected) issue(path, messages.allocationTotal(actual, expected));
        });
        item.branches.forEach((branch, branchIndex) => {
          const path = [...base, "branches", branchIndex, "questionCount"];
          if (!positiveInteger(branch.questionCount)) issue(path, messages.subjectCountPositive);
          else if (checkTotal && branch.questionCount !== expected) {
            issue(path, messages.allocationTotal(branch.questionCount, expected));
          }
        });
      });
    });
}

// ---------------------------------------------------------------- mẫu và giá trị mặc định

/** Phần/môn áp dụng của bài lẻ (bản vẽ TaoBaiThi): Toán, Văn, Khoa học (theo tổ hợp), Tiếng Anh. */
export const SINGLE_SCOPES = ["MATH", "LITERATURE", "SCIENCE", "ENGLISH"] as const;
export type SingleScope = (typeof SINGLE_SCOPES)[number];
/** Bài lẻ Khoa học: một phần tự chọn theo tổ hợp, không có nhánh Tiếng Anh. */
export const SCIENCE_SCOPE: SingleScope = "SCIENCE";

export function isSingleScope(value: string): value is SingleScope {
  return (SINGLE_SCOPES as readonly string[]).includes(value);
}

/** Số câu mặc định từng môn của tổ hợp: chia đều theo phần dư lớn nhất (17–17–16 cho 50 câu ba môn, 25–25 cho hai môn). */
export function defaultSubjectCounts(questionCount: number, subjectCount: number): number[] {
  return splitEvenly(Math.max(0, questionCount), subjectCount);
}

let partKeySequence = 0;
export function newPartKey(): string {
  partKeySequence += 1;
  return `part-${partKeySequence}`;
}

/** Tổ hợp gắn sẵn khi mẫu không ghi lựa chọn: mọi tổ hợp của kỳ thi, số câu chia đều (bản vẽ TaoBaiThi). */
function defaultCombinations(combinations: readonly CombinationOption[], questionCount: number): CreateCombinationValues[] {
  return combinations.map((combination) => ({
    code: combination.code,
    subjectCounts: defaultSubjectCounts(questionCount, combination.subjectCodes.length),
  }));
}

/** Phần trống với các trường mặc định. */
function basePart(overrides: Partial<CreatePartValues>): CreatePartValues {
  return {
    key: newPartKey(),
    name: "",
    mode: "FIXED",
    subjectCode: "",
    questionCount: 0,
    durationMinutes: 0,
    points: null,
    englishOption: false,
    englishAvailable: false,
    combinations: [],
    subjectChoice: null,
    sections: [],
    branches: [],
    blocks: [],
    ...overrides,
  };
}

const cloneBlocks = (blocks: readonly ExamTemplateBlock[] | undefined): ExamTemplateBlock[] =>
  (blocks ?? []).map((block) => structuredClone(block));

/**
 * Một phần của mẫu thành phần của cấu trúc khởi tạo: phần một môn kèm khối gợi ý; phần nhiều môn (BCA Phần 2); phần tự
 * chọn có lựa chọn gắn sẵn (tổ hợp CA1–CA4, nhánh Tin học, kèm phần chung) hoặc, khi mẫu không ghi lựa chọn, nhánh
 * Tiếng Anh và mọi tổ hợp của kỳ thi chia đều.
 */
function partFromTemplate(part: ExamTemplatePart, combinations: readonly CombinationOption[]): CreatePartValues {
  const sections: CreateSectionValues[] = (part.sections ?? []).map((section) => ({
    ...structuredClone(section),
    questionCount: section.questionCount ?? 0,
    blocks: cloneBlocks(section.blocks),
  }));
  const common: CreatePartValues = basePart({
    name: part.name,
    mode: part.mode,
    questionCount: part.questionCount,
    durationMinutes: part.durationMinutes ?? 0,
    points: part.points ?? null,
    sections,
  });
  if (part.mode === "FIXED") {
    return { ...common, subjectCode: sections.length > 0 ? "" : (part.subjectCode ?? ""), blocks: cloneBlocks(part.blocks) };
  }
  const target = part.questionCount - sum(sections.map((section) => section.questionCount));
  const options = part.options ?? [];
  if (options.length === 0) {
    return { ...common, englishOption: true, englishAvailable: true, combinations: defaultCombinations(combinations, target) };
  }
  const english = options.some((option) => option.kind === "SUBJECT" && option.code === ENGLISH);
  return {
    ...common,
    englishOption: english,
    englishAvailable: english,
    combinations: options
      .filter((option) => option.kind === "COMBINATION")
      .map((option) => {
        const size = combinations.find((item) => item.code === option.code)?.subjectCodes.length ?? 1;
        return {
          code: option.code,
          subjectCounts: option.subjectCounts ? [...option.subjectCounts] : defaultSubjectCounts(target, size),
          ...(option.blocks?.length ? { blocks: cloneBlocks(option.blocks) } : {}),
          ...(option.expertConfirmed !== undefined ? { expertConfirmed: option.expertConfirmed } : {}),
        };
      }),
    subjectChoice: (() => {
      const choice = options.find((option) => option.kind === "SUBJECT_CHOICE" && option.subjectChoice);
      return choice?.subjectChoice
        ? { code: choice.code, name: choice.name ?? null, ...structuredClone(choice.subjectChoice) }
        : null;
    })(),
    branches: options
      .filter((option) => option.kind === "BRANCH")
      .map((option) => ({
        code: option.code,
        name: option.name ?? option.code,
        questionCount: option.subjectCounts?.[0] ?? target,
        blocks: cloneBlocks(option.blocks),
      })),
  };
}

export interface InitialPartsInput {
  templates: readonly ExamTemplate[];
  kind: ExamKind;
  format: ExamFormat;
  /** Phần/môn áp dụng của bài lẻ (`SINGLE_SCOPES`). */
  singleScope: string;
  /** Tổ hợp của kỳ thi (`combinationsForKind`). */
  combinations: readonly CombinationOption[];
  /** Tên phần/môn của bài lẻ ("Toán", "Khoa học"…). */
  scopeName: (scope: string) => string;
  /**
   * Mẫu đã chọn của bài FULL. Bỏ trống: mẫu FULL đầu tiên của kỳ thi; `null`: không dùng mẫu (môn chưa có mẫu của kỳ
   * thi theo môn), khi đó dựng một phần trống của `subjectCode`.
   */
  template?: ExamTemplate | null;
  subjectCode?: string;
  /** Tên môn trong danh mục, cho phần trống của môn chưa có mẫu. Bỏ trống thì dùng `scopeName`. */
  subjectName?: (code: string) => string;
}

/**
 * Cấu trúc khởi tạo theo kỳ thi, hình thức, mẫu (UniNote PBI-04 mục 5.1; spec 7 kỳ thi). Bài FULL dựng phần, môn, lựa
 * chọn và khối gợi ý từ mẫu; bài lẻ lấy số câu và thời lượng của phần tương ứng trong mẫu FULL; môn chưa có mẫu thì
 * một phần trống của môn đó để người tạo nhập số câu, thời lượng.
 */
export function initialParts({
  templates,
  kind,
  format,
  singleScope,
  combinations,
  scopeName,
  template,
  subjectCode = "",
  subjectName,
}: InitialPartsInput): CreatePartValues[] {
  if (format === "SINGLE") {
    const full = templateFor(templates, kind, "FULL");
    const choiceSource = full?.parts.find((part) => part.mode === "CHOICE");
    const science = singleScope === SCIENCE_SCOPE;
    const english = singleScope === ENGLISH;
    const source =
      science || english ? choiceSource : full?.parts.find((part) => part.mode === "FIXED" && part.subjectCode === singleScope);
    const fallback = templateFor(templates, kind, "SINGLE")?.parts[0];
    const questionCount = source?.questionCount ?? fallback?.questionCount ?? 0;
    return [
      basePart({
        name: singleScope ? scopeName(singleScope) : "",
        mode: science ? "CHOICE" : "FIXED",
        subjectCode: science ? "" : singleScope,
        questionCount,
        durationMinutes: source?.durationMinutes ?? fallback?.durationMinutes ?? 0,
        combinations: science ? defaultCombinations(combinations, questionCount) : [],
      }),
    ];
  }
  const source = template === undefined ? templateFor(templates, kind, "FULL") : template;
  if (!source) {
    return subjectCode ? [basePart({ name: (subjectName ?? scopeName)(subjectCode), subjectCode })] : [];
  }
  return source.parts.map((part) => partFromTemplate(part, combinations));
}

/** Phần trống thêm vào bài FULL (bản vẽ: "Thêm phần"). */
export function emptyPart(): CreatePartValues {
  return basePart({});
}

// ---------------------------------------------------------------- tổng hợp

export interface CreateExamSummary {
  partCount: number;
  totalQuestions: number;
  totalMinutes: number;
  /** Số đường lựa chọn của học viên: tích số lựa chọn của các phần tự chọn (phần cố định tính 1). */
  pathCount: number;
  /** Tổng điểm các phần khi chia điểm theo phần; `null` khi không chia. */
  totalPoints: number | null;
}

/** Số đường lựa chọn của một phần tự chọn: nhánh Tiếng Anh, tổ hợp đã gắn và nhánh nội dung. */
export function choicePathCount(
  part: Pick<CreatePartValues, "englishOption" | "combinations" | "branches" | "subjectChoice">,
): number {
  return (part.englishOption ? 1 : 0) + part.combinations.length + part.branches.length + (part.subjectChoice ? 1 : 0);
}

export function summarizeParts(
  parts: readonly CreatePartValues[],
  scoring: { timing?: ExamTiming; sharedDuration?: number; pointsByPart?: boolean } = {},
): CreateExamSummary {
  const pathCount = parts.reduce((product, part) => (part.mode === "CHOICE" ? product * choicePathCount(part) : product), 1);
  const safe = (value: number | null) => (value !== null && Number.isFinite(value) && value > 0 ? value : 0);
  return {
    partCount: parts.length,
    totalQuestions: sum(parts.map((part) => safe(part.questionCount))),
    totalMinutes:
      scoring.timing === "SHARED"
        ? safe(scoring.sharedDuration ?? 0)
        : sum(parts.map((part) => safe(part.durationMinutes))),
    pathCount: parts.length === 0 ? 0 : pathCount,
    totalPoints: scoring.pointsByPart ? sumPoints(parts.map((part) => safe(part.points))) : null,
  };
}

/** Phần đã được người dùng sửa so với cấu trúc khởi tạo (để hỏi lại trước khi nạp mẫu khác). */
export function partsEdited(parts: readonly CreatePartValues[], baseline: readonly CreatePartValues[]): boolean {
  const strip = (items: readonly CreatePartValues[]) =>
    items.map((part) => [
      part.name,
      part.mode,
      part.subjectCode,
      part.questionCount,
      part.durationMinutes,
      part.points,
      part.englishOption,
      part.combinations.map((item) => [item.code, item.subjectCounts]),
      part.subjectChoice ? [part.subjectChoice.code, part.subjectChoice.countsByPick] : null,
      part.sections.map((section) => [section.subjectCode, section.questionCount]),
      part.branches.map((branch) => [branch.code, branch.questionCount]),
    ]);
  return JSON.stringify(strip(parts)) !== JSON.stringify(strip(baseline));
}

// ---------------------------------------------------------------- gửi lên máy chủ

/**
 * Cấu trúc khởi tạo gửi lên máy chủ: thời lượng phần bỏ khi bài dùng thời lượng chung; điểm phần bỏ khi không chia
 * điểm theo phần; môn, phần chung, lựa chọn và khối gợi ý của mẫu đi kèm.
 */
export function partInputsFromValues(
  parts: readonly CreatePartValues[],
  scoring: { timing: ExamTiming; pointsByPart: boolean },
): CreateExamPartInput[] {
  const shared = scoring.timing === "SHARED";
  return parts.map((part) => {
    const choice = part.mode === "CHOICE";
    const multi = part.sections.length > 0;
    return {
      name: part.name.trim(),
      questionCount: part.questionCount,
      durationMinutes: shared ? null : part.durationMinutes,
      points: scoring.pointsByPart ? part.points : null,
      mode: part.mode,
      subjectCode: !choice && !multi ? part.subjectCode : null,
      englishOption: choice && part.englishOption,
      combinations: choice
        ? part.combinations.map((item) => ({
            code: item.code,
            subjectCounts: [...item.subjectCounts],
            ...(item.blocks?.length ? { blocks: item.blocks } : {}),
            ...(item.expertConfirmed !== undefined ? { expertConfirmed: item.expertConfirmed } : {}),
          }))
        : [],
      ...(choice && part.subjectChoice ? { subjectChoice: part.subjectChoice } : {}),
      ...(multi
        ? {
            sections: part.sections.map((section) => ({
              ...section,
              blocks: section.blocks.length ? section.blocks : undefined,
            })),
          }
        : {}),
      ...(!choice && !multi && part.blocks.length ? { blocks: part.blocks } : {}),
      ...(choice && part.branches.length
        ? {
            branches: part.branches.map((branch) => ({
              code: branch.code,
              name: branch.name,
              questionCount: branch.questionCount,
              ...(branch.blocks.length ? { blocks: branch.blocks } : {}),
            })),
          }
        : {}),
    };
  });
}

/**
 * Yêu cầu tạo bài (bước 1, PBI-04 mục 4). Mẫu và xác nhận năm không đi theo yêu cầu: máy chủ nạp mẫu mới nhất khớp
 * phạm vi; mẫu khác hoặc xác nhận năm đi sau bằng `applyExamTemplate` và `updateExamInfo` (xem `useCreateExam`).
 */
export function toCreateExamRequest(
  values: CreateExamFormValues,
  kinds: readonly ExamKindOption[],
): CreateExamRequest {
  const shared = values.timing === "SHARED";
  return {
    name: values.name.trim(),
    code: values.code.trim().toUpperCase(),
    kind: values.kind,
    scope: scopeOfFormat(values.format, kinds, values.kind),
    partCode: values.format === "SINGLE" ? values.singleSubjectCode : null,
    subjectCode: values.subjectCode || null,
    durationMinutes: shared ? values.sharedDuration : null,
    maxScore: values.maxScore,
    description: values.description.trim() || null,
    instructions: null,
    parts: partInputsFromValues(values.parts, values),
  };
}

/** Môn chọn được cho bài lẻ và phần cố định (mọi môn của danh mục). */
export function subjectOptions(subjects: readonly CatalogOption[]) {
  return subjects.map((subject) => ({ value: subject.code, label: subject.name }));
}
