import { expect, type Page, test } from "@playwright/test";

const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");
const ADMIN_URL = (process.env.THI360_ADMIN_URL ?? "http://localhost:3001").replace(/\/+$/, "");
const DEV_PASSWORD = process.env.THI360_DEV_PASSWORD ?? "Thi360@dev";

const PHONE = { width: 390, height: 844 } as const;
const DESKTOP = { width: 1280, height: 800 } as const;
const SCREENS = ["/", "/users", "/catalog", "/rbac/roles", "/appearance"] as const;

async function login(page: Page) {
  await page.goto(`${WEB_URL}/login`);
  await page.getByLabel(/Tên đăng nhập hoặc email/).fill("sys.admin");
  await page.getByLabel(/Mật khẩu/).fill(DEV_PASSWORD);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
}

async function openScreen(page: Page, path: string) {
  await page.goto(`${ADMIN_URL}${path}`);
  await expect(page.locator("main h1")).toBeVisible();
  await page.waitForLoadState("networkidle");
}

/**
 * Trang không cuộn ngang: `scrollWidth` của tài liệu không vượt `clientWidth`. Khi hỏng, liệt kê vài phần tử thò ra
 * ngoài mép phải để biết màn nào, khối nào cần sửa.
 */
async function expectNoHorizontalOverflow(page: Page, label: string) {
  const result = await page.evaluate(() => {
    const root = document.documentElement;
    const limit = root.clientWidth;
    const offenders: string[] = [];
    for (const element of Array.from(document.body.querySelectorAll<HTMLElement>("*"))) {
      const rect = element.getBoundingClientRect();
      if (rect.width === 0 || rect.right <= limit + 0.5) continue;
      // Bỏ qua phần tử nằm trong vùng tự cuộn ngang (bảng rộng cuộn trong khung của nó là đúng quy ước).
      let clipped = false;
      for (let parent = element.parentElement; parent && parent !== document.body; parent = parent.parentElement) {
        const overflowX = getComputedStyle(parent).overflowX;
        if (overflowX !== "visible") {
          clipped = true;
          break;
        }
      }
      if (clipped) continue;
      const name = `${element.tagName.toLowerCase()}${element.id ? `#${element.id}` : ""}.${String(element.className).split(/\s+/).slice(0, 4).join(".")}`;
      offenders.push(`${name} right=${Math.round(rect.right)}`);
      if (offenders.length >= 5) break;
    }
    return { scrollWidth: root.scrollWidth, clientWidth: limit, offenders };
  });
  expect(
    result.scrollWidth,
    `${label}: trang cuộn ngang (scrollWidth ${result.scrollWidth} > clientWidth ${result.clientWidth}). ${result.offenders.join("; ")}`,
  ).toBeLessThanOrEqual(result.clientWidth);
}

test.describe("Khung admin trên màn hẹp (UNI-2586)", () => {
  test.use({ viewport: PHONE });

  test(`${PHONE.width}x${PHONE.height}: các màn chính không cuộn ngang, sidebar ẩn, nút menu hiện`, async ({ page }) => {
    await login(page);
    for (const path of SCREENS) {
      await openScreen(page, path);
      await expect(page.getByRole("button", { name: "Mở menu điều hướng" })).toBeVisible();
      await expect(page.getByRole("navigation", { name: "Menu chính" })).toBeHidden();
      // Các nút của thanh trên đều nằm trọn trong cửa sổ.
      for (const name of ["Mở menu điều hướng", /^Chuyển sang chế độ/, "Chọn ngôn ngữ", "Menu tài khoản"]) {
        const box = await page.getByRole("banner").getByRole("button", { name }).boundingBox();
        expect(box, `${path}: thiếu nút ${name}`).not.toBeNull();
        expect(box!.x).toBeGreaterThanOrEqual(0);
        expect(box!.x + box!.width, `${path}: nút ${name} tràn mép phải`).toBeLessThanOrEqual(PHONE.width);
      }
      await expectNoHorizontalOverflow(page, path);
    }
  });

  test("ngăn menu: mở từ trái, điều hướng được, đóng bằng mục menu / Esc / nền mờ, focus về nút menu", async ({
    page,
  }) => {
    await login(page);
    await openScreen(page, "/");
    const menuButton = page.getByRole("banner").getByRole("button", { name: "Mở menu điều hướng" });
    const drawer = page.getByRole("dialog", { name: "Menu điều hướng" });
    await expect(menuButton).toHaveAttribute("aria-expanded", "false");

    // Mở bằng bàn phím: focus vào trong ngăn.
    await menuButton.focus();
    await page.keyboard.press("Enter");
    await expect(drawer).toBeVisible();
    await expect(menuButton).toHaveAttribute("aria-expanded", "true");
    await expect(menuButton).toHaveAttribute("aria-controls", (await drawer.getAttribute("id")) ?? "");
    await expect(drawer.locator(":focus")).toHaveCount(1);
    const box = await drawer.boundingBox();
    expect(box).not.toBeNull();
    expect(Math.round(box!.x)).toBe(0);
    expect(box!.width).toBeLessThan(PHONE.width);

    // Điều hướng qua ngăn: tới trang mới, ngăn đóng, focus về nút menu.
    await drawer.getByRole("navigation", { name: "Menu chính" }).getByRole("link", { name: "Tài khoản" }).click();
    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/users`));
    await expect(drawer).toBeHidden();
    await expect(menuButton).toHaveAttribute("aria-expanded", "false");
    await expect(menuButton).toBeFocused();
    await expect(page.locator("main h1")).toBeVisible();
    await expectNoHorizontalOverflow(page, "/users sau khi đi qua ngăn menu");

    // Esc.
    await menuButton.click();
    await expect(drawer).toBeVisible();
    await expect(drawer.getByRole("link", { name: "Tài khoản" })).toHaveAttribute("aria-current", "page");
    await page.keyboard.press("Escape");
    await expect(drawer).toBeHidden();
    await expect(menuButton).toBeFocused();

    // Bấm nền mờ (phần bên phải ngăn).
    await menuButton.click();
    await expect(drawer).toBeVisible();
    await page.mouse.click(PHONE.width - 20, PHONE.height / 2);
    await expect(drawer).toBeHidden();
    await expect(menuButton).toBeFocused();

    // Nút ×.
    await menuButton.click();
    await drawer.getByRole("button", { name: "Đóng menu điều hướng" }).click();
    await expect(drawer).toBeHidden();
    await expect(menuButton).toBeFocused();

    // Nới cửa sổ tới 1024px khi ngăn đang mở: ngăn tự đóng vì sidebar đã hiện.
    await menuButton.click();
    await expect(drawer).toBeVisible();
    await page.setViewportSize(DESKTOP);
    await expect(drawer).toBeHidden();
    await expect(page.getByRole("navigation", { name: "Menu chính" })).toBeVisible();
  });
});

test.describe("Khung admin trên màn rộng (UNI-2586)", () => {
  test.use({ viewport: DESKTOP });

  test(`${DESKTOP.width}x${DESKTOP.height}: sidebar cố định hiện, không có nút menu, không cuộn ngang`, async ({ page }) => {
    await login(page);
    for (const path of SCREENS) {
      await openScreen(page, path);
      const sidebar = page.getByRole("navigation", { name: "Menu chính" });
      await expect(sidebar).toBeVisible();
      const box = await sidebar.boundingBox();
      expect(Math.round(box!.width)).toBe(248);
      await expect(page.getByRole("button", { name: "Mở menu điều hướng" })).toBeHidden();
      await expectNoHorizontalOverflow(page, path);
    }
  });
});
