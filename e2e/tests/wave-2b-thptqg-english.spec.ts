import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  blockingCodes,
  continueToReviewInUi,
  countBySubject,
  createExamInUi,
  examCode,
  expectStructureQuestions,
  expectValidPaths,
  expectWellFormedPaper,
  generateInUi,
  getExam,
  renameFirstBlockAndSaveInUi,
  SCENARIO_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/** Sáu nhóm của THPTQG Tiếng Anh theo addendum mục 4 (T §6.1): dải câu, số câu, bộ × cỡ chùm. */
const ENGLISH_GROUPS = [
  { first: 1, last: 6, count: 6, sets: 1, size: 6 },
  { first: 7, last: 12, count: 6, sets: 1, size: 6 },
  { first: 13, last: 17, count: 5, sets: null, size: null },
  { first: 18, last: 22, count: 5, sets: 1, size: 5 },
  { first: 23, last: 30, count: 8, sets: 1, size: 8 },
  { first: 31, last: 40, count: 10, sets: 1, size: 10 },
] as const;

/**
 * Nghiệm thu bổ sung V-ACT/THPTQG, kịch bản 7: THPTQG Tiếng Anh. Tạo (mẫu THPTQG_ENGLISH_2025, một phần), đề thử 40 câu
 * 10 điểm, sáu nhóm đúng dải câu 1–6, 7–12, 13–17, 18–22, 23–30, 31–40. Bước 1, bước 2, kiểm tra và sinh thử ở giao diện.
 */
test("THPTQG Tiếng Anh: tạo, sinh thử 40 câu, sáu nhóm đúng dải câu", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const id = await createExamInUi(page, {
    kind: "THPTQG",
    scope: "SUBJECT",
    subject: "ANH",
    code: examCode("W2B-TQG-ANH"),
    name: "THPTQG Tiếng Anh nghiệm thu 2B",
  });
  const admin = await adminApi();
  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("THPTQG_ENGLISH_2025");
  expect(exam.questionCount).toBe(40);
  expect(exam.maxScore).toBe(10);

  await expectStructureQuestions(page, /^40(\D|$)/);
  await renameFirstBlockAndSaveInUi(page, admin, id);

  const check = await continueToReviewInUi(page, id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  await expectValidPaths(page, 1, 1);

  const { panel, request, result } = await generateInUi(page, id);
  expect(request.target).toEqual({ kind: "EXAM" });
  const questions = expectWellFormedPaper(result);
  expect(result.paper.totalQuestions).toBe(40);
  expect(result.paper.totalPoints).toBe(10);
  expect(result.paper.parts).toHaveLength(1);
  expect(countBySubject(result)).toEqual({ ANH: 40 });
  expect(questions.every((question) => question.points === 0.25)).toBe(true);

  const blocks = result.paper.parts[0]!.blocks ?? [];
  expect(blocks).toHaveLength(6);
  blocks.forEach((block, index) => {
    const expected = ENGLISH_GROUPS[index]!;
    const label = `nhóm ${index + 1} (${block.blockName})`;
    expect([block.firstNumber, block.lastNumber, block.questionCount], label).toEqual([expected.first, expected.last, expected.count]);
    // Số thứ tự của từng câu nằm đúng trong dải của nhóm.
    const numbers = (block.items ?? [])
      .flatMap((item) => (item.kind === "SET" ? (item.children ?? []) : item.question ? [item.question] : []))
      .map((question) => question.number ?? 0)
      .sort((a, b) => a - b);
    expect(numbers, label).toEqual(Array.from({ length: expected.count }, (_, offset) => expected.first + offset));
    if (expected.sets !== null) {
      expect([block.setCount, block.setSize, block.items?.length, block.items?.[0]?.kind], label).toEqual([expected.sets, expected.size, 1, "SET"]);
    } else {
      expect(block.items?.every((item) => item.kind === "QUESTION"), label).toBe(true);
    }
  });

  await expect(panel.getByText(/^40 câu · 10 điểm/)).toBeVisible();
  for (const group of ENGLISH_GROUPS) {
    await expect(panel.getByRole("heading", { name: new RegExp(`câu ${group.first}–${group.last}(\\D|$)`) })).toBeVisible();
  }
});
