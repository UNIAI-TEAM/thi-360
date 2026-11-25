import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import type { BlockField } from "./components/block-form-fields";
import { extractExamQuotaId, findExamQuotaForIssue } from "./exam-overview";
import type { CheckIssue, ExamCheckResult, ExamPathRef, PathChoice } from "./review-types";
import type { ExamDetail, ExamPart, ExamQuota, ExamSection, ExamSectionState, QuestionBlock, StructureCombination } from "./types";

/** Giới hạn dài hơn khi người dùng chọn "Kiểm tra lại với thời gian dài hơn" (mẫu gửi server). */
export const EXTENDED_CHECK_TIME_LIMIT_SECONDS = 120;

export type ChoiceNameResolver = (kind: PathChoice["kind"], code: string) => string;

const codeAsName: ChoiceNameResolver = (_kind, code) => code;

const DEFAULT_PATH_ID = "default";

export function sortedParts(parts: readonly ExamPart[]): ExamPart[] {
  return [...parts].sort((a, b) => a.order - b.order);
}

/** Liệt kê mọi đường lựa chọn: tích Descartes các lựa chọn của mọi phần tự chọn (theo thứ tự phần). */
export function enumeratePaths(parts: readonly ExamPart[], nameOf: ChoiceNameResolver = codeAsName): ExamPathRef[] {
  let combos: PathChoice[][] = [[]];
  for (const part of sortedParts(parts)) {
    if (part.mode !== "CHOICE" || part.options.length === 0) continue;
    combos = combos.flatMap((prefix) =>
      part.options.map((option) => [
        ...prefix,
        {
          partId: part.id,
          optionId: option.id,
          kind: option.kind,
          code: option.code,
          // Nhánh và tổ hợp sinh từ "chọn k trong n" (chưa có mã danh mục) mang tên riêng; còn lại lấy theo danh mục.
          name: option.name ?? (option.kind === "BRANCH" ? option.code : nameOf(option.kind, option.code)),
        },
      ]),
    );
  }
  return combos.map((choices) => ({
    pathId: choices.length ? choices.map((choice) => choice.optionId).join("+") : DEFAULT_PATH_ID,
    choices,
  }));
}

/** Năm cấu trúc khác năm ma trận (SP2E 2026 dùng ma trận 2025) mà chuyên gia chưa xác nhận chỉ tiêu. */
export function yearsNeedConfirmation(exam: Pick<ExamDetail, "structureYear" | "matrixYear" | "yearsConfirmed">): boolean {
  return (
    exam.structureYear !== null && exam.matrixYear !== null && exam.structureYear !== exam.matrixYear && !exam.yearsConfirmed
  );
}

/** Khối, môn và phần chứa một khối theo id, tìm trong phần chung, `subjectPool`, môn của lựa chọn và khối của lựa chọn. */
export interface BlockContext {
  block: QuestionBlock;
  section: ExamSection | null;
  part: ExamPart;
  optionId: string | null;
}

export function findBlockContext(exam: Pick<ExamDetail, "parts">, blockId: string | null | undefined): BlockContext | null {
  if (!blockId) return null;
  for (const part of exam.parts) {
    const groups: Array<{ sections: ExamSection[]; optionId: string | null; blocks?: QuestionBlock[] }> = [
      { sections: part.sections, optionId: null },
      { sections: part.subjectPool ?? [], optionId: null },
      ...part.options.map((option) => ({ sections: option.sections, optionId: option.id, blocks: option.blocks })),
    ];
    for (const group of groups) {
      for (const section of group.sections) {
        const block = section.blocks.find((item) => item.id === blockId);
        if (block) return { block, section, part, optionId: group.optionId };
      }
      const block = group.blocks?.find((item) => item.id === blockId);
      if (block) return { block, section: null, part, optionId: group.optionId };
    }
  }
  return null;
}

export interface BlockFilterConfig {
  itemShape: { statements: number | null; leftItems: number | null; rightItems: number | null } | null;
  answerFormats: string[];
  childPositions: Array<{ from: number; to: number }>;
}

export function blockFilterConfig(block: QuestionBlock | null | undefined): BlockFilterConfig {
  const shape = block?.itemShape;
  // Vị trí câu con đếm từ 1; `api-mapping` điền 0 khi máy chủ thiếu số, điều kiện đó bị bỏ.
  const childPositions = (block?.childConditions ?? [])
    .filter((condition) => condition.from >= 1)
    .map((condition) => ({ from: condition.from, to: Math.max(condition.from, condition.to) }));
  return {
    itemShape: shape
      ? { statements: shape.statements ?? null, leftItems: shape.leftItems ?? null, rightItems: shape.rightItems ?? null }
      : null,
    answerFormats: block?.answerFormats ?? [],
    childPositions,
  };
}


/** Mọi vấn đề của lần kiểm tra, mỗi khóa một lần: các nhóm kiểm tra cộng vấn đề của từng đường. */
export function collectCheckIssues(result: Pick<ExamCheckResult, "groups" | "paths">): CheckIssue[] {
  const seen = new Map<string, CheckIssue>();
  const add = (issue: CheckIssue) => {
    if (!seen.has(issue.key)) seen.set(issue.key, issue);
  };
  result.groups.forEach((group) => group.issues.forEach(add));
  result.paths.forEach((path) => path.issues.forEach(add));
  return [...seen.values()];
}

export interface IssuesBySeverity {
  /** Lỗi xác định: chặn công bố. */
  errors: CheckIssue[];
  /** Chưa tìm được phương án trong giới hạn xử lý: chưa kết luận được. */
  unconfirmed: CheckIssue[];
  /** Cảnh báo: không chặn công bố. */
  warnings: CheckIssue[];
}

export function splitBySeverity(issues: readonly CheckIssue[]): IssuesBySeverity {
  return {
    errors: issues.filter((issue) => issue.severity === "ERROR"),
    unconfirmed: issues.filter((issue) => issue.severity === "UNCONFIRMED"),
    warnings: issues.filter((issue) => issue.severity === "WARNING"),
  };
}

export type IssueGrouping = "subject" | "code";

export interface IssueGroup {
  key: string;
  /** Mã môn (nhóm theo môn) hoặc mã bài thi (nhóm theo mã); `null`: vấn đề không gắn môn hoặc không gắn mã. */
  value: string | null;
  issues: CheckIssue[];
}

function foldCode(value: string): string {
  return value.replace(/\u0110/g, "D").replace(/\u0111/g, "d").toLowerCase();
}

export function combinationLabel(
  code: string,
  names: { combination?: StructureCombination; locale: string; fallback?: string | null },
): string {
  const { combination, locale, fallback } = names;
  const localized = normalizeUiLocale(locale) === "en" ? combination?.nameEn : combination?.nameVi;
  const foldedCode = foldCode(code);
  const name = [localized, fallback].map((item) => item?.trim()).find((item) => item && foldCode(item) !== foldedCode);
  if (!name) return code;
  return foldCode(name).startsWith(foldedCode) ? name : `${code} · ${name}`;
}

export function groupIssues(issues: readonly CheckIssue[], by: IssueGrouping): IssueGroup[] {
  const groups = new Map<string, IssueGroup>();
  const put = (value: string | null, issue: CheckIssue) => {
    const key = value ?? "";
    const group = groups.get(key) ?? { key, value, issues: [] };
    group.issues.push(issue);
    groups.set(key, group);
  };
  for (const issue of issues) {
    if (by === "subject") {
      put(issue.subjectCode, issue);
    } else if (issue.affectedCodes && issue.affectedCodes.length > 0) {
      issue.affectedCodes.forEach((code) => put(code, issue));
    } else {
      put(null, issue);
    }
  }
  const all = [...groups.values()];
  return [...all.filter((group) => group.value !== null), ...all.filter((group) => group.value === null)];
}

export function issueBlockField(issue: CheckIssue): BlockField | null {
  switch (issue.code) {
    case "SET_SIZE_MISMATCH":
      return "setCount";
    case "ITEM_SHAPE_SHORTAGE":
      return "itemShape";
    case "ANSWER_FORMAT_SHORTAGE":
      return "answerFormats";
    case "CHILD_CONDITION_SHORTAGE":
      return "childConditions";
    case "SLOT_SOURCE_SHORTAGE":
      return "slots";
    case "OMITTED_BLOCK_LOCKED":
      return "omitted";
    case "BLOCK_COUNT_LOCKED":
      switch (issue.dimension) {
        case "COUNT":
          return "questionCount";
        case "SET_COUNT":
          return "setCount";
        case "SET_SIZE":
          return "setSize";
        case "TYPE":
          return "questionType";
        default:
          return null;
      }
    default:
      return null;
  }
}

export function issueStructureHref(examId: string, issue: CheckIssue): string | null {
  const base = `/exams/${encodeURIComponent(examId)}/structure`;
  if (issue.blockId) {
    const field = issueBlockField(issue);
    return `${base}?block=${encodeURIComponent(issue.blockId)}${field ? `&field=${field}` : ""}`;
  }
  if (issue.sectionId) return `${base}?section=${encodeURIComponent(issue.sectionId)}`;
  if (issue.partId && issue.optionId) {
    return `${base}?part=${encodeURIComponent(issue.partId)}&option=${encodeURIComponent(issue.optionId)}`;
  }
  if (issue.partId) return `${base}?part=${encodeURIComponent(issue.partId)}`;
  return null;
}

export function issueRuleHref(examId: string, issue: Pick<CheckIssue, "ruleId" | "sectionId">): string | null {
  if (!issue.ruleId || !issue.sectionId) return null;
  const base = `/exams/${encodeURIComponent(examId)}/structure`;
  return `${base}?section=${encodeURIComponent(issue.sectionId)}&rule=${encodeURIComponent(issue.ruleId)}`;
}

export function issueQuotaHref(
  examId: string,
  issue: Pick<CheckIssue, "code" | "key" | "dimension" | "gradeCode" | "difficultyCode">,
  quotas?: readonly ExamQuota[],
): string | null {
  if (issue.code !== "EXAM_QUOTA_DEVIATION") return null;
  const quotaId = findExamQuotaForIssue(issue, quotas)?.id ?? extractExamQuotaId(issue);
  const base = `/exams/${encodeURIComponent(examId)}/structure?exam=1`;
  return quotaId ? `${base}&quota=${encodeURIComponent(quotaId)}` : base;
}

/** Môn theo id, tìm trong phần chung, `subjectPool` và môn của mọi lựa chọn. */
export function findSectionById(exam: Pick<ExamDetail, "parts">, sectionId: string | null | undefined): ExamSection | null {
  if (!sectionId) return null;
  for (const part of exam.parts) {
    const found = partSections(part).find((section) => section.id === sectionId);
    if (found) return found;
  }
  return null;
}

const STATE_RANK: Record<ExamSectionState["state"], number> = { CHECKED: 0, NEEDS_CHECK: 1, INCOMPLETE: 2 };

export function worstSectionState(states: readonly ExamSectionState[]): ExamSectionState["state"] | null {
  let worst: ExamSectionState["state"] | null = null;
  for (const item of states) if (worst === null || STATE_RANK[item.state] > STATE_RANK[worst]) worst = item.state;
  return worst;
}

export interface UnitRow {
  key: string;
  kind: "part" | "code";
  /** Tên phần (`kind = part`) hoặc mã bài thi (`kind = code`). */
  name: string;
  partId: string;
  subjectCodes: string[];
  state: ExamSectionState["state"] | null;
  errors: number;
  warnings: number;
}

function partSections(part: ExamPart): ExamSection[] {
  return [...part.sections, ...(part.subjectPool ?? []), ...part.options.flatMap((option) => option.sections)];
}

export function buildUnitRows(
  exam: Pick<ExamDetail, "parts">,
  combinations: readonly StructureCombination[],
  sectionStates: readonly ExamSectionState[],
  issues: readonly CheckIssue[],
): UnitRow[] {
  const count = (list: readonly CheckIssue[], severity: CheckIssue["severity"]) =>
    list.filter((issue) => issue.severity === severity).length;
  const rows: UnitRow[] = [];
  /** Phần chứa vấn đề: `partId` của máy chủ, hoặc phần tìm lại qua khối / môn của vấn đề. */
  const partIdOf = (issue: CheckIssue): string | null => {
    if (issue.partId) return issue.partId;
    if (issue.blockId) return findBlockContext(exam, issue.blockId)?.part.id ?? null;
    if (issue.sectionId) {
      const owner = exam.parts.find((candidate) =>
        partSections(candidate).some((section) => section.id === issue.sectionId),
      );
      if (owner) return owner.id;
    }
    return null;
  };
  for (const part of sortedParts(exam.parts)) {
    const sections = partSections(part);
    const sectionIds = new Set(sections.map((section) => section.id));
    const states = sectionStates.filter((item) => item.partId === part.id || (item.sectionId && sectionIds.has(item.sectionId)));
    const partIssues = issues.filter((issue) => partIdOf(issue) === part.id);
    rows.push({
      key: part.id,
      kind: "part",
      name: part.name,
      partId: part.id,
      subjectCodes: [...new Set(sections.map((section) => section.subjectCode))],
      state: worstSectionState(states),
      errors: count(partIssues, "ERROR"),
      warnings: count(partIssues, "WARNING"),
    });
    for (const combination of combinations.filter((item) => item.partId === part.id)) {
      const codes = new Set([combination.code, combination.examCode].filter((code): code is string => code !== null));
      const codeIssues = issues.filter((issue) => issue.affectedCodes?.some((code) => codes.has(code)));
      rows.push({
        key: `${part.id}:${combination.code}`,
        kind: "code",
        name: combination.code,
        partId: part.id,
        subjectCodes: combination.subjectCodes,
        state: worstSectionState(states.filter((item) => item.subjectCode && combination.subjectCodes.includes(item.subjectCode))),
        errors: count(codeIssues, "ERROR"),
        warnings: count(codeIssues, "WARNING"),
      });
    }
  }
  return rows;
}
