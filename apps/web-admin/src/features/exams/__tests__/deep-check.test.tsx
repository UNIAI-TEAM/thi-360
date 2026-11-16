import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { questionStore } from "@/features/questions/mock/store";
import { MockApiError } from "@/lib/mock/content-mock";
import { renderWithProviders } from "@/test/render";
import { getExam } from "../api";
import { ExamReviewCheck } from "../components/exam-review-check";
import { reviewStore } from "../mock/review-store";
import { examStore } from "../mock/store";
import { defaultExamReviewApiDeps, type ExamReviewApiDeps, getExamCheck } from "../review-api";
import type { ExamCheckResult, ExamCheckRun } from "../review-types";

const HSA = "ex-hsa-f-001";
const POLL_MS = 2000;

beforeEach(() => {
  examStore.reset();
  questionStore.reset();
  reviewStore.reset();
});

afterEach(() => {
  vi.useRealTimers();
});

/** Kết quả sau kiểm tra kỹ: cách chọn chưa xác nhận được đã có kết luận, giới hạn xử lý 120 giây. */
function confirmed(result: ExamCheckResult): ExamCheckResult {
  return {
    ...result,
    timeLimitSeconds: 120,
    durationSeconds: 77,
    paths: result.paths.map((path) => ({
      ...path,
      selections: path.selections.map((selection) =>
        selection.result === "UNCONFIRMED" ? { ...selection, result: "VALID" as const, issues: [] } : selection,
      ),
    })),
  };
}

const running: ExamCheckRun = { runId: "run-1", state: "RUNNING", result: null, errorCode: null };

async function setup({ startFails = false }: { startFails?: boolean } = {}) {
  const exam = await getExam(HSA);
  const initial = (await getExamCheck({ examId: HSA }))!;
  // `shouldAdvanceTime`: waitFor của Testing Library chạy trên đồng hồ thật; chu kỳ hỏi 2 giây do test tiến tay.
  vi.useFakeTimers({ shouldAdvanceTime: true });
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  const getCheckRun = vi.fn(async (_examId: string, runId: string): Promise<ExamCheckRun> => ({ ...running, runId }));
  let started = 0;
  const startDeepCheck = vi.fn(async () => {
    if (startFails) throw new MockApiError(503, "UNAVAILABLE");
    started += 1;
    return { runId: `run-${started}` };
  });
  const deps: ExamReviewApiDeps = {
    ...defaultExamReviewApiDeps,
    getExamCheck: vi.fn(async () => initial),
    startDeepCheck,
    getCheckRun,
  };
  const element = (active = true) => (
    <ExamReviewCheck exam={exam} onPreview={() => {}} deps={deps} active={active} />
  );
  const view = renderWithProviders(element());
  return { user, getCheckRun, startDeepCheck, initial, view, element };
}

async function tick(ms = POLL_MS) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

async function startFromTopCard(user: UserEvent) {
  await user.click(await screen.findByRole("button", { name: "Kiểm tra kỹ" }));
}

const liveText = () =>
  screen
    .getAllByRole("status")
    .map((node) => node.textContent ?? "")
    .join(" ");

describe("Kiểm tra kỹ", () => {
  it("chạy nền: hỏi mỗi 2 giây, kết quả cũ còn hiện tới khi xong, rồi thay kết quả và dừng hỏi", async () => {
    const { user, getCheckRun, startDeepCheck, initial } = await setup();
    // Hai lần hỏi đầu còn chạy, lần thứ ba xong.
    getCheckRun.mockImplementation(async (_examId, runId) =>
      getCheckRun.mock.calls.length < 3
        ? { ...running, runId }
        : { runId, state: "DONE", result: confirmed(initial), errorCode: null },
    );

    expect(await screen.findByText(/giới hạn xử lý 30 giây/)).toBeInTheDocument();
    expect(screen.getByText(/12 trường hợp: 1 hợp lệ, 8 không hợp lệ, 3 chưa xác nhận được\./)).toBeInTheDocument();
    expect(screen.getByText(/Có kết quả chưa xác nhận được trong giới hạn xử lý/)).toBeInTheDocument();
    await startFromTopCard(user);

    expect(startDeepCheck).toHaveBeenCalledWith(HSA);
    expect(await screen.findByText("Đang kiểm tra kỹ")).toBeInTheDocument();
    expect(liveText()).toContain("Đang kiểm tra kỹ");
    // Kết quả lần trước vẫn hiện trong lúc chờ; không chạy song song một lần kiểm tra thường.
    expect(screen.getByRole("table", { name: "Kết quả kiểm tra của 2 đường lựa chọn" })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Kiểm tra lại" })[0]).toHaveAttribute("aria-disabled", "true");
    await waitFor(() => expect(getCheckRun).toHaveBeenCalledTimes(1));
    expect(getCheckRun).toHaveBeenLastCalledWith(HSA, "run-1");

    await tick();
    expect(getCheckRun).toHaveBeenCalledTimes(2);
    expect(screen.getByText("Đang kiểm tra kỹ")).toBeInTheDocument();
    await tick();
    expect(getCheckRun).toHaveBeenCalledTimes(3);

    await waitFor(() => expect(screen.queryByText("Đang kiểm tra kỹ")).not.toBeInTheDocument());
    expect(screen.getByText(/giới hạn xử lý 120 giây/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Kiểm tra kỹ" })).not.toBeInTheDocument();
    expect(screen.getByText(/12 trường hợp: 4 hợp lệ, 8 không hợp lệ, 0 chưa xác nhận được\./)).toBeInTheDocument();
    expect(liveText()).toContain("Đã kiểm tra:");

    // Xong thì không hỏi nữa.
    await tick(POLL_MS * 3);
    expect(getCheckRun).toHaveBeenCalledTimes(3);
  });

  it("lần chạy lỗi hiện lỗi kèm Thử lại, thử lại bắt đầu lần chạy mới", async () => {
    const { user, getCheckRun, startDeepCheck, initial } = await setup();
    getCheckRun.mockImplementation(async (_examId, runId) =>
      runId === "run-1"
        ? { runId, state: "FAILED", result: null, errorCode: "CHECK_RUN_ABANDONED" }
        : { runId, state: "DONE", result: confirmed(initial), errorCode: null },
    );

    await startFromTopCard(user);
    expect(await screen.findByText("Kiểm tra kỹ không hoàn tất")).toBeInTheDocument();
    expect(screen.getByText(/Lần chạy nền bị ngắt trước khi có kết quả/)).toBeInTheDocument();
    expect(liveText()).toContain("Kiểm tra kỹ không hoàn tất");
    // Lỗi: dừng hỏi, kết quả cũ vẫn còn.
    const callsAtError = getCheckRun.mock.calls.length;
    await tick(POLL_MS * 2);
    expect(getCheckRun).toHaveBeenCalledTimes(callsAtError);
    expect(screen.getByRole("table", { name: "Kết quả kiểm tra của 2 đường lựa chọn" })).toBeInTheDocument();

    const alert = screen.getByText("Kiểm tra kỹ không hoàn tất").closest("div[class*='border-danger']") as HTMLElement;
    await user.click(within(alert).getByRole("button", { name: "Thử lại" }));
    expect(startDeepCheck).toHaveBeenCalledTimes(2);
    await waitFor(() => expect(getCheckRun).toHaveBeenLastCalledWith(HSA, "run-2"));
    await waitFor(() => expect(screen.queryByText("Kiểm tra kỹ không hoàn tất")).not.toBeInTheDocument());
    expect(screen.getByText(/giới hạn xử lý 120 giây/)).toBeInTheDocument();
  });

  it("không bắt đầu được lần chạy nền thì báo lỗi, không hỏi kết quả", async () => {
    const { user, getCheckRun, startDeepCheck } = await setup({ startFails: true });
    await startFromTopCard(user);
    expect(await screen.findByText("Kiểm tra kỹ không hoàn tất")).toBeInTheDocument();
    expect(screen.getByText(/Không lấy được kết quả của lần kiểm tra kỹ/)).toBeInTheDocument();
    expect(startDeepCheck).toHaveBeenCalledTimes(1);
    expect(getCheckRun).not.toHaveBeenCalled();
  });

  it("dừng hỏi khi rời màn: đổi tab thì tạm dừng và hỏi tiếp khi quay lại, gỡ khỏi cây thì dừng hẳn", async () => {
    const { user, getCheckRun, view, element } = await setup();
    await startFromTopCard(user);
    await waitFor(() => expect(getCheckRun).toHaveBeenCalledTimes(1));
    await tick();
    expect(getCheckRun).toHaveBeenCalledTimes(2);

    // Đổi tab: tab kiểm tra vẫn nằm trong DOM nhưng không hỏi nữa.
    view.rerender(element(false));
    await tick(POLL_MS * 3);
    expect(getCheckRun).toHaveBeenCalledTimes(2);
    expect(screen.getByText("Đang kiểm tra kỹ")).toBeInTheDocument();

    // Quay lại: hỏi tiếp lần chạy đang có, không bắt đầu lần mới.
    view.rerender(element(true));
    await waitFor(() => expect(getCheckRun).toHaveBeenCalledTimes(3));
    await tick();
    expect(getCheckRun).toHaveBeenCalledTimes(4);
    expect(getCheckRun.mock.calls.every(([, runId]) => runId === "run-1")).toBe(true);

    view.unmount();
    await tick(POLL_MS * 3);
    expect(getCheckRun).toHaveBeenCalledTimes(4);
  });
});
