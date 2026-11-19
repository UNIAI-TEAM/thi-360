import { apportionByPercent, countsToPercents, isFlexibleSet, type MatrixAxes } from "../matrix";
import type { AnswerQuotas, BlockMatrix, ExamSection, MatrixDimensionValue, MatrixUnit, QuestionBlock, QuotaEnforcement } from "../types";

/**
 * Mô hình của biểu mẫu Ma trận môn (FTM R-3, R-4, R-8, R-9): hàm thuần từ bản nháp của một môn tới các ô nhập và ngược lại. Màn
 * (`matrix-form.tsx`) chỉ vẽ và gọi các hàm này; không có chỗ nào khác ghi nhóm của môn MT.
 *
 * Quy ước chung:
 * - Cập nhật nhóm theo `id`, giữ thứ tự và mọi trường không chỉnh. Không xóa, không tạo nhóm: câu đơn và câu điền về 0 vẫn
 *   giữ nhóm; chùm cỡ cố định không về 0 (lỗi "Ít nhất 1 chùm" ở `matrix-blocking.ts`).
 * - Hàm không đổi gì thì trả lại đối tượng cũ (không tạo thay đổi giả ở bản nháp).
 */

export type MatrixRole = "SINGLE" | "SET" | "FILL";

/** Vai trò của nhóm trong MT; `null` với dạng ngoài ba vai trò (môn không khớp MT, xem `fit.ts`). */
export function roleOf(block: Pick<QuestionBlock, "questionType">): MatrixRole | null {
  if (block.questionType === null || block.questionType === "SINGLE_CHOICE") return "SINGLE";
  if (block.questionType === "QUESTION_SET") return "SET";
  if (block.questionType === "SHORT_ANSWER") return "FILL";
  return null;
}

/** Nhóm của từng vai trò (nhiều nhất một nhóm mỗi vai trò, R-2); vai trò đã bị xóa khỏi bài cũ là `null`. */
export interface MatrixRoleBlocks {
  single: QuestionBlock | null;
  set: QuestionBlock | null;
  fill: QuestionBlock | null;
}

export function roleBlocks(section: Pick<ExamSection, "blocks">): MatrixRoleBlocks {
  const find = (role: MatrixRole) => section.blocks.find((block) => roleOf(block) === role) ?? null;
  return { single: find("SINGLE"), set: find("SET"), fill: find("FILL") };
}

/** Các số của Cấu trúc câu đọc từ bản nháp. */
export interface MatrixStructure {
  blocks: MatrixRoleBlocks;
  /** Chùm cỡ linh hoạt (H28): chỉ có tổng câu con; `setCount`, `setSize` giữ `null`. */
  flexible: boolean;
  /** Số chùm (cỡ cố định); `null` khi không có chùm hoặc chùm cỡ linh hoạt. */
  sets: number | null;
  /** Số câu con của chùm: `sets × setSize` (cỡ cố định) hoặc tổng câu con (cỡ linh hoạt); 0 khi không có chùm. */
  children: number;
  /** Cỡ chùm cố định; `null` khi không có chùm hoặc cỡ linh hoạt. */
  setSize: number | null;
  /** Câu đơn: nhóm câu đơn. */
  single: number;
  /** Trắc nghiệm T: câu đơn cộng câu con của chùm. */
  choice: number;
  fill: number;
  /** Tổng câu đã phân bổ của ba vai trò. */
  total: number;
}

const countOf = (block: QuestionBlock | null) => block?.questionCount ?? 0;

export function structureOf(section: Pick<ExamSection, "blocks">): MatrixStructure {
  const blocks = roleBlocks(section);
  const flexible = blocks.set !== null && isFlexibleSet(blocks.set);
  const children = countOf(blocks.set);
  const single = countOf(blocks.single);
  const fill = countOf(blocks.fill);
  return {
    blocks,
    flexible,
    sets: blocks.set && !flexible ? (blocks.set.setCount ?? 0) : null,
    children,
    setSize: blocks.set && !flexible ? (blocks.set.setSize ?? 0) : null,
    single,
    choice: single + children,
    fill,
    total: single + children + fill,
  };
}

/** Ô của Cấu trúc câu mà người dùng nhập; Trắc nghiệm đơn tự tính. */
export interface StructureInput {
  /** Trắc nghiệm T (tổng, gồm câu con của chùm). */
  choice?: number;
  /** Số chùm k (chùm cỡ cố định). */
  sets?: number;
  /** Tổng câu con (chùm cỡ linh hoạt). */
  children?: number;
  /** Điền đáp án S. */
  fill?: number;
}

export interface StructureWrite {
  section: ExamSection;
  /** T nhỏ hơn số câu con: nhóm câu đơn ghi 0, màn giữ T đã gõ ở kho nhập thô và chặn lưu (R-3). */
  choiceBelowChildren: boolean;
}

function setBlock(block: QuestionBlock, patch: Partial<QuestionBlock>): QuestionBlock {
  const changed = (Object.keys(patch) as Array<keyof QuestionBlock>).some((key) => block[key] !== patch[key]);
  return changed ? { ...block, ...patch } : block;
}

/**
 * `answerQuotas` tính lại cho Cấu trúc câu mới, giữ đúng dạng đang lưu (FTM R-3, G1-6): trống thì để trống; có dòng "Bộ câu hỏi"
 * (dạng HSA-B, cả QDA Toán) thì Trắc nghiệm đơn, Bộ câu hỏi và Điền đáp án đếm riêng; không có dòng này (dạng cũ) thì câu con của
 * chùm đếm vào Trắc nghiệm. Đơn vị `PERCENT` quy lại thành % của số câu của môn. Dòng của vai trò không còn nhóm giữ nguyên.
 */
export function recomputeAnswerQuotas(quotas: AnswerQuotas | null, structure: MatrixStructure, sectionCount: number | null): AnswerQuotas | null {
  if (quotas === null) return null;
  const hsaShape = quotas.items.some((item) => item.answerType === "QUESTION_SET");
  const counts = quotas.items.map((item) => {
    if (item.answerType === "SINGLE_CHOICE") return structure.blocks.single || structure.blocks.set ? (hsaShape ? structure.single : structure.single + structure.children) : item.value;
    if (item.answerType === "QUESTION_SET") return structure.blocks.set ? structure.children : item.value;
    if (item.answerType === "SHORT_ANSWER") return structure.blocks.fill ? structure.fill : item.value;
    return item.value;
  });
  if (quotas.unit === "COUNT") return { unit: "COUNT", items: quotas.items.map((item, index) => ({ ...item, value: counts[index] ?? 0 })) };
  const percents = countsToPercents(counts, sectionCount ?? structure.total);
  return { unit: "PERCENT", items: quotas.items.map((item, index) => ({ ...item, value: percents[index] ?? 0 })) };
}

/**
 * Ghi Cấu trúc câu (R-3). `choice` mặc định là T hiện có, nên đổi số chùm giữ T và chia lại câu đơn (BCA Sử T = 10, 1 → 2 chùm: nhóm
 * chùm 2 × 3 = 6, nhóm đơn 4). Chùm cỡ cố định ghi nhất quán `setCount`, `setSize`, `questionCount = setCount × setSize`; chùm cỡ
 * linh hoạt chỉ ghi `questionCount` (tổng câu con), `setCount`, `setSize` vẫn `null`. `answerQuotas` tính lại ở đây và chỉ ở đây: gọi
 * hàm này khi người dùng đổi một ô của Cấu trúc câu, không gọi khi lưu không sửa.
 */
export function writeStructure(section: ExamSection, input: StructureInput): StructureWrite {
  const current = structureOf(section);
  const { single, set, fill } = current.blocks;
  const choice = input.choice ?? current.choice;
  const children = set ? (current.flexible ? (input.children ?? current.children) : (input.sets ?? current.sets ?? 0) * (current.setSize ?? 0)) : 0;
  const choiceBelowChildren = single !== null && choice < children;
  const singleCount = choiceBelowChildren ? 0 : choice - children;
  const fillCount = input.fill ?? current.fill;

  const blocks = section.blocks.map((block) => {
    if (single && block.id === single.id) return setBlock(block, { questionCount: singleCount });
    if (fill && block.id === fill.id) return setBlock(block, { questionCount: fillCount });
    if (set && block.id === set.id) {
      return current.flexible ? setBlock(block, { questionCount: children }) : setBlock(block, { setCount: input.sets ?? current.sets ?? 0, questionCount: children });
    }
    return block;
  });
  if (blocks.every((block, index) => block === section.blocks[index])) return { section, choiceBelowChildren };
  const next: ExamSection = { ...section, blocks };
  return { section: { ...next, answerQuotas: recomputeAnswerQuotas(section.answerQuotas, structureOf(next), section.questionCount) }, choiceBelowChildren };
}

// ---------------------------------------------------------------- khối lớp, độ khó

export type MatrixDimensionKey = "grade" | "difficulty";

const DIMENSION_FIELD = { grade: "byGrade", difficulty: "byDifficulty" } as const;
const ENFORCEMENT_FIELD = { grade: "gradeEnforcement", difficulty: "difficultyEnforcement" } as const;

/** Mức áp dụng ban đầu của ma trận mới: kỳ có mức Tham khảo hay Chỉ đối chiếu (BCA, V-ACT) ghi `REFERENCE`; kỳ khác để mặc định. */
export function initialEnforcement(mode: "FIXED_REQUIRED" | "DECLARED_REQUIRED" | "FIXED_REFERENCE" | "REFERENCE_WITH_APPLY"): QuotaEnforcement | undefined {
  return mode === "FIXED_REFERENCE" || mode === "REFERENCE_WITH_APPLY" ? "REFERENCE" : undefined;
}

export interface MatrixCellEdit {
  dimension: MatrixDimensionKey;
  code: string;
  /** Đơn vị của giá trị vừa nhập: nhập % ở cột Tỷ lệ (V-ACT) đổi ma trận sang `PERCENT`, nhập số câu đổi về `COUNT` (R-9). `null`: ô để trống. */
  unit: MatrixUnit;
  value: number | null;
}

export interface MatrixEditContext {
  /** Số câu của môn: mẫu số khi đổi đơn vị. */
  total: number;
  axes: MatrixAxes;
  /** Mức áp dụng đặt cho ma trận mới tạo (xem `initialEnforcement`). */
  enforcement?: QuotaEnforcement;
}

/**
 * Đổi đơn vị của ma trận SEPARATE: chỉ các ô đã khai được quy đổi (% → số câu theo phần dư lớn nhất, số câu → % nguyên tổng 100);
 * chiều chưa khai giữ mảng rỗng và ô để trống giữ trống, không biến thành ô 0 (khác `convertMatrixUnit` của màn MD).
 */
function convertDeclaredDimensions(matrix: BlockMatrix, unit: MatrixUnit, total: number): BlockMatrix {
  const convert = (items: readonly MatrixDimensionValue[]): MatrixDimensionValue[] => {
    const values = items.map((item) => item.value);
    const next = unit === "COUNT" ? apportionByPercent(values, total).results : countsToPercents(values, total);
    return items.map((item, index) => ({ code: item.code, value: next[index] ?? 0 }));
  };
  return { ...matrix, unit, byGrade: convert(matrix.byGrade), byDifficulty: convert(matrix.byDifficulty) };
}

/**
 * Sửa một ô của ma trận khối lớp hoặc độ khó của môn (R-4, R-8). Ma trận luôn `SEPARATE`, `COUNT` (hoặc `PERCENT` khi nhập % ở
 * V-ACT). Ô để trống không có trong mảng (khác ô 0): chiều đã khai là Bắt buộc và ô trống tính 0; chiều không khai ghi mảng rỗng.
 * Ma trận `null` đã lưu giữ `null` cho tới khi có ô được nhập.
 */
export function editMatrixCell(matrix: BlockMatrix | null, edit: MatrixCellEdit, context: MatrixEditContext): BlockMatrix | null {
  if (matrix === null && edit.value === null) return null;
  const base: BlockMatrix =
    matrix ??
    ({
      mode: "SEPARATE",
      unit: edit.unit,
      cells: [],
      byGrade: [],
      byDifficulty: [],
      ...(context.enforcement ? { gradeEnforcement: context.enforcement, difficultyEnforcement: context.enforcement } : {}),
    } satisfies BlockMatrix);
  const unified = base.mode === "SEPARATE" ? base : { ...base, mode: "SEPARATE" as const };
  const converted = unified.unit === edit.unit ? unified : convertDeclaredDimensions(unified, edit.unit, context.total);
  const codes = edit.dimension === "grade" ? context.axes.grades : context.axes.difficulties;
  const field = DIMENSION_FIELD[edit.dimension];
  const byCode = new Map(converted[field].map((item) => [item.code, item.value]));
  if (edit.value === null) byCode.delete(edit.code);
  else byCode.set(edit.code, edit.value);
  const values = codes.filter((code) => byCode.has(code)).map((code) => ({ code, value: byCode.get(code)! }));
  const extra = converted[field].filter((item) => !codes.includes(item.code));
  return { ...converted, [field]: [...values, ...extra] };
}

/** Giá trị của ô (không có trong chiều: `null`, tức ô để trống). */
export function matrixCell(matrix: BlockMatrix | null, dimension: MatrixDimensionKey, code: string): number | null {
  return matrix?.[DIMENSION_FIELD[dimension]].find((item) => item.code === code)?.value ?? null;
}

/** Chiều đã khai: có ít nhất một ô. */
export function dimensionDeclared(matrix: BlockMatrix | null, dimension: MatrixDimensionKey): boolean {
  return (matrix?.[DIMENSION_FIELD[dimension]].length ?? 0) > 0;
}

/** Mức áp dụng đang lưu của một chiều; không ghi thì Bắt buộc. */
export function dimensionEnforcement(matrix: BlockMatrix | null, dimension: MatrixDimensionKey): QuotaEnforcement {
  return matrix?.[ENFORCEMENT_FIELD[dimension]] ?? "REQUIRED";
}

/** "Áp dụng khi sinh đề" của một chiều (V-ACT, R-8): ghi mức của riêng chiều đó. Ma trận `null` không có gì để áp dụng. */
export function setDimensionEnforcement(matrix: BlockMatrix | null, dimension: MatrixDimensionKey, enforcement: QuotaEnforcement): BlockMatrix | null {
  if (matrix === null || dimensionEnforcement(matrix, dimension) === enforcement) return matrix;
  return { ...matrix, [ENFORCEMENT_FIELD[dimension]]: enforcement };
}

/** Tổng các ô đã khai của một chiều (đơn vị đang lưu: số câu, hoặc % khi `PERCENT`). */
export function dimensionTotal(matrix: BlockMatrix | null, dimension: MatrixDimensionKey): number {
  return (matrix?.[DIMENSION_FIELD[dimension]] ?? []).reduce((total, item) => total + item.value, 0);
}
