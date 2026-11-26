import { expect, test, type Page } from "@playwright/test";
import {
  ADMIN_URL,
  adminApi,
  GRADE_G12_ITEM_ID,
  KNOWLEDGE_GROUP_ID,
  loginAdmin,
  RUN_ID,
  SUBJECT_ITEM_ID,
  UNIT_LABEL,
  type AdminApi,
} from "./helpers/editor-qa";
import {
  createReadyQuestions,
  examCode,
  getStructure,
  issueCodes,
  paperQuestions,
  SOLVER_TIMEOUT_MS,
  type PreviewResultLite,
  type QuestionDraft,
  type Structure,
  type StructureBlock,
  type StructureSection,
} from "./helpers/wave-2b";

test.setTimeout(SOLVER_TIMEOUT_MS);

/**
 * CR-2 (UNI-2763), biểu mẫu Ma trận môn (MT) của QDA, BCA, V-ACT (FTM R-3, R-4, R-6, R-8, R-9, R-12, R-15). Bài tạo bằng API với mã có hậu tố
 * lane `CR2` và `RUN_ID` (không dùng chung dữ liệu); mở môn trong trình duyệt, sửa bằng ô của MT, rồi đọc lại cấu trúc và kết quả kiểm tra
 * qua API để chứng minh dữ liệu lưu và lỗi của máy chủ (không chỉ giao diện). Khối ca HSA là của CR-2H.
 */

interface ExamRef {
  id: string;
}

async function createExam(admin: AdminApi, data: Record<string, unknown>, prefix: string): Promise<ExamRef> {
  const res = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data: { code: examCode(`${prefix}-CR2`), name: `${prefix} CR2 ${Date.now()}`, ...data },
  });
  expect(res.status(), await res.text()).toBe(201);
  return (await res.json()) as ExamRef;
}

function sectionsOf(structure: Structure): StructureSection[] {
  return structure.parts.flatMap((part) => [...(part.sections ?? []), ...(part.options ?? []).flatMap((option) => option.sections ?? []), ...(part.subjectPool ?? [])]);
}

function sectionOf(structure: Structure, subjectCode: string): StructureSection {
  const found = sectionsOf(structure).find((section) => section.subjectCode === subjectCode);
  expect(found, `bài không có môn ${subjectCode}`).toBeDefined();
  return found!;
}

const roleBlock = (section: StructureSection, type: string | null): StructureBlock | undefined =>
  (section.blocks ?? []).find((block) => (type === null ? block.questionType == null || block.questionType === "SINGLE_CHOICE" : block.questionType === type));

async function checkSection(admin: AdminApi, examId: string, sectionId: string): Promise<string[]> {
  const res = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${examId}/check`, { headers: admin.headers, data: { scope: "SECTION", sectionId } });
  expect(res.ok(), await res.text()).toBe(true);
  return issueCodes(await res.json());
}

async function saveInUi(page: Page, examId: string): Promise<void> {
  const returnUrl = page.url();
  const next = page.getByRole("button", { name: "Tiếp tục → Thông tin bán", exact: true });
  const [response] = await Promise.all([
    page.waitForResponse((item) => item.url().endsWith(`/exams/${examId}/structure`) && item.request().method() === "PUT", { timeout: SOLVER_TIMEOUT_MS }),
    next.click(),
  ]);
  expect(response.status(), await response.text()).toBe(200);
  await page.waitForURL(/\/exams\/[^/]+\/listing/);
  await page.goto(returnUrl);
}

const MATRIX_STRUCTURE_HEADING = "Cấu trúc thành phần câu hỏi";

function field(page: Page, name: string) {
  if (name === "Số chùm") return page.getByRole("textbox", { name: /^Số chùm của / });
  if (name === "Điền đáp án") return page.getByRole("textbox", { name: /^Số câu của .*(Điền đáp án|Trả lời ngắn)/i });
  return page.getByRole("textbox", { name, exact: true });
}

async function typeInto(page: Page, name: string, value: string): Promise<void> {
  const input = field(page, name);
  await input.fill(value);
  await expect(input).toHaveValue(value);
}

test.describe("MT của QDA: khung, giới hạn Khoa học, ma trận (FTM AC 35 phần QDA, AC 53, AC 59)", () => {
  test("khung: QDA Khoa học mở MT (Cấu trúc thành phần câu hỏi, ô, không nút thêm nhóm cũ); `?block=` của nhóm chùm đặt focus vào ô Số chùm", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const exam = await createExam(admin, { kind: "QDA", scope: "PART", partCode: "SCIENCE" }, "QDA-S");
    const structure = await getStructure(admin, exam.id);
    const ly = sectionOf(structure, "LY");
    const set = roleBlock(ly, "QUESTION_SET")!;

    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${ly.id}`);
    await expect(page.getByRole("heading", { level: 3, name: MATRIX_STRUCTURE_HEADING })).toBeVisible();
    await expect(field(page, "Số chùm")).toHaveValue("1");
    await expect(page.getByRole("button", { name: /Thêm nhóm/ })).toHaveCount(0);
    await expect(page.getByRole("heading", { level: 3, name: "Khối lớp và độ khó" })).toBeVisible();

    // R1-8: mở thẳng theo `?block=` (môn vẽ sau khi tải xong dữ liệu) vẫn đặt focus đúng ô của nhóm.
    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?block=${set.id}`);
    await expect(page.getByRole("heading", { level: 3, name: MATRIX_STRUCTURE_HEADING })).toBeVisible();
    await expect(field(page, "Số chùm")).toBeFocused();
    await expect(page.getByRole("textbox", { name: /^Tên nhóm/ })).toHaveCount(0);
  });

  test("QDA Lý: 3 chùm hoặc 0 câu điền báo lỗi tại ô và không lưu; 2 chùm lưu 2 × 3 = 6, Kiểm tra không có SET_SIZE_MISMATCH; QDA Sử 0 câu điền không lỗi", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const exam = await createExam(admin, { kind: "QDA", scope: "PART", partCode: "SCIENCE" }, "QDA-L");
    const before = await getStructure(admin, exam.id);
    const ly = sectionOf(before, "LY");
    const fillBefore = roleBlock(ly, "SHORT_ANSWER")!.questionCount!;

    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${ly.id}`);
    await typeInto(page, "Số chùm", "3");
    await expect(page.getByText("Số chùm từ 1 đến 2")).toBeVisible();
    await page.getByRole("button", { name: "Lưu nháp và thoát", exact: true }).click();
    // Không gửi PUT: cấu trúc ở máy chủ không đổi và nút giữ nguyên trạng thái chưa lưu.
    expect(roleBlock(sectionOf(await getStructure(admin, exam.id), "LY"), "QUESTION_SET")!.setCount).toBe(1);
    await expect(field(page, "Số chùm")).toBeFocused();

    await typeInto(page, "Số chùm", "2");
    await typeInto(page, "Điền đáp án", "0");
    await expect(page.getByText("Cần ít nhất 1 câu điền đáp án")).toBeVisible();
    await page.getByRole("button", { name: "Kiểm tra cấu trúc", exact: true }).click();
    expect(roleBlock(sectionOf(await getStructure(admin, exam.id), "LY"), "SHORT_ANSWER")!.questionCount).toBe(fillBefore);

    await typeInto(page, "Điền đáp án", String(fillBefore));
    await saveInUi(page, exam.id);
    const saved = sectionOf(await getStructure(admin, exam.id), "LY");
    expect(roleBlock(saved, "QUESTION_SET")).toMatchObject({ setCount: 2, setSize: 3, questionCount: 6 });
    expect(await checkSection(admin, exam.id, ly.id!)).not.toContain("SET_SIZE_MISMATCH");

    // Sử cho phép 0 câu điền: mở môn không có lỗi giới hạn.
    const su = sectionOf(before, "SU");
    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${su.id}`);
    await expect(page.getByRole("heading", { level: 3, name: MATRIX_STRUCTURE_HEADING })).toBeVisible();
    await expect(page.getByText(/Cần ít nhất \d+ câu điền đáp án/)).toHaveCount(0);
  });

  test("QDA Lý khai độ khó 10/10/5 (để trống Vận dụng cao) lưu REQUIRED 3 giá trị; khai 10/5/5 lưu được và Kiểm tra báo SUBJECT_MATRIX_TOTAL_MISMATCH ở môn Lý", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const exam = await createExam(admin, { kind: "QDA", scope: "PART", partCode: "SCIENCE" }, "QDA-M");
    const ly = sectionOf(await getStructure(admin, exam.id), "LY");
    const target = ly.questionCount ?? 25;

    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${ly.id}`);
    const difficulty = page.getByRole("heading", { level: 4, name: "Độ khó" }).locator("xpath=ancestor::*[@data-form-field][1]");
    await difficulty.getByLabel("Nhận biết", { exact: true }).fill("10");
    await difficulty.getByLabel("Thông hiểu", { exact: true }).fill("10");
    await difficulty.getByLabel("Vận dụng", { exact: true }).fill(String(target - 20));
    await expect(difficulty.getByText(`Đã khai ${target}/${target}`)).toBeVisible();
    await saveInUi(page, exam.id);
    const matrix = sectionOf(await getStructure(admin, exam.id), "LY").matrix;
    expect(matrix?.byDifficulty?.map((row) => [row.code, row.value])).toHaveLength(3);
    expect(await checkSection(admin, exam.id, ly.id!)).not.toContain("SUBJECT_MATRIX_TOTAL_MISMATCH");

    await difficulty.getByLabel("Thông hiểu", { exact: true }).fill("5");
    await expect(difficulty.getByText(`Đã khai ${target - 5}/${target}`)).toBeVisible();
    await saveInUi(page, exam.id);
    expect(await checkSection(admin, exam.id, ly.id!)).toContain("SUBJECT_MATRIX_TOTAL_MISMATCH");
  });

  test("QDA Toán: Phạm vi kiến thức và nút Thêm chỉ tiêu chủ đề; chưa có dòng thì không hiện bảng", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    // Mẫu QDA_PART_MATH có mã phần `TOAN` (không có `MATH`).
    const exam = await createExam(admin, { kind: "QDA", scope: "PART", partCode: "TOAN" }, "QDA-T");
    const toan = sectionOf(await getStructure(admin, exam.id), "TOAN");
    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${toan.id}`);
    await expect(page.getByRole("heading", { level: 3, name: "Phạm vi và chỉ tiêu chủ đề" })).toBeVisible();
    await expect(page.getByText("Phần còn lại")).toHaveCount(0);
    await page.getByRole("button", { name: "Thêm chỉ tiêu chủ đề", exact: true }).click();
    await expect(page.getByRole("heading", { name: /Chỉ tiêu chủ đề/ })).toBeVisible();
  });
});

test.describe("MT của BCA: số chùm của Sử (FTM AC 44 b, AC 53, AC 27 phần BCA)", () => {
  test("BCA Sử T = 10: 0 chùm báo \"Ít nhất 1 chùm\" và không lưu; 2 chùm lưu chùm 2 × 3 = 6, nhóm đơn 4, giữ nhãn, Kiểm tra không có SET_SIZE_MISMATCH", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    // Mẫu SUBJECT của BCA tính giờ chung (SHARED): bài tạo phải có `durationMinutes`.
    const exam = await createExam(admin, { kind: "BCA", scope: "SUBJECT", subjectCode: "SU", durationMinutes: 60 }, "BCA-S");
    const before = sectionOf(await getStructure(admin, exam.id), "SU");
    const setBefore = roleBlock(before, "QUESTION_SET")!;
    const singleBefore = roleBlock(before, null)!;

    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${before.id}`);
    await expect(field(page, "Số chùm")).toHaveValue("1");
    await expect(page.getByText("Tham khảo").first()).toBeVisible();
    await expect(page.getByRole("switch", { name: "Áp dụng khi sinh đề" })).toHaveCount(0);

    await typeInto(page, "Số chùm", "0");
    await expect(page.getByText("Ít nhất 1 chùm")).toBeVisible();
    await page.getByRole("button", { name: "Lưu", exact: true }).click();
    expect(roleBlock(sectionOf(await getStructure(admin, exam.id), "SU"), "QUESTION_SET")!.setCount).toBe(setBefore.setCount);
    await expect(field(page, "Số chùm")).toBeFocused();

    await typeInto(page, "Số chùm", "2");
    const singleInput = page.getByRole("textbox", { name: `Số câu của ${singleBefore.name}`, exact: true });
    await singleInput.fill(String((singleBefore.questionCount ?? 0) - 3));
    await saveInUi(page, exam.id);
    const saved = sectionOf(await getStructure(admin, exam.id), "SU");
    expect(roleBlock(saved, "QUESTION_SET")).toMatchObject({ id: setBefore.id, setCount: 2, setSize: 3, questionCount: 6 });
    expect(roleBlock(saved, null)).toMatchObject({ id: singleBefore.id, questionCount: (singleBefore.questionCount ?? 0) - 3 });
    expect(roleBlock(saved, "QUESTION_SET")?.tagCodes ?? []).toEqual(setBefore.tagCodes ?? []);
    expect(await checkSection(admin, exam.id, before.id!)).not.toContain("SET_SIZE_MISMATCH");
  });
});

test.describe("MT của V-ACT: hai cột Số câu / Tỷ lệ (FTM AC 9, AC 11, AC 19, AC 44 i, AC 50, AC 54)", () => {
  test("V-ACT Toán: độ khó 25/50/25% của 30 hiện 8/15/7, lưu PERCENT 25/50/25, tải lại vẫn %; Cấu trúc câu 30%/70% lưu 9/21, chùm cỡ linh hoạt giữ null", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const exam = await createExam(admin, { kind: "VACT", scope: "SUBJECT", subjectCode: "TOAN", durationMinutes: 60 }, "VACT-T");
    const toan = sectionOf(await getStructure(admin, exam.id), "TOAN");
    const singleBlock = roleBlock(toan, null)!;
    const setBlock = roleBlock(toan, "QUESTION_SET")!;

    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${toan.id}`);
    await expect(page.getByLabel("Liên lớp", { exact: true })).toBeVisible();
    const difficulty = page.getByRole("heading", { level: 4, name: "Độ khó" }).locator("xpath=ancestor::*[@data-form-field][1]");
    await difficulty.getByRole("textbox", { name: "Tỷ lệ của Nhận biết", exact: true }).fill("25");
    await difficulty.getByRole("textbox", { name: "Tỷ lệ của Thông hiểu", exact: true }).fill("50");
    await difficulty.getByRole("textbox", { name: "Tỷ lệ của Vận dụng", exact: true }).fill("25");
    await expect(difficulty.getByLabel("Nhận biết", { exact: true })).toHaveValue("8");
    await expect(difficulty.getByLabel("Thông hiểu", { exact: true })).toHaveValue("15");
    await expect(difficulty.getByLabel("Vận dụng", { exact: true })).toHaveValue("7");
    await saveInUi(page, exam.id);
    const saved = sectionOf(await getStructure(admin, exam.id), "TOAN").matrix;
    expect(saved?.unit).toBe("PERCENT");
    expect(saved?.byDifficulty?.map((row) => row.value)).toEqual([25, 50, 25]);

    await page.reload();
    await expect(page.getByRole("textbox", { name: "Tỷ lệ của Nhận biết", exact: true })).toHaveValue("25");

    await page.getByRole("textbox", { name: `Tỷ lệ của ${singleBlock.name}`, exact: true }).fill("30");
    await expect(page.getByText("Tổng tỷ lệ 30%, cần 100%")).toBeVisible();
    await page.getByRole("textbox", { name: `Tỷ lệ của ${setBlock.name}`, exact: true }).fill("70");
    await expect(page.getByRole("textbox", { name: `Số câu của ${singleBlock.name}`, exact: true })).toHaveValue("9");
    await expect(page.getByRole("textbox", { name: `Số câu của ${setBlock.name}`, exact: true })).toHaveValue("21");
    await saveInUi(page, exam.id);
    const set = roleBlock(sectionOf(await getStructure(admin, exam.id), "TOAN"), "QUESTION_SET")!;
    expect(set.questionCount).toBe(21);
    expect(set.setCount ?? null).toBeNull();
    expect(set.setSize ?? null).toBeNull();
  });

  test("V-ACT Tiếng Việt: không có hàng khối lớp khi chưa có số, thêm từ Thêm điều kiện; bảng mạch luôn hiện", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const exam = await createExam(admin, { kind: "VACT", scope: "SUBJECT", subjectCode: "VIETNAMESE", durationMinutes: 60 }, "VACT-V");
    const vn = sectionOf(await getStructure(admin, exam.id), "VIETNAMESE");
    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${vn.id}`);
    await expect(page.getByRole("heading", { level: 3, name: MATRIX_STRUCTURE_HEADING })).toBeVisible();
    await expect(page.getByRole("heading", { level: 4, name: "Khối lớp" })).toHaveCount(0);
    await expect(page.getByText(/Chờ mapping/).first()).toBeVisible();
    await page.getByRole("button", { name: "Thêm điều kiện", exact: true }).first().click();
    await page.getByRole("menuitem", { name: "Khối lớp" }).click();
    await expect(page.getByRole("heading", { level: 4, name: "Khối lớp" })).toBeVisible();
  });
});

const QUESTION_TAG_GROUP_ID = "00000000-0000-7000-8100-000000000008";

test.describe("MT của HSA: Toán, Sử, Địa lí và Quy tắc MAPID (CR-2H AC-2, AC-3, AC-11, AD-98, AD-99)", () => {
  test("hsaMathSaveAndCheck: sửa Điền đáp án Toán MT cập nhật HSA-B answerQuotas và Kiểm tra không báo trùng hay lệch", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const exam = await createExam(admin, { kind: "HSA", scope: "PART", partCode: "TOAN" }, "HSA-T");
    const toan = sectionOf(await getStructure(admin, exam.id), "TOAN");

    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${toan.id}`);
    await expect(page.getByRole("heading", { level: 3, name: MATRIX_STRUCTURE_HEADING })).toBeVisible();
    await expect(page.getByRole("textbox", { name: "Số câu của Trắc nghiệm độc lập", exact: true })).toHaveValue("32");
    await expect(field(page, "Điền đáp án")).toHaveValue("15");
    await expect(field(page, "Số chùm")).toHaveValue("1");
    await expect(page.getByRole("textbox", { name: "Số câu của Chùm cuối", exact: true })).toHaveValue("3");

    await typeInto(page, "Điền đáp án", "12");
    await saveInUi(page, exam.id);
    const savedQuotas = sectionOf(await getStructure(admin, exam.id), "TOAN").answerQuotas;
    expect(savedQuotas?.items).toEqual([
      { answerType: "SINGLE_CHOICE", value: 32 },
      { answerType: "SHORT_ANSWER", value: 12 },
      { answerType: "QUESTION_SET", value: 3 },
    ]);

    await typeInto(page, "Điền đáp án", "15");
    await saveInUi(page, exam.id);
    const codes = await checkSection(admin, exam.id, toan.id);
    expect(codes).not.toContain("ANSWER_QUOTA_DUPLICATE");
    expect(codes).not.toContain("ANSWER_QUOTA_MISMATCH");
  });

  test("hsaHistoryFillZero: HSA Sử giữ nguyên nhóm Điền đáp án qua 1 -> 0 -> tải lại -> 1 -> lưu", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const exam = await createExam(admin, { kind: "HSA", scope: "SUBJECT", subjectCode: "SU" }, "HSA-SU0");
    const initial = await getStructure(admin, exam.id);
    const su = sectionOf(initial, "SU");
    const fillId = crypto.randomUUID();
    const single = roleBlock(su, null)!;
    single.questionCount = (single.questionCount ?? 14) - 1;
    (su.blocks ?? []).push({
      id: fillId,
      name: "Trả lời ngắn",
      questionType: "SHORT_ANSWER",
      questionCount: 1,
      requiredPointsPerQuestion: 1,
      // fit.ts chỉ cho phép tagCodes ở nhóm chùm (SET); nhóm điền đáp án phải để [] để khớp MT.
      tagCodes: [],
    });
    const put = await admin.api.put(`${ADMIN_URL}/api/v1/admin/exams/${exam.id}/structure`, {
      headers: admin.headers,
      data: { parts: initial.parts, examQuotas: initial.examQuotas ?? [], expectedRevision: initial.revision },
    });
    expect(put.status(), await put.text()).toBe(200);
    const beforeSu = sectionOf(await getStructure(admin, exam.id), "SU");
    const beforeUi = roleBlock(beforeSu, "SHORT_ANSWER")!;
    const beforeSingleCode = roleBlock(beforeSu, null)?.templateGroupCode;
    expect(beforeSingleCode).toBeTruthy();

    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${su.id}`);
    await expect(field(page, "Điền đáp án")).toHaveValue("1");
    await typeInto(page, "Điền đáp án", "0");
    await saveInUi(page, exam.id);

    await page.reload();
    await expect(field(page, "Điền đáp án")).toHaveValue("0");
    const afterZeroSu = sectionOf(await getStructure(admin, exam.id), "SU");
    const afterZero = roleBlock(afterZeroSu, "SHORT_ANSWER");
    expect(afterZero).toMatchObject({
      id: fillId,
      name: "Trả lời ngắn",
      questionCount: 0,
      requiredPointsPerQuestion: 1,
      tagCodes: [],
    });
    expect(afterZero?.templateGroupCode ?? null).toBe(beforeUi.templateGroupCode ?? null);
    expect(roleBlock(afterZeroSu, null)?.templateGroupCode).toBe(beforeSingleCode);

    await typeInto(page, "Điền đáp án", "1");
    await saveInUi(page, exam.id);
    const afterRestoreSu = sectionOf(await getStructure(admin, exam.id), "SU");
    const afterRestore = roleBlock(afterRestoreSu, "SHORT_ANSWER");
    expect(afterRestore).toMatchObject({
      id: fillId,
      name: "Trả lời ngắn",
      questionCount: 1,
      requiredPointsPerQuestion: 1,
      tagCodes: [],
    });
    expect(afterRestore?.templateGroupCode ?? null).toBe(beforeUi.templateGroupCode ?? null);
    expect(roleBlock(afterRestoreSu, null)?.templateGroupCode).toBe(beforeSingleCode);
  });

  test("hsaMathRulesInMatrix: HSA Toán MT thêm quy tắc 2 và 5 trong MapidRuleDialog nhúng rồi sinh thử tuân thủ", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();

    async function createNode(parentId: string | null, code: string, name: string, refItemIds: string[]) {
      const res = await admin.api.post(`${ADMIN_URL}/api/v1/admin/catalog/groups/${KNOWLEDGE_GROUP_ID}/items`, {
        headers: admin.headers,
        data: { code, nameVi: name, nameEn: name, ...(parentId ? { parentId } : {}), ...(refItemIds.length ? { refItemIds } : {}) },
      });
      expect(res.status(), await res.text()).toBe(201);
      return ((await res.json()) as { id: string }).id;
    }

    const base = `HSAMT-${RUN_ID}`.toUpperCase();
    const chapter = await createNode(null, `${base}-C`, `Chương MT ${RUN_ID}`, [SUBJECT_ITEM_ID.TOAN!, GRADE_G12_ITEM_ID]);
    const lessonX = await createNode(chapter, `${base}-LX`, `Bài X MT ${RUN_ID}`, []);
    const leafX = await createNode(lessonX, `${base}-KX`, `Kiến thức X MT ${RUN_ID}`, []);
    const lessonA = await createNode(chapter, `${base}-LA`, `Bài A MT ${RUN_ID}`, []);
    const leafA = await createNode(lessonA, `${base}-KA`, `Kiến thức A MT ${RUN_ID}`, []);

    const opt = (label: string, correct: boolean) => ({ id: crypto.randomUUID(), content: `<p>${label}</p>`, correct });
    const mcBody = () => ({ type: "SINGLE_CHOICE", options: [opt("A", true), opt("B", false), opt("C", false), opt("D", false)], shuffleOptions: true });
    const carrierDrafts: QuestionDraft[] = Array.from({ length: 12 }, (_, idx) => ({
      type: idx % 2 === 0 ? "SINGLE_CHOICE" : "SHORT_ANSWER",
      subjectCode: "TOAN",
      gradeCode: "G12",
      difficultyCode: idx % 2 === 0 ? "TH" : "VD",
      points: 1,
      knowledge: [{ nodeId: leafX, primary: true }],
      content: `<p>${UNIT_LABEL}MT X ${RUN_ID} ${idx}.</p>`,
      body: idx % 2 === 0 ? mcBody() : { type: "SHORT_ANSWER", answers: ["1"], inputHint: null, answerFormat: null },
      sourceCode: `HSAMT-${RUN_ID}-X${idx}`.slice(0, 60),
    }));
    const setDrafts: QuestionDraft[] = Array.from({ length: 2 }, (_, idx) => ({
      type: "QUESTION_SET",
      subjectCode: "TOAN",
      gradeCode: "G10",
      difficultyCode: "TH",
      points: 3,
      knowledge: [{ nodeId: leafA, primary: true }],
      content: `<p>${UNIT_LABEL}Chùm MT ${RUN_ID} ${idx}.</p>`,
      body: {
        type: "QUESTION_SET",
        passage: `<p>${UNIT_LABEL}Ngữ liệu MT ${RUN_ID} ${idx}.</p>`,
        blankNumberStart: 1,
        children: [1, 2, 3].map((child) => ({ type: "SINGLE_CHOICE", content: `<p>${UNIT_LABEL}Con ${child}.</p>`, body: mcBody(), points: 1, knowledgeItemIds: [leafA] })),
      },
      sourceCode: `HSAMT-${RUN_ID}-A${idx}`.slice(0, 60),
    }));
    const carriers = await createReadyQuestions(admin, carrierDrafts);
    const sets = await createReadyQuestions(admin, setDrafts);
    const carrierCodes = new Set(carriers.map((q) => q.code));
    const allowedSetCodes = new Set(sets.map((q) => q.code));

    const exam = await createExam(admin, { kind: "HSA", scope: "PART", partCode: "TOAN" }, "HSA-RM");
    const toan = sectionOf(await getStructure(admin, exam.id), "TOAN");

    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${toan.id}`);
    await expect(page.getByRole("heading", { level: 3, name: MATRIX_STRUCTURE_HEADING })).toBeVisible();
    await expect(page.getByRole("button", { name: /Thêm nhóm/ })).toHaveCount(0);

    await page.getByTestId("mapid-mode-matrix").check();
    await expect(page.getByTestId("mapid-rules-table")).toBeVisible();

    const pickMapid = async (code: string) => {
      const dialog = page.getByRole("dialog", { name: /^Thêm quy tắc/ });
      await dialog.getByTestId("mapid-rule-mapid-picker").click();
      const picker = page.getByRole("dialog", { name: /Chọn kiến thức/ });
      await picker.getByPlaceholder(/Tìm theo mã hoặc tên/).fill(code);
      await picker.getByRole("checkbox", { name: new RegExp(`^${code} `) }).check();
      await picker.getByRole("button", { name: /Áp dụng/ }).click();
    };

    await page.getByTestId("mapid-rule-add").click();
    let dialog = page.getByRole("dialog", { name: "Thêm quy tắc" });
    await dialog.getByTestId("mapid-rule-type-COUNT").click();
    await pickMapid(`${base}-KX`);
    await dialog.getByTestId("mapid-rule-comparator").getByRole("radio", { name: "Bằng" }).check();
    await dialog.getByTestId("mapid-rule-count").fill("3");
    await dialog.getByTestId("mapid-rule-save").click();

    await page.getByTestId("mapid-rule-add").click();
    dialog = page.getByRole("dialog", { name: "Thêm quy tắc" });
    await dialog.getByTestId("mapid-rule-scope").getByRole("combobox").click();
    await page.getByRole("option", { name: /^Chùm cuối/ }).click();
    await dialog.getByTestId("mapid-rule-type-SOURCE").click();
    await pickMapid(`${base}-KA`);
    await dialog.getByTestId("mapid-rule-save").click();

    await saveInUi(page, exam.id);
    const previewRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${exam.id}/preview`, { headers: admin.headers, data: { seed: 1, target: { kind: "EXAM" } } });
    expect(previewRes.status(), await previewRes.text()).toBe(200);
    const paper = (await previewRes.json()) as PreviewResultLite;
    expect(paper.status, paper.reason ?? "").toBe("GENERATED");
    expect(paperQuestions(paper).filter((q) => carrierCodes.has(q.code)).length).toBe(3);
    const setItem = paper.paper.parts.flatMap((p) => p.blocks ?? []).find((b) => b.questionType === "QUESTION_SET")?.items?.[0];
    expect(allowedSetCodes.has(setItem?.code ?? "")).toBe(true);
  });

  test("hsaHistoryGeographyAd98Ad99: Sử/Địa đặt Mạch kiến thức trước Cấu trúc câu gọn, đếm phân bổ, khối lớp tùy chọn và Địa lí chọn nhãn tư liệu động", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();

    const suExam = await createExam(admin, { kind: "HSA", scope: "SUBJECT", subjectCode: "SU" }, "HSA-SUA");
    const su = sectionOf(await getStructure(admin, suExam.id), "SU");
    await page.goto(`${ADMIN_URL}/exams/${suExam.id}/structure?section=${su.id}`);
    const suStrand = page.getByTestId("hsa-strand-section");
    const suStruct = page.locator('[data-form-field="STRUCTURE"]');
    await expect(suStrand).toBeVisible();
    await expect(suStruct).toHaveAttribute("data-structure-variant", "compact");
    const suStrandBox = await suStrand.boundingBox();
    const suStructBox = await suStruct.boundingBox();
    expect(suStrandBox && suStructBox && suStrandBox.y < suStructBox.y).toBe(true);

    const tagCode = `DIA_E2E_${RUN_ID}`.toUpperCase();
    const tagName = `Tư liệu Địa ${RUN_ID}`;
    const tagRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/catalog/groups/${QUESTION_TAG_GROUP_ID}/items`, {
      headers: admin.headers,
      data: { code: tagCode, nameVi: tagName, nameEn: `Geography Material ${RUN_ID}` },
    });
    expect(tagRes.status(), await tagRes.text()).toBe(201);

    const exam = await createExam(admin, { kind: "HSA", scope: "SUBJECT", subjectCode: "DIA" }, "HSA-DIA");
    const dia = sectionOf(await getStructure(admin, exam.id), "DIA");

    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${dia.id}`);
    const strand = page.getByTestId("hsa-strand-section");
    const structure = page.locator('[data-form-field="STRUCTURE"]');
    await expect(strand).toBeVisible();
    await expect(structure).toHaveAttribute("data-structure-variant", "compact");
    const strandBox = await strand.boundingBox();
    const structBox = await structure.boundingBox();
    expect(strandBox && structBox && strandBox.y < structBox.y).toBe(true);

    await page.getByTestId("mapid-mode-matrix").check();
    await expect(page.getByTestId("hsa-mapid-allocation")).toContainText(/0 \/ 17.*còn tự do: 17/);

    const gradeField = page.getByRole("heading", { level: 4, name: "Khối lớp" }).locator("xpath=ancestor::*[@data-form-field][1]");
    for (const label of ["Lớp 10", "Lớp 11", "Lớp 12"]) {
      await gradeField.getByLabel(label, { exact: true }).fill("");
    }
    await expect(gradeField.getByText("Chưa áp dụng")).toBeVisible();

    await page.getByTestId("hsa-dia-material-add").click();
    const picker = page.getByTestId("hsa-dia-material-picker");
    await picker.getByRole("checkbox", { name: tagName }).check();
    await saveInUi(page, exam.id);

    const saved = sectionOf(await getStructure(admin, exam.id), "DIA");
    expect(saved.matrix?.byGrade ?? []).toEqual([]);
    expect(roleBlock(saved, "QUESTION_SET")?.tagCodes).toContain(tagCode);
    const checkCodes = await checkSection(admin, exam.id, dia.id);
    expect(checkCodes.some((code) => code.includes("GRADE"))).toBe(false);
    await page.reload();
    await expect(page.getByTestId("hsa-dia-material-picker").getByRole("checkbox", { name: tagName })).toBeChecked();
  });
});
