import type { CheckIssue, ExamCheckResult, ExamPathRef, PathChoice } from "./review-types";
import type { ExamDetail, ExamPart, ExamSection, ExamSectionState, QuestionBlock, StructureCombination } from "./types";

/**
 * Tiện ích thuần còn dùng trên màn thật: liệt kê đường lựa chọn (xem trước đề) và cờ "năm cấu trúc khác năm
 * ma trận cần chuyên gia xác nhận". Bộ kiểm tra cấu hình phía client đã bị gỡ cùng lớp mock — server kiểm tra
 * thật qua `review-api.ts`.
 */

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

/**
 * Điều kiện lọc của khối dùng dựng câu "ngân hàng chỉ có N câu …" (2B 7.3): hình dạng câu, định dạng đáp án, vị trí
 * câu con. Máy chủ chỉ trả mã, `required`, `available` và vị trí khối; phần còn lại đọc từ cấu hình khối. Trường chưa
 * có trên khối thì để rỗng và câu bỏ cụm tương ứng.
 */
export interface BlockFilterConfig {
  itemShape: { statements: number | null; leftItems: number | null; rightItems: number | null } | null;
  answerFormats: string[];
  childPositions: Array<{ from: number; to: number }>;
}

interface BlockFilterFields {
  itemShape?: { statements?: number | null; leftItems?: number | null; rightItems?: number | null } | null;
  answerFormats?: string[] | null;
  childConditions?: Array<{ from?: number | null; to?: number | null }> | null;
}

export function blockFilterConfig(block: QuestionBlock | null | undefined): BlockFilterConfig {
  const fields: BlockFilterFields = (block as (QuestionBlock & BlockFilterFields) | null | undefined) ?? {};
  const shape = fields.itemShape;
  const positions: Array<{ from: number; to: number }> = [];
  for (const condition of fields.childConditions ?? []) {
    if (condition.from == null) continue;
    positions.push({ from: condition.from, to: condition.to ?? condition.from });
  }
  return {
    itemShape: shape
      ? { statements: shape.statements ?? null, leftItems: shape.leftItems ?? null, rightItems: shape.rightItems ?? null }
      : null,
    answerFormats: fields.answerFormats ?? [],
    childPositions: positions,
  };
}

/* ---------- Lỗi và cảnh báo của tab Kiểm tra (2B 7.3, 15.2) ---------- */

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

/**
 * Gom vấn đề theo môn, hoặc theo mã bài thi bị ảnh hưởng (`affectedCodes`): một vấn đề của nhiều mã nằm ở mọi mã của nó.
 * Giữ thứ tự xuất hiện; nhóm "không gắn" (`value = null`) xếp cuối.
 */
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

/**
 * Chỗ sửa của một vấn đề ở bước Cấu trúc: khối, môn, lựa chọn của phần, rồi phần (`?block=`, `?section=`, `?part=&option=`,
 * `?part=`). Vấn đề cả bài không có chỗ sửa riêng (`null`).
 */
export function issueStructureHref(examId: string, issue: CheckIssue): string | null {
  const base = `/exams/${encodeURIComponent(examId)}/structure`;
  if (issue.blockId) return `${base}?block=${encodeURIComponent(issue.blockId)}`;
  if (issue.sectionId) return `${base}?section=${encodeURIComponent(issue.sectionId)}`;
  if (issue.partId && issue.optionId) {
    return `${base}?part=${encodeURIComponent(issue.partId)}&option=${encodeURIComponent(issue.optionId)}`;
  }
  if (issue.partId) return `${base}?part=${encodeURIComponent(issue.partId)}`;
  return null;
}

const STATE_RANK: Record<ExamSectionState["state"], number> = { CHECKED: 0, NEEDS_CHECK: 1, INCOMPLETE: 2 };

/** Trạng thái xấu nhất của một tập môn (spec 7.1: phần và mã lấy trạng thái xấu nhất của các môn); `null` khi không có môn nào. */
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

/**
 * Bảng "Theo mã và phần": mỗi phần một dòng (môn theo thứ tự, trạng thái xấu nhất, số lỗi, số cảnh báo); phần theo mã có
 * thêm một dòng cho từng mã (R-24), với số lỗi tính theo `affectedCodes`. Vấn đề không có `partId` nhưng gắn khối hoặc
 * môn vẫn tính vào phần chứa khối / môn đó.
 */
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
        name: combination.examCode ?? combination.code,
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
