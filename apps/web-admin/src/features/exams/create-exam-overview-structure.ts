import { type OverviewRow, omittedRow, overviewDiff, sectionCompetency } from "./create-exam-overview";
import { isAllOmittedStructurePart } from "./matrix";
import type { ExamPart, ExamSection } from "./types";

export function overviewRowsFromParts(
  parts: readonly ExamPart[],
  options: { fixedCounts: boolean; countsLocked: boolean },
): OverviewRow[] {
  const text = (value: number | null | undefined) => (value == null ? "" : String(value));
  const rows: OverviewRow[] = [];
  parts.forEach((part, index) => {
    if (isAllOmittedStructurePart(part)) {
      const omittedCount = part.sections.reduce(
        (total, section) => total + section.blocks.reduce((sum, block) => sum + (block.omitted ? (block.questionCount ?? 0) : 0), 0),
        0,
      );
      rows.push(omittedRow(index, 0, part.sections[0]?.subjectCode ?? "", { name: part.name, questionCount: omittedCount }, 0));
      return;
    }
    const sections = part.mode === "FIXED" ? part.sections : [];
    const group = sections.length > 1 || (options.fixedCounts && sections.length > 0);
    const base = {
      questionCount: part.questionCount,
      durationMinutes: part.durationMinutes ?? null,
      maxPoints: part.maxPoints !== null && part.maxPoints !== undefined && part.maxPoints > 0 ? part.maxPoints : null,
    };
    rows.push({
      key: `p:${index}`,
      kind: "part",
      label: part.name,
      subjectCode: "",
      partIndex: index,
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
        competencyCode: sectionCompetency(section),
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
    const countChanged = partChange?.questionCount !== undefined;
    // Khớp ExamOverview.onPaper của backend: phần có codeSource (QDA/BCA) hoặc CHOICE lấy số câu trên đề theo đường lựa chọn;
    // phần FIXED không mã tăng số câu vượt questionCountOnPaper cũ thì kéo questionCountOnPaper lên theo.
    const fixed = !part.codeSource && part.participation?.mode !== "CHOICE" && part.mode !== "CHOICE";
    const questionCountOnPaper =
      countChanged && fixed && part.questionCountOnPaper < questionCount ? questionCount : part.questionCountOnPaper;
    return {
      ...part,
      questionCount,
      questionCountOnPaper,
      durationMinutes: partChange?.durationMinutes ?? part.durationMinutes,
      maxPoints: partChange?.maxPoints ?? part.maxPoints,
      sections,
    };
  });
}
