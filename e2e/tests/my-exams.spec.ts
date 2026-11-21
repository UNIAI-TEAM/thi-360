import { expect, test } from "@playwright/test";
import { loginAdmin } from "./helpers/editor-qa";
import { createVerifiedAccount, WEB_URL } from "./helpers/mail";
import {
  allExams,
  publishedExamSet,
  waitForPublishedExamSet,
  type PublishedExam,
} from "./helpers/published-exam-set";

function saleExam(): PublishedExam {
  const discontinued = publishedExamSet().discontinuedExam;
  return allExams().find((exam) => exam.code !== discontinued) ?? allExams()[0]!;
}

test.beforeAll(async ({ browser }) => {
  test.setTimeout(300_000);
  const context = await browser.newContext();
  try {
    await loginAdmin(context, "vi");
    await waitForPublishedExamSet(context.request, { timeoutMs: 240_000 });
  } finally {
    await context.close();
  }
});

test.describe("Bài thi của tôi & Đơn của tôi", () => {
  test.setTimeout(180_000);

  test("AC-19: mua xong thấy thẻ bài thi trên /home và nút Bắt đầu trỏ /prepare/<uuid>", async ({ context, page }) => {
    const exam = saleExam();
    const { cookies } = await createVerifiedAccount("me.home");
    await context.addCookies(cookies);

    await page.goto(`${WEB_URL}/home`);
    await expect(page.getByRole("heading", { level: 1, name: "Bài thi của tôi" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Bạn chưa có bài thi nào" })).toBeVisible();

    await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}`);
    await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await page.waitForURL(/\/mock-vnpay/);
    await page.locator('a[data-mock-action="success"]').click();
    await page.waitForURL(/\/checkout\/result/);
    await page.getByRole("link", { name: "Vào Bài thi của tôi" }).click();

    await expect(page).toHaveURL(`${WEB_URL}/home`);
    const examTitle = page.locator("[data-exam-content]").first();
    await expect(examTitle).toBeVisible();

    const startLink = page.getByRole("link", { name: "Bắt đầu" }).first();
    await expect(startLink).toHaveAttribute("href", /^\/prepare\/[0-9a-f-]+$/i);
    await expect(page.getByRole("link", { name: "Mua thêm lượt" }).first()).toHaveAttribute(
      "href",
      new RegExp(`/checkout/${exam.productCode}`),
    );
  });

  test("AC-20: Đơn của tôi — hủy đơn chờ và xem chi tiết đơn", async ({ context, page }) => {
    const exam = saleExam();
    const { cookies } = await createVerifiedAccount("me.orders");
    await context.addCookies(cookies);

    await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}`);
    await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await page.waitForURL(/\/mock-vnpay/);

    await page.goto(`${WEB_URL}/account/orders`);
    await expect(page.getByRole("heading", { level: 1, name: "Đơn của tôi" })).toBeVisible();
    await expect(page.getByText("Chờ thanh toán").first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Tiếp tục thanh toán" }).first()).toBeVisible();

    await page.getByRole("button", { name: "Hủy đơn" }).first().click();
    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible();
    await dialog.getByRole("button", { name: "Hủy đơn" }).click();

    await expect(page.getByText("Đã hủy").first()).toBeVisible();

    await page.getByRole("link", { name: "Xem chi tiết" }).first().click();
    await page.waitForURL(/\/account\/orders\/.+/);
    await expect(page.getByRole("heading", { level: 2, name: "Thông tin đơn hàng" })).toBeVisible();
    await expect(page.getByRole("heading", { level: 2, name: "Lịch sử xử lý" })).toBeVisible();
  });
});
