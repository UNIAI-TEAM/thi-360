import { expect, test } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  allSections,
  blockingCodes,
  collectIssues,
  continueToReviewInUi,
  createExamInUi,
  examCode,
  expectStructureQuestions,
  expectPublishRefused,
  fillExamStep1InUi,
  getExam,
  getStructure,
  getStructureSummary,
  openBlockInUi,
  partCardNames,
  renameFirstBlockAndSaveInUi,
  setMaxScoreInGeneralTab,
  SOLVER_TIMEOUT_MS,
  SCENARIO_TIMEOUT_MS,
  expectWritingSwitchLocked,
  forceBlockOmitted,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu bổ sung H-SCA/V-SAT (QA addendum mục 7, H49): V-SAT Ngữ văn. Mẫu có Phần I-III (20 câu, 62 lệnh, 120 điểm) và
 * Phần IV Viết đã lược (30 điểm, không vào đề). Thang 150 = 120 + 30 nên kiểm tra báo `SCALE_INCLUDES_EXCLUDED_PART` với
 * đúng 30 điểm của phần Viết (không báo `POINTS_TOTAL_MISMATCH`); phần Viết không bật lại được; bài theo phần chỉ có
 * Phần I, II, III.
 */
test("V-SAT Ngữ văn: thang 150 báo đúng 30 điểm của phần Viết; Viết không bật lại được", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  // Mẫu để trống thời lượng (Q-35): nhập khi tạo. Thang mặc định 120 = Phần I-III; thang 150 đặt sau (phần Viết 30 điểm).
  const id = await createExamInUi(page, {
    kind: "VSAT",
    scope: "SUBJECT",
    scopeLabel: /^Cả môn/,
    subject: "VAN",
    overview: { durationMinutes: 60 },
    code: examCode("W2B-VSAT-VAN"),
    name: "V-SAT Ngữ văn thang 150 nghiệm thu 2B",
  });
  const admin = await adminApi();

  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("VSAT_SUBJECT_VAN");
  expect(exam.questionCount).toBe(20);
  expect(exam.maxScore).toBe(120);
  expect(exam.durationMinutes).toBe(60);

  // Cấu trúc: Phần I-III như mẫu, Phần IV Viết 0 câu với một nhóm Tự luận đã lược, 30 điểm.
  const structure = await getStructure(admin, id);
  expect(structure.parts.map((part) => [part.name, part.questionCount])).toEqual([
    ["Phần I · Đúng/Sai", 9],
    ["Phần II · Trắc nghiệm chọn một", 6],
    ["Phần III · Ghép đôi", 5],
    ["Phần IV · Viết", 0],
  ]);
  const writing = allSections(structure)
    .flatMap((section) => section.blocks ?? [])
    .filter((block) => block.questionType === "ESSAY");
  expect(writing).toHaveLength(1);
  expect(writing[0]!.omitted).toBe(true);
  expect(writing[0]!.requiredPointsPerQuestion).toBe(30);
  const summary = await getStructureSummary(admin, id);
  expect(summary.parts.slice(0, 3).reduce((sum, row) => sum + (row.commandCount ?? 0), 0), "số lệnh Phần I-III").toBe(62);
  await expectStructureQuestions(page, /^20(\D|$)/);

  // Lưu bước 2 qua giao diện bằng một giá trị sửa được (tên nhóm Phần I).
  await renameFirstBlockAndSaveInUi(page, admin, id);

  // Đường cơ sở: thang 120 khớp bài (Phần I-III), kiểm tra đạt.
  const baseline = await continueToReviewInUi(page, id);
  expect(baseline.status, `lỗi chặn: ${blockingCodes(baseline).join(", ")}`).toBe("VALID");

  // Đặt thang 150 qua tab "Thông tin chung" (ô "Điểm tối đa" luôn hiện ở chế độ sửa, lưu bằng "Lưu thông tin"): thang là đầu vào của
  // kết quả kiểm tra đang thử, nên đi qua màn hình chứ không ghi thẳng bằng API.
  await setMaxScoreInGeneralTab(page, admin, id, 150);

  // Kiểm tra: thang 150 lệch đúng 30 điểm của phần Viết đã lược, báo một lỗi chặn riêng thay cho POINTS_TOTAL_MISMATCH.
  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await expectStructureQuestions(page, /^20(\D|$)/);
  const scale = await continueToReviewInUi(page, id);
  expect(scale.status).toBe("INVALID");
  const scaleIssues = collectIssues(scale).filter((issue) => issue.code === "SCALE_INCLUDES_EXCLUDED_PART");
  expect(scaleIssues.length, `không có SCALE_INCLUDES_EXCLUDED_PART: ${JSON.stringify(blockingCodes(scale))}`).toBeGreaterThan(0);
  for (const issue of scaleIssues) {
    expect(issue.severity).toBe("ERROR");
    expect(issue.excludedPoints, "điểm của phần Viết").toBe(30);
    expect(issue.partName).toMatch(/Viết/);
  }
  expect(collectIssues(scale).map((issue) => issue.code)).not.toContain("POINTS_TOTAL_MISMATCH");
  await expect(page).toHaveURL(/\/exams\/[^/]+\/structure/);
  await expect(page.getByText(/Chênh 30 điểm, đúng bằng điểm của phần đã lược/).first()).toBeVisible({ timeout: SOLVER_TIMEOUT_MS });

  // Phần Viết không bật lại được: công tắc "Tạm bỏ qua" bật sẵn, tắt và có lý do đi kèm (R3-09).
  await openBlockInUi(page, id, writing[0]!.id!, writing[0]!.name);
  await expectWritingSwitchLocked(page);
  await expect(page.getByText("Tự luận – tạm bỏ qua")).toBeVisible();

  // Lưới an toàn của máy chủ: PUT ép bật lại được nhận (200) nhưng kiểm tra báo OMITTED_BLOCK_LOCKED.
  await forceBlockOmitted(admin, id, writing[0]!.id!, false);
  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await expectStructureQuestions(page, /^\d+(\D|$)/);
  const reenabled = await continueToReviewInUi(page, id);
  expect(reenabled.status).toBe("INVALID");
  const locked = collectIssues(reenabled).filter((issue) => issue.code === "OMITTED_BLOCK_LOCKED");
  expect(locked.length, `không có OMITTED_BLOCK_LOCKED: ${JSON.stringify(blockingCodes(reenabled))}`).toBeGreaterThan(0);
  expect(locked.every((issue) => issue.severity === "ERROR")).toBe(true);
  await expect(page.getByText(/nhóm đã lược của mẫu chưa có trong V1 nên không bật lại được/).first()).toBeVisible({ timeout: SOLVER_TIMEOUT_MS });

  // Công bố bị từ chối (R3-09): hộp thoại "Sẽ công bố 0 / N", máy chủ trả 422 PATH_NOT_VALID.
  await expectPublishRefused(page, admin, id, reenabled);
});

test("V-SAT Ngữ văn theo phần: chỉ Phần I, II, III; không chào phần Viết", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  await fillExamStep1InUi(page, {
    kind: "VSAT",
    scope: "SUBJECT",
    scopeLabel: /^Theo phần/,
    subject: "VAN",
    part: /^Phần I ·/,
    overview: { durationMinutes: 20 },
    code: examCode("W2B-VSAT-VANP"),
    name: "V-SAT Ngữ văn theo phần nghiệm thu 2B",
  });
  const cards = await partCardNames(page);
  expect(cards, `thẻ phần: ${cards.join(" | ")}`).toHaveLength(3);
  expect(cards[0]).toMatch(/^Phần I ·/);
  expect(cards[1]).toMatch(/^Phần II ·/);
  expect(cards[2]).toMatch(/^Phần III ·/);
  expect(cards.some((name) => /Viết/.test(name))).toBe(false);
  await expect(page.getByText("Phần viết chưa có trong V1 nên không chọn được.")).toBeVisible();
});
