import { expect, type Page, request as playwrightRequest, test } from "@playwright/test";

const WEB_URL = process.env.THI360_WEB_URL ?? "http://localhost:3000";
const ADMIN_URL = process.env.THI360_ADMIN_URL ?? "http://localhost:3001";
const PLATFORM_PASSWORD = process.env.THI360_DEV_PLATFORM_PASSWORD ?? "platform-dev-only";

async function submitLoginForm(page: Page, identifier: string, password: string) {
  await page.getByLabel(/Tên đăng nhập hoặc email/).fill(identifier);
  await page.getByLabel(/Mật khẩu/).fill(password);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
}

async function createPlatformApiContext() {
  const api = await playwrightRequest.newContext({ baseURL: ADMIN_URL });
  const loginRes = await api.post(`${WEB_URL}/api/v1/auth/login`, {
    headers: { origin: WEB_URL },
    data: { identifier: "platform", password: PLATFORM_PASSWORD },
  });
  if (!loginRes.ok()) {
    throw new Error(`Platform API login failed: ${loginRes.status()} ${await loginRes.text()}`);
  }
  const state = await api.storageState();
  const xsrf = state.cookies.find((cookie) => cookie.name === "thi360_xsrf")?.value ?? "";
  return { api, xsrf };
}

function escapeXml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");
}

function crc32(bytes: Uint8Array): number {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let i = 0; i < 8; i++) {
      crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function buildStoredZip(entries: Array<{ name: string; data: Uint8Array }>): Buffer {
  const encoder = new TextEncoder();
  const localParts: Buffer[] = [];
  const centralParts: Buffer[] = [];
  let offset = 0;

  for (const entry of entries) {
    const nameBytes = encoder.encode(entry.name);
    const dataBuf = Buffer.from(entry.data);
    const checksum = crc32(dataBuf);

    const localHeader = Buffer.alloc(30 + nameBytes.length);
    localHeader.writeUInt32LE(0x04034b50, 0);
    localHeader.writeUInt16LE(20, 4);
    localHeader.writeUInt16LE(0, 6);
    localHeader.writeUInt16LE(0, 8);
    localHeader.writeUInt16LE(0, 10);
    localHeader.writeUInt16LE(0, 12);
    localHeader.writeUInt32LE(checksum, 14);
    localHeader.writeUInt32LE(dataBuf.length, 18);
    localHeader.writeUInt32LE(dataBuf.length, 22);
    localHeader.writeUInt16LE(nameBytes.length, 26);
    localHeader.writeUInt16LE(0, 28);
    Buffer.from(nameBytes).copy(localHeader, 30);

    localParts.push(localHeader, dataBuf);

    const centralHeader = Buffer.alloc(46 + nameBytes.length);
    centralHeader.writeUInt32LE(0x02014b50, 0);
    centralHeader.writeUInt16LE(20, 4);
    centralHeader.writeUInt16LE(20, 6);
    centralHeader.writeUInt16LE(0, 8);
    centralHeader.writeUInt16LE(0, 10);
    centralHeader.writeUInt16LE(0, 12);
    centralHeader.writeUInt16LE(0, 14);
    centralHeader.writeUInt32LE(checksum, 16);
    centralHeader.writeUInt32LE(dataBuf.length, 20);
    centralHeader.writeUInt32LE(dataBuf.length, 24);
    centralHeader.writeUInt16LE(nameBytes.length, 28);
    centralHeader.writeUInt16LE(0, 30);
    centralHeader.writeUInt16LE(0, 32);
    centralHeader.writeUInt16LE(0, 34);
    centralHeader.writeUInt16LE(0, 36);
    centralHeader.writeUInt32LE(0, 38);
    centralHeader.writeUInt32LE(offset, 42);
    Buffer.from(nameBytes).copy(centralHeader, 46);

    centralParts.push(centralHeader);
    offset += localHeader.length + dataBuf.length;
  }

  const centralDir = Buffer.concat(centralParts);
  const endRecord = Buffer.alloc(22);
  endRecord.writeUInt32LE(0x06054b50, 0);
  endRecord.writeUInt16LE(0, 4);
  endRecord.writeUInt16LE(0, 6);
  endRecord.writeUInt16LE(entries.length, 8);
  endRecord.writeUInt16LE(entries.length, 10);
  endRecord.writeUInt32LE(centralDir.length, 12);
  endRecord.writeUInt32LE(offset, 16);
  endRecord.writeUInt16LE(0, 20);

  return Buffer.concat([...localParts, centralDir, endRecord]);
}

function columnLetters(colIndex: number): string {
  let n = colIndex + 1;
  let result = "";
  while (n > 0) {
    const rem = (n - 1) % 26;
    result = String.fromCharCode(65 + rem) + result;
    n = Math.floor((n - 1) / 26);
  }
  return result;
}

function buildMinimalXlsx(rows: string[][]): Buffer {
  const encoder = new TextEncoder();
  const sheetRowsXml = rows
    .map((cells, rIdx) => {
      const rowNum = rIdx + 1;
      const cellsXml = cells
        .map((val, cIdx) => {
          const ref = `${columnLetters(cIdx)}${rowNum}`;
          return `<c r="${ref}" t="inlineStr"><is><t>${escapeXml(val)}</t></is></c>`;
        })
        .join("");
      return `<row r="${rowNum}">${cellsXml}</row>`;
    })
    .join("");

  const sheetXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">` +
    `<sheetData>${sheetRowsXml}</sheetData></worksheet>`;

  const workbookXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" ` +
    `xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">` +
    `<sheets><sheet name="DanhSachTaiKhoan" sheetId="1" r:id="rId1"/></sheets></workbook>`;

  const workbookRelsXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">` +
    `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>` +
    `</Relationships>`;

  const rootRelsXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">` +
    `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>` +
    `</Relationships>`;

  const contentTypesXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">` +
    `<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>` +
    `<Default Extension="xml" ContentType="application/xml"/>` +
    `<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>` +
    `<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>` +
    `</Types>`;

  return buildStoredZip([
    { name: "[Content_Types].xml", data: encoder.encode(contentTypesXml) },
    { name: "_rels/.rels", data: encoder.encode(rootRelsXml) },
    { name: "xl/workbook.xml", data: encoder.encode(workbookXml) },
    { name: "xl/_rels/workbook.xml.rels", data: encoder.encode(workbookRelsXml) },
    { name: "xl/worksheets/sheet1.xml", data: encoder.encode(sheetXml) },
  ]);
}

test.describe("Cấp tài khoản hàng loạt (UNI-2548)", () => {
  test("luồng 4 bước: tải mẫu -> upload file có lỗi -> tải file lỗi -> upload file đúng -> xác nhận với Idempotency-Key -> kết quả -> cảnh báo rời trang -> tải & xóa file kết quả", async ({
    page,
  }) => {
    const suffix = Date.now().toString(36);
    const userGen = `e2e.imp1.${suffix}`;
    const userPwd = `e2e.imp2.${suffix}`;
    const explicitPassword = `Thi360@${suffix}P1`;

    const headers = ["Tên đăng nhập*", "Mật khẩu", "Họ và tên*", "Email", "Số điện thoại", "Mã lớp"];
    const invalidXlsx = buildMinimalXlsx([
      headers,
      ["bad user", "", "Lỗi Tên Đăng Nhập", "", "", ""],
      [userGen, "", `Import Gen ${suffix}`, "", "", ""],
    ]);
    const validXlsx = buildMinimalXlsx([
      headers,
      [userGen, "", `Import Gen ${suffix}`, "", "", ""],
      [userPwd, explicitPassword, `Import Pwd ${suffix}`, "", "", ""],
    ]);

    const { api, xsrf } = await createPlatformApiContext();

    try {
      await page.goto(`${WEB_URL}/dang-nhap`);
      await submitLoginForm(page, "platform", PLATFORM_PASSWORD);
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      await page.getByRole("link", { name: "Tài khoản", exact: true }).click();
      await expect(page).toHaveURL(`${ADMIN_URL}/tai-khoan`);

      await page.getByRole("link", { name: "Cấp hàng loạt" }).click();
      await expect(page).toHaveURL(`${ADMIN_URL}/tai-khoan/cap-hang-loat`);
      await expect(page.getByRole("heading", { name: "Cấp tài khoản hàng loạt" })).toBeVisible();

      const breadcrumb = page.getByRole("navigation", { name: "Vị trí hiện tại" });
      await expect(breadcrumb).toContainText("Tài khoản");
      await expect(breadcrumb).toContainText("Cấp hàng loạt");
      await expect(page.getByRole("link", { name: "Tài khoản", exact: true })).toHaveAttribute(
        "aria-current",
        "page",
      );

      // 1. Tải file mẫu
      const templateDownload = page.waitForEvent("download");
      await page.getByRole("button", { name: "Tải file mẫu" }).click();
      const templateFile = await templateDownload;
      expect(templateFile.suggestedFilename()).toContain(".xlsx");

      // 2. Upload file có dòng lỗi -> xem lỗi theo dòng & tải file lỗi
      await page.locator('input[type="file"]').setInputFiles({
        name: `invalid-${suffix}.xlsx`,
        mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        buffer: invalidXlsx,
      });
      await page.getByRole("button", { name: "Tải lên và kiểm tra" }).click();
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/tai-khoan/cap-hang-loat/[^/]+$`));
      await expect(page.locator("tbody tr", { hasText: "bad user" })).toContainText("Lỗi");

      const errDownload = page.waitForEvent("download");
      await page.getByRole("button", { name: /Tải file lỗi/ }).click();
      const errFile = await errDownload;
      expect(errFile.suggestedFilename()).toContain(".xlsx");

      // 3. Tải lên lại file hợp lệ -> xem trước -> sang bước xác nhận -> xác nhận với Idempotency-Key
      await page.getByRole("link", { name: "Tải lên lại" }).click();
      await expect(page).toHaveURL(`${ADMIN_URL}/tai-khoan/cap-hang-loat`);

      await page.locator('input[type="file"]').setInputFiles({
        name: `valid-${suffix}.xlsx`,
        mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        buffer: validXlsx,
      });
      await page.getByRole("button", { name: "Tải lên và kiểm tra" }).click();
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/tai-khoan/cap-hang-loat/[^/]+$`));
      await page.getByRole("button", { name: "Xác nhận cấp 2 tài khoản" }).click();
      await expect(page.getByRole("heading", { name: "Kiểm tra lần cuối" })).toBeVisible();

      const confirmResponsePromise = page.waitForResponse(
        (res) =>
          res.url().includes("/api/v1/admin/import-batches/") &&
          res.url().endsWith("/confirm") &&
          res.request().method() === "POST",
      );
      await page.getByRole("button", { name: "Xác nhận cấp 2 tài khoản" }).click();
      const confirmRes = await confirmResponsePromise;
      expect(confirmRes.status()).toBe(200);
      expect(confirmRes.request().headers()["idempotency-key"]).toBeTruthy();
      // 4. Đợi worker xử lý xong -> cảnh báo rời trang -> tải file kết quả -> xóa file kết quả
      await expect(page.getByRole("button", { name: "Tải file kết quả" })).toBeVisible({
        timeout: 30_000,
      });

      await page.getByRole("link", { name: "Về danh sách tài khoản" }).click();
      const leaveDialog = page.getByRole("dialog");
      await expect(
        leaveDialog.getByText("Rời trang khi chưa tải file kết quả?"),
      ).toBeVisible();
      await leaveDialog.getByRole("button", { name: "Ở lại trang" }).click();
      await expect(leaveDialog).toBeHidden();

      const resultDownload = page.waitForEvent("download");
      await page.getByRole("button", { name: "Tải file kết quả" }).click();
      const resultFile = await resultDownload;
      expect(resultFile.suggestedFilename()).toContain(".xlsx");

      await page.getByRole("button", { name: "Xóa file kết quả" }).click();
      const deleteDialog = page.getByRole("dialog");
      await expect(deleteDialog.getByText("Xóa file kết quả?")).toBeVisible();
      await deleteDialog.getByRole("button", { name: "Xóa file kết quả" }).click();
      await expect(page.getByText("File kết quả đã được xóa khỏi hệ thống.")).toBeVisible();
    } finally {
      for (const username of [userGen, userPwd]) {
        const searchRes = await api.get(`/api/v1/admin/users?q=${encodeURIComponent(username)}&size=10`);
        if (searchRes.ok()) {
          const body = (await searchRes.json()) as { items?: Array<{ id: string; username: string }> };
          const found = body.items?.find((item) => item.username === username);
          if (found) {
            await api.delete(`/api/v1/admin/users/${found.id}?reason=E2E%20cleanup`, {
              headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
            });
          }
        }
      }
      await api.dispose();
    }
  });

  test("người dùng chỉ có quyền USER.VIEW không thấy nút Cấp hàng loạt và bị chặn khi vào thẳng đường dẫn", async ({
    page,
  }) => {
    const suffix = Date.now().toString(36);
    const roleCode = `E2E_IMP_VIEW_${suffix}`.toUpperCase();
    const viewerUsername = `e2e.impv.${suffix}`;
    const viewerPassword = `Thi360@${suffix}V1`;

    const { api, xsrf } = await createPlatformApiContext();
    let roleId: string | undefined;
    let viewerId: string | undefined;

    try {
      const roleRes = await api.post("/api/v1/admin/roles", {
        headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        data: {
          access: "MANAGE",
          code: roleCode,
          name: `E2E Import Viewer ${suffix}`,
          permissionCodes: ["USER.VIEW"],
        },
      });
      expect(roleRes.status()).toBe(201);
      const roleBody = (await roleRes.json()) as { id: string };
      roleId = roleBody.id;

      const userRes = await api.post("/api/v1/admin/users", {
        headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        data: {
          username: viewerUsername,
          password: viewerPassword,
          fullName: `Import Viewer ${suffix}`,
          roleIds: [roleId],
          mustChangePassword: false,
        },
      });
      expect(userRes.status()).toBe(201);
      const userBody = (await userRes.json()) as { id: string };
      viewerId = userBody.id;

      await page.goto(`${WEB_URL}/dang-nhap`);
      await submitLoginForm(page, viewerUsername, viewerPassword);
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      await page.getByRole("link", { name: "Tài khoản", exact: true }).click();
      await expect(page).toHaveURL(`${ADMIN_URL}/tai-khoan`);
      await expect(page.getByRole("link", { name: "Cấp hàng loạt" })).toHaveCount(0);

      await page.goto(`${ADMIN_URL}/tai-khoan/cap-hang-loat`);
      await expect(page.getByText("Bạn không có quyền vào trang này")).toBeVisible();
    } finally {
      if (viewerId) {
        await api.put(`/api/v1/admin/users/${viewerId}/roles`, {
          headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
          data: { roleIds: [] },
        });
        await api.delete(`/api/v1/admin/users/${viewerId}?reason=E2E%20cleanup`, {
          headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        });
      }
      if (roleId) {
        await api.delete(`/api/v1/admin/roles/${roleId}`, {
          headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        });
      }
      await api.dispose();
    }
  });
});
