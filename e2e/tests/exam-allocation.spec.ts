import { expect, test, type APIRequestContext } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin, RUN_ID } from "./helpers/editor-qa";

/**
 * Bước 2, vùng sửa FE-3b (UNI-2664): phần có mã của QDA FULL (đủ 6 môn từ mẫu, lưu được) và bài ôn HSA 10 câu.
 * Cả hai bài tạo bằng API (mẫu cấu trúc nạp sẵn ở máy chủ).
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

test("QDA FULL: phần 3 liệt kê đủ 6 môn từ mẫu, thêm chỉ tiêu chủ đề rồi lưu", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const id = await createExam(admin, { code: `QDA-F-E2E-${RUN_ID}`.toUpperCase(), name: `QDA phân bổ ${RUN_ID}`, kind: "QDA", scope: "FULL" });

  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  const list = page.getByRole("navigation", { name: "Cấu trúc" });
  await expect(list).toBeVisible();

  // Phần 3 là phần có mã: danh sách môn thay cho ma trận theo từng mã, không còn thao tác gắn tổ hợp.
  const parts = list.locator(":scope > ol > li > button[data-list-item]");
  await parts.nth(3).click();
  const subjects = page.getByRole("table", { name: /Các môn của/ });
  await expect(subjects).toBeVisible();
  await expect(subjects.getByRole("row")).toHaveCount(7);
  await expect(page.getByRole("button", { name: "Gắn tổ hợp" })).toHaveCount(0);

  // Mở môn đầu tiên, thêm một chỉ tiêu chủ đề (chờ mapping) và lưu.
  await subjects.getByRole("button", { name: /^Thiết lập môn/ }).first().click();
  // MT (FTM R-6): "Thêm chỉ tiêu chủ đề" của khối phạm vi chỉ mở bảng; thêm dòng là nút của chính bảng (chờ mapping).
  await page.getByRole("button", { name: "Thêm chỉ tiêu chủ đề" }).click();
  await page.locator("[data-topic-add]").click();
  await expect(page.getByText("Chờ mapping")).toBeVisible();
  // Môn Khoa học của QDA mở MT (FTM B7), không có dòng chữ "thiết lập đầy đủ" của MD.
  await expect(page.getByTestId("full-form-notice")).toHaveCount(0);
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(page.getByText("Có thay đổi chưa lưu")).toHaveCount(0);

  await page.reload();
  await expect(page.getByText("Chờ mapping")).toBeVisible();
});

test("bài ôn HSA 10 câu: màn rút gọn ba trường", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const templatesRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/exam-templates`, { headers: admin.headers });
  expect(templatesRes.status(), await templatesRes.text()).toBe(200);
  const templates = (await templatesRes.json()) as Array<{ code: string; parts?: Array<{ partId?: string | null }> }>;
  const partId = templates.find((template) => template.code === "HSA_REVIEW")?.parts?.[0]?.partId;
  expect(partId).toBeTruthy();

  const id = await createExam(admin, {
    code: `HSA-R-E2E-${RUN_ID}`.toUpperCase(),
    name: `HSA bài ôn ${RUN_ID}`,
    kind: "HSA",
    scope: "SUBJECT",
    subjectCode: "TOAN",
    buildMode: "REVIEW",
    // `ExamOverview.apply` khớp hàng theo `partId`; sau FU (V202611200712), `partId` của `HSA_REVIEW` là UUID nên đọc động từ `/exam-templates`.
    overview: [{ partId, questionCount: 10 }],
    durationMinutes: 15,
  });

  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await expect(page.getByRole("heading", { name: /^Bài ôn ·/ })).toBeVisible();
  await expect(page.getByRole("textbox", { name: /Số lượng/ })).toHaveValue("10");
  // Ba giá trị của bước 1 chỉ đọc; phạm vi, dạng câu, phân bố và câu chùm nằm trong vùng đóng "Tùy chọn thêm" (UNI-2752).
  await expect(page.getByRole("textbox", { name: /Số lượng/ })).toHaveAttribute("readonly", "");
  await expect(page.getByText(/Phân bố khối lớp và độ khó/)).toBeHidden();
  await page.getByText("Tùy chọn thêm (không bắt buộc)").click();
  await expect(page.getByText(/Phân bố khối lớp và độ khó/)).toBeVisible();
  await expect(page.getByText(/^Câu chùm/)).toBeVisible();
  // Bài ôn không bao giờ ra MT/DS và không có dòng chữ của MD (FTM R-1).
  await expect(page.getByTestId("full-form-notice")).toHaveCount(0);
});
