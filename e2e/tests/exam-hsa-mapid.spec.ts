import { expect, test, type Locator, type Page } from "@playwright/test";
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
  paperQuestions,
  type PreviewResultLite,
  type QuestionDraft,
  type StructureSection,
} from "./helpers/wave-2b";

test.setTimeout(900_000);

/**
 * UNI-2758 (AC-12, AC-6/AC-9 hiển thị): bài HSA theo phần Toán ở chế độ "Theo ma trận MAPID", quy tắc thêm qua hộp thoại
 * của bước 2: quy tắc 2 (Đúng N câu thuộc một MAPID trong cả môn) và quy tắc 5 (nhóm "Chùm cuối" chỉ lấy nguồn từ một Bài).
 *
 * Các khẳng định phải ĐỎ khi máy chủ bỏ qua quy tắc, nên dữ liệu do chính kịch bản dựng (cây MAPID riêng của lần chạy):
 * - 60 câu mang MAPID X (câu đơn và trả lời ngắn, rải theo khối lớp và độ khó) trong một kho Toán chưa gắn MAPID: khi không có
 *   quy tắc, đề lấy ngẫu nhiên nên số câu mang X lệch xa N;
 * - Chùm cuối nhận bộ 3 câu con từ kho (3 bộ SEED-2B-MAPID của dữ liệu dev, 4 bộ nguồn cho phép dưới Bài A, 24 bộ ngoài Bài A):
 *   khi không có quy tắc, bộ lấy ra gần như luôn không thuộc Bài A.
 * Kết quả đọc từ đáp án sinh thử (mã câu của chính kịch bản), không từ bất biến của dữ liệu mẫu. Bước đối chứng chạy lại cùng các
 * hạt giống khi quy tắc bị tạm ngừng (Toàn bài) và đòi ít nhất một hạt giống vi phạm số câu và một hạt giống lấy bộ ngoài nguồn.
 */

const SET_GROUP = "Chùm cuối";
/** Quy tắc 2: Đúng EXACT_N câu thuộc MAPID X trong cả môn. */
const EXACT_N = 3;
const POSITIVE_SEEDS = [1, 2, 3];
/** Đối chứng: nhiều hạt giống hơn để xác suất "không quy tắc mà vẫn đúng ý" tiến tới 0. */
const CONTROL_SEEDS = [1, 2, 3, 4, 5, 6];
const CARRIER_CELLS = [
  { gradeCode: "G11", difficultyCode: "TH" },
  { gradeCode: "G12", difficultyCode: "TH" },
  { gradeCode: "G11", difficultyCode: "VD" },
  { gradeCode: "G12", difficultyCode: "VD" },
];
const ALLOWED_SETS = 4;
const DECOY_SETS = 24;

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

interface Node {
  id: string;
  code: string;
}

/** Cây MAPID riêng: Chương → Bài X (nút X), Bài A (nút A), Bài D (nút D); mọi câu của luồng gắn nút của cây này. */
interface Bank {
  lessonX: Node;
  leafX: Node;
  lessonA: Node;
  carrierCodes: Set<string>;
  allowedSetCodes: Set<string>;
}

async function createNode(admin: AdminApi, parentId: string | null, code: string, name: string, refItemIds: string[]): Promise<Node> {
  const res = await admin.api.post(`${ADMIN_URL}/api/v1/admin/catalog/groups/${KNOWLEDGE_GROUP_ID}/items`, {
    headers: admin.headers,
    data: { code, nameVi: name, nameEn: name, ...(parentId ? { parentId } : {}), ...(refItemIds.length ? { refItemIds } : {}) },
  });
  expect(res.status(), `tạo nút MAPID ${code}: ${await res.text()}`).toBe(201);
  return { id: ((await res.json()) as { id: string }).id, code };
}

const option = (label: string, correct: boolean) => ({ id: crypto.randomUUID(), content: `<p>${label}</p>`, correct });
const singleChoiceBody = () => ({ type: "SINGLE_CHOICE", options: [option("A", true), option("B", false), option("C", false), option("D", false)], shuffleOptions: true });
const shortAnswerBody = () => ({ type: "SHORT_ANSWER", answers: ["1"], inputHint: null, answerFormat: null });

function carrierDraft(index: number, leaf: Node): QuestionDraft {
  const cell = CARRIER_CELLS[index % CARRIER_CELLS.length]!;
  const type = index % 2 === 0 ? "SINGLE_CHOICE" : "SHORT_ANSWER";
  return {
    type,
    subjectCode: "TOAN",
    ...cell,
    points: 1,
    knowledge: [{ nodeId: leaf.id, primary: true }],
    content: `<p>${UNIT_LABEL}MAPID X ${RUN_ID} câu ${index}.</p>`,
    body: type === "SINGLE_CHOICE" ? singleChoiceBody() : shortAnswerBody(),
    sourceCode: `HSAMAP-${RUN_ID}-X${index}`.slice(0, 60),
  };
}

/** Bộ 3 câu con trắc nghiệm, cả bộ và từng câu con gắn cùng một nút; khối lớp và độ khó như bộ SEED-2B-MAPID (đã sinh được). */
function setDraft(tag: string, index: number, leaf: Node): QuestionDraft {
  return {
    type: "QUESTION_SET",
    subjectCode: "TOAN",
    gradeCode: "G10",
    difficultyCode: "TH",
    points: 3,
    knowledge: [{ nodeId: leaf.id, primary: true }],
    content: `<p>${UNIT_LABEL}Chùm ${tag} ${RUN_ID} số ${index}.</p>`,
    body: {
      type: "QUESTION_SET",
      passage: `<p>${UNIT_LABEL}Ngữ liệu chùm ${tag} ${index}.</p>`,
      blankNumberStart: 1,
      children: [1, 2, 3].map((child) => ({
        type: "SINGLE_CHOICE",
        content: `<p>${UNIT_LABEL}Câu con ${child} của chùm ${tag} ${index}.</p>`,
        body: singleChoiceBody(),
        points: 1,
        knowledgeItemIds: [leaf.id],
      })),
    },
    sourceCode: `HSAMAP-${RUN_ID}-${tag}${index}`.slice(0, 60),
  };
}

async function seedBank(admin: AdminApi): Promise<Bank> {
  const base = `HSAMAP-${RUN_ID}`.toUpperCase();
  const chapter = await createNode(admin, null, `${base}-C`, `Chương MAPID ${RUN_ID}`, [SUBJECT_ITEM_ID.TOAN!, GRADE_G12_ITEM_ID]);
  const lessonX = await createNode(admin, chapter.id, `${base}-LX`, `Bài X ${RUN_ID}`, []);
  const leafX = await createNode(admin, lessonX.id, `${base}-KX`, `Kiến thức X ${RUN_ID}`, []);
  const lessonA = await createNode(admin, chapter.id, `${base}-LA`, `Bài A ${RUN_ID}`, []);
  const leafA = await createNode(admin, lessonA.id, `${base}-KA`, `Kiến thức A ${RUN_ID}`, []);
  const lessonD = await createNode(admin, chapter.id, `${base}-LD`, `Bài D ${RUN_ID}`, []);
  const leafD = await createNode(admin, lessonD.id, `${base}-KD`, `Kiến thức D ${RUN_ID}`, []);

  const carriers = await createReadyQuestions(admin, Array.from({ length: 60 }, (_, index) => carrierDraft(index, leafX)));
  const allowed = await createReadyQuestions(admin, Array.from({ length: ALLOWED_SETS }, (_, index) => setDraft("A", index, leafA)));
  await createReadyQuestions(admin, Array.from({ length: DECOY_SETS }, (_, index) => setDraft("D", index, leafD)));
  return {
    lessonX,
    leafX,
    lessonA,
    carrierCodes: new Set(carriers.map((question) => question.code)),
    allowedSetCodes: new Set(allowed.map((question) => question.code)),
  };
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

async function previewExam(admin: AdminApi, id: string, seed: number): Promise<PreviewResultLite> {
  const res = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/preview`, {
    headers: admin.headers,
    data: { seed, target: { kind: "EXAM" } },
  });
  expect(res.status(), await res.text()).toBe(200);
  return (await res.json()) as PreviewResultLite;
}

async function runExamCheck(admin: AdminApi, id: string): Promise<Record<string, unknown>> {
  const res = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/check`, { headers: admin.headers, data: {} });
  expect(res.status(), await res.text()).toBe(200);
  return (await res.json()) as Record<string, unknown>;
}

/** Đọc từ đề thử: số câu mang MAPID X (theo mã câu của kịch bản) và mã bộ của nhóm Chùm cuối. */
function readPaper(result: PreviewResultLite, bank: Bank): { carriers: number; setCode: string } {
  expect(result.status, result.reason ?? "").toBe("GENERATED");
  const carriers = paperQuestions(result).filter((question) => bank.carrierCodes.has(question.code)).length;
  const block = result.paper.parts.flatMap((part) => part.blocks ?? []).find((item) => item.blockName === SET_GROUP);
  expect(block, "đề thử phải có nhóm Chùm cuối").toBeTruthy();
  expect(block!.items ?? []).toHaveLength(1);
  const set = block!.items![0]!;
  expect(set.children ?? [], "bộ Chùm cuối phải đủ 3 câu con").toHaveLength(3);
  return { carriers, setCode: set.code ?? "" };
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
  await picker.getByRole("checkbox", { name: new RegExp(`^${code} `) }).check();
  await picker.getByRole("button", { name: /Áp dụng/ }).click();
  await expect(picker).not.toBeVisible();
  await expect(dialog.getByText(new RegExp(`^${code} `)).first()).toBeVisible();
}

/** Chọn phạm vi "Áp dụng cho" từ hộp chọn đơn. */
async function selectScope(page: Page, dialog: Locator, optionPattern: RegExp) {
  await dialog.getByTestId("mapid-rule-scope").getByRole("combobox").click();
  await page.getByRole("option", { name: optionPattern }).click();
}

async function saveStructure(page: Page) {
  await page.getByRole("button", { name: "Lưu" }).click();
  await expect(page.getByText("Có thay đổi chưa lưu")).not.toBeVisible({ timeout: 60_000 });
}

test("HSA Toán MAPID: quy tắc 2 + 5 ràng buộc đề sinh thử (đỏ khi bỏ quy tắc), kiểm tra chéo 3 chiều, Toàn bài tạm ngừng", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const bank = await seedBank(admin);
  const id = await createMathExam(admin);

  const before = mathSection(await getStructure(admin, id));
  const setBlock = (before.blocks ?? []).find((block) => block.name === SET_GROUP);
  expect(setBlock?.id, `mẫu HSA Toán phải có nhóm ${SET_GROUP}`).toBeTruthy();

  await page.goto(`${ADMIN_URL}/exams/${id}/structure?section=${encodeURIComponent(before.id)}`);
  await expect(page.getByRole("radio", { name: /Theo ma trận MAPID/ })).toBeVisible({ timeout: 60_000 });
  await page.getByRole("radio", { name: /Theo ma trận MAPID/ }).check();
  await expect(page.getByTestId("mapid-rules-table")).toBeVisible();

  // Quy tắc 2: cả môn — Số câu theo MAPID · Bằng · N câu thuộc MAPID X (Toàn môn chỉ hiện 3 loại quy tắc).
  let dialog = await openRuleDialog(page);
  await expect(dialog.getByTestId("mapid-rule-scope").getByRole("combobox")).toContainText("Toàn môn");
  await expect(dialog.getByTestId("mapid-rule-type-ONLY_IN_GROUP")).toHaveCount(0);
  await dialog.getByTestId("mapid-rule-type-COUNT").click();
  await pickMapid(page, bank.leafX.code);
  await dialog.getByTestId("mapid-rule-comparator").getByRole("radio", { name: "Bằng" }).check();
  await dialog.getByTestId("mapid-rule-count").fill(String(EXACT_N));
  await expect(dialog.getByTestId("mapid-rule-summary")).toContainText(`lấy đúng ${EXACT_N} câu`);
  await dialog.getByTestId("mapid-rule-save").click();
  await expect(page.getByRole("dialog")).toHaveCount(0);

  // Quy tắc 5: nhóm Chùm cuối — chọn nhóm hiện đủ 4 loại; Chỉ lấy nguồn từ Bài A.
  dialog = await openRuleDialog(page);
  await selectScope(page, dialog, new RegExp(`^${SET_GROUP}`));
  await expect(dialog.getByTestId("mapid-rule-type-ONLY_IN_GROUP")).toBeVisible();
  await dialog.getByTestId("mapid-rule-type-SOURCE").click();
  await pickMapid(page, bank.lessonA.code);
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
  expect(countRule.groupId ?? null, "quy tắc 2 áp cho cả môn").toBeNull();
  expect(countRule.comparator).toBe("EXACT");
  expect(countRule.count).toBe(EXACT_N);
  expect(sourceRule.groupId).toBe(setBlock!.id);

  // Sinh thử với nhiều hạt giống: mỗi đề có đúng N câu mang X, và Chùm cuối là một trong các bộ nguồn của Bài A (AC-12).
  for (const seed of POSITIVE_SEEDS) {
    const result = await previewExam(admin, id, seed);
    const paper = readPaper(result, bank);
    console.log(`MAPID with rules seed=${seed}: carriers=${paper.carriers} set=${paper.setCode}`);
    expect(paper.carriers, `hạt giống ${seed}: đúng ${EXACT_N} câu mang MAPID X`).toBe(EXACT_N);
    expect(bank.allowedSetCodes.has(paper.setCode), `hạt giống ${seed}: bộ ${paper.setCode} phải thuộc nguồn Bài A`).toBe(true);
    // AC-6: bảng đối chiếu có dòng của quy tắc Số lượng (quy tắc Chỉ lấy nguồn không có dòng riêng).
    const countRow = (result.compliance ?? []).find((row) => row.criterion === `MAPID:${countRule.id}`);
    expect(countRow, "đối chiếu phải có dòng của quy tắc Số lượng").toBeTruthy();
    expect(countRow!.required).toBe(EXACT_N);
    expect(countRow!.actual).toBe(EXACT_N);
  }

  // Kiểm tra chéo 3 chiều trong hộp thoại (UNI-1707 AC-1707-7):
  // 1) Xung đột cùng phạm vi (Đúng N + 2 vs Đúng N): chặn lưu, giữ nguyên số đã nhập, cho mở quy tắc đang có để sửa.
  dialog = await openRuleDialog(page);
  await dialog.getByTestId("mapid-rule-type-COUNT").click();
  await pickMapid(page, bank.leafX.code);
  await dialog.getByTestId("mapid-rule-comparator").getByRole("radio", { name: "Bằng" }).check();
  await dialog.getByTestId("mapid-rule-count").fill(String(EXACT_N + 2));
  await expect(dialog.getByTestId("mapid-rule-save")).toBeDisabled();
  await expect(dialog.getByTestId("mapid-rule-cross-check")).toContainText(/Xung đột/);
  await expect(dialog.getByTestId("mapid-rule-count")).toHaveValue(String(EXACT_N + 2));
  await dialog.getByTestId(`mapid-rule-open-${countRule.id}`).click();
  await expect(dialog.getByTestId("mapid-rule-count")).toHaveValue(String(EXACT_N));
  await dialog.getByRole("button", { name: "Hủy" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);

  // 2) Bao hàm (PRESENT + Đúng 2 trên nhóm Trắc nghiệm): hiện nút "Thay quy tắc cũ" và thay nguyên tử.
  dialog = await openRuleDialog(page);
  await selectScope(page, dialog, /^Trắc nghiệm/);
  await pickMapid(page, bank.leafX.code);
  await dialog.getByTestId("mapid-rule-save").click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByTestId("mapid-rules-table").getByRole("row")).toHaveCount(4);

  dialog = await openRuleDialog(page);
  await selectScope(page, dialog, /^Trắc nghiệm/);
  await dialog.getByTestId("mapid-rule-type-COUNT").click();
  await pickMapid(page, bank.leafX.code);
  await dialog.getByTestId("mapid-rule-comparator").getByRole("radio", { name: "Bằng" }).check();
  await dialog.getByTestId("mapid-rule-count").fill("2");
  await expect(dialog.getByTestId("mapid-rule-cross-check")).toContainText("Đúng 2 câu đã bao gồm yêu cầu phải xuất hiện");
  await expect(dialog.getByTestId("mapid-rule-save")).toBeEnabled();
  await dialog.getByTestId("mapid-rule-replace").click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByTestId("mapid-rules-table").getByRole("row")).toHaveCount(4);

  // Xóa quy tắc thử nghiệm trên Trắc nghiệm để bản nháp sạch trước khi tiêm lỗi máy chủ.
  await page.getByTestId("mapid-rules-table").getByRole("row", { name: /Trắc nghiệm/ }).getByRole("button", { name: /^Xóa quy tắc/ }).click();
  await expect(page.getByTestId("mapid-rules-table").getByRole("row")).toHaveCount(3);
  await expect(page.getByText("Có thay đổi chưa lưu")).not.toBeVisible();

  // Tiêm quy tắc xung đột qua API PUT /structure để kiểm tra lưới an toàn của máy chủ (Kiểm tra môn → MAPID_RULE_CONFLICT → Đi tới quy tắc).
  const latestStructure = await getStructure(admin, id);
  const latestMath = mathSection(latestStructure);
  latestMath.mapidRules = [
    ...(latestMath.mapidRules ?? []),
    { id: "server-conflict-exact", type: "COUNT", groupId: null, nodeIds: [bank.leafX.id], comparator: "EXACT", count: EXACT_N + 2 },
  ];
  const putConflict = await admin.api.put(`${ADMIN_URL}/api/v1/admin/exams/${id}/structure`, {
    headers: admin.headers,
    data: { parts: latestStructure.parts, examQuotas: latestStructure.examQuotas ?? [], expectedRevision: latestStructure.revision },
  });
  expect(putConflict.status(), await putConflict.text()).toBe(200);
  await page.reload();
  await expect(page.getByTestId("mapid-rules-table").getByRole("row")).toHaveCount(4);

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

  // Toàn bài: quy tắc tạm ngừng, ghi chú nêu số quy tắc.
  await page.getByRole("radio", { name: /Không giới hạn MAPID/ }).check();
  await expect(page.getByTestId("mapid-paused-note")).toContainText("3 quy tắc tạm ngừng áp dụng");
  await saveStructure(page);
  const relaxed = mathSection(await getStructure(admin, id));
  expect(relaxed.mapidMode).toBe("ALL");

  // Đối chứng (bước âm): quy tắc không còn hiệu lực thì cùng các hạt giống phải vi phạm số câu và nguồn của chùm. Nếu bước này
  // xanh mà các khẳng định ở trên cũng xanh khi máy chủ bỏ qua quy tắc, cặp khẳng định không phân biệt được quy tắc.
  let countViolations = 0;
  let sourceViolations = 0;
  for (const seed of CONTROL_SEEDS) {
    const result = await previewExam(admin, id, seed);
    const paper = readPaper(result, bank);
    console.log(`MAPID control (Toàn bài) seed=${seed}: carriers=${paper.carriers} set=${paper.setCode}`);
    if (paper.carriers !== EXACT_N) countViolations += 1;
    if (!bank.allowedSetCodes.has(paper.setCode)) sourceViolations += 1;
    expect(
      (result.compliance ?? []).filter((row) => (row.criterion ?? "").startsWith("MAPID:")),
      "Toàn bài không còn đối chiếu quy tắc MAPID",
    ).toHaveLength(0);
  }
  console.log(`MAPID control: ${countViolations}/${CONTROL_SEEDS.length} đề sai số câu, ${sourceViolations}/${CONTROL_SEEDS.length} đề lấy bộ ngoài nguồn`);
  expect(countViolations, "không có quy tắc: ít nhất một hạt giống phải lệch số câu mang MAPID X").toBeGreaterThanOrEqual(1);
  expect(sourceViolations, "không có quy tắc: ít nhất một hạt giống phải lấy bộ ngoài Bài A").toBeGreaterThanOrEqual(1);
});
