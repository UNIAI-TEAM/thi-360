import { expect, test, type Page } from "@playwright/test";
import { adminApi, ADMIN_URL, loginAdmin } from "./helpers/editor-qa";
import { createExamInUi, examCode, getStructure, type StructurePart, type UiExamSpec } from "./helpers/wave-2b";

test.setTimeout(240_000);

/** Ô số câu đầu tiên của bảng "Cấu trúc tổng quan" ở tab Thông tin chung. */
const overviewCount = (page: Page) =>
  page.getByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" }).getByRole("textbox", { name: /^Số câu của/ }).first();

/** Số câu của từng khối lấy câu trong phần, theo thứ tự lưu. */
const blockCounts = (part: StructurePart) =>
  (part.sections ?? []).flatMap((section) => (section.blocks ?? []).map((block) => block.questionCount ?? null));

/**
 * UNI-2751: "Sửa thông tin tổng quan" ở bước 2 đưa về tab Thông tin chung, nơi có lại bảng "Cấu trúc tổng quan" của bước 1.
 * Sửa số câu của phần đầu rồi Lưu thì cấu trúc đổi đúng số đó (khối lấy câu giữ nguyên) và bước 2 báo lệch phân bổ.
 */
async function backToStep1AndEdit(page: Page, spec: UiExamSpec, newCount: number): Promise<string> {
  const id = await createExamInUi(page, spec);
  const admin = await adminApi();
  const before = await getStructure(admin, id);

  await page.getByRole("link", { name: "Sửa thông tin tổng quan" }).click();
  await page.waitForURL(new RegExp(`/exams/${id}$`));
  const count = overviewCount(page);
  await expect(count).toBeVisible();
  await expect(count).not.toHaveValue(String(newCount));

  await count.fill(String(newCount));
  await page.getByRole("button", { name: "Lưu thông tin" }).click();
  // Chỉ thông báo nổi: cùng câu còn nằm trong vùng đọc màn hình ẩn (sr-only), nên getByText trần vi phạm strict mode.
  await expect(page.locator("[data-sonner-toast]").getByText("Đã lưu thông tin chung.")).toBeVisible();

  const after = await getStructure(admin, id);
  expect(after.parts[0]!.questionCount).toBe(newCount);
  // Số câu của khối lấy câu không tự đổi theo số câu của phần: bước 2 báo lệch để người dùng tự chia lại.
  expect(blockCounts(after.parts[0]!)).toEqual(blockCounts(before.parts[0]!));
  expect(after.parts.slice(1).map((part) => part.questionCount)).toEqual(before.parts.slice(1).map((part) => part.questionCount));
  return id;
}

async function expectStep2Mismatch(page: Page, id: string, newCount: number): Promise<void> {
  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await expect(page.getByText(new RegExp(`các khối có \\d+ câu, môn cần ${newCount} câu`)).first()).toBeVisible();
}

test("HSA Toàn bài: quay lại từ bước 2, sửa số câu phần 1, bước 2 hiện số mới và lệch phân bổ", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = { kind: "HSA", scope: "FULL", code: examCode("E2E-HSA-BACK"), name: "HSA quay lại bước 1 nghiệm thu" };
  const id = await backToStep1AndEdit(page, spec, 45);
  await expectStep2Mismatch(page, id, 45);
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
  const id = await backToStep1AndEdit(page, spec, 7);
  await expectStep2Mismatch(page, id, 7);
});
