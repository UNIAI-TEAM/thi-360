import { onlineManager, QueryClient } from "@tanstack/react-query";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GRADING_QUERY_KEYS } from "@/features/grading/keys";
import { SessionProvider } from "@/features/session/session-provider";
import viMessages from "@/messages/vi.json";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";
import { renderWithProviders } from "@/test/render";
import { ATTEMPTS_QUERY_KEYS } from "../keys";
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

vi.mock("next-intl/server", async () => {
  const { createTranslator } = await import("next-intl");
  return {
    getTranslations: async (namespace: string) =>
      createTranslator({ locale: "vi", messages: viMessages, namespace: namespace as "attempts" }),
  };
});
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

    const heading = await screen.findByRole("heading", { level: 1, name: "Lượt 7C1E4B9A" });
    expect(screen.getByText("nguyenminhanh")).toBeInTheDocument();
    // Tên đề hiện ở dòng phụ của đầu trang và ở thẻ thông tin lượt, cả hai đều đánh dấu nội dung đề.
    const examNameEls = screen.getAllByText("Đề thi thử ĐGNL HSA số 3");
    expect(examNameEls).toHaveLength(2);
    for (const el of examNameEls) expect(el.closest("[data-exam-content]")).not.toBeNull();

    // Đầu trang chi tiết (UNI-2825): lối quay lại ở trên tiêu đề, chip trạng thái cạnh tiêu đề, dòng phụ tên đề · tài khoản.
    const header = heading.closest('[data-slot="detail-header"]') as HTMLElement;
    expect(header).not.toBeNull();
    expect(header.parentElement!.closest(".shadow-xs")).toBeNull();
    const backLink = within(header).getByRole("link", { name: "Danh sách lượt thi" });
    expect(backLink).toHaveAttribute("href", "/attempts");
    expect(backLink.compareDocumentPosition(heading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const titleRow = heading.parentElement!;
    expect(within(titleRow).getByText("Đang làm")).toBeInTheDocument();
    expect(header).toHaveTextContent("Đề thi thử ĐGNL HSA số 3 · nguyenminhanh");

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

  it("hiện trạng thái ngoại tuyến với nút Thử lại và Thử lại gọi lại detail + examKinds (R1-27, R2-29)", async () => {
    let catalogCalls = 0;
    let detailCalls = 0;
    let failInitial = false;
    fakeApi.get("/api/v1/catalog/options", () => {
      catalogCalls += 1;
      return failInitial ? fail(500, { code: "INTERNAL_ERROR" }) : ok(ATTEMPT_EXAM_KIND_FIXTURES);
    });
    fakeApi.get("/api/v1/admin/attempts/{id}", () => {
      detailCalls += 1;
      return failInitial ? fail(500, { code: "INTERNAL_ERROR" }) : ok(makeAttemptDetail({ state: "SCORED" }));
    });

    onlineManager.setOnline(false);
    let unmount!: () => void;
    try {
      unmount = renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />).unmount;
      expect(await screen.findByText("Không có kết nối")).toBeInTheDocument();
      expect(screen.getByRole("link", { name: "Danh sách lượt thi" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
      expect(screen.queryByLabelText("Đang tải chi tiết lượt thi")).not.toBeInTheDocument();
    } finally {
      onlineManager.setOnline(true);
    }
    unmount();

    failInitial = true;
    renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />);
    expect(await screen.findByText("Không tải được lượt thi")).toBeInTheDocument();
    expect(detailCalls).toBe(1);
    expect(catalogCalls).toBe(1);

    failInitial = false;
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByRole("heading", { level: 1, name: "Lượt 7C1E4B9A" });
    expect(detailCalls).toBe(2);
    expect(catalogCalls).toBe(2);
  });

  it("dịch nhãn & giá trị cấu trúc trong dòng sự kiện, tách partId/partCode và giữ chỉ số CLOZE (R1-12, R1-13, R1-28, R1-29, R2-15)", async () => {
    fakeApi.get(
      "/api/v1/admin/attempts/{id}",
      ok(
        makeAttemptDetail({
          state: "GRADING_ERROR",
          gradingError: "NullPointer tại chấm câu 4",
          faultFlag: "SYSTEM_FAULT",
          faultReason: "Sập worker chấm tự động",
          parts: [
            makeAttemptPart({
              partId: PART_1_ID,
              partName: "Phần 1",
              state: "GENERATION_FAILED",
              generationError: "Không đủ câu hỏi ngân hàng",
            }),
          ],
          events: [
            {
              id: "ev-1",
              type: "GRADING_FAILED",
              at: "2026-11-17T02:20:00Z",
              partId: PART_1_ID,
              data: {
                partId: "part-raw-1",
                partCode: "P1",
                fromState: "GRADING",
                toState: "GRADING_ERROR",
                endReason: "SUBMITTED",
                closeReason: "FINISHED",
                abortReason: "AUDIO_ASSET_MISSING",
                reclaimed: true,
                compensated: false,
                submittedAt: "2026-11-17T02:19:00Z",
                reason: "Hết thời gian xử lý",
                reopenedReason: "Phúc khảo lần 1",
                comment: "Ghi chú giám khảo",
                unknownKey: "raw-val",
              },
            },
          ],
          items: [
            makeAttemptItem({
              itemId: "item-cloze",
              partId: PART_1_ID,
              ord: 1,
              displayNo: 1,
              questionType: "CLOZE",
              answer: {
                type: "CLOZE",
                blanks: { b1: "", b2: "Hà Nội" },
              },
            }),
          ],
        }),
      ),
    );

    renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />);
    await screen.findByRole("heading", { level: 1, name: "Lượt 7C1E4B9A" });

    // Free-text error/reason strings wrapped in data-exam-content
    expect(screen.getByText("NullPointer tại chấm câu 4").closest("[data-exam-content]")).not.toBeNull();
    for (const el of screen.getAllByText("Sập worker chấm tự động")) {
      expect(el.closest("[data-exam-content]")).not.toBeNull();
    }
    for (const el of screen.getAllByText("Không đủ câu hỏi ngân hàng")) {
      expect(el.closest("[data-exam-content]")).not.toBeNull();
    }

    // Event data localization + structured enum/boolean/ISO formatting + free-text isolation
    const timeline = screen.getByRole("list");
    expect(within(timeline).getByText(/ID phần: part-raw-1/)).toBeInTheDocument();
    expect(within(timeline).getByText(/Mã phần: P1/)).toBeInTheDocument();
    expect(within(timeline).getByText(/Từ trạng thái: Đang chấm/)).toBeInTheDocument();
    expect(within(timeline).getByText(/Sang trạng thái: Lỗi chấm/)).toBeInTheDocument();
    expect(within(timeline).getByText(/Lý do kết thúc: Nộp bài/)).toBeInTheDocument();
    expect(within(timeline).getByText(/Lý do đóng: Hoàn thành phần/)).toBeInTheDocument();
    expect(within(timeline).getByText(/Lý do hủy: Thiếu tệp audio/)).toBeInTheDocument();
    expect(within(timeline).getByText(/Thu hồi phiên: Có/)).toBeInTheDocument();
    expect(within(timeline).getByText(/Đã cấp bù: Không/)).toBeInTheDocument();
    expect(within(timeline).getByText(/Nộp lúc:/)).toBeInTheDocument();
    expect(within(timeline).getByText(/Nộp lúc:/)).not.toHaveTextContent("2026-11-17T02:19:00Z");

    for (const isolated of ["Hết thời gian xử lý", "Phúc khảo lần 1", "Ghi chú giám khảo"]) {
      expect(within(timeline).getByText(isolated).closest("[data-exam-content]")).not.toBeNull();
    }
    expect(within(timeline).getByText(/unknownKey: raw-val/)).toBeInTheDocument();

    // CLOZE blank index preserved ("Ô 2:")
    expect(screen.getByText("Ô 2:")).toBeInTheDocument();
    expect(screen.getByText("Hà Nội").closest("[data-exam-content]")).not.toBeNull();
  });

  it("invalidate GRADING_QUERY_KEYS khi regrade/fault, invalidate detail+lists khi 409 (regrade/fault/compensate), và reset khi đổi attemptId ấm cache (R1-21, R1-24, R1-43, R2-24, R2-25)", async () => {
    const OTHER_ATTEMPT_ID = "88888888-8888-4888-8888-888888888888";
    const invalidateSpy = vi.spyOn(QueryClient.prototype, "invalidateQueries");
    let conflictOn: "none" | "regrade" | "fault" | "compensate" = "none";

    const otherAttemptFixture = makeAttemptDetail({
      attemptId: OTHER_ATTEMPT_ID,
      state: "SCORED",
      parts: [
        makeAttemptPart({ partId: PART_1_ID, partName: "Phần A" }),
        makeAttemptPart({ partId: PART_3_ID, partName: "Phần B" }),
      ],
      items: [
        makeAttemptItem({ itemId: "o-1", partId: PART_1_ID, displayNo: 1 }),
        makeAttemptItem({ itemId: "o-2", partId: PART_3_ID, displayNo: 2 }),
      ],
    });

    fakeApi.get("/api/v1/admin/attempts/{id}", (req) => {
      const pathParams = (req.options as { params?: { path?: { id?: string } } } | undefined)
        ?.params?.path;
      if (pathParams?.id === OTHER_ATTEMPT_ID) {
        return new Promise(() => {}); // Giữ pending để chứng minh dữ liệu lấy trực tiếp từ warm cache
      }
      return ok(
        makeAttemptDetail({
          attemptId: ATTEMPT_1_ID,
          state: "GRADING_ERROR",
          faultFlag: conflictOn === "compensate" ? "SYSTEM_FAULT" : undefined,
          compensated: false,
          parts: [
            makeAttemptPart({ partId: PART_1_ID, partName: "Phần 1" }),
            makeAttemptPart({ partId: PART_3_ID, partName: "Phần 3" }),
          ],
          items: [
            makeAttemptItem({ itemId: "i-1", partId: PART_1_ID, displayNo: 1 }),
            makeAttemptItem({ itemId: "i-2", partId: PART_3_ID, displayNo: 2 }),
          ],
        }),
      );
    });
    fakeApi.post("/api/v1/admin/attempts/{id}/regrade", () =>
      conflictOn === "regrade"
        ? fail(409, { code: "ATTEMPT_CONFLICT" })
        : ok(makeAttemptDetail({ state: "SCORED" })),
    );
    fakeApi.post("/api/v1/admin/attempts/{id}/fault", () =>
      conflictOn === "fault"
        ? fail(409, { code: "ATTEMPT_CONFLICT" })
        : ok(makeAttemptDetail({ faultFlag: "SYSTEM_FAULT" })),
    );
    fakeApi.post("/api/v1/admin/attempts/{id}/compensate", () =>
      fail(409, { code: "GRANT_REFUNDED" }),
    );

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    queryClient.setQueryData(ATTEMPTS_QUERY_KEYS.detail(OTHER_ATTEMPT_ID), otherAttemptFixture);
    const session = makeAttemptSession();

    const { rerender } = renderWithProviders(
      <SessionProvider loadSession={async () => session}>
        <AttemptDetailView attemptId={ATTEMPT_1_ID} />
      </SessionProvider>,
      { client: queryClient, locale: "vi", messages: viMessages },
    );
    await screen.findByRole("heading", { level: 1, name: "Lượt 7C1E4B9A" });

    // 1. Regrade thành công -> invalidates GRADING_QUERY_KEYS.all
    invalidateSpy.mockClear();
    await userEvent.click(screen.getByRole("button", { name: "Chấm lại" }));
    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: GRADING_QUERY_KEYS.all });
    });

    // 2. Regrade gặp 409 -> invalidates detail + lists()
    conflictOn = "regrade";
    invalidateSpy.mockClear();
    await userEvent.click(screen.getByRole("button", { name: "Chấm lại" }));
    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ATTEMPTS_QUERY_KEYS.detail(ATTEMPT_1_ID) });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ATTEMPTS_QUERY_KEYS.lists() });
    });

    // 3. Fault thành công -> invalidates GRADING_QUERY_KEYS.all
    conflictOn = "none";
    invalidateSpy.mockClear();
    await userEvent.click(screen.getByRole("button", { name: "Đánh dấu hỏng" }));
    const faultOkDialog = await screen.findByRole("dialog");
    await userEvent.type(within(faultOkDialog).getByLabelText(/Lý do/), "Lỗi hệ thống");
    await userEvent.click(within(faultOkDialog).getByRole("button", { name: "Đánh dấu lượt hỏng" }));
    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: GRADING_QUERY_KEYS.all });
    });

    // 4. Fault gặp 409 -> invalidates detail + lists()
    conflictOn = "fault";
    invalidateSpy.mockClear();
    await userEvent.click(screen.getByRole("button", { name: "Đánh dấu hỏng" }));
    const faultConflictDialog = await screen.findByRole("dialog");
    await userEvent.type(within(faultConflictDialog).getByLabelText(/Lý do/), "Lỗi worker");
    await userEvent.click(within(faultConflictDialog).getByRole("button", { name: "Đánh dấu lượt hỏng" }));
    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ATTEMPTS_QUERY_KEYS.detail(ATTEMPT_1_ID) });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ATTEMPTS_QUERY_KEYS.lists() });
    });
    await userEvent.click(within(faultConflictDialog).getByRole("button", { name: "Hủy" }));

    // 5. Compensate gặp 409 -> invalidates detail + lists()
    conflictOn = "compensate";
    await queryClient.invalidateQueries({ queryKey: ATTEMPTS_QUERY_KEYS.detail(ATTEMPT_1_ID) });
    const compBtn = await screen.findByRole("button", { name: "Cấp bù 1 lượt" });
    invalidateSpy.mockClear();
    await userEvent.click(compBtn);
    const compDialog = await screen.findByRole("dialog");
    await userEvent.type(within(compDialog).getByLabelText(/Lý do/), "Hoàn lượt");
    await userEvent.click(within(compDialog).getByRole("button", { name: "Cấp bù 1 lượt" }));
    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ATTEMPTS_QUERY_KEYS.detail(ATTEMPT_1_ID) });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ATTEMPTS_QUERY_KEYS.lists() });
    });
    await userEvent.click(within(compDialog).getByRole("button", { name: "Hủy" }));

    // 6. Đổi từ attemptId cũ (đang chọn Phần 3) sang OTHER_ATTEMPT_ID đã warm cache -> reset ngay về "Tất cả phần"
    const partFilter = screen.getByRole("combobox", { name: "Lọc theo phần" });
    await userEvent.click(partFilter);
    await userEvent.click(screen.getByRole("option", { name: "Phần 3" }));

    rerender(
      <SessionProvider loadSession={async () => session}>
        <AttemptDetailView attemptId={OTHER_ATTEMPT_ID} />
      </SessionProvider>,
    );
    await screen.findByRole("heading", { level: 1, name: "Lượt 88888888" });
    expect(screen.getByRole("combobox", { name: "Lọc theo phần" })).toHaveTextContent("Tất cả phần");
  });

  it("chặn Đánh dấu hỏng khi thiếu ATTEMPT.COMPENSATE trên lượt chưa hỏng, khi STARTING/ABORTED, và xử lý 404/500 Retry (R1-35, R1-37, R2-42)", async () => {
    // 1. Lượt IN_PROGRESS chưa hỏng + thiếu ATTEMPT.COMPENSATE -> ẩn Đánh dấu hỏng
    fakeApi.get(
      "/api/v1/admin/attempts/{id}",
      ok(makeAttemptDetail({ state: "IN_PROGRESS", faultFlag: undefined })),
    );
    const { unmount } = renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />, {
      session: makeAttemptSession({ permissions: ["ATTEMPT.VIEW", "GRADING.MODIFY"] }),
    });
    await screen.findByRole("heading", { level: 1, name: "Lượt 7C1E4B9A" });
    expect(screen.queryByRole("button", { name: "Đánh dấu hỏng" })).not.toBeInTheDocument();
    unmount();

    // 2. Có ATTEMPT.COMPENSATE nhưng state = ABORTED hoặc STARTING -> không cho Đánh dấu hỏng
    for (const blockedState of ["ABORTED", "STARTING"] as const) {
      fakeApi.get(
        "/api/v1/admin/attempts/{id}",
        ok(makeAttemptDetail({ state: blockedState, faultFlag: undefined })),
      );
      const view = renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />);
      await screen.findByRole("heading", { level: 1, name: "Lượt 7C1E4B9A" });
      expect(screen.queryByRole("button", { name: "Đánh dấu hỏng" })).not.toBeInTheDocument();
      view.unmount();
    }

    // 3. 404 -> Không tìm thấy lượt thi
    fakeApi.get("/api/v1/admin/attempts/{id}", fail(404, { code: "NOT_FOUND" }));
    const { unmount: unmount404 } = renderAttemptsUi(
      <AttemptDetailView attemptId={ATTEMPT_1_ID} />,
    );
    expect(await screen.findByText("Không tìm thấy lượt thi")).toBeInTheDocument();
    unmount404();

    // 4. 500 -> Thử lại tải lại thành công
    let detailCalls = 0;
    fakeApi.get("/api/v1/admin/attempts/{id}", () => {
      detailCalls += 1;
      if (detailCalls === 1) return fail(500, { code: "INTERNAL_ERROR" });
      return ok(makeAttemptDetail({ state: "SCORED" }));
    });
    renderAttemptsUi(<AttemptDetailView attemptId={ATTEMPT_1_ID} />);
    expect(await screen.findByText("Không tải được lượt thi")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(
      await screen.findByRole("heading", { level: 1, name: "Lượt 7C1E4B9A" }),
    ).toBeInTheDocument();
  });

  it("AttemptDetailPage truyền label={t('detail.loading')} cho Suspense fallback (R3-24)", async () => {
    const { default: AttemptDetailPage } = await import("@/app/(shell)/attempts/[id]/page");
    const element = (await AttemptDetailPage({
      params: Promise.resolve({ id: ATTEMPT_1_ID }),
    })) as ReactElement<{ fallback: ReactElement }>;
    const { container } = renderAttemptsUi(element.props.fallback);
    expect(
      container.querySelector('[aria-busy="true"][aria-label="Đang tải chi tiết lượt thi"]'),
    ).not.toBeNull();
  });
});
