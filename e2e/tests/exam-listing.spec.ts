import { expect, test, type Browser, type BrowserContext, type Page } from "@playwright/test";
import { deflateSync } from "node:zlib";
import { adminApi, ADMIN_URL, loginAdmin, RUN_ID, tinyPng, WEB_URL, type AdminApi } from "./helpers/editor-qa";
import { createPlatformApiContext } from "./helpers/platform";
import {
  applyMatrixCookies,
  expectMatrixClean,
  launchMatrixBrowser,
  matrixCells,
  openMatrixContext,
  type MatrixCell,
} from "./helpers/visual-matrix";
import { SCENARIO_TIMEOUT_MS, SOLVER_TIMEOUT_MS } from "./helpers/wave-2b";

/**
 * FA-1 (UNI-2738): tab "Thông tin bán" của bài thi, nhóm "Thông tin bán" và cảnh báo "Có câu chấm tay" ở tab Kiểm tra, menu.
 * Dữ liệu tạo trong spec mang tiền tố `FA1-`; bài BCA FULL của DS (mã `DS-…`) chỉ đọc, và là nguồn của bản sao ở ca công bố.
 * Chạy `workers: 1` (cấu hình Playwright của repo).
 */

const TAG = `FA1-${RUN_ID}`.toUpperCase();
const TEST_USER_PASSWORD = process.env.THI360_E2E_USER_PASSWORD ?? "Thi360@e2e1!";
const admin = (path: string) => `${ADMIN_URL}/api/v1/admin${path}`;

interface ListingWire {
  revision: number;
  coverAssetId?: string | null;
  description?: string | null;
  listPriceVnd?: number | null;
  attemptsGranted?: number | null;
  summary?: string | null;
  objectives?: string | null;
  audience?: string | null;
  benefitAiExplanation?: boolean;
  benefitStrengthWeakness?: boolean;
  benefitPractice?: boolean;
  benefitNote?: string | null;
  resultView?: string;
  checkIssues?: Array<{ code: string; severity: string }>;
}

async function createDraftExam(api: AdminApi, label: string): Promise<string> {
  const name = `${TAG}-${label}`;
  const created = await api.api.post(admin("/exams"), {
    headers: { ...api.headers, "idempotency-key": crypto.randomUUID() },
    data: { code: name.slice(0, 30), name, kind: "HSA", scope: "FULL" },
  });
  expect(created.status(), await created.text()).toBe(201);
  return ((await created.json()) as { id: string }).id;
}

async function getListing(api: AdminApi, id: string): Promise<ListingWire> {
  const response = await api.api.get(admin(`/exams/${id}/listing`));
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()) as ListingWire;
}

/** Lưu thông tin bán bằng API để dựng dữ liệu của ca; trường không nêu thì giữ giá trị đang có. */
async function putListing(api: AdminApi, id: string, patch: Partial<ListingWire>): Promise<ListingWire> {
  const current = await getListing(api, id);
  const response = await api.api.put(admin(`/exams/${id}/listing`), {
    headers: api.headers,
    data: {
      summary: current.summary ?? undefined,
      objectives: current.objectives ?? undefined,
      audience: current.audience ?? undefined,
      benefitAiExplanation: current.benefitAiExplanation,
      benefitStrengthWeakness: current.benefitStrengthWeakness,
      benefitPractice: current.benefitPractice,
      benefitNote: current.benefitNote ?? undefined,
      resultView: current.resultView,
      description: current.description ?? undefined,
      listPriceVnd: current.listPriceVnd ?? undefined,
      attemptsGranted: current.attemptsGranted ?? undefined,
      coverAssetId: current.coverAssetId ?? undefined,
      ...patch,
      expectedRevision: current.revision,
    },
  });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()) as ListingWire;
}

/** PNG một màu `size` x `size` px, hợp lệ cho ảnh bìa (cạnh ngắn từ 480 px). */
function solidPng(size: number): Buffer {
  const row = Buffer.concat([Buffer.from([0]), Buffer.alloc(size * 3, 0x3b)]);
  const raw = Buffer.concat(Array.from({ length: size }, () => row));
  const table = Array.from({ length: 256 }, (_, n) => {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) !== 0 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    return c >>> 0;
  });
  const crc32 = (buf: Buffer) => {
    let crc = 0xffffffff;
    for (const byte of buf) crc = table[(crc ^ byte) & 0xff]! ^ (crc >>> 8);
    return (crc ^ 0xffffffff) >>> 0;
  };
  const chunk = (type: string, data: Buffer) => {
    const length = Buffer.alloc(4);
    length.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(body));
    return Buffer.concat([length, body, crc]);
  };
  const header = Buffer.alloc(13);
  header.writeUInt32BE(size, 0);
  header.writeUInt32BE(size, 4);
  header[8] = 8;
  header[9] = 2;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

const priceBox = (page: Page) => page.getByRole("textbox", { name: "Giá bán", exact: true });
const attemptsBox = (page: Page) => page.getByRole("spinbutton", { name: "Số lượt mỗi lần mua" });
const descriptionBox = (page: Page) => page.getByRole("textbox", { name: "Mô tả", exact: true });
const saveButton = (page: Page) => page.getByRole("button", { name: "Lưu", exact: true });
const listingForm = (page: Page) => page.getByRole("form", { name: "Thông tin bán" });
const isListingPut = (id: string) => (response: { url(): string; request(): { method(): string } }) =>
  response.url().endsWith(`/exams/${id}/listing`) && response.request().method() === "PUT";

test.describe("FA-1: tab Thông tin bán", () => {
  test("thứ tự tab: Thông tin bán nằm giữa Cấu trúc và Kiểm tra, mở được bằng URL /exams/[id]/listing", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const api = await adminApi();
    const id = await createDraftExam(api, "TAB");

    await page.goto(`${ADMIN_URL}/exams/${id}`);
    const tabs = page.getByRole("navigation", { name: "Các phần của bài thi" });
    await expect(tabs.getByRole("link")).toHaveText([
      "Thông tin chung",
      "Cấu trúc và ma trận",
      "Thông tin bán",
      "Kiểm tra và xem trước",
      "Phiên bản và công bố",
    ]);

    await tabs.getByRole("link", { name: "Thông tin bán" }).click();
    await expect(page).toHaveURL(`${ADMIN_URL}/exams/${id}/listing`);
    await expect(tabs.getByRole("link", { name: "Thông tin bán" })).toHaveAttribute("aria-current", "page");
    await expect(listingForm(page)).toBeVisible();

    // Mở thẳng bằng URL (tải lại trang) cũng ra đúng tab, và tab cũ vẫn mở đúng.
    await page.reload();
    await expect(listingForm(page)).toBeVisible();
    await tabs.getByRole("link", { name: "Cấu trúc và ma trận" }).click();
    await expect(page).toHaveURL(`${ADMIN_URL}/exams/${id}/structure`);
    await expect(tabs.getByRole("link", { name: "Cấu trúc và ma trận" })).toHaveAttribute("aria-current", "page");
  });

  test("lưu thông tin bán: gửi expectedRevision đang có và nhận revision mới", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const api = await adminApi();
    const id = await createDraftExam(api, "SAVE");
    const before = await getListing(api, id);

    await page.goto(`${ADMIN_URL}/exams/${id}/listing`);
    await expect(listingForm(page)).toBeVisible();
    await descriptionBox(page).fill(`Mô tả của bài ${TAG}`);
    await priceBox(page).fill("249000");
    await attemptsBox(page).fill("3");
    await page.getByRole("checkbox", { name: /Ôn luyện bổ sung/ }).check();
    await page.getByRole("radio", { name: /Điểm, đáp án và lời giải/ }).check();

    const [response] = await Promise.all([page.waitForResponse(isListingPut(id)), saveButton(page).click()]);
    expect(response.status(), await response.text()).toBe(200);
    expect((response.request().postDataJSON() as { expectedRevision: number }).expectedRevision).toBe(before.revision);
    const saved = (await response.json()) as ListingWire;
    expect(saved.revision).toBeGreaterThan(before.revision);
    expect(saved).toMatchObject({ listPriceVnd: 249000, attemptsGranted: 3, benefitPractice: true, resultView: "FULL_SOLUTION" });
    await expect(page.locator("[data-sonner-toast]").getByText("Đã lưu thông tin bán.")).toBeVisible();

    // Tải lại: giá trị đã lưu hiện lại; lưu tiếp dùng revision mới.
    await page.reload();
    await expect(priceBox(page)).toHaveValue("249000");
    await expect(attemptsBox(page)).toHaveValue("3");
    await expect(descriptionBox(page)).toHaveValue(`Mô tả của bài ${TAG}`);
    await attemptsBox(page).fill("4");
    const [second] = await Promise.all([page.waitForResponse(isListingPut(id)), saveButton(page).click()]);
    expect((second.request().postDataJSON() as { expectedRevision: number }).expectedRevision).toBe(saved.revision);
    expect(second.status(), await second.text()).toBe(200);
  });

  test("412 khi hai người cùng sửa: nạp lại bản mới và giữ thông báo trên màn", async ({ browser, context, page }) => {
    await loginAdmin(context, "vi");
    const api = await adminApi();
    const id = await createDraftExam(api, "412");
    await putListing(api, id, { listPriceVnd: 100000, attemptsGranted: 2, description: `Mô tả ${TAG}` });

    const otherContext = await browser.newContext();
    try {
      await loginAdmin(otherContext, "vi");
      const other = await otherContext.newPage();
      await page.goto(`${ADMIN_URL}/exams/${id}/listing`);
      await other.goto(`${ADMIN_URL}/exams/${id}/listing`);
      await expect(priceBox(page)).toBeVisible();
      await expect(priceBox(other)).toBeVisible();

      // Phiên thứ hai lưu trước.
      await priceBox(other).fill("111000");
      const [first] = await Promise.all([other.waitForResponse(isListingPut(id)), saveButton(other).click()]);
      expect(first.status(), await first.text()).toBe(200);

      // Phiên thứ nhất vẫn giữ revision cũ: lưu bị 412.
      await priceBox(page).fill("222000");
      const [stale] = await Promise.all([page.waitForResponse(isListingPut(id)), saveButton(page).click()]);
      expect(stale.status()).toBe(412);

      const notice = page.getByRole("alert").filter({ hasText: "Bài thi vừa được người khác sửa. Đã nạp lại bản mới nhất." });
      await expect(notice).toBeVisible();
      // Dữ liệu mới của phiên kia đã được nạp; giá đang nhập bị bỏ, thông báo vẫn còn.
      await expect(priceBox(page)).toHaveValue("111000");
      await expect(notice).toBeVisible();
      await expect(notice.getByText(/Thay đổi của bạn chưa được lưu/)).toBeVisible();

      // Nhập lại trên bản mới thì lưu được và thông báo biến mất.
      await priceBox(page).fill("222000");
      const [retry] = await Promise.all([page.waitForResponse(isListingPut(id)), saveButton(page).click()]);
      expect(retry.status(), await retry.text()).toBe(200);
      await expect(notice).toBeHidden();
    } finally {
      await otherContext.close();
    }
  });

  test("ảnh bìa sai định dạng: lỗi rõ, ảnh đang dùng không đổi; ảnh quá lớn hoặc quá nhỏ cũng báo rõ", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const api = await adminApi();
    const id = await createDraftExam(api, "COVER");
    await page.goto(`${ADMIN_URL}/exams/${id}/listing`);
    await expect(listingForm(page)).toBeVisible();

    const uploads: string[] = [];
    page.on("request", (request) => {
      if (request.method() === "POST" && request.url().includes(`/exams/${id}/cover`)) uploads.push(request.url());
    });
    const fileInput = page.locator('input[type="file"]');
    const cover = page.getByRole("img", { name: "Ảnh bìa bài thi" });

    // Ảnh hợp lệ: tải lên và hiện ngay.
    const [uploaded] = await Promise.all([
      page.waitForResponse((response) => response.url().includes(`/exams/${id}/cover`) && response.request().method() === "POST"),
      fileInput.setInputFiles({ name: "bia-hop-le.png", mimeType: "image/png", buffer: solidPng(480) }),
    ]);
    expect(uploaded.status(), await uploaded.text()).toBe(201);
    await expect(cover).toBeVisible();
    const currentSrc = await cover.getAttribute("src");
    expect(uploads).toHaveLength(1);

    // GIF: chặn ngay trên web, không gọi API, ảnh đang dùng giữ nguyên.
    await fileInput.setInputFiles({ name: "bia-hsa-thang-11.gif", mimeType: "image/gif", buffer: Buffer.from("GIF89a") });
    await expect(page.getByText("Tệp bia-hsa-thang-11.gif không đúng định dạng. Chọn ảnh JPG, PNG hoặc WEBP. Ảnh đang dùng được giữ nguyên.")).toBeVisible();
    await expect(cover).toHaveAttribute("src", currentSrc!);
    expect(uploads).toHaveLength(1);

    // Lớn hơn 2 MB: chặn ngay trên web.
    await fileInput.setInputFiles({ name: "bia-lon.png", mimeType: "image/png", buffer: Buffer.alloc(2 * 1024 * 1024 + 1, 1) });
    await expect(page.getByText(/Ảnh lớn hơn 2 MB\. Chọn ảnh nhỏ hơn\./)).toBeVisible();
    expect(uploads).toHaveLength(1);

    // Cạnh ngắn dưới 480 px: máy chủ từ chối, web hiện lỗi bằng chữ của mình và giữ ảnh đang dùng.
    const [rejected] = await Promise.all([
      page.waitForResponse((response) => response.url().includes(`/exams/${id}/cover`) && response.request().method() === "POST"),
      fileInput.setInputFiles({ name: "bia-nho.png", mimeType: "image/png", buffer: tinyPng() }),
    ]);
    expect(rejected.status()).toBe(422);
    await expect(page.getByText(/Cạnh ngắn của ảnh nhỏ hơn 480 px/)).toBeVisible();
    await expect(cover).toHaveAttribute("src", currentSrc!);

    // Ảnh hợp lệ gắn vào bài khi Lưu.
    const [saved] = await Promise.all([page.waitForResponse(isListingPut(id)), saveButton(page).click()]);
    expect(saved.status(), await saved.text()).toBe(200);
    expect((await getListing(api, id)).coverAssetId).toBeTruthy();
  });

  test("vai trò tùy chỉnh chỉ có EXAM.VIEW: tab chỉ xem, menu không lộ mục ẩn", async ({ page }) => {
    const suffix = `${RUN_ID}`;
    const roleCode = `FA1_EXAM_VIEW_${suffix}`.toUpperCase();
    const username = `fa1.view.${suffix}`.toLowerCase();
    const { api, xsrf } = await createPlatformApiContext();
    const headers = { origin: ADMIN_URL, "x-xsrf-token": xsrf };
    let roleId: string | undefined;
    let userId: string | undefined;
    try {
      const examId = await (async () => {
        const created = await api.post(admin("/exams"), {
          headers: { ...headers, "idempotency-key": crypto.randomUUID() },
          data: { code: `${TAG}-VIEW`.slice(0, 30), name: `${TAG}-VIEW`, kind: "HSA", scope: "FULL" },
        });
        expect(created.status(), await created.text()).toBe(201);
        return ((await created.json()) as { id: string }).id;
      })();

      const role = await api.post(admin("/roles"), {
        headers,
        data: { access: "MANAGE", code: roleCode, name: `FA1 chỉ xem bài thi ${suffix}`, permissionCodes: ["EXAM.VIEW"] },
      });
      expect(role.status(), await role.text()).toBe(201);
      roleId = ((await role.json()) as { id: string }).id;
      const user = await api.post(admin("/users"), {
        headers,
        data: { username, password: TEST_USER_PASSWORD, fullName: "FA1 chỉ xem bài thi", roleIds: [roleId], mustChangePassword: false },
      });
      expect(user.status(), await user.text()).toBe(201);
      userId = ((await user.json()) as { id: string }).id;

      await page.goto(`${WEB_URL}/login`);
      await page.getByLabel(/Tên đăng nhập hoặc email/).fill(username);
      await page.getByLabel(/Mật khẩu/).fill(TEST_USER_PASSWORD);
      await page.getByRole("button", { name: "Đăng nhập" }).click();
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      // Menu: Bài thi và Bộ đề (cùng quyền EXAM.VIEW, thuộc nhóm Thương mại) hiện; các mục không có quyền vẫn ẩn.
      const menu = page.getByRole("navigation", { name: "Menu chính" });
      await expect(menu.getByRole("link", { name: "Bài thi" })).toBeVisible();
      await expect(menu.getByRole("link", { name: "Bộ đề" })).toBeVisible();
      await expect(menu.getByText("Thương mại", { exact: true })).toBeVisible();
      for (const hidden of ["Khuyến mãi", "Đơn hàng", "Lượt thi"]) {
        await expect(menu.getByRole("link", { name: hidden })).toHaveCount(0);
      }
      await expect(menu.getByText("Học viên", { exact: true })).toHaveCount(0);

      await page.goto(`${ADMIN_URL}/exams/${examId}/listing`);
      await expect(listingForm(page)).toBeVisible();
      await expect(priceBox(page)).toBeDisabled();
      await expect(attemptsBox(page)).toBeDisabled();
      await expect(descriptionBox(page)).toBeDisabled();
      await expect(page.getByRole("checkbox").first()).toBeDisabled();
      await expect(page.getByRole("radio").first()).toBeDisabled();
      await expect(page.getByRole("button", { name: "Lưu", exact: true })).toHaveCount(0);
      await expect(page.getByText("Đổi ảnh")).toHaveCount(0);
      await expect(page.getByText("Chọn ảnh")).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Bỏ ảnh" })).toHaveCount(0);
      await expect(page.getByText(/Bạn chỉ có quyền xem thông tin bán/)).toBeVisible();
      // Thẻ xem trước vẫn hiện.
      await expect(page.getByRole("heading", { name: "Xem trước thẻ" })).toBeVisible();
    } finally {
      if (userId) {
        await api.put(admin(`/users/${userId}/roles`), { headers, data: { roleIds: [] } });
        await api.delete(admin(`/users/${userId}?reason=E2E%20cleanup`), { headers });
      }
      if (roleId) await api.delete(admin(`/roles/${roleId}`), { headers });
      await api.dispose();
    }
  });
});

interface ExamListItem {
  id: string;
  code: string;
  name: string;
  status: string;
}

/** Bài BCA FULL do DS nạp (mã `DS-…`): ưu tiên bài đã công bố. Chỉ đọc. */
async function dsBcaFull(api: AdminApi): Promise<ExamListItem> {
  const response = await api.api.get(admin("/exams?kind=BCA&scope=FULL&q=DS-&size=50"));
  expect(response.status(), await response.text()).toBe(200);
  const items = ((await response.json()) as { items: ExamListItem[] }).items.filter((item) => item.code.startsWith("DS-"));
  const found = items.find((item) => item.status === "PUBLISHED") ?? items[0];
  expect(found, "DS chưa nạp bài BCA FULL (mã DS-…, kỳ thi BCA, phạm vi FULL): ca cảnh báo chấm tay cần bài này").toBeDefined();
  return found!;
}

test.describe("FA-1: cảnh báo chấm tay và công bố trên bài BCA FULL của DS", () => {
  test.setTimeout(SCENARIO_TIMEOUT_MS);

  test("cảnh báo chấm tay BCA FULL: đúng câu chữ, đúng phần, môn, khối, ở vị trí của bản vẽ", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const api = await adminApi();
    const exam = await dsBcaFull(api);

    await page.goto(`${ADMIN_URL}/exams/${exam.id}/review`);
    // Chưa có kết quả kiểm tra đã lưu thì chạy một lần (không đổi dữ liệu của bài).
    const heading = page.getByRole("heading", { name: "Có câu chấm tay" });
    const run = page.getByRole("button", { name: /^Kiểm tra( cấu hình)?$/ });
    await expect(heading.or(run)).toBeVisible({ timeout: SOLVER_TIMEOUT_MS });
    if (await run.isVisible()) await run.click();
    await expect(heading).toBeVisible({ timeout: SOLVER_TIMEOUT_MS });

    const section = page.locator("section", { has: heading });
    await expect(section.getByText("Cảnh báo · không chặn công bố")).toBeVisible();
    await expect(section.getByText("Bài có câu tự luận. Điểm tổng hiện sau khi người chấm chấm xong.")).toBeVisible();
    const table = section.getByRole("table", { name: "Phần, môn và khối có câu tự luận" });
    const rows = table.getByRole("row").filter({ hasText: "Chấm tay" });
    await expect(rows.first()).toBeVisible();
    // BCA: câu tự luận là khối Ngữ văn của Phần 1.
    await expect(rows.first()).toContainText("Ngữ văn");
    await expect(rows.first().getByRole("link", { name: /Mở khối .* ở Cấu trúc và ma trận/ })).toHaveAttribute("href", /\/structure\?block=/);

    // Vị trí: sau tóm tắt lỗi và cảnh báo, trước nhóm Thông tin bán và kết quả theo đường lựa chọn.
    const y = async (name: string | RegExp) => (await page.getByRole("heading", { name }).first().boundingBox())!.y;
    expect(await y("Lỗi và cảnh báo")).toBeLessThan(await y("Có câu chấm tay"));
    expect(await y("Có câu chấm tay")).toBeLessThan(await y(/^Thông tin bán$/));
    expect(await y(/^Thông tin bán$/)).toBeLessThan(await y("Kết quả theo đường lựa chọn"));

    // Cảnh báo không chặn: banner tổng không báo lỗi chặn công bố.
    await expect(page.getByText(/Chưa công bố được: .* lỗi chặn công bố\./)).toHaveCount(0);
  });

  test("công bố khi có cảnh báo chấm tay: bản sao FA1- của bài BCA FULL công bố được", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const api = await adminApi();
    const source = await dsBcaFull(api);

    const duplicated = await api.api.post(admin(`/exams/${source.id}/duplicate`), {
      headers: { ...api.headers, "idempotency-key": crypto.randomUUID() },
      data: { name: `${TAG}-BCA` },
    });
    expect(duplicated.status(), await duplicated.text()).toBe(201);
    const copyId = ((await duplicated.json()) as { id: string }).id;
    try {
      // Thông tin bán đủ điều kiện công bố (bản sao chép thông tin bán của DS; điền lại nếu thiếu).
      const listing = await getListing(api, copyId);
      await putListing(api, copyId, {
        listPriceVnd: listing.listPriceVnd ?? 249000,
        attemptsGranted: listing.attemptsGranted ?? 1,
        description: listing.description || `Mô tả ${TAG}`,
      });

      const checked = await api.api.post(admin(`/exams/${copyId}/check`), { headers: api.headers, data: {}, timeout: SOLVER_TIMEOUT_MS });
      expect(checked.status(), await checked.text()).toBe(200);
      const result = (await checked.json()) as { status: string; groups: Array<{ issues: Array<{ code: string; severity: string }> }>; listingCheck?: { status: string } };
      const issues = result.groups.flatMap((group) => group.issues);
      expect(issues.some((issue) => issue.code === "MANUAL_GRADING_REQUIRED" && issue.severity === "WARNING")).toBe(true);
      expect(result.status).toBe("VALID");
      expect(result.listingCheck?.status).not.toBe("FAIL");

      await page.goto(`${ADMIN_URL}/exams/${copyId}/versions`);
      await page.getByRole("button", { name: "Công bố", exact: true }).first().click();
      const dialog = page.getByRole("dialog");
      await expect(dialog).toBeVisible();
      await dialog.getByRole("button", { name: /^Công bố \d+ đường lựa chọn$/ }).click();
      await expect(dialog).toBeHidden({ timeout: SOLVER_TIMEOUT_MS });

      const exam = await (await api.api.get(admin(`/exams/${copyId}`))).json() as { status: string; servingVersion: number | null };
      expect(exam.status).toBe("PUBLISHED");
      expect(exam.servingVersion).toBe(1);
    } finally {
      // Dọn: ngừng bán bản sao để danh mục không còn bài FA1- đang bán.
      const exam = (await (await api.api.get(admin(`/exams/${copyId}`))).json()) as { status: string; revision: number };
      if (exam.status === "PUBLISHED") {
        await api.api.post(admin(`/exams/${copyId}/discontinue`), {
          headers: { ...api.headers, "idempotency-key": crypto.randomUUID() },
          data: { expectedRevision: exam.revision },
        });
      }
    }
  });
});

test.describe("FA-1: ảnh tab Thông tin bán (ma trận khổ x nền x ngôn ngữ)", () => {
  let browser: Browser;
  let examId: string;
  let sessionCookies: Awaited<ReturnType<BrowserContext["cookies"]>>;

  test.beforeAll(async () => {
    browser = await launchMatrixBrowser();
    const bootstrap = await browser.newContext();
    await loginAdmin(bootstrap, "vi");
    sessionCookies = await bootstrap.cookies();
    await bootstrap.close();
    const api = await adminApi();
    examId = await createDraftExam(api, "SHOT");
    await putListing(api, examId, {
      summary: "Đề luyện đủ ba phần theo cấu trúc mới nhất.",
      description: "Mô tả bài thi hiện ở trang chi tiết.",
      objectives: "Làm quen áp lực thời gian.",
      audience: "Học sinh lớp 11, 12.",
      listPriceVnd: 249000,
      attemptsGranted: 3,
      benefitAiExplanation: true,
    });
  });

  test.afterAll(async () => {
    await browser.close();
  });

  async function openCell(cell: MatrixCell): Promise<{ context: BrowserContext; page: Page }> {
    const context = await openMatrixContext(browser, cell, ADMIN_URL);
    await context.addCookies(sessionCookies);
    await applyMatrixCookies(context, cell, ADMIN_URL);
    return { context, page: await context.newPage() };
  }

  for (const cell of matrixCells()) {
    test(`ảnh tab: ${cell.width}px ${cell.mode} ${cell.lang}`, async () => {
      const { context, page } = await openCell(cell);
      try {
        await page.goto(`${ADMIN_URL}/exams/${examId}/listing`);
        const name = cell.lang === "vi" ? "Thông tin bán" : "Listing";
        await expect(page.getByRole("form", { name })).toBeVisible();
        await expect(page.getByTestId("listing-card-preview")).toBeVisible();
        await expectMatrixClean(page, "exam-listing", cell);
      } finally {
        await context.close();
      }
    });
  }
});
