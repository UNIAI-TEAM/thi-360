import { expect, test } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  blockingCodes,
  continueToReviewInUi,
  countBySubject,
  createExamInUi,
  examCode,
  expectStructureQuestions,
  expectValidPaths,
  expectWellFormedPaper,
  fillExamStep1InUi,
  generateInUi,
  getExam,
  getStructure,
  getStructureSummary,
  partCardNames,
  publishInUi,
  renameFirstBlockAndSaveInUi,
  submitExamStep1InUi,
  SCENARIO_TIMEOUT_MS,
  type PreviewResultLite,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

const SEGMENTS = [
  { code: "P1", heading: /^Phần I · câu 1–9 · 36 lệnh · 54 điểm$/, from: 1, to: 9, commands: 36, points: 54 },
  { code: "P2", heading: /^Phần II · câu 10–15 · 6 lệnh · 36 điểm$/, from: 10, to: 15, commands: 6, points: 36 },
  { code: "P3", heading: /^Phần III · câu 16–20 · 20 lệnh · 30 điểm$/, from: 16, to: 20, commands: 20, points: 30 },
  { code: "P4", heading: /^Phần IV · câu 21–25 · 5 lệnh · 30 điểm$/, from: 21, to: 25, commands: 5, points: 30 },
] as const;

/** Đề thử mang đủ bốn phân đoạn Phần I-IV đúng dải câu, lệnh và điểm của mẫu (H40). */
function expectSegments(result: PreviewResultLite): void {
  const part = result.paper.parts[0]!;
  expect(result.paper.parts).toHaveLength(1);
  expect((part.displaySegments ?? []).map((segment) => [segment.code, segment.fromPosition, segment.toPosition, segment.commandCount, segment.maxPoints])).toEqual(
    SEGMENTS.map((segment) => [segment.code, segment.from, segment.to, segment.commands, segment.points]),
  );
}

/**
 * Nghiệm thu bổ sung H-SCA/V-SAT (QA addendum mục 5, R-146): V-SAT Tiếng Anh cả môn là một phần 25 câu với bốn phân đoạn hiển
 * thị Phần I-IV. Câu 8-15 là một bài đọc dùng chung: máy chủ chọn đúng một bộ 8 câu con (ranh giới Phần I/II cắt giữa bộ nên
 * ngữ liệu hiện ở cả hai phân đoạn, mỗi nơi giữ câu con của mình). Lưu bước 2 rồi sinh thử lại vẫn còn Phần I-IV.
 */
test("V-SAT Tiếng Anh cả môn: bài đọc câu 8-15 chọn một lần, Phần I-IV, lưu bước 2 không mất phân đoạn", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  const id = await createExamInUi(page, {
    kind: "VSAT",
    scope: "SUBJECT",
    scopeLabel: /^Cả môn/,
    subject: "ANH",
    code: examCode("W2B-VSAT-ANH"),
    name: "V-SAT Tiếng Anh cả môn nghiệm thu 2B",
  });
  const admin = await adminApi();

  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("VSAT_SUBJECT_ANH");
  expect(exam.questionCount).toBe(25);
  expect(exam.maxScore).toBe(150);
  expect(exam.durationMinutes).toBe(60);

  // Một phần duy nhất, bốn phân đoạn phủ kín câu 1-25; bốn nhóm 7 + 8 + 5 + 5.
  const structure = await getStructure(admin, id);
  expect(structure.parts).toHaveLength(1);
  expect(structure.parts[0]!.questionCount).toBe(25);
  expect(structure.parts[0]!.displaySegments?.map((segment) => segment.code)).toEqual(["P1", "P2", "P3", "P4"]);
  const blocks = structure.parts[0]!.sections![0]!.blocks!;
  expect(blocks.map((block) => [block.questionType, block.questionCount])).toEqual([
    ["TRUE_FALSE", 7],
    ["QUESTION_SET", 8],
    ["MATCHING", 5],
    ["CLOZE", 5],
  ]);
  const summary = await getStructureSummary(admin, id);
  expect(summary.parts.reduce((sum, row) => sum + (row.commandCount ?? 0), 0), "tổng số lệnh").toBe(67);
  await expectStructureQuestions(page, /^25(\D|$)/);

  // Lưu bước 2 qua giao diện: phân đoạn còn nguyên trong cấu trúc đã lưu (R-146).
  await renameFirstBlockAndSaveInUi(page, admin, id);
  const saved = await getStructure(admin, id);
  expect(saved.parts[0]!.displaySegments?.map((segment) => segment.code), "phân đoạn sau khi lưu bước 2").toEqual(["P1", "P2", "P3", "P4"]);

  const check = await continueToReviewInUi(page, id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  await expectValidPaths(page, 1, 1);

  // Sinh thử lần 1: Phần I-IV hiện trên đề thử; bài đọc câu 8-15 là một bộ 8 câu con được chọn đúng một lần.
  const first = await generateInUi(page, id);
  expectWellFormedPaper(first.result);
  expect(first.result.paper.totalQuestions).toBe(25);
  expect(first.result.paper.totalPoints).toBe(150);
  expect(countBySubject(first.result)).toEqual({ ANH: 25 });
  expectSegments(first.result);
  for (const segment of SEGMENTS) await expect(first.panel.getByRole("heading", { name: segment.heading })).toBeVisible();
  const reading = first.result.paper.parts[0]!.blocks!.find((block) => block.blockName?.startsWith("Bài đọc dùng chung"));
  expect(reading, "đề thử không có nhóm bài đọc dùng chung").toBeDefined();
  expect(reading!.items).toHaveLength(1);
  expect(reading!.items![0]!.kind).toBe("SET");
  expect(reading!.items![0]!.children).toHaveLength(8);
  await expect(first.panel.getByText(/^25 câu · 150 điểm · 60 phút/)).toBeVisible();

  // Lưu bước 2 thêm lần nữa rồi sinh thử lại: vẫn còn Phần I-IV (R-146).
  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await renameFirstBlockAndSaveInUi(page, admin, id);
  const again = await continueToReviewInUi(page, id);
  expect(again.status, `lỗi chặn lần hai: ${blockingCodes(again).join(", ")}`).toBe("VALID");
  const second = await generateInUi(page, id);
  expectWellFormedPaper(second.result);
  expectSegments(second.result);
  for (const segment of SEGMENTS) await expect(second.panel.getByRole("heading", { name: segment.heading })).toBeVisible();

  const published = await publishInUi(page, id);
  expect(published.version).toBe(1);
  expect((await getExam(admin, id)).servingVersion).toBe(1);
});

/**
 * Nghiệm thu bổ sung H-SCA/V-SAT (QA addendum mục 6, H41): V-SAT Tiếng Anh theo phần. Chỉ có Phần II, III, IV (không có
 * Phần I); Phần IV là một đoạn Đọc điền 5 vị trí: 5 câu, 5 lệnh, 30 điểm. Bài theo phần không mang phân đoạn.
 */
test("V-SAT Tiếng Anh theo phần: chỉ Phần II, III, IV; Phần IV 5 câu 30 điểm", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  const spec = {
    kind: "VSAT",
    scope: "SUBJECT",
    scopeLabel: /^Theo phần/,
    subject: "ANH",
    part: /^Phần IV/,
    // Bài theo phần luôn nhập thời lượng khi tạo (R-141).
    overview: { durationMinutes: 15 },
    code: examCode("W2B-VSAT-ANHP"),
    name: "V-SAT Tiếng Anh Phần IV nghiệm thu 2B",
  } as const;
  await fillExamStep1InUi(page, spec);

  const cards = await partCardNames(page);
  expect(cards, `thẻ phần: ${cards.join(" | ")}`).toHaveLength(3);
  expect(cards[0]).toMatch(/^Phần II ·/);
  expect(cards[1]).toMatch(/^Phần III ·/);
  expect(cards[2]).toMatch(/^Phần IV ·/);
  expect(cards.some((name) => /^Phần I ·/.test(name))).toBe(false);

  const id = await submitExamStep1InUi(page, spec);
  const admin = await adminApi();

  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("VSAT_PART_ANH_P4");
  expect(exam.scope).toBe("PART");
  expect(exam.partCode).toBe("P4");
  expect(exam.questionCount).toBe(5);
  expect(exam.maxScore).toBe(30);
  expect(exam.durationMinutes).toBe(15);

  const structure = await getStructure(admin, id);
  expect(structure.parts).toHaveLength(1);
  expect(structure.parts[0]!.questionCount).toBe(5);
  expect(structure.parts[0]!.maxPoints).toBe(30);
  expect(structure.parts[0]!.displaySegments ?? []).toHaveLength(0);
  expect(structure.parts[0]!.sections![0]!.blocks!.map((block) => [block.questionType, block.questionCount])).toEqual([["CLOZE", 5]]);
  const summary = await getStructureSummary(admin, id);
  expect(summary.parts[0]!.allocated).toBe(5);
  expect(summary.parts[0]!.commandCount).toBe(5);
  expect(summary.parts[0]!.maxPoints).toBe(30);
  await expectStructureQuestions(page, /^5(\D|$)/);

  await renameFirstBlockAndSaveInUi(page, admin, id);
  const check = await continueToReviewInUi(page, id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");

  const { result, panel } = await generateInUi(page, id);
  expectWellFormedPaper(result);
  expect(result.paper.totalQuestions).toBe(5);
  expect(result.paper.totalPoints).toBe(30);
  expect(countBySubject(result)).toEqual({ ANH: 5 });
  expect(result.paper.parts[0]!.displaySegments ?? []).toHaveLength(0);
  await expect(panel.getByText(/^5 câu · 30 điểm · 15 phút/)).toBeVisible();
  // Bài theo phần không mang phân đoạn: không có tiêu đề "Phần … · câu … · … lệnh · … điểm".
  await expect(panel.getByRole("heading", { name: /· \d+ lệnh · \d+ điểm$/ })).toHaveCount(0);

  const published = await publishInUi(page, id);
  expect(published.version).toBe(1);
});
