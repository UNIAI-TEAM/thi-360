import { expect, test, type Page } from "@playwright/test";
import { adminApi, ADMIN_URL, loginAdmin } from "./helpers/editor-qa";
import {
  createExamInUi,
  examCode,
  getExam,
  getStructure,
  openEditorTab,
  subjectCombobox,
  type StructurePart,
  type UiExamSpec,
} from "./helpers/wave-2b";
import { examByCode } from "./helpers/published-exam-set";

test.setTimeout(240_000);

/** Ô số câu đầu tiên của bảng "Cấu trúc tổng quan" ở tab Thông tin chung. */
const overviewCount = (page: Page) =>
  page.getByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" }).getByRole("textbox", { name: /^Số câu của/ }).first();

/** Số câu của từng khối lấy câu trong phần, theo thứ tự lưu. */
const blockCounts = (part: StructurePart) =>
  (part.sections ?? []).flatMap((section) => (section.blocks ?? []).map((block) => block.questionCount ?? null));

/**
 * R-01 / R-02 (UNI-2776): quay về tab "Thông tin chung" (`/exams/<id>`) từ tab Cấu trúc mở biểu mẫu hợp nhất `ExamGeneralForm`
 * kèm cột Tóm tắt và thẻ Tình trạng, điền sẵn theo bài đã lưu.
 */
async function backToStep1(page: Page, id: string, spec: UiExamSpec): Promise<void> {
  await openEditorTab(page, "Thông tin chung", new RegExp(`/exams/${id}$`));
  await expect(page.getByRole("form", { name: "Thông tin bài thi" })).toBeVisible();
  await expect(
    page.getByRole("navigation", { name: "Các phần của bài thi" }).getByRole("link", { name: "Thông tin chung" }),
  ).toHaveAttribute("aria-current", "page");
  for (const heading of ["Thông tin chung", "Phạm vi bài thi", "Cấu trúc tổng quan"]) {
    await expect(page.getByRole("heading", { name: heading, level: 2 }).first()).toBeVisible();
  }
  await expect(page.getByRole("region", { name: "Tóm tắt" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Tình trạng" })).toBeVisible();
  await expect(page.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue(spec.code);
  await expect(page.getByRole("textbox", { name: /Tên bài thi/ })).toHaveValue(spec.name);
}

/** Từ tab Thông tin chung: đặt số câu của phần đầu rồi "Lưu thông tin" (giữ ở `/exams/<id>`), sau đó sang tab Cấu trúc kiểm chứng. */
async function editFirstCountAndContinue(page: Page, id: string, newCount: number): Promise<void> {
  const admin = await adminApi();
  const before = await getStructure(admin, id);
  const count = overviewCount(page);
  await expect(count).toBeVisible();
  await expect(count).not.toHaveValue(String(newCount));

  await count.fill(String(newCount));
  const [savedStructure] = await Promise.all([
    page.waitForResponse(
      (item) => new URL(item.url()).pathname === `/api/v1/admin/exams/${id}/structure` && item.request().method() === "PUT",
    ),
    page.getByRole("button", { name: "Lưu thông tin", exact: true }).click(),
  ]);
  expect(savedStructure.status(), await savedStructure.text()).toBe(200);
  await expect(page).toHaveURL(new RegExp(`/exams/${id}$`));
  await expect(page.getByRole("button", { name: "Hoàn tác", exact: true }).filter({ hasText: "Hoàn tác" })).toBeDisabled();
  await openEditorTab(page, "Cấu trúc và ma trận", new RegExp(`/exams/${id}/structure$`));

  const after = await getStructure(admin, id);
  expect(after.parts[0]!.questionCount).toBe(newCount);
  // Số câu của khối lấy câu không tự đổi theo số câu của phần: bước 2 báo lệch để người dùng tự chia lại.
  expect(blockCounts(after.parts[0]!)).toEqual(blockCounts(before.parts[0]!));
  expect(after.parts.slice(1).map((part) => part.questionCount)).toEqual(before.parts.slice(1).map((part) => part.questionCount));
}

async function expectStep2Mismatch(page: Page, newCount: number): Promise<void> {
  await expect(page.getByText(new RegExp(`các khối có \\d+ câu, môn cần ${newCount} câu`)).first()).toBeVisible();
}

test("HSA Toàn bài: quay lại bước 1 từ bước 2, sửa số câu phần 1, bước 2 hiện số mới và lệch phân bổ", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = { kind: "HSA", scope: "FULL", code: examCode("E2E-HSA-BACK"), name: "HSA quay lại bước 1 nghiệm thu" };
  const id = await createExamInUi(page, spec);
  await backToStep1(page, id, spec);
  await editFirstCountAndContinue(page, id, 45);
  await expectStep2Mismatch(page, 45);
});

test("HSA Theo phần: quay lại bước 1 điền sẵn phạm vi và phần đã lưu, sửa số câu rồi về bước 2", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = {
    kind: "HSA",
    scope: "FULL",
    scopeLabel: /^Theo phần/,
    code: examCode("E2E-HSA-PBACK"),
    name: "HSA theo phần quay lại bước 1 nghiệm thu",
  };
  const id = await createExamInUi(page, spec);
  await backToStep1(page, id, spec);
  await expect(page.getByRole("radio", { name: /^Theo phần/ })).toBeChecked();
  const current = Number(await overviewCount(page).inputValue());
  await editFirstCountAndContinue(page, id, current + 1);
});

test("HSA Theo môn + Tự thiết lập: quay lại bước 1, cách dựng bài hiện đúng, sửa số câu rồi về bước 2", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = {
    kind: "HSA",
    scope: "SUBJECT",
    subject: "TOAN",
    review: true,
    code: examCode("E2E-HSA-RBACK"),
    name: "HSA tự thiết lập quay lại bước 1 nghiệm thu",
    overview: { questionCount: 10, durationMinutes: 15, maxScore: 10 },
  };
  const id = await createExamInUi(page, spec);
  await backToStep1(page, id, spec);
  await expect(page.getByRole("radio", { name: /^Tự thiết lập bài ôn/ })).toBeChecked();
  await editFirstCountAndContinue(page, id, 12);
});

test("THPTQG Toán: cùng đường quay lại bước 1, sửa số câu phần đầu", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = {
    kind: "THPTQG",
    scope: "SUBJECT",
    subject: "TOAN",
    code: examCode("E2E-TQG-BACK"),
    name: "THPTQG Toán quay lại bước 1 nghiệm thu",
  };
  const id = await createExamInUi(page, spec);
  await backToStep1(page, id, spec);
  await editFirstCountAndContinue(page, id, 7);
  await expectStep2Mismatch(page, 7);
});

test("HSA Toàn bài: đổi sang Theo môn ở bước 1 hỏi nạp lại cấu trúc, xác nhận xong bước 2 hiện cấu trúc mới", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = { kind: "HSA", scope: "FULL", code: examCode("E2E-HSA-SBACK"), name: "HSA đổi phạm vi ở bước 1 nghiệm thu" };
  const id = await createExamInUi(page, spec);
  await backToStep1(page, id, spec);

  await page.getByRole("radio", { name: /^Theo môn/ }).check();
  await subjectCombobox(page).click();
  await page.getByRole("option", { name: "Toán học", exact: true }).click();
  await page.getByRole("button", { name: "Lưu thông tin", exact: true }).click();

  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  // Chưa xác nhận thì chưa đổi gì ở máy chủ.
  expect((await getExam(await adminApi(), id)).scope).toBe("FULL");
  const [reloaded] = await Promise.all([
    page.waitForResponse((item) => new URL(item.url()).pathname === `/api/v1/admin/exams/${id}` && item.request().method() === "PATCH"),
    dialog.getByRole("button", { name: "Nạp lại cấu trúc" }).click(),
  ]);
  expect(reloaded.status(), await reloaded.text()).toBe(200);
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("button", { name: "Hoàn tác", exact: true }).filter({ hasText: "Hoàn tác" })).toBeDisabled();
  await openEditorTab(page, "Cấu trúc và ma trận", new RegExp(`/exams/${id}/structure$`));

  const exam = await getExam(await adminApi(), id);
  expect(exam.scope).toBe("SUBJECT");
  expect(exam.templateCode).toBe("HSA_SUBJECT_TOAN");
  await expect(page.getByRole("navigation", { name: "Cấu trúc" })).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: /^Môn Toán học/ })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Cấu trúc" }).getByText("Ngôn ngữ – Văn học")).toHaveCount(0);
});

interface ExamListBody {
  items: Array<{ id: string; code: string }>;
}

interface ApiErrorBody {
  code?: string;
}

/** Cách dựng bài đọc từ `GET /exams/{id}`; `CreatedExam` của helper chưa mang trường này. */
async function buildModeOf(id: string): Promise<string | undefined> {
  const response = await (await adminApi()).api.get(`${ADMIN_URL}/api/v1/admin/exams/${id}`);
  expect(response.status(), await response.text()).toBe(200);
  const body: { buildMode?: string } = await response.json();
  return body.buildMode;
}

/** Tab Thông tin chung mở lại, đổi cách dựng bài rồi "Lưu thông tin": hộp cảnh báo hiện trước khi gửi gì; trả hộp để kịch bản xác nhận hay hủy. */
async function changeBuildModeAndContinue(page: Page, mode: RegExp) {
  await page.getByRole("radio", { name: mode }).check();
  await page.getByRole("button", { name: "Lưu thông tin", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await expect(
    dialog.getByText("Đổi cách dựng bài sẽ tạo lại cấu trúc như đề mới. Cấu trúc, ma trận và nhóm lấy câu hiện tại sẽ bị thay."),
  ).toBeVisible();
  return dialog;
}

test("AC-1697-5a HSA Theo môn: đổi sang Tự thiết lập bài ôn cảnh báo, xác nhận xong bước 2 hiện bảng Tự thiết lập mới", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = { kind: "HSA", scope: "SUBJECT", subject: "TOAN", code: examCode("E2E-HSA-MBACK"), name: "HSA đổi cách dựng bài nghiệm thu" };
  const id = await createExamInUi(page, spec);
  expect(await buildModeOf(id)).toBe("STANDARD");
  await backToStep1(page, id, spec);

  const dialog = await changeBuildModeAndContinue(page, /^Tự thiết lập bài ôn/);
  // Chưa xác nhận thì chưa đổi gì ở máy chủ.
  expect(await buildModeOf(id)).toBe("STANDARD");
  const [recreated] = await Promise.all([
    page.waitForResponse((item) => new URL(item.url()).pathname === `/api/v1/admin/exams/${id}` && item.request().method() === "PATCH"),
    dialog.getByRole("button", { name: "Tạo lại cấu trúc" }).click(),
  ]);
  expect(recreated.status(), await recreated.text()).toBe(200);
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("button", { name: "Hoàn tác", exact: true }).filter({ hasText: "Hoàn tác" })).toBeDisabled();
  await openEditorTab(page, "Cấu trúc và ma trận", new RegExp(`/exams/${id}/structure$`));

  await expect(page.getByRole("heading", { level: 2, name: /^Bài ôn · Toán học/ })).toBeVisible();
  await expect(page.getByText("Số câu, thời lượng và thang điểm lấy từ bước 1. Bài ôn có một phần, một môn, một nhóm.")).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: /^Môn Toán học/ })).toHaveCount(0);

  const exam = await getExam(await adminApi(), id);
  expect(await buildModeOf(id)).toBe("REVIEW");
  expect(exam.templateCode).toBe("HSA_REVIEW");
  // Cùng bài (mã, tên giữ nguyên); cấu trúc là mặc định của bài ôn: một phần.
  expect(exam.code).toBe(spec.code);
  expect((await getStructure(await adminApi(), id)).parts).toHaveLength(1);
});

test("AC-1697-5a HSA Theo môn + Tự thiết lập: đổi lại sang cấu trúc môn nạp cấu trúc mặc định của môn", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = {
    kind: "HSA",
    scope: "SUBJECT",
    subject: "TOAN",
    review: true,
    code: examCode("E2E-HSA-MBACK2"),
    name: "HSA đổi lại cách dựng bài nghiệm thu",
    overview: { questionCount: 10, durationMinutes: 15, maxScore: 10 },
  };
  const id = await createExamInUi(page, spec);
  expect(await buildModeOf(id)).toBe("REVIEW");
  await backToStep1(page, id, spec);

  const dialog = await changeBuildModeAndContinue(page, /^Dùng cấu trúc môn/);
  const [recreated] = await Promise.all([
    page.waitForResponse((item) => new URL(item.url()).pathname === `/api/v1/admin/exams/${id}` && item.request().method() === "PATCH"),
    dialog.getByRole("button", { name: "Tạo lại cấu trúc" }).click(),
  ]);
  expect(recreated.status(), await recreated.text()).toBe(200);
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("button", { name: "Hoàn tác", exact: true }).filter({ hasText: "Hoàn tác" })).toBeDisabled();
  await openEditorTab(page, "Cấu trúc và ma trận", new RegExp(`/exams/${id}/structure$`));

  await expect(page.getByRole("navigation", { name: "Cấu trúc" })).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: /^Môn Toán học/ })).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: /^Bài ôn · Toán học/ })).toHaveCount(0);

  const exam = await getExam(await adminApi(), id);
  expect(await buildModeOf(id)).toBe("STANDARD");
  expect(exam.templateCode).toBe("HSA_SUBJECT_TOAN");
  expect(exam.questionCount).toBeGreaterThan(10);
});

test("AC-1697-5b HSA Theo môn: Quay lại ở hộp cảnh báo không lưu gì, màn vẫn ở tab Thông tin chung và cách dựng bài giữ nguyên", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = { kind: "HSA", scope: "SUBJECT", subject: "TOAN", code: examCode("E2E-HSA-MCANCEL"), name: "HSA hủy đổi cách dựng bài nghiệm thu" };
  const id = await createExamInUi(page, spec);
  const before = await getExam(await adminApi(), id);
  await backToStep1(page, id, spec);

  const dialog = await changeBuildModeAndContinue(page, /^Tự thiết lập bài ôn/);
  await dialog.getByRole("button", { name: "Quay lại" }).click();
  await expect(dialog).toBeHidden();
  await expect(page).toHaveURL(new RegExp(`/exams/${id}$`));

  const after = await getExam(await adminApi(), id);
  expect(await buildModeOf(id)).toBe("STANDARD");
  expect(after.revision).toBe(before.revision);
  expect(after.templateCode).toBe(before.templateCode);
});

test("AC-1697-5c bài đã công bố: /exams/new?exam=<id> chuyển hướng về /exams/<id>, cách dựng bài chỉ đọc kèm lý do, máy chủ từ chối đổi trực tiếp bằng API", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const published = examByCode("DS-HSA-SUBJECT-TOAN");
  const list = await admin.api.get(`${ADMIN_URL}/api/v1/admin/exams?q=${encodeURIComponent(published.code)}&size=10`);
  expect(list.status(), await list.text()).toBe(200);
  const listed: ExamListBody = await list.json();
  const found = listed.items.find((item) => item.code === published.code);
  expect(found, `bài đã công bố ${published.code} không có trong danh sách`).toBeDefined();
  const id = found!.id;
  const exam = await getExam(admin, id);
  expect(exam.servingVersion ?? null).not.toBeNull();

  await page.goto(`${ADMIN_URL}/exams/new?exam=${id}`);
  await expect(page).toHaveURL(new RegExp(`/exams/${id}$`));
  await expect(page.getByRole("form", { name: "Thông tin bài thi" })).toBeVisible();
  const buildModeGroup = page.getByRole("group", { name: /^Cách dựng bài/ });
  // Chromium không xem `<fieldset disabled>` là disabled trên `role="group"`, nên kiểm cả thuộc tính lẫn từng radio bên trong.
  await expect(buildModeGroup).toHaveAttribute("disabled");
  await expect(buildModeGroup.getByRole("radio")).toHaveCount(2);
  await expect(buildModeGroup.getByRole("radio", { name: /^Dùng cấu trúc môn HSA/ })).toBeDisabled();
  await expect(buildModeGroup.getByRole("radio", { name: /^Tự thiết lập bài ôn/ })).toBeDisabled();
  await expect(page.getByText("Bài đã có phiên bản công bố; muốn đổi cách dựng bài hãy tạo bài mới")).toBeVisible();

  const refused = await admin.api.patch(`${ADMIN_URL}/api/v1/admin/exams/${id}`, {
    headers: admin.headers,
    data: { buildMode: "REVIEW", confirmReplace: true, expectedRevision: exam.revision },
  });
  expect(refused.status(), await refused.text()).toBe(409);
  const refusal: ApiErrorBody = await refused.json();
  expect(refusal.code).toBe("EXAM_SCOPE_LOCKED");
  expect(await buildModeOf(id)).toBe("STANDARD");
});

test("tab Thông tin chung của bài đã lưu hợp nhất: hiển thị cả Tóm tắt, Tình trạng, Hoàn tác và Lưu thông tin", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec: UiExamSpec = { kind: "HSA", scope: "FULL", code: examCode("E2E-HSA-GENERAL"), name: "HSA tab thông tin chung nghiệm thu" };
  const id = await createExamInUi(page, spec);
  await page.goto(`${ADMIN_URL}/exams/${id}`);
  await expect(page.getByRole("region", { name: "Tóm tắt" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Tình trạng" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Hoàn tác", exact: true }).filter({ hasText: "Hoàn tác" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Lưu thông tin", exact: true })).toBeVisible();
});
