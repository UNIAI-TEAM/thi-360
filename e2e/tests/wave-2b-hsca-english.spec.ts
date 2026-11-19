import { expect, test } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  blockingCodes,
  collectIssues,
  continueToReviewInUi,
  countBySubject,
  createExamInUi,
  examCode,
  expectValidPaths,
  expectWellFormedPaper,
  generateInUi,
  getExam,
  getStructure,
  hscaAudioKey,
  openBlockInUi,
  openEditorTab,
  expectPublishRefused,
  publishInUi,
  renameFirstBlockAndSaveInUi,
  s3DeleteObject,
  s3RestoreSeedAsset,
  SCENARIO_TIMEOUT_MS,
  SOLVER_TIMEOUT_MS,
  type PreviewItemLite,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/** Tệp nghe của ngân hàng H-SCA (8 đơn + 4 chùm 4 câu + 4 chùm 5 câu; `questions/dev-seed/…` trong kho dev). */
const AUDIO_FILES = [
  ...Array.from({ length: 10 }, (_, index) => `hsca-en-listening-single-${String(index + 1).padStart(2, "0")}.mp3`),
  ...Array.from({ length: 4 }, (_, index) => `hsca-en-listening-set4-${String(index + 1).padStart(2, "0")}.mp3`),
  ...Array.from({ length: 4 }, (_, index) => `hsca-en-listening-set5-${String(index + 1).padStart(2, "0")}.mp3`),
];

/** Mọi mục (câu đơn và bộ) của phần trong đề thử. */
function itemsOf(result: { paper: { parts: Array<{ name?: string; blocks?: Array<{ items?: PreviewItemLite[] }> }> } }, partName: string): PreviewItemLite[] {
  const part = result.paper.parts.find((item) => item.name === partName);
  expect(part, `đề thử không có phần ${partName}`).toBeDefined();
  return (part!.blocks ?? []).flatMap((block) => block.items ?? []);
}

/** Tệp nghe của một mục: câu đơn mang tệp ở `question`, bộ mang tệp ở chính mục. */
const audioAttachments = (item: PreviewItemLite) =>
  [...(item.attachments ?? []), ...(item.question?.attachments ?? [])].filter((attachment) => attachment.mime?.startsWith("audio/"));


/**
 * Nghiệm thu bổ sung H-SCA/V-SAT (QA addendum mục 2): bài H-SCA Tiếng Anh "Nghe và Đọc". Phần Nghe chỉ lấy câu có audio và
 * đề thử có trình phát; phần Đọc không có audio; mục của phần Nghe đều mang tệp nghe. Kịch bản thứ hai: xóa tệp nghe khỏi
 * kho lưu trữ → kiểm tra báo ATTACHMENT_MISSING và không công bố được; khôi phục tệp → kiểm tra lại đạt.
 */
test("H-SCA Tiếng Anh Nghe và Đọc: phần Nghe chỉ câu audio, đề thử phát được audio, công bố", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  const id = await createExamInUi(page, {
    kind: "HSCA",
    scope: "SUBJECT",
    scopeLabel: /^Nghe và Đọc/,
    subject: "ANH",
    overview: { durationMinutes: 60 },
    code: examCode("W2B-HSCA-ANH"),
    name: "H-SCA Tiếng Anh Nghe và Đọc nghiệm thu 2B",
  });
  const admin = await adminApi();

  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("HSCA_SUBJECT_ANH");
  expect(exam.questionCount).toBe(75);
  expect(exam.durationMinutes).toBe(60);
  expect(exam.maxScore).toBe(75);

  // Phần Nghe bắt buộc audio (REQUIRED), phần Đọc cấm audio (FORBIDDEN).
  const structure = await getStructure(admin, id);
  const nghe = structure.parts.find((part) => part.name === "Nghe");
  const doc = structure.parts.find((part) => part.name === "Đọc");
  expect(nghe?.questionCount).toBe(35);
  expect(doc?.questionCount).toBe(40);
  const ngheSection = (nghe?.sections ?? [])[0];
  const docSection = (doc?.sections ?? [])[0];
  expect(ngheSection?.audioRequirement).toBe("REQUIRED");
  expect(docSection?.audioRequirement).toBe("FORBIDDEN");
  expect((ngheSection?.blocks ?? []).map((block) => [block.questionType, block.questionCount])).toEqual([
    ["SINGLE_CHOICE", 8],
    ["QUESTION_SET", 12],
    ["QUESTION_SET", 15],
  ]);
  expect((docSection?.blocks ?? []).map((block) => [block.questionType, block.questionCount])).toEqual([["QUESTION_SET", 40]]);

  // Nhãn audio của nhóm đầu mỗi phần (điều kiện audio đặt theo phần): Nghe "Có audio", Đọc "Không có audio".
  await openBlockInUi(page, id, doc!.sections![0]!.blocks![0]!.id!);
  await expect(page.getByText("Không có audio", { exact: true }).first()).toBeVisible();
  await openBlockInUi(page, id, ngheSection!.blocks![0]!.id!);
  await expect(page.getByText("Có audio", { exact: true }).first()).toBeVisible();

  await renameFirstBlockAndSaveInUi(page, admin, id);

  const check = await continueToReviewInUi(page, id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  await expectValidPaths(page, 1, 1);

  const { result, panel } = await generateInUi(page, id);
  expectWellFormedPaper(result);
  expect(result.paper.totalQuestions).toBe(75);
  expect(result.paper.totalPoints).toBe(75);
  expect(countBySubject(result)).toEqual({ ANH: 75 });

  // Phần Nghe: mọi mục (câu đơn lẫn bộ) phải có đúng một tệp audio; phần Đọc không mục nào có.
  const ngheItems = itemsOf(result, "Nghe");
  const docItems = itemsOf(result, "Đọc");
  expect(ngheItems.length, "phần Nghe không có mục nào").toBeGreaterThan(0);
  for (const item of ngheItems) {
    expect(audioAttachments(item), `mục ${item.code ?? item.question?.code} thiếu audio`).toHaveLength(1);
  }
  expect(docItems.length, "phần Đọc không có mục nào").toBeGreaterThan(0);
  for (const item of docItems) {
    expect(audioAttachments(item), `mục ${item.code ?? item.question?.code} của phần Đọc có audio`).toHaveLength(0);
  }

  // Trình phát của bộ/câu Nghe đầu tiên: chip "Có audio", nhóm điều khiển và nút Phát hiện trên đề thử.
  const firstNghe = ngheItems[0]!;
  const playerName = firstNghe.kind === "SET" ? /^Trình phát audio · câu \d+–\d+$/ : /^Trình phát audio · câu \d+$/;
  const badge = panel.getByText("Có audio").first();
  await expect(badge).toBeVisible();
  const player = panel.getByRole("group", { name: playerName }).first();
  await expect(player).toBeVisible();
  const play = player.getByRole("button", { name: /^Phát audio · / });
  await expect(play).toBeEnabled();
  await play.click();
  // Phát được thật thì nút đổi thành "Tạm dừng"; môi trường thiếu codec sẽ báo lỗi tại đây (giữ khẳng định cứng).
  await expect(player.getByRole("button", { name: /^Tạm dừng audio · / })).toBeVisible({ timeout: 30_000 });

  const published = await publishInUi(page, id);
  expect(published.version).toBe(1);
  expect((await getExam(admin, id)).servingVersion).toBe(1);
});

test("H-SCA Tiếng Anh Nghe: mất tệp nghe báo ATTACHMENT_MISSING và không công bố được", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  // Bài chỉ phần Nghe: mọi nguồn đều bắt buộc audio nên thiếu tệp nào cũng lộ trong kiểm tra.
  const id = await createExamInUi(page, {
    kind: "HSCA",
    scope: "SUBJECT",
    scopeLabel: /^Nghe\b(?! và)/,
    subject: "ANH",
    overview: { durationMinutes: 35 },
    code: examCode("W2B-HSCA-NGHE"),
    name: "H-SCA Tiếng Anh Nghe nghiệm thu 2B",
  });
  const admin = await adminApi();
  const exam = await getExam(admin, id);
  expect(exam.scope).toBe("PART");
  expect(exam.questionCount).toBe(35);

  // Đường cơ sở: kiểm tra đạt trước khi xóa tệp (sau đó quay lại bước 2 để kiểm tra lại).
  const baseline = await continueToReviewInUi(page, id);
  expect(baseline.status, `lỗi chặn: ${blockingCodes(baseline).join(", ")}`).toBe("VALID");
  expect(collectIssues(baseline).map((issue) => issue.code)).not.toContain("ATTACHMENT_MISSING");
  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await expect(page.getByLabel("Tổng hợp cấu trúc")).toBeVisible();

  // Xóa toàn bộ tệp nghe khỏi kho lưu trữ: mọi câu Nghe đều mất tệp nên kiểm tra chắc chắn bắt được.
  // Vòng xóa nằm trong `try`: xóa dở chừng thì `finally` vẫn trả lại mọi tệp.
  try {
    for (const file of AUDIO_FILES) await s3DeleteObject(hscaAudioKey(file));
    const failed = await continueToReviewInUi(page, id);
    const missing = collectIssues(failed).filter((issue) => issue.code === "ATTACHMENT_MISSING");
    expect(missing.length, `không có ATTACHMENT_MISSING: ${JSON.stringify(failed)}`).toBeGreaterThan(0);
    const missingCodes = [...new Set(missing.flatMap((issue) => issue.questionCodes ?? []))];
    expect(missingCodes.length, "ATTACHMENT_MISSING không chỉ rõ câu").toBeGreaterThan(0);
    // Lỗi chặn nên vẫn ở bước 2; tab Kiểm tra hiện câu và khối có tệp không đọc được.
    await expect(page).toHaveURL(/\/exams\/[^/]+\/structure/);
    expect(failed.status).toBe("INVALID");
    await openEditorTab(page, "Kiểm tra và xem trước", /\/exams\/[^/]+\/review/);
    await expect(page.getByText(/có tệp đính kèm không đọc được/).first()).toBeVisible({ timeout: SOLVER_TIMEOUT_MS });

    // Công bố bị từ chối: hộp thoại "Sẽ công bố 0 / N"; máy chủ trả 422 PATH_NOT_VALID và không tạo phiên bản.
    await expectPublishRefused(page, admin, id, failed);
  } finally {
    // Trả lại đúng byte của tệp nguồn để các bài khác (cùng kho) không chịu ảnh hưởng.
    for (const file of AUDIO_FILES) await s3RestoreSeedAsset(file);
  }

  // Sau khôi phục: quay lại bước 2, kiểm tra lại đạt và công bố được.
  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await expect(page.getByLabel("Tổng hợp cấu trúc")).toBeVisible();
  const restored = await continueToReviewInUi(page, id);
  expect(restored.status, `lỗi chặn sau khôi phục: ${blockingCodes(restored).join(", ")}`).toBe("VALID");
  expect(collectIssues(restored).map((issue) => issue.code)).not.toContain("ATTACHMENT_MISSING");
  const published = await publishInUi(page, id);
  expect(published.version).toBe(1);
});
