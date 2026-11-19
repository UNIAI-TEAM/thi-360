import { expect, test, type Page } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin, type AdminApi } from "./helpers/editor-qa";
import {
  examCode,
  getStructure,
  SOLVER_TIMEOUT_MS,
  type PreviewResultLite,
  type StructureSection,
} from "./helpers/wave-2b";

test.setTimeout(SOLVER_TIMEOUT_MS);

/**
 * UNI-2758 (AC-12, AC-6/AC-9 hiển thị): bài HSA theo phần Toán ở chế độ "Theo ma trận MAPID", quy tắc thêm qua hộp thoại
 * của bước 2. Mã MAPID lấy từ dữ liệu dev: ba bộ SEED-2B-MAPID gồm ba câu con gắn đúng 0D1-1-1, 0D1-1-2, 0D1-1-3
 * (thuộc Bài 0D1-1), còn nhóm "Chùm cuối" của mẫu cần đúng 1 bộ 3 câu con nên bộ lọc chỉ còn ba bộ đó.
 */

const SET_GROUP = "Chùm cuối";
const MAPID_CODE = /^SEED-2B-MAPID-/;
/** MAPID của ba bộ nguồn duy nhất: cả bài 0D1-1 (mọi câu con cùng nhánh) và kiến thức 0D1-1-1 (mỗi bộ đúng một câu con). */
const LESSON = { code: "0D1-1", name: "Bài 1" };
const LEAF = { code: "0D1-1-1" };

interface RuleLite {
  id: string;
  type: string;
  groupId?: string | null;
  nodeIds: string[];
  comparator?: string;
  count?: number | null;
}

interface MapidSection extends StructureSection {
  mapidMode?: string | null;
  mapidRules?: RuleLite[] | null;
}

async function createMathExam(admin: AdminApi): Promise<string> {
  const res = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data: { code: examCode("HSAMAP"), name: `HSA Toán MAPID ${examCode("R")}`, kind: "HSA", scope: "PART", partCode: "TOAN" },
  });
  expect(res.status(), await res.text()).toBe(201);
  return ((await res.json()) as { id: string }).id;
}

function mathSection(structure: { parts: Array<{ sections?: MapidSection[] }> }): MapidSection {
  const section = structure.parts.flatMap((part) => part.sections ?? []).find((item) => item.subjectCode === "TOAN");
  expect(section, "mẫu HSA Toán phải có mục Toán").toBeTruthy();
  return section!;
}

async function previewExam(admin: AdminApi, id: string): Promise<PreviewResultLite> {
  const res = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/preview`, {
    headers: admin.headers,
    data: { seed: 1, target: { kind: "EXAM" } },
  });
  expect(res.status(), await res.text()).toBe(200);
  return (await res.json()) as PreviewResultLite;
}

async function runExamCheck(admin: AdminApi, id: string): Promise<Record<string, unknown>> {
  const res = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/check`, { headers: admin.headers, data: {} });
  expect(res.status(), await res.text()).toBe(200);
  return (await res.json()) as Record<string, unknown>;
}

/** Nhấn "Thêm quy tắc" của bảng Quy tắc MAPID; hộp thoại thao tác toàn bằng data-testid theo subcontract. */
async function openRuleDialog(page: Page) {
  const table = page.getByTestId("mapid-rules-table");
  await expect(table).toBeVisible();
  await table.getByTestId("mapid-rule-add").click();
  const dialog = page.getByRole("dialog", { name: /^Thêm quy tắc|Sửa quy tắc/ });
  await expect(dialog).toBeVisible();
  return dialog;
}

/** Chọn MAPID trong hộp chọn kiến thức của hộp thoại quy tắc (tìm theo mã rồi tick). */
async function pickMapid(page: Page, code: string) {
  const dialog = page.getByRole("dialog", { name: /^Thêm quy tắc|Sửa quy tắc/ });
  await dialog.getByTestId("mapid-rule-mapid-picker").click();
  const picker = page.getByRole("dialog", { name: /Chọn kiến thức/ });
  await expect(picker).toBeVisible();
  await picker.getByPlaceholder(/Tìm theo mã hoặc tên/).fill(code);
  await picker.getByRole("checkbox", { name: new RegExp(`^${code.replace("-", "\\-")} `) }).check();
  await picker.getByRole("button", { name: /Áp dụng/ }).click();
  await expect(picker).not.toBeVisible();
  await expect(dialog.getByText(new RegExp(`^${code.replace("-", "\\-")} `)).first()).toBeVisible();
}

async function saveStructure(page: Page) {
  await page.getByRole("button", { name: "Lưu" }).click();
  await expect(page.getByText("Có thay đổi chưa lưu")).not.toBeVisible({ timeout: 60_000 });
}

test("HSA Toán MAPID: sinh thử tôn trọng quy tắc, kiểm tra báo xung đột với Đi tới quy tắc, Toàn bài tạm ngừng quy tắc", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const id = await createMathExam(admin);

  const before = mathSection(await getStructure(admin, id));
  const setBlock = (before.blocks ?? []).find((block) => block.name === SET_GROUP);
  expect(setBlock?.id, `mẫu HSA Toán phải có nhóm ${SET_GROUP}`).toBeTruthy();

  await page.goto(`${ADMIN_URL}/exams/${id}/structure?section=${encodeURIComponent(before.id)}`);
  await expect(page.getByRole("radio", { name: /Theo ma trận MAPID/ })).toBeVisible({ timeout: 60_000 });
  await page.getByRole("radio", { name: /Theo ma trận MAPID/ }).check();
  await expect(page.getByTestId("mapid-rules-table")).toBeVisible();

  // Quy tắc 1: nhóm Chùm cuối — Số lượng · Đúng · 1 câu thuộc 0D1-1-1.
  let dialog = await openRuleDialog(page);
  await dialog.getByRole("radio", { name: new RegExp(`^${SET_GROUP}`) }).check();
  await dialog.getByRole("radio", { name: /^Số lượng/ }).check();
  await pickMapid(page, LEAF.code);
  await expect(dialog.getByTestId("mapid-rule-comparator")).toBeVisible();
  await dialog.getByTestId("mapid-rule-count").fill("1");
  await dialog.getByTestId("mapid-rule-comparator").getByRole("radio", { name: "Đúng" }).check();
  await dialog.getByTestId("mapid-rule-save").click();
  await expect(page.getByRole("dialog")).toHaveCount(0);

  // Quy tắc 2: nhóm Chùm cuối — Chỉ lấy nguồn từ 0D1-1 (cả nhánh, mọi câu con cùng một MAPID).
  dialog = await openRuleDialog(page);
  await dialog.getByRole("radio", { name: new RegExp(`^${SET_GROUP}`) }).check();
  await dialog.getByRole("radio", { name: /Chỉ lấy nguồn/ }).check();
  await pickMapid(page, LESSON.code);
  await dialog.getByTestId("mapid-rule-save").click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByTestId("mapid-rules-table").getByRole("row")).toHaveCount(3); // header + 2 quy tắc
  await saveStructure(page);

  const saved = mathSection(await getStructure(admin, id));
  expect(saved.mapidMode).toBe("MATRIX");
  const rules = saved.mapidRules ?? [];
  expect(rules, "máy chủ phải lưu hai quy tắc").toHaveLength(2);
  const countRule = rules.find((rule) => rule.type === "COUNT")!;
  const sourceRule = rules.find((rule) => rule.type === "SOURCE")!;
  expect(countRule.groupId).toBe(setBlock!.id);
  expect(sourceRule.groupId).toBe(setBlock!.id);

  // Sinh thử: nhóm Chùm cuối chỉ được lấy một bộ của nguồn 0D1-1 và đúng 1 đơn vị mang 0D1-1-1 (AC-12).
  const result = await previewExam(admin, id);
  expect(result.status, result.reason ?? "").toBe("GENERATED");
  const paperSet = result.paper.parts
    .flatMap((part) => part.blocks ?? [])
    .find((block) => block.blockName === SET_GROUP);
  expect(paperSet, "đề thử phải có nhóm Chùm cuối").toBeTruthy();
  expect(paperSet!.items ?? []).toHaveLength(1);
  const set = paperSet!.items![0]!;
  expect(set.code ?? set.setId ?? "", "bộ được lấy phải thuộc nguồn MAPID 0D1-1").toMatch(MAPID_CODE);
  const children = set.children ?? [];
  expect(children, "bộ Chùm cuối phải đủ 3 câu con").toHaveLength(3);
  for (const child of children) {
    expect(child.code ?? "", "câu con phải cùng nhánh MAPID của bộ").toMatch(MAPID_CODE);
  }
  // Quy tắc Đúng 1: trong cả đề đúng một đơn vị của nhóm mang MAPID 0D1-1-1 (bộ lấy ra có câu con đầu gắn mã đó).
  expect(children.filter((child) => (child.code ?? "").endsWith("-0"))).toHaveLength(1);

  // AC-6: bảng đối chiếu có dòng của quy tắc Số lượng (quy tắc Chỉ lấy nguồn không có dòng riêng).
  const mapidRows = (result.compliance ?? []).filter((row) => (row.criterion ?? "").startsWith("MAPID:"));
  expect(mapidRows.length, "đối chiếu phải có dòng MAPID").toBeGreaterThanOrEqual(1);
  const countRow = mapidRows.find((row) => row.criterion === `MAPID:${countRule.id}`)!;
  expect(countRow.required).toBe(1);

  // Thêm quy tắc xung đột với quy tắc Đúng 1 (cùng nhóm, cùng MAPID, Đúng 2) → EXACT_COUNTS_DIFFER.
  dialog = await openRuleDialog(page);
  await dialog.getByRole("radio", { name: new RegExp(`^${SET_GROUP}`) }).check();
  await dialog.getByRole("radio", { name: /^Số lượng/ }).check();
  await pickMapid(page, LEAF.code);
  await dialog.getByTestId("mapid-rule-count").fill("2");
  await dialog.getByTestId("mapid-rule-comparator").getByRole("radio", { name: "Đúng" }).check();
  await dialog.getByTestId("mapid-rule-save").click();
  await expect(page.getByRole("dialog")).toHaveCount(0);

  // Kiểm tra môn chạy trên bản đã lưu (màn tự lưu trước khi kiểm tra).
  await page.getByRole("button", { name: "Kiểm tra môn" }).click();
  const conflictLine = page.getByText(/Quy tắc .*xung đột|xung đột/).first();
  await expect(conflictLine, "kết quả kiểm tra phải nêu xung đột MAPID").toBeVisible({ timeout: 180_000 });
  await expect(page.getByText(/cùng phạm vi nhưng một quy tắc đòi đúng|đòi đúng/)).toBeVisible();

  const check = await runExamCheck(admin, id);
  const groups = (check.groups ?? []) as Array<{ issues?: Array<Record<string, unknown>> }>;
  const conflicts = groups
    .flatMap((group) => group.issues ?? [])
    .filter((issue) => issue.code === "MAPID_RULE_CONFLICT");
  expect(conflicts, "máy chủ phải báo MAPID_RULE_CONFLICT").not.toHaveLength(0);
  expect(conflicts[0]!.conflictKind).toBe("EXACT_COUNTS_DIFFER");
  expect(conflicts[0]!.ruleIds).toEqual(expect.arrayContaining([countRule.id]));

  // "Đi tới quy tắc" chọn lại môn và cuộn/đưa tiêu điểm tới đúng dòng quy tắc (AC-9 hiển thị).
  const firstRuleId = String(conflicts[0]!.ruleId);
  await page.getByRole("button", { name: /Đi tới quy tắc/ }).first().click();
  const row = page.getByTestId(`mapid-rule-row-${firstRuleId}`);
  await expect(row).toBeVisible();
  await expect(row).toBeFocused({ timeout: 15_000 });

  // Toàn bài: quy tắc tạm ngừng, ghi chú nêu số quy tắc; sinh thử bỏ qua chúng (không còn dòng MAPID trong đối chiếu).
  await page.getByRole("radio", { name: /Toàn bài/ }).check();
  await expect(page.getByTestId("mapid-paused-note")).toContainText("3 quy tắc tạm ngừng áp dụng");
  await saveStructure(page);
  const relaxed = mathSection(await getStructure(admin, id));
  expect(relaxed.mapidMode).toBe("ALL");

  const unfiltered = await previewExam(admin, id);
  expect(unfiltered.status, unfiltered.reason ?? "").toBe("GENERATED");
  const freeSet = unfiltered.paper.parts
    .flatMap((part) => part.blocks ?? [])
    .find((block) => block.blockName === SET_GROUP);
  expect(freeSet?.items ?? []).toHaveLength(1);
  expect(
    (unfiltered.compliance ?? []).filter((row) => (row.criterion ?? "").startsWith("MAPID:")),
    "Toàn bài không còn đối chiếu quy tắc MAPID",
  ).toHaveLength(0);
});
