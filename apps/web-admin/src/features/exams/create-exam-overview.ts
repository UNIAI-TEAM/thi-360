import type { QuestionType } from "@/features/content/types";
import { isAllOmittedPart } from "./omitted-part";
import type { ExamOverviewRow, ExamTemplate, ExamTemplateBlock, ExamTiming, ItemShape, KnowledgeScope } from "./types";

type OverviewRowKind = "part" | "section" | "block" | "omitted";

export interface OverviewRow {
  /** Khóa ổn định cho React và cho `onRowChange`: `p:<partId|index>` hoặc `p:<…>/s:<sectionId|subject>`. */
  key: string;
  /** Dòng môn/tiểu mục: khóa của dòng phần cha (để tính tỷ lệ trong phần); dòng phần để trống. */
  parentKey?: string;
  kind: OverviewRowKind;
  /** Tên hiển thị: tên phần, hoặc mã môn (giao diện đổi thành tên môn qua danh mục). */
  label: string;
  /** Môn của dòng môn (để giao diện tra tên); dòng phần để trống. */
  subjectCode: string;
  /** Năng lực đầu tiên của mục (V-ACT: hai mục cùng môn SCIENTIFIC_THINKING tách tên theo năng lực). */
  competencyCode?: string;
  /** Dòng phần: vị trí của phần trong mẫu hoặc cấu trúc (không suy từ `key`); dòng khác để trống. */
  partIndex?: number;
  /** Khóa so khớp gửi máy chủ. */
  partId?: string;
  partCode?: string;
  sectionId?: string;
  /** Ô nhập dạng chữ; rỗng hợp lệ (mẫu chưa ghi / bài ôn tự đặt). */
  questionCount: string;
  durationMinutes: string;
  maxPoints: string;
  /** Giá trị của mẫu để so sánh; `null`: mẫu không ghi (ô luôn "khác mẫu" khi có số). */
  base: { questionCount: number | null; durationMinutes: number | null; maxPoints: number | null };
  /** Phần tự chọn của mẫu: giao diện ghi chú rằng học viên chọn lựa chọn (số câu của phần vẫn sửa được). */
  choice: boolean;
  /** Dòng phần có dòng môn con: số câu của phần là tổng các môn, không nhập ở dòng phần. */
  group: boolean;
  lockedCount: boolean;
  /** Dòng lấy câu của mẫu H-SCA, V-SAT (`kind = "block"`): chỉ đọc, không vào `overview` và không vào tổng số câu. */
  block?: OverviewBlockInfo;
}

interface OverviewTotals {
  questions: number | null;
  durationMinutes: number | null;
  maxPoints: number | null;
  /** Tổng lệnh hỏi và điểm tối đa của các dòng lấy câu; `null` khi bảng không có dòng lấy câu. */
  commands: number | null;
  blockPoints: number | null;
}


const numberOrNull = (text: string): number | null => {
  const trimmed = text.trim();
  if (!trimmed) return null;
  const value = Number(trimmed);
  return Number.isFinite(value) ? value : null;
};

const formatNumber = (value: number | null | undefined): string => (value == null ? "" : String(value));

function partRow(part: ExamTemplate["parts"][number], index: number): OverviewRow {
  const base = {
    questionCount: part.questionCount ?? null,
    durationMinutes: part.durationMinutes ?? null,
    maxPoints: part.maxPoints ?? part.points ?? null,
  };
  return {
    key: `p:${index}`,
    kind: "part",
    label: part.name,
    subjectCode: "",
    partIndex: index,
    partId: part.partId || undefined,
    questionCount: formatNumber(base.questionCount),
    durationMinutes: formatNumber(base.durationMinutes),
    maxPoints: formatNumber(base.maxPoints),
    base,
    choice: part.mode === "CHOICE",
    group: false,
    lockedCount: false,
  };
}

/** Năng lực đầu tiên của mục: phạm vi của mục, không thì của nhóm đầu (V-ACT hai mục cùng môn SCIENTIFIC_THINKING). */
export function sectionCompetency(
  section:
    | {
        knowledgeScope?: KnowledgeScope | null;
        blocks?: ReadonlyArray<{ knowledgeScope?: KnowledgeScope | null }>;
      }
    | undefined,
): string | undefined {
  return (
    section?.knowledgeScope?.competencyCodes?.[0] ??
    section?.blocks?.find((block) => block.knowledgeScope?.competencyCodes?.length)?.knowledgeScope?.competencyCodes?.[0]
  );
}

function sectionRow(part: ExamTemplate["parts"][number], partIndex: number, sectionIndex: number, locked: boolean): OverviewRow {
  const section = part.sections?.[sectionIndex];
  const partPoints = locked ? (part.maxPoints ?? part.points ?? null) : null;
  const partCount = part.questionCount ?? null;
  const sectionPoints =
    partPoints !== null && partCount && section?.questionCount
      ? Math.round((partPoints * section.questionCount * 100) / partCount) / 100
      : null;
  const base = {
    questionCount: section?.questionCount ?? null,
    durationMinutes: null,
    maxPoints: sectionPoints,
  };
  return {
    key: `p:${partIndex}/s:${sectionIndex}`,
    parentKey: `p:${partIndex}`,
    kind: "section",
    label: section?.subjectCode ?? "",
    subjectCode: section?.subjectCode ?? "",
    competencyCode: sectionCompetency(section),
    sectionId: section?.sectionId || undefined,
    partId: part.partId || undefined,
    questionCount: formatNumber(base.questionCount),
    durationMinutes: "",
    maxPoints: formatNumber(base.maxPoints),
    base,
    choice: false,
    group: false,
    lockedCount: false,
  };
}


/** Nhóm tạm bỏ qua của môn (SPT Toán: Tự luận): hiện mờ, chỉ đọc, không tính vào tổng và không vào `overview`. */
export function omittedRow(
  partIndex: number,
  sectionIndex: number,
  subjectCode: string,
  block: { name: string; questionCount: number | null },
  blockIndex: number,
): OverviewRow {
  return {
    key: `p:${partIndex}/s:${sectionIndex}/o:${blockIndex}`,
    parentKey: `p:${partIndex}`,
    kind: "omitted",
    label: block.name,
    subjectCode,
    questionCount: "",
    durationMinutes: "",
    maxPoints: "",
    base: { questionCount: block.questionCount, durationMinutes: null, maxPoints: null },
    choice: false,
    group: false,
    lockedCount: false,
  };
}

/** Phần của mẫu mà mọi nhóm đều tạm bỏ qua (quy tắc chung ở `isAllOmittedPart`). */
export function isAllOmittedTemplatePart(part: ExamTemplate["parts"][number]): boolean {
  return isAllOmittedPart(
    (part.sections ?? []).map((section) => ({ blockCount: section.blocks?.length ?? 0, omittedCount: section.omittedBlocks?.length ?? 0 })),
  );
}

export function overviewRowsFromTemplate(
  template: ExamTemplate | undefined | null,
  scope: "FULL" | "PART" | "SUBJECT",
  partCode = "",
): OverviewRow[] {
  if (!template) return [];
  const fixedCounts = template.fixedCounts === true;
  const locked = fixedCounts || (template.fixedBlockCounts === true && scope === "SUBJECT");
  const rows: OverviewRow[] = [];
  template.parts.forEach((part, index) => {
    if (isAllOmittedTemplatePart(part)) {
      // Chỉ đọc như nhóm tạm bỏ qua: không kiểm tra, không vào tổng, không vào `overview`.
      const omittedCount = (part.sections ?? []).reduce((total, section) => total + (section.omittedBlocks ?? []).reduce((sum, block) => sum + (block.questionCount ?? 0), 0), 0);
      rows.push(omittedRow(index, 0, part.sections?.[0]?.subjectCode ?? "", { name: part.name, questionCount: omittedCount }, 0));
      return;
    }
    const row = partRow(part, index);
    if (scope === "PART") row.partCode = partCode || undefined;
    row.lockedCount = locked;
    rows.push(row);
    const sections = part.mode === "FIXED" ? (part.sections ?? []) : [];
    if (sections.length > 1 || (fixedCounts && sections.length > 0)) {
      row.group = true;
      sections.forEach((section, sectionIndex) => {
        rows.push({ ...sectionRow(part, index, sectionIndex, fixedCounts), lockedCount: locked });
        (section.omittedBlocks ?? []).forEach((block, blockIndex) =>
          rows.push(omittedRow(index, sectionIndex, section.subjectCode, block, blockIndex)),
        );
      });
    } else {
      // Một môn: nhóm tạm bỏ qua nằm ngay dưới dòng phần (SPT Toán: Tự luận).
      // H-SCA, V-SAT: từng dòng lấy câu của mẫu nằm dưới dòng phần, kèm lệnh hỏi và điểm (TaoBaiThiHscaVsat2B).
      if (template.kind === "HSCA" || template.kind === "VSAT") {
        sections.forEach((section, sectionIndex) =>
          (section.blocks ?? []).forEach((block, blockIndex) => rows.push(blockRow(index, sectionIndex, block, blockIndex))),
        );
      }
      sections.forEach((section, sectionIndex) =>
        (section.omittedBlocks ?? []).forEach((block, blockIndex) =>
          rows.push(omittedRow(index, sectionIndex, section.subjectCode, block, blockIndex)),
        ),
      );
    }
  });
  return rows;
}

export function overviewRowsEmpty(scope: "FULL" | "PART" | "SUBJECT", subjectCode: string, partCode = ""): OverviewRow[] {
  return [
    {
      key: "p:new",
      kind: "part",
      label: "",
      subjectCode: scope === "SUBJECT" ? subjectCode : "",
      partCode: scope === "PART" ? partCode : undefined,
      questionCount: "",
      durationMinutes: "",
      maxPoints: "",
      base: { questionCount: null, durationMinutes: null, maxPoints: null },
      choice: false,
      group: false,
      lockedCount: false,
    },
  ];
}

export function updateRowCell(rows: readonly OverviewRow[], key: string, field: "questionCount" | "durationMinutes" | "maxPoints", text: string): OverviewRow[] {
  return rows.map((row) => (row.key === key ? { ...row, [field]: text } : row));
}

export function rowsDifferFromTemplate(rows: readonly OverviewRow[]): boolean {
  return rows.some(
    (row) =>
      (row.questionCount.trim() !== "" && numberOrNull(row.questionCount) !== row.base.questionCount) ||
      (row.durationMinutes.trim() !== "" && numberOrNull(row.durationMinutes) !== row.base.durationMinutes) ||
      (row.maxPoints.trim() !== "" && numberOrNull(row.maxPoints) !== row.base.maxPoints),
  );
}

/** Dòng `overview` của `POST /exams`: chỉ dòng có ô khác mẫu, chỉ ô khác. */
export function overviewDiff(rows: readonly OverviewRow[]): ExamOverviewRow[] {
  const diff: ExamOverviewRow[] = [];
  for (const row of rows) {
    if (row.kind === "block") continue;
    const out: ExamOverviewRow = {};
    if (row.partId) out.partId = row.partId;
    if (row.partCode) out.partCode = row.partCode;
    if (row.sectionId) out.sectionId = row.sectionId;
    if (row.subjectCode && row.kind === "section") out.subjectCode = row.subjectCode;
    const count = row.group || row.kind === "omitted" ? null : numberOrNull(row.questionCount);
    if (count !== null && count !== row.base.questionCount) out.questionCount = count;
    const duration = numberOrNull(row.durationMinutes);
    if (duration !== null && duration !== row.base.durationMinutes) out.durationMinutes = duration;
    const points = numberOrNull(row.maxPoints);
    if (points !== null && points !== row.base.maxPoints) out.maxPoints = points;
    if (out.questionCount !== undefined || out.durationMinutes !== undefined || out.maxPoints !== undefined) diff.push(out);
  }
  return diff;
}

type OverviewIssueCode = "countInvalid" | "durationInvalid" | "pointsInvalid";

export interface OverviewIssue {
  rowKey: string;
  field: "questionCount" | "durationMinutes" | "maxPoints";
  code: OverviewIssueCode;
}

export function validateOverview(rows: readonly OverviewRow[]): OverviewIssue[] {
  const issues: OverviewIssue[] = [];
  const integer = (text: string) => {
    const value = numberOrNull(text);
    return text.trim() === "" || (value !== null && Number.isInteger(value) && value > 0);
  };
  for (const row of rows) {
    if (!integer(row.questionCount)) issues.push({ rowKey: row.key, field: "questionCount", code: "countInvalid" });
    if (!integer(row.durationMinutes)) issues.push({ rowKey: row.key, field: "durationMinutes", code: "durationInvalid" });
    const points = numberOrNull(row.maxPoints);
    if (row.maxPoints.trim() !== "" && (points === null || points <= 0)) {
      issues.push({ rowKey: row.key, field: "maxPoints", code: "pointsInvalid" });
    }
  }
  return issues;
}

function sumColumn(values: ReadonlyArray<string>): number | null {
  let total = 0;
  let seen = false;
  for (const text of values) {
    const value = numberOrNull(text);
    if (value === null) continue;
    total += value;
    seen = true;
  }
  return seen ? Math.round(total * 100) / 100 : null;
}

export function overviewTotals(rows: readonly OverviewRow[], timing: ExamTiming, sharedDuration: string): OverviewTotals {
  const counted = rows.filter((row) => row.kind !== "omitted");
  const parts = counted.filter((row) => row.kind === "part");
  const blocks = counted.filter((row) => row.block !== undefined);
  const blockPoints = blocks.filter((row) => row.block?.points != null);
  return {
    questions: sumColumn(counted.filter((row) => !row.group).map((row) => row.questionCount)),
    durationMinutes: timing === "SHARED" ? numberOrNull(sharedDuration) : sumColumn(parts.map((row) => row.durationMinutes)),
    maxPoints: sumColumn(parts.map((row) => row.maxPoints)),
    commands: blocks.length > 0 ? blocks.reduce((total, row) => total + (row.block?.commands ?? 0), 0) : null,
    blockPoints: blockPoints.length > 0 ? Math.round(blockPoints.reduce((total, row) => total + (row.block?.points ?? 0), 0) * 100) / 100 : null,
  };
}

/** Số liệu của một dòng lấy câu: số chùm × cỡ chùm (chùm), số câu, lệnh hỏi và điểm tối đa (`null`: mẫu không ghi điểm mỗi câu). */
export interface OverviewBlockInfo {
  sets: number | null;
  setSize: number | null;
  questions: number;
  commands: number;
  points: number | null;
}

/** Lệnh hỏi của một câu: câu Đúng/Sai tính theo số ý, Ghép đôi theo số ý trái, dạng khác một lệnh (VS §2). */
function commandsPerQuestion(type: QuestionType | null | undefined, shape?: ItemShape | null): number {
  if (type === "TRUE_FALSE") return shape?.statements ?? 1;
  if (type === "MATCHING") return shape?.leftItems ?? 1;
  return 1;
}

function blockCommands(block: ExamTemplateBlock, questions: number): number {
  const typed = (block.childConditions ?? []).filter((condition) => condition.answerType !== undefined && condition.to >= condition.from);
  if (typed.length > 0 && block.setCount) {
    const covered = typed.reduce((total, condition) => total + (condition.to - condition.from + 1), 0);
    const perSet = typed.reduce(
      (total, condition) => total + (condition.to - condition.from + 1) * commandsPerQuestion(condition.answerType, condition.itemShape),
      0,
    );
    return block.setCount * (perSet + Math.max(0, (block.setSize ?? covered) - covered));
  }
  const type = block.questionType === "QUESTION_SET" ? block.childAnswerType : block.questionType;
  return questions * commandsPerQuestion(type, block.itemShape);
}

function blockRow(partIndex: number, sectionIndex: number, block: ExamTemplateBlock, blockIndex: number): OverviewRow {
  const sets = block.setCount ?? null;
  const setSize = block.setSize ?? null;
  const questions = block.questionCount ?? (sets !== null && setSize !== null ? sets * setSize : 0);
  const points = block.requiredPointsPerQuestion != null ? Math.round(questions * block.requiredPointsPerQuestion * 100) / 100 : null;
  return {
    key: `p:${partIndex}/b:${sectionIndex}.${blockIndex}`,
    parentKey: `p:${partIndex}`,
    kind: "block",
    label: block.name,
    subjectCode: "",
    questionCount: "",
    durationMinutes: "",
    maxPoints: "",
    base: { questionCount: null, durationMinutes: null, maxPoints: null },
    choice: false,
    group: false,
    lockedCount: true,
    block: { sets, setSize, questions, commands: blockCommands(block, questions), points },
  };
}
