import type { ContentReference } from "@/features/content/types";
import type { ExamSection, ExamStructure, QuestionBlock } from "./types";
import type { PreviewCompliance, PreviewPaper, PreviewQuestion, TopicQuotaIndex } from "./review-types";

/** Sai lệch số thập phân nhỏ hơn mức này coi là bằng nhau (điểm và số câu máy chủ tính bằng BigDecimal). */
const EPSILON = 1e-9;

export type ComplianceLevel = "OK" | "REFERENCE_DEVIATION" | "REQUIRED_DEVIATION";

export type CriterionRef =
  | { kind: "ANSWER_TYPE" | "GRADE" | "DIFFICULTY" | "COMPETENCY" | "TOPIC"; code: string }
  | { kind: "CELL"; gradeCode: string; difficultyCode: string }
  | { kind: "EXAM"; quotaId: string; codes: string[] }
  | { kind: "UNKNOWN"; raw: string };

/** Tiêu chí của một dòng (`ANSWER_TYPE:SINGLE_CHOICE`, `CELL:G12:VD`, `EXAM:<id>:G10,G11`, ...). */
export function parseCriterion(raw: string): CriterionRef {
  const [kind = "", first = "", second = ""] = raw.split(":");
  switch (kind) {
    case "ANSWER_TYPE":
    case "GRADE":
    case "DIFFICULTY":
    case "COMPETENCY":
    case "TOPIC":
      return { kind, code: [first, second].filter(Boolean).join(":") };
    case "CELL":
      return { kind, gradeCode: first, difficultyCode: second };
    case "EXAM":
      return { kind, quotaId: first, codes: second ? second.split(",") : [] };
    default:
      return { kind: "UNKNOWN", raw };
  }
}

export type ScopeRef = { kind: "SECTION" | "BLOCK"; id: string } | { kind: "EXAM" };

/** Phạm vi của một dòng: `SECTION:<id môn>`, `BLOCK:<id khối>` hoặc `EXAM`. */
export function parseScope(raw: string): ScopeRef {
  const separator = raw.indexOf(":");
  const kind = separator < 0 ? raw : raw.slice(0, separator);
  if (kind === "SECTION" || kind === "BLOCK") return { kind, id: raw.slice(separator + 1) };
  return { kind: "EXAM" };
}

/**
 * Mã của dòng "Chưa phân loại" ở bảng đối chiếu theo lệnh (D19, H44): câu thiếu nhãn khối lớp hoặc độ khó dồn vào dòng
 * `GRADE:UNCLASSIFIED` hoặc `DIFFICULTY:UNCLASSIFIED`. Máy chủ chỉ gửi dòng này khi có lệnh thiếu nhãn (`actual > 0`),
 * `required` là `null` và `note` là `UNCLASSIFIED`.
 */
export const UNCLASSIFIED_CODE = "UNCLASSIFIED";

/** Dòng "Chưa phân loại": không có yêu cầu để so, chỉ báo số lệnh còn thiếu nhãn. */
export function isUnclassifiedRow(row: PreviewCompliance): boolean {
  const ref = parseCriterion(row.criterion);
  return (ref.kind === "GRADE" || ref.kind === "DIFFICULTY") && ref.code === UNCLASSIFIED_CODE;
}

export interface CommandDenominator {
  /** Phạm vi của các dòng (`SECTION:<id>`, `EXAM`): cùng chuỗi với `PreviewCompliance.scope`. */
  scope: string;
  /** Tổng số lệnh của mẫu: cộng yêu cầu của các dòng khối lớp, không có thì của các dòng độ khó (Địa lí, Tiếng Anh không có lớp). Phạm vi `EXAM` lấy tổng của chỉ tiêu cấp bài đầu tiên theo thứ tự máy chủ. */
  total: number;
}

/**
 * Mẫu số của bảng đối chiếu theo lệnh (VS §5.1, D21): mỗi phạm vi có dòng `PROMPT` ở mức tham khảo cho một mẫu số, ví dụ
 * Toán cả môn 67 lệnh. Dòng "Chưa phân loại" và dòng chủ đề không có yêu cầu theo lệnh nên không góp vào mẫu số. Phạm
 * vi `EXAM`: chỉ tiêu cấp bài `EXAM:<id>:<mã>` nhóm theo id, đề thường mang chỉ tiêu khối lớp lẫn độ khó (cùng tổng) nên
 * lấy chỉ tiêu đầu tiên theo thứ tự máy chủ gửi (mẫu đặt khối lớp trước), không bao giờ cộng hai chỉ tiêu hay in hai dòng.
 */
export function commandDenominators(rows: readonly PreviewCompliance[]): CommandDenominator[] {
  const byScope = new Map<string, { grade: number; difficulty: number; hasGrade: boolean; examQuotas: Map<string, number> }>();
  for (const row of rows) {
    if (row.basis !== "PROMPT" || row.enforcement !== "REFERENCE" || isUnclassifiedRow(row)) continue;
    const ref = parseCriterion(row.criterion);
    if (ref.kind === "EXAM" && parseScope(row.scope).kind === "EXAM") {
      const entry = byScope.get(row.scope) ?? { grade: 0, difficulty: 0, hasGrade: false, examQuotas: new Map<string, number>() };
      entry.examQuotas.set(ref.quotaId, (entry.examQuotas.get(ref.quotaId) ?? 0) + (row.required ?? 0));
      byScope.set(row.scope, entry);
      continue;
    }
    if (ref.kind !== "GRADE" && ref.kind !== "DIFFICULTY") continue;
    const entry = byScope.get(row.scope) ?? { grade: 0, difficulty: 0, hasGrade: false, examQuotas: new Map<string, number>() };
    if (ref.kind === "GRADE") {
      entry.grade += row.required ?? 0;
      entry.hasGrade = true;
    } else {
      entry.difficulty += row.required ?? 0;
    }
    byScope.set(row.scope, entry);
  }
  return [...byScope].map(([scope, entry]) => {
    const firstQuota = entry.examQuotas.values().next();
    return { scope, total: firstQuota.done ? (entry.hasGrade ? entry.grade : entry.difficulty) : firstQuota.value };
  });
}

/**
 * Mức lệch của một dòng. Chỉ tiêu chủ đề là "ít nhất" (K6): thực tế nhiều hơn yêu cầu vẫn đạt, chỉ thiếu mới lệch. Dòng
 * Bắt buộc lệch là lỗi, dòng Tham khảo lệch là cảnh báo không chặn.
 */
export function complianceLevel(row: PreviewCompliance): ComplianceLevel {
  if (isUnclassifiedRow(row)) return "OK";
  const atLeast = parseCriterion(row.criterion).kind === "TOPIC";
  const required = row.required ?? 0;
  const deviates = atLeast ? row.actual < required - EPSILON : Math.abs(row.actual - required) > EPSILON;
  if (!deviates) return "OK";
  return row.enforcement === "REQUIRED" ? "REQUIRED_DEVIATION" : "REFERENCE_DEVIATION";
}

/** Chênh lệch = Thực tế − Yêu cầu (R-124); chênh lệch nhỏ hơn `EPSILON` coi là 0 như quy tắc lệch ở trên. */
export function complianceDifference(row: PreviewCompliance): number {
  const difference = row.actual - (row.required ?? 0);
  return Math.abs(difference) > EPSILON ? difference : 0;
}

/**
 * Chỉ tiêu chủ đề của cả bài theo id (`TOPIC:<id>`): đi qua mọi môn của phần cố định, nhóm môn của phần theo mã và lựa
 * chọn, lấy chỉ tiêu ở môn và ở từng khối. `undefined` khi chưa có cấu trúc.
 */
export function buildTopicQuotaIndex(structure: ExamStructure | undefined): TopicQuotaIndex | undefined {
  if (!structure) return undefined;
  const index: TopicQuotaIndex = new Map();
  const addQuotas = (items: readonly (ExamSection | QuestionBlock)[] | undefined) => {
    for (const item of items ?? []) {
      for (const quota of item.topicQuotas ?? []) {
        index.set(quota.id, {
          name: quota.name,
          enforcement: quota.enforcement,
          nodeIds: quota.nodeIds,
          difficultyCodes: quota.difficultyCodes ?? [],
        });
      }
    }
  };
  const addSection = (section: ExamSection) => {
    addQuotas([section]);
    addQuotas(section.blocks);
  };
  for (const part of structure.parts) {
    part.sections.forEach(addSection);
    part.subjectPool?.forEach(addSection);
    for (const option of part.options) {
      option.sections.forEach(addSection);
      addQuotas(option.blocks);
    }
  }
  return index;
}

/**
 * Dòng chỉ tiêu chủ đề `REFERENCE` chưa gắn MAPID (H31): chờ mapping nên không có dữ liệu đối chiếu và không tính là
 * lệch; chỉ tiêu của dòng tra theo id (`TOPIC:<id>`) trong cấu trúc bài. `undefined` khi chưa tải được cấu trúc.
 */
export function topicQuotaPending(row: PreviewCompliance, quotas: TopicQuotaIndex | undefined): boolean {
  const ref = parseCriterion(row.criterion);
  if (ref.kind !== "TOPIC" || row.enforcement !== "REFERENCE") return false;
  const quota = quotas?.get(ref.code);
  return quota !== undefined && quota.enforcement === "REFERENCE" && quota.nodeIds.length === 0;
}

/** Số câu đã bỏ ghi trong `note` của máy chủ (`dropped 1`); 0 khi không bỏ câu nào. */
export function droppedCount(note: string | null): number {
  const match = /^dropped (\d+)$/.exec(note ?? "");
  return match ? Number(match[1]) : 0;
}

type NameCatalog = Pick<ContentReference, "grades" | "difficulties" | "competencies">;

/**
 * Tên các mã của chỉ tiêu cấp bài (`EXAM:<id>:G10,G11`): mỗi mã là khối lớp hoặc độ khó, quyết định theo danh mục khối
 * lớp chứ không theo chữ đầu của mã. Mã không có trong danh mục nào thì giữ nguyên mã.
 */
export function examQuotaNames(codes: readonly string[], reference: Pick<NameCatalog, "grades" | "difficulties"> | undefined): string[] {
  const grades = new Map((reference?.grades ?? []).map((option) => [option.code, option.name]));
  const difficulties = new Map((reference?.difficulties ?? []).map((option) => [option.code, option.name]));
  return codes.map((code) => grades.get(code) ?? difficulties.get(code) ?? code);
}

/** Tên năng lực theo danh mục; mã không có trong danh mục thì giữ nguyên mã. */
export function competencyName(code: string, reference: Pick<NameCatalog, "competencies"> | undefined): string {
  return reference?.competencies?.find((item) => item.code === code)?.name ?? code;
}

/**
 * Số câu được gán vào từng chỉ tiêu chủ đề: khóa là id chỉ tiêu, như `TOPIC:<id>` của bảng. Số theo đề đã đảo; `original`
 * dùng số gốc (`originalNumber`) để khớp đề đang xem thứ tự gốc (K12).
 */
export function topicAssignments(paper: PreviewPaper, original = false): Map<string, number[]> {
  const assigned = new Map<string, number[]>();
  const add = (question: PreviewQuestion) => {
    const number = original ? (question.originalNumber ?? question.number) : question.number;
    for (const id of question.assignedTopicQuotaIds) assigned.set(id, [...(assigned.get(id) ?? []), number]);
  };
  for (const part of paper.parts) {
    for (const block of part.blocks) {
      for (const item of block.items) {
        if (item.kind === "QUESTION") add(item.question);
        else item.children.forEach(add);
      }
    }
  }
  for (const numbers of assigned.values()) numbers.sort((a, b) => a - b);
  return assigned;
}
