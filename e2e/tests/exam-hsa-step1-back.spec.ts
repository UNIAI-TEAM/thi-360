import { expect, test, type Page } from "@playwright/test";
import { adminApi, ADMIN_URL, loginAdmin } from "./helpers/editor-qa";
import {
  createExamInUi,
  examCode,
  getExam,
  getStructure,
  subjectCombobox,
  type StructurePart,
  type UiExamSpec,
} from "./helpers/wave-2b";

test.setTimeout(240_000);

/** Ô số câu đầu tiên của bảng "Cấu trúc tổng quan" ở bước 1. */
const overviewCount = (page: Page) =>
  page.getByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" }).getByRole("textbox", { name: /^Số câu của/ }).first();

/** Số câu của từng khối lấy câu trong phần, theo thứ tự lưu. */
const blockCounts = (part: StructurePart) =>
  (part.sections ?? []).flatMap((section) => (section.blocks ?? []).map((block) => block.questionCount ?? null));

/**
 * UNI-2751: "Sửa thông tin tổng quan" ở bước 2 mở lại đúng màn Tạo bài thi (`/exams/new?exam=<id>`): thanh bước ở bước 1,
 * ba khối đánh số, cột Tóm tắt, điền sẵn theo bài đã lưu. Tab Thông tin chung của bài đã lưu không đổi.
 */
async function backToStep1(page: Page, id: string, spec: UiExamSpec): Promise<void> {
  await page.getByLabel("Tổng hợp cấu trúc").locator("..").getByRole("link", { name: "Sửa thông tin tổng quan" }).click();
  await page.waitForURL(new RegExp(`/exams/new\\?exam=${id}$`));
  await expect(page.getByRole("form", { name: "Tạo bài thi" })).toBeVisible();
  await expect(page.getByRole("list", { name: "Các bước tạo bài thi" }).locator('[aria-current="step"]')).toContainText("Thông tin chung");
  for (const heading of ["Thông tin chung", "Phạm vi bài thi", "Cấu trúc tổng quan"]) {
    await expect(page.getByRole("heading", { name: heading, level: 2 }).first()).toBeVisible();
  }
  await expect(page.getByRole("region", { name: "Tóm tắt" })).toBeVisible();
  await expect(page.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue(spec.code);
  await expect(page.getByRole("textbox", { name: /Tên bài thi/ })).toHaveValue(spec.name);
  // Không phải tab Thông tin chung: không có cột Tình trạng.
  await expect(page.getByRole("heading", { name: "Tình trạng" })).toHaveCount(0);
}

/** Từ bước 1 đã mở lại: đặt số câu của phần đầu rồi "Lưu và tiếp tục"; cấu trúc đổi đúng số đó (khối lấy câu giữ nguyên). */
async function editFirstCountAndContinue(page: Page, id: string, newCount: number): Promise<void> {
  const admin = await adminApi();
  const before = await getStructure(admin, id);
  const count = overviewCount(page);
  await expect(count).toBeVisible();
  await expect(count).not.toHaveValue(String(newCount));

  await count.fill(String(newCount));
  await page.getByRole("button", { name: "Lưu và tiếp tục" }).click();
  await page.waitForURL(new RegExp(`/exams/${id}/structure$`));

  const after = await getStructure(admin, id);
  expect(after.parts[0]!.questionCount).toBe(newCount);
  // Số câu của khối lấy câu không tự đổi theo số câu của phần: bước 2 báo lệch để người dùng tự chia lại.
  expect(blockCounts(after.parts[0]!)).toEqual(blockCounts(before.parts[0]!));
  expect(after.parts.slice(1).map((part) => part.questionCount)).toEqual(before.parts.slice(1).map((part) => part.questionCount));
}

async function expectStep2Mismatch(page: Page, newCount: number): Promise<void> {
  await expect(page.getByText(new RegExp(`các khối có \\d+ câu, môn cần ${newCount} câu`)).first()).toBeVisible();
}

test("HSA Toàn bài: quay lại bước 1 từ bước 2, sửa số câu phần 1, bước 2 hiện số mới và lệch phân bổ", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = { kind: "HSA", scope: "FULL", code: examCode("E2E-HSA-BACK"), name: "HSA quay lại bước 1 nghiệm thu" };
  const id = await createExamInUi(page, spec);
  await backToStep1(page, id, spec);
  await editFirstCountAndContinue(page, id, 45);
  await expectStep2Mismatch(page, 45);
});

test("HSA Theo phần: quay lại bước 1 điền sẵn phạm vi và phần đã lưu, sửa số câu rồi về bước 2", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = {
    kind: "HSA",
    scope: "FULL",
    scopeLabel: /^Theo phần/,
    code: examCode("E2E-HSA-PBACK"),
    name: "HSA theo phần quay lại bước 1 nghiệm thu",
  };
  const id = await createExamInUi(page, spec);
  await backToStep1(page, id, spec);
  await expect(page.getByRole("radio", { name: /^Theo phần/ })).toBeChecked();
  const current = Number(await overviewCount(page).inputValue());
  await editFirstCountAndContinue(page, id, current + 1);
});

test("HSA Theo môn + Tự thiết lập: quay lại bước 1, cách dựng bài hiện đúng, sửa số câu rồi về bước 2", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = {
    kind: "HSA",
    scope: "SUBJECT",
    subject: "TOAN",
    review: true,
    code: examCode("E2E-HSA-RBACK"),
    name: "HSA tự thiết lập quay lại bước 1 nghiệm thu",
    overview: { questionCount: 10, durationMinutes: 15, maxScore: 10 },
  };
  const id = await createExamInUi(page, spec);
  await backToStep1(page, id, spec);
  await expect(page.getByRole("radio", { name: /^Tự thiết lập bài ôn/ })).toBeChecked();
  await editFirstCountAndContinue(page, id, 12);
});

test("THPTQG Toán: cùng đường quay lại bước 1, sửa số câu phần đầu", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = {
    kind: "THPTQG",
    scope: "SUBJECT",
    subject: "TOAN",
    code: examCode("E2E-TQG-BACK"),
    name: "THPTQG Toán quay lại bước 1 nghiệm thu",
  };
  const id = await createExamInUi(page, spec);
  await backToStep1(page, id, spec);
  await editFirstCountAndContinue(page, id, 7);
  await expectStep2Mismatch(page, 7);
});

test("HSA Toàn bài: đổi sang Theo môn ở bước 1 hỏi nạp lại cấu trúc, xác nhận xong bước 2 hiện cấu trúc mới", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = { kind: "HSA", scope: "FULL", code: examCode("E2E-HSA-SBACK"), name: "HSA đổi phạm vi ở bước 1 nghiệm thu" };
  const id = await createExamInUi(page, spec);
  await backToStep1(page, id, spec);

  await page.getByRole("radio", { name: /^Theo môn/ }).check();
  await subjectCombobox(page).click();
  await page.getByRole("option", { name: "Toán học", exact: true }).click();
  await page.getByRole("button", { name: "Lưu và tiếp tục" }).click();

  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  // Chưa xác nhận thì chưa đổi gì ở máy chủ.
  expect((await getExam(await adminApi(), id)).scope).toBe("FULL");
  await dialog.getByRole("button", { name: "Nạp lại cấu trúc" }).click();
  await page.waitForURL(new RegExp(`/exams/${id}/structure$`));

  const exam = await getExam(await adminApi(), id);
  expect(exam.scope).toBe("SUBJECT");
  expect(exam.templateCode).toBe("HSA_SUBJECT_TOAN");
  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await expect(page.getByRole("navigation", { name: "Cấu trúc" })).toBeVisible();
});

test("tab Thông tin chung của bài đã lưu không đổi: vẫn là bố cục tab với cột Tình trạng", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = { kind: "HSA", scope: "FULL", code: examCode("E2E-HSA-GENERAL"), name: "HSA tab thông tin chung nghiệm thu" };
  const id = await createExamInUi(page, spec);
  await page.goto(`${ADMIN_URL}/exams/${id}`);
  await expect(page.getByRole("heading", { name: "Tình trạng" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Lưu thông tin" })).toBeVisible();
});
