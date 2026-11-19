import { expect, test, type Page } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  attachAssetToQuestions,
  collectIssues,
  detachAssetFromQuestions,
  continueToReviewInUi,
  examCode,
  fillExamStep1InUi,
  listReadyQuestions,
  submitExamStep1InUi,
  uploadQuestionAsset,
  type CheckResult,
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
 * Nghiệm thu bổ sung H-SCA/V-SAT (QA addendum mục 8, H48): bảy kỳ thi cũ, mỗi kỳ một bài theo môn Toán. Kiểm tra trước khi
 * có câu đính kèm, rồi gắn một tệp PDF đọc được vào mọi câu Sẵn sàng của môn Toán (nên chắc chắn có câu đính kèm trong đề
 * chứng minh) và kiểm tra lại: kết quả giống hệt và không có ATTACHMENT_MISSING. Câu Toán là dữ liệu dùng chung của kho nên
 * tệp được gỡ trong `finally` (mỗi câu trả về danh sách đính kèm ban đầu, kể cả khi kịch bản hỏng giữa chừng); kho chỉ có
 * thêm phiên bản của câu, đính kèm và nội dung không đổi giữa các lần chạy.
 */
test("Bảy kỳ thi cũ: kết quả kiểm tra không đổi khi câu có tệp đính kèm", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();

  const ids = new Map<UiExamKind, string>();
  const before = new Map<UiExamKind, CheckDigest>();
  for (const kind of OLDER_KINDS) {
    const id = await createToanExam(page, kind);
    ids.set(kind, id);
    const check = await continueToReviewInUi(page, id);
    expect(check.status, `${kind}: kiểm tra cơ sở`).not.toBe("UNCHECKED");
    before.set(kind, digest(check));
  }

  const questions = await listReadyQuestions(admin, "TOAN");
  expect(questions.length, "không có câu Toán Sẵn sàng để gắn tệp").toBeGreaterThan(0);
  const assetId = await uploadQuestionAsset(admin, "w2b-att.pdf", "application/pdf", PDF);
  try {
    const attached = await attachAssetToQuestions(admin, questions, assetId);
    expect(attached.failed, `gắn tệp lỗi ở ${attached.failed}/${questions.length} câu`).toBe(0);
    expect(attached.ok).toBe(questions.length);

    for (const kind of OLDER_KINDS) {
      const check = await recheck(page, ids.get(kind)!);
      expect(collectIssues(check).map((issue) => issue.code), `${kind}: ATTACHMENT_MISSING`).not.toContain("ATTACHMENT_MISSING");
      expect(digest(check), `${kind}: kết quả kiểm tra sau khi gắn tệp`).toEqual(before.get(kind));
    }
  } finally {
    const detached = await detachAssetFromQuestions(admin, questions, assetId);
    expect(detached.failed, `gỡ tệp lỗi ở ${detached.failed}/${questions.length} câu`).toBe(0);
  }
});
