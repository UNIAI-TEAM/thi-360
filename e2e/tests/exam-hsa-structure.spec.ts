import { expect, test, type APIRequestContext } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin, RUN_ID } from "./helpers/editor-qa";
import { createExamInUi, examCode, SCENARIO_TIMEOUT_MS } from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * UNI-2752, UNI-2753 (lane HSA-B): bước 2 của HSA. Bài "Tự thiết lập" chỉ cần ba giá trị của bước 1 để đi tiếp; bài dùng cấu trúc
 * môn có số câu của phần và mục chỉ đọc, số câu của nhóm vẫn sửa được.
 */
async function createExam(
  admin: { api: APIRequestContext; headers: Record<string, string> },
  data: Record<string, unknown>,
): Promise<string> {
  const created = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data,
  });
  expect(created.status(), await created.text()).toBe(201);
  const { id } = (await created.json()) as { id: string };
  return id;
}

test("HSA Tự thiết lập: chỉ số câu, thời lượng, thang điểm ở bước 1 là đủ để bước 2 'Tiếp tục' sang tab Kiểm tra", async ({ context, page }) => {
  // Cần cả thay đổi của máy chủ (UNI-2752, `GROUP_ANSWER_TYPE_MISSING` không chặn bài ôn): chỉ xanh khi worker_be đã vào nhánh.
  await loginAdmin(context, "vi");
  const id = await createExamInUi(page, {
    kind: "HSA",
    scope: "SUBJECT",
    subject: "TOAN",
    review: true,
    code: examCode("HSA-TTL"),
    name: "HSA tự thiết lập chỉ ba giá trị",
    overview: { questionCount: 10, durationMinutes: 15, maxScore: 10 },
  });

  await expect(page.getByRole("heading", { name: /^Bài ôn ·/ })).toBeVisible();
  for (const [name, value] of [
    [/^Số lượng/, "10"],
    [/^Thời lượng/, "15"],
    [/^Thang điểm/, "10"],
  ] as const) {
    const field = page.getByRole("textbox", { name });
    await expect(field).toHaveValue(value);
    await expect(field).toHaveAttribute("readonly", "");
  }
  await expect(page.getByRole("link", { name: "Sửa thông tin tổng quan" }).first()).toHaveAttribute("href", `/exams/new?exam=${id}`);
  // Mọi thứ khác (phạm vi, dạng câu, phân bố, câu chùm) nằm trong vùng đóng, không bắt buộc.
  await expect(page.locator("details").filter({ hasText: "Tùy chọn thêm (không bắt buộc)" }).first()).not.toHaveAttribute("open", "");

  await page.getByRole("button", { name: "Tiếp tục", exact: true }).click();
  await page.waitForURL(/\/exams\/[^/]+\/review/, { timeout: 60_000 });
});

test("HSA dùng cấu trúc môn: màn môn không còn ô Số câu của mục; số câu của phần chỉ đọc; số câu của nhóm sửa được", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const id = await createExam(admin, {
    code: `HSA-S-E2E-${RUN_ID}`.toUpperCase(),
    name: `HSA cấu trúc môn ${RUN_ID}`,
    kind: "HSA",
    scope: "SUBJECT",
    subjectCode: "TOAN",
  });

  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  const list = page.getByRole("navigation", { name: "Cấu trúc" });
  await expect(list).toBeVisible();

  // Màn môn: không còn ô Số câu của mục (UNI-2753); tiêu đề vẫn nêu số câu, đường dẫn Sửa thông tin tổng quan ở đầu màn vẫn về bước 1.
  await expect(page.getByRole("heading", { name: /^Môn / })).toBeVisible();
  await expect(page.getByRole("textbox", { name: "Số câu của mục" })).toHaveCount(0);
  await expect(page.getByLabel("Tổng hợp cấu trúc").locator("..").getByRole("link", { name: "Sửa thông tin tổng quan" })).toHaveAttribute(
    "href",
    `/exams/new?exam=${id}`,
  );

  // Màn phần: số câu phải làm và trên đề chỉ đọc. Mục đầu danh sách là "Tổng quan bài" (FE-3b), không phải phần.
  await list.locator(":scope > ol > li:not(:first-child) > button[data-list-item]").first().click();
  for (const name of [/^Số câu phải làm/, /^Số câu trên đề/]) {
    await expect(page.getByRole("textbox", { name })).toHaveAttribute("readonly", "");
  }

  // CR-2H / AD-129: `HSA SUBJECT TOAN` mở `MatrixFormHsa` (`Cấu trúc câu`); các ô vai trò sửa được, còn `Trắc nghiệm đơn` tự tính chỉ đọc.
  await page.getByRole("button", { name: /^Soạn môn/ }).first().click();
  await expect(page.getByRole("heading", { level: 3, name: "Cấu trúc câu" })).toBeVisible();
  for (const name of ["Trắc nghiệm", "Điền đáp án", "Số chùm"] as const) {
    await expect(page.getByRole("textbox", { name, exact: true })).not.toHaveAttribute("readonly", "");
  }
  await expect(page.getByRole("textbox", { name: "Trắc nghiệm đơn", exact: true })).toHaveAttribute("readonly", "");
});

test("HSA Văn: nhóm lấy câu mở và sửa qua danh sách dòng", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const id = await createExam(admin, {
    code: `HSA-VAN-ROW-${RUN_ID}`.toUpperCase(),
    name: `HSA Văn dòng ${RUN_ID}`,
    kind: "HSA",
    scope: "SUBJECT",
    subjectCode: "VAN",
  });

  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  const rows = page.locator("[data-form-row]");
  await expect(rows).toHaveCount(5);
  await expect(page.locator("[data-form-slots] [data-form-slot]")).toHaveCount(5);
  await rows.last().getByRole("button", { name: /^Sửa dòng/ }).click();
  await expect(page.locator("[data-form-row-editor]")).toBeVisible();
  await expect(page.getByRole("radio", { name: "Câu chùm" })).toBeChecked();
  await expect(page.getByRole("switch", { name: "Đảo thứ tự các chùm" })).toBeVisible();
});
