import { expect, type Browser, type Page, test } from "@playwright/test";
import { deflateRawSync } from "node:zlib";

const WEB_URL = process.env.THI360_WEB_URL ?? "http://localhost:3000";
const ADMIN_URL = process.env.THI360_ADMIN_URL ?? "http://localhost:3001";
const DEV_PASSWORD = process.env.THI360_DEV_PASSWORD ?? "Thi360@dev";

async function loginThroughSso(page: Page, identifier: string, password: string) {
  await page.goto(`${WEB_URL}/dang-nhap`);
  await page.getByLabel(/Tên đăng nhập hoặc email/).fill(identifier);
  await page.getByLabel(/Mật khẩu/).fill(password);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
}

function escapeXml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

function columnLetter(indexZeroBased: number): string {
  let n = indexZeroBased + 1;
  let letters = "";
  while (n > 0) {
    const rem = (n - 1) % 26;
    letters = String.fromCharCode(65 + rem) + letters;
    n = Math.floor((n - 1) / 26);
  }
  return letters;
}

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) {
      c = (c & 1) !== 0 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[i] = c >>> 0;
  }
  return table;
})();

function crc32(buf: Buffer): number {
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    crc = CRC_TABLE[(crc ^ buf[i]!) & 0xff]! ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function buildZip(entries: Array<{ name: string; content: string }>): Buffer {
  const localParts: Buffer[] = [];
  const centralParts: Buffer[] = [];
  let offset = 0;

  for (const entry of entries) {
    const nameBuf = Buffer.from(entry.name, "utf8");
    const rawBuf = Buffer.from(entry.content, "utf8");
    const compressedBuf = deflateRawSync(rawBuf);
    const crc = crc32(rawBuf);

    const localHeader = Buffer.alloc(30);
    localHeader.writeUInt32LE(0x04034b50, 0);
    localHeader.writeUInt16LE(20, 4);
    localHeader.writeUInt16LE(0, 6);
    localHeader.writeUInt16LE(8, 8);
    localHeader.writeUInt16LE(0, 10);
    localHeader.writeUInt16LE(0, 12);
    localHeader.writeUInt32LE(crc, 14);
    localHeader.writeUInt32LE(compressedBuf.length, 18);
    localHeader.writeUInt32LE(rawBuf.length, 22);
    localHeader.writeUInt16LE(nameBuf.length, 26);
    localHeader.writeUInt16LE(0, 28);

    localParts.push(localHeader, nameBuf, compressedBuf);

    const centralHeader = Buffer.alloc(46);
    centralHeader.writeUInt32LE(0x02014b50, 0);
    centralHeader.writeUInt16LE(20, 4);
    centralHeader.writeUInt16LE(20, 6);
    centralHeader.writeUInt16LE(0, 8);
    centralHeader.writeUInt16LE(8, 10);
    centralHeader.writeUInt16LE(0, 12);
    centralHeader.writeUInt16LE(0, 14);
    centralHeader.writeUInt32LE(crc, 16);
    centralHeader.writeUInt32LE(compressedBuf.length, 20);
    centralHeader.writeUInt32LE(rawBuf.length, 24);
    centralHeader.writeUInt16LE(nameBuf.length, 28);
    centralHeader.writeUInt16LE(0, 30);
    centralHeader.writeUInt16LE(0, 32);
    centralHeader.writeUInt16LE(0, 34);
    centralHeader.writeUInt16LE(0, 36);
    centralHeader.writeUInt32LE(0, 38);
    centralHeader.writeUInt32LE(offset, 42);

    centralParts.push(centralHeader, nameBuf);
    offset += localHeader.length + nameBuf.length + compressedBuf.length;
  }

  const centralSize = centralParts.reduce((sum, b) => sum + b.length, 0);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4);
  eocd.writeUInt16LE(0, 6);
  eocd.writeUInt16LE(entries.length, 8);
  eocd.writeUInt16LE(entries.length, 10);
  eocd.writeUInt32LE(centralSize, 12);
  eocd.writeUInt32LE(offset, 16);
  eocd.writeUInt16LE(0, 20);

  return Buffer.concat([...localParts, ...centralParts, eocd]);
}

function buildXlsxBuffer(sheetName: string, rows: string[][]): Buffer {
  const rowXml = rows
    .map((cells, rowIdx) => {
      const rowNum = rowIdx + 1;
      const cellsXml = cells
        .map((cell, colIdx) => {
          if (!cell) return "";
          const ref = `${columnLetter(colIdx)}${rowNum}`;
          return `<c r="${ref}" t="inlineStr"><is><t>${escapeXml(cell)}</t></is></c>`;
        })
        .join("");
      return `<row r="${rowNum}">${cellsXml}</row>`;
    })
    .join("");

  return buildZip([
    {
      name: "[Content_Types].xml",
      content:
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
        '<Default Extension="xml" ContentType="application/xml"/>' +
        '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>' +
        '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>' +
        "</Types>",
    },
    {
      name: "_rels/.rels",
      content:
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
        '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>' +
        "</Relationships>",
    },
    {
      name: "xl/workbook.xml",
      content:
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
        '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">' +
        `<sheets><sheet name="${escapeXml(sheetName)}" sheetId="1" r:id="rId1"/></sheets>` +
        "</workbook>",
    },
    {
      name: "xl/_rels/workbook.xml.rels",
      content:
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
        '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>' +
        "</Relationships>",
    },
    {
      name: "xl/worksheets/sheet1.xml",
      content:
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
        '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">' +
        `<sheetData>${rowXml}</sheetData>` +
        "</worksheet>",
    },
  ]);
}

async function provisionCatalogViewerUser(browser: Browser, suffix: string) {
  const adminContext = await browser.newContext();
  const adminPage = await adminContext.newPage();
  await loginThroughSso(adminPage, "sys.admin", DEV_PASSWORD);

  const cookies = await adminContext.cookies();
  const xsrf = cookies.find((c) => c.name === "thi360_xsrf")?.value ?? "";
  const headers: Record<string, string> = {
    "content-type": "application/json",
    origin: ADMIN_URL,
    ...(xsrf ? { "x-xsrf-token": xsrf } : {}),
  };

  const roleResp = await adminContext.request.post(`${ADMIN_URL}/api/v1/admin/roles`, {
    headers,
    data: {
      code: `E2E_CAT_VIEW_${suffix}`,
      name: `E2E Catalog Viewer ${suffix}`,
      access: "MANAGE",
      permissionCodes: ["CATALOG.VIEW"],
    },
  });
  expect(roleResp.ok()).toBeTruthy();
  const roleJson = (await roleResp.json()) as { id: string };

  const username = `e2e.catview.${suffix.toLowerCase()}`;
  const password = "Thi360@ViewOnly1!";
  const userResp = await adminContext.request.post(`${ADMIN_URL}/api/v1/admin/users`, {
    headers,
    data: {
      username,
      fullName: `E2E Viewer ${suffix}`,
      email: `${username}@thi360.vn`,
      password,
      mustChangePassword: false,
      roleIds: [roleJson.id],
    },
  });
  expect(userResp.ok()).toBeTruthy();
  await adminContext.close();

  return { username, password };
}

test.describe("Cấu hình chung — Danh mục dùng chung, tổ hợp, cây và Excel (AC-2)", () => {
  test("thêm nhóm → 3 giá trị → sắp xếp (giữ sau reload) → ngừng 1 giá trị → tổ hợp khóa xóa môn", async ({
    page,
  }) => {
    const suffix = Date.now().toString(36).toUpperCase();
    const groupCode = `E2E_GRP_${suffix}`;
    const groupName = `Nhóm E2E ${suffix}`;
    const subjCode = `E2E_SUBJ_${suffix}`;
    const subjName = `Môn E2E ${suffix}`;
    const comboCode = `E2E_COMBO_${suffix}`;
    const comboName = `Tổ hợp E2E ${suffix}`;

    await loginThroughSso(page, "content.admin", DEV_PASSWORD);
    await page.goto(`${ADMIN_URL}/cau-hinh-chung`);
    await expect(page.getByRole("heading", { name: "Cấu hình chung" })).toBeVisible();

    // 1. Add custom SIMPLE group
    await page.getByRole("button", { name: "Thêm danh mục" }).click();
    const groupDialog = page.getByRole("dialog");
    await groupDialog.getByLabel(/Mã danh mục/).fill(groupCode);
    await groupDialog.getByLabel(/Tên tiếng Việt/).fill(groupName);
    await groupDialog.getByRole("button", { name: "Lưu", exact: true }).click();
    await expect(page.getByRole("heading", { name: groupName })).toBeVisible();

    // 2. Add three values
    for (const [idx, code] of ["V1", "V2", "V3"].entries()) {
      await page.getByRole("button", { name: "Thêm giá trị" }).click();
      const itemDialog = page.getByRole("dialog");
      await itemDialog.getByLabel(/Mã giá trị/).fill(`${code}_${suffix}`);
      await itemDialog.getByLabel(/Tên tiếng Việt/).fill(`Giá trị ${idx + 1} ${suffix}`);
      await itemDialog.getByRole("button", { name: "Lưu", exact: true }).click();
      await expect(page.getByText(`Giá trị ${idx + 1} ${suffix}`)).toBeVisible();
    }

    // 3. Reorder V3 up and verify order survives reload
    await page.getByRole("button", { name: `Di chuyển Giá trị 3 ${suffix} lên` }).click();
    await expect(page.getByText("Đã cập nhật thứ tự hiển thị.")).toBeVisible();

    await page.reload();
    const rowsAfterReload = page.locator("tbody tr");
    await expect(rowsAfterReload.nth(0)).toContainText(`Giá trị 1 ${suffix}`);
    await expect(rowsAfterReload.nth(1)).toContainText(`Giá trị 3 ${suffix}`);
    await expect(rowsAfterReload.nth(2)).toContainText(`Giá trị 2 ${suffix}`);

    // 4. Stop V2
    await page.getByRole("button", { name: `Ngừng sử dụng Giá trị 2 ${suffix}` }).click();
    const stopDialog = page.getByRole("dialog");
    await stopDialog.getByLabel(/Lý do ngừng sử dụng/).fill("Ngừng trong E2E");
    await stopDialog.getByRole("button", { name: "Ngừng sử dụng", exact: true }).click();
    await expect(rowsAfterReload.nth(2)).toContainText("Ngừng sử dụng");

    // 5. Create subject then combination using it, verify deleting subject is blocked
    await page.getByRole("button", { name: /Môn học/ }).click();
    await page.getByRole("button", { name: "Thêm giá trị" }).click();
    const subjDialog = page.getByRole("dialog");
    await subjDialog.getByLabel(/Mã giá trị/).fill(subjCode);
    await subjDialog.getByLabel(/Tên tiếng Việt/).fill(subjName);
    await subjDialog.getByRole("button", { name: "Lưu", exact: true }).click();
    await expect(page.getByText(subjName)).toBeVisible();

    await page.getByRole("button", { name: /Mã tổ hợp/ }).click();
    await page.getByRole("button", { name: "Thêm giá trị" }).click();
    const comboDialog = page.getByRole("dialog");
    await comboDialog.getByLabel(/Mã giá trị/).fill(comboCode);
    await comboDialog.getByLabel(/Tên tiếng Việt/).fill(comboName);

    await comboDialog.getByRole("combobox", { name: /Kỳ thi/ }).click();
    await page.getByRole("option").first().click();

    await comboDialog.getByRole("combobox", { name: "Thêm môn", exact: true }).click();
    await page.getByRole("option", { name: new RegExp(subjName) }).click();

    await comboDialog.getByRole("button", { name: "Lưu", exact: true }).click();
    await expect(page.getByText(comboName)).toBeVisible();

    await page.getByRole("button", { name: /Môn học/ }).click();
    await page.getByRole("button", { name: `Xóa ${subjName}` }).click();
    const confirmDelete = page.getByRole("dialog");
    await confirmDelete.getByLabel(/Lý do xóa/).fill("Thử xóa môn đang dùng");
    await confirmDelete.getByRole("button", { name: "Xóa giá trị" }).click();

    await expect(
      page.getByRole("heading", { name: "Không thể xóa giá trị đang được dùng" }),
    ).toBeVisible();
    await expect(page.getByRole("dialog")).toContainText("Mã tổ hợp");
  });

  test("tải lại với ?group= rồi vẫn đổi được nhóm và chọn nút cây (VIS-1)", async ({ page }) => {
    await loginThroughSso(page, "content.admin", DEV_PASSWORD);
    await page.goto(`${ADMIN_URL}/cau-hinh-chung?group=SUBJECT`);
    await expect(page.getByRole("heading", { name: "Môn học" })).toBeVisible();

    await page.reload();
    await expect(page).toHaveURL(/group=SUBJECT(&|$)/);
    await expect(page.getByRole("heading", { name: "Môn học" })).toBeVisible();

    await page.getByRole("button", { name: /Mã tổ hợp/ }).click();
    await expect(page.getByRole("heading", { name: /Mã tổ hợp/ })).toBeVisible();
    await expect(page).toHaveURL(/group=SUBJECT_COMBINATION(&|$)/);

    await page.getByRole("button", { name: /Môn học/ }).click();
    await expect(page.getByRole("heading", { name: "Môn học" })).toBeVisible();
    await expect(page).toHaveURL(/group=SUBJECT(&|$)/);

    await page.goto(`${ADMIN_URL}/cau-hinh-chung?group=KNOWLEDGE`);
    await page.reload();
    const treeItems = page.getByRole("treeitem");
    await expect(treeItems.first()).toBeVisible();
    await treeItems.nth(0).focus();
    await page.keyboard.press("Enter");
    await expect(page).toHaveURL(/node=/);
    const firstNodeUrl = page.url();

    await treeItems.nth(1).focus();
    await page.keyboard.press("Enter");
    await expect(page).not.toHaveURL(firstNodeUrl);
    await expect(page).toHaveURL(/node=/);
  });

  test("hộp thoại cao vẫn bấm được Lưu ở màn hình 1280x720 (VIS-2)", async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 });
    await loginThroughSso(page, "content.admin", DEV_PASSWORD);

    await page.goto(`${ADMIN_URL}/cau-hinh-chung?group=SUBJECT_COMBINATION`);
    await page.getByRole("button", { name: "Thêm giá trị" }).click();
    let dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("button", { name: "Lưu", exact: true })).toBeInViewport();
    await page.keyboard.press("Escape");

    await page.goto(`${ADMIN_URL}/cau-hinh-chung?group=KNOWLEDGE`);
    await page.getByRole("button", { name: "Thêm Chương", exact: true }).first().click();
    dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("button", { name: "Lưu", exact: true })).toBeInViewport();
  });

  test("người dùng chỉ có CATALOG.VIEW không thấy nút ghi", async ({ browser }) => {
    const suffix = Date.now().toString(36).toUpperCase();
    const viewer = await provisionCatalogViewerUser(browser, suffix);

    const viewerContext = await browser.newContext();
    const viewerPage = await viewerContext.newPage();
    await loginThroughSso(viewerPage, viewer.username, viewer.password);

    await viewerPage.goto(`${ADMIN_URL}/cau-hinh-chung?group=SUBJECT`);
    await expect(viewerPage.getByRole("heading", { name: "Môn học" })).toBeVisible();
    await expect(viewerPage.getByRole("button", { name: "Xuất Excel" })).toBeVisible();

    await expect(viewerPage.getByRole("button", { name: "Thêm danh mục" })).toHaveCount(0);
    await expect(viewerPage.getByRole("button", { name: "Thêm giá trị" })).toHaveCount(0);
    await expect(viewerPage.getByRole("button", { name: "Nhập Excel" })).toHaveCount(0);
    await expect(viewerPage.getByRole("button", { name: /^Sửa / })).toHaveCount(0);
    await expect(viewerPage.getByRole("button", { name: /^Xóa / })).toHaveCount(0);

    await viewerContext.close();
  });

  test("KNOWLEDGE: thêm chương → bài → kiến thức, mở/đóng, ngừng chương hiện Ngừng theo cấp trên và nhập tệp mẫu", async ({
    page,
  }) => {
    const suffix = Date.now().toString(36).toUpperCase();
    const chapCode = `CH_${suffix}`;
    const chapName = `Chương ${suffix}`;
    const lessonCode = `BAI_${suffix}`;
    const lessonName = `Bài ${suffix}`;
    const knowCode = `KT_${suffix}`;
    const knowName = `Kiến thức ${suffix}`;

    await loginThroughSso(page, "content.admin", DEV_PASSWORD);
    await page.goto(`${ADMIN_URL}/cau-hinh-chung?group=KNOWLEDGE`);
    await expect(page.getByRole("heading", { name: "Cấu trúc kiến thức" })).toBeVisible();

    // 0. V2-2: the dialog's close X must be the top element at its centre, so a real click closes it
    await page.getByRole("button", { name: "Thêm Chương", exact: true }).first().click();
    let dlg = page.getByRole("dialog");
    const closeX = dlg.getByRole("button", { name: "Đóng", exact: true });
    await closeX.click({ trial: true }); // waits for the box to be stable and not covered
    const closeBox = await closeX.boundingBox();
    expect(closeBox).not.toBeNull();
    await page.mouse.click(closeBox!.x + closeBox!.width / 2, closeBox!.y + closeBox!.height / 2);
    await expect(dlg).toBeHidden();

    // 1. Add chapter
    await page.getByRole("button", { name: "Thêm Chương", exact: true }).first().click();
    dlg = page.getByRole("dialog");
    await dlg.getByLabel(/Mã giá trị/).fill(chapCode);
    await dlg.getByLabel(/Tên tiếng Việt/).fill(chapName);
    await dlg.getByRole("button", { name: "Lưu", exact: true }).click();
    await expect(page.getByText(chapName)).toBeVisible();

    // 2. Add lesson under chapter
    await page.getByRole("button", { name: `Thêm Bài vào ${chapName}`, exact: true }).click();
    dlg = page.getByRole("dialog");
    await dlg.getByLabel(/Mã giá trị/).fill(lessonCode);
    await dlg.getByLabel(/Tên tiếng Việt/).fill(lessonName);
    await dlg.getByRole("button", { name: "Lưu", exact: true }).click();

    await page.getByRole("button", { name: `Mở ${chapName}`, exact: true }).click();
    await expect(page.getByText(lessonName)).toBeVisible();

    // 3. Add knowledge under lesson
    await page.getByRole("button", { name: `Thêm Kiến thức vào ${lessonName}`, exact: true }).click();
    dlg = page.getByRole("dialog");
    await dlg.getByLabel(/Mã giá trị/).fill(knowCode);
    await dlg.getByLabel(/Tên tiếng Việt/).fill(knowName);
    await dlg.getByRole("button", { name: "Lưu", exact: true }).click();

    await page.getByRole("button", { name: `Mở ${lessonName}`, exact: true }).click();
    await expect(page.getByText(knowName)).toBeVisible();

    // 4. Collapse & re-expand
    await page.getByRole("button", { name: "Thu gọn", exact: true }).click();
    await expect(page.getByText(knowName)).toHaveCount(0);
    await page.getByRole("button", { name: `Mở ${chapName}`, exact: true }).click();
    await expect(page.getByText(lessonName)).toBeVisible();

    // 5. Stop chapter -> lesson shows "Ngừng theo cấp trên"
    await page.getByRole("button", { name: `Thao tác của ${chapName}`, exact: true }).click();
    await page.getByRole("menuitem", { name: "Ngừng sử dụng", exact: true }).click();
    const stopDialog = page.getByRole("dialog");
    await expect(stopDialog).toContainText(/2 nút bên dưới sẽ chuyển sang Ngừng theo cấp trên/);
    await stopDialog.getByLabel(/Lý do ngừng sử dụng/).fill("Ngừng chương E2E");
    await stopDialog.getByRole("button", { name: "Ngừng sử dụng", exact: true }).click();

    await expect(
      page.getByRole("treeitem", { name: new RegExp(lessonName) }),
    ).toContainText("Ngừng theo cấp trên");

    // 6. Import sample xlsx into KNOWLEDGE and verify imported tree node renders
    const impChapCode = `ICH_${suffix}`;
    const impChapName = `Chương nhập ${suffix}`;
    const impLessonCode = `IBAI_${suffix}`;
    const impLessonName = `Bài nhập ${suffix}`;
    const impKnowCode = `IKT_${suffix}`;
    const impKnowName = `Kiến thức nhập ${suffix}`;

    const sampleXlsx = buildXlsxBuffer("DanhMuc", [
      [
        "Mã Chương",
        "Chương (Tiếng Việt)",
        "Mã Bài",
        "Bài (Tiếng Việt)",
        "Mã Kiến thức",
        "Kiến thức (Tiếng Việt)",
      ],
      [impChapCode, impChapName, impLessonCode, impLessonName, impKnowCode, impKnowName],
    ]);

    await page.getByRole("button", { name: "Nhập Excel" }).click();
    const importDlg = page.getByRole("dialog");
    await importDlg.locator('input[type="file"]').setInputFiles({
      name: `knowledge-${suffix}.xlsx`,
      mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      buffer: sampleXlsx,
    });
    await importDlg.getByRole("button", { name: "Kiểm tra tệp" }).click();
    await expect(importDlg.getByText(/Tất cả 1 dòng đều hợp lệ/)).toBeVisible();
    await importDlg.getByRole("button", { name: /Nhập 1 dòng hợp lệ/ }).click();
    await expect(importDlg.getByText(/Đã nhập xong/)).toBeVisible();
    await importDlg.getByRole("button", { name: "Đóng", exact: true }).click();

    await expect(page.getByText(impChapName)).toBeVisible();
    await page.getByRole("button", { name: `Mở ${impChapName}` }).click();
    await expect(page.getByText(impLessonName)).toBeVisible();
  });
});
