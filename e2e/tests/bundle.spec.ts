import { randomUUID } from "node:crypto";
import { expect, test, type APIRequestContext } from "@playwright/test";
import { adminApi, ADMIN_URL, loginAdmin, RUN_ID, WEB_URL, type AdminApi } from "./helpers/editor-qa";
import { createVerifiedAccount } from "./helpers/mail";
import { examByCode, reviewExam, sp2eBundle, waitForPublishedExamSet } from "./helpers/published-exam-set";

const CLEAN_RUN_SUFFIX = RUN_ID.replace(/[^a-z0-9]/gi, "").toUpperCase().slice(0, 8) || "RUN";

const SUBJECT_NAME_VI: Readonly<Record<string, string>> = {
  TOAN: "Toán học",
  VAN: "Ngữ văn",
  ANH: "Tiếng Anh",
};

function readXsrfCookie(
  cookies: Awaited<ReturnType<APIRequestContext["storageState"]>>["cookies"],
): string {
  return (
    cookies.find((cookie) => cookie.name === "thi360_xsrf")?.value ??
    cookies.find((cookie) => cookie.name === "XSRF-TOKEN")?.value ??
    ""
  );
}

async function discontinueBundleIfPublished({ api, headers }: AdminApi, bundleId: string | undefined): Promise<void> {
  if (!bundleId) return;
  const detailRes = await api.get(`${ADMIN_URL}/api/v1/admin/bundles/${bundleId}`);
  if (!detailRes.ok()) return;
  const detail = (await detailRes.json()) as { status: string; revision: number };
  if (detail.status === "PUBLISHED") {
    await api.post(`${ADMIN_URL}/api/v1/admin/bundles/${bundleId}/discontinue`, {
      headers,
      data: { expectedRevision: detail.revision },
    });
  }
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

test.describe("FB · Bộ đề trên web", () => {
  test.setTimeout(240_000);

  test("AC-1, AC-2, AC-9, AC-10, AC-13: tạo và công bố bộ SP2E 3 môn, lọc danh mục, mua qua VNPAY, xem đơn admin và bắt đầu 1 môn", async ({
    browser,
  }) => {
    const expectedExamCodes = sp2eBundle().examCodes;
    const expectedExamNames = expectedExamCodes.map((code) => examByCode(code).name);
    const bundleCode = `BO-SP2E-${CLEAN_RUN_SUFFIX}`;
    const bundleName = `Bộ đề SP2E E2E ${CLEAN_RUN_SUFFIX}`;
    const bundleSummary = `Bộ 3 đề SP2E E2E ${CLEAN_RUN_SUFFIX}`;
    const bundleDescription = `Bộ đề luyện thi SP2E 3 môn Toán, Văn, Anh cho kiểm thử E2E ${CLEAN_RUN_SUFFIX}.`;

    const adminCtx = await browser.newContext();
    const studentCtx = await browser.newContext();
    const api = await adminApi();
    let createdBundleId: string | undefined;

    try {
      // 1. Admin tạo và công bố bộ đề SP2E 3 môn (AC-1)
      await loginAdmin(adminCtx, "vi");
      const adminPage = await adminCtx.newPage();

      await adminPage.goto(`${ADMIN_URL}/bundles`);
      const menu = adminPage.getByRole("navigation", { name: "Menu chính" });
      await expect(menu.getByRole("link", { name: "Bộ đề" })).toBeVisible();
      await expect(adminPage.getByRole("heading", { level: 1, name: "Bộ đề" })).toBeVisible();

      await adminPage.getByRole("link", { name: "Tạo bộ đề" }).first().click();
      await expect(adminPage).toHaveURL(`${ADMIN_URL}/bundles/new`);

      await adminPage.getByLabel("Mã bộ").fill(bundleCode);
      await adminPage.getByLabel("Tên bộ").fill(bundleName);
      await adminPage.getByLabel("Mô tả ngắn trên thẻ").fill(bundleSummary);
      await adminPage.getByLabel("Mô tả").fill(bundleDescription);

      await adminPage.getByRole("combobox", { name: "Kỳ thi" }).click();
      await adminPage.getByRole("option", { name: /\(SP2E\)/ }).click();

      const candidatesTable = adminPage.getByRole("table", { name: "Danh sách bài thi đang bán cùng kỳ thi" });
      await expect(candidatesTable).toBeVisible();

      for (const examCode of expectedExamCodes) {
        const candidateRow = candidatesTable.locator("tbody tr").filter({ hasText: examCode });
        await expect(candidateRow).toHaveCount(1);
        await candidateRow.getByRole("checkbox").check();
      }
      await expect(
        adminPage.getByRole("list", { name: "Thứ tự bài thi trong bộ" }).getByRole("listitem"),
      ).toHaveCount(3);

      await adminPage.getByLabel("Giá bộ").fill("99000");
      await adminPage.getByLabel("Số lượt mỗi bài").fill("3");
      await adminPage.getByRole("radio", { name: /^Điểm và đáp án/ }).check();
      await adminPage.getByRole("checkbox", { name: /Ôn luyện bổ sung/ }).check();
      await adminPage.getByRole("checkbox", { name: /Giải thích bằng AI/ }).check();

      const [createRes] = await Promise.all([
        adminPage.waitForResponse(
          (res) => res.url().endsWith("/api/v1/admin/bundles") && res.request().method() === "POST",
        ),
        adminPage.getByRole("button", { name: "Lưu nháp" }).click(),
      ]);
      expect(createRes.status(), await createRes.text()).toBe(201);
      const createdBody = (await createRes.json()) as { id: string };
      createdBundleId = createdBody.id;

      await expect(adminPage).toHaveURL(new RegExp(`/bundles/${createdBundleId}$`));

      await adminPage.getByRole("button", { name: "Công bố", exact: true }).click();
      const publishDialog = adminPage.getByRole("dialog");
      await expect(publishDialog).toBeVisible();

      const [publishRes] = await Promise.all([
        adminPage.waitForResponse(
          (res) => res.url().endsWith(`/bundles/${createdBundleId}/publish`) && res.request().method() === "POST",
        ),
        publishDialog.getByRole("button", { name: "Công bố", exact: true }).click(),
      ]);
      expect(publishRes.status(), await publishRes.text()).toBe(200);
      await expect(publishDialog).toBeHidden();
      await expect(adminPage.getByText("Đang bán", { exact: true }).first()).toBeVisible();

      // 2. Học viên lọc danh mục "Bộ đề" & xem chi tiết bộ đề (AC-10, AC-11)
      const { cookies: studentCookies } = await createVerifiedAccount("fb.e2e");
      await studentCtx.addCookies(studentCookies);
      const studentPage = await studentCtx.newPage();

      await studentPage.goto(`${WEB_URL}/#exams`);
      await studentPage.getByRole("combobox", { name: "Hình thức" }).click();
      await studentPage.getByRole("option", { name: "Bộ đề", exact: true }).click();
      await expect(studentPage).toHaveURL(/format=BUNDLE/);
      await expect(studentPage.locator('[aria-busy="true"]')).toHaveCount(0);

      const catalogCards = studentPage.getByRole("article");
      await expect(catalogCards.first()).toBeVisible();
      const visibleCount = await catalogCards.count();
      for (let idx = 0; idx < visibleCount; idx += 1) {
        const card = catalogCards.nth(idx);
        await expect(card.getByText("Bộ đề", { exact: true }).first()).toBeVisible();
        await expect(card.getByText(/Bài thi FULL|Bài thi theo phần|Bài thi theo môn/)).toHaveCount(0);
      }

      // Dự phòng nếu có >12 bộ hoặc cache 60 giây của trang chủ vẫn còn từ lượt chạy trước
      let ourCard = catalogCards.filter({ has: studentPage.getByRole("heading", { name: bundleName }) });
      if ((await ourCard.count()) === 0) {
        await studentPage.getByRole("searchbox", { name: "Tìm theo tên hoặc mã" }).fill(bundleCode);
        await expect(studentPage).toHaveURL(new RegExp(`q=${encodeURIComponent(bundleCode)}`));
        await expect(studentPage.locator('[aria-busy="true"]')).toHaveCount(0);
        ourCard = studentPage.getByRole("article").filter({
          has: studentPage.getByRole("heading", { name: bundleName }),
        });
      }
      await expect(ourCard).toBeVisible();
      await expect(ourCard.getByText("Bộ đề", { exact: true }).first()).toBeVisible();
      await expect(ourCard.getByRole("link", { name: "Mua bộ đề" })).toBeVisible();

      await ourCard.getByRole("link", { name: "Xem chi tiết" }).click();
      await expect(studentPage).toHaveURL(`${WEB_URL}/exams/${encodeURIComponent(bundleCode)}`);
      await expect(studentPage.getByRole("heading", { level: 1, name: bundleName })).toBeVisible();

      const itemsSection = studentPage.locator('section[aria-labelledby="b-items"]');
      await expect(itemsSection).toBeVisible();
      for (const examCode of expectedExamCodes) {
        const exam = examByCode(examCode);
        const itemRow = itemsSection.locator("li").filter({ hasText: exam.name });
        await expect(itemRow).toBeVisible();
        await expect(itemRow.getByRole("link", { name: exam.name })).toHaveAttribute(
          "href",
          `/exams/${encodeURIComponent(exam.code)}`,
        );
        const subjectVi = exam.subjectCode ? SUBJECT_NAME_VI[exam.subjectCode] : undefined;
        if (subjectVi) {
          await expect(itemRow.getByText(subjectVi, { exact: true })).toBeVisible();
        }
        await expect(itemRow.getByText(/\d+ câu/)).toBeVisible();
        await expect(itemRow.getByText(/\d+ phút/)).toBeVisible();
        await expect(itemRow.getByText(/\d+(?:,\d+)? điểm/)).toBeVisible();
        await expect(itemRow.getByText("Quyền lợi bài này:")).toBeVisible();
        await expect(itemRow.getByText("3 lượt", { exact: true })).toBeVisible();
        await expect(itemRow.getByText("Điểm và đáp án", { exact: true })).toBeVisible();
      }

      const buyCta = studentPage.getByRole("complementary").getByRole("link", { name: "Mua bộ đề" });
      await expect(buyCta).toBeVisible();
      await expect(buyCta).toHaveAttribute("href", `/checkout/${encodeURIComponent(bundleCode)}?v=1&p=99000`);

      // 3. Học viên xác nhận mua và thanh toán qua Mock VNPAY (AC-1, AC-13)
      await buyCta.click();
      await expect(studentPage).toHaveURL(`${WEB_URL}/checkout/${encodeURIComponent(bundleCode)}?v=1&p=99000`);
      await expect(studentPage.getByRole("heading", { level: 1, name: "Xác nhận mua" })).toBeVisible();

      const breakdownRegion = studentPage.getByRole("region", { name: "Các bài trong bộ và quyền nhận được" });
      await expect(breakdownRegion).toBeVisible();
      for (const examName of expectedExamNames) {
        await expect(breakdownRegion.getByText(examName, { exact: true })).toBeVisible();
      }
      await expect(studentPage.getByText("99.000 đ").first()).toBeVisible();

      await studentPage.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
      await studentPage.waitForURL(/\/mock-vnpay/);
      await studentPage.locator('a[data-mock-action="success"]').click();
      await studentPage.waitForURL(/\/checkout\/result/);
      await expect(studentPage.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();

      let orderCode = new URL(studentPage.url()).searchParams.get("order") ?? "";
      if (!orderCode) {
        const orderHref = await studentPage.getByRole("link", { name: "Xem chi tiết đơn" }).getAttribute("href");
        orderCode = (orderHref ?? "").split("/").pop() ?? "";
      }
      expect(orderCode).toMatch(/^DH/i);

      await studentPage.getByRole("link", { name: "Vào Bài thi của tôi" }).click();
      await expect(studentPage).toHaveURL(`${WEB_URL}/home`);

      const bundleGroup = studentPage.locator('section[aria-labelledby^="bundle-group-"]').filter({
        has: studentPage.getByRole("heading", { level: 3, name: bundleName }),
      });
      await expect(bundleGroup).toBeVisible();
      await expect(bundleGroup.locator("article")).toHaveCount(3);
      for (const examName of expectedExamNames) {
        const card = bundleGroup.locator("article").filter({
          has: studentPage.getByRole("heading", { level: 3, name: examName }),
        });
        await expect(card).toBeVisible();
        await expect(card.getByText("Có thể bắt đầu", { exact: true })).toBeVisible();
        await expect(card.locator("strong", { hasText: /^3$/ })).toBeVisible();
        await expect(card.getByRole("link", { name: "Bắt đầu" })).toHaveAttribute(
          "href",
          /^\/prepare\/[0-9a-f-]{36}$/i,
        );
      }

      // 4. Admin /orders và /orders/[code] hiện sản phẩm bộ đề và 3 bài thi trong bộ (AC-9)
      await adminPage.goto(`${ADMIN_URL}/orders?q=${encodeURIComponent(orderCode)}`);
      const orderRow = adminPage.locator("tbody tr", { hasText: orderCode });
      await expect(orderRow).toBeVisible();
      await expect(orderRow.getByText("Bộ đề", { exact: true })).toBeVisible();
      await expect(orderRow.getByText(bundleName, { exact: true })).toBeVisible();
      await expect(orderRow.getByText(/3 bài thi/)).toBeVisible();
      for (const examName of expectedExamNames) {
        await expect(orderRow.getByText(examName)).toBeVisible();
      }

      await orderRow.getByRole("link", { name: orderCode, exact: true }).click();
      await expect(adminPage).toHaveURL(new RegExp(`/orders/${encodeURIComponent(orderCode)}`));
      await expect(adminPage.getByText("Bộ đề", { exact: true }).first()).toBeVisible();
      await expect(adminPage.getByRole("link", { name: "Mở bộ đề" })).toHaveAttribute(
        "href",
        `/bundles/${createdBundleId}`,
      );

      const bundleItemsTable = adminPage.getByRole("table", { name: "Bài thi trong bộ đề" });
      await expect(bundleItemsTable).toBeVisible();
      for (const examName of expectedExamNames) {
        await expect(bundleItemsTable.getByText(examName, { exact: true })).toBeVisible();
      }

      // 5. Bắt đầu 1 môn trong bộ, 2 môn còn lại giữ nguyên lượt và trạng thái (AC-2)
      const firstExamName = expectedExamNames[0]!;
      const firstCard = bundleGroup.locator("article").filter({
        has: studentPage.getByRole("heading", { level: 3, name: firstExamName }),
      });
      const prepareHref = await firstCard.getByRole("link", { name: "Bắt đầu" }).getAttribute("href");
      const entitlementMatch = prepareHref ? /^\/prepare\/([0-9a-f-]{36})$/i.exec(prepareHref) : null;
      expect(entitlementMatch?.[1], `Invalid prepare href: ${prepareHref}`).toBeTruthy();
      const entitlementId = entitlementMatch![1]!;

      const xsrf = readXsrfCookie(await studentCtx.cookies(WEB_URL));
      expect(xsrf, "Student XSRF cookie must be present").toBeTruthy();

      const startRes = await studentCtx.request.post(`${WEB_URL}/api/v1/me/attempts`, {
        headers: {
          Origin: WEB_URL,
          "X-XSRF-TOKEN": xsrf,
          "Idempotency-Key": randomUUID(),
        },
        data: { entitlementId },
      });
      expect([200, 201, 202]).toContain(startRes.status());
      const startBody = (await startRes.json()) as { attemptId: string; state: string };
      expect(startBody.attemptId).toBeTruthy();

      if (startBody.state === "STARTING") {
        await expect
          .poll(
            async () => {
              const stateRes = await studentCtx.request.get(`${WEB_URL}/api/v1/me/attempts/${startBody.attemptId}`);
              expect(stateRes.status(), await stateRes.text()).toBe(200);
              const stateBody = (await stateRes.json()) as { state: string; abortReason?: string };
              if (stateBody.state === "ABORTED") {
                throw new Error(`Attempt aborted during generation: ${stateBody.abortReason ?? "unknown"}`);
              }
              return stateBody.state;
            },
            {
              message: "wait for started attempt to transition from STARTING to IN_PROGRESS",
              timeout: 45_000,
              intervals: [250, 500, 1000],
            },
          )
          .toBe("IN_PROGRESS");
      }

      await studentPage.reload();
      await expect(bundleGroup).toBeVisible();

      await expect(firstCard.getByText("Đang làm", { exact: true })).toBeVisible();
      await expect(firstCard.getByRole("link", { name: "Tiếp tục" })).toHaveAttribute(
        "href",
        `/attempts/${startBody.attemptId}`,
      );
      await expect(firstCard.locator("strong", { hasText: /^2$/ })).toBeVisible();

      for (const otherName of expectedExamNames.slice(1)) {
        const otherCard = bundleGroup.locator("article").filter({
          has: studentPage.getByRole("heading", { level: 3, name: otherName }),
        });
        await expect(otherCard.getByText("Có thể bắt đầu", { exact: true })).toBeVisible();
        await expect(otherCard.getByRole("link", { name: "Bắt đầu" })).toHaveAttribute(
          "href",
          /^\/prepare\/[0-9a-f-]{36}$/i,
        );
        await expect(otherCard.locator("strong", { hasText: /^3$/ })).toBeVisible();
      }
    } finally {
      await discontinueBundleIfPublished(api, createdBundleId);
      await api.api.dispose();
      await adminCtx.close();
      await studentCtx.close();
    }
  });

  test("AC-17: bài ôn trong bộ hiện nhãn Bài ôn đúng ở màn chọn bài và trang chi tiết bộ", async ({
    browser,
  }) => {
    const review = reviewExam();
    const stdExam = examByCode("DS-HSA-SUBJECT-TOAN");
    const reviewBundleCode = `BO-REV-${CLEAN_RUN_SUFFIX}`;
    const reviewBundleName = `Bộ đề ôn HSA E2E ${CLEAN_RUN_SUFFIX}`;

    const adminCtx = await browser.newContext();
    const studentCtx = await browser.newContext();
    const api = await adminApi();
    let reviewBundleId: string | undefined;

    try {
      await loginAdmin(adminCtx, "vi");
      const adminPage = await adminCtx.newPage();

      await adminPage.goto(`${ADMIN_URL}/bundles/new`);
      await adminPage.getByRole("combobox", { name: "Kỳ thi" }).click();
      await adminPage.getByRole("option", { name: /\(HSA\)/ }).click();

      const candidatesTable = adminPage.getByRole("table", { name: "Danh sách bài thi đang bán cùng kỳ thi" });
      await expect(candidatesTable).toBeVisible();

      const reviewRow = candidatesTable.locator("tbody tr").filter({ hasText: review.code });
      await expect(reviewRow).toBeVisible();
      await expect(reviewRow.getByText("Bài ôn", { exact: true })).toBeVisible();

      const stdRow = candidatesTable.locator("tbody tr").filter({ hasText: stdExam.code });
      await expect(stdRow).toBeVisible();
      await expect(stdRow.getByText("Bài ôn", { exact: true })).toHaveCount(0);

      await reviewRow.getByRole("checkbox").check();
      await stdRow.getByRole("checkbox").check();

      await adminPage.getByLabel("Mã bộ").fill(reviewBundleCode);
      await adminPage.getByLabel("Tên bộ").fill(reviewBundleName);
      await adminPage.getByLabel("Mô tả ngắn trên thẻ").fill(`Bộ đề HSA có bài ôn ${CLEAN_RUN_SUFFIX}`);
      await adminPage.getByLabel("Mô tả").fill(`Kiểm tra nhãn Bài ôn trong bộ đề ${CLEAN_RUN_SUFFIX}.`);
      await adminPage.getByLabel("Giá bộ").fill("59000");
      await adminPage.getByLabel("Số lượt mỗi bài").fill("2");

      const [createRes] = await Promise.all([
        adminPage.waitForResponse(
          (res) => res.url().endsWith("/api/v1/admin/bundles") && res.request().method() === "POST",
        ),
        adminPage.getByRole("button", { name: "Lưu nháp" }).click(),
      ]);
      expect(createRes.status(), await createRes.text()).toBe(201);
      reviewBundleId = ((await createRes.json()) as { id: string }).id;
      await expect(adminPage).toHaveURL(new RegExp(`/bundles/${reviewBundleId}$`));

      await adminPage.getByRole("button", { name: "Công bố", exact: true }).click();
      const publishDialog = adminPage.getByRole("dialog");
      await expect(publishDialog).toBeVisible();
      const [publishRes] = await Promise.all([
        adminPage.waitForResponse(
          (res) => res.url().endsWith(`/bundles/${reviewBundleId}/publish`) && res.request().method() === "POST",
        ),
        publishDialog.getByRole("button", { name: "Công bố", exact: true }).click(),
      ]);
      expect(publishRes.status(), await publishRes.text()).toBe(200);
      await expect(publishDialog).toBeHidden();
      await expect(adminPage.getByText("Đang bán", { exact: true }).first()).toBeVisible();

      const studentPage = await studentCtx.newPage();
      await studentPage.goto(`${WEB_URL}/exams/${encodeURIComponent(reviewBundleCode)}`);
      await expect(studentPage.getByRole("heading", { level: 1, name: reviewBundleName })).toBeVisible();

      const itemsSection = studentPage.locator('section[aria-labelledby="b-items"]');
      await expect(itemsSection).toBeVisible();

      const reviewItem = itemsSection.locator("li").filter({ hasText: review.name });
      await expect(reviewItem).toBeVisible();
      await expect(reviewItem.getByText("Bài ôn", { exact: true })).toBeVisible();

      const stdItem = itemsSection.locator("li").filter({ hasText: stdExam.name });
      await expect(stdItem).toBeVisible();
      await expect(stdItem.getByText("Bài ôn", { exact: true })).toHaveCount(0);
    } finally {
      await discontinueBundleIfPublished(api, reviewBundleId);
      await api.api.dispose();
      await adminCtx.close();
      await studentCtx.close();
    }
  });
});
