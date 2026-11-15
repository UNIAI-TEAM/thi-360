import { expect, type Page, test } from "@playwright/test";

const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");
const ADMIN_URL = (process.env.THI360_ADMIN_URL ?? "http://localhost:3001").replace(/\/+$/, "");
const DEV_PASSWORD = process.env.THI360_DEV_PASSWORD ?? "Thi360@dev";

const VIEWPORTS = [
  { width: 1366, height: 768 },
  { width: 1280, height: 720 },
] as const;

const VARIANTS = [
  { locale: "vi", theme: "light", title: "Tạo tài khoản", mustChange: "Bắt đổi mật khẩu ở lần đăng nhập đầu", classes: "Lớp học" },
  { locale: "en", theme: "dark", title: "Create account", mustChange: "Require password change on first sign-in", classes: "Classes" },
] as const;

async function login(page: Page) {
  await page.goto(`${WEB_URL}/login`);
  await page.getByLabel(/Tên đăng nhập hoặc email/).fill("sys.admin");
  await page.getByLabel(/Mật khẩu/).fill(DEV_PASSWORD);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
}

test.describe("Hộp thoại Tạo tài khoản: hai cột, hiện đủ trong một khung (UNI-2578)", () => {
  for (const viewport of VIEWPORTS) {
    for (const variant of VARIANTS) {
      test(`${viewport.width}x${viewport.height} · ${variant.locale} · ${variant.theme}: không phải cuộn để thấy mọi trường và hai nút`, async ({
        page,
        context,
      }, testInfo) => {
        await page.setViewportSize(viewport);
        await login(page);
        await context.addCookies([
          { name: "thi360_locale", value: variant.locale, url: ADMIN_URL },
          { name: "thi360_theme", value: variant.theme, url: ADMIN_URL },
        ]);
        await page.goto(`${ADMIN_URL}/users`);
        await expect(page.getByRole("table").getByRole("row").nth(1)).toBeVisible();

        await page.getByRole("button", { name: variant.title }).first().click();
        const dialog = page.getByRole("dialog");
        await expect(dialog.getByRole("heading", { name: variant.title })).toBeVisible();
        // Danh sách lớp tải xong (sys.admin có CLASS.MODIFY, dữ liệu mẫu có LOP-A, LOP-B).
        await expect(dialog.getByRole("group", { name: variant.classes })).toBeVisible();

        // Điền mật khẩu để danh sách quy tắc ở trạng thái đầy đủ nhất.
        await dialog.locator('input[autocomplete="new-password"]').fill("Admin@123");

        // Không vùng nào của hộp thoại phải cuộn, trừ chính danh sách lớp (có chiều cao tối đa riêng).
        const scrollable = await dialog.evaluate((root) =>
          [...root.querySelectorAll<HTMLElement>("*")]
            .filter((element) => {
              if (element.getAttribute("role") === "group") return false;
              const overflowY = getComputedStyle(element).overflowY;
              return (overflowY === "auto" || overflowY === "scroll") && element.scrollHeight > element.clientHeight + 1;
            })
            .map((element) => `${element.tagName.toLowerCase()}.${element.className.toString().slice(0, 60)}`),
        );
        expect(scrollable).toEqual([]);

        // Hộp thoại nằm trọn trong màn hình; trường cuối của mỗi cột và hai nút đều thấy được.
        const box = await dialog.boundingBox();
        expect(box).not.toBeNull();
        expect(box!.y).toBeGreaterThanOrEqual(0);
        expect(box!.y + box!.height).toBeLessThanOrEqual(viewport.height);
        await expect(dialog.getByText(variant.mustChange)).toBeInViewport({ ratio: 1 });
        await expect(dialog.getByRole("group", { name: variant.classes })).toBeInViewport({ ratio: 1 });
        const buttons = dialog.getByRole("button", { name: variant.title });
        await expect(buttons.last()).toBeInViewport({ ratio: 1 });

        // Hai cột: ô họ tên nằm bên phải ô tên đăng nhập, cùng hàng trên cùng.
        const fields = await dialog.evaluate((root) => {
          const inputs = [...root.querySelectorAll<HTMLInputElement>("input")].filter((input) => input.type !== "checkbox");
          const rect = (element: Element) => element.getBoundingClientRect();
          const username = inputs[0]!;
          const fullName = inputs.find((input) => input.name === "fullName")!;
          return { usernameLeft: rect(username).left, fullNameLeft: rect(fullName).left, usernameTop: rect(username).top, fullNameTop: rect(fullName).top };
        });
        expect(fields.fullNameLeft).toBeGreaterThan(fields.usernameLeft + 200);
        expect(Math.abs(fields.fullNameTop - fields.usernameTop)).toBeLessThan(4);

        await page.screenshot({
          path: testInfo.outputPath(`create-user-${viewport.width}x${viewport.height}-${variant.locale}-${variant.theme}.png`),
        });
      });
    }
  }

  test("màn hình hẹp (390 px): xếp một cột, cuộn trong hộp thoại, hai nút vẫn thấy được", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 780 });
    await login(page);
    await page.goto(`${ADMIN_URL}/users`);
    await page.getByRole("button", { name: "Tạo tài khoản" }).first().click();
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("heading", { name: "Tạo tài khoản" })).toBeVisible();

    const fields = await dialog.evaluate((root) => {
      const inputs = [...root.querySelectorAll<HTMLInputElement>("input")].filter((input) => input.type !== "checkbox");
      const username = inputs[0]!.getBoundingClientRect();
      const fullName = inputs.find((input) => input.name === "fullName")!.getBoundingClientRect();
      return { sameColumn: Math.abs(username.left - fullName.left) < 4, below: fullName.top > username.top };
    });
    expect(fields).toEqual({ sameColumn: true, below: true });
    await expect(dialog.getByRole("button", { name: "Tạo tài khoản" }).last()).toBeInViewport({ ratio: 1 });
  });
});
