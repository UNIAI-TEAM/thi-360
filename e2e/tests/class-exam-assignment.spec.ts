import { expect, type Page, test } from "@playwright/test";
import { loginAdmin } from "./helpers/editor-qa";
import { createVerifiedAccount, WEB_URL } from "./helpers/mail";
import { createPlatformApiContext } from "./helpers/platform";
import {
  allExams,
  publishedExamSet,
  waitForPublishedExamSet,
  type PublishedExam,
} from "./helpers/published-exam-set";

const ADMIN_URL = (process.env.THI360_ADMIN_URL ?? "http://localhost:3001").replace(/\/+$/, "");
const PLATFORM_PASSWORD = process.env.THI360_DEV_PLATFORM_PASSWORD ?? "platform-dev-only";

function pickPublishedExam(): PublishedExam {
  const discontinued = publishedExamSet().discontinuedExam;
  return allExams().find((exam) => exam.code !== discontinued) ?? allExams()[0]!;
}

async function submitLoginForm(page: Page, identifier: string, password: string) {
  await page.getByLabel(/Tên đăng nhập hoặc email/).fill(identifier);
  await page.getByLabel(/Mật khẩu/).fill(password);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
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

test.describe("Giao bài thi theo lớp học (UNI-2774)", () => {
  test.setTimeout(180_000);

  test("quản trị giao bài cho lớp -> học viên thấy Bài theo lớp ở /home & /prepare -> thu hồi lượt chưa dùng", async ({
    browser,
  }) => {
    const exam = pickPublishedExam();
    const suffix = Date.now().toString(36).toUpperCase();
    const classCode = `E2E-CLS-${suffix}`;
    const className = `Lớp giao bài ${suffix}`;

    const { account, cookies } = await createVerifiedAccount("cls.exam");
    const { api, xsrf } = await createPlatformApiContext();

    const adminContext = await browser.newContext();
    const studentContext = await browser.newContext();
    await studentContext.addCookies(cookies);

    const adminPage = await adminContext.newPage();
    const studentPage = await studentContext.newPage();

    try {
      // 1. Admin đăng nhập và tạo lớp mới + thêm học viên vừa đăng ký
      await adminPage.goto(`${WEB_URL}/login`);
      await submitLoginForm(adminPage, "platform", PLATFORM_PASSWORD);
      await expect(adminPage).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      await adminPage.goto(`${ADMIN_URL}/classes`);
      await expect(adminPage.getByRole("heading", { name: "Lớp học" })).toBeVisible();

      await adminPage.getByRole("button", { name: "Tạo lớp" }).click();
      const createDialog = adminPage.getByRole("dialog");
      await createDialog.getByLabel(/Mã lớp/).fill(classCode);
      await createDialog.getByLabel(/Tên lớp/).fill(className);
      await createDialog.getByRole("button", { name: "Tạo lớp" }).click();
      await expect(createDialog).toBeHidden();

      await adminPage.getByLabel("Tìm kiếm").fill(classCode);
      await adminPage.getByRole("link", { name: classCode }).click();
      await expect(adminPage.getByRole("heading", { name: className })).toBeVisible();

      await adminPage.getByRole("button", { name: "Thêm học viên" }).first().click();
      const addDialog = adminPage.getByRole("dialog");
      await addDialog.getByLabel("Tìm học viên").fill(account.username);
      await addDialog.getByRole("checkbox", { name: new RegExp(account.username) }).check();
      await addDialog.getByRole("button", { name: /Thêm 1 học viên/ }).click();
      await expect(addDialog).toBeHidden();
      await expect(adminPage.getByRole("link", { name: account.username })).toBeVisible();

      // 2. Admin giao bài thi đang xuất bản cho lớp (2 lượt/học viên)
      await adminPage.getByRole("button", { name: "Giao bài thi" }).first().click();
      const assignDialog = adminPage.getByRole("dialog", {
        name: new RegExp(`Giao bài thi cho lớp ${classCode}`),
      });
      await assignDialog.getByLabel("Tìm bài thi").fill(exam.code);
      await assignDialog
        .getByRole("checkbox", { name: new RegExp(`(?:^|\\s)${exam.code}\\s·`) })
        .check();
      const attemptsInput = assignDialog.getByLabel(/Số lượt mỗi học viên/);
      await attemptsInput.fill("2");
      await assignDialog.getByLabel("Ghi chú đợt giao").fill("Bài tập lớp E2E");
      await assignDialog.getByRole("button", { name: "Giao 1 bài thi" }).click();
      await expect(assignDialog).toBeHidden();

      const assignmentsTable = adminPage.getByRole("table", {
        name: "Danh sách đợt giao bài thi cho lớp",
      });
      await expect(assignmentsTable.getByText(`${exam.code} ·`)).toBeVisible();
      await expect(assignmentsTable.getByText("Đang hiệu lực")).toBeVisible();
      await expect(assignmentsTable.getByText("1 HV · đã dùng 0, còn 2")).toBeVisible();

      // 3. Học viên mở /home thấy thẻ "Bài theo lớp · <Tên lớp>", không có nút Mua thêm lượt, mở được /prepare
      await studentPage.goto(`${WEB_URL}/home`);
      await expect(studentPage.getByRole("heading", { level: 1, name: "Bài thi của tôi" })).toBeVisible();
      await expect(studentPage.getByText(`Bài theo lớp · ${className}`)).toBeVisible();
      await expect(studentPage.getByRole("link", { name: /Mua thêm lượt|Mua lại/ })).toHaveCount(0);

      const startLink = studentPage.getByRole("link", { name: "Bắt đầu" }).first();
      await expect(startLink).toHaveAttribute("href", /^\/prepare\/[0-9a-f-]+$/i);
      await startLink.click();

      await expect(studentPage).toHaveURL(/\/prepare\/[0-9a-f-]+$/i);
      await expect(studentPage.getByText(/Giao theo lớp ngày/)).toBeVisible();
      await expect(studentPage.getByText("2 lượt")).toBeVisible();

      // 4. Admin thu hồi đợt giao -> học viên chưa làm lượt nào nên thẻ rỗng ẩn khỏi /home
      await assignmentsTable
        .getByRole("button", { name: `Thu hồi đợt giao bài ${exam.code}` })
        .click();
      const revokeDialog = adminPage.getByRole("dialog", { name: "Thu hồi đợt giao bài thi" });
      await revokeDialog.getByLabel(/Lý do thu hồi/).fill("E2E thu hồi đợt giao");
      await revokeDialog.getByRole("button", { name: "Thu hồi đợt giao" }).click();
      await expect(revokeDialog).toBeHidden();
      await expect(assignmentsTable.getByText("Đã thu hồi")).toBeVisible();

      await studentPage.goto(`${WEB_URL}/home`);
      await expect(studentPage.getByRole("heading", { name: "Bạn chưa có bài thi nào" })).toBeVisible();
    } finally {
      const listRes = await api.get(`${ADMIN_URL}/api/v1/admin/classes?q=${classCode}&size=10`);
      if (listRes.ok()) {
        const listJson = (await listRes.json()) as { items?: Array<{ id: string; code: string }> };
        for (const item of listJson.items ?? []) {
          if (item.code === classCode) {
            await api.delete(`${ADMIN_URL}/api/v1/admin/classes/${item.id}?reason=E2E+cleanup`, {
              headers: {
                origin: ADMIN_URL,
                "x-xsrf-token": xsrf,
              },
            });
          }
        }
      }
      await adminContext.close();
      await studentContext.close();
      await api.dispose();
    }
  });
});
