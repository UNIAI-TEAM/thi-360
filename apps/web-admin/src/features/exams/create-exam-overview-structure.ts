import { type OverviewRow, overviewDiff } from "./create-exam-overview";
import type { ExamPart, ExamSection } from "./types";

/**
 * "Bảng tổng quan" dựng từ cấu trúc ĐÃ LƯU của bài (tab Thông tin chung), cùng hình dạng dòng với bảng ở bước 1 để dùng
 * chung `ExamOverviewTable`, `validateOverview` và `overviewDiff`. `base` là số đang lưu, nên dòng "khác" là dòng người
 * dùng vừa sửa. Chỉ hiện dòng phần và, với phần cố định nhiều môn (hoặc mọi phần của mẫu `fixedCounts`), dòng môn; dòng
 * lấy câu và nhóm không vào bảng này.
 */
export function overviewRowsFromParts(
  parts: readonly ExamPart[],
  options: { fixedCounts: boolean; countsLocked: boolean },
): OverviewRow[] {
  const text = (value: number | null | undefined) => (value == null ? "" : String(value));
  const rows: OverviewRow[] = [];
  parts.forEach((part, index) => {
    const sections = part.mode === "FIXED" ? part.sections : [];
    const group = sections.length > 1 || (options.fixedCounts && sections.length > 0);
    const base = {
      questionCount: part.questionCount,
      durationMinutes: part.durationMinutes ?? null,
      maxPoints: part.maxPoints ?? null,
    };
    rows.push({
      key: `p:${index}`,
      kind: "part",
      label: part.name,
      subjectCode: "",
      partId: part.id,
      questionCount: text(base.questionCount),
      durationMinutes: text(base.durationMinutes),
      maxPoints: text(base.maxPoints),
      base,
      choice: part.mode === "CHOICE",
      group,
      lockedCount: options.countsLocked,
    });
    if (!group) return;
    sections.forEach((section, sectionIndex) =>
      rows.push({
        key: `p:${index}/s:${sectionIndex}`,
        parentKey: `p:${index}`,
        kind: "section",
        label: section.subjectCode,
        subjectCode: section.subjectCode,
        partId: part.id,
        sectionId: section.id,
        questionCount: text(section.questionCount),
        durationMinutes: "",
        maxPoints: "",
        base: { questionCount: section.questionCount, durationMinutes: null, maxPoints: null },
        choice: false,
        group: false,
        lockedCount: options.countsLocked,
      }),
    );
  });
  return rows;
}

/**
 * Cấu trúc đã lưu sau khi áp các ô người dùng sửa ở bảng tổng quan. Cùng quy tắc với máy chủ khi nhận `overview` ở bước
 * 1 (`ExamOverview.applyChanges`): chỉ đổi số câu, thời lượng, điểm của phần và số câu của môn; phần cố định đúng một môn
 * thì số câu môn đi theo số câu phần (trừ khi môn đó cũng được sửa). Không đụng nhóm lấy câu, ma trận, chỉ tiêu, số câu
 * trên đề: số câu phần lệch với phân bổ thì bước Cấu trúc báo lệch, không tự giảm câu (spec 5.3).
 */
export function applyOverviewToParts(parts: readonly ExamPart[], rows: readonly OverviewRow[]): ExamPart[] {
  const changes = overviewDiff(rows);
  return parts.map((part) => {
    const own = changes.filter((change) => change.partId === part.id);
    if (own.length === 0) return part;
    const partChange = own.find((change) => change.sectionId === undefined);
    const sectionCount = (section: ExamSection) => own.find((change) => change.sectionId === section.id)?.questionCount;
    const questionCount = partChange?.questionCount ?? part.questionCount;
    const single = part.sections[0];
    const propagate =
      partChange?.questionCount !== undefined &&
      part.sections.length === 1 &&
      part.options.length === 0 &&
      !part.subjectPool?.length &&
      single !== undefined &&
      sectionCount(single) === undefined;
    const sections = part.sections.map((section) => {
      const edited = sectionCount(section);
      if (edited !== undefined) return { ...section, questionCount: edited };
      return propagate ? { ...section, questionCount } : section;
    });
    return {
      ...part,
      questionCount,
      durationMinutes: partChange?.durationMinutes ?? part.durationMinutes,
      maxPoints: partChange?.maxPoints ?? part.maxPoints,
      sections,
    };
  });
}
