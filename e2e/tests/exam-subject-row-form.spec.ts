import { expect, test, type APIRequestContext } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin, RUN_ID } from "./helpers/editor-qa";
import { SCENARIO_TIMEOUT_MS } from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * FTM CR-3 lát CR-3a: Danh sách dòng lấy câu trên stack thật. Bài tạo bằng API từ mẫu chuẩn của kỳ (đã có DS bật ở sản phẩm), mở bước Cấu trúc, sửa trên
 * màn sửa dòng, lưu và đọc lại bằng API; bước Kiểm tra qua API cho đúng mã ở đúng mục. Bài có hậu tố theo lane (`RUN_ID`).
 */
type Admin = { api: APIRequestContext; headers: Record<string, string> };

async function createExam(admin: Admin, data: Record<string, unknown>): Promise<string> {
  const created = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, { headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() }, data });
  expect(created.status(), await created.text()).toBe(201);
  return ((await created.json()) as { id: string }).id;
}

interface Structure {
  revision: number;
  parts: Array<{ sections?: Array<{ id: string; subjectCode: string; matrix?: unknown; blocks: Array<{ id: string; name: string; shuffleUnits?: boolean; setCount?: number | null }> }> }>;
  examQuotas?: unknown[];
}

async function structureOf(admin: Admin, id: string): Promise<Structure> {
  const response = await admin.api.get(`${ADMIN_URL}/api/v1/admin/exams/${id}/structure`, { headers: admin.headers });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()) as Structure;
}

test("HSA Văn: bảng 5 dòng theo mẫu, nhóm 5 có công tắc đảo; sửa nhóm 5 rồi lưu và đọc lại", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const id = await createExam(admin, { code: `HSA-V-E2E-${RUN_ID}`.toUpperCase(), name: `HSA Văn dòng ${RUN_ID}`, kind: "HSA", scope: "SUBJECT", subjectCode: "VAN" });

  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await page.getByRole("button", { name: /^Soạn môn/ }).first().click();
  await expect(page.getByRole("heading", { name: "Các dòng lấy câu" })).toBeVisible();
  const rows = page.locator("[data-form-row]");
  await expect(rows).toHaveCount(5);
  await expect(page.locator("[data-form-slots] [data-form-slot]")).toHaveCount(5);

  // Nhóm 5: công tắc đảo chùm có; nhóm khác không.
  await rows.nth(4).getByRole("button", { name: /^Sửa dòng/ }).click();
  const toggle = page.getByRole("switch", { name: "Đảo thứ tự các chùm" });
  await expect(toggle).toBeChecked();
  await toggle.click();
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(page.getByText("Có thay đổi chưa lưu")).toBeHidden();

  const saved = await structureOf(admin, id);
  const blocks = saved.parts.flatMap((part) => part.sections ?? []).flatMap((section) => section.blocks);
  expect(blocks).toHaveLength(5);
  expect(blocks.at(-1)).toMatchObject({ shuffleUnits: false });
  expect(blocks.slice(0, 4).every((block) => block.shuffleUnits !== false)).toBe(true);

  // Bước Kiểm tra qua API không phát sinh lỗi cấu trúc mới từ việc đổi công tắc đảo.
  const check = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/check`, { headers: admin.headers, data: { scope: "EXAM" } });
  expect(check.status(), await check.text()).toBeLessThan(300);
  expect(JSON.stringify(await check.json())).not.toContain("GROUP_ANSWER_TYPE_MISSING");
});

test("THPTQG Lý: Phân bổ đối chiếu độ khó 8/6/4 lưu được và Kiểm tra không báo tổng; 8/6/2 báo SUBJECT_MATRIX_TOTAL_MISMATCH ở mục Phần I", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const id = await createExam(admin, { code: `THPT-LY-E2E-${RUN_ID}`.toUpperCase(), name: `THPTQG Lý dòng ${RUN_ID}`, kind: "THPTQG", scope: "SUBJECT", subjectCode: "LY" });

  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await page.getByRole("button", { name: /^Soạn môn/ }).first().click();
  await expect(page.getByRole("heading", { name: "Các dòng lấy câu" })).toBeVisible();
  await page.locator("[data-reference-block] summary").click();
  const cells = page.locator("[data-reference-block]").getByRole("textbox", { name: /^Độ khó: .*số câu$/ });
  const fill = async (values: string[]) => {
    for (const [index, value] of values.entries()) await cells.nth(index).fill(value);
    await page.getByRole("button", { name: "Lưu", exact: true }).click();
    await expect(page.getByText("Có thay đổi chưa lưu")).toBeHidden();
  };
  const issues = async (): Promise<string> => {
    const check = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/check`, { headers: admin.headers, data: { scope: "EXAM" } });
    expect(check.status(), await check.text()).toBeLessThan(300);
    return JSON.stringify(await check.json());
  };

  await fill(["8", "6", "4"]);
  expect(await issues()).not.toContain("SUBJECT_MATRIX_TOTAL_MISMATCH");
  const matrix = (await structureOf(admin, id)).parts.flatMap((part) => part.sections ?? [])[0]?.matrix as { byDifficulty: unknown[] };
  expect(matrix.byDifficulty).toHaveLength(3);

  await fill(["8", "6", "2"]);
  expect(await issues()).toContain("SUBJECT_MATRIX_TOTAL_MISMATCH");
});
