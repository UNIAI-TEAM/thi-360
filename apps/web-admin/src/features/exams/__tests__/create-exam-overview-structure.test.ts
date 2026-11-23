import { describe, expect, it } from "vitest";
import { updateRowCell } from "../create-exam-overview";
import { applyOverviewToParts, overviewRowsFromParts } from "../create-exam-overview-structure";
import type { ExamPart, ExamPartOption, ExamSection, QuestionBlock } from "../types";

/** applyOverviewToParts: chỉ đổi số câu, thời lượng, điểm của phần và số câu của môn; không đụng nhóm lấy câu (UNI-2751). */

const block = (id: string, count: number): QuestionBlock => ({
  id,
  name: id,
  questionType: "SINGLE_CHOICE",
  questionCount: count,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: 1,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
});

const section = (id: string, subjectCode: string, count: number): ExamSection => ({
  id,
  subjectCode,
  questionCount: count,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [block(`${id}-blk`, count)],
});

const part = (id: string, count: number, sections: ExamSection[], extra: Partial<ExamPart> = {}): ExamPart => ({
  id,
  order: 1,
  name: id,
  durationMinutes: 60,
  questionCount: count,
  questionCountOnPaper: count,
  questionCountRequired: count,
  participation: { mode: "ALL", choose: null },
  mode: "FIXED",
  sections,
  options: [],
  ...extra,
});

const branch: ExamPartOption = { id: "opt", kind: "BRANCH", code: "ICT", name: null, sections: [], chooseSubjects: null, countsByPick: null };

/** Dựng dòng từ cấu trúc, gõ các ô `edits` (khóa dòng → ô → chữ), rồi áp vào cấu trúc. */
function apply(
  parts: ExamPart[],
  edits: Record<string, Partial<Record<"questionCount" | "durationMinutes" | "maxPoints", string>>>,
  options = { fixedCounts: false, countsLocked: false },
): ExamPart[] {
  let rows = overviewRowsFromParts(parts, options);
  for (const [key, fields] of Object.entries(edits)) {
    for (const [field, text] of Object.entries(fields)) {
      rows = updateRowCell(rows, key, field as "questionCount" | "durationMinutes" | "maxPoints", text);
    }
  }
  return applyOverviewToParts(parts, rows);
}

describe("applyOverviewToParts", () => {
  it("phần cố định hai môn: sửa một môn chỉ đổi môn đó; số câu phần, môn kia và nhóm lấy câu giữ nguyên", () => {
    const parts = [part("p1", 50, [section("s1", "MATH", 20), section("s2", "PHYSICS", 30)])];
    const [next] = apply(parts, { "p:0/s:1": { questionCount: "25" } });
    expect(next!.questionCount).toBe(50);
    expect(next!.sections.map((item) => item.questionCount)).toEqual([20, 25]);
    expect(next!.sections[0]).toBe(parts[0]!.sections[0]);
    expect(next!.sections[1]!.blocks).toEqual(parts[0]!.sections[1]!.blocks);
    expect(next!.questionCountOnPaper).toBe(50);
  });

  it("phần cố định một môn: số câu phần kéo theo số câu môn, nhóm lấy câu không đổi", () => {
    const parts = [part("p1", 45, [section("s1", "MATH", 45)])];
    const [next] = apply(parts, { "p:0": { questionCount: "40" } });
    expect(next!.questionCount).toBe(40);
    expect(next!.sections[0]!.questionCount).toBe(40);
    expect(next!.sections[0]!.blocks[0]!.questionCount).toBe(45);
  });

  it("mẫu fixedCounts hiện dòng môn cho phần một môn: sửa riêng dòng môn thì số câu phần đứng yên", () => {
    const parts = [part("p1", 45, [section("s1", "MATH", 45)])];
    const [next] = apply(parts, { "p:0/s:0": { questionCount: "44" } }, { fixedCounts: true, countsLocked: false });
    expect(next!.sections[0]!.questionCount).toBe(44);
    expect(next!.questionCount).toBe(45);
  });

  it("phần tự chọn (có phương án): chỉ đổi số câu của phần, môn chung không đổi", () => {
    const parts = [part("p1", 30, [section("s1", "ICT_COMMON", 28)], { mode: "CHOICE", options: [branch], participation: { mode: "CHOICE", choose: 1 } })];
    const [next] = apply(parts, { "p:0": { questionCount: "32" } });
    expect(next!.questionCount).toBe(32);
    expect(next!.sections).toEqual(parts[0]!.sections);
  });

  it("thời lượng và điểm của phần đổi, số câu và các phần khác giữ nguyên", () => {
    const parts = [part("p1", 45, [section("s1", "MATH", 45)]), part("p2", 50, [section("s2", "LITERATURE", 50)])];
    const [first, second] = apply(parts, { "p:0": { durationMinutes: "90", maxPoints: "7.5" } });
    expect(first).toMatchObject({ questionCount: 45, durationMinutes: 90, maxPoints: 7.5 });
    expect(first!.sections[0]!.questionCount).toBe(45);
    expect(second).toBe(parts[1]);
  });

  it("không ô nào đổi: trả lại đúng từng phần cũ", () => {
    const parts = [part("p1", 45, [section("s1", "MATH", 45)])];
    const next = apply(parts, {});
    expect(next[0]).toBe(parts[0]);
  });

  it("AC-12 (R-08.2): đồng bộ questionCountOnPaper khớp ExamOverview.onPaper khi questionCount tăng ở phần FIXED không mã", () => {
    // Bài ôn (từ 0 lên 30) hoặc bài chuẩn tăng từ 45 lên 60: questionCountOnPaper tăng theo questionCount mới.
    const reviewParts = [part("p-rev", 0, [section("s-rev", "MATH", 0)], { questionCountOnPaper: 0 })];
    const [nextReview] = apply(reviewParts, { "p:0": { questionCount: "30" } });
    expect(nextReview!.questionCount).toBe(30);
    expect(nextReview!.questionCountOnPaper).toBe(30);

    const grownParts = [part("p-std", 45, [section("s-std", "MATH", 45)], { questionCountOnPaper: 45 })];
    const [nextGrown] = apply(grownParts, { "p:0": { questionCount: "60" } });
    expect(nextGrown!.questionCount).toBe(60);
    expect(nextGrown!.questionCountOnPaper).toBe(60);

    // Giảm số câu (45 -> 40 khi onPaper = 50): giữ nguyên 50.
    const shrunkParts = [part("p-shrunk", 45, [section("s-shrunk", "MATH", 45)], { questionCountOnPaper: 50 })];
    const [nextShrunk] = apply(shrunkParts, { "p:0": { questionCount: "40" } });
    expect(nextShrunk!.questionCountOnPaper).toBe(50);

    // Phần có codeSource (QDA/BCA) hoặc CHOICE: không tự kéo questionCountOnPaper.
    const codedParts = [
      part("p-coded", 50, [section("s-coded", "PHYSICS", 17)], {
        codeSource: { source: "GLOBAL_LIST", branch: null },
        questionCountOnPaper: 17,
      }),
    ];
    const [nextCoded] = apply(codedParts, { "p:0": { questionCount: "60" } });
    expect(nextCoded!.questionCountOnPaper).toBe(17);

    const choiceParts = [
      part("p-choice", 30, [section("s-choice", "ICT_COMMON", 28)], {
        mode: "CHOICE",
        options: [branch],
        participation: { mode: "CHOICE", choose: 1 },
        questionCountOnPaper: 28,
      }),
    ];
    const [nextChoice] = apply(choiceParts, { "p:0": { questionCount: "35" } });
    expect(nextChoice!.questionCountOnPaper).toBe(28);
  });
});

