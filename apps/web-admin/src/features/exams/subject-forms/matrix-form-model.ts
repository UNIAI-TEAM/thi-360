import type { QuestionType } from "@/features/content/types";
import { draftId } from "../components/exam-structure-draft";
import { convertValuesByUnit, countsToPercents, isFlexibleSet, type MatrixAxes, sum } from "../matrix";
import type {
  AnswerQuotas,
  BlockMatrix,
  ExamPart,
  ExamSection,
  ExamTemplate,
  ExamTemplateBlock,
  ExamTemplateSection,
  MatrixDimensionValue,
  MatrixUnit,
  QuestionBlock,
  QuotaEnforcement,
} from "../types";

type MatrixRole = "SINGLE" | "SET" | "FILL";

/** Vai trò chuẩn của nhóm trong MT; `null` với các định dạng mở rộng (Đúng/Sai, Nhiều đáp án, Điền khuyết, Ghép đôi, Tự luận). */
function roleOf(block: Pick<QuestionBlock, "questionType">): MatrixRole | null {
  if (block.questionType === null || block.questionType === "SINGLE_CHOICE") return "SINGLE";
  if (block.questionType === "QUESTION_SET") return "SET";
  if (block.questionType === "SHORT_ANSWER") return "FILL";
  return null;
}

interface MatrixRoleBlocks {
  single: QuestionBlock | null;
  set: QuestionBlock | null;
  fill: QuestionBlock | null;
}

export function roleBlocks(section: Pick<ExamSection, "blocks">): MatrixRoleBlocks {
  const active = section.blocks.filter((block) => !block.omitted);
  const find = (role: MatrixRole) => active.find((block) => roleOf(block) === role) ?? null;
  return { single: find("SINGLE"), set: find("SET"), fill: find("FILL") };
}

/** Các số tổng hợp của Cấu trúc thành phần đọc từ bản nháp. */
export interface MatrixStructure {
  blocks: MatrixRoleBlocks;
  flexible: boolean;
  /** Số chùm (cỡ cố định của dòng chùm đầu tiên); `null` khi không có chùm hoặc chùm cỡ linh hoạt. */
  sets: number | null;
  /** Tổng câu con của mọi dòng chùm. */
  children: number;
  /** Cỡ chùm cố định của dòng chùm đầu tiên; `null` khi không có chùm hoặc cỡ linh hoạt. */
  setSize: number | null;
  /** Tổng câu của các dòng trắc nghiệm đơn. */
  single: number;
  /** Trắc nghiệm T: câu đơn cộng câu con của chùm. */
  choice: number;
  /** Tổng câu của các dòng điền đáp án. */
  fill: number;
  /** Tổng câu đã phân bổ của tất cả các dòng thành phần đang bật. */
  total: number;
}

export function structureOf(section: Pick<ExamSection, "blocks">): MatrixStructure {
  const active = section.blocks.filter((block) => !block.omitted);
  const blocks = roleBlocks(section);
  const flexible = blocks.set !== null && isFlexibleSet(blocks.set);
  const single = sum(active.filter((block) => roleOf(block) === "SINGLE").map((block) => block.questionCount ?? 0));
  const children = sum(active.filter((block) => roleOf(block) === "SET").map((block) => block.questionCount ?? 0));
  const fill = sum(active.filter((block) => roleOf(block) === "FILL").map((block) => block.questionCount ?? 0));
  const total = sum(active.map((block) => block.questionCount ?? 0));
  return {
    blocks,
    flexible,
    sets: blocks.set && !flexible ? (blocks.set.setCount ?? 0) : null,
    children,
    setSize: blocks.set && !flexible ? (blocks.set.setSize ?? 0) : null,
    single,
    choice: single + children,
    fill,
    total,
  };
}

export interface StructureInput {
  choice?: number;
  sets?: number;
  children?: number;
  fill?: number;
}

interface StructureWrite {
  section: ExamSection;
  choiceBelowChildren: boolean;
}

function setBlock(block: QuestionBlock, patch: Partial<QuestionBlock>): QuestionBlock {
  const changed = (Object.keys(patch) as Array<keyof QuestionBlock>).some((key) => block[key] !== patch[key]);
  return changed ? { ...block, ...patch } : block;
}

const STANDARD_QUOTA_TYPES = new Set<QuestionType | null>([null, "SINGLE_CHOICE", "QUESTION_SET", "SHORT_ANSWER"]);

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

/** Đồng bộ `answerQuotas` sau mọi thay đổi dòng thành phần; nếu có loại câu ngoài 3 loại chuẩn thì bỏ `answerQuotas` cấp mục để mỗi dòng tự quyết định loại câu. */
export function syncSectionAnswerQuotas(section: ExamSection, nextBlocks: readonly QuestionBlock[]): AnswerQuotas | null {
  if (section.answerQuotas === null) return null;
  const active = nextBlocks.filter((block) => !block.omitted);
  if (active.some((block) => !STANDARD_QUOTA_TYPES.has(block.questionType))) return null;
  return recomputeAnswerQuotas(section.answerQuotas, structureOf({ blocks: [...nextBlocks] }), section.questionCount);
}

function commitBlocks(section: ExamSection, nextBlocks: QuestionBlock[]): ExamSection {
  if (nextBlocks.length === section.blocks.length && nextBlocks.every((item, idx) => item === section.blocks[idx])) {
    return section;
  }
  const activeIds = new Set(nextBlocks.map((item) => item.id));
  const removedNames = new Map(
    section.blocks.filter((item) => !activeIds.has(item.id)).map((item) => [item.id, item.name.trim()]),
  );
  const nextRules = section.mapidRules?.map((rule) =>
    rule.groupId && removedNames.has(rule.groupId) && rule.deletedGroupName === undefined
      ? { ...rule, deletedGroupName: removedNames.get(rule.groupId)! }
      : rule,
  );
  return {
    ...section,
    blocks: nextBlocks,
    answerQuotas: syncSectionAnswerQuotas(section, nextBlocks),
    ...(nextRules ? { mapidRules: nextRules } : {}),
  };
}

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
  return { section: commitBlocks(section, blocks), choiceBelowChildren };
}

function inferUniformPoints(blocks: readonly QuestionBlock[]): number | null {
  const active = blocks.filter((item) => !item.omitted);
  if (active.length === 0) return null;
  const first = active[0]!.requiredPointsPerQuestion;
  if (first === null) return null;
  return active.every((item) => item.requiredPointsPerQuestion === first) ? first : null;
}

/** Thêm một dòng thành phần mới vào cuối bảng Bước 1. */
export function addMatrixBlock(section: ExamSection, questionType: QuestionType, name: string): ExamSection {
  const isSet = questionType === "QUESTION_SET";
  const block: QuestionBlock = {
    id: draftId("block"),
    name,
    questionType,
    questionCount: isSet ? 3 : 0,
    requiredPointsPerQuestion: inferUniformPoints(section.blocks),
    setCount: isSet ? 1 : null,
    setSize: isSet ? 3 : null,
    knowledgeScope: null,
    tagCodes: [],
    matrix: null,
    childAnswerType: isSet ? "SINGLE_CHOICE" : null,
    templateGroupCode: null,
    answerTypeLocked: false,
    shuffleUnits: true,
    shuffleEditable: false,
  };
  return commitBlocks(section, [...section.blocks, block]);
}

/** Xóa một dòng thành phần (giữ ít nhất 1 dòng). Quy tắc MAPID trỏ vào dòng bị xóa được gắn `deletedGroupName` để cảnh báo trước khi lưu. */
export function removeMatrixBlock(section: ExamSection, blockId: string): ExamSection {
  const activeCount = section.blocks.filter((item) => !item.omitted).length;
  if (activeCount <= 1) return section;
  return commitBlocks(
    section,
    section.blocks.filter((item) => item.id !== blockId),
  );
}

/** Đổi thứ tự xuất hiện của dòng trên đề (vô hiệu khi đang bật trộn lẫn toàn môn). */
export function moveMatrixBlock(section: ExamSection, blockId: string, offset: -1 | 1): ExamSection {
  if (section.mixBlocks === true) return section;
  const index = section.blocks.findIndex((item) => item.id === blockId);
  const target = index + offset;
  if (index < 0 || target < 0 || target >= section.blocks.length) return section;
  const next = [...section.blocks];
  const [moved] = next.splice(index, 1);
  next.splice(target, 0, moved!);
  return commitBlocks(section, next);
}

export function updateMatrixBlockName(section: ExamSection, blockId: string, name: string): ExamSection {
  return commitBlocks(
    section,
    section.blocks.map((item) => (item.id === blockId ? setBlock(item, { name }) : item)),
  );
}

export function updateMatrixBlockCount(section: ExamSection, blockId: string, questionCount: number): ExamSection {
  return commitBlocks(
    section,
    section.blocks.map((item) => (item.id === blockId ? setBlock(item, { questionCount }) : item)),
  );
}

/** Đổi loại câu của một dòng, dọn các thuộc tính không tương thích giữa câu chùm và câu đơn lẻ. */
export function changeMatrixBlockType(
  section: ExamSection,
  blockId: string,
  nextType: QuestionType,
  options: { nextName?: string } = {},
): ExamSection {
  const nextBlocks = section.blocks.map((item) => {
    if (item.id !== blockId) return item;
    const currentEffective = item.questionType ?? "SINGLE_CHOICE";
    if (currentEffective === nextType && options.nextName === undefined) return item;
    const base: QuestionBlock = {
      ...item,
      ...(options.nextName !== undefined ? { name: options.nextName } : {}),
      questionType: nextType,
      itemShape: null,
    };
    delete base.expectedScoring;
    delete base.answerFormats;
    if (nextType === "QUESTION_SET") {
      if (item.questionType === "QUESTION_SET") return base;
      return {
        ...base,
        setCount: 1,
        setSize: 3,
        questionCount: 3,
        childAnswerType: "SINGLE_CHOICE" as const,
      };
    }
    delete base.childConditions;
    delete base.slots;
    return {
      ...base,
      setCount: null,
      setSize: null,
      childAnswerType: null,
    };
  });
  return commitBlocks(section, nextBlocks);
}

/** Chuyển dòng `QUESTION_SET` giữa chế độ Cố định cỡ (`setCount × setSize`) và Linh hoạt cỡ (`setCount = null, setSize = null`). */
export function toggleMatrixSetMode(section: ExamSection, blockId: string, mode: "FIXED" | "FLEXIBLE"): ExamSection {
  const nextBlocks = section.blocks.map((item) => {
    if (item.id !== blockId || item.questionType !== "QUESTION_SET") return item;
    if (mode === "FLEXIBLE") {
      if (isFlexibleSet(item)) return item;
      return setBlock(item, { setCount: null, setSize: null });
    }
    if (!isFlexibleSet(item)) return item;
    const setSize = 3;
    const currentCount = item.questionCount ?? 3;
    const setCount = Math.max(1, Math.round(currentCount / setSize) || 1);
    return setBlock(item, { setCount, setSize, questionCount: setCount * setSize });
  });
  return commitBlocks(section, nextBlocks);
}

/** Cập nhật số chùm hoặc số câu/chùm của dòng `QUESTION_SET` cỡ cố định, tự tính `questionCount = setCount × setSize`. */
export function updateMatrixSetDimensions(
  section: ExamSection,
  blockId: string,
  patch: { setCount?: number; setSize?: number },
): ExamSection {
  const nextBlocks = section.blocks.map((item) => {
    if (item.id !== blockId || item.questionType !== "QUESTION_SET") return item;
    const setCount = patch.setCount ?? item.setCount ?? 1;
    const setSize = patch.setSize ?? item.setSize ?? 3;
    const questionCount = Math.max(0, setCount) * Math.max(0, setSize);
    return setBlock(item, { setCount, setSize, questionCount });
  });
  return commitBlocks(section, nextBlocks);
}

/** Bật/tắt cờ trộn lẫn giữa các loại câu trên toàn môn; khi tắt xóa hẳn khóa `mixBlocks` để giữ nguyên trạng thái sạch ban đầu. */
export function toggleSectionMixBlocks(section: ExamSection, enabled: boolean): ExamSection {
  if (enabled) {
    return section.mixBlocks === true ? section : { ...section, mixBlocks: true };
  }
  if (section.mixBlocks === undefined) return section;
  const rest: ExamSection = { ...section };
  delete rest.mixBlocks;
  return rest;
}

export interface MatrixDefaultSnapshot {
  blocks: QuestionBlock[];
  answerQuotas: AnswerQuotas | null;
  mixBlocks?: boolean;
}

function templateSectionsOf(template: ExamTemplate): ExamTemplateSection[] {
  const result: ExamTemplateSection[] = [];
  for (const part of template.parts) {
    if (part.sections) result.push(...part.sections);
    for (const option of part.options ?? []) {
      if (option.subjectChoice?.sections) result.push(...option.subjectChoice.sections);
    }
  }
  return result;
}

function fromTemplateBlock(tpl: ExamTemplateBlock, reusedId: string): QuestionBlock {
  return {
    id: reusedId,
    name: tpl.name,
    questionType: tpl.questionType,
    questionCount: tpl.questionCount ?? 0,
    requiredPointsPerQuestion: tpl.requiredPointsPerQuestion,
    setCount: tpl.setCount ?? null,
    setSize: tpl.setSize ?? null,
    knowledgeScope: tpl.knowledgeScope ?? null,
    tagCodes: [],
    matrix: tpl.matrix ?? null,
    childAnswerType: tpl.childAnswerType ?? null,
    templateGroupCode: tpl.templateGroupCode ?? null,
    answerTypeLocked: tpl.answerTypeLocked ?? false,
    ...(tpl.expectedScoring ? { expectedScoring: tpl.expectedScoring } : {}),
    shuffleUnits: true,
    shuffleEditable: false,
    ...(tpl.childConditions?.length ? { childConditions: tpl.childConditions } : {}),
    ...(tpl.itemShape ? { itemShape: tpl.itemShape } : {}),
  };
}

function findBaselineSection(parts: readonly ExamPart[] | undefined, sectionId: string): ExamSection | null {
  for (const part of parts ?? []) {
    for (const item of part.sections) if (item.id === sectionId) return item;
    for (const option of part.options) {
      for (const item of option.sections) if (item.id === sectionId) return item;
    }
    for (const item of part.subjectPool ?? []) if (item.id === sectionId) return item;
  }
  return null;
}

/** Tìm cấu hình dòng mặc định của môn từ mẫu bài thi, danh sách mẫu chuẩn cùng kỳ, hoặc bản lưu gốc. */
export function resolveMatrixDefaultSnapshot(
  section: ExamSection,
  kindCode: string,
  template?: ExamTemplate | null,
  templates?: readonly ExamTemplate[],
  baselineParts?: readonly ExamPart[],
): MatrixDefaultSnapshot | null {
  const candidates: ExamTemplate[] = [];
  if (template) candidates.push(template);
  if (templates?.length) {
    const standard = templates.filter((item) => item.kind === kindCode && item.buildMode !== "REVIEW" && item.code !== template?.code);
    candidates.push(
      ...standard.filter((item) => item.scope === "SUBJECT"),
      ...standard.filter((item) => item.scope !== "SUBJECT"),
    );
  }
  for (const candidate of candidates) {
    const tplSections = templateSectionsOf(candidate);
    const matched =
      tplSections.find((item) => item.sectionId && item.sectionId === section.id && (item.blocks?.length ?? 0) > 0) ??
      tplSections.find((item) => item.subjectCode === section.subjectCode && (item.blocks?.length ?? 0) > 0);
    if (matched?.blocks?.length) {
      const usedIds = new Set<string>();
      const blocks = matched.blocks.map((tpl) => {
        const reusable = section.blocks.find(
          (existing) =>
            !usedIds.has(existing.id) &&
            ((tpl.templateGroupCode && existing.templateGroupCode === tpl.templateGroupCode && (existing.questionType ?? "SINGLE_CHOICE") === (tpl.questionType ?? "SINGLE_CHOICE")) ||
              (existing.questionType ?? "SINGLE_CHOICE") === (tpl.questionType ?? "SINGLE_CHOICE")),
        );
        const id = reusable ? reusable.id : draftId("block");
        usedIds.add(id);
        return fromTemplateBlock(tpl, id);
      });
      return {
        blocks,
        answerQuotas: matched.answerQuotas ?? section.answerQuotas,
      };
    }
  }
  const baseline = findBaselineSection(baselineParts, section.id);
  if (baseline && baseline.blocks.length > 0) {
    return {
      blocks: baseline.blocks.map((item) => ({ ...item, tagCodes: [...item.tagCodes] })),
      answerQuotas: baseline.answerQuotas,
      ...(baseline.mixBlocks === true ? { mixBlocks: true } : {}),
    };
  }
  return null;
}

/** Khôi phục danh sách dòng thành phần và cờ đảo câu về mặc định của môn. */
export function resetMatrixSectionBlocks(section: ExamSection, snapshot: MatrixDefaultSnapshot): ExamSection {
  const seeded: ExamSection = {
    ...toggleSectionMixBlocks(section, snapshot.mixBlocks === true),
    answerQuotas: snapshot.answerQuotas,
  };
  return commitBlocks(seeded, snapshot.blocks);
}

export type MatrixDimensionKey = "grade" | "difficulty";

const DIMENSION_FIELD = { grade: "byGrade", difficulty: "byDifficulty" } as const;
const ENFORCEMENT_FIELD = { grade: "gradeEnforcement", difficulty: "difficultyEnforcement" } as const;

export function initialEnforcement(mode: "FIXED_REQUIRED" | "DECLARED_REQUIRED" | "FIXED_REFERENCE" | "REFERENCE_WITH_APPLY"): QuotaEnforcement | undefined {
  return mode === "FIXED_REFERENCE" || mode === "REFERENCE_WITH_APPLY" ? "REFERENCE" : undefined;
}

interface MatrixCellEdit {
  dimension: MatrixDimensionKey;
  code: string;
  unit: MatrixUnit;
  value: number | null;
}

interface MatrixEditContext {
  total: number;
  axes: MatrixAxes;
  enforcement?: QuotaEnforcement;
}

export function convertDeclaredDimensions(matrix: BlockMatrix, unit: MatrixUnit, total: number): BlockMatrix {
  const convert = (items: readonly MatrixDimensionValue[]): MatrixDimensionValue[] => {
    const next = convertValuesByUnit(items.map((item) => item.value), unit, total);
    return items.map((item, index) => ({ code: item.code, value: next[index] ?? 0 }));
  };
  return { ...matrix, unit, byGrade: convert(matrix.byGrade), byDifficulty: convert(matrix.byDifficulty) };
}

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

export function matrixCell(matrix: BlockMatrix | null, dimension: MatrixDimensionKey, code: string): number | null {
  return matrix?.[DIMENSION_FIELD[dimension]].find((item) => item.code === code)?.value ?? null;
}

export function dimensionDeclared(matrix: BlockMatrix | null, dimension: MatrixDimensionKey): boolean {
  return (matrix?.[DIMENSION_FIELD[dimension]].length ?? 0) > 0;
}

export function dimensionEnforcement(matrix: BlockMatrix | null, dimension: MatrixDimensionKey): QuotaEnforcement {
  return matrix?.[ENFORCEMENT_FIELD[dimension]] ?? "REQUIRED";
}

export function setDimensionEnforcement(matrix: BlockMatrix | null, dimension: MatrixDimensionKey, enforcement: QuotaEnforcement): BlockMatrix | null {
  if (matrix === null || dimensionEnforcement(matrix, dimension) === enforcement) return matrix;
  return { ...matrix, [ENFORCEMENT_FIELD[dimension]]: enforcement };
}

export function dimensionTotal(matrix: BlockMatrix | null, dimension: MatrixDimensionKey): number {
  return sum((matrix?.[DIMENSION_FIELD[dimension]] ?? []).map((item) => item.value));
}
