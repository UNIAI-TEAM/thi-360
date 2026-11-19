import { expect, test } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin, RUN_ID } from "./helpers/editor-qa";

/**
 * Bước 2 (khung bốn vùng, R-05): sửa một phần, chuyển sang phần khác thì hiện hộp thoại ba nút; "Bỏ thay đổi" khôi phục
 * giá trị đã lưu. Chạy trên bài HSA Toàn bài tạo bằng API (mẫu cấu trúc nạp sẵn).
 */
test("sửa phần rồi chuyển phần: hộp thoại ba nút, Bỏ thay đổi trả về giá trị đã lưu", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const created = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data: { code: `HSA-F-E2E-${RUN_ID}`.toUpperCase(), name: `HSA khung bước 2 ${RUN_ID}`, kind: "HSA", scope: "FULL" },
  });
  expect(created.status(), await created.text()).toBe(201);
  const { id } = (await created.json()) as { id: string };

  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  const list = page.getByRole("navigation", { name: "Cấu trúc" });
  await expect(list).toBeVisible();

  // Mở Phần 1 (chưa có thay đổi nên không hỏi), sửa điểm tối đa của phần. Nút phần là nút đầu của mỗi mục mức trên:
  // chọn theo cấu trúc vùng danh sách, không theo tên (tên phần lấy từ mẫu, không còn tiền tố "Phần N"). Mục đầu
  // danh sách là "Tổng quan bài" (FE-3b), không phải phần.
  const parts = list.locator(":scope > ol > li:not(:first-child) > button[data-list-item]");
  await parts.nth(0).click();
  const points = page.getByRole("textbox", { name: /Điểm tối đa của phần/ });
  await expect(points).toBeVisible();
  const saved = await points.inputValue();
  await points.fill(saved === "77" ? "78" : "77");
  await expect(page.getByText("Có thay đổi chưa lưu")).toBeVisible();

  // Chuyển sang Phần 2: hộp thoại ba nút.
  await parts.nth(1).click();
  const dialog = page.getByRole("dialog", { name: /^Lưu thay đổi của/ });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("button", { name: "Ở lại" })).toBeVisible();
  await expect(dialog.getByRole("button", { name: "Lưu và chuyển" })).toBeVisible();

  // Bỏ thay đổi: sang Phần 2, rồi về Phần 1 thì ô điểm trở lại giá trị đã lưu.
  await dialog.getByRole("button", { name: "Bỏ thay đổi" }).click();
  await expect(dialog).toBeHidden();
  await parts.nth(0).click();
  await expect(page.getByRole("textbox", { name: /Điểm tối đa của phần/ })).toHaveValue(saved);
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

/**
 * `?block=` đi qua `navigation.ts` (FTM R-15): khi mọi dạng biểu mẫu theo môn đang tắt, đường dẫn tới một nhóm vẫn mở màn nhóm đầy đủ
 * như trước, không có dòng chữ "thiết lập đầy đủ". Bài HSA Toàn bài tạo bằng API; id nhóm lấy từ cấu trúc đã lưu.
 */
test("?block= mở màn nhóm đầy đủ khi biểu mẫu theo môn đang tắt", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const created = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data: { code: `HSA-B-E2E-${RUN_ID}`.toUpperCase(), name: `HSA nhóm ${RUN_ID}`, kind: "HSA", scope: "FULL" },
  });
  expect(created.status(), await created.text()).toBe(201);
  const { id } = (await created.json()) as { id: string };
  const structure = await admin.api.get(`${ADMIN_URL}/api/v1/admin/exams/${id}/structure`);
  expect(structure.status(), await structure.text()).toBe(200);
  const { parts } = (await structure.json()) as { parts: Array<{ sections?: Array<{ blocks?: Array<{ id: string }> }> }> };
  const blockId = parts.flatMap((part) => part.sections ?? []).flatMap((section) => section.blocks ?? [])[0]?.id;
  expect(blockId).toBeTruthy();

  await page.goto(`${ADMIN_URL}/exams/${id}/structure?block=${blockId}`);
  await expect(page.getByRole("textbox", { name: "Tên nhóm" })).toBeVisible();
  await expect(page.getByTestId("full-form-notice")).toHaveCount(0);
});
