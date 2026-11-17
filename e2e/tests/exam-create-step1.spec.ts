import { expect, test } from "@playwright/test";
import { ADMIN_URL, loginAdmin, RUN_ID } from "./helpers/editor-qa";

test.setTimeout(120_000);

/**
 * FE-1 (UNI-2661), bước 1 của Tạo bài thi (2B 15.1): HSA, Toàn bài, mẫu mặc định nạp vào bảng tổng quan, tạo bài rồi
 * vào bước 2 (Cấu trúc). Cần stack có BE-6 (`POST /exams` nhận `buildMode`, `overview`).
 */
test("Tạo bài thi HSA Toàn bài ở bước 1 rồi vào bước 2", async ({ page, context }) => {
  await loginAdmin(context, "vi");
  await page.goto(`${ADMIN_URL}/exams/new`);

  const form = page.getByRole("form", { name: "Tạo bài thi" });
  await expect(form).toBeVisible();

  // HSA là kỳ thi đầu danh mục: Toàn bài, bảng tổng quan nạp số của mẫu mặc định, không có ô chọn phiên bản.
  await expect(page.getByRole("radio", { name: /^HSA/ })).toBeChecked();
  await expect(page.getByRole("radio", { name: /^Toàn bài/ })).toBeChecked();
  await expect(page.getByText("Phiên bản cấu trúc chọn ở bước 2")).toBeVisible();
  const counts = page.getByRole("textbox", { name: /^Số câu của/ });
  await expect(counts.first()).not.toHaveValue("");
  const summary = page.getByRole("complementary", { name: "Tóm tắt" });
  await expect(summary.getByText("Tổng số câu")).toBeVisible();

  const code = `E2E-HSA-${RUN_ID}`.toUpperCase();
  await page.getByRole("textbox", { name: /Mã bài thi/ }).fill(code);
  await page.getByRole("textbox", { name: /Tên bài thi/ }).fill(`HSA Toàn bài ${RUN_ID}`);
  await page.getByRole("button", { name: "Tạo bài thi và soạn khối câu hỏi" }).first().click();

  await page.waitForURL(/\/exams\/[^/]+\/structure$/);
  await expect(page.getByRole("main")).toBeVisible();
});
