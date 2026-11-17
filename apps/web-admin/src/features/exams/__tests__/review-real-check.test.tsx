import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { ExamPreviewPanel } from "../components/exam-preview-panel";
import { ExamReviewCheck } from "../components/exam-review-check";
import { getExamCheck } from "../review-api";
import { checkResultWire, EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, RUN_ID } from "./fake-exams-server";

const POLL_MS = 2000;
const CHECK_PATH = `${EXAM_PATH}/check`;
const RUN_PATH = `${CHECK_PATH}/runs/${RUN_ID}`;

const running = { status: 200, body: { runId: RUN_ID, state: "RUNNING" } };
const done = {
  status: 200,
  body: { runId: RUN_ID, state: "DONE", result: { ...checkResultWire("VALID"), timeLimitSeconds: 120 } },
};

let server: FakeServer;

beforeEach(() => {
  server = new FakeServer()
    .on("POST", CHECK_PATH, (request) =>
      request.url.searchParams.get("deep") === "true"
        ? { status: 202, body: { runId: RUN_ID } }
        : { status: 200, body: checkResultWire("UNCONFIRMED") },
    )
    .on("GET", CHECK_PATH, { status: 200, body: checkResultWire("UNCONFIRMED") });
});

afterEach(() => {
  vi.useRealTimers();
});

async function tick(ms = POLL_MS) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

const runPolls = () => server.calls("GET", RUN_PATH).length;

/** Màn Kiểm tra với API thật: chu kỳ hỏi 2 giây do test tiến tay (`shouldAdvanceTime` giữ waitFor trên đồng hồ thật). */
async function startDeepCheck() {
  server.install();
  vi.useFakeTimers({ shouldAdvanceTime: true });
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  const exam = mapExamDetail(examDetailWire());
  const view = renderWithProviders(<ExamReviewCheck exam={exam} onPreview={() => {}} active />);
  await user.click(await screen.findByRole("button", { name: "Kiểm tra kỹ" }));
  await waitFor(() => expect(runPolls()).toBe(1));
  return view;
}

describe("kiểm tra kỹ, nhánh thật", () => {
  it("hỏi theo runId cho tới khi DONE rồi dừng; kết quả mới thay kết quả cũ", async () => {
    server.on("GET", RUN_PATH, running, running, done);
    await startDeepCheck();
    expect(server.calls("POST", CHECK_PATH).some((request) => request.url.searchParams.get("deep") === "true")).toBe(true);
    expect(screen.getByText("Đang kiểm tra kỹ")).toBeInTheDocument();

    await tick();
    expect(runPolls()).toBe(2);
    await tick();
    expect(runPolls()).toBe(3);
    await waitFor(() => expect(screen.queryByText("Đang kiểm tra kỹ")).not.toBeInTheDocument());
    expect(screen.getByText(/giới hạn xử lý 120 giây/)).toBeInTheDocument();

    await tick(POLL_MS * 3);
    expect(runPolls()).toBe(3);
  });

  it("FAILED cũng là trạng thái cuối: dừng hỏi và báo lần chạy bị ngắt", async () => {
    server.on("GET", RUN_PATH, running, {
      status: 200,
      body: { runId: RUN_ID, state: "FAILED", errorCode: "CHECK_RUN_ABANDONED" },
    });
    await startDeepCheck();

    await tick();
    expect(await screen.findByText("Kiểm tra kỹ không hoàn tất")).toBeInTheDocument();
    expect(screen.getByText(/Lần chạy nền bị ngắt trước khi có kết quả/)).toBeInTheDocument();

    const polls = runPolls();
    await tick(POLL_MS * 3);
    expect(runPolls()).toBe(polls);
  });

  it("lần chạy hết hạn (404) báo lỗi ngay, không thử lại", async () => {
    server.on("GET", RUN_PATH, running, { status: 404, body: { code: "CHECK_RUN_NOT_FOUND" } });
    await startDeepCheck();

    await tick();
    expect(await screen.findByText("Kiểm tra kỹ không hoàn tất")).toBeInTheDocument();
    await tick(POLL_MS * 3);
    // Một lần hỏi đầu (RUNNING) và một lần 404: không còn lần hỏi lại nào.
    expect(runPolls()).toBe(2);
  });

  it("lỗi máy chủ 5xx khi hỏi lần chạy được thử lại hai lần rồi mới báo lỗi", async () => {
    server.on("GET", RUN_PATH, running, { status: 503, body: { code: "UNAVAILABLE" } });
    await startDeepCheck();

    await tick();
    await tick();
    await tick();
    expect(await screen.findByText("Kiểm tra kỹ không hoàn tất")).toBeInTheDocument();
    // 1 lần RUNNING + 1 lần 503 + 2 lần thử lại.
    expect(runPolls()).toBe(4);
  });

  it("kết quả hết hiệu lực vì lý do lạ thì hiện câu chung, không in khóa dịch thô", async () => {
    new FakeServer()
      .on("GET", CHECK_PATH, { status: 200, body: { ...checkResultWire("VALID"), stale: true, invalidReason: "SOMETHING_NEW" } })
      .install();
    renderWithProviders(<ExamReviewCheck exam={mapExamDetail(examDetailWire())} onPreview={() => {}} active />);

    expect(await screen.findByText(/Nguồn câu hỏi hoặc cấu hình đã đổi sau lần kiểm tra này/)).toBeInTheDocument();
    expect(screen.queryByText(/staleReason/)).not.toBeInTheDocument();
  });

  it("gỡ màn khỏi cây thì dừng hỏi dù lần chạy chưa xong", async () => {
    server.on("GET", RUN_PATH, running);
    const view = await startDeepCheck();
    await tick();
    expect(runPolls()).toBe(2);

    view.unmount();
    await tick(POLL_MS * 3);
    expect(runPolls()).toBe(2);
  });
});

describe("xem trước, nhánh thật", () => {
  async function renderPreview() {
    server.on("GET", EXAM_PATH, { status: 200, body: examDetailWire() }).install();
    const exam = mapExamDetail(examDetailWire());
    const check = await getExamCheck({ examId: EXAM_ID });
    renderWithProviders(
      <ExamPreviewPanel
        exam={exam}
        check={check}
        initialPathId="default"
        autoGenerate
        version={null}
        onClearVersion={() => {}}
        onShowCheck={() => {}}
      />,
    );
  }

  it("422 PREVIEW_SUBJECTS_INVALID hiện câu tiếng Việt về số môn của phương án chọn môn", async () => {
    server.on("POST", `${EXAM_PATH}/preview`, {
      status: 422,
      body: { code: "PREVIEW_SUBJECTS_INVALID", message: "subjectCodes must match chooseSubjects" },
    });
    await renderPreview();

    // Câu hiện ở thân màn và ở vùng đọc (aria-live): cả hai đều là tiếng Việt.
    expect(
      (await screen.findAllByText("Xem trước đường có phương án chọn môn cần gửi đúng số môn thuộc phương án.")).length,
    ).toBeGreaterThan(0);
    expect(screen.queryByText(/subjectCodes must match/)).not.toBeInTheDocument();
  });

  it("lỗi khác 422 PREVIEW_SUBJECTS_INVALID hiện lỗi chung", async () => {
    server.on("POST", `${EXAM_PATH}/preview`, { status: 500, body: { code: "INTERNAL" } });
    await renderPreview();

    await waitFor(() => expect(server.calls("POST", `${EXAM_PATH}/preview`)).toHaveLength(1));
    expect(
      screen.queryByText("Xem trước đường có phương án chọn môn cần gửi đúng số môn thuộc phương án."),
    ).not.toBeInTheDocument();
    expect((await screen.findAllByText(/Không sinh được đề xem trước/)).length).toBeGreaterThan(0);
  });
});
