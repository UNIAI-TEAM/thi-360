import { expect, test, type BrowserContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { ensureDsReady } from "./helpers/attempt-fixtures";
import { ADMIN_URL, adminApi, createKnowledgeTree, loginAdmin, RUN_ID, WEB_URL, type AdminApi } from "./helpers/editor-qa";
import { ACCOUNT_PASSWORD } from "./helpers/mail";
import {
  applyMatrixCookies,
  expectMatrixClean,
  launchMatrixBrowser,
  matrixCells,
  openMatrixContext,
} from "./helpers/visual-matrix";
import {
  clickAndCapturePreview,
  createReadyQuestions,
  getStructure,
  openPreviewTab,
  selectPathInUi,
  SOLVER_TIMEOUT_MS,
  type PreviewResultLite,
  type QuestionDraft,
} from "./helpers/wave-2b";

test.describe.configure({ mode: "serial" });
test.setTimeout(SOLVER_TIMEOUT_MS);

interface AdminExamListResponse {
  items: Array<{
    id: string;
    code: string;
    name: string;
    kindCode: string;
    status: string;
  }>;
}

interface PreviewTrialPaperQuestion {
  number: number;
  questionId: string;
  code: string;
  type: string;
  points: number;
  optionOrder: string[] | null;
  body: {
    type: string;
    options?: Array<{ id: string; content: string }>;
    statements?: Array<{ id: string; content: string }>;
    left?: Array<{ id: string; content: string }>;
    right?: Array<{ id: string; content: string }>;
    wordLimit?: number | null;
  };
}

interface PreviewTrialPaperItem {
  kind: "QUESTION" | "SET";
  question?: PreviewTrialPaperQuestion;
  set?: {
    questionId: string;
    children: PreviewTrialPaperQuestion[];
  };
}

interface PreviewTrialPaper {
  examId: string;
  previewId: string;
  parts: Array<{
    partId: string;
    name: string;
    order: number;
    blocks: Array<{
      blockId: string;
      name: string;
      questionType: string;
      items: PreviewTrialPaperItem[];
    }>;
  }>;
}

interface PreviewGradeResponse {
  examId: string;
  previewId: string;
  kindCode: string;
  rawScore: number;
  maxScore: number;
  hasPendingManualGrading: boolean;
  statistics: {
    correctCount: number;
    partialCount: number;
    wrongCount: number;
    blankCount: number;
    pendingReviewCount: number;
  };
  items: Array<{
    questionId: string;
    number: number;
    type: string;
    outcome: string;
    earnedPoints: number;
    maxPoints: number;
    pendingReview: boolean;
    gradingDetail?: {
      statementResults?: Array<{
        id?: string;
        answered?: boolean;
        matched?: boolean;
      }>;
    } | null;
  }>;
}

interface AttemptCountResponse {
  totalItems: number;
}

const SENSITIVE_PAPER_KEYS = new Set([
  "correct",
  "correctOptionIds",
  "answer",
  "answers",
  "acceptedAnswers",
  "pairs",
  "gradingGuide",
  "sampleAnswer",
  "rubric",
  "solution",
  "commonMistakes",
  "sourceCode",
]);

const SHORT_RUN = RUN_ID.toLowerCase().replace(/[^a-z0-9]/g, "").slice(-6);
let seq = 0;

function nextTag(): string {
  seq += 1;
  return `${SHORT_RUN}${seq.toString(36)}`;
}

function collectSensitiveKeys(value: unknown, path = "$"): string[] {
  const hits: string[] = [];
  if (Array.isArray(value)) {
    value.forEach((item, idx) => hits.push(...collectSensitiveKeys(item, `${path}[${idx}]`)));
    return hits;
  }
  if (value !== null && typeof value === "object") {
    for (const [key, nested] of Object.entries(value as Record<string, unknown>)) {
      const nextPath = `${path}.${key}`;
      if (SENSITIVE_PAPER_KEYS.has(key)) {
        hits.push(nextPath);
      }
      hits.push(...collectSensitiveKeys(nested, nextPath));
    }
  }
  return hits;
}

async function findExamByCode(admin: AdminApi, code: string) {
  const res = await admin.api.get(
    `${ADMIN_URL}/api/v1/admin/exams?q=${encodeURIComponent(code)}&size=20`,
  );
  expect(res.status(), `GET /admin/exams?q=${code}: ${await res.text()}`).toBe(200);
  const data = (await res.json()) as AdminExamListResponse;
  const matched = data.items.find((item) => item.code === code);
  expect(matched, `Không tìm thấy đề thi mã ${code}`).toBeDefined();
  return matched!;
}

async function generateAndOpenTrial(
  page: Page,
  context: BrowserContext,
  examId: string,
  options?: { pathChoice?: RegExp },
): Promise<{
  previewId: string;
  previewResult: PreviewResultLite;
  trialExpiresAt: string;
  trialPage: Page;
  paper: PreviewTrialPaper;
  trialUrl: string;
}> {
  await page.goto(`${ADMIN_URL}/exams/${examId}/review`);
  const previewPanel = await openPreviewTab(page);

  if (options?.pathChoice) {
    await selectPathInUi(page, previewPanel, options.pathChoice);
  }

  const genButton = previewPanel.getByRole("button", { name: /^Sinh đề (xem trước|thử mới)$/ });
  await expect(genButton).toBeVisible({ timeout: 20_000 });
  const { result } = await clickAndCapturePreview(page, examId, genButton);
  expect(result.status, `Preview failed: ${result.reason ?? ""}`).toBe("GENERATED");
  const previewId = result.previewId ?? "";
  expect(previewId).toBeTruthy();

  const trialBtn = previewPanel.getByRole("button", { name: "Làm thử như học viên" });
  await expect(trialBtn).toBeVisible();

  let capturedPaper: PreviewTrialPaper | null = null;
  const paperPromise = new Promise<PreviewTrialPaper>((resolve) => {
    const onResponse = async (res: import("@playwright/test").Response) => {
      if (res.url().includes(`/exams/${examId}/preview/${previewId}/paper`) && res.request().method() === "GET") {
        expect(res.status()).toBe(200);
        capturedPaper = (await res.json()) as PreviewTrialPaper;
        context.off("response", onResponse);
        resolve(capturedPaper);
      }
    };
    context.on("response", onResponse);
  });

  let trialExpiresAt = "";
  const [trialPage] = await Promise.all([
    context.waitForEvent("page"),
    page
      .waitForResponse(
        (r) => r.url().includes(`/exams/${examId}/preview/${previewId}/trial`) && r.request().method() === "POST" && r.status() === 200,
      )
      .then(async (res) => {
        const body = (await res.json()) as { expiresAt?: string };
        trialExpiresAt = body.expiresAt ?? "";
        return res;
      }),
    trialBtn.click(),
  ]);

  const previewExpiresAt = (result as PreviewResultLite & { expiresAt?: string }).expiresAt;
  expect(trialExpiresAt).toBeTruthy();
  expect(Date.parse(trialExpiresAt)).toBeGreaterThan(Date.now());
  if (previewExpiresAt) {
    expect(Date.parse(trialExpiresAt)).toBeGreaterThanOrEqual(Date.parse(previewExpiresAt));
  }

  await expect(trialPage).toHaveURL(new RegExp(`/exams/${examId}/preview/${previewId}/try`));
  const trialUrl = trialPage.url();
  const paper = capturedPaper ?? (await paperPromise);
  await expect(trialPage.getByRole("heading", { level: 1, name: "Làm thử như học viên" })).toBeVisible({
    timeout: 30_000,
  });

  return { previewId, previewResult: result, trialExpiresAt, trialPage, paper, trialUrl };
}

async function createNoExamStaff(admin: AdminApi): Promise<{ username: string; password: string }> {
  const uniq = nextTag();
  const roleCode = `FA4-ROLE-NOEXAM-${uniq}`;
  const roleRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/roles`, {
    headers: { ...admin.headers, "idempotency-key": randomUUID() },
    data: {
      code: roleCode,
      name: `FA4-Role NoExam ${uniq}`,
      description: "FA-4 custom role without EXAM.VIEW",
      access: "MANAGE",
      status: "ACTIVE",
      permissionCodes: ["ATTEMPT.VIEW"],
    },
  });
  expect(roleRes.status(), await roleRes.text()).toBe(201);
  const roleJson = (await roleRes.json()) as { id: string };

  const username = `fa4.noexam.${uniq}`.slice(0, 29);
  const userRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/users`, {
    headers: { ...admin.headers, "idempotency-key": randomUUID() },
    data: {
      username,
      password: ACCOUNT_PASSWORD,
      fullName: `FA4-NoExam-${uniq}`,
      email: `${username}@e2e.thi360.test`,
      roleIds: [roleJson.id],
      mustChangePassword: false,
    },
  });
  expect(userRes.status(), await userRes.text()).toBe(201);
  return { username, password: ACCOUNT_PASSWORD };
}

async function waitTrialSettled(page: Page): Promise<void> {
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0, { timeout: 20_000 });
  await expect(page.locator(".animate-pulse")).toHaveCount(0, { timeout: 20_000 });
  await page.evaluate(() => document.fonts.ready);
}

test.describe("FA-4 Làm thử đề xem trước (Preview Trial)", () => {
  test.beforeAll(async ({ browser }) => {
    await ensureDsReady(browser);
  });

  test("mở tab mới, gia hạn TTL và không lộ đáp án hay hướng dẫn chấm (AC-17, AC-18, AC-24)", async ({
    browser,
  }) => {
    const admin = await adminApi();
    const sp2e = await findExamByCode(admin, "DS-SP2E-MATH-2025");

    const ctx = await browser.newContext();
    try {
      await loginAdmin(ctx, "vi");
      const page = await ctx.newPage();

      const { trialPage, paper } = await generateAndOpenTrial(page, ctx, sp2e.id);

      // Xác minh giao diện không đếm ngược và gia hạn thời gian giữ
      await expect(page.getByText("Đã mở đề làm thử trong tab mới.")).toBeAttached();
      await expect(trialPage.getByText(/Đề giữ tới/)).toBeVisible();
      await expect(trialPage.getByText("Chế độ làm thử")).toBeVisible();
      await expect(trialPage.getByText("Không tính giờ · Không lưu lượt, không trừ quyền dùng")).toBeVisible();
      await expect(trialPage.locator('[role="timer"]')).toHaveCount(0);
      await expect(trialPage.getByRole("navigation", { name: "Chuyển phần đề thi" })).toBeVisible();
      await expect(trialPage.getByRole("complementary", { name: "Bảng điều hướng câu hỏi" })).toBeVisible();

      // Xác minh allowlist response /paper không chứa đáp án hay hướng dẫn chấm
      const leakedPaths = collectSensitiveKeys(paper);
      expect(leakedPaths, `Lộ trường nhạy cảm trong /paper: ${leakedPaths.join(", ")}`).toEqual([]);

      await trialPage.close();
    } finally {
      await ctx.close();
    }
  });

  test("chấm SP2E Toán tức thì theo phần/mệnh đề và không tạo lượt thi thật (AC-19, AC-20, AC-24)", async ({
    browser,
  }) => {
    const admin = await adminApi();
    const sp2e = await findExamByCode(admin, "DS-SP2E-MATH-2025");

    const beforeRes = await admin.api.get(
      `${ADMIN_URL}/api/v1/admin/attempts?exam=${encodeURIComponent(sp2e.id)}&size=1`,
    );
    expect(beforeRes.status()).toBe(200);
    const beforeAttempts = ((await beforeRes.json()) as AttemptCountResponse).totalItems;

    const ctx = await browser.newContext();
    try {
      await loginAdmin(ctx, "vi");
      const page = await ctx.newPage();
      const { trialPage, paper, previewId } = await generateAndOpenTrial(page, ctx, sp2e.id);

      // Theo dõi để chắc chắn luồng làm thử không gọi endpoint lượt thi học viên
      const studentAttemptCalls: string[] = [];
      trialPage.on("request", (req) => {
        if (req.url().includes("/api/v1/me/attempts")) {
          studentAttemptCalls.push(`${req.method()} ${req.url()}`);
        }
      });

      expect(paper.parts.length).toBeGreaterThanOrEqual(3);

      // Phần 1: SINGLE_CHOICE -> chọn phương án đầu tiên của câu đầu tiên
      const part1FirstQ = paper.parts[0]!.blocks[0]!.items[0]!.question!;
      const q1Section = trialPage.locator(`#trial-question-${part1FirstQ.questionId}`);
      await expect(q1Section).toBeVisible();
      await q1Section.locator('[data-question-options] input[type="radio"]').first().click();

      // Bảng điều hướng câu hỏi cập nhật trạng thái đã trả lời
      await expect(
        trialPage
          .getByRole("complementary", { name: "Bảng điều hướng câu hỏi" })
          .getByRole("button", { name: new RegExp(`Câu ${part1FirstQ.number}\\s*·\\s*đã trả lời`) }),
      ).toBeVisible();

      // Chuyển sang Phần 2: TRUE_FALSE -> chọn 4 mệnh đề (Đúng, Sai, Đúng, Sai)
      const partNav = trialPage.getByRole("navigation", { name: "Chuyển phần đề thi" });
      await partNav.getByRole("button").nth(1).click();
      const part2FirstQ = paper.parts[1]!.blocks[0]!.items[0]!.question!;
      const q2Section = trialPage.locator(`#trial-question-${part2FirstQ.questionId}`);
      await expect(q2Section).toBeVisible();
      const statementGroups = q2Section.locator('[role="radiogroup"]');
      await expect(statementGroups).toHaveCount(4);
      await statementGroups.nth(0).getByRole("radio", { name: "Đúng" }).click();
      await statementGroups.nth(1).getByRole("radio", { name: "Sai" }).click();
      await statementGroups.nth(2).getByRole("radio", { name: "Đúng" }).click();
      await statementGroups.nth(3).getByRole("radio", { name: "Sai" }).click();

      // Chuyển sang Phần 3: SHORT_ANSWER -> nhập đáp số
      await partNav.getByRole("button").nth(2).click();
      const part3FirstQ = paper.parts[2]!.blocks[0]!.items[0]!.question!;
      const q3Section = trialPage.locator(`#trial-question-${part3FirstQ.questionId}`);
      await expect(q3Section).toBeVisible();
      await q3Section.getByLabel("Đáp số").fill("1");

      // Nộp bài & Chấm thử -> bắt phản hồi /grade
      const [gradeResp] = await Promise.all([
        trialPage.waitForResponse(
          (r) => r.url().includes(`/exams/${sp2e.id}/preview/${previewId}/grade`) && r.request().method() === "POST",
        ),
        trialPage.getByRole("button", { name: "Nộp bài & Chấm thử" }).click(),
      ]);
      expect(gradeResp.status()).toBe(200);
      const gradeJson = (await gradeResp.json()) as PreviewGradeResponse;
      expect("attemptId" in gradeJson).toBe(false);
      expect(gradeJson.items.length).toBeGreaterThanOrEqual(22);
      expect(gradeJson.maxScore).toBeGreaterThan(0);
      expect(typeof gradeJson.rawScore).toBe("number");
      const tfItem = gradeJson.items.find((it) => it.type === "TRUE_FALSE");
      expect(tfItem?.gradingDetail?.statementResults).toHaveLength(4);

      const viNum = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 });

      // Kiểm tra giao diện Kết quả làm thử: tổng điểm, điểm theo phần và bảng chi tiết từng câu
      await expect(trialPage.getByRole("heading", { level: 1, name: "Kết quả làm thử" })).toBeVisible();
      await expect(trialPage.locator("main")).toContainText(
        `${viNum.format(gradeJson.rawScore)} / ${viNum.format(gradeJson.maxScore)}`,
      );
      await expect(trialPage.getByRole("heading", { name: "Điểm theo phần" })).toBeVisible();
      for (const p of paper.parts) {
        await expect(trialPage.locator("main")).toContainText(p.name);
      }

      const detailTable = trialPage.getByRole("table", { name: "Chi tiết từng câu của kết quả làm thử" });
      await expect(detailTable).toBeVisible();
      // Cột chi tiết Đúng/Sai của câu TRUE_FALSE có 4 nhãn mệnh đề a) b) c) d)
      await expect(detailTable).toContainText("a)");
      await expect(detailTable).toContainText("b)");
      await expect(detailTable).toContainText("c)");
      await expect(detailTable).toContainText("d)");

      // Không ghi thêm bất kỳ lượt thi nào vào attempt.*
      expect(studentAttemptCalls).toEqual([]);
      const afterRes = await admin.api.get(
        `${ADMIN_URL}/api/v1/admin/attempts?exam=${encodeURIComponent(sp2e.id)}&size=1`,
      );
      expect(afterRes.status()).toBe(200);
      const afterAttempts = ((await afterRes.json()) as AttemptCountResponse).totalItems;
      expect(afterAttempts).toBe(beforeAttempts);

      // "Làm lại đề này" quay lại tờ đề mới sạch đáp án trên cùng previewId
      await trialPage.getByRole("button", { name: "Làm lại đề này" }).click();
      await expect(trialPage.getByRole("heading", { level: 1, name: "Làm thử như học viên" })).toBeVisible();
      await expect(trialPage.locator('[role="timer"]')).toHaveCount(0);
      await expect(
        trialPage
          .getByRole("complementary", { name: "Bảng điều hướng câu hỏi" })
          .getByRole("button", { name: new RegExp(`Câu ${part1FirstQ.number}\\s*·\\s*chưa trả lời`) }),
      ).toBeVisible();

      await trialPage.close();
    } finally {
      await ctx.close();
    }
  });

  test("giữ thứ tự phương án khi shuffleOptions = false, giữ đảo cùng seed và cột phải MATCHING (AC-21)", async ({
    browser,
  }) => {
    const admin = await adminApi();
    const tree = await createKnowledgeTree(admin, "TOAN", `fa4ord${SHORT_RUN}`);

    const fixedScTexts = ["SC Co dinh 1", "SC Co dinh 2", "SC Co dinh 3", "SC Co dinh 4"];
    const fixedChildScTexts = ["Con1 Co dinh 1", "Con1 Co dinh 2", "Con1 Co dinh 3", "Con1 Co dinh 4"];
    const fixedChildMcTexts = ["Con2 Co dinh 1", "Con2 Co dinh 2", "Con2 Co dinh 3", "Con2 Co dinh 4"];

    const drafts: QuestionDraft[] = [
      {
        type: "SINGLE_CHOICE",
        subjectCode: "TOAN",
        gradeCode: "G12",
        difficultyCode: "TH",
        points: 1,
        knowledge: [{ nodeId: tree.leafId, primary: true }],
        content: `<p>FA4 SC khong dao ${RUN_ID}</p>`,
        body: {
          type: "SINGLE_CHOICE",
          options: fixedScTexts.map((txt, idx) => ({ id: randomUUID(), content: `<p>${txt}</p>`, correct: idx === 0 })),
          shuffleOptions: false,
        },
        sourceCode: `FA4-SC-${RUN_ID}`.slice(0, 60),
      },
      {
        type: "QUESTION_SET",
        subjectCode: "TOAN",
        gradeCode: "G12",
        difficultyCode: "TH",
        points: 2,
        knowledge: [{ nodeId: tree.leafId, primary: true }],
        content: `<p>FA4 Chum khong dao con ${RUN_ID}</p>`,
        body: {
          type: "QUESTION_SET",
          passage: `<p>Ngu lieu chum FA4 ${RUN_ID}</p>`,
          blankNumberStart: 1,
          children: [
            {
              type: "SINGLE_CHOICE",
              content: `<p>FA4 Con 1 khong dao ${RUN_ID}</p>`,
              points: 1,
              knowledgeItemIds: [tree.leafId],
              body: {
                type: "SINGLE_CHOICE",
                options: fixedChildScTexts.map((txt, idx) => ({
                  id: randomUUID(),
                  content: `<p>${txt}</p>`,
                  correct: idx === 0,
                })),
                shuffleOptions: false,
              },
            },
            {
              type: "MULTIPLE_CHOICE",
              content: `<p>FA4 Con 2 khong dao ${RUN_ID}</p>`,
              points: 1,
              knowledgeItemIds: [tree.leafId],
              body: {
                type: "MULTIPLE_CHOICE",
                options: fixedChildMcTexts.map((txt, idx) => ({
                  id: randomUUID(),
                  content: `<p>${txt}</p>`,
                  correct: idx < 2,
                })),
                scoringRule: "ALL_OR_NOTHING",
                shuffleOptions: false,
              },
            },
          ],
        },
        sourceCode: `FA4-QS-${RUN_ID}`.slice(0, 60),
      },
      {
        type: "MATCHING",
        subjectCode: "TOAN",
        gradeCode: "G12",
        difficultyCode: "TH",
        points: 3,
        knowledge: [{ nodeId: tree.leafId, primary: true }],
        content: `<p>FA4 Ghep doi ${RUN_ID}</p>`,
        body: {
          type: "MATCHING",
          left: [
            { id: "l1", content: "<p>Trai Mot</p>" },
            { id: "l2", content: "<p>Trai Hai</p>" },
            { id: "l3", content: "<p>Trai Ba</p>" },
          ],
          right: [
            { id: "r1", content: "<p>Phai Mot</p>" },
            { id: "r2", content: "<p>Phai Hai</p>" },
            { id: "r3", content: "<p>Phai Ba</p>" },
            { id: "r4", content: "<p>Phai Bon</p>" },
          ],
          pairs: [
            { leftId: "l1", rightId: "r1", points: 1 },
            { leftId: "l2", rightId: "r2", points: 1 },
            { leftId: "l3", rightId: "r3", points: 1 },
          ],
          reuseRight: false,
        },
        sourceCode: `FA4-MT-${RUN_ID}`.slice(0, 60),
      },
    ];

    await createReadyQuestions(admin, drafts);

    const examCode = `FA4-ORD-${SHORT_RUN}`.toUpperCase().slice(0, 30);
    const createExamRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
      headers: { ...admin.headers, "idempotency-key": randomUUID() },
      data: {
        kind: "HSA",
        scope: "SUBJECT",
        subjectCode: "TOAN",
        buildMode: "REVIEW",
        code: examCode,
        name: `FA4 Thu tu phuong an ${RUN_ID}`,
        description: `De on kiem tra giu thu tu va ghep doi FA4 ${RUN_ID}`,
      },
    });
    expect(createExamRes.status(), await createExamRes.text()).toBe(201);
    const createdExam = (await createExamRes.json()) as { id: string };
    const examId = createdExam.id;

    const structure = await getStructure(admin, examId);
    const part = structure.parts[0]!;
    const section = part.sections![0]!;
    const templateBlock = section.blocks![0]!;
    const baseOrd = typeof templateBlock.ord === "number" ? templateBlock.ord : 0;

    section.blocks = [
      {
        ...templateBlock,
        name: "Nhom 1 Mot dap an co dinh",
        questionType: "SINGLE_CHOICE",
        questionCount: 1,
        knowledgeScope: { nodeIds: [tree.leafId], competencyCodes: [] },
      },
      {
        ...templateBlock,
        id: randomUUID(),
        ord: baseOrd + 1,
        name: "Nhom 2 Chum co dinh",
        questionType: "QUESTION_SET",
        questionCount: 2,
        setCount: 1,
        setSize: 2,
        knowledgeScope: { nodeIds: [tree.leafId], competencyCodes: [] },
      },
      {
        ...templateBlock,
        id: randomUUID(),
        ord: baseOrd + 2,
        name: "Nhom 3 Ghep doi",
        questionType: "MATCHING",
        questionCount: 1,
        knowledgeScope: { nodeIds: [tree.leafId], competencyCodes: [] },
      },
    ];
    section.questionCount = 4;
    part.questionCount = 4;

    const putStructRes = await admin.api.put(`${ADMIN_URL}/api/v1/admin/exams/${examId}/structure`, {
      headers: admin.headers,
      data: {
        parts: structure.parts,
        examQuotas: structure.examQuotas ?? [],
        expectedRevision: structure.revision,
      },
    });
    expect(putStructRes.status(), await putStructRes.text()).toBe(200);

    const ctx = await browser.newContext();
    try {
      await loginAdmin(ctx, "vi");
      const page = await ctx.newPage();

      // Lần sinh 1 (seed mặc định 1 từ UI)
      const first = await generateAndOpenTrial(page, ctx, examId);
      const allItems1 = first.paper.parts.flatMap((p) => p.blocks.flatMap((b) => b.items));
      const sc1 = allItems1.find((it) => it.kind === "QUESTION" && it.question?.type === "SINGLE_CHOICE")!.question!;
      const qsSet1 = allItems1.find((it) => it.kind === "SET")!.set!;
      const qsChildren1 = qsSet1.children;
      const qs1ChildSc = qsChildren1[0]!;
      const qs1ChildMc = qsChildren1[1]!;
      const mt1 = allItems1.find((it) => it.kind === "QUESTION" && it.question?.type === "MATCHING")!.question!;

      const scDomTexts1 = await first.trialPage
        .locator(`#trial-question-${sc1.questionId} [data-question-options] [data-exam-content]`)
        .allInnerTexts();
      expect(scDomTexts1.map((t) => t.trim())).toEqual(fixedScTexts);

      const childScDomTexts1 = await first.trialPage
        .locator(
          `#trial-question-${qsSet1.questionId} [aria-labelledby="${qs1ChildSc.questionId}-title"] [data-question-options] [data-exam-content]`,
        )
        .allInnerTexts();
      expect(childScDomTexts1.map((t) => t.trim())).toEqual(fixedChildScTexts);

      const childMcDomTexts1 = await first.trialPage
        .locator(
          `#trial-question-${qsSet1.questionId} [aria-labelledby="${qs1ChildMc.questionId}-title"] [data-question-options] [data-exam-content]`,
        )
        .allInnerTexts();
      expect(childMcDomTexts1.map((t) => t.trim())).toEqual(fixedChildMcTexts);

      // Kiểm tra cột phải MATCHING đang hiển thị & tương tác chọn ghép đôi hoạt động
      const mtSection1 = first.trialPage.locator(`#trial-question-${mt1.questionId}`);
      await expect(mtSection1).toBeVisible();
      const firstMatchCombo = mtSection1.getByRole("combobox").first();
      await firstMatchCombo.click();
      await first.trialPage.getByRole("option").first().click();

      const matchingRightIds1 = mt1.optionOrder ?? mt1.body.right?.map((r) => r.id) ?? [];
      expect(matchingRightIds1.length).toBe(4);
      await first.trialPage.close();

      // Lần sinh 2 trên trang vừa reload (cùng seed = 1 từ giao diện) -> thứ tự optionOrder giữ nguyên hoàn toàn
      const second = await generateAndOpenTrial(page, ctx, examId);
      const allItems2 = second.paper.parts.flatMap((p) => p.blocks.flatMap((b) => b.items));
      const sc2 = allItems2.find((it) => it.kind === "QUESTION" && it.question?.type === "SINGLE_CHOICE")!.question!;
      const qsChildren2 = allItems2.find((it) => it.kind === "SET")!.set!.children;
      const mt2 = allItems2.find((it) => it.kind === "QUESTION" && it.question?.type === "MATCHING")!.question!;

      expect(sc2.optionOrder).toEqual(sc1.optionOrder);
      expect(qsChildren2[0]!.optionOrder).toEqual(qs1ChildSc.optionOrder);
      expect(qsChildren2[1]!.optionOrder).toEqual(qs1ChildMc.optionOrder);
      expect(mt2.optionOrder ?? mt2.body.right?.map((r) => r.id)).toEqual(matchingRightIds1);
      await second.trialPage.close();
    } finally {
      await ctx.close();
    }
  });

  test("tự luận BCA FULL: đếm chữ trực tiếp, không chặn khi dưới ngưỡng và hiện trạng thái chờ chấm (AC-22)", async ({
    browser,
  }) => {
    const admin = await adminApi();
    const bcaMini = await findExamByCode(admin, "DS-BCA-FULL-MINI");

    const ctx = await browser.newContext();
    try {
      await loginAdmin(ctx, "vi");
      const page = await ctx.newPage();

      const { trialPage, paper } = await generateAndOpenTrial(page, ctx, bcaMini.id, {
        pathChoice: /CA1/i,
      });

      // Tìm câu ESSAY ở Phần 1
      const essayItem = paper.parts[0]!.blocks.flatMap((b) => b.items).find((it) => it.question?.type === "ESSAY");
      expect(essayItem?.question).toBeDefined();
      const essayQ = essayItem!.question!;

      const essaySection = trialPage.locator(`#trial-question-${essayQ.questionId}`);
      await expect(essaySection).toBeVisible();
      const editor = essaySection.locator('[role="textbox"][contenteditable="true"]').first();
      await editor.click();
      await trialPage.keyboard.type("Bai luan ngan", { delay: 15 });

      const statusText = essaySection.getByRole("status");
      await expect(statusText).toContainText(/3/);
      if (essayQ.body.wordLimit) {
        await expect(statusText).toContainText(String(essayQ.body.wordLimit));
      }

      // Nút Nộp bài & Chấm thử không bị chặn dù dưới ngưỡng tối thiểu chữ
      const submitBtn = trialPage.getByRole("button", { name: "Nộp bài & Chấm thử" });
      await expect(submitBtn).toBeEnabled();
      await submitBtn.click();

      await expect(trialPage.getByRole("heading", { level: 1, name: "Kết quả làm thử" })).toBeVisible();
      await expect(trialPage.locator("main")).toContainText(/phần trắc nghiệm · tự luận chưa chấm/);

      const resultTable = trialPage.getByRole("table", { name: "Chi tiết từng câu của kết quả làm thử" });
      await expect(resultTable).toBeVisible();
      await expect(resultTable.getByText("Chờ chấm").first()).toBeVisible();

      await trialPage.close();
    } finally {
      await ctx.close();
    }
  });

  test("thiếu quyền xem bài: 403 trên trang làm thử và toàn bộ API làm thử (AC-27)", async ({ browser }) => {
    const admin = await adminApi();
    const sp2e = await findExamByCode(admin, "DS-SP2E-MATH-2025");

    // Admin tạo sẵn 1 previewId hợp lệ
    const previewRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${sp2e.id}/preview`, {
      headers: admin.headers,
      data: { seed: 1, target: { kind: "EXAM" } },
    });
    expect(previewRes.status(), await previewRes.text()).toBe(200);
    const previewJson = (await previewRes.json()) as { status: string; previewId: string };
    expect(previewJson.status).toBe("GENERATED");
    const previewId = previewJson.previewId;

    const noExamUser = await createNoExamStaff(admin);
    const noCtx = await browser.newContext();
    try {
      const loginRes = await noCtx.request.post(`${WEB_URL}/api/v1/auth/login`, {
        data: { identifier: noExamUser.username, password: noExamUser.password },
        headers: { "Content-Type": "application/json", origin: WEB_URL },
      });
      expect(loginRes.ok(), await loginRes.text()).toBe(true);
      await noCtx.addCookies([
        { name: "thi360_locale", value: "vi", url: WEB_URL },
        { name: "thi360_locale", value: "vi", url: ADMIN_URL },
      ]);

      const page = await noCtx.newPage();
      await page.goto(`${ADMIN_URL}/exams/${sp2e.id}/preview/${previewId}/try`);
      await expect(page.getByText("MÃ LỖI 403")).toBeVisible();
      await expect(page.getByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeVisible();

      await page.goto(`${ADMIN_URL}/exams/${sp2e.id}/review`);
      await expect(page.getByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeVisible();

      const cookies = await noCtx.cookies();
      const xsrf = cookies.find((c) => c.name === "thi360_xsrf")?.value ?? "";
      const apiHeaders: Record<string, string> = {
        origin: ADMIN_URL,
        ...(xsrf ? { "x-xsrf-token": xsrf } : {}),
      };

      const trialApi = await noCtx.request.post(
        `${ADMIN_URL}/api/v1/admin/exams/${sp2e.id}/preview/${previewId}/trial`,
        { headers: apiHeaders },
      );
      expect(trialApi.status()).toBe(403);

      const paperApi = await noCtx.request.get(
        `${ADMIN_URL}/api/v1/admin/exams/${sp2e.id}/preview/${previewId}/paper`,
        { headers: apiHeaders },
      );
      expect(paperApi.status()).toBe(403);

      const gradeApi = await noCtx.request.post(
        `${ADMIN_URL}/api/v1/admin/exams/${sp2e.id}/preview/${previewId}/grade`,
        { headers: apiHeaders, data: { answers: {} } },
      );
      expect(gradeApi.status()).toBe(403);
    } finally {
      await noCtx.close();
    }
  });

  test("ảnh màn 8 ô chuẩn cho màn hình làm thử đề và kết quả làm thử (AC-29)", async ({ browser }) => {
    const admin = await adminApi();
    const sp2e = await findExamByCode(admin, "DS-SP2E-MATH-2025");

    const seedCtx = await browser.newContext();
    await loginAdmin(seedCtx, "vi");
    const seedPage = await seedCtx.newPage();
    const { trialUrl, trialPage } = await generateAndOpenTrial(seedPage, seedCtx, sp2e.id);
    await trialPage.close();
    const adminCookies = await seedCtx.cookies();
    await seedCtx.close();

    const matrixBrowser = await launchMatrixBrowser();
    try {
      const cells = matrixCells();
      const allowLanguage = [/^FA4/i, /^fa4\./i];
      const overflowOpts = { scope: "main", allowAccessibleScroller: true } as const;

      for (const cell of cells) {
        const ctx = await openMatrixContext(matrixBrowser, cell, ADMIN_URL);
        try {
          await ctx.addCookies(adminCookies);
          await applyMatrixCookies(ctx, cell, ADMIN_URL);
          const page = await ctx.newPage();

          await page.goto(trialUrl);
          const submitBtn = page.getByRole("button", { name: /Nộp bài & Chấm thử|Submit & Grade/ });
          await expect(submitBtn).toBeVisible({ timeout: 20_000 });
          await waitTrialSettled(page);
          await expectMatrixClean(page, "fa4-trial-paper", cell, {
            allowLanguage,
            elementOverflow: overflowOpts,
          });

          await submitBtn.click();

          await expect(
            page.getByRole("heading", { level: 1, name: /Kết quả làm thử|Trial results/ }),
          ).toBeVisible({ timeout: 20_000 });
          await waitTrialSettled(page);
          await expectMatrixClean(page, "fa4-trial-result", cell, {
            allowLanguage,
            elementOverflow: overflowOpts,
          });
        } finally {
          await ctx.close();
        }
      }
    } finally {
      await matrixBrowser.close();
    }
  });
});
