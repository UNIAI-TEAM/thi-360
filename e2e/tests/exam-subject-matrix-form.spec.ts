import { expect, test, type Page } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin, type AdminApi } from "./helpers/editor-qa";
import { examCode, getStructure, issueCodes, SOLVER_TIMEOUT_MS, type Structure, type StructureBlock, type StructureSection } from "./helpers/wave-2b";

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
  const save = page.getByRole("button", { name: "Lưu", exact: true });
  const [response] = await Promise.all([
    page.waitForResponse((item) => item.url().endsWith(`/exams/${examId}/structure`) && item.request().method() === "PUT", { timeout: SOLVER_TIMEOUT_MS }),
    save.click(),
  ]);
  expect(response.status(), await response.text()).toBe(200);
  await expect(save).toBeDisabled();
}

const field = (page: Page, name: string) => page.getByRole("textbox", { name, exact: true });

async function typeInto(page: Page, name: string, value: string): Promise<void> {
  const input = field(page, name);
  await input.fill(value);
  await expect(input).toHaveValue(value);
}

test.describe("MT của QDA: khung, giới hạn Khoa học, ma trận (FTM AC 35 phần QDA, AC 53, AC 59)", () => {
  test("khung: QDA Khoa học mở MT (Cấu trúc câu, ô, không nút thêm nhóm); `?block=` của nhóm chùm đặt focus vào ô Số chùm", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const exam = await createExam(admin, { kind: "QDA", scope: "PART", partCode: "SCIENCE" }, "QDA-S");
    const structure = await getStructure(admin, exam.id);
    const ly = sectionOf(structure, "LY");
    const set = roleBlock(ly, "QUESTION_SET")!;

    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${ly.id}`);
    await expect(page.getByRole("heading", { level: 3, name: "Cấu trúc câu" })).toBeVisible();
    await expect(field(page, "Số chùm")).toHaveValue("1");
    await expect(page.getByRole("button", { name: /Thêm nhóm/ })).toHaveCount(0);
    await expect(page.getByRole("heading", { level: 3, name: "Khối lớp và độ khó" })).toBeVisible();

    // R1-8: mở thẳng theo `?block=` (môn vẽ sau khi tải xong dữ liệu) vẫn đặt focus đúng ô của nhóm.
    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?block=${set.id}`);
    await expect(page.getByRole("heading", { level: 3, name: "Cấu trúc câu" })).toBeVisible();
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
    await page.getByRole("button", { name: "Lưu", exact: true }).click();
    // Không gửi PUT: cấu trúc ở máy chủ không đổi và nút giữ nguyên trạng thái chưa lưu.
    expect(roleBlock(sectionOf(await getStructure(admin, exam.id), "LY"), "QUESTION_SET")!.setCount).toBe(1);
    await expect(field(page, "Số chùm")).toBeFocused();

    await typeInto(page, "Số chùm", "2");
    await typeInto(page, "Điền đáp án", "0");
    await expect(page.getByText("Cần ít nhất 1 câu điền đáp án")).toBeVisible();
    await page.getByRole("button", { name: "Kiểm tra toàn bài", exact: true }).click();
    expect(roleBlock(sectionOf(await getStructure(admin, exam.id), "LY"), "SHORT_ANSWER")!.questionCount).toBe(fillBefore);

    await typeInto(page, "Điền đáp án", String(fillBefore));
    await saveInUi(page, exam.id);
    const saved = sectionOf(await getStructure(admin, exam.id), "LY");
    expect(roleBlock(saved, "QUESTION_SET")).toMatchObject({ setCount: 2, setSize: 3, questionCount: 6 });
    expect(await checkSection(admin, exam.id, ly.id!)).not.toContain("SET_SIZE_MISMATCH");

    // Sử cho phép 0 câu điền: mở môn không có lỗi giới hạn.
    const su = sectionOf(before, "SU");
    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${su.id}`);
    await expect(page.getByRole("heading", { level: 3, name: "Cấu trúc câu" })).toBeVisible();
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
    await expect(page.getByText(`Đã khai ${target}/${target}`)).toBeVisible();
    await saveInUi(page, exam.id);
    const matrix = sectionOf(await getStructure(admin, exam.id), "LY").matrix;
    expect(matrix?.byDifficulty?.map((row) => [row.code, row.value])).toHaveLength(3);
    expect(await checkSection(admin, exam.id, ly.id!)).not.toContain("SUBJECT_MATRIX_TOTAL_MISMATCH");

    await difficulty.getByLabel("Thông hiểu", { exact: true }).fill("5");
    await expect(page.getByText(`Đã khai ${target - 5}/${target}`)).toBeVisible();
    await saveInUi(page, exam.id);
    expect(await checkSection(admin, exam.id, ly.id!)).toContain("SUBJECT_MATRIX_TOTAL_MISMATCH");
  });

  test("QDA Toán: Phạm vi kiến thức và nút Thêm chỉ tiêu chủ đề; chưa có dòng thì không hiện bảng", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const exam = await createExam(admin, { kind: "QDA", scope: "PART", partCode: "MATH" }, "QDA-T");
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
    const exam = await createExam(admin, { kind: "BCA", scope: "SUBJECT", subjectCode: "SU" }, "BCA-S");
    const before = sectionOf(await getStructure(admin, exam.id), "SU");
    const setBefore = roleBlock(before, "QUESTION_SET")!;
    const singleBefore = roleBlock(before, null)!;

    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${before.id}`);
    await expect(field(page, "Số chùm")).toHaveValue("1");
    await expect(page.getByText("Tham khảo").first()).toBeVisible();
    await expect(page.getByRole("switch")).toHaveCount(0);

    await typeInto(page, "Số chùm", "0");
    await expect(page.getByText("Ít nhất 1 chùm")).toBeVisible();
    await page.getByRole("button", { name: "Lưu", exact: true }).click();
    expect(roleBlock(sectionOf(await getStructure(admin, exam.id), "SU"), "QUESTION_SET")!.setCount).toBe(setBefore.setCount);
    await expect(field(page, "Số chùm")).toBeFocused();

    await typeInto(page, "Số chùm", "2");
    await expect(page.getByRole("textbox", { name: "Trắc nghiệm đơn" })).toHaveValue(String((singleBefore.questionCount ?? 0) - 3));
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
    const exam = await createExam(admin, { kind: "VACT", scope: "SUBJECT", subjectCode: "TOAN" }, "VACT-T");
    const toan = sectionOf(await getStructure(admin, exam.id), "TOAN");

    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${toan.id}`);
    await expect(page.getByLabel("Liên lớp", { exact: true })).toBeVisible();
    const difficulty = page.getByRole("heading", { level: 4, name: "Độ khó" }).locator("xpath=ancestor::*[@data-form-field][1]");
    await difficulty.getByRole("textbox", { name: "Tỷ lệ của Nhận biết" }).fill("25");
    await difficulty.getByRole("textbox", { name: "Tỷ lệ của Thông hiểu" }).fill("50");
    await difficulty.getByRole("textbox", { name: "Tỷ lệ của Vận dụng" }).fill("25");
    await expect(difficulty.getByLabel("Nhận biết", { exact: true })).toHaveValue("8");
    await expect(difficulty.getByLabel("Thông hiểu", { exact: true })).toHaveValue("15");
    await expect(difficulty.getByLabel("Vận dụng", { exact: true })).toHaveValue("7");
    await saveInUi(page, exam.id);
    const saved = sectionOf(await getStructure(admin, exam.id), "TOAN").matrix;
    expect(saved?.unit).toBe("PERCENT");
    expect(saved?.byDifficulty?.map((row) => row.value)).toEqual([25, 50, 25]);

    await page.reload();
    await expect(page.getByRole("textbox", { name: "Tỷ lệ của Nhận biết" })).toHaveValue("25");

    await page.getByRole("textbox", { name: "Tỷ lệ của Trắc nghiệm đơn" }).fill("30");
    await expect(page.getByText("Tổng tỷ lệ 30%, cần 100%")).toBeVisible();
    await page.getByRole("textbox", { name: "Tỷ lệ của Câu con trong chùm" }).fill("70");
    await expect(page.getByLabel("Trắc nghiệm đơn", { exact: true })).toHaveValue("9");
    await expect(page.getByLabel("Câu con trong chùm", { exact: true })).toHaveValue("21");
    await saveInUi(page, exam.id);
    const set = roleBlock(sectionOf(await getStructure(admin, exam.id), "TOAN"), "QUESTION_SET")!;
    expect(set.questionCount).toBe(21);
    expect(set.setCount ?? null).toBeNull();
    expect(set.setSize ?? null).toBeNull();
  });

  test("V-ACT Tiếng Việt: không có hàng khối lớp khi chưa có số, thêm từ Thêm điều kiện; bảng mạch luôn hiện", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const exam = await createExam(admin, { kind: "VACT", scope: "SUBJECT", subjectCode: "VIETNAMESE" }, "VACT-V");
    const vn = sectionOf(await getStructure(admin, exam.id), "VIETNAMESE");
    await page.goto(`${ADMIN_URL}/exams/${exam.id}/structure?section=${vn.id}`);
    await expect(page.getByRole("heading", { level: 3, name: "Cấu trúc câu" })).toBeVisible();
    await expect(page.getByRole("heading", { level: 4, name: "Khối lớp" })).toHaveCount(0);
    await expect(page.getByText(/Chờ mapping/).first()).toBeVisible();
    await page.getByRole("button", { name: "Thêm điều kiện", exact: true }).first().click();
    await page.getByRole("menuitem", { name: "Khối lớp" }).click();
    await expect(page.getByRole("heading", { level: 4, name: "Khối lớp" })).toBeVisible();
  });
});
