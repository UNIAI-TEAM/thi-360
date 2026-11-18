import { quotaUnitOf } from "./exam-overview";
import { convertMatrixMode, dimensionValues, emptyMatrix, type MatrixAxes, sum, toDimension } from "./matrix";
import type { BlockMatrix, ExamQuota, ExamScope, ExamSection, MatrixUnit, TopicQuota } from "./types";

/**
 * Phân bố tham khảo của một môn và hai nút chuyển (2B 15.3, H6, H7): "Dùng ma trận tham khảo" chép số của phân bố tham
 * khảo vào ma trận bắt buộc của môn và giữ lại bản tham khảo; "Áp dụng phân bổ này" đổi chính phân bố đó thành bắt buộc.
 *
 * Phân bố tham khảo của một môn nằm ở hai nơi:
 * - chỉ tiêu cấp bài chỉ có một môn (`examQuotas`, `subjectCodes = [môn]`): mức gộp mã được ghi theo bucket (HSA Tiếng
 *   Anh: lớp 10 + 11 là 30%, lớp 12 là 70%);
 * - chiều lớp hoặc độ khó của ma trận môn đang ở mức `REFERENCE` (SP2E Hóa học theo lệnh hỏi).
 */

export type ReferenceReason =
  /** Đơn vị lệnh hỏi hoặc điểm: phân bố bắt buộc phải tính theo câu (H7). */
  | "BASIS"
  /** Chưa có số nào (chờ chuyên gia xác nhận). */
  | "NO_VALUES"
  /** Một mức gộp nhiều mã: không chép được vào ma trận theo từng mã. */
  | "MERGED_BUCKETS"
  /** Ma trận môn đang nhập chiều kia theo đơn vị khác: chép sẽ trộn hai đơn vị. */
  | "UNIT_CONFLICT"
  /** Số này đã nằm ngay trong ma trận của môn. */
  | "IN_MATRIX";

export type ReferenceEntry =
  | { kind: "quota"; key: string; quota: ExamQuota; use: ReferenceReason | null; apply: ReferenceReason | null; expertPending: boolean }
  | {
      kind: "matrix";
      key: string;
      dimension: "GRADE" | "DIFFICULTY";
      values: Array<{ code: string; value: number }>;
      unit: MatrixUnit;
      basis: "QUESTION" | "PROMPT" | "POINTS";
      use: ReferenceReason | null;
      apply: ReferenceReason | null;
      expertPending: boolean;
    };

function quotaHasValues(quota: ExamQuota): boolean {
  return quota.buckets.some((bucket) => (bucket.percent ?? bucket.count ?? 0) > 0);
}

/** Chiều kia của ma trận có số khác 0 theo đơn vị khác với `unit` không. */
function otherDimensionConflicts(matrix: BlockMatrix | null, dimension: "GRADE" | "DIFFICULTY", unit: MatrixUnit): boolean {
  if (!matrix || matrix.unit === unit) return false;
  const other = dimension === "GRADE" ? matrix.byDifficulty : matrix.byGrade;
  return sum(other.map((item) => item.value)) > 0 || matrix.cells.some((cell) => cell.value > 0);
}

/**
 * Phân bố khối lớp của HSA Tiếng Anh luôn chờ chuyên gia xác nhận (ADV:FE-3b:2): chip hiện cả khi đã có số (30/70); hai nút
 * chỉ khóa khi chưa có số.
 */
function awaitsExpert(examKind: string | null, subjectCode: string, dimension: "GRADE" | "DIFFICULTY"): boolean {
  return examKind === "HSA" && subjectCode === "ANH" && dimension === "GRADE";
}

/** Các phân bố tham khảo của môn (chỉ tiêu cấp bài một môn, chiều `REFERENCE` của ma trận môn). */
export function referenceEntries(section: ExamSection, examQuotas: readonly ExamQuota[], examKind: string | null = null): ReferenceEntry[] {
  const entries: ReferenceEntry[] = [];
  for (const quota of examQuotas) {
    if (quota.enforcement !== "REFERENCE" || quota.subjectCodes?.length !== 1 || quota.subjectCodes[0] !== section.subjectCode) continue;
    const merged = quota.buckets.some((bucket) => bucket.codes.length !== 1);
    const has = quotaHasValues(quota);
    entries.push({
      kind: "quota",
      key: `quota-${quota.id}`,
      quota,
      use: !has ? "NO_VALUES" : quota.basis !== "QUESTION" ? "BASIS" : merged ? "MERGED_BUCKETS" : otherDimensionConflicts(section.matrix, quota.dimension, quotaUnitOf(quota)) ? "UNIT_CONFLICT" : null,
      // Phân bố bắt buộc chỉ nhận đơn vị câu (H7): bản tham khảo tính theo lệnh hỏi hoặc điểm không đặt Bắt buộc được (R1-12).
      apply: quota.basis !== "QUESTION" ? "BASIS" : has ? null : "NO_VALUES",
      expertPending: awaitsExpert(examKind, section.subjectCode, quota.dimension),
    });
  }
  const matrix = section.matrix;
  if (matrix) {
    const basis = matrix.basis ?? "QUESTION";
    const dimensions: Array<{ dimension: "GRADE" | "DIFFICULTY"; enforcement: BlockMatrix["gradeEnforcement"]; values: Array<{ code: string; value: number }> }> = [
      { dimension: "GRADE", enforcement: matrix.gradeEnforcement, values: matrix.byGrade },
      { dimension: "DIFFICULTY", enforcement: matrix.difficultyEnforcement, values: matrix.byDifficulty },
    ];
    for (const item of dimensions) {
      if (item.enforcement !== "REFERENCE") continue;
      // Ma trận kết hợp lớp × độ khó có một mức cho cả hai chiều: bản tham khảo là cả lưới, chỉ nêu một lần.
      if (matrix.mode === "COMBINED" && item.dimension === "DIFFICULTY") continue;
      const has = item.values.some((value) => value.value > 0) || (matrix.mode === "COMBINED" && matrix.cells.some((cell) => cell.value > 0));
      entries.push({
        kind: "matrix",
        key: `matrix-${item.dimension}`,
        dimension: item.dimension,
        values: item.values,
        unit: matrix.unit,
        basis,
        use: basis !== "QUESTION" ? "BASIS" : "IN_MATRIX",
        apply: basis !== "QUESTION" ? "BASIS" : has ? null : "NO_VALUES",
        expertPending: awaitsExpert(examKind, section.subjectCode, item.dimension),
      });
    }
  }
  return entries;
}

/**
 * Chép số của chỉ tiêu tham khảo vào ma trận bắt buộc của môn theo từng mã (ma trận riêng từng chiều, đơn vị câu); chỉ
 * tiêu cấp bài giữ nguyên, tức là giữ lại bản tham khảo. Chiều kia giữ số và mức của nó.
 */
export function referenceToMatrix(section: ExamSection, quota: ExamQuota, axes: MatrixAxes): BlockMatrix {
  const grade = quota.dimension === "GRADE";
  const codes = grade ? axes.grades : axes.difficulties;
  const base: BlockMatrix = section.matrix
    ? section.matrix.mode === "SEPARATE"
      ? section.matrix
      : convertMatrixMode(section.matrix, "SEPARATE", axes)
    : { ...emptyMatrix(), mode: "SEPARATE" };
  const values = codes.map((code) => {
    const bucket = quota.buckets.find((item) => item.codes.length === 1 && item.codes[0] === code);
    return bucket?.percent ?? bucket?.count ?? 0;
  });
  const otherCodes = grade ? axes.difficulties : axes.grades;
  const otherKey = grade ? "byDifficulty" : "byGrade";
  const otherEnforcementKey = grade ? "difficultyEnforcement" : "gradeEnforcement";
  const otherHasValues = sum(dimensionValues(base[otherKey], otherCodes)) > 0;
  return {
    ...base,
    unit: quotaUnitOf(quota),
    basis: "QUESTION",
    [grade ? "byGrade" : "byDifficulty"]: toDimension(values, codes),
    [otherKey]: otherHasValues ? base[otherKey] : [],
    // Chiều kia chưa có số thì để ở mức Tham khảo (chưa khai, `validateMatrix` bỏ qua): môn không có ma trận riêng mà chép
    // một chiều sẽ không bị chặn lưu vì chiều người dùng chưa đụng tới (R1-08).
    [otherEnforcementKey]: otherHasValues ? base[otherEnforcementKey] : "REFERENCE",
    [grade ? "gradeEnforcement" : "difficultyEnforcement"]: "REQUIRED",
  };
}

// ---------------------------------------------------------------- mạch đối chiếu theo lệnh (V-SAT, spec 5.3, H44)

export interface StrandRow {
  id: string;
  name: string;
  commands: number;
  /** Chưa gắn MAPID nào: trạng thái "Chờ mapping" (H31). */
  pending: boolean;
}

export interface StrandTable {
  rows: StrandRow[];
  /** Tổng số lệnh của các mạch: mẫu số của bảng. */
  total: number;
  /** Độ khó theo lệnh của môn (đối chiếu), theo thứ tự khai; rỗng khi không có. */
  difficulty: Array<{ code: string; value: number }>;
}

function isStrandQuota(quota: TopicQuota): boolean {
  return quota.basis === "PROMPT" && quota.enforcement === "REFERENCE";
}

function promptDifficulty(section: ExamSection, examQuotas: readonly ExamQuota[]): StrandTable["difficulty"] {
  const quota = examQuotas.find(
    (item) =>
      item.basis === "PROMPT" &&
      item.dimension === "DIFFICULTY" &&
      (item.subjectCodes?.length ? item.subjectCodes.includes(section.subjectCode) : true) &&
      !item.excludeSubjectCodes?.includes(section.subjectCode),
  );
  const fromQuota = (quota?.buckets ?? []).flatMap((bucket) => {
    const value = bucket.count ?? 0;
    return bucket.codes.length === 1 && value > 0 ? [{ code: bucket.codes[0]!, value }] : [];
  });
  if (fromQuota.length > 0) return fromQuota;
  const matrix = section.matrix;
  return matrix?.basis === "PROMPT" ? matrix.byDifficulty.filter((item) => item.value > 0) : [];
}

/**
 * Bảng "Mạch đối chiếu theo lệnh" của một môn (V-SAT Ngữ văn, Địa lí, Tiếng Anh, bản vẽ `BaiDocDungChung2B`): các chỉ tiêu chủ
 * đề cấp môn đặt theo lệnh (`basis = PROMPT`, chỉ ở mức `REFERENCE`). Chỉ bài cả môn có bảng này (ADV:BE-18:1): bài `PART` hay
 * môn không có mạch trả `null` để màn không vẽ gì, không báo lỗi, không khung rỗng.
 */
export function strandTable(section: ExamSection, examQuotas: readonly ExamQuota[], examScope: ExamScope): StrandTable | null {
  if (examScope !== "SUBJECT") return null;
  const strands = (section.topicQuotas ?? []).filter(isStrandQuota);
  if (strands.length === 0) return null;
  const rows = strands.map((quota) => ({ id: quota.id, name: quota.name, commands: quota.count, pending: quota.nodeIds.length === 0 }));
  return { rows, total: rows.reduce((acc, row) => acc + row.commands, 0), difficulty: promptDifficulty(section, examQuotas) };
}
