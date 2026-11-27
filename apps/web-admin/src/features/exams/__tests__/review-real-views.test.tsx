import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { ExamReviewCheck } from "../components/exam-review-check";
import { ExamVersionsView } from "../components/exam-versions-view";
import { checkResultWire, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

vi.mock("next/navigation", () => ({
  usePathname: () => "/exams/ex/versions",
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

const PUBLISH_PATH = `${EXAM_PATH}/publish`;
const DISCONTINUE_PATH = `${EXAM_PATH}/discontinue`;
const SERVING_PATH = { pathId: "default", choices: [] };

let server: FakeServer;

beforeEach(() => {
  window.history.replaceState(null, "", "/");
  server = new FakeServer()
    .on("GET", EXAM_PATH, {
      status: 200,
      body: examDetailWire({
        status: "PUBLISHED",
        servingVersion: 1,
        check: { status: "VALID", issueCount: 0, checkedAt: "2026-11-17T01:00:00Z" },
      }),
    })
    .on("GET", `${EXAM_PATH}/check`, { status: 200, body: checkResultWire("VALID") })
    .on("GET", `${EXAM_PATH}/versions`, {
      status: 200,
      body: {
        examId: "11111111-1111-1111-1111-111111111111",
        workingCopy: {
          editedBy: { id: "u1", name: "Cô Lan" },
          savedAt: "2026-11-17T02:00:00Z",
          baseVersion: 1,
          check: { status: "VALID", issueCount: 0, checkedAt: "2026-11-17T01:00:00Z" },
          unpublishedPaths: [SERVING_PATH],
        },
        changes: [
          { id: "c1", kind: "ORDER_CHANGED", where: "Phần 1 · Toán", before: "Cố định", after: "Đảo" },
        ],
        items: [
          {
            version: 1,
            state: "SERVING",
            publishedAt: "2026-11-10T01:00:00Z",
            attemptCount: 0,
            publishedPaths: [SERVING_PATH],
            servingPaths: [SERVING_PATH],
          },
        ],
      },
    })
    .install();
});

async function openPublishDialog() {
  const user = userEvent.setup();
  renderWithProviders(<ExamVersionsView examId="11111111-1111-1111-1111-111111111111" />);
  await user.click(await screen.findByRole("button", { name: "Công bố" }));
  const dialog = await screen.findByRole("dialog");
  const submit = await within(dialog).findByRole("button", { name: "Công bố 1 đường lựa chọn" });
  return { user, dialog, submit };
}

const keysOf = () => server.calls("POST", PUBLISH_PATH).map((request) => request.headers.get("Idempotency-Key"));

describe("màn Phiên bản, nhánh thật", () => {
  it("thay đổi loại ORDER_CHANGED hiện nhãn Đổi thứ tự cùng giá trị trước và sau", async () => {
    renderWithProviders(<ExamVersionsView examId="11111111-1111-1111-1111-111111111111" />);

    expect(await screen.findByRole("heading", { name: "So sánh bản sửa với v1" })).toBeInTheDocument();
    expect(screen.getByText("Đổi thứ tự")).toBeInTheDocument();
    expect(screen.getByText("Phần 1 · Toán")).toBeInTheDocument();
    expect(screen.getByText("Cố định")).toBeInTheDocument();
    expect(screen.getByText("Đảo")).toBeInTheDocument();
  });
});

describe("công bố, nhánh thật", () => {
  it("409 CHECK_REQUIRED lý do SOURCE_CHANGED hiện câu tiếng Việt và liên kết Kiểm tra lại; thử lại giữ Idempotency-Key", async () => {
    server.on(
      "POST",
      PUBLISH_PATH,
      { status: 409, body: { code: "CHECK_REQUIRED", message: "Check required", fieldErrors: [{ field: "reason", code: "SOURCE_CHANGED" }] } },
      { status: 409, body: { code: "CHECK_REQUIRED", fieldErrors: [{ field: "reason", code: "SOURCE_CHANGED" }] } },
    );
    const { user, dialog, submit } = await openPublishDialog();

    await user.click(submit);
    expect(await within(dialog).findByText(/Nguồn câu hỏi đã đổi sau lần kiểm tra gần nhất/)).toBeInTheDocument();
    expect(within(dialog).queryByText(/Check required/)).not.toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: "Kiểm tra lại" })).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Công bố 1 đường lựa chọn" }));
    await waitFor(() => expect(keysOf()).toHaveLength(2));
    const [first, second] = keysOf();
    expect(first).toMatch(/^[0-9a-f-]{36}$/);
    expect(second).toBe(first);
    expect(server.calls("POST", PUBLISH_PATH)[0]!.body).toEqual({ pathIds: ["default"], expectedRevision: 7 });
  });

  it("409 CHECK_REQUIRED lý do PATH_NOT_VALID nêu đường không còn hợp lệ", async () => {
    server.on("POST", PUBLISH_PATH, {
      status: 409,
      body: { code: "CHECK_REQUIRED", fieldErrors: [{ field: "reason", code: "PATH_NOT_VALID" }] },
    });
    const { user, dialog, submit } = await openPublishDialog();

    await user.click(submit);

    expect(await within(dialog).findByText(/Có đường lựa chọn không còn hợp lệ/)).toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: "Kiểm tra lại" })).toBeInTheDocument();
  });

  it("lý do lạ thì báo chung, không in mã thô; 412 báo bài đã được sửa ở nơi khác", async () => {
    server.on(
      "POST",
      PUBLISH_PATH,
      { status: 409, body: { code: "CHECK_REQUIRED", fieldErrors: [{ field: "reason", code: "SOMETHING_NEW" }] } },
      { status: 412, body: { code: "EXAM_REVISION_CONFLICT" } },
    );
    const { user, dialog, submit } = await openPublishDialog();

    await user.click(submit);
    expect(await within(dialog).findByText("Cấu hình cần được kiểm tra lại trước khi công bố.")).toBeInTheDocument();
    expect(within(dialog).queryByText(/SOMETHING_NEW/)).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Công bố 1 đường lựa chọn" }));
    expect(await within(dialog).findByText(/Bài thi vừa được sửa ở nơi khác/)).toBeInTheDocument();
  });

  it("thử lại sau lỗi mạng giữ khóa; công bố xong thì lần công bố sau dùng khóa mới", async () => {
    server.on(
      "POST",
      PUBLISH_PATH,
      { status: 503, body: { code: "UNAVAILABLE" } },
      { status: 200, body: { examId: "11111111-1111-1111-1111-111111111111", version: 2, publishedPathIds: ["default"], keptPaths: [] } },
    );
    const { user, dialog, submit } = await openPublishDialog();

    await user.click(submit);
    expect(await within(dialog).findByText("Không công bố được. Thử lại.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Công bố 1 đường lựa chọn" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    const [failed, retried] = keysOf();
    expect(retried).toBe(failed);

    await user.click(await screen.findByRole("button", { name: "Công bố" }));
    await user.click(await screen.findByRole("button", { name: "Công bố 1 đường lựa chọn" }));
    await waitFor(() => expect(keysOf()).toHaveLength(3));
    expect(keysOf()[2]).not.toBe(failed);
  });
});

describe("ngừng bán, nhánh thật", () => {
  it("gửi Idempotency-Key và expectedRevision; thử lại sau lỗi mạng giữ khóa", async () => {
    server.on(
      "POST",
      DISCONTINUE_PATH,
      { status: 503, body: { code: "UNAVAILABLE" } },
      { status: 200, body: examDetailWire({ status: "DISCONTINUED", revision: 8 }) },
    );
    const user = userEvent.setup();
    renderWithProviders(<ExamVersionsView examId="11111111-1111-1111-1111-111111111111" />);

    await user.click(await screen.findByRole("button", { name: "Ngừng bán" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Ngừng bán" }));
    expect(await within(dialog).findByText("Không ngừng bán được bài thi. Thử lại.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Ngừng bán" }));
    await waitFor(() => expect(server.calls("POST", DISCONTINUE_PATH)).toHaveLength(2));

    const [first, second] = server.calls("POST", DISCONTINUE_PATH);
    expect(first!.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
    expect(second!.headers.get("Idempotency-Key")).toBe(first!.headers.get("Idempotency-Key"));
    expect(first!.body).toEqual({ expectedRevision: 7 });
  });

  it("409 hoặc 412 báo bài không còn Đang phục vụ", async () => {
    server.on("POST", DISCONTINUE_PATH, { status: 409, body: { code: "EXAM_NOT_PUBLISHED" } });
    const user = userEvent.setup();
    renderWithProviders(<ExamVersionsView examId="11111111-1111-1111-1111-111111111111" />);

    await user.click(await screen.findByRole("button", { name: "Ngừng bán" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Ngừng bán" }));

    expect(await within(dialog).findByText(/không còn ở trạng thái Đang phục vụ/)).toBeInTheDocument();
  });
});

describe("rulesChangedOnCheckPage", () => {
  const CHECK = `${EXAM_PATH}/check`;
  const STRUCTURE = `/exams/${EXAM_PATH.split("/").pop()}/structure`;
  const issue = (code: string, sectionId: string, subjectCode: string) => ({
    key: `${code}:${sectionId}`,
    code,
    severity: "ERROR",
    // The backend files SECTION_SET_COUNT_RANGE under QUESTION_SET and SHORT_ANSWER_MIN under STRUCTURE.
    group: code === "SECTION_SET_COUNT_RANGE" ? "QUESTION_SET" : "STRUCTURE",
    partId: "part-1",
    sectionId,
    subjectCode,
    affectedCodes: [],
  });
  const SET_ISSUE = issue("SECTION_SET_COUNT_RANGE", "sec-phys", "LY");
  const SHORT_ISSUE = issue("SHORT_ANSWER_MIN", "sec-chem", "HOA");
  const STALE_CHECK = {
    ...checkResultWire("INVALID"),
    stale: true,
    invalidReason: "RULES_CHANGED",
    issueCount: 2,
    groups: [
      { group: "QUESTION_SET", status: "FAIL", issues: [SET_ISSUE] },
      { group: "STRUCTURE", status: "FAIL", issues: [SHORT_ISSUE] },
    ],
    paths: [{ pathId: "default", result: "INVALID", issues: [] }],
  };

  function checkPageServer() {
    return new FakeServer()
      .on("POST", CHECK, { status: 200, body: { ...checkResultWire("INVALID"), issueCount: 2, groups: STALE_CHECK.groups, paths: STALE_CHECK.paths } })
      .on("GET", CHECK, { status: 200, body: STALE_CHECK })
      .install();
  }

  it("vi: hai mã mới đúng môn và đích, lý do RULES_CHANGED bằng chữ, nút kiểm tra lại gửi kiểm tra", async () => {
    server = checkPageServer();
    const user = userEvent.setup();
    renderWithProviders(<ExamReviewCheck exam={mapExamDetail(examDetailWire())} onPreview={() => {}} active />);

    expect(
      await screen.findByText("Quy tắc kiểm tra đã đổi sau lần kiểm tra này. Kiểm tra lại cả bài trước khi công bố."),
    ).toBeInTheDocument();
    const errors = within(await screen.findByTestId("findings-errors"));
    // Không hiện mã kỹ thuật, chỉ câu (UNI-2889).
    expect(errors.queryByText("SECTION_SET_COUNT_RANGE")).toBeNull();
    expect(errors.queryByText("SHORT_ANSWER_MIN")).toBeNull();
    expect(errors.queryByText("INFO_MISSING")).not.toBeInTheDocument();
    expect(errors.getByText(/LY: số chùm phải từ 1 đến 2/)).toBeInTheDocument();
    expect(errors.getByText(/HOA: cần ít nhất 1 câu Điền đáp án \(loại Trả lời ngắn\)/)).toBeInTheDocument();
    expect(errors.getByText("LY")).toBeInTheDocument();
    expect(errors.getByText("HOA")).toBeInTheDocument();
    expect(errors.getAllByRole("link").map((link) => link.getAttribute("href"))).toEqual([
      `${STRUCTURE}?section=sec-phys&from=review`,
      `${STRUCTURE}?section=sec-chem&from=review`,
    ]);

    expect(server.calls("POST", CHECK)).toHaveLength(0);
    await user.click(screen.getAllByRole("button", { name: "Kiểm tra lại" })[0]!);
    await waitFor(() => expect(server.calls("POST", CHECK)).toHaveLength(1));
    // RULES_CHANGED clears only on a whole-exam run: the button sends no part or section scope.
    const sent = (server.calls("POST", CHECK)[0]!.body ?? {}) as { scope?: string; partId?: string; sectionId?: string };
    expect(sent.scope === undefined || sent.scope === "EXAM").toBe(true);
    expect(sent.partId).toBeUndefined();
    expect(sent.sectionId).toBeUndefined();
  });

  it("en: cùng kết quả bằng chữ tiếng Anh, không còn khóa dịch thô", async () => {
    server = checkPageServer();
    renderWithProviders(<ExamReviewCheck exam={mapExamDetail(examDetailWire())} onPreview={() => {}} active />, {
      locale: "en",
      messages: en,
    });

    expect(
      await screen.findByText("The check rules changed after this check. Run the check on the whole exam again before publishing."),
    ).toBeInTheDocument();
    const errors = within(await screen.findByTestId("findings-errors"));
    expect(errors.getByText(/LY: the number of sets must be 1 to 2/)).toBeInTheDocument();
    expect(errors.getByText(/HOA: at least 1 short-answer question is required/)).toBeInTheDocument();
    expect(errors.getAllByRole("link").map((link) => link.getAttribute("href"))).toEqual([
      `${STRUCTURE}?section=sec-phys&from=review`,
      `${STRUCTURE}?section=sec-chem&from=review`,
    ]);
    expect(screen.queryByText(/staleReason|exams\.review/)).not.toBeInTheDocument();
  });

  it("vi: 409 CHECK_REQUIRED lý do RULES_CHANGED ở hộp công bố hiện câu tiếng Việt và liên kết Kiểm tra lại", async () => {
    server.on("POST", PUBLISH_PATH, {
      status: 409,
      body: { code: "CHECK_REQUIRED", message: "Check required", fieldErrors: [{ field: "reason", code: "RULES_CHANGED" }] },
    });
    const { user, dialog, submit } = await openPublishDialog();

    await user.click(submit);

    expect(
      await within(dialog).findByText("Quy tắc kiểm tra đã được cập nhật sau lần kiểm tra gần nhất. Kiểm tra lại cả bài rồi công bố."),
    ).toBeInTheDocument();
    expect(within(dialog).queryByText("Cấu hình cần được kiểm tra lại trước khi công bố.")).not.toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: "Kiểm tra lại" })).toHaveAttribute("href", `/exams/${EXAM_PATH.split("/").pop()}/review`);
  });
});
