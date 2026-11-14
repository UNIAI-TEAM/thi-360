import { type BrowserContext, expect, type Page, test } from "@playwright/test";

const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");
const ADMIN_URL = (process.env.THI360_ADMIN_URL ?? "http://localhost:3001").replace(/\/+$/, "");
const API_URL = (process.env.THI360_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");
const DEV_PASSWORD = process.env.THI360_DEV_PASSWORD ?? "Thi360@dev";

async function loginOnWeb(page: Page, identifier: string, password: string) {
  await page.goto(`${WEB_URL}/dang-nhap`);
  await page.getByLabel(/Tên đăng nhập hoặc email|Username or email/).fill(identifier);
  await page.getByLabel(/Mật khẩu|Password/).fill(password);
  await page.getByRole("button", { name: /^(Đăng nhập|Sign in)$/ }).click();
}

function cookieValue(cookies: Array<{ name: string; value: string }>, name: string): string | undefined {
  return cookies.find((cookie) => cookie.name === name)?.value;
}

type AppearanceSnapshot = {
  palette: unknown;
  mode: "light" | "dark";
  version: number;
};

async function restoreAppearance(
  adminContext: BrowserContext,
  originalAppearance: AppearanceSnapshot | null,
  currentVersion: number | null,
  xsrfToken: string,
) {
  if (!originalAppearance || !xsrfToken) {
    return;
  }
  let restoreVersion = currentVersion;
  if (restoreVersion === null) {
    const latestRes = await adminContext.request.get(`${API_URL}/api/v1/appearance`, {
      headers: { "cache-control": "no-cache" },
    });
    if (latestRes.ok()) {
      const latest = (await latestRes.json()) as { version: number };
      if (latest.version !== originalAppearance.version) {
        restoreVersion = latest.version;
      }
    }
  }
  if (restoreVersion === null) {
    return;
  }
  await adminContext.request.put(`${ADMIN_URL}/api/v1/admin/appearance`, {
    headers: {
      "content-type": "application/json",
      "x-xsrf-token": xsrfToken,
      origin: ADMIN_URL,
    },
    data: {
      palette: originalAppearance.palette,
      mode: originalAppearance.mode,
      version: restoreVersion,
    },
  });
}

test.describe("Đa ngôn ngữ (vi/en) và chế độ sáng/tối theo từng người", () => {
  test("đổi ngôn ngữ ở /dang-nhap -> đăng nhập ops.admin giữ nguyên tiếng Anh sang web-admin -> đổi lại tiếng Việt -> cookie sai rơi về tiếng Việt", async ({
    browser,
  }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    try {
      await page.goto(`${WEB_URL}/dang-nhap`);
      await expect(page.locator("html")).toHaveAttribute("lang", "vi");
      await expect(page.getByRole("heading", { name: "Đăng nhập" })).toBeVisible();

      // Điền trước tên đăng nhập rồi đổi sang English bằng bàn phím -> giữ nguyên input và focus trên nút
      const identifierInput = page.getByLabel(/Tên đăng nhập hoặc email|Username or email/);
      await identifierInput.fill("ops.admin");
      const localeButton = page.getByRole("button", { name: "Chọn ngôn ngữ" });
      await localeButton.focus();
      await page.keyboard.press("Enter");
      const viItem = page.getByRole("menuitemradio", { name: "Tiếng Việt" });
      const englishItem = page.getByRole("menuitemradio", { name: "English" });
      await expect(viItem).toHaveAttribute("aria-checked", "true");
      await expect(englishItem).toHaveAttribute("aria-checked", "false");
      await expect(viItem).toBeFocused();
      await page.keyboard.press("ArrowDown");
      await expect(englishItem).toBeFocused();
      await page.keyboard.press("Enter");
      await expect(page.locator("html")).toHaveAttribute("lang", "en");
      await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
      await expect(page.getByLabel(/Username or email/)).toHaveValue("ops.admin");
      await expect(page.getByRole("button", { name: "Change language" })).toBeFocused();
      await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();

      // Đăng nhập ops.admin -> điều hướng sang web-admin vẫn giữ tiếng Anh qua cookie dùng chung
      await page.getByLabel(/Password/).fill(DEV_PASSWORD);
      await page.getByRole("button", { name: "Sign in" }).click();

      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
      await expect(page.locator("html")).toHaveAttribute("lang", "en");
      await expect(page.getByRole("heading", { name: /Hello, / })).toBeVisible();
      await expect(page.getByRole("link", { name: "Dashboard" })).toBeVisible();

      // Đổi lại Tiếng Việt từ thanh trên của web-admin
      await page.getByRole("button", { name: "Change language" }).click();
      const adminEnItem = page.getByRole("menuitemradio", { name: "English" });
      const adminViItem = page.getByRole("menuitemradio", { name: "Tiếng Việt" });
      await expect(adminEnItem).toHaveAttribute("aria-checked", "true");
      await expect(adminViItem).toHaveAttribute("aria-checked", "false");
      await expect(adminEnItem).toBeFocused();
      await adminViItem.click();
      await expect(page.locator("html")).toHaveAttribute("lang", "vi");
      await expect(page.getByRole("heading", { name: /Xin chào, / })).toBeVisible();
      await expect(page.getByRole("link", { name: "Bảng điều khiển" })).toBeVisible();
      // Cookie sai giá trị -> rơi về tiếng Việt (dùng trình duyệt mới chưa đăng nhập để không bị chuyển hướng khỏi /dang-nhap)
      const invalidLocaleContext = await browser.newContext();
      try {
        await invalidLocaleContext.addCookies([
          { name: "thi360_locale", value: "fr", url: WEB_URL },
        ]);
        const guestPage = await invalidLocaleContext.newPage();
        await guestPage.goto(`${WEB_URL}/dang-nhap`);
        await expect(guestPage.locator("html")).toHaveAttribute("lang", "vi");
        await expect(guestPage.getByRole("heading", { name: "Đăng nhập" })).toBeVisible();

        // HTML máy chủ đã có sẵn lang="en" khi có cookie thi360_locale=en
        const rawHtmlRes = await guestPage.request.get(`${WEB_URL}/dang-nhap`, {
          headers: { cookie: "thi360_locale=en" },
        });
        expect(rawHtmlRes.ok()).toBe(true);
        const rawHtml = await rawHtmlRes.text();
        expect(rawHtml).toContain('<html lang="en"');
        expect(rawHtml).toContain("Sign in");
      } finally {
        await invalidLocaleContext.close();
      }
    } finally {
      await context.close();
    }
  });

  test("bật chế độ tối ở thanh trên -> đổi data-theme ngay, giữ sau reload và sang web kia, không ảnh hưởng trình duyệt khác, HTML máy chủ có sẵn data-theme", async ({
    browser,
  }) => {
    const contextA = await browser.newContext();
    const contextB = await browser.newContext();

    try {
      const pageA = await contextA.newPage();
      const pageB = await contextB.newPage();

      await pageA.goto(`${WEB_URL}/`);
      await pageB.goto(`${WEB_URL}/`);

      const initialTheme = await pageA.locator("html").getAttribute("data-theme");
      const toggledTheme = initialTheme === "dark" ? "light" : "dark";
      const toggleLabel =
        initialTheme === "dark" ? "Chuyển sang chế độ sáng" : "Chuyển sang chế độ tối";

      await pageA.getByRole("button", { name: toggleLabel }).click();
      await expect(pageA.locator("html")).toHaveAttribute("data-theme", toggledTheme);

      // Giữ sau khi tải lại
      await pageA.reload();
      await expect(pageA.locator("html")).toHaveAttribute("data-theme", toggledTheme);

      // HTML máy chủ đã có sẵn data-theme đúng (không nháy)
      const cookies = await contextA.cookies(WEB_URL);
      expect(cookieValue(cookies, "thi360_theme")).toBe(toggledTheme);
      const ssrRes = await pageA.request.get(`${WEB_URL}/`, {
        headers: { cookie: `thi360_theme=${toggledTheme}` },
      });
      expect(ssrRes.ok()).toBe(true);
      expect(await ssrRes.text()).toContain(`data-theme="${toggledTheme}"`);

      // Context B không bị ảnh hưởng
      await pageB.reload();
      await expect(pageB.locator("html")).toHaveAttribute("data-theme", initialTheme ?? "light");

      // Đăng nhập ops.admin ở context A -> sang web-admin vẫn giữ chế độ đã chọn
      await loginOnWeb(pageA, "ops.admin", DEV_PASSWORD);
      await expect(pageA).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
      await expect(pageA.locator("html")).toHaveAttribute("data-theme", toggledTheme);
    } finally {
      await contextA.close();
      await contextB.close();
    }
  });

  test("admin đổi chế độ mặc định qua API -> người chưa chọn theo chế độ mới, người đã chọn giữ lựa chọn của mình", async ({
    browser,
  }) => {
    test.setTimeout(120_000);
    const adminContext = await browser.newContext();
    const chosenContext = await browser.newContext();
    const freshContext = await browser.newContext();
    let originalAppearance: AppearanceSnapshot | null = null;
    let currentVersion: number | null = null;
    let xsrfToken = "";
    const adminPage = await adminContext.newPage();

    try {
      // Lấy cấu hình giao diện hiện tại
      const initialRes = await adminContext.request.get(`${API_URL}/api/v1/appearance`);
      expect(initialRes.ok()).toBe(true);
      originalAppearance = (await initialRes.json()) as AppearanceSnapshot;
      const targetDefaultMode = originalAppearance.mode === "light" ? "dark" : "light";
      const chosenMode = originalAppearance.mode;

      // Người dùng đã tự chọn giữ nguyên mode ban đầu bằng cookie
      const chosenPage = await chosenContext.newPage();
      await chosenPage.goto(`${WEB_URL}/dang-nhap`);
      await expect(chosenPage.locator("html")).toHaveAttribute("data-theme", originalAppearance.mode);
      if (chosenMode === "light") {
        await chosenPage.getByRole("button", { name: "Chuyển sang chế độ tối" }).click();
        await chosenPage.getByRole("button", { name: "Chuyển sang chế độ sáng" }).click();
      } else {
        await chosenPage.getByRole("button", { name: "Chuyển sang chế độ sáng" }).click();
        await chosenPage.getByRole("button", { name: "Chuyển sang chế độ tối" }).click();
      }
      await expect(chosenPage.locator("html")).toHaveAttribute("data-theme", chosenMode);

      // Admin sys.admin đăng nhập và đổi chế độ mặc định qua API
      await loginOnWeb(adminPage, "sys.admin", DEV_PASSWORD);
      await expect(adminPage).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      const adminCookies = await adminContext.cookies(ADMIN_URL);
      xsrfToken = cookieValue(adminCookies, "thi360_xsrf") ?? "";
      expect(xsrfToken).not.toBe("");

      const putRes = await adminPage.request.put(`${ADMIN_URL}/api/v1/admin/appearance`, {
        headers: {
          "content-type": "application/json",
          "x-xsrf-token": xsrfToken,
          origin: ADMIN_URL,
        },
        data: {
          palette: originalAppearance.palette,
          mode: targetDefaultMode,
          version: originalAppearance.version,
        },
      });
      expect(putRes.ok()).toBe(true);
      const updated = (await putRes.json()) as { version: number };
      currentVersion = updated.version;

      // Người đã tự chọn giữ lựa chọn của mình ngay lập tức
      await chosenPage.reload();
      await expect(chosenPage.locator("html")).toHaveAttribute("data-theme", chosenMode);

      // Người chưa chọn (không có cookie thi360_theme) theo chế độ mặc định mới;
      // RootLayout dùng next: { revalidate: 60, tags: ["appearance"] }, nên thay đổi qua API
      // lan sang web-student trong cửa sổ revalidate 60 giây.
      const freshPage = await freshContext.newPage();
      await expect
        .poll(
          async () => {
            await freshPage.goto(`${WEB_URL}/`);
            return freshPage.locator("html").getAttribute("data-theme");
          },
          { timeout: 75_000, intervals: [2_000] },
        )
        .toBe(targetDefaultMode);
    } finally {
      try {
        await restoreAppearance(adminContext, originalAppearance, currentVersion, xsrfToken);
      } finally {
        await adminContext.close();
        await chosenContext.close();
        await freshContext.close();
      }
    }
  });

  test("admin đã tự chọn chế độ qua cookie khi Lưu và áp dụng trên /tuy-chinh-giao-dien vẫn giữ data-theme cá nhân và cập nhật bảng màu", async ({
    browser,
  }) => {
    test.setTimeout(120_000);
    const adminContext = await browser.newContext();
    const adminPage = await adminContext.newPage();
    let originalAppearance: AppearanceSnapshot | null = null;
    let currentVersion: number | null = null;
    let xsrfToken = "";

    try {
      const initialRes = await adminContext.request.get(`${API_URL}/api/v1/appearance`);
      expect(initialRes.ok()).toBe(true);
      originalAppearance = (await initialRes.json()) as AppearanceSnapshot;

      await loginOnWeb(adminPage, "sys.admin", DEV_PASSWORD);
      await expect(adminPage).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      const adminCookies = await adminContext.cookies(ADMIN_URL);
      xsrfToken = cookieValue(adminCookies, "thi360_xsrf") ?? "";
      expect(xsrfToken).not.toBe("");

      await adminPage.goto(`${ADMIN_URL}/tuy-chinh-giao-dien`);
      await expect(adminPage.getByRole("heading", { name: "Tùy chỉnh giao diện" })).toBeVisible();

      const chosenMode = originalAppearance.mode === "light" ? "dark" : "light";
      const savedDefaultMode = originalAppearance.mode;
      const toggleName =
        chosenMode === "dark" ? "Chuyển sang chế độ tối" : "Chuyển sang chế độ sáng";
      await adminPage.getByRole("button", { name: toggleName }).click();
      await expect(adminPage.locator("html")).toHaveAttribute("data-theme", chosenMode);

      await adminPage.getByRole("button", { name: /Xanh hải quân & Ngọc lam/ }).click();
      await adminPage
        .getByRole("tab", { name: savedDefaultMode === "light" ? "Sáng" : "Tối" })
        .click();
      await adminPage.getByRole("button", { name: "Lưu và áp dụng" }).click();

      await expect(
        adminPage.getByText("Đã lưu và áp dụng giao diện mới cho toàn hệ thống."),
      ).toBeVisible();
      await expect(adminPage.locator("html")).toHaveAttribute("data-theme", chosenMode);
      await expect
        .poll(() => adminPage.locator("#thi360-theme-vars").textContent())
        .toContain("#0f3c6e");

      const afterSaveCookies = await adminContext.cookies(ADMIN_URL);
      expect(cookieValue(afterSaveCookies, "thi360_theme")).toBe(chosenMode);

      const updatedRes = await adminContext.request.get(`${API_URL}/api/v1/appearance`);
      if (updatedRes.ok()) {
        const updated = (await updatedRes.json()) as { version: number };
        currentVersion = updated.version;
      }
    } finally {
      try {
        await restoreAppearance(adminContext, originalAppearance, currentVersion, xsrfToken);
      } finally {
        await adminContext.close();
      }
    }
  });
});
