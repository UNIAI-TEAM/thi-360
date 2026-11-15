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

  test(`${PHONE.width}x${PHONE.height} /users: danh sách thẻ thay cho bảng, chọn thẻ hiện thanh thao tác hàng loạt, ô Sắp xếp theo đổi thứ tự (UNI-2587)`, async ({
    page,
  }) => {
    await login(page);
    await openScreen(page, "/users");

    // Khung hẹp: chỉ dựng danh sách, không có bảng song song trong DOM.
    const list = page.getByRole("list", { name: "Danh sách tài khoản" });
    await expect(list).toBeVisible();
    await expect(page.getByRole("table", { name: "Danh sách tài khoản" })).toHaveCount(0);
    const cards = list.getByRole("listitem");
    await expect(cards.first().getByRole("link")).toBeVisible();
    expect(await cards.count(), "cần ít nhất hai tài khoản mẫu để kiểm sắp xếp").toBeGreaterThan(1);

    // Mỗi thẻ: tiêu đề là liên kết tới chi tiết, chip trạng thái luôn có, menu ⋯ nằm trọn trong cửa sổ.
    const first = cards.first();
    await expect(first.getByRole("link")).toHaveAttribute("href", /^\/users\/[^/]+$/);
    await expect(first.locator("[data-tone]").first()).toBeVisible();
    const menuButton = first.getByRole("button", { name: /^Thao tác với / });
    const menuBox = await menuButton.boundingBox();
    expect(menuBox).not.toBeNull();
    expect(menuBox!.x + menuBox!.width, "nút ⋯ của thẻ tràn mép phải").toBeLessThanOrEqual(PHONE.width);
    await expectNoHorizontalOverflow(page, "/users (danh sách thẻ)");

    // Chọn một thẻ: thanh thao tác hàng loạt hiện ngay trên danh sách, không để lại khoảng trống lớn.
    const bar = page.getByRole("group", { name: "Thao tác với các tài khoản đã chọn" });
    await expect(bar).toBeHidden();
    await expect(page.getByText("Chọn tất cả tài khoản", { exact: true })).toBeVisible();
    await first.getByRole("checkbox").click();
    await expect(bar).toBeVisible();
    await expect(bar).toContainText("Đã chọn 1 tài khoản");
    await expect(first).toHaveAttribute("data-state", "selected");
    const sort = page.getByRole("combobox", { name: "Sắp xếp theo" });
    const barBox = await bar.boundingBox();
    const sortBox = await sort.boundingBox();
    expect(barBox).not.toBeNull();
    expect(sortBox).not.toBeNull();
    expect(barBox!.x + barBox!.width, "thanh thao tác hàng loạt tràn mép phải").toBeLessThanOrEqual(PHONE.width);
    expect(sortBox!.y - (barBox!.y + barBox!.height), "khoảng trống giữa thanh thao tác và danh sách").toBeLessThan(48);
    await bar.getByRole("button", { name: "Bỏ chọn" }).click();
    await expect(bar).toBeHidden();
    await expect(page.getByRole("checkbox", { name: "Chọn tất cả tài khoản" })).toBeFocused();

    // Ô Sắp xếp theo thay cho tiêu đề cột: sắp xếp ở máy chủ, đổi chiều thì đổi thứ tự thẻ.
    async function sortBy(option: string, sortParam: string) {
      const response = page.waitForResponse((r) => decodeURIComponent(r.url()).includes(`sort=${sortParam}`));
      await sort.click();
      await page.getByRole("option", { name: option }).click();
      await response;
      await expect(sort).toContainText(option);
      await expect(list).not.toHaveAttribute("aria-busy", "true");
      return list.getByRole("listitem").getByRole("link").allTextContents();
    }
    const ascending = await sortBy("Tên đăng nhập · tăng dần", "username,asc");
    const descending = await sortBy("Tên đăng nhập · giảm dần", "username,desc");
    expect(ascending.length).toBeGreaterThan(1);
    expect(descending[0], "đổi chiều sắp xếp phải đổi tài khoản đứng đầu").not.toBe(ascending[0]);
  });

  test("ngăn menu: mở từ trái, điều hướng được, đóng bằng mục menu / Esc / nền mờ, focus về nút menu", async ({
    page,
  }) => {
    await login(page);
    await openScreen(page, "/");
    // Khi ngăn (hộp thoại modal) mở, Radix đặt aria-hidden lên phần còn lại của trang nên truy vấn theo role không
    // còn thấy nút menu; kiểm tên truy cập một lần rồi giữ nút bằng bộ chọn CSS để đọc aria-expanded khi ngăn mở.
    await expect(page.getByRole("banner").getByRole("button", { name: "Mở menu điều hướng" })).toBeVisible();
    const menuButton = page.locator('header button[aria-haspopup="dialog"][aria-label="Mở menu điều hướng"]');
    const drawer = page.getByRole("dialog", { name: "Menu điều hướng" });
    await expect(menuButton).toHaveAttribute("aria-expanded", "false");

    // Mở bằng bàn phím: focus vào trong ngăn.
    await menuButton.focus();
    await page.keyboard.press("Enter");
    await expect(drawer).toBeVisible();
    await expect(menuButton).toHaveAttribute("aria-expanded", "true");
    await expect(menuButton).toHaveAttribute("aria-controls", (await drawer.getAttribute("id")) ?? "");
    await expect(drawer.locator(":focus")).toHaveCount(1);
    // Ngăn trượt vào trong 200ms: chờ hiệu ứng xong rồi mới đo vị trí.
    await expect.poll(async () => Math.round((await drawer.boundingBox())?.x ?? -1)).toBe(0);
    const box = await drawer.boundingBox();
    expect(box).not.toBeNull();
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
