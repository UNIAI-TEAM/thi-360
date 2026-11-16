import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
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
