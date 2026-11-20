import { expect, test, type APIRequestContext } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin, RUN_ID } from "./helpers/editor-qa";
import { allSections, collectIssues, forceBlockOmitted, getStructure, SCENARIO_TIMEOUT_MS } from "./helpers/wave-2b";

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

for (const kind of ["HSCA", "VSAT"] as const) {
  test(`omittedReenableViaApi: ${kind} Văn không cho bật lại dòng Viết`, async ({ context }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const id = await createExam(admin, { code: `${kind}-OMIT-${RUN_ID}`.toUpperCase(), name: `${kind} Văn ${RUN_ID}`, kind, scope: "SUBJECT", subjectCode: "VAN" });
    const before = await getStructure(admin, id);
    const writing = allSections(before).flatMap((section) => section.blocks ?? []).find((block) => block.questionType === "ESSAY" && block.omitted === true);
    expect(writing?.id).toBeTruthy();
    await forceBlockOmitted(admin, id, writing!.id!, false);
    const after = await getStructure(admin, id);
    expect(allSections(after).flatMap((section) => section.blocks ?? []).find((block) => block.id === writing!.id)?.omitted).toBe(false);
    const check = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/check`, { headers: admin.headers, data: { scope: "EXAM" } });
    expect(check.status(), await check.text()).toBeLessThan(300);
    expect(collectIssues(await check.json()).map((issue) => issue.code)).toContain("OMITTED_BLOCK_LOCKED");
  });

  test(`lockedCountViaApi: ${kind} Toán SUBJECT báo khóa số lượng`, async ({ context }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const id = await createExam(admin, { code: `${kind}-LOCK-${RUN_ID}`.toUpperCase(), name: `${kind} Toán ${RUN_ID}`, kind, scope: "SUBJECT", subjectCode: "TOAN" });
    const structure = await getStructure(admin, id);
    const block = allSections(structure).flatMap((section) => section.blocks ?? []).find((item) => item.questionCount !== null && item.questionType !== "QUESTION_SET");
    expect(block?.id).toBeTruthy();
    block!.questionCount = (block!.questionCount ?? 0) + 1;
    const response = await admin.api.put(`${ADMIN_URL}/api/v1/admin/exams/${id}/structure`, {
      headers: admin.headers,
      data: { parts: structure.parts, examQuotas: structure.examQuotas ?? [], expectedRevision: structure.revision },
    });
    expect(response.status(), await response.text()).toBe(200);
    const check = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/check`, { headers: admin.headers, data: { scope: "EXAM" } });
    expect(check.status(), await check.text()).toBeLessThan(300);
    expect(collectIssues(await check.json()).map((issue) => issue.code)).toContain("BLOCK_COUNT_LOCKED");
  });
}

for (const scenario of [
  { kind: "HSCA", scope: "SUBJECT", subjectCode: "ANH", match: /Đọc – chùm 10 câu/, setSize: 10 },
  { kind: "VACT", scope: "FULL", subjectCode: undefined, match: /Logic, phân tích số liệu/, setSize: 3 },
] as const) {
  test(`splitRowsPutGet: ${scenario.kind} giữ mục và điều kiện audio sau khi tách`, async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const id = await createExam(admin, { code: `${scenario.kind}-SPLIT-${RUN_ID}`.toUpperCase(), name: `${scenario.kind} split ${RUN_ID}`, kind: scenario.kind, scope: scenario.scope, ...(scenario.subjectCode ? { subjectCode: scenario.subjectCode } : {}) });
    const before = await getStructure(admin, id);
    const section = allSections(before).find((item) => item.blocks?.some((block) => scenario.match.test(block.name ?? "")));
    const parent = section?.blocks?.find((block) => scenario.match.test(block.name ?? ""));
    expect(parent).toMatchObject({ setCount: 4, setSize: scenario.setSize });
    expect(parent?.id).toBeTruthy();
    await page.goto(`${ADMIN_URL}/exams/${id}/structure?section=${section!.id}`);
    const row = page.locator(`[data-form-row="${parent!.id}"]`);
    await expect(row).toBeVisible();
    await row.getByRole("button", { name: /^Sửa dòng/ }).click();
    await page.getByRole("button", { name: "Tách dòng" }).click();
    await page.getByRole("combobox", { name: "Số dòng sau khi tách" }).click();
    await expect(page.getByRole("option", { name: "5 dòng" })).toHaveCount(0);
    await page.getByRole("option", { name: "4 dòng" }).click();
    const children = page.locator("[data-row-split] input[aria-label^='Số chùm của dòng con']");
    await children.nth(0).fill("0");
    await children.nth(1).fill("2");
    await expect(page.getByText(/Dòng con 1 phải có ít nhất 1 chùm/)).toBeVisible();
    await expect(page.getByRole("button", { name: "Tách", exact: true })).toBeDisabled();
    let saves = 0;
    page.on("request", (request) => {
      if (request.method() === "PUT" && request.url().endsWith(`/exams/${id}/structure`)) saves += 1;
    });
    await page.getByRole("button", { name: "Lưu", exact: true }).click();
    expect(saves).toBe(0);
    await children.nth(0).fill("1");
    await children.nth(1).fill("1");
    await page.getByRole("button", { name: "Tách", exact: true }).click();
    await page.getByRole("button", { name: "Lưu", exact: true }).click();
    await expect(page.getByText("Có thay đổi chưa lưu")).toBeHidden();
    const after = await getStructure(admin, id);
    const next = allSections(after).find((item) => item.id === section!.id)!;
    expect(next.blocks?.map((item) => [item.setCount, item.setSize, item.questionCount])).toEqual(Array(4).fill([1, scenario.setSize, scenario.setSize]));
    expect(next.blocks?.[0]?.id).toBe(parent!.id);
    expect(next.blocks?.[0]?.templateGroupCode).toBe(parent!.templateGroupCode);
    expect(allSections(after).map((item) => [item.id, item.audioRequirement])).toEqual(allSections(before).map((item) => [item.id, item.audioRequirement]));
    const check = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/check`, { headers: admin.headers, data: { scope: "EXAM" } });
    expect(check.status(), await check.text()).toBeLessThan(300);
    const codes = collectIssues(await check.json()).map((issue) => issue.code);
    expect(codes).not.toContain("BLOCK_COUNT_LOCKED");
    expect(codes).not.toContain("SET_SIZE_MISMATCH");
  });
}

test("V-SAT Toán: xác nhận bảng kết hợp của phần và đọc lại", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const id = await createExam(admin, { code: `VSAT-COMBINED-${RUN_ID}`.toUpperCase(), name: `V-SAT Toán ${RUN_ID}`, kind: "VSAT", scope: "SUBJECT", subjectCode: "TOAN" });
  const before = await getStructure(admin, id);
  const section = allSections(before).find((item) => item.subjectCode === "TOAN")!;
  await page.goto(`${ADMIN_URL}/exams/${id}/structure?section=${section.id}`);
  const reference = page.locator("[data-reference-block]");
  await reference.locator("summary").click();
  const open = async () => {
    await reference.getByRole("button", { name: "Thêm điều kiện của Phân bổ đối chiếu" }).click();
    await page.getByRole("menuitem", { name: /^Kết hợp Lớp × Độ khó/ }).click();
  };
  await open();
  await expect(reference.getByRole("heading", { name: "Xem trước: sang bảng kết hợp" })).toBeVisible();
  await reference.getByRole("button", { name: "Hủy", exact: true }).click();
  await expect(reference.locator("[data-reference-combined]")).toHaveCount(0);
  await open();
  await reference.getByRole("button", { name: "Xác nhận chuyển" }).click();
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(page.getByText("Có thay đổi chưa lưu")).toBeHidden();
  const after = await getStructure(admin, id);
  expect(allSections(after).find((item) => item.id === section.id)?.matrix?.mode).toBe("COMBINED");
});

test("SP2E Lịch sử: không hiện khối lớp sau khi lưu", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const id = await createExam(admin, { code: `SP2E-SU-${RUN_ID}`.toUpperCase(), name: `SP2E Sử ${RUN_ID}`, kind: "SP2E", scope: "SUBJECT", subjectCode: "SU" });
  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await page.getByRole("button", { name: /^Soạn môn/ }).first().click();
  await page.locator("[data-reference-block] summary").click();
  await expect(page.locator('[data-reference-dimension="GRADE"]')).toHaveCount(0);
  const row = page.locator("[data-form-row]").first();
  await row.getByRole("button", { name: /^Sửa dòng/ }).click();
  await page.getByRole("button", { name: "Thêm điều kiện", exact: true }).click();
  await expect(page.getByRole("menuitem", { name: /^Khối lớp/ })).toHaveCount(0);
  await page.keyboard.press("Escape");
  const name = page.getByRole("textbox", { name: /Tên nhóm/ });
  await name.fill(`${await name.inputValue()} updated`);
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(page.getByText("Có thay đổi chưa lưu")).toBeHidden();
  await page.reload();
  await page.getByRole("button", { name: /^Soạn môn/ }).first().click();
  await page.locator("[data-reference-block] summary").click();
  await expect(page.locator('[data-reference-dimension="GRADE"]')).toHaveCount(0);
});

test("rowPercentPutGet: 30% của mục 40 câu lưu số 12, tải lại giữ số", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const id = await createExam(admin, { code: `SP2E-PERCENT-${RUN_ID}`.toUpperCase(), name: `SP2E percent ${RUN_ID}`, kind: "SP2E", scope: "SUBJECT", subjectCode: "SU" });
  const initial = await getStructure(admin, id);
  const part = initial.parts[0]!;
  const section = part.sections![0]!;
  const block = section.blocks![0]!;
  part.questionCount = 40;
  part.questionCountOnPaper = 40;
  part.questionCountRequired = 40;
  section.questionCount = 40;
  block.questionCount = 40;
  const response = await admin.api.put(`${ADMIN_URL}/api/v1/admin/exams/${id}/structure`, {
    headers: admin.headers,
    data: { parts: initial.parts, examQuotas: initial.examQuotas ?? [], expectedRevision: initial.revision },
  });
  expect(response.status(), await response.text()).toBe(200);
  await page.goto(`${ADMIN_URL}/exams/${id}/structure?section=${section.id}`);
  await page.locator(`[data-form-row="${block.id}"]`).getByRole("button", { name: /^Sửa dòng/ }).click();
  await page.getByRole("textbox", { name: "Tỷ lệ (%)" }).fill("30");
  await expect(page.getByText("30% của 40 câu = 12 câu")).toBeVisible();
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(page.getByText("Có thay đổi chưa lưu")).toBeHidden();
  const saved = await getStructure(admin, id);
  expect(allSections(saved).find((item) => item.id === section.id)?.blocks?.[0]?.questionCount).toBe(12);
  await page.reload();
  await page.locator(`[data-form-row="${block.id}"]`).getByRole("button", { name: /^Sửa dòng/ }).click();
  await expect(page.getByRole("textbox", { name: "Số câu", exact: true })).toHaveValue("12");
});

test("rowPercentWholeSets: 25% của 40 không chia được chùm 4, chặn lưu và kiểm tra", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const id = await createExam(admin, { code: `SP2E-SET-${RUN_ID}`.toUpperCase(), name: `SP2E sets ${RUN_ID}`, kind: "SP2E", scope: "SUBJECT", subjectCode: "SU" });
  const initial = await getStructure(admin, id);
  const part = initial.parts[0]!;
  const section = part.sections![0]!;
  const block = section.blocks![0]!;
  part.questionCount = 40;
  part.questionCountOnPaper = 40;
  part.questionCountRequired = 40;
  section.questionCount = 40;
  block.questionType = "QUESTION_SET";
  block.setCount = 10;
  block.setSize = 4;
  block.questionCount = 40;
  const response = await admin.api.put(`${ADMIN_URL}/api/v1/admin/exams/${id}/structure`, {
    headers: admin.headers,
    data: { parts: initial.parts, examQuotas: initial.examQuotas ?? [], expectedRevision: initial.revision },
  });
  expect(response.status(), await response.text()).toBe(200);
  await page.goto(`${ADMIN_URL}/exams/${id}/structure?section=${section.id}`);
  await page.locator(`[data-form-row="${block.id}"]`).getByRole("button", { name: /^Sửa dòng/ }).click();
  await page.getByRole("textbox", { name: "Tỷ lệ (%)" }).fill("25");
  await expect(page.getByText("Tỷ lệ này ra 10 câu, không chia được thành chùm 4 câu")).toBeVisible();
  let sends = 0;
  page.on("request", (request) => {
    if ((request.method() === "PUT" && request.url().endsWith(`/exams/${id}/structure`)) ||
        (request.method() === "POST" && request.url().endsWith(`/exams/${id}/check`))) sends += 1;
  });
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await page.getByRole("button", { name: "Kiểm tra toàn bài" }).click();
  await page.getByRole("button", { name: "Tiếp tục", exact: true }).click();
  expect(sends).toBe(0);
  await expect(page.locator('[data-form-row-editor] [data-form-field="COUNT"]')).toContainText("10 câu");
});

test("SP2E Văn: bật lại dòng Viết được lưu mà không có lỗi khóa", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const id = await createExam(admin, { code: `SP2E-VAN-${RUN_ID}`.toUpperCase(), name: `SP2E Văn ${RUN_ID}`, kind: "SP2E", scope: "SUBJECT", subjectCode: "VAN" });
  const before = await getStructure(admin, id);
  const section = allSections(before).find((item) => item.blocks?.some((block) => block.questionType === "ESSAY" && block.omitted === true))!;
  const writing = section.blocks!.find((block) => block.questionType === "ESSAY" && block.omitted === true)!;
  await page.goto(`${ADMIN_URL}/exams/${id}/structure?section=${section.id}`);
  await page.locator(`[data-form-row="${writing.id}"]`).getByRole("button", { name: /^Sửa dòng/ }).click();
  const toggle = page.getByRole("switch", { name: "Tạm bỏ qua" });
  await expect(toggle).toBeEnabled();
  await expect(toggle).toBeChecked();
  await toggle.click();
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(page.getByText("Có thay đổi chưa lưu")).toBeHidden();
  const after = await getStructure(admin, id);
  expect(allSections(after).flatMap((item) => item.blocks ?? []).find((block) => block.id === writing.id)?.omitted).toBe(false);
  const check = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/check`, { headers: admin.headers, data: { scope: "EXAM" } });
  expect(check.status(), await check.text()).toBeLessThan(300);
  expect(collectIssues(await check.json()).map((issue) => issue.code)).not.toContain("OMITTED_BLOCK_LOCKED");
});
