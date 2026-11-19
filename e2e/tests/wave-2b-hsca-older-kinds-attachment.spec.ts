import { expect, test, type Page } from "@playwright/test";
import { ADMIN_URL, adminApi, createKnowledgeTree, loginAdmin } from "./helpers/editor-qa";
import {
  attachAssetToQuestions,
  collectIssues,
  continueToReviewInUi,
  createReadyQuestions,
  detachAssetFromQuestions,
  examCode,
  fillExamStep1InUi,
  narrowSectionsToKnowledge,
  planQuestionsForSections,
  submitExamStep1InUi,
  uploadQuestionAsset,
  type CheckResult,
  type QuestionSummaryLite,
  type UiExamKind,
} from "./helpers/wave-2b";

/** Gắn tệp vào hàng nghìn câu qua API nên cần thời hạn dài hơn các kịch bản khác. */
test.setTimeout(2_400_000);

const OLDER_KINDS: UiExamKind[] = ["HSA", "QDA", "BCA", "VACT", "SPT", "SP2E", "THPTQG"];

/** Tệp PDF nhỏ nhưng thật: được tải lên kho lưu trữ, nên đọc được (`ObjectStorage.exists`). */
const PDF = Buffer.from(
  "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n",
);

/** Phần của kết quả kiểm tra cần giữ nguyên: trạng thái, các vấn đề (mã/mức) và tổng của từng đường. */
interface CheckDigest {
  status: string;
  issues: string[];
  paths: Array<{ result?: string; totalQuestions?: number; totalPoints?: number }>;
}

function digest(check: CheckResult): CheckDigest {
  return {
    status: check.status,
    issues: collectIssues(check)
      .map((issue) => `${issue.code}/${issue.severity}`)
      .sort(),
    paths: check.paths.map((path) => ({ result: path.result, totalQuestions: path.totalQuestions, totalPoints: path.totalPoints })),
  };
}

/** Tạo bài theo môn Toán của một kỳ thi cũ qua bước 1; kỳ thi cần thời lượng riêng thì nhập 90 phút sau lần gửi bị chặn. */
async function createToanExam(page: Page, kind: UiExamKind): Promise<string> {
  const spec = {
    kind,
    scope: "SUBJECT",
    subject: "TOAN",
    code: examCode(`W2B-ATT-${kind}`),
    name: `${kind} Toán có câu đính kèm nghiệm thu 2B`,
  } as const;
  await fillExamStep1InUi(page, spec);
  try {
    return await submitExamStep1InUi(page, spec);
  } catch (error) {
    if (!/Thời lượng/.test(String(error))) throw error;
    await page.getByRole("textbox", { name: /^Thời lượng (của|chung)/ }).first().fill("90");
    return submitExamStep1InUi(page, spec);
  }
}

/** "Tiếp tục" của bước 2 trên bài `id` (mở lại bước 2 để kiểm tra lại). */
async function recheck(page: Page, id: string): Promise<CheckResult> {
  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await expect(page.getByLabel("Tổng hợp cấu trúc")).toBeVisible();
  return continueToReviewInUi(page, id);
}

/**
 * Nghiệm thu bổ sung H-SCA/V-SAT (QA addendum mục 8, H48): bảy kỳ thi cũ, mỗi kỳ một bài theo môn Toán thu hẹp về
 * một nút kiến thức riêng của lần chạy (R4-2, QA:22 — không đụng câu dữ liệu mẫu). Mỗi bài có ngân hàng riêng vừa
 * đủ lấp cấu trúc của nó (điểm, hình dạng, chỉ tiêu của từng khối) nên đề chắc chắn toàn câu của luồng. Kiểm tra
 * trước khi có tệp, gắn một tệp PDF đọc được vào đúng các câu đó và kiểm tra lại: kết quả giống hệt và không có
 * ATTACHMENT_MISSING. Sau đó gỡ tệp (câu của luồng, nhưng vẫn trả đính kèm về rỗng); lỗi gỡ chỉ báo nhẹ qua
 * `expect.soft` để không che lỗi nghiệp vụ đang bay (R4-3).
 */
test("Bảy kỳ thi cũ: kết quả kiểm tra không đổi khi câu có tệp đính kèm", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();

  const ids = new Map<UiExamKind, string>();
  const before = new Map<UiExamKind, CheckDigest>();
  const questions = new Map<UiExamKind, QuestionSummaryLite[]>();
  for (const kind of OLDER_KINDS) {
    const id = await createToanExam(page, kind);
    ids.set(kind, id);
    const tree = await createKnowledgeTree(admin, "TOAN", `ATT-${kind}`);
    const narrowed = await narrowSectionsToKnowledge(admin, id, "TOAN", tree.leafId);
    const pool = await createReadyQuestions(admin, planQuestionsForSections(narrowed, "TOAN", tree.leafId, kind));
    expect(pool.length, `${kind}: cần câu riêng cho mục Toán`).toBeGreaterThan(0);
    questions.set(kind, pool);
    const check = await continueToReviewInUi(page, id);
    expect(check.status, `${kind}: kiểm tra cơ sở`).not.toBe("UNCHECKED");
    before.set(kind, digest(check));
  }

  const all = [...questions.values()].flat();
  const assetId = await uploadQuestionAsset(admin, "w2b-att.pdf", "application/pdf", PDF);
  try {
    const attached = await attachAssetToQuestions(admin, all, assetId);
    expect(attached.failed, `gắn tệp lỗi ở ${attached.failed}/${all.length} câu`).toBe(0);
    expect(attached.ok).toBe(all.length);

    for (const kind of OLDER_KINDS) {
      const check = await recheck(page, ids.get(kind)!);
      expect(collectIssues(check).map((issue) => issue.code), `${kind}: ATTACHMENT_MISSING`).not.toContain("ATTACHMENT_MISSING");
      expect(digest(check), `${kind}: kết quả kiểm tra sau khi gắn tệp`).toEqual(before.get(kind));
    }
  } finally {
    const detached = await detachAssetFromQuestions(admin, all, assetId);
    console.log(`[wave-2b] gỡ tệp của luồng: ${detached.ok} câu, lỗi ${detached.failed}`);
    expect.soft(detached.failed, `gỡ tệp lỗi ở ${detached.failed}/${all.length} câu`).toBe(0);
  }
});
