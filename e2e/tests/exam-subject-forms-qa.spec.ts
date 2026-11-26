import { expect, test } from "@playwright/test";
import { ADMIN_URL, adminApi, createKnowledgeTree, loginAdmin, RUN_ID, type AdminApi } from "./helpers/editor-qa";
import {
  allSections,
  collectIssues,
  examCode,
  getStructure,
  SOLVER_TIMEOUT_MS,
  type StructureBlock,
  type StructurePart,
  type StructureSection,
} from "./helpers/wave-2b";

test.setTimeout(SOLVER_TIMEOUT_MS);

/**
 * Nghiệm thu bổ sung CR-5 (UNI-2766) cho biểu mẫu theo môn (FTM):
 *  1) R-2: cặp chuẩn không khớp lùi về MD kèm FullFormNotice, ghim hết phiên dù đã sửa khớp, lưu và tải lại mới về MT;
 *     giữ nguyên gradeEnforcement REFERENCE của HSA Tiếng Anh khi mở DS và lưu.
 *  2) R-12 sống: QDA Lý 3 chùm → SECTION_SET_COUNT_RANGE và HSA Hóa 0 điền → SHORT_ANSWER_MIN hiện đúng câu ở /review
 *     (không rơi về INFO_MISSING), điều hướng về /structure; HSA Sử 0 điền không lỗi; hộp Công bố cảnh báo NEEDS_RECHECK và POST /publish trả 409 CHECK_REQUIRED.
 *  3) DS đặc thù: THPTQG Văn chỉ đọc không có nút Sửa vs BCA Văn sửa MAPID trên dòng tự luận; V-SAT Toán 67 lệnh
 *     PROMPT/REFERENCE khóa công tắc áp dụng còn Phần II bật Áp dụng khi sinh đề và giữ sau tải lại.
 *  4) Sau FU #155 (V202611200710/0712/0713): GET /api/v1/admin/exam-templates khớp BCA REFERENCE, REVIEW UUID + Bài ôn,
 *     75 cặp chuẩn + 4 bí danh, và SPT GDKTPL mở DS giữ nguyên mã `GDKTPL` qua PUT/GET.
 */

const FULL_FORM_NOTICE_TEXT = "Môn này đang dùng thiết lập đầy đủ vì có thiết lập mà biểu mẫu gọn không hiển thị được.";
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const CANONICAL_PAIRS: ReadonlySet<string> = new Set([
  // HSA, QDA, BCA (mỗi kỳ 6 MT + 2 DS = 8)
  ...["HSA", "QDA", "BCA"].flatMap((kind) =>
    ["TOAN", "LY", "HOA", "SINH", "SU", "DIA", "VAN", "ANH"].map((subj) => `${kind}:${subj}`),
  ),
  // V-ACT (2 MT + 2 DS)
  "VACT:TOAN",
  "VACT:VIETNAMESE",
  "VACT:ANH",
  "VACT:SCIENTIFIC_THINKING",
  // SP2E (9 DS)
  ...["TOAN", "VAN", "ANH", "LY", "HOA", "SINH", "SU", "DIA", "ECON_LAW"].map((subj) => `SP2E:${subj}`),
  // SPT (12 DS)
  ...["TOAN", "VAN", "ANH", "LY", "HOA", "SINH", "SU", "DIA", "ECON_LAW", "INFORMATICS", "TECH_INDUSTRY", "TECH_AGRICULTURE"].map(
    (subj) => `SPT:${subj}`,
  ),
  // THPTQG (12 DS)
  ...["TOAN", "VAN", "ANH", "LY", "HOA", "SINH", "SU", "DIA", "ECON_LAW", "INFORMATICS", "TECH_INDUSTRY", "TECH_AGRICULTURE"].map(
    (subj) => `THPTQG:${subj}`,
  ),
  // HSCA (6 DS)
  ...["TOAN", "VAN", "ANH", "LY", "HOA", "SINH"].map((subj) => `HSCA:${subj}`),
  // VSAT (8 DS)
  ...["TOAN", "VAN", "ANH", "LY", "HOA", "SINH", "SU", "DIA"].map((subj) => `VSAT:${subj}`),
]);

const SUBJECT_ALIASES: Record<string, Readonly<Record<string, string>>> = {
  SP2E: { GDKTPL: "ECON_LAW" },
  SPT: { TIN: "INFORMATICS", GDKTPL: "ECON_LAW", CNCN: "TECH_INDUSTRY", CNNN: "TECH_AGRICULTURE" },
  THPTQG: { TIN: "INFORMATICS", GDKTPL: "ECON_LAW", CNCN: "TECH_INDUSTRY", CNNN: "TECH_AGRICULTURE" },
};

function resolveCanonicalSubject(kindCode: string, subjectCode: string): string {
  return SUBJECT_ALIASES[kindCode]?.[subjectCode] ?? subjectCode;
}

async function createApiExam(admin: AdminApi, data: Record<string, unknown>): Promise<string> {
  const created = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data,
  });
  expect(created.status(), await created.text()).toBe(201);
  return ((await created.json()) as { id: string }).id;
}

async function putStructure(
  admin: AdminApi,
  examId: string,
  parts: StructurePart[],
  examQuotas: unknown,
  expectedRevision: number,
): Promise<{ revision: number; parts: StructurePart[]; examQuotas?: unknown }> {
  const response = await admin.api.put(`${ADMIN_URL}/api/v1/admin/exams/${examId}/structure`, {
    headers: admin.headers,
    data: { parts, examQuotas: examQuotas ?? [], expectedRevision },
  });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()) as { revision: number; parts: StructurePart[]; examQuotas?: unknown };
}

function findBlock(section: StructureSection, questionType: string): StructureBlock {
  const found = (section.blocks ?? []).find((block) => block.questionType === questionType);
  if (!found) throw new Error(`Missing block ${questionType} in section ${section.id}`);
  return found;
}

function collectSectionsFromParts(parts: StructurePart[]): StructureSection[] {
  const out: StructureSection[] = [];
  for (const part of parts) {
    out.push(...(part.sections ?? []));
    out.push(...(part.subjectPool ?? []));
    for (const option of part.options ?? []) out.push(...(option.sections ?? []));
  }
  return out;
}

interface TemplateWire {
  code: string;
  kindCode: string;
  buildMode?: string;
  active?: boolean;
  structure?: { parts?: StructurePart[] };
}

test("R-2 lùi MD có thông báo, ghim hết phiên, tải lại về MT và giữ Tham khảo cho HSA Anh (AC-5, AC-56)", async ({
  context,
  page,
}) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();

  // Dựng QDA Lý với 3 chùm (LIMITS_VIOLATED): tải lần đầu phải mở MD kèm FullFormNotice.
  const qdaId = await createApiExam(admin, {
    kind: "QDA",
    scope: "SUBJECT",
    subjectCode: "LY",
    code: examCode("CR5-R2-LY"),
    name: `CR5 R2 Ly ${RUN_ID}`,
  });
  const initial = await getStructure(admin, qdaId);
  const editedParts = structuredClone(initial.parts);
  const ly = allSections({ ...initial, parts: editedParts })[0]!;
  const setBlock = findBlock(ly, "QUESTION_SET");
  const singleBlock = findBlock(ly, "SINGLE_CHOICE");
  const origSetName = setBlock.name ?? "Câu chùm";
  const origSingleName = singleBlock.name ?? "Trắc nghiệm";
  const setDelta = 3 * 3 - (setBlock.questionCount ?? 3);
  setBlock.setCount = 3;
  setBlock.setSize = 3;
  setBlock.questionCount = 9;
  singleBlock.questionCount = (singleBlock.questionCount ?? 21) - setDelta;
  await putStructure(admin, qdaId, editedParts, initial.examQuotas, initial.revision);

  await page.goto(`${ADMIN_URL}/exams/${qdaId}/structure`);
  const notice = page.getByTestId("full-form-notice");
  await expect(notice).toBeVisible();
  await expect(notice).toHaveAttribute("data-reason", "LIMITS_VIOLATED");
  await expect(notice).toContainText(FULL_FORM_NOTICE_TEXT);
  await expect(page.locator('[data-form-kind="MATRIX"]')).toHaveCount(0);

  // Sửa trong MD về lại 2 chùm (đã khớp MT): trong phiên vẫn phải ghim ở MD, không tự bật sang MT.
  await page.getByRole("button", { name: `Mở nhóm ${origSetName}` }).click();
  await page.getByRole("textbox", { name: "Số chùm" }).fill("2");
  await page.getByRole("button", { name: "Về màn môn" }).click();

  await page.getByRole("button", { name: `Mở nhóm ${origSingleName}` }).click();
  await page.getByRole("textbox", { name: /^Số câu cần lấy/ }).fill(String((singleBlock.questionCount ?? 15) + 3));
  await page.getByRole("button", { name: "Về màn môn" }).click();

  await expect(notice).toBeVisible();
  await expect(page.locator('[data-form-kind="MATRIX"]')).toHaveCount(0);

  const qdaReturnUrl = page.url();
  const [saveRes] = await Promise.all([
    page.waitForResponse((res) => res.url().endsWith(`/exams/${qdaId}/structure`) && res.request().method() === "PUT"),
    page.getByRole("button", { name: "Tiếp tục → Thông tin bán", exact: true }).click(),
  ]);
  expect(saveRes.status(), await saveRes.text()).toBe(200);
  await page.waitForURL(/\/exams\/[^/]+\/listing/);

  // Sau tải lại trang bước 2: giờ đã khớp MT nên mở MT và mất dòng thông báo MD.
  await page.goto(qdaReturnUrl);
  await expect(page.locator('[data-form-kind="MATRIX"]')).toBeVisible();
  await expect(page.getByTestId("full-form-notice")).toHaveCount(0);
  await expect(page.getByRole("textbox", { name: /^Số chùm của / })).toHaveValue("2");

  // AC-56: HSA Tiếng Anh dùng DS; ma trận lớp tham khảo chờ chuyên gia giữ `REFERENCE` khi mở và lưu.
  const hsaAnhId = await createApiExam(admin, {
    kind: "HSA",
    scope: "SUBJECT",
    subjectCode: "ANH",
    code: examCode("CR5-HSA-ANH"),
    name: `CR5 HSA Anh ${RUN_ID}`,
  });
  const anhBefore = await getStructure(admin, hsaAnhId);
  const anhSectionBefore = allSections(anhBefore)[0]!;
  expect(anhSectionBefore.matrix?.gradeEnforcement).toBe("REFERENCE");

  await page.goto(`${ADMIN_URL}/exams/${hsaAnhId}/structure`);
  await expect(page.locator('[data-subject-form="ROWS"]')).toBeVisible();
  await page.locator("[data-form-row]").first().getByRole("button", { name: /^Sửa dòng/ }).click();
  const rowEditor = page.locator("[data-form-row-editor]");
  await expect(rowEditor).toBeVisible();
  const nameInput = rowEditor.getByRole("textbox", { name: "Tên nhóm" });
  const currentRowName = await nameInput.inputValue();
  await nameInput.fill(`${currentRowName} QA`);
  const [anhSaveRes] = await Promise.all([
    page.waitForResponse((res) => res.url().endsWith(`/exams/${hsaAnhId}/structure`) && res.request().method() === "PUT"),
    page.getByRole("button", { name: "Tiếp tục → Thông tin bán", exact: true }).click(),
  ]);
  expect(anhSaveRes.status(), await anhSaveRes.text()).toBe(200);
  await page.waitForURL(/\/exams\/[^/]+\/listing/);
  const anhAfter = await getStructure(admin, hsaAnhId);
  expect(allSections(anhAfter)[0]!.blocks?.[0]?.name).toBe(`${currentRowName} QA`);
  expect(allSections(anhAfter)[0]!.matrix?.gradeEnforcement).toBe("REFERENCE");
});

test("R-12 máy chủ (SECTION_SET_COUNT_RANGE, SHORT_ANSWER_MIN), Kiểm tra thật và 409 CHECK_REQUIRED khi công bố (AC-36, AC-37, AC-39, AC-60)", async ({
  context,
  page,
}) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();

  // (a) QDA Lý 3 chùm lưu thẳng qua PUT /structure → Kiểm tra thật báo SECTION_SET_COUNT_RANGE ở Lý và dẫn về /structure.
  const qdaId = await createApiExam(admin, {
    kind: "QDA",
    scope: "SUBJECT",
    subjectCode: "LY",
    code: examCode("CR5-R12-QDA"),
    name: `CR5 R12 QDA Ly ${RUN_ID}`,
  });
  const qdaStruct = await getStructure(admin, qdaId);
  const qdaParts = structuredClone(qdaStruct.parts);
  const lySec = allSections({ ...qdaStruct, parts: qdaParts })[0]!;
  const lySet = findBlock(lySec, "QUESTION_SET");
  const lySingle = findBlock(lySec, "SINGLE_CHOICE");
  const delta = 9 - (lySet.questionCount ?? 3);
  lySet.setCount = 3;
  lySet.setSize = 3;
  lySet.questionCount = 9;
  lySingle.questionCount = (lySingle.questionCount ?? 21) - delta;
  await putStructure(admin, qdaId, qdaParts, qdaStruct.examQuotas, qdaStruct.revision);

  await page.goto(`${ADMIN_URL}/exams/${qdaId}/review`);
  const [qdaCheckRes] = await Promise.all([
    page.waitForResponse((res) => res.url().endsWith(`/exams/${qdaId}/check`) && res.request().method() === "POST", {
      timeout: SOLVER_TIMEOUT_MS,
    }),
    page.getByRole("button", { name: "Kiểm tra cấu hình" }).click(),
  ]);
  expect(qdaCheckRes.status()).toBe(200);
  const qdaCheck = await qdaCheckRes.json();
  expect(qdaCheck.status).toBe("INVALID");
  expect(collectIssues(qdaCheck)).toEqual(
    expect.arrayContaining([expect.objectContaining({ code: "SECTION_SET_COUNT_RANGE", subjectCode: "LY" })]),
  );

  const qdaErrors = page.getByTestId("findings-errors");
  await expect(qdaErrors).toBeVisible();
  await expect(qdaErrors).toContainText("Môn Vật lí: số chùm phải từ 1 đến 2 và mỗi chùm có 3 câu con.");
  await expect(qdaErrors).not.toContainText("Thiếu thông tin bắt buộc");
  await qdaErrors.getByRole("link", { name: /số chùm phải từ 1 đến 2/ }).click();
  await page.waitForURL(new RegExp(`/exams/${qdaId}/structure`));
  await expect(page.getByTestId("full-form-notice")).toHaveAttribute("data-reason", "LIMITS_VIOLATED");

  // (b) HSA Hóa 0 điền đáp án → SHORT_ANSWER_MIN ở Hóa; HSA Sử 0 điền không mắc hai lỗi R-12.
  const hoaId = await createApiExam(admin, {
    kind: "HSA",
    scope: "SUBJECT",
    subjectCode: "HOA",
    code: examCode("CR5-R12-HOA"),
    name: `CR5 R12 HSA Hoa ${RUN_ID}`,
  });
  const hoaStruct = await getStructure(admin, hoaId);
  const hoaParts = structuredClone(hoaStruct.parts);
  const hoaSec = allSections({ ...hoaStruct, parts: hoaParts })[0]!;
  findBlock(hoaSec, "SHORT_ANSWER").questionType = "SINGLE_CHOICE";
  if (hoaSec.answerQuotas?.items) {
    const shortItem = hoaSec.answerQuotas.items.find((item) => item.answerType === "SHORT_ANSWER");
    const singleItem = hoaSec.answerQuotas.items.find((item) => item.answerType === "SINGLE_CHOICE");
    if (shortItem && singleItem && typeof shortItem.value === "number" && typeof singleItem.value === "number") {
      singleItem.value += shortItem.value;
    }
    hoaSec.answerQuotas.items = hoaSec.answerQuotas.items.filter((item) => item.answerType !== "SHORT_ANSWER");
  }
  await putStructure(admin, hoaId, hoaParts, hoaStruct.examQuotas, hoaStruct.revision);

  await page.goto(`${ADMIN_URL}/exams/${hoaId}/review`);
  const [hoaCheckRes] = await Promise.all([
    page.waitForResponse((res) => res.url().endsWith(`/exams/${hoaId}/check`) && res.request().method() === "POST", {
      timeout: SOLVER_TIMEOUT_MS,
    }),
    page.getByRole("button", { name: "Kiểm tra cấu hình" }).click(),
  ]);
  expect(hoaCheckRes.status()).toBe(200);
  const hoaCheck = await hoaCheckRes.json();
  expect(collectIssues(hoaCheck)).toEqual(
    expect.arrayContaining([expect.objectContaining({ code: "SHORT_ANSWER_MIN", subjectCode: "HOA" })]),
  );

  const hoaErrors = page.getByTestId("findings-errors");
  await expect(hoaErrors).toContainText("Môn Hóa học: cần ít nhất 1 câu Điền đáp án (loại Trả lời ngắn).");
  await expect(hoaErrors).not.toContainText("Thiếu thông tin bắt buộc");

  const suId = await createApiExam(admin, {
    kind: "HSA",
    scope: "SUBJECT",
    subjectCode: "SU",
    code: examCode("CR5-R12-SU"),
    name: `CR5 R12 HSA Su ${RUN_ID}`,
  });
  const suCheckRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${suId}/check`, {
    headers: admin.headers,
    data: { scope: "EXAM" },
    timeout: SOLVER_TIMEOUT_MS,
  });
  expect(suCheckRes.status()).toBe(200);
  const suCodes = collectIssues(await suCheckRes.json()).map((item) => item.code);
  expect(suCodes).not.toContain("SECTION_SET_COUNT_RANGE");
  expect(suCodes).not.toContain("SHORT_ANSWER_MIN");

  // AC-60: Hộp Công bố hiện cảnh báo cần kiểm tra lại trên UI (NEEDS_RECHECK) và POST /publish trực tiếp trả 409 CHECK_REQUIRED (STRUCTURE_CHANGED).
  const vsatId = await createApiExam(admin, {
    kind: "VSAT",
    scope: "SUBJECT",
    subjectCode: "TOAN",
    code: examCode("CR5-409-CHK"),
    name: `CR5 Publish 409 ${RUN_ID}`,
  });
  const validCheckRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${vsatId}/check`, {
    headers: admin.headers,
    data: { scope: "EXAM" },
    timeout: SOLVER_TIMEOUT_MS,
  });
  expect(validCheckRes.status()).toBe(200);
  const validCheck = (await validCheckRes.json()) as { status: string; paths: Array<{ pathId: string }> };
  expect(validCheck.status).toBe("VALID");

  const vsatStruct = await getStructure(admin, vsatId);
  const vsatParts = structuredClone(vsatStruct.parts);
  const firstBlock = allSections({ ...vsatStruct, parts: vsatParts })[0]!.blocks![0]!;
  firstBlock.name = `${firstBlock.name ?? "Block"} QA`;
  const updatedVsat = await putStructure(admin, vsatId, vsatParts, vsatStruct.examQuotas, vsatStruct.revision);

  await page.goto(`${ADMIN_URL}/exams/${vsatId}/versions`);
  await page.getByRole("button", { name: "Công bố", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText("Cần kiểm tra cấu hình trên nguồn câu hỏi hiện tại trước khi công bố.");
  await expect(dialog.getByRole("link", { name: "Đến Kiểm tra cấu hình" })).toHaveAttribute(
    "href",
    `/exams/${vsatId}/review`,
  );
  await dialog.getByRole("button", { name: "Hủy", exact: true }).click();
  await expect(dialog).toBeHidden();

  const publishRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${vsatId}/publish`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data: {
      pathIds: validCheck.paths.map((path) => path.pathId),
      expectedRevision: updatedVsat.revision,
      note: "CR-5 check",
    },
    timeout: SOLVER_TIMEOUT_MS,
  });
  expect(publishRes.status(), await publishRes.text()).toBe(409);
  const publishBody = (await publishRes.json()) as {
    code?: string;
    fieldErrors?: Array<{ field?: string; code?: string; message?: string }>;
  };
  expect(publishBody.code).toBe("CHECK_REQUIRED");
  expect(publishBody.fieldErrors).toEqual(
    expect.arrayContaining([expect.objectContaining({ field: "reason", code: "STRUCTURE_CHANGED" })]),
  );
});

test("DS đặc thù sống: THPTQG Văn chỉ đọc vs BCA Văn sửa MAPID, và V-SAT Toán 67 lệnh khóa áp dụng + Phần II bật Bắt buộc (AC-24, AC-28)", async ({
  context,
  page,
}) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();

  // AC-24 (a): THPTQG Văn chỉ đọc trên DS, không có ô/nút Sửa dòng.
  const thptVanId = await createApiExam(admin, {
    kind: "THPTQG",
    scope: "SUBJECT",
    subjectCode: "VAN",
    code: examCode("CR5-THPT-VAN"),
    name: `CR5 THPTQG Van ${RUN_ID}`,
  });
  await page.goto(`${ADMIN_URL}/exams/${thptVanId}/structure`);
  const thptRows = page.locator('[data-subject-form="ROWS"]');
  await expect(thptRows).toBeVisible();
  await expect(thptRows.locator("[data-read-only-notice]")).toHaveText("Tự luận – tạm bỏ qua. Phần này không có ô sửa.");
  await expect(thptRows.getByRole("button", { name: /^Sửa dòng/ })).toHaveCount(0);

  // AC-24 (b): BCA Văn 1 dòng tự luận mở sửa được và gán MAPID thật qua KnowledgePickerDialog.
  const vanTree = await createKnowledgeTree(admin, "VAN", "CR5BCAVAN");
  const bcaVanId = await createApiExam(admin, {
    kind: "BCA",
    scope: "SUBJECT",
    subjectCode: "VAN",
    durationMinutes: 90,
    code: examCode("CR5-BCA-VAN"),
    name: `CR5 BCA Van ${RUN_ID}`,
  });
  await page.goto(`${ADMIN_URL}/exams/${bcaVanId}/structure`);
  const bcaRows = page.locator('[data-subject-form="ROWS"]');
  await expect(bcaRows).toBeVisible();
  await bcaRows.locator("[data-form-row]").first().getByRole("button", { name: /^Sửa dòng/ }).click();
  const bcaEditor = page.locator("[data-form-row-editor]");
  await expect(bcaEditor).toBeVisible();
  await bcaEditor.getByRole("button", { name: "Thêm điều kiện" }).click();
  await page.getByRole("menuitem", { name: /^MAPID \(lọc\)/ }).click();
  await bcaEditor.getByRole("radio", { name: /^Theo chủ đề/ }).check();
  await bcaEditor.getByRole("button", { name: "Chọn kiến thức" }).click();
  const picker = page.getByRole("dialog", { name: /Chọn kiến thức/ });
  await picker.getByPlaceholder("Tìm theo mã hoặc tên").fill(vanTree.leafCode);
  const leafCheckbox = picker.getByRole("checkbox", { name: new RegExp(`^${vanTree.leafCode} `) });
  await expect(leafCheckbox).toHaveCount(1);
  await leafCheckbox.check();
  await picker.getByRole("button", { name: /^Áp dụng/ }).click();
  await expect(picker).toBeHidden();
  const [bcaSaveRes] = await Promise.all([
    page.waitForResponse((res) => res.url().endsWith(`/exams/${bcaVanId}/structure`) && res.request().method() === "PUT"),
    page.getByRole("button", { name: "Tiếp tục → Thông tin bán", exact: true }).click(),
  ]);
  expect(bcaSaveRes.status(), await bcaSaveRes.text()).toBe(200);
  await page.waitForURL(/\/exams\/[^/]+\/listing/);
  const bcaAfter = await getStructure(admin, bcaVanId);
  expect(allSections(bcaAfter)[0]!.blocks?.[0]?.knowledgeScope?.nodeIds).toContain(vanTree.leafId);

  // AC-28: V-SAT Toán có 67 lệnh PROMPT/REFERENCE khóa áp dụng, còn Phần II thêm độ khó QUESTION bật REQUIRED và bền qua tải lại.
  const vsatId = await createApiExam(admin, {
    kind: "VSAT",
    scope: "SUBJECT",
    subjectCode: "TOAN",
    code: examCode("CR5-VSAT-TOAN"),
    name: `CR5 VSAT Toan ${RUN_ID}`,
  });
  await page.goto(`${ADMIN_URL}/exams/${vsatId}/structure`);
  await expect(page.locator('[data-subject-form="ROWS"]')).toBeVisible();
  await page.locator("[data-reference-block] summary").click();
  const quotasSection = page.locator("[data-reference-quotas]");
  await expect(quotasSection).toContainText("Đơn vị: lệnh hỏi");
  await expect(quotasSection).toContainText(/67 lệnh/);
  const quotaSwitches = quotasSection.getByRole("switch", { name: "Áp dụng khi sinh đề" });
  const quotaCount = await quotaSwitches.count();
  expect(quotaCount).toBeGreaterThanOrEqual(1);
  for (let i = 0; i < quotaCount; i += 1) {
    await expect(quotaSwitches.nth(i)).toBeDisabled();
    await expect(quotaSwitches.nth(i)).toHaveAccessibleDescription(/Phân bố theo lệnh hỏi chỉ dùng để đối chiếu/);
  }

  const structureNav = page.getByRole("navigation", { name: "Cấu trúc" });
  const partTwoSectionButton = structureNav
    .locator(":scope > ol > li")
    .filter({ has: page.locator(":scope > button[data-list-item]", { hasText: /^Phần II\b/ }) })
    .locator("ol button[data-list-item]");
  await partTwoSectionButton.click();
  await expect(partTwoSectionButton).toHaveAttribute("aria-current", "true");
  await expect(page.locator('[data-subject-form="ROWS"]')).toContainText("6 câu");
  await page.locator("[data-reference-block] summary").click();
  const difficultySection = page.locator('[data-reference-dimension="DIFFICULTY"]');
  await difficultySection.getByRole("textbox", { name: /^Độ khó: .*, số câu$/ }).first().fill("6");
  const difficultySwitch = difficultySection.getByRole("switch", { name: "Áp dụng khi sinh đề" });
  await expect(difficultySwitch).toBeEnabled();
  await difficultySwitch.click();
  await expect(difficultySwitch).toBeChecked();

  const vsatReturnUrl = page.url();
  const [vsatSaveRes] = await Promise.all([
    page.waitForResponse((res) => res.url().endsWith(`/exams/${vsatId}/structure`) && res.request().method() === "PUT"),
    page.getByRole("button", { name: "Tiếp tục → Thông tin bán", exact: true }).click(),
  ]);
  expect(vsatSaveRes.status(), await vsatSaveRes.text()).toBe(200);
  await page.waitForURL(/\/exams\/[^/]+\/listing/);

  const vsatSaved = await getStructure(admin, vsatId);
  expect(vsatSaved.parts[1]!.sections![0]!.matrix?.difficultyEnforcement).toBe("REQUIRED");
  expect(vsatSaved.parts[1]!.sections![0]!.matrix?.basis ?? "QUESTION").toBe("QUESTION");
  const savedQuotas = vsatSaved.examQuotas as Array<{ basis?: string; enforcement?: string }>;
  expect(savedQuotas.length).toBeGreaterThan(0);
  expect(savedQuotas.every((quota) => quota.basis === "PROMPT" && quota.enforcement === "REFERENCE")).toBe(true);

  await page.goto(vsatReturnUrl);
  await expect(partTwoSectionButton).toHaveAttribute("aria-current", "true");
  await page.locator("[data-reference-block] summary").click();
  await expect(page.locator('[data-reference-dimension="DIFFICULTY"]').getByRole("switch", { name: "Áp dụng khi sinh đề" })).toBeChecked();
  await expect(page.locator("[data-reference-quotas]").getByRole("switch", { name: "Áp dụng khi sinh đề" }).first()).toBeDisabled();
});

test("Sau FU #155: GET /exam-templates khớp BCA REFERENCE, REVIEW UUID + Bài ôn, và SPT GDKTPL giữ nguyên mã trên DS (AC-1, AC-4, AC-55, B6-7)", async ({
  context,
  page,
}) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();

  const res = await admin.api.get(`${ADMIN_URL}/api/v1/admin/exam-templates`, { headers: admin.headers });
  expect(res.status(), await res.text()).toBe(200);
  const allTemplates = (await res.json()) as TemplateWire[];
  const active = allTemplates.filter((template) => template.active === true);
  expect(active.length).toBeGreaterThanOrEqual(100);

  // V202611200710: mọi mẫu BCA đang hoạt động đặt gradeEnforcement = REFERENCE (không còn REQUIRED).
  const activeBca = active.filter((template) => template.kindCode === "BCA");
  expect(activeBca.length).toBeGreaterThan(0);
  for (const template of activeBca) {
    const json = JSON.stringify(template.structure ?? {});
    expect(json, `${template.code} còn gradeEnforcement REQUIRED`).not.toContain('"gradeEnforcement":"REQUIRED"');
  }
  expect(activeBca.some((template) => JSON.stringify(template.structure ?? {}).includes('"gradeEnforcement":"REFERENCE"'))).toBe(true);

  // V202611200712 + V202611200713: mọi mẫu REVIEW đang hoạt động có part.id dạng UUID và tên "Bài ôn".
  const activeReview = active.filter((template) => template.buildMode === "REVIEW");
  expect(activeReview.length).toBeGreaterThanOrEqual(9);
  for (const template of activeReview) {
    for (const part of template.structure?.parts ?? []) {
      expect(part.id, `${template.code} thiếu UUID part.id`).toMatch(UUID_RE);
      expect(part.name, `${template.code} chưa đổi sang "Bài ôn"`).toBe("Bài ôn");
    }
  }

  // Mọi section của mẫu STANDARD đang hoạt động đều ánh xạ vào bảng 75 cặp chuẩn + 4 bí danh.
  for (const template of active.filter((item) => item.buildMode !== "REVIEW")) {
    for (const section of collectSectionsFromParts(template.structure?.parts ?? [])) {
      const canonical = resolveCanonicalSubject(template.kindCode, section.subjectCode);
      expect(CANONICAL_PAIRS.has(`${template.kindCode}:${canonical}`), `${template.code}: ${template.kindCode}:${section.subjectCode}`).toBe(true);
    }
  }

  // AC-55: SPT với mã `GDKTPL` mở DS và lưu qua PUT/GET giữ nguyên `subjectCode: "GDKTPL"`.
  const sptId = await createApiExam(admin, {
    kind: "SPT",
    scope: "SUBJECT",
    subjectCode: "ECON_LAW",
    code: examCode("CR5-SPT-GDKTPL"),
    name: `CR5 SPT GDKTPL ${RUN_ID}`,
  });
  const sptBefore = await getStructure(admin, sptId);
  const gdktplParts = structuredClone(sptBefore.parts);
  for (const section of collectSectionsFromParts(gdktplParts)) {
    section.subjectCode = "GDKTPL";
  }
  const sptUpdated = await putStructure(admin, sptId, gdktplParts, sptBefore.examQuotas, sptBefore.revision);
  expect(collectSectionsFromParts(sptUpdated.parts)[0]?.subjectCode).toBe("GDKTPL");

  await page.goto(`${ADMIN_URL}/exams/${sptId}/structure`);
  const ds = page.locator('[data-subject-form="ROWS"]');
  await expect(ds).toBeVisible();
  await expect(page.getByTestId("full-form-notice")).toHaveCount(0);

  await ds.locator("[data-form-row]").first().getByRole("button", { name: /^Sửa dòng/ }).click();
  const rowEditor = page.locator("[data-form-row-editor]");
  const rowName = rowEditor.getByRole("textbox", { name: "Tên nhóm" });
  await rowName.fill(`${await rowName.inputValue()} QA`);
  const [putReq, putRes] = await Promise.all([
    page.waitForRequest((req) => req.url().endsWith(`/exams/${sptId}/structure`) && req.method() === "PUT"),
    page.waitForResponse((res) => res.url().endsWith(`/exams/${sptId}/structure`) && res.request().method() === "PUT"),
    page.getByRole("button", { name: "Tiếp tục → Thông tin bán", exact: true }).click(),
  ]);
  expect(putRes.status(), await putRes.text()).toBe(200);
  const putBody = putReq.postDataJSON() as { parts: StructurePart[] };
  expect(collectSectionsFromParts(putBody.parts).every((section) => section.subjectCode === "GDKTPL")).toBe(true);
  expect(collectSectionsFromParts((await getStructure(admin, sptId)).parts).every((section) => section.subjectCode === "GDKTPL")).toBe(
    true,
  );
});
