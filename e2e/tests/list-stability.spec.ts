import { expect, type Page, test } from "@playwright/test";

const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");
const ADMIN_URL = (process.env.THI360_ADMIN_URL ?? "http://localhost:3001").replace(/\/+$/, "");
const DEV_PASSWORD = process.env.THI360_DEV_PASSWORD ?? "Thi360@dev";

async function loginAsSysAdmin(page: Page) {
  await page.goto(`${WEB_URL}/login`);
  await page.getByLabel(/Tên đăng nhập hoặc email/).fill("sys.admin");
  await page.getByLabel(/Mật khẩu/).fill(DEV_PASSWORD);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
}

async function left(page: Page, selector: string) {
  return page.locator(selector).first().evaluate((element) => Math.round(element.getBoundingClientRect().left));
}

// Chromium không giao diện mặc định ẩn thanh cuộn (rộng 0 px) nên không bao giờ thấy trang lệch vì thanh cuộn.
// Bỏ cờ đó để thanh cuộn chiếm chỗ như trình duyệt thật trên Windows.
test.use({ launchOptions: { ignoreDefaultArgs: ["--hide-scrollbars"] } });

test.describe("Danh sách không nhảy khi đổi bộ lọc (UNI-2577)", () => {
  test("đổi bộ lọc ở màn Tài khoản: các dòng cũ ở lại trong lúc tải, không hiện khung chờ, cột không lệch", async ({
    page,
  }) => {
    await loginAsSysAdmin(page);
    await page.goto(`${ADMIN_URL}/users`);
    const table = page.getByRole("table");
    // Cơ sở dữ liệu dev dùng chung có nhiều tài khoản do các lượt e2e khác tạo: thu về 5 học viên mẫu trước.
    await page.getByLabel("Tìm kiếm").fill("student0");
    await expect(table.getByRole("cell", { name: "student05", exact: true })).toBeVisible();
    await expect(table.getByRole("row")).toHaveCount(1 + 5);
    await page.waitForLoadState("networkidle");

    const rowsBefore = await table.getByRole("row").count();
    const emailColumnBefore = await left(page, "table thead th:nth-child(4)");
    const tableHeightBefore = await table.evaluate((element) => Math.round(element.getBoundingClientRect().height));

    // Làm chậm đúng lượt tải của bộ lọc mới để quan sát được trạng thái ở giữa.
    let release: () => void = () => {};
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    await page.route("**/api/v1/admin/users?*includeDeleted=true*", async (route) => {
      await held;
      await route.continue();
    });

    await page.getByLabel("Hiện tài khoản đã xóa").check();
    await expect(table).toHaveAttribute("aria-busy", "true");

    // Trong lúc chờ: vẫn đủ các dòng cũ, cùng chiều cao, cùng vị trí cột.
    await expect(table.getByRole("cell", { name: "student05", exact: true })).toBeVisible();
    expect(await table.getByRole("row").count()).toBe(rowsBefore);
    expect(await table.evaluate((element) => Math.round(element.getBoundingClientRect().height))).toBe(
      tableHeightBefore,
    );
    expect(await left(page, "table thead th:nth-child(4)")).toBe(emailColumnBefore);

    release();
    await expect(table).not.toHaveAttribute("aria-busy", "true");
    await expect(table.getByRole("cell", { name: "student05", exact: true })).toBeVisible();
    expect(await left(page, "table thead th:nth-child(4)")).toBe(emailColumnBefore);
  });

  test("thanh cuộn dọc hiện/ẩn và mở hộp thoại không làm trang lệch ngang", async ({ page }) => {
    await page.setViewportSize({ width: 1366, height: 300 });
    await loginAsSysAdmin(page);
    await page.goto(`${ADMIN_URL}/users`);
    const table = page.getByRole("table");
    const search = page.getByLabel("Tìm kiếm");
    const noMatch = "khong-co-tai-khoan-nao-ten-nay";
    const contentHeight = () => page.evaluate(() => document.documentElement.scrollHeight);
    const scrolls = () => page.evaluate(() => document.documentElement.scrollHeight > window.innerHeight);
    const headingLeft = () => left(page, "h1");
    // Giữ tham chiếu tới nút ở đầu trang: khi hộp thoại mở, tìm theo vai trò sẽ ra nút trong hộp thoại.
    const createButton = await page.getByRole("button", { name: "Tạo tài khoản" }).first().elementHandle();
    expect(createButton).not.toBeNull();
    const createButtonRight = () =>
      createButton!.evaluate((element) => Math.round(element.getBoundingClientRect().right));

    // Đo chiều cao trang khi có dữ liệu và khi rỗng (ở màn hình thấp, chiều cao cuộn = chiều cao nội dung),
    // rồi chọn chiều cao màn hình nằm giữa hai mức: có dữ liệu thì cuộn, rỗng thì không.
    await expect(table.getByRole("row").nth(1)).toBeVisible();
    await page.waitForLoadState("networkidle");
    const fullHeight = await contentHeight();
    await search.fill(noMatch);
    await expect(page.getByText("Không tìm thấy tài khoản")).toBeVisible();
    const emptyHeight = await contentHeight();
    expect(fullHeight).toBeGreaterThan(emptyHeight + 80);
    await page.setViewportSize({ width: 1366, height: emptyHeight + 40 });

    await search.fill("");
    await expect(page.getByText("Không tìm thấy tài khoản")).toBeHidden();
    await expect.poll(scrolls).toBe(true);
    // Thanh cuộn thật sự chiếm chỗ trong lượt chạy này, nếu không các so sánh bên dưới vô nghĩa.
    const scrollbarWidth = await page.evaluate(() => window.innerWidth - document.documentElement.clientWidth);
    expect(scrollbarWidth).toBeGreaterThan(0);
    const headingBefore = await headingLeft();
    const buttonBefore = await createButtonRight();

    // Lọc về 0 dòng: trang hết cuộn nhưng nội dung không dịch sang phải.
    await search.fill(noMatch);
    await expect(page.getByText("Không tìm thấy tài khoản")).toBeVisible();
    expect(await scrolls()).toBe(false);
    expect(await headingLeft()).toBe(headingBefore);
    expect(await createButtonRight()).toBe(buttonBefore);

    // Có dữ liệu trở lại rồi mở hộp thoại (khóa cuộn trang): nền phía sau cũng không dịch.
    await search.fill("");
    await expect(page.getByText("Không tìm thấy tài khoản")).toBeHidden();
    await expect.poll(scrolls).toBe(true);
    expect(await createButtonRight()).toBe(buttonBefore);
    await createButton!.click();
    await expect(page.getByRole("dialog")).toBeVisible();
    expect(await headingLeft()).toBe(headingBefore);
    expect(await createButtonRight()).toBe(buttonBefore);
  });
});
