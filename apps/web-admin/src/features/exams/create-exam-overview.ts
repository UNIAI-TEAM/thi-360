import type { ExamOverviewRow, ExamTemplate, ExamTemplateSection, ExamTiming } from "./types";

/**
 * "Bảng tổng quan" của bước 1 (2B 15.1): một dòng cho mỗi phần của mẫu và, khi phần cố định có nhiều môn (BCA Phần 2,
 * phần chung THPTQG Tin học), một dòng thụt vào cho từng môn. Ô Số câu, Thời lượng và Điểm sửa trên dòng; sửa một ô
 * không nạp lại mẫu. Chỉ phần tử khác giá trị mẫu được gửi trong `overview` của `POST /exams` (so khớp theo `partId`
 * hoặc `partCode`/`subjectCode`).
 */

export type OverviewRowKind = "part" | "section" | "omitted";

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
  /** Mẫu `fixedCounts` (H27): ô số câu chỉ đọc — V-ACT toàn bài khóa số câu từng tiểu mục. */
  lockedCount: boolean;
}

export interface OverviewTotals {
  questions: number | null;
  durationMinutes: number | null;
  maxPoints: number | null;
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
function sectionCompetency(section: ExamTemplateSection | undefined): string | undefined {
  return (
    section?.knowledgeScope?.competencyCodes?.[0] ??
    section?.blocks?.find((block) => block.knowledgeScope?.competencyCodes?.length)?.knowledgeScope?.competencyCodes?.[0]
  );
}

/**
 * Dòng của một môn/tiểu mục trong phần (con của `parentKey`). Điểm của mục: mẫu chia điểm theo phần thì chia điểm
 * của phần theo tỷ lệ số câu của mục (V-ACT: 10 điểm một câu, mục 30/60 của phần 600).
 */
function sectionRow(part: ExamTemplate["parts"][number], partIndex: number, sectionIndex: number): OverviewRow {
  const section = part.sections?.[sectionIndex];
  const partPoints = part.maxPoints ?? part.points ?? null;
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
function omittedRow(
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

/**
 * Dòng tổng quan của mẫu đã chọn. `scope = PART` gắn `partCode` của bài vào dòng phần (khóa so khớp của mẫu theo
 * phần). Phần cố định nhiều môn thêm dòng môn ngay sau dòng phần; dòng phần khi đó chỉ là dòng nhóm (tổng môn), ô
 * số câu của phần không sửa — sửa ở dòng môn. Mẫu `fixedCounts` (H27, V-ACT toàn bài) hiện dòng cho mọi tiểu mục
 * và khóa ô số câu của phần lẫn mục.
 */
export function overviewRowsFromTemplate(
  template: ExamTemplate | undefined | null,
  scope: "FULL" | "PART" | "SUBJECT",
  partCode = "",
): OverviewRow[] {
  if (!template) return [];
  const locked = template.fixedCounts === true;
  const rows: OverviewRow[] = [];
  template.parts.forEach((part, index) => {
    const row = partRow(part, index);
    if (scope === "PART") row.partCode = partCode || undefined;
    row.lockedCount = locked;
    rows.push(row);
    const sections = part.mode === "FIXED" ? (part.sections ?? []) : [];
    if (sections.length > 1 || (locked && sections.length > 0)) {
      row.group = true;
      sections.forEach((section, sectionIndex) => {
        rows.push({ ...sectionRow(part, index, sectionIndex), lockedCount: locked });
        (section.omittedBlocks ?? []).forEach((block, blockIndex) =>
          rows.push(omittedRow(index, sectionIndex, section.subjectCode, block, blockIndex)),
        );
      });
    } else {
      // Một môn: nhóm tạm bỏ qua nằm ngay dưới dòng phần (SPT Toán: Tự luận).
      sections.forEach((section, sectionIndex) =>
        (section.omittedBlocks ?? []).forEach((block, blockIndex) =>
          rows.push(omittedRow(index, sectionIndex, section.subjectCode, block, blockIndex)),
        ),
      );
    }
  });
  return rows;
}

/**
 * Bảng trống khi không có mẫu (bài ôn `REVIEW` chưa có mẫu `<KỲ>_REVIEW`, 2B 15.1): một phần trống, mọi ô nhập được.
 * `partCode` của bài Theo phần đi kèm để máy chủ so khớp.
 */
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

/**
 * Sửa nội dung nhập (bảng tổng quan, thời lượng chung, thang điểm, mô tả, hướng dẫn) kể từ lần nạp mẫu gần nhất.
 * Đổi kỳ thi/phạm vi/môn/phần/`buildMode` khi `dirty` phải hỏi xác nhận vì nạp lại bảng (2B 15.1).
 */
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

export type OverviewIssueCode = "countInvalid" | "durationInvalid" | "pointsInvalid";

export interface OverviewIssue {
  rowKey: string;
  field: "questionCount" | "durationMinutes" | "maxPoints";
  code: OverviewIssueCode;
}

/**
 * Kiểm tra ô nhập: số phải là số nguyên dương (điểm cho phép thập phân 0,25); để trống hợp lệ — mẫu/máy chủ quyết.
 * Không ràng buộc tổng điểm các phần với thang điểm của mẫu: dòng sửa được, tổng của bài là tổng các dòng (2B 5.2).
 */
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

/**
 * Tổng ở cuối bảng; `null` cho cột không có số nào. Số câu cộng dòng không có dòng con (dòng môn, dòng phần thường);
 * dòng nhóm bỏ qua nhóm tạm bỏ qua. `sharedDuration`: thời lượng chung khi `timing = SHARED`.
 */
export function overviewTotals(rows: readonly OverviewRow[], timing: ExamTiming, sharedDuration: string): OverviewTotals {
  const counted = rows.filter((row) => row.kind !== "omitted");
  const parts = counted.filter((row) => row.kind === "part");
  return {
    questions: sumColumn(counted.filter((row) => !row.group).map((row) => row.questionCount)),
    durationMinutes: timing === "SHARED" ? numberOrNull(sharedDuration) : sumColumn(parts.map((row) => row.durationMinutes)),
    maxPoints: sumColumn(parts.map((row) => row.maxPoints)),
  };
}
