import { expect, test, type Browser, type BrowserContext, type Page } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin, RUN_ID } from "./helpers/editor-qa";
import {
  applyMatrixCookies,
  auditHorizontalOverflow,
  cellLabel,
  launchMatrixBrowser,
  matrixCells,
  openMatrixContext,
  type MatrixCell,
} from "./helpers/visual-matrix";

/**
 * FU (UNI-2700) AC-8, "giao diện soạn đề 2B": ở 390px và 1280px, sáng và tối, vi và en, thẻ câu hỏi, bảng nhóm câu, bảng
 * tóm tắt, bảng chỉ tiêu và bảng môn không làm trang cuộn ngang; KiemTra2B và SinhThu2B khớp bản vẽ (thanh thẻ, nút đầu
 * trang, dải tóm tắt, phạm vi dạng thẻ chọn); xem trước có đếm ngược và giá trị gốc của câu bị bỏ.
 *
 * Chạy trên stack của lane (bài HSA có phần chọn môn Khoa học tạo bằng API). Luôn export `THI360_S3_ENDPOINT` của lane trước
 * khi chạy bộ E2E: `helpers/wave-2b.ts` báo lỗi khi thiếu để không chạm kho dùng chung.
 */
test.setTimeout(600_000);

let browser: Browser;
let examId: string;

test.beforeAll(async () => {
  browser = await launchMatrixBrowser();
  const admin = await adminApi();
  const created = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data: { code: `HSA-FU-E2E-${RUN_ID}`.toUpperCase(), name: `HSA tồn đọng 2B ${RUN_ID}`, kind: "HSA", scope: "FULL" },
  });
  expect(created.status(), await created.text()).toBe(201);
  examId = ((await created.json()) as { id: string }).id;
  // Bảng kết quả kiểm tra cấp các đường lựa chọn cho tab Xem trước.
  const checked = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${examId}/check`, {
    headers: admin.headers,
    data: { scope: "EXAM" },
  });
  expect(checked.ok(), await checked.text()).toBe(true);
});

test.afterAll(async () => {
  await browser.close();
});

async function openCell(cell: MatrixCell): Promise<{ context: BrowserContext; page: Page }> {
  const context = await openMatrixContext(browser, cell, ADMIN_URL);
  await loginAdmin(context, cell.lang);
  await applyMatrixCookies(context, cell, ADMIN_URL);
  return { context, page: await context.newPage() };
}

/** Trang không cuộn ngang; khi hỏng nêu màn và các phần tử thò ra ngoài mép phải. */
async function expectNoOverflow(page: Page, label: string): Promise<void> {
  const overflow = await auditHorizontalOverflow(page);
  expect(overflow.scrollWidth, `${label}: trang cuộn ngang. ${overflow.offenders.join("; ")}`).toBeLessThanOrEqual(overflow.clientWidth);
}

test.describe("giao diện soạn đề 2B", () => {
  for (const cell of matrixCells()) {
    test(`bước Cấu trúc không tràn ngang ở ${cellLabel(cell)}: bảng tóm tắt, bảng nhóm câu, bảng chỉ tiêu, bảng môn`, async () => {
      const { context, page } = await openCell(cell);
      try {
        await page.goto(`${ADMIN_URL}/exams/${examId}/structure`);
        const list = page.getByRole("navigation").filter({ has: page.locator("button[data-list-item]") }).first();
        await expect(list).toBeVisible();
        await expectNoOverflow(page, `${cellLabel(cell)} tổng quan`);

        // Mở lần lượt từng mục của danh sách (tổng quan, phần, môn, nhóm): mỗi màn không làm trang cuộn ngang.
        // Bảng rộng cuộn trong khung của nó (role=region) là đúng quy ước, nên chỉ đo tài liệu.
        const items = list.locator("button[data-list-item]");
        const count = await items.count();
        expect(count, "danh sách cấu trúc có mục").toBeGreaterThan(1);
        for (let index = 0; index < count; index += 1) {
          await items.nth(index).click();
          await page.waitForLoadState("networkidle");
          await expectNoOverflow(page, `${cellLabel(cell)} mục ${index + 1}/${count}`);
        }
        // Mỗi bảng của màn có vùng cuộn đặt tên (nhãn bảng), tức đã bọc trong khung cuộn thay vì tràn ra trang.
        for (const region of await page.getByRole("region").all()) {
          await expect(region, "vùng cuộn có tên").toHaveAccessibleName(/.+/);
        }
      } finally {
        await context.close();
      }
    });

    test(`thẻ câu hỏi của đề xem trước không tràn ngang ở ${cellLabel(cell)}`, async () => {
      test.skip(cell.mode === "dark" && cell.lang === "en", "một ô cho mỗi khổ đủ để đo thẻ câu hỏi; ô tối-en dùng chung bố cục");
      const { context, page } = await openCell(cell);
      try {
        await page.goto(`${ADMIN_URL}/exams/${examId}/review?tab=preview`);
        const scope = page.getByRole("group", { name: cell.lang === "vi" ? "Phạm vi sinh thử" : "Trial scope" });
        await expect(scope).toBeVisible();
        await page.getByRole("button", { name: cell.lang === "vi" ? "Sinh đề xem trước" : "Generate preview paper" }).click();
        const card = page.locator("article").first();
        await expect(card).toBeVisible({ timeout: 120_000 });
        await expectNoOverflow(page, `${cellLabel(cell)} thẻ câu hỏi`);
      } finally {
        await context.close();
      }
    });
  }

  test("KiemTra2B: thanh thẻ, nút đầu trang, dải tóm tắt", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    await page.goto(`${ADMIN_URL}/exams/${examId}/review`);

    // Thanh thẻ: các trang của bài thi, thẻ đang mở là Kiểm tra và xem trước.
    const tabs = page.getByRole("navigation", { name: "Các phần của bài thi" });
    await expect(tabs.getByRole("link", { name: "Kiểm tra và xem trước" })).toHaveAttribute("aria-current", "page");
    // Nút đầu trang: hàng tiêu đề của bài có nút Kiểm tra lại.
    await expect(page.locator("header").getByRole("button", { name: "Kiểm tra lại" })).toBeVisible();
    // Dải tóm tắt: lỗi, cảnh báo, chưa xác nhận, môn đã kiểm tra.
    const counters = page.getByLabel("Số lượng vấn đề");
    await expect(counters).toBeVisible();
    for (const label of ["Lỗi", "Cảnh báo"]) await expect(counters.getByText(label, { exact: true })).toBeVisible();
  });

  test("SinhThu2B: phạm vi dạng thẻ chọn, có đếm ngược và giá trị gốc của câu bị bỏ", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    await page.goto(`${ADMIN_URL}/exams/${examId}/review?tab=preview`);

    const scope = page.getByRole("group", { name: "Phạm vi sinh thử" });
    await expect(scope).toBeVisible();
    const radios = scope.getByRole("radio");
    await expect(radios).toHaveCount(3);
    await expect(scope.getByRole("radio", { name: "Cả bài" })).toBeDisabled();
    await expect(scope.getByRole("radio", { name: "Một mã hoặc nhánh" })).toBeChecked();

    // Một môn của HSA Khoa học, 16 câu: đề bỏ 1 câu nên bảng Yêu cầu / Thực tế ghi cả số gốc.
    await scope.getByRole("radio", { name: "Một môn" }).check();
    await scope.getByRole("combobox", { name: "Môn" }).click();
    await page.getByRole("option", { name: /Vật lí/ }).first().click();
    await scope.getByRole("combobox", { name: "Số câu của môn" }).click();
    await page.getByRole("option", { name: /^16 câu/ }).click();
    await page.getByRole("button", { name: "Sinh đề xem trước" }).click();

    await expect(page.getByText(/giữ trong 30 phút \(còn \d+ phút\)/)).toBeVisible({ timeout: 120_000 });
    await expect(page.getByText(/\(\d+, đã bỏ 1 câu\)/).first()).toBeVisible();
  });
});
