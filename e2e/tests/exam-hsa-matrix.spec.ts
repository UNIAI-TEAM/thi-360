import { expect, test, type Page } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin, type AdminApi } from "./helpers/editor-qa";
import {
  examCode,
  getStructure,
  SOLVER_TIMEOUT_MS,
  type CheckResult,
  type PreviewResultLite,
  type Structure,
  type StructureBlock,
  type StructureSection,
} from "./helpers/wave-2b";

test.setTimeout(SOLVER_TIMEOUT_MS);

/**
 * HSA-C (UNI-1700, UNI-1701). Bài HSA theo phần tạo bằng API: nhóm Bộ câu hỏi (Câu chùm) hiện đủ ở bước 2 và sinh thử lấy
 * nguyên chùm (khóa hành vi hiện có, không phải test đỏ trước); mức Tham khảo đã lưu của ma trận được mở thành Bắt buộc
 * và lưu lại đúng.
 */

interface HsaExam {
  id: string;
}

async function createPartExam(admin: AdminApi, partCode: "TOAN" | "VAN" | "SCIENCE", prefix: string): Promise<HsaExam> {
  const res = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data: { code: examCode(prefix), name: `HSA ${partCode} ma trận ${prefix}`, kind: "HSA", scope: "PART", partCode },
  });
  expect(res.status(), await res.text()).toBe(201);
  return (await res.json()) as HsaExam;
}

function sectionsOf(structure: Structure): StructureSection[] {
  return structure.parts.flatMap((part) => [
    ...(part.sections ?? []),
    ...(part.options ?? []).flatMap((option) => option.sections ?? []),
    ...(part.subjectPool ?? []),
  ]);
}

function setBlocks(structure: Structure): Array<{ subjectCode: string; block: StructureBlock }> {
  return sectionsOf(structure).flatMap((section) =>
    (section.blocks ?? []).filter((block) => block.questionType === "QUESTION_SET").map((block) => ({ subjectCode: section.subjectCode, block })),
  );
}

async function previewOf(admin: AdminApi, id: string, target: unknown): Promise<PreviewResultLite> {
  const res = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/preview`, {
    headers: admin.headers,
    data: { seed: 1, target },
  });
  expect(res.status(), await res.text()).toBe(200);
  return (await res.json()) as PreviewResultLite;
}

/** Mọi nhóm Bộ câu hỏi của đề sinh thử có đủ `setCount` phần tử loại SET (nguyên chùm, không câu con lẻ). */
function expectWholeSets(result: PreviewResultLite, expected: Array<{ subjectCode: string }>): void {
  expect(result.status, `${result.reason ?? ""}`).toBe("GENERATED");
  const blocks = result.paper.parts.flatMap((part) => part.blocks ?? []).filter((block) => block.questionType === "QUESTION_SET");
  expect(blocks.length).toBeGreaterThanOrEqual(expected.length);
  for (const block of blocks) {
    const items = block.items ?? [];
    expect(block.setCount ?? 0, `${block.subjectCode} ${block.blockName}`).toBeGreaterThan(0);
    expect(items.length, `${block.subjectCode} ${block.blockName}`).toBe(block.setCount);
    expect(items.every((item) => item.kind === "SET")).toBe(true);
  }
  for (const { subjectCode } of expected) {
    expect(blocks.some((block) => block.subjectCode === subjectCode), `thiếu nhóm Bộ câu hỏi của ${subjectCode}`).toBe(true);
  }
}

/** Mở môn trong danh sách bước 2, mở nhóm Bộ câu hỏi đầu tiên của môn và kiểm tra "Câu chùm" đang chọn. */
async function expectSetGroupOpens(page: Page, id: string, subject: { index?: number; name?: RegExp }): Promise<void> {
  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  const list = page.getByRole("navigation", { name: "Cấu trúc" });
  await expect(list).toBeVisible();
  if (subject.name) await list.getByRole("button", { name: subject.name }).first().click();
  else await list.locator("button[data-list-item]").nth(subject.index ?? 2).click();
  const row = page.locator("tr", { hasText: "Bộ câu hỏi" }).first();
  await expect(row).toBeVisible();
  await row.getByRole("button", { name: /Mở nhóm/ }).click();
  await expect(page.getByRole("radio", { name: "Câu chùm" })).toBeChecked();
  await expect(page.getByText("Bộ câu hỏi").first()).toBeVisible();
}

test.describe("HSA: nhóm Bộ câu hỏi (UNI-1700 AC-1)", () => {
  for (const [partCode, label, subjects] of [
    ["TOAN", "Toán", ["TOAN"]],
    ["VAN", "Văn", ["VAN"]],
  ] as const) {
    test(`HSA theo phần ${label}: nhóm Bộ câu hỏi hiện ở bước 2 và sinh thử lấy nguyên chùm`, async ({ context, page }) => {
      await loginAdmin(context, "vi");
      const admin = await adminApi();
      const { id } = await createPartExam(admin, partCode, `HSAM-${partCode}`);
      const structure = await getStructure(admin, id);
      const sets = setBlocks(structure);
      expect(sets.length, "mẫu HSA phải có nhóm QUESTION_SET").toBeGreaterThan(0);

      await expectSetGroupOpens(page, id, { index: 2 });
      expectWholeSets(await previewOf(admin, id, { kind: "EXAM" }), subjects.map((subjectCode) => ({ subjectCode })));
    });
  }

  test("HSA theo phần Khoa học: Lý, Hóa, Sinh có nhóm Chùm, sinh thử theo đường Lý-Hóa-Sinh lấy nguyên chùm", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const { id } = await createPartExam(admin, "SCIENCE", "HSAM-SCI");
    const structure = await getStructure(admin, id);
    for (const subjectCode of ["LY", "HOA", "SINH"]) {
      expect(setBlocks(structure).some((item) => item.subjectCode === subjectCode), subjectCode).toBe(true);
    }

    await expectSetGroupOpens(page, id, { name: /^Vật lí/ });

    const checkRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/check`, { headers: admin.headers, data: {} });
    expect(checkRes.status(), await checkRes.text()).toBe(200);
    const check = (await checkRes.json()) as CheckResult;
    const pathId = check.paths[0]?.pathId;
    expect(pathId, "kiểm tra phải trả về ít nhất một đường").toBeTruthy();
    const result = await previewOf(admin, id, { kind: "PATH", pathId, subjectCodes: ["LY", "HOA", "SINH"] });
    expectWholeSets(result, [{ subjectCode: "LY" }, { subjectCode: "HOA" }, { subjectCode: "SINH" }]);
  });
});

test.describe("HSA: mức Bắt buộc cố định (UNI-1701 AC-4)", () => {
  test("ma trận đã lưu Tham khảo: mở bài thấy Bắt buộc cố định kèm ghi chú, lưu thì máy chủ ghi Bắt buộc", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const { id } = await createPartExam(admin, "TOAN", "HSAM-ENF");
    const saved = await getStructure(admin, id);
    const matrices = sectionsOf(saved).flatMap((section) => [section.matrix, ...(section.blocks ?? []).map((block) => block.matrix)]);
    const withMatrix = matrices.filter((matrix): matrix is NonNullable<typeof matrix> => matrix !== null && matrix !== undefined);
    expect(withMatrix.length, "mẫu HSA Toán có ma trận").toBeGreaterThan(0);
    for (const matrix of withMatrix) {
      matrix.gradeEnforcement = "REFERENCE";
      matrix.difficultyEnforcement = "REFERENCE";
    }
    const put = await admin.api.put(`${ADMIN_URL}/api/v1/admin/exams/${id}/structure`, {
      headers: admin.headers,
      data: { parts: saved.parts, examQuotas: saved.examQuotas ?? [], expectedRevision: saved.revision },
    });
    expect(put.status(), await put.text()).toBeLessThan(300);
    const stored = sectionsOf(await getStructure(admin, id)).flatMap((section) => [section.matrix, ...(section.blocks ?? []).map((block) => block.matrix)]);
    expect(stored.some((matrix) => matrix?.gradeEnforcement === "REFERENCE"), "máy chủ giữ Tham khảo đã đặt bằng API").toBe(true);

    await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
    await expect(page.getByText(/Đã chuyển mức Tham khảo sang Bắt buộc theo quy định HSA/)).toBeVisible();
    await expect(page.getByText("Có thay đổi chưa lưu")).toBeVisible();
    const list = page.getByRole("navigation", { name: "Cấu trúc" });
    await list.locator("button[data-list-item]").nth(2).click();
    await expect(page.getByRole("radio", { name: "Tham khảo" })).toHaveCount(0);

    await page.getByRole("button", { name: "Lưu" }).click();
    await expect(page.getByText("Có thay đổi chưa lưu")).toBeHidden();
    await expect(page.getByText(/Đã chuyển mức Tham khảo sang Bắt buộc theo quy định HSA/)).toBeHidden();

    const after = sectionsOf(await getStructure(admin, id)).flatMap((section) => [section.matrix, ...(section.blocks ?? []).map((block) => block.matrix)]);
    const remaining = after.filter((matrix) => matrix?.gradeEnforcement === "REFERENCE" || matrix?.difficultyEnforcement === "REFERENCE");
    expect(remaining, "sau khi lưu không còn ma trận Tham khảo").toHaveLength(0);
    expect(after.some((matrix) => matrix?.gradeEnforcement === "REQUIRED")).toBe(true);
  });
});
