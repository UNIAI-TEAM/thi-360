import { z } from "zod";
import type { CatalogOption, CombinationOption } from "@/features/content/types";
import { splitEvenly, sum } from "./matrix";
import type { CreateExamPartInput, CreateExamRequest, ExamFormat, ExamKind, ExamTemplate } from "./types";

/**
 * Kiểm tra và dựng dữ liệu của trang Tạo bài thi (UniNote PBI-04 mục 4, 5.1–5.3) và tab Thông tin chung. Không phụ thuộc
 * React để kiểm thử riêng; chữ báo lỗi truyền vào từ `exams.create.validation`.
 */

export const EXAM_NAME_MAX = 200;
export const EXAM_CODE_MAX = 50;
export const EXAM_DESCRIPTION_MAX = 1000;
export const PART_NAME_MAX = 200;
/** Ảnh giới thiệu: chỉ nhận ảnh, tối đa 5 MB (dữ liệu mẫu không tải lên). */
export const COVER_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
const CODE_PATTERN = /^[A-Z0-9][A-Z0-9-]*$/;

/** Giá trị của ô mẫu khi kỳ thi và hình thức không có mẫu (bài lẻ QDA). */
export const NO_TEMPLATE = "NONE";

// ---------------------------------------------------------------- Thông tin chung

export interface ExamInfoFormValues {
  name: string;
  code: string;
  kind: ExamKind;
  format: ExamFormat;
  /** Mẫu theo kỳ thi và hình thức (không chọn tay); `NO_TEMPLATE` khi không có mẫu. */
  templateCode: string;
  singleSubjectCode: string;
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
  singleSubjectCode: z.string(),
  description: z.string().max(EXAM_DESCRIPTION_MAX, messages.descriptionMax),
});

/** Thông tin chung: tên, mã (duy nhất), kỳ thi, hình thức bắt buộc; bài lẻ phải chọn môn. */
export function createExamInfoSchema(messages: ExamInfoSchemaMessages, takenCodes: TakenCodes = new Set()) {
  return z
    .object({ ...infoShape(messages, takenCodes), templateCode: z.string() })
    .superRefine((values, context) => {
      if (values.format === "SINGLE" && !values.singleSubjectCode) {
        context.addIssue({ code: "custom", path: ["singleSubjectCode"], message: messages.subjectRequired });
      }
    });
}

// ---------------------------------------------------------------- cấu trúc khởi tạo

export interface CreateCombinationValues {
  code: string;
  /** Số câu từng môn theo thứ tự môn của tổ hợp. */
  subjectCounts: number[];
}

export interface CreatePartValues {
  /** Khóa ổn định cho danh sách (không gửi lên máy chủ). */
  key: string;
  name: string;
  mode: "FIXED" | "CHOICE";
  subjectCode: string;
  questionCount: number;
  durationMinutes: number;
  englishOption: boolean;
  combinations: CreateCombinationValues[];
}

export interface CreateExamFormValues {
  name: string;
  code: string;
  kind: ExamKind;
  format: ExamFormat;
  singleSubjectCode: string;
  description: string;
  parts: CreatePartValues[];
}

export interface CreateExamSchemaMessages extends ExamInfoSchemaMessages {
  partNameRequired: string;
  partNameMax: string;
  partSubjectRequired: string;
  questionCountPositive: string;
  durationPositive: string;
  noOptions: string;
  subjectCountPositive: string;
  /** Có `{actual}` và `{expected}` đã điền sẵn qua hàm. */
  allocationTotal: (actual: number, expected: number) => string;
  partsRequired: string;
}

const positiveInteger = (value: number) => Number.isInteger(value) && value > 0;

/**
 * Trang Tạo bài thi: Thông tin chung, cấu trúc khởi tạo (số câu nguyên dương, thời lượng lớn hơn 0, phần cố định có
 * môn), phần tự chọn có ít nhất một lựa chọn, tổng số câu các môn của mỗi tổ hợp bằng số câu của phần (mục 5.3).
 */
export function createExamSchema(messages: CreateExamSchemaMessages, takenCodes: TakenCodes = new Set()) {
  const part = z.object({
    key: z.string(),
    name: z.string().trim().min(1, messages.partNameRequired).max(PART_NAME_MAX, messages.partNameMax),
    mode: z.enum(["FIXED", "CHOICE"]),
    subjectCode: z.string(),
    questionCount: z.number().refine(positiveInteger, messages.questionCountPositive),
    durationMinutes: z.number().refine(positiveInteger, messages.durationPositive),
    englishOption: z.boolean(),
    combinations: z.array(
      z.object({
        code: z.string(),
        subjectCounts: z.array(z.number()),
      }),
    ),
  });
  return z
    .object({ ...infoShape(messages, takenCodes), parts: z.array(part) })
    .superRefine((values, context) => {
      if (values.format === "SINGLE" && !values.singleSubjectCode) {
        context.addIssue({ code: "custom", path: ["singleSubjectCode"], message: messages.subjectRequired });
      }
      if (values.parts.length === 0) {
        context.addIssue({ code: "custom", path: ["parts"], message: messages.partsRequired });
      }
      values.parts.forEach((item, partIndex) => {
        if (item.mode === "FIXED") {
          if (!item.subjectCode) {
            context.addIssue({ code: "custom", path: ["parts", partIndex, "subjectCode"], message: messages.partSubjectRequired });
          }
          return;
        }
        if (!item.englishOption && item.combinations.length === 0) {
          context.addIssue({ code: "custom", path: ["parts", partIndex, "combinations"], message: messages.noOptions });
        }
        item.combinations.forEach((combination, combinationIndex) => {
          const path = ["parts", partIndex, "combinations", combinationIndex, "subjectCounts"];
          if (!combination.subjectCounts.every(positiveInteger)) {
            context.addIssue({ code: "custom", path, message: messages.subjectCountPositive });
            return;
          }
          const actual = sum(combination.subjectCounts);
          if (positiveInteger(item.questionCount) && actual !== item.questionCount) {
            context.addIssue({ code: "custom", path, message: messages.allocationTotal(actual, item.questionCount) });
          }
        });
      });
    });
}

// ---------------------------------------------------------------- mẫu và giá trị mặc định

export function templateFor(templates: readonly ExamTemplate[], kind: ExamKind, format: ExamFormat): ExamTemplate | undefined {
  return templates.find((template) => template.kind === kind && template.format === format);
}

/** Tổ hợp dùng được cho kỳ thi (bản vẽ TaoBaiThi): theo kỳ thi của tổ hợp trong danh mục (KH-n, QĐ01–QĐ04, CA1–CA4). */
export function combinationsForKind(combinations: readonly CombinationOption[], kind: ExamKind): CombinationOption[] {
  return combinations.filter((combination) => combination.examKindCode === kind);
}

/** Phần/môn áp dụng của bài lẻ (bản vẽ TaoBaiThi): Toán, Văn, Khoa học (theo tổ hợp), Tiếng Anh. */
export const SINGLE_SCOPES = ["MATH", "LITERATURE", "SCIENCE", "ENGLISH"] as const;
export type SingleScope = (typeof SINGLE_SCOPES)[number];
/** Bài lẻ Khoa học: một phần tự chọn theo tổ hợp, không có nhánh Tiếng Anh. */
export const SCIENCE_SCOPE: SingleScope = "SCIENCE";
const ENGLISH = "ENGLISH";

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

/** Tổ hợp gắn sẵn khi nạp mẫu: mọi tổ hợp của kỳ thi, số câu chia đều (bản vẽ TaoBaiThi). */
function defaultCombinations(combinations: readonly CombinationOption[], questionCount: number): CreateCombinationValues[] {
  return combinations.map((combination) => ({
    code: combination.code,
    subjectCounts: defaultSubjectCounts(questionCount, combination.subjectCodes.length),
  }));
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
}

/**
 * Cấu trúc khởi tạo theo kỳ thi, hình thức, phần/môn bài lẻ (UniNote PBI-04 mục 5.1). Phần tự chọn bật nhánh Tiếng Anh
 * và gắn sẵn mọi tổ hợp của kỳ thi; bài lẻ lấy số câu và thời lượng của phần tương ứng trong mẫu FULL.
 */
export function initialParts({ templates, kind, format, singleScope, combinations, scopeName }: InitialPartsInput): CreatePartValues[] {
  const full = templateFor(templates, kind, "FULL");
  const choiceSource = full?.parts.find((part) => part.mode === "CHOICE");
  if (format === "SINGLE") {
    const science = singleScope === SCIENCE_SCOPE;
    const english = singleScope === ENGLISH;
    const source =
      science || english ? choiceSource : full?.parts.find((part) => part.mode === "FIXED" && part.subjectCode === singleScope);
    const fallback = templateFor(templates, kind, "SINGLE")?.parts[0];
    const questionCount = source?.questionCount ?? fallback?.questionCount ?? 0;
    return [
      {
        key: newPartKey(),
        name: singleScope ? scopeName(singleScope) : "",
        mode: science ? "CHOICE" : "FIXED",
        subjectCode: science ? "" : singleScope,
        questionCount,
        durationMinutes: source?.durationMinutes ?? fallback?.durationMinutes ?? 0,
        englishOption: false,
        combinations: science ? defaultCombinations(combinations, questionCount) : [],
      },
    ];
  }
  return (full?.parts ?? []).map((part) => ({
    key: newPartKey(),
    name: part.name,
    mode: part.mode,
    subjectCode: part.mode === "FIXED" ? (part.subjectCode ?? "") : "",
    questionCount: part.questionCount,
    durationMinutes: part.durationMinutes ?? 0,
    englishOption: part.mode === "CHOICE",
    combinations: part.mode === "CHOICE" ? defaultCombinations(combinations, part.questionCount) : [],
  }));
}

/** Phần trống thêm vào bài FULL (bản vẽ: "Thêm phần"). */
export function emptyPart(): CreatePartValues {
  return {
    key: newPartKey(),
    name: "",
    mode: "FIXED",
    subjectCode: "",
    questionCount: 0,
    durationMinutes: 0,
    englishOption: false,
    combinations: [],
  };
}

// ---------------------------------------------------------------- tổng hợp

export interface CreateExamSummary {
  partCount: number;
  totalQuestions: number;
  totalMinutes: number;
  /** Số đường lựa chọn của học viên: tích số lựa chọn của các phần tự chọn (phần cố định tính 1). */
  pathCount: number;
}

export function summarizeParts(parts: readonly CreatePartValues[]): CreateExamSummary {
  const pathCount = parts.reduce(
    (product, part) =>
      part.mode === "CHOICE" ? product * ((part.englishOption ? 1 : 0) + part.combinations.length) : product,
    1,
  );
  const safe = (value: number) => (Number.isFinite(value) && value > 0 ? value : 0);
  return {
    partCount: parts.length,
    totalQuestions: sum(parts.map((part) => safe(part.questionCount))),
    totalMinutes: sum(parts.map((part) => safe(part.durationMinutes))),
    pathCount: parts.length === 0 ? 0 : pathCount,
  };
}

/** Phần đã được người dùng sửa so với cấu trúc khởi tạo (để hỏi lại trước khi nạp mẫu khác). */
export function partsEdited(parts: readonly CreatePartValues[], baseline: readonly CreatePartValues[]): boolean {
  const strip = (items: readonly CreatePartValues[]) =>
    items.map((part) => [part.name, part.mode, part.subjectCode, part.questionCount, part.durationMinutes, part.englishOption, part.combinations]);
  return JSON.stringify(strip(parts)) !== JSON.stringify(strip(baseline));
}

// ---------------------------------------------------------------- gửi lên máy chủ

export function toCreateExamRequest(values: CreateExamFormValues, templates: readonly ExamTemplate[]): CreateExamRequest {
  const template = templateFor(templates, values.kind, values.format);
  const parts: CreateExamPartInput[] = values.parts.map((part) => ({
    name: part.name.trim(),
    questionCount: part.questionCount,
    durationMinutes: part.durationMinutes,
    mode: part.mode,
    subjectCode: part.mode === "FIXED" ? part.subjectCode : null,
    englishOption: part.mode === "CHOICE" && part.englishOption,
    combinations: part.mode === "CHOICE" ? part.combinations.map((item) => ({ ...item, subjectCounts: [...item.subjectCounts] })) : [],
  }));
  return {
    name: values.name.trim(),
    code: values.code.trim().toUpperCase(),
    kind: values.kind,
    format: values.format,
    templateCode: template?.code ?? null,
    singleSubjectCode: values.format === "SINGLE" ? values.singleSubjectCode : null,
    description: values.description.trim() || null,
    parts,
  };
}

/** Môn chọn được cho bài lẻ và phần cố định (mọi môn của danh mục). */
export function subjectOptions(subjects: readonly CatalogOption[]) {
  return subjects.map((subject) => ({ value: subject.code, label: subject.name }));
}
