import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";
import { AttemptDetailView } from "../components/attempt-detail-view";
import {
  ATTEMPT_1_ID,
  ATTEMPT_EXAM_KIND_FIXTURES,
  makeAttemptDetail,
  makeAttemptItem,
  makeAttemptPart,
  makeAttemptSession,
  PART_1_ID,
  PART_3_ID,
  renderAttemptsUi,
} from "./attempt-fixtures";

vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});

withRealApi();

beforeEach(() => {
  fakeApi.get("/api/v1/catalog/options", ok(ATTEMPT_EXAM_KIND_FIXTURES));
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("AttemptDetailView", () => {
  it("hiển thị thông tin lượt, các phần và hạn, dòng sự kiện và câu trả lời cuối kèm answerReceivedAt và answerSeq (AC-11)", async () => {
    const items = Array.from({ length: 25 }, (_, i) =>
      makeAttemptItem({
        itemId: `item-${i + 1}`,
        partId: i < 20 ? PART_1_ID : PART_3_ID,
        ord: i + 1,
        displayNo: i + 1,
        questionType: i === 1 ? "SHORT_ANSWER" : "SINGLE_CHOICE",
        answer:
          i === 1
            ? { type: "SHORT_ANSWER", text: "12,5" }
            : i === 24
              ? undefined
              : { type: "SINGLE_CHOICE", optionId: "opt-c" },
        answerReceivedAt: i === 24 ? undefined : "2026-11-17T02:06:10Z",
        answerSeq: i === 24 ? undefined : i + 10,
      }),
    );

    fakeApi.get(
      "/api/v1/admin/attempts/{id}",
      ok(
        makeAttemptDetail({
          examName: "Đề thi thử ĐGNL HSA số 3",
          parts: [
            makeAttemptPart({ partName: "Phần 1 · Tư duy định lượng" }),
            makeAttemptPart({
              partId: PART_3_ID,
              partCode: "P3",
              partName: "Phần 3 · Khoa học hoặc Tiếng Anh",
              ord: 3,
              state: "GENERATION_FAILED",
              startedAt: undefined,
              deadlineAt: undefined,
              closedAt: undefined,
              closeReason: undefined,
              generationTries: 4,
              generationError: "GENERATION_TIMEOUT",
              choice: { optionCode: "SCIENCE", subjectCodes: ["PHY", "CHEM", "BIO"] },
            }),
          ],
          items,
        }),
      ),
    );

    renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />);

    expect(await screen.findByRole("heading", { level: 1, name: "Lượt 7C1E4B9A" })).toBeInTheDocument();
    expect(screen.getByText("nguyenminhanh")).toBeInTheDocument();
    const examNameEl = screen.getByText("Đề thi thử ĐGNL HSA số 3");
    expect(examNameEl.closest("[data-exam-content]")).not.toBeNull();

    // Các phần và hạn
    const partsTable = screen.getByRole("table", { name: "Các phần của lượt 7C1E4B9A" });
    expect(within(partsTable).getByText("Phần 1 · Tư duy định lượng")).toBeInTheDocument();
    expect(within(partsTable).getByText("Phần 3 · Khoa học hoặc Tiếng Anh")).toBeInTheDocument();
    expect(within(partsTable).getByText("Sinh đề lỗi")).toBeInTheDocument();
    expect(within(partsTable).getByText("GENERATION_TIMEOUT")).toBeInTheDocument();

    // Dòng sự kiện
    const eventsHeading = screen.getByRole("heading", { level: 2, name: "Dòng sự kiện" });
    const eventsCard = eventsHeading.closest("div")!.parentElement!;
    expect(within(eventsCard).getByText("Bắt đầu lượt")).toBeInTheDocument();
    expect(within(eventsCard).getByText("Mở phần")).toBeInTheDocument();
    expect(within(eventsCard).getByText("Phiên SESS1234")).toBeInTheDocument();

    // Câu trả lời cuối kèm answerReceivedAt & answerSeq + phân trang & lọc phần
    const answersTable = screen.getByRole("table", { name: "Câu trả lời cuối của lượt 7C1E4B9A" });
    expect(within(answersTable).getByText("Câu 1")).toBeInTheDocument();
    expect(within(answersTable).getByText("12,5")).toBeInTheDocument();
    expect(within(answersTable).getByText("11")).toBeInTheDocument();
    expect(within(answersTable).queryByText("Câu 25")).not.toBeInTheDocument();

    // Chuyển trang 2 của bảng câu trả lời
    await userEvent.click(screen.getByRole("button", { name: "Trang 2" }));
    expect(within(answersTable).getByText("Câu 25")).toBeInTheDocument();
    expect(within(answersTable).getByText("Bỏ trống")).toBeInTheDocument();

    // Lọc theo phần 3
    await userEvent.click(screen.getByRole("combobox", { name: "Lọc theo phần" }));
    await userEvent.click(screen.getByRole("option", { name: "Phần 3 · Khoa học hoặc Tiếng Anh" }));
    expect(within(answersTable).getByText("Câu 21")).toBeInTheDocument();
    expect(within(answersTable).queryByText("Câu 1")).not.toBeInTheDocument();
  });

  it("chấm lại: nút chỉ hiện khi state GRADING_ERROR và có quyền GRADING.MODIFY (AC-12)", async () => {
    const detail = makeAttemptDetail({ state: "GRADING_ERROR", gradingError: "Missing rubric" });
    let calls = 0;
    fakeApi.get("/api/v1/admin/attempts/{id}", () => {
      calls += 1;
      if (calls === 1) return ok(detail);
      return ok(makeAttemptDetail({ state: "SCORED", displayScore: 112, maxScore: 150, gradingError: undefined }));
    });
    fakeApi.post(
      "/api/v1/admin/attempts/{id}/regrade",
      ok({ attemptId: ATTEMPT_1_ID, state: "SCORED", displayScore: 112, maxScore: 150 }),
    );

    const { unmount } = renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />);
    const regradeBtn = await screen.findByRole("button", { name: "Chấm lại" });
    await userEvent.click(regradeBtn);

    await waitFor(() => {
      expect(fakeApi.calls.some((c) => c.method === "POST" && c.path === "/api/v1/admin/attempts/{id}/regrade")).toBe(
        true,
      );
      expect(screen.queryByRole("button", { name: "Chấm lại" })).not.toBeInTheDocument();
    });
    unmount();

    // Khi state không phải GRADING_ERROR -> không có nút Chấm lại
    fakeApi.get("/api/v1/admin/attempts/{id}", ok(makeAttemptDetail({ state: "SCORED" })));
    const { unmount: unmountScored } = renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />);
    await screen.findByRole("heading", { level: 1, name: "Lượt 7C1E4B9A" });
    expect(screen.queryByRole("button", { name: "Chấm lại" })).not.toBeInTheDocument();
    unmountScored();

    // Khi thiếu quyền GRADING.MODIFY -> không có nút Chấm lại dù state = GRADING_ERROR
    fakeApi.get("/api/v1/admin/attempts/{id}", ok(detail));
    renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />, {
      session: makeAttemptSession({ permissions: ["ATTEMPT.VIEW", "ATTEMPT.COMPENSATE"] }),
    });
    await screen.findByRole("heading", { level: 1, name: "Lượt 7C1E4B9A" });
    expect(screen.queryByRole("button", { name: "Chấm lại" })).not.toBeInTheDocument();
  });

  it("đánh dấu hỏng: chặn lý do trống kèm focus, lý do hợp lệ gọi POST .../fault (AC-13)", async () => {
    let faulted = false;
    fakeApi.get("/api/v1/admin/attempts/{id}", () =>
      ok(
        makeAttemptDetail({
          state: faulted ? "SCORED" : "IN_PROGRESS",
          faultFlag: faulted ? "SYSTEM_FAULT" : undefined,
          faultReason: faulted ? "Phần 3 sinh đề lỗi 4 lần" : undefined,
        }),
      ),
    );
    fakeApi.post("/api/v1/admin/attempts/{id}/fault", () => {
      faulted = true;
      return ok({ attemptId: ATTEMPT_1_ID, state: "SCORED", faultFlag: "SYSTEM_FAULT" });
    });

    renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />);

    await userEvent.click(await screen.findByRole("button", { name: "Đánh dấu hỏng" }));
    const dialog = await screen.findByRole("dialog");

    // Nút xác nhận luôn bấm được; bấm khi trống -> báo lỗi và focus ô lý do
    const confirmBtn = within(dialog).getByRole("button", { name: "Đánh dấu hỏng và đóng lượt" });
    expect(confirmBtn).toBeEnabled();
    await userEvent.click(confirmBtn);

    const reasonBox = within(dialog).getByLabelText(/Lý do/);
    expect(await within(dialog).findByText("Nhập lý do đánh dấu lượt hỏng.")).toBeInTheDocument();
    expect(reasonBox).toHaveFocus();
    expect(fakeApi.calls.some((c) => c.path === "/api/v1/admin/attempts/{id}/fault")).toBe(false);

    // Nhập lý do hợp lệ và xác nhận
    await userEvent.type(reasonBox, "Phần 3 sinh đề lỗi 4 lần");
    await userEvent.click(confirmBtn);

    await waitFor(() => {
      const faultCall = fakeApi.calls.find(
        (c) => c.method === "POST" && c.path === "/api/v1/admin/attempts/{id}/fault",
      );
      expect(faultCall?.options?.body).toEqual({ reason: "Phần 3 sinh đề lỗi 4 lần" });
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });

  it("cấp bù: chỉ hiện khi đã đánh dấu hỏng & chưa cấp bù, gửi Idempotency-Key, và hiện lỗi 409 GRANT_REFUNDED trong dialog (AC-14)", async () => {
    // 1. Chưa đánh dấu hỏng -> không có nút Cấp bù 1 lượt
    fakeApi.get("/api/v1/admin/attempts/{id}", ok(makeAttemptDetail({ faultFlag: undefined, compensated: false })));
    const { unmount } = renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />);
    await screen.findByRole("heading", { level: 1, name: "Lượt 7C1E4B9A" });
    expect(screen.queryByRole("button", { name: "Cấp bù 1 lượt" })).not.toBeInTheDocument();
    unmount();

    // 2. Đã đánh dấu hỏng & chưa cấp bù -> kiểm tra 409 GRANT_REFUNDED giữ nguyên Idempotency-Key rồi thành công
    let compensated = false;
    fakeApi.get("/api/v1/admin/attempts/{id}", () =>
      ok(
        makeAttemptDetail({
          state: "SCORED",
          faultFlag: "SYSTEM_FAULT",
          faultReason: "Lỗi hệ thống",
          compensated,
          parts: [makeAttemptPart()],
        }),
      ),
    );
    let postAttempt = 0;
    fakeApi.post("/api/v1/admin/attempts/{id}/compensate", () => {
      postAttempt += 1;
      if (postAttempt === 1) return fail(409, { code: "GRANT_REFUNDED" });
      compensated = true;
      return ok({ attemptId: ATTEMPT_1_ID, compensated: true });
    });

    const { unmount: unmountFaulted } = renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />);
    await userEvent.click(await screen.findByRole("button", { name: "Cấp bù 1 lượt" }));
    const dialog = await screen.findByRole("dialog");

    const confirmBtn = within(dialog).getByRole("button", { name: "Cấp bù 1 lượt" });
    await userEvent.click(confirmBtn);
    const reasonBox = within(dialog).getByLabelText(/Lý do/);
    expect(await within(dialog).findByText("Nhập lý do cấp bù lượt.")).toBeInTheDocument();
    expect(reasonBox).toHaveFocus();

    await userEvent.type(reasonBox, "Cấp bù lượt do lỗi sinh đề");
    await userEvent.click(confirmBtn);

    // 409 GRANT_REFUNDED hiện rõ trong dialog (role="alert")
    const alert = await within(dialog).findByRole("alert");
    expect(alert).toHaveTextContent("Không cấp bù được: lần cấp mà lượt này đã dùng đã được hoàn tiền hết.");

    // Thử lại cùng lý do giữ nguyên cùng Idempotency-Key
    await userEvent.click(confirmBtn);
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

    const compCalls = fakeApi.calls.filter(
      (c) => c.method === "POST" && c.path === "/api/v1/admin/attempts/{id}/compensate",
    );
    expect(compCalls).toHaveLength(2);
    const key1 = (compCalls[0]?.options?.params as { header?: Record<string, string> })?.header?.["Idempotency-Key"];
    const key2 = (compCalls[1]?.options?.params as { header?: Record<string, string> })?.header?.["Idempotency-Key"];
    expect(key1).toBeTruthy();
    expect(key2).toBe(key1);
    expect(await screen.findByText("Đã cấp bù 1 lượt")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cấp bù 1 lượt" })).not.toBeInTheDocument();
    unmountFaulted();
  });

  it("theo quyền: thiếu ATTEMPT.COMPENSATE ẩn fault & compensate; thiếu cả hai ẩn khối Xử lý lượt (AC-15)", async () => {
    fakeApi.get(
      "/api/v1/admin/attempts/{id}",
      ok(
        makeAttemptDetail({
          state: "GRADING_ERROR",
          faultFlag: "SYSTEM_FAULT",
          compensated: false,
        }),
      ),
    );

    // 1. Có GRADING.MODIFY, thiếu ATTEMPT.COMPENSATE -> hiện khối Xử lý lượt + Chấm lại, ẩn Đánh dấu hỏng & Cấp bù
    const { unmount } = renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />, {
      session: makeAttemptSession({ permissions: ["ATTEMPT.VIEW", "GRADING.MODIFY"] }),
    });
    expect(await screen.findByRole("heading", { level: 2, name: "Xử lý lượt" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Chấm lại" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Đánh dấu hỏng" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cấp bù 1 lượt" })).not.toBeInTheDocument();
    unmount();

    // 2. Thiếu cả ATTEMPT.COMPENSATE và GRADING.MODIFY -> ẩn hoàn toàn khối Xử lý lượt
    renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />, {
      session: makeAttemptSession({ permissions: ["ATTEMPT.VIEW"] }),
    });
    await screen.findByRole("heading", { level: 1, name: "Lượt 7C1E4B9A" });
    expect(screen.queryByRole("heading", { level: 2, name: "Xử lý lượt" })).not.toBeInTheDocument();
  });
});
