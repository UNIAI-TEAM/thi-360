import { expect, type APIRequestContext, type BrowserContext, type Locator, type Page } from "@playwright/test";
import { deflateSync } from "node:zlib";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createPlatformApiContext } from "./platform";

export const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");
export const ADMIN_URL = (process.env.THI360_ADMIN_URL ?? "http://localhost:3001").replace(/\/+$/, "");
const PLATFORM_PASSWORD = process.env.THI360_DEV_PLATFORM_PASSWORD ?? "platform-dev-only";

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(HERE, "../../..");

/** Mã/ID danh mục của dữ liệu mẫu (R__seed_catalog_dev_data.sql): cố định, dùng chung cho dev và test. */
export const KNOWLEDGE_GROUP_ID = "00000000-0000-7000-8100-000000000009";
export const SUBJECT_ITEM_ID: Record<string, string> = {
  TOAN: "00000000-0000-7000-8200-000000000201",
  VAN: "00000000-0000-7000-8200-000000000202",
  ANH: "00000000-0000-7000-8200-000000000203",
  HOA: "00000000-0000-7000-8200-000000000205",
  DIA: "00000000-0000-7000-8200-000000000208",
};
export const GRADE_G12_ITEM_ID = "00000000-0000-7000-8200-000000000303";

/** Phần nhãn sạch (ASCII) để đặt tên duy nhất theo từng lần chạy. */
export const RUN_ID = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);

export interface FixtureSample {
  name: string;
  valid: boolean;
  html: string;
}

/** Bảng mẫu HTML chuẩn của trình soạn (docs/specs/fixtures/rich-text-samples.json). */
export function fixtureSample(name: string): string {
  const raw = JSON.parse(readFileSync(resolve(REPO_ROOT, "docs/specs/fixtures/rich-text-samples.json"), "utf8")) as {
    samples: FixtureSample[];
  };
  const sample = raw.samples.find((item) => item.name === name);
  if (!sample) throw new Error(`Fixture sample not found: ${name}`);
  return sample.html;
}

export function word365Html(): string {
  return readFileSync(
    resolve(REPO_ROOT, "apps/web-admin/src/components/rich-text/insert/word/__fixtures__/word365-sample.html"),
    "utf8",
  );
}

/** Đăng nhập `platform` bằng API (cookie dùng chung mọi cổng của localhost) và đặt ngôn ngữ giao diện tiếng Anh. */
export async function loginAdmin(context: BrowserContext, locale: "en" | "vi" = "en"): Promise<void> {
  const res = await context.request.post(`${WEB_URL}/api/v1/auth/login`, {
    headers: { origin: WEB_URL },
    data: { identifier: "platform", password: PLATFORM_PASSWORD },
  });
  expect(res.ok(), `platform login: ${res.status()}`).toBe(true);
  await context.addCookies([{ name: "thi360_locale", value: locale, url: ADMIN_URL }]);
}

/** Ngữ cảnh API quản trị có XSRF: gọi qua cổng web-admin như trình duyệt. */
export interface AdminApi {
  api: APIRequestContext;
  headers: Record<string, string>;
}

export async function adminApi(): Promise<AdminApi> {
  const { api, xsrf } = await createPlatformApiContext();
  return { api, headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf } };
}

interface IdBody {
  id: string;
}

async function createNode(
  { api, headers }: AdminApi,
  parentId: string | null,
  code: string,
  name: string,
  refItemIds: string[],
): Promise<string> {
  const res = await api.post(`${ADMIN_URL}/api/v1/admin/catalog/groups/${KNOWLEDGE_GROUP_ID}/items`, {
    headers,
    data: { code, nameVi: name, nameEn: name, ...(parentId ? { parentId } : {}), ...(refItemIds.length ? { refItemIds } : {}) },
  });
  expect(res.status(), `create knowledge node ${code}: ${await res.text()}`).toBe(201);
  return ((await res.json()) as IdBody).id;
}

export interface KnowledgeTree {
  /** Tên nút kiến thức (cấp ba) cần chọn trong hộp chọn kiến thức. */
  leafName: string;
  leafCode: string;
}

/**
 * Cây kiến thức riêng của một lần chạy (chương → bài → kiến thức) gắn với môn và lớp 12: dữ liệu mẫu chỉ có kiến thức
 * của Toán lớp 10, và nút riêng khiến chỉ câu của luồng đó khớp bộ lọc của đề.
 */
export async function createKnowledgeTree(admin: AdminApi, subjectCode: string, tag: string): Promise<KnowledgeTree> {
  const subjectId = SUBJECT_ITEM_ID[subjectCode];
  if (!subjectId) throw new Error(`Unknown subject ${subjectCode}`);
  const base = `QA-${tag}-${RUN_ID}`.toUpperCase();
  const root = await createNode(admin, null, `${base}-C`, `QA chapter ${tag} ${RUN_ID}`, [subjectId, GRADE_G12_ITEM_ID]);
  const lesson = await createNode(admin, root, `${base}-L`, `QA lesson ${tag} ${RUN_ID}`, []);
  const leafName = `QA knowledge ${tag} ${RUN_ID}`;
  await createNode(admin, lesson, `${base}-K`, leafName, []);
  return { leafName, leafCode: `${base}-K` };
}

/** PNG 8x8 hợp lệ, một màu: ảnh tải lên qua hộp Hình thật. */
export function tinyPng(): Buffer {
  const width = 8;
  const height = 8;
  const row = Buffer.concat([Buffer.from([0]), Buffer.alloc(width * 3, 0x3b)]);
  const raw = Buffer.concat(Array.from({ length: height }, () => row));
  const chunk = (type: string, data: Buffer) => {
    const length = Buffer.alloc(4);
    length.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(body));
    return Buffer.concat([length, body, crc]);
  };
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8;
  header[9] = 2;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) !== 0 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(buf: Buffer): number {
  let crc = 0xffffffff;
  for (const byte of buf) crc = CRC_TABLE[(crc ^ byte) & 0xff]! ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

/* ---------- Biểu mẫu soạn câu ---------- */

export interface Classification {
  subject: string;
  difficulty: string;
  knowledgeCode: string;
}

async function choose(page: Page, label: string, option: string): Promise<void> {
  await page.getByLabel(new RegExp(`^${label}`)).first().click();
  await page.getByRole("option", { name: option, exact: true }).click();
}

/** Khối Phân loại: môn, lớp 12, mức độ, điểm 1 và một kiến thức (chọn trong hộp MAPID). */
export async function fillClassification(page: Page, values: Classification): Promise<void> {
  await choose(page, "Subject", values.subject);
  await choose(page, "Grade", "Grade 12");
  await choose(page, "Difficulty", values.difficulty);
  await page.getByRole("button", { name: "Choose knowledge" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByPlaceholder("Search by code or name").fill(values.knowledgeCode);
  await expect(dialog.getByRole("checkbox")).toHaveCount(1);
  await dialog.getByRole("checkbox").check();
  await dialog.getByRole("button", { name: /^Apply/ }).click();
  await expect(dialog).toBeHidden();
}

export async function openNewQuestion(page: Page, type: string): Promise<void> {
  await page.goto(`${ADMIN_URL}/questions/new?type=${type}`);
  await expect(page.getByRole("heading", { name: "Create question" })).toBeVisible();
}

/** Ô soạn nội dung thứ `index` trên trang (đề bài là ô đầu tiên). */
export function richEditor(page: Page, index = 0): Locator {
  return page.locator(".rich-text-editor").nth(index);
}

/** Vùng gõ (contenteditable) của một ô soạn. */
export function editable(page: Page, index = 0): Locator {
  return richEditor(page, index).locator('[contenteditable="true"]');
}

/** Nút của nhóm Chèn trên thanh công cụ của ô soạn (nhãn tiếng Anh của mục). */
export function insertButton(page: Page, label: string, index = 0): Locator {
  return richEditor(page, index).getByRole("toolbar").getByRole("button", { name: label, exact: true });
}

/* ---------- Lưu, Sẵn sàng, xem trước ---------- */

export interface SavedQuestion {
  id: string;
  code: string;
}

/** Điền bốn phương án chữ thuần vào các ô soạn từ `firstIndex` và chọn phương án A là đáp án đúng. */
export async function fillChoices(page: Page, firstIndex: number, texts = ["alpha", "beta", "gamma", "delta"]): Promise<void> {
  for (const [offset, text] of texts.entries()) {
    await editable(page, firstIndex + offset).click();
    await page.keyboard.type(text);
  }
  await page.getByRole("radio", { name: "Option A is correct" }).check();
}

/** Lưu nháp: máy chủ trả 201, trang chuyển sang chi tiết câu. */
export async function saveDraft(page: Page): Promise<SavedQuestion> {
  const [response] = await Promise.all([
    page.waitForResponse((r) => /\/api\/v1\/admin\/questions$/.test(r.url()) && r.request().method() === "POST"),
    page.getByRole("button", { name: "Save draft" }).click(),
  ]);
  expect(response.status(), await response.text()).toBe(201);
  const body = (await response.json()) as { summary: { id: string; code: string } };
  await page.waitForURL(new RegExp(`/questions/${body.summary.id}$`));
  return { id: body.summary.id, code: body.summary.code };
}

/** Câu đã lưu như máy chủ trả về (HTML đã qua bộ lọc phía máy chủ). */
export async function storedQuestion(admin: AdminApi, id: string): Promise<{ content: string; solution?: string | null; body: unknown }> {
  const res = await admin.api.get(`${ADMIN_URL}/api/v1/admin/questions/${id}`);
  expect(res.status()).toBe(200);
  return (await res.json()) as { content: string; solution?: string | null; body: unknown };
}

/** Đưa câu sang Sẵn sàng qua hộp xác nhận; máy chủ trả 200. */
export async function markReady(page: Page): Promise<void> {
  await page.getByRole("button", { name: "Mark as Ready" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByText("All 7 conditions met")).toBeVisible();
  const [response] = await Promise.all([
    page.waitForResponse((r) => /\/questions\/[0-9a-f-]{36}\/ready$/.test(r.url()) && r.request().method() === "POST"),
    dialog.getByRole("button", { name: "Mark as ready" }).click(),
  ]);
  expect(response.status(), await response.text()).toBe(200);
  await expect(page.getByText(/^Ready · v1$/)).toBeVisible();
}

/** Trang Xem trước của câu: trả vùng nội dung câu hỏi. */
export async function openQuestionPreview(page: Page, id: string): Promise<Locator> {
  await page.goto(`${ADMIN_URL}/questions/${id}/preview`);
  const main = page.getByRole("main");
  await expect(main.getByRole("button", { name: "Grade" }).or(main.getByText("Preview").first())).toBeVisible();
  return main;
}
