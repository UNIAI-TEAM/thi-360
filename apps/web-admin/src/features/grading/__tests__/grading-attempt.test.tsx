import { onlineManager, QueryClient, useQueryClient } from "@tanstack/react-query";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ATTEMPTS_QUERY_KEYS } from "@/features/attempts/keys";
import { SessionProvider } from "@/features/session/session-provider";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";
import { GradingItemView } from "../components/grading-item-view";
import { GRADING_QUERY_KEYS } from "../keys";
import {
  EXAM_KIND_CATALOG_FIXTURES,
  GRADER_1_ID,
  GRADER_2_ID,
  ITEM_1_ID,
  makeDetail,
  makeEssayGrade,
  makeSession,
  renderGradingUi,
} from "./grading-fixtures";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => `/grading/${ITEM_1_ID}`,
  useSearchParams: () => new URLSearchParams(window.location.search),
}));
vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});

withRealApi();

beforeEach(() => {
  fakeApi.get("/api/v1/catalog/options", ok(EXAM_KIND_CATALOG_FIXTURES));
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("GradingItemView workflows", () => {
  it("câu đã có người nhận", async () => {
    // 1. Khi câu đang do người khác giữ (chưa hết 30 phút): hiển thị chỉ xem, không có nút chấm/lưu.
    const heldByOther = makeDetail({
      state: "IN_PROGRESS",
      activeGrade: makeEssayGrade({
        graderId: GRADER_2_ID,
        graderName: "Nguyễn Thị B",
        claimExpired: false,
      }),
    });
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", ok(heldByOther));
    const { unmount } = renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />);

    expect(await screen.findByText("Đang có người chấm khác giữ")).toBeInTheDocument();
    expect(screen.getByText("Câu này đang được Nguyễn Thị B nhận chấm.")).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Lưu nháp" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Hoàn tất chấm" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Nhận câu để chấm" })).not.toBeInTheDocument();
    unmount();

    // 2. Race khi nhận câu: POST /claim trả 409 GRADING_CLAIMED -> hiện alert nêu tên người giữ và refetch.
    let detailCalls = 0;
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", () => {
      detailCalls += 1;
      return ok(detailCalls === 1 ? makeDetail({ state: "WAITING" }) : heldByOther);
    });
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/claim", {
      data: undefined,
      error: {
        code: "GRADING_CLAIMED",
        message: "Claimed",
        fieldErrors: [],
        traceId: "trace-1",
        claimedById: GRADER_2_ID,
        claimedByName: "Trần Văn C",
      },
      response: new Response(null, { status: 409 }),
    });

    renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />);
    await userEvent.click(await screen.findByRole("button", { name: "Nhận câu để chấm" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Trần Văn C");
    await waitFor(() => expect(detailCalls).toBe(2));
  });

  it("nhận câu WAITING và tiếp quản câu đã quá hạn 30 phút", async () => {
    let claimed = false;
    const claimedGrade = makeEssayGrade({
      graderId: GRADER_1_ID,
      graderName: "Grader One",
      claimExpired: false,
      version: 1,
    });
    const expired = makeDetail({
      state: "WAITING",
      activeGrade: makeEssayGrade({
        graderId: GRADER_2_ID,
        graderName: "Giám khảo cũ",
        claimExpired: true,
      }),
    });
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", () =>
      ok(claimed ? makeDetail({ state: "IN_PROGRESS", activeGrade: claimedGrade }) : expired),
    );
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/claim", () => {
      claimed = true;
      return ok(claimedGrade);
    });

    renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />);
    const takeoverBtn = await screen.findByRole("button", { name: "Tiếp quản chấm" });
    await userEvent.click(takeoverBtn);

    expect(await screen.findByRole("button", { name: "Lưu nháp" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Hoàn tất chấm" })).toBeInTheDocument();
  });

  it("lỗi khi chốt", async () => {
    const owned = makeDetail({
      state: "IN_PROGRESS",
      pointsMax: 10,
      activeGrade: makeEssayGrade({ graderId: GRADER_1_ID, version: 1 }),
    });
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", ok(owned));
    renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />);

    const c1 = await screen.findByLabelText(/Tiêu chí 1\./);
    const c2 = screen.getByLabelText(/Tiêu chí 2\./);
    const total = screen.getByLabelText("Tổng điểm câu");

    // 1. Điểm tiêu chí vượt quá tối đa (max = 4) -> không chốt được và báo lỗi.
    await userEvent.type(c1, "5");
    await userEvent.type(c2, "4");
    await userEvent.click(screen.getByRole("button", { name: "Hoàn tất chấm" }));

    expect(await screen.findByText("Điểm không được vượt quá 4.")).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Hoàn tất chấm câu tự luận?" })).not.toBeInTheDocument();

    // 2. Tổng điểm khác tổng tiêu chí -> không chốt được và báo lỗi rõ.
    await userEvent.clear(c1);
    await userEvent.type(c1, "3");
    await userEvent.clear(total);
    await userEvent.type(total, "9");
    await userEvent.click(screen.getByRole("button", { name: "Hoàn tất chấm" }));

    expect(await screen.findByText(/không khớp với điểm câu đã nhập/)).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Hoàn tất chấm câu tự luận?" })).not.toBeInTheDocument();

    // 3. Điểm câu ngoài khoảng (max = 10) -> báo lỗi.
    await userEvent.clear(total);
    await userEvent.type(total, "11");
    await userEvent.click(screen.getByRole("button", { name: "Hoàn tất chấm" }));
    expect(await screen.findByText("Điểm không được vượt quá 10.")).toBeInTheDocument();

    // 4. Lỗi 422 từ máy chủ (ESSAY_CRITERIA_SUM_MISMATCH) hiển thị alert rõ ràng.
    await userEvent.clear(total);
    await userEvent.type(total, "7");
    fakeApi.post(
      "/api/v1/admin/grading/items/{itemId}/finalize",
      fail(422, { code: "ESSAY_CRITERIA_SUM_MISMATCH" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "Hoàn tất chấm" }));
    const confirmRegion = await screen.findByRole("region", { name: "Hoàn tất chấm câu tự luận?" });
    await userEvent.click(within(confirmRegion).getByRole("button", { name: "Xác nhận hoàn tất" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Tổng điểm các tiêu chí phải bằng tổng điểm của câu.");
  });

  it("xung đột phiên bản", async () => {
    let getCalls = 0;
    const v1 = makeDetail({
      state: "IN_PROGRESS",
      activeGrade: makeEssayGrade({
        graderId: GRADER_1_ID,
        version: 3,
        points: 5,
        criteria: [
          { id: "c1", points: 2 },
          { id: "c2", points: 3 },
        ],
      }),
    });
    const v2 = makeDetail({
      state: "IN_PROGRESS",
      activeGrade: makeEssayGrade({
        graderId: GRADER_1_ID,
        version: 4,
        points: 8,
        criteria: [
          { id: "c1", points: 3 },
          { id: "c2", points: 5 },
        ],
        comment: "Updated elsewhere",
      }),
    });
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", () => {
      getCalls += 1;
      return ok(getCalls === 1 ? v1 : v2);
    });
    fakeApi.put(
      "/api/v1/admin/grading/items/{itemId}",
      fail(412, { code: "VERSION_CONFLICT", message: "Version mismatch" }),
    );

    renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />);
    const c1 = await screen.findByLabelText(/Tiêu chí 1\./);
    await userEvent.clear(c1);
    await userEvent.type(c1, "3,5");
    await userEvent.click(screen.getByRole("button", { name: "Lưu nháp" }));

    expect(fakeApi.calls.find((c) => c.method === "PUT")?.options?.body).toMatchObject({
      expectedVersion: 3,
    });
    expect(await screen.findByRole("alert")).toHaveTextContent("Dữ liệu đã được cập nhật ở nơi khác");
    await waitFor(() => expect(getCalls).toBe(2));
    await waitFor(() => expect(screen.getByLabelText("Tổng điểm câu")).toHaveValue("8"));
  });

  it("mở lại", async () => {
    let reopened = false;
    const finalizedGrade = makeEssayGrade({
      state: "FINAL",
      points: 8,
      criteria: [
        { id: "c1", points: 3 },
        { id: "c2", points: 5 },
      ],
      comment: "Initial grade",
      finalizedAt: "2026-11-20T03:30:00Z",
      version: 2,
    });
    const reopenedGrade = makeEssayGrade({
      id: "grade-2",
      state: "DRAFT",
      graderId: GRADER_1_ID,
      points: 8,
      criteria: [
        { id: "c1", points: 3 },
        { id: "c2", points: 5 },
      ],
      comment: "Initial grade",
      reopenedReason: "Phúc khảo điểm câu 25",
      version: 1,
    });
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", () =>
      ok(
        reopened
          ? makeDetail({ state: "IN_PROGRESS", regrading: true, activeGrade: reopenedGrade })
          : makeDetail({ state: "DONE", activeGrade: finalizedGrade }),
      ),
    );
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/reopen", () => {
      reopened = true;
      return ok(reopenedGrade);
    });

    renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />);
    await userEvent.click(await screen.findByRole("button", { name: "Mở lại để chấm lại" }));

    const dialog = await screen.findByRole("dialog", { name: "Mở lại bản chấm?" });
    const confirmBtn = within(dialog).getByRole("button", { name: "Xác nhận mở lại" });
    const reasonField = within(dialog).getByLabelText(/Lý do mở lại/);

    // Nút xác nhận luôn bấm được; bấm khi trống -> báo lỗi và focus vào ô lý do (DESIGN.md 5.10).
    expect(confirmBtn).toBeEnabled();
    await userEvent.click(confirmBtn);
    await waitFor(() => expect(reasonField).toHaveFocus());
    expect(reasonField).toHaveAttribute("aria-invalid", "true");

    await userEvent.type(reasonField, "  Phúc khảo điểm câu 25  ");
    await userEvent.click(confirmBtn);

    await waitFor(() => {
      const reopenCall = fakeApi.calls.find((c) => c.path.endsWith("/reopen"));
      expect(reopenCall?.options?.body).toEqual({ reason: "Phúc khảo điểm câu 25" });
    });
    expect(await screen.findByRole("button", { name: "Hoàn tất chấm" })).toBeInTheDocument();
  });

  it("thiếu quyền GRADING.MODIFY chỉ được xem kể cả khi WAITING hoặc DONE (AC-9, R1-34)", async () => {
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", ok(makeDetail({ state: "WAITING" })));

    const { unmount } = renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />, {
      session: makeSession({ permissions: ["GRADING.VIEW"] }),
    });

    expect(await screen.findByText("Chế độ chỉ xem")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Nhận câu để chấm" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Lưu nháp" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Hoàn tất chấm" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mở lại để chấm lại" })).not.toBeInTheDocument();
    unmount();

    fakeApi.get(
      "/api/v1/admin/grading/items/{itemId}",
      ok(
        makeDetail({
          state: "DONE",
          activeGrade: makeEssayGrade({ state: "FINAL", points: 8, version: 1 }),
        }),
      ),
    );
    renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />, {
      session: makeSession({ permissions: ["GRADING.VIEW"] }),
    });
    expect(await screen.findByRole("heading", { level: 2, name: "Đã chốt điểm" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mở lại để chấm lại" })).not.toBeInTheDocument();
  });

  it("hỗ trợ câu tự luận không có bảng tiêu chí (chỉ nhập tổng điểm)", async () => {
    let finalizedDone = false;
    const finalizedGrade = makeEssayGrade({ state: "FINAL", points: 7.5, version: 2 });
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", () =>
      ok(
        makeDetail({
          state: finalizedDone ? "DONE" : "IN_PROGRESS",
          rubric: [],
          pointsMax: 10,
          activeGrade: finalizedDone
            ? finalizedGrade
            : makeEssayGrade({ graderId: GRADER_1_ID, version: 1 }),
        }),
      ),
    );
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/finalize", () => {
      finalizedDone = true;
      return ok({
        attemptId: "att-1",
        attemptState: "SCORED",
        grade: finalizedGrade,
      });
    });

    renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />);
    const total = await screen.findByLabelText("Tổng điểm câu");
    await userEvent.type(total, "7,5");
    await userEvent.click(screen.getByRole("button", { name: "Hoàn tất chấm" }));
    const confirmRegion = await screen.findByRole("region", { name: "Hoàn tất chấm câu tự luận?" });
    await userEvent.click(within(confirmRegion).getByRole("button", { name: "Xác nhận hoàn tất" }));

    expect(await screen.findByRole("heading", { level: 2, name: "Đã chốt điểm" })).toBeInTheDocument();
    const finalizeCall = fakeApi.calls.find((c) => c.path.endsWith("/finalize"));
    expect(finalizeCall?.options?.body).toEqual({ expectedVersion: 1, points: 7.5, comment: "" });
  });

  it("giữ input dirty khi refetch có version mới, hiện notice Tải lại dữ liệu và chỉ reset khi bấm (R1-19)", async () => {
    let serverVersion = 1;
    let qcRef: QueryClient | null = null;
    function CaptureClient() {
      const qc = useQueryClient();
      useEffect(() => {
        qcRef = qc;
      }, [qc]);
      return null;
    }

    fakeApi.get("/api/v1/admin/grading/items/{itemId}", () =>
      ok(
        makeDetail({
          state: "IN_PROGRESS",
          rubric: [],
          pointsMax: 10,
          activeGrade: makeEssayGrade({
            graderId: GRADER_1_ID,
            points: serverVersion === 1 ? undefined : 8.5,
            comment: serverVersion === 1 ? "" : "Ghi chú từ máy chủ",
            version: serverVersion,
          }),
        }),
      ),
    );

    renderGradingUi(
      <>
        <CaptureClient />
        <GradingItemView itemId={ITEM_1_ID} />
      </>,
    );

    const total = await screen.findByLabelText("Tổng điểm câu");
    await userEvent.type(total, "4");
    expect(total).toHaveValue("4");

    serverVersion = 2;
    await qcRef!.invalidateQueries({ queryKey: GRADING_QUERY_KEYS.detail(ITEM_1_ID) });

    const staleMsg = await screen.findByText(/Dữ liệu chấm từ máy chủ đã thay đổi/);
    expect(staleMsg.closest('[role="status"]')).toBeNull();
    await waitFor(() => {
      const liveNodes = Array.from(document.querySelectorAll('[role="status"][aria-live="polite"]'));
      expect(
        liveNodes.some((el) => el.textContent?.includes("Dữ liệu chấm từ máy chủ đã thay đổi")),
      ).toBe(true);
    });
    expect(screen.getByLabelText("Tổng điểm câu")).toHaveValue("4");

    await userEvent.click(screen.getByRole("button", { name: "Tải lại dữ liệu" }));
    await waitFor(() => {
      expect(screen.getByLabelText("Tổng điểm câu")).toHaveValue("8,5");
    });
    expect(screen.getByLabelText("Nhận xét")).toHaveValue("Ghi chú từ máy chủ");
    expect(screen.queryByText(/Dữ liệu chấm từ máy chủ đã thay đổi/)).not.toBeInTheDocument();
  });

  it("gửi expectedVersion đã chốt khi lưu nháp và chốt điểm dù refetch nền tăng version lúc đang dirty (R2-1, R2-2)", async () => {
    let serverVersion = 1;
    let qcRef: QueryClient | null = null;
    function CaptureClient() {
      const qc = useQueryClient();
      useEffect(() => {
        qcRef = qc;
      }, [qc]);
      return null;
    }

    fakeApi.get("/api/v1/admin/grading/items/{itemId}", () =>
      ok(
        makeDetail({
          state: "IN_PROGRESS",
          rubric: [],
          pointsMax: 10,
          activeGrade: makeEssayGrade({ graderId: GRADER_1_ID, points: serverVersion === 1 ? 5 : 9, version: serverVersion }),
        }),
      ),
    );
    fakeApi.put("/api/v1/admin/grading/items/{itemId}", () => {
      serverVersion = 2;
      return ok(makeEssayGrade({ graderId: GRADER_1_ID, points: 6, version: 2 }));
    });
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/finalize", () =>
      ok({
        attemptId: "att-1",
        attemptState: "SCORED",
        grade: makeEssayGrade({ state: "FINAL", points: 7, version: 3 }),
      }),
    );

    renderGradingUi(
      <>
        <CaptureClient />
        <GradingItemView itemId={ITEM_1_ID} />
      </>,
    );

    const total = await screen.findByLabelText("Tổng điểm câu");
    await userEvent.clear(total);
    await userEvent.type(total, "6");

    // Refetch nền trả v2 khi form đang dirty -> vẫn pin expectedVersion = 1 khi Lưu nháp.
    serverVersion = 2;
    await qcRef!.invalidateQueries({ queryKey: GRADING_QUERY_KEYS.detail(ITEM_1_ID) });
    expect(await screen.findByText(/Dữ liệu chấm từ máy chủ đã thay đổi/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Lưu nháp" }));
    await waitFor(() => {
      const putCall = fakeApi.calls.find((c) => c.method === "PUT");
      expect(putCall?.options?.body).toMatchObject({ expectedVersion: 1, points: 6 });
    });

    // Chỉnh tiếp và có refetch nền lên v3 -> Hoàn tất chấm dùng version 2 (đã đồng bộ sau Lưu nháp).
    await userEvent.clear(total);
    await userEvent.type(total, "7");
    serverVersion = 3;
    await qcRef!.invalidateQueries({ queryKey: GRADING_QUERY_KEYS.detail(ITEM_1_ID) });
    expect(await screen.findByText(/Dữ liệu chấm từ máy chủ đã thay đổi/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Hoàn tất chấm" }));
    const confirmRegion = await screen.findByRole("region", { name: "Hoàn tất chấm câu tự luận?" });
    await userEvent.click(within(confirmRegion).getByRole("button", { name: "Xác nhận hoàn tất" }));

    await waitFor(() => {
      const finalizeCall = fakeApi.calls.find((c) => c.path.endsWith("/finalize"));
      expect(finalizeCall?.options?.body).toMatchObject({ expectedVersion: 2, points: 7 });
    });
  });

  it("chỉ tự reset 1 lần sau 412; sửa tiếp rồi refetch mới không ghi đè input người dùng (R2-3)", async () => {
    let getCalls = 0;
    let qcRef: QueryClient | null = null;
    function CaptureClient() {
      const qc = useQueryClient();
      useEffect(() => {
        qcRef = qc;
      }, [qc]);
      return null;
    }

    fakeApi.get("/api/v1/admin/grading/items/{itemId}", () => {
      getCalls += 1;
      if (getCalls === 1) {
        return ok(makeDetail({ state: "IN_PROGRESS", rubric: [], pointsMax: 10, activeGrade: makeEssayGrade({ graderId: GRADER_1_ID, points: 5, version: 3 }) }));
      }
      if (getCalls === 2) {
        return ok(makeDetail({ state: "IN_PROGRESS", rubric: [], pointsMax: 10, activeGrade: makeEssayGrade({ graderId: GRADER_1_ID, points: 8, comment: "Updated elsewhere", version: 4 }) }));
      }
      return ok(makeDetail({ state: "IN_PROGRESS", rubric: [], pointsMax: 10, activeGrade: makeEssayGrade({ graderId: GRADER_1_ID, points: 1, comment: "V5 server", version: 5 }) }));
    });
    fakeApi.put("/api/v1/admin/grading/items/{itemId}", fail(412, { code: "VERSION_CONFLICT" }));

    renderGradingUi(
      <>
        <CaptureClient />
        <GradingItemView itemId={ITEM_1_ID} />
      </>,
    );

    const total = await screen.findByLabelText("Tổng điểm câu");
    await userEvent.clear(total);
    await userEvent.type(total, "6");
    await userEvent.click(screen.getByRole("button", { name: "Lưu nháp" }));
    await waitFor(() => expect(screen.getByLabelText("Tổng điểm câu")).toHaveValue("8"));

    await userEvent.clear(total);
    await userEvent.type(total, "9");
    await qcRef!.invalidateQueries({ queryKey: GRADING_QUERY_KEYS.detail(ITEM_1_ID) });
    await waitFor(() => expect(getCalls).toBe(3));
    expect(screen.getByLabelText("Tổng điểm câu")).toHaveValue("9");
    expect(
      screen.getAllByText(/Dữ liệu chấm từ máy chủ đã thay đổi/).length,
    ).toBeGreaterThanOrEqual(1);
  });

  it("Tải lại dữ liệu giữ nguyên input dirty khi GET 500 và focus ô nhập điểm khi thành công (R2-4, R2-5, R2-6)", async () => {
    let phase: "v1" | "v2" | "fail" = "v1";
    let qcRef: QueryClient | null = null;
    function CaptureClient() {
      const qc = useQueryClient();
      useEffect(() => {
        qcRef = qc;
      }, [qc]);
      return null;
    }

    fakeApi.get("/api/v1/admin/grading/items/{itemId}", () => {
      if (phase === "fail") return fail(500, { code: "INTERNAL_ERROR" });
      return ok(
        makeDetail({
          state: "IN_PROGRESS",
          rubric: [],
          pointsMax: 10,
          activeGrade: makeEssayGrade({
            graderId: GRADER_1_ID,
            points: phase === "v1" ? 5 : 8.5,
            version: phase === "v1" ? 1 : 2,
          }),
        }),
      );
    });

    renderGradingUi(
      <>
        <CaptureClient />
        <GradingItemView itemId={ITEM_1_ID} />
      </>,
    );

    const total = await screen.findByLabelText("Tổng điểm câu");
    await userEvent.clear(total);
    await userEvent.type(total, "4");

    phase = "v2";
    await qcRef!.invalidateQueries({ queryKey: GRADING_QUERY_KEYS.detail(ITEM_1_ID) });
    await screen.findByText(/Dữ liệu chấm từ máy chủ đã thay đổi/);

    // Bấm Tải lại dữ liệu khi GET 500 -> giữ form dirty và hiện alert lỗi.
    phase = "fail";
    await userEvent.click(screen.getByRole("button", { name: "Tải lại dữ liệu" }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByLabelText("Tổng điểm câu")).toHaveValue("4");

    // Khi GET thành công -> cập nhật điểm mới, ẩn alert và focus vào ô Tổng điểm.
    phase = "v2";
    await userEvent.click(screen.getByRole("button", { name: "Tải lại dữ liệu" }));
    await waitFor(() => expect(screen.getByLabelText("Tổng điểm câu")).toHaveValue("8,5"));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText("Tổng điểm câu")).toHaveFocus());
  });

  it("không focus tiêu đề Đã chốt điểm khi chuyển sang itemId DONE khác không qua mở lại (R2-22)", async () => {
    const OTHER_ITEM_ID = "88888888-8888-4888-8888-888888888888";
    let finalizedDone = false;
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", (req) => {
      const pathParams = (req.options as { params?: { path?: { itemId?: string } } } | undefined)
        ?.params?.path;
      if (pathParams?.itemId === OTHER_ITEM_ID) {
        return ok(
          makeDetail({
            itemId: OTHER_ITEM_ID,
            state: "DONE",
            activeGrade: makeEssayGrade({ id: "g-other", state: "FINAL", points: 9, version: 1 }),
          }),
        );
      }
      return ok(
        makeDetail({
          state: finalizedDone ? "DONE" : "IN_PROGRESS",
          rubric: [],
          pointsMax: 10,
          activeGrade: makeEssayGrade({
            graderId: GRADER_1_ID,
            state: finalizedDone ? "FINAL" : "DRAFT",
            points: 7,
            version: finalizedDone ? 2 : 1,
          }),
        }),
      );
    });
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/finalize", () => {
      finalizedDone = true;
      return ok({
        attemptId: "att-1",
        attemptState: "SCORED",
        grade: makeEssayGrade({ state: "FINAL", points: 7, version: 2 }),
      });
    });

    const { rerender } = renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />);
    await screen.findByLabelText("Tổng điểm câu");
    await userEvent.click(screen.getByRole("button", { name: "Hoàn tất chấm" }));
    const confirmRegion = await screen.findByRole("region", { name: "Hoàn tất chấm câu tự luận?" });
    await userEvent.click(within(confirmRegion).getByRole("button", { name: "Xác nhận hoàn tất" }));

    const doneHeading = await screen.findByRole("heading", { level: 2, name: "Đã chốt điểm" });
    await waitFor(() => expect(document.activeElement).toBe(doneHeading));

    const session = makeSession();
    rerender(
      <SessionProvider loadSession={async () => session}>
        <GradingItemView itemId={OTHER_ITEM_ID} />
      </SessionProvider>,
    );
    const otherDoneHeading = await screen.findByRole("heading", { level: 2, name: "Đã chốt điểm" });
    expect(document.activeElement).not.toBe(otherDoneHeading);
  });

  it("chốt điểm -> mở lại reset justFinalized và invalidate detail + attempts trên cùng item (R2-22)", async () => {
    const invalidateSpy = vi.spyOn(QueryClient.prototype, "invalidateQueries");
    let qcRef: QueryClient | null = null;
    function CaptureClient() {
      const qc = useQueryClient();
      useEffect(() => {
        qcRef = qc;
      }, [qc]);
      return null;
    }
    let currentState: "IN_PROGRESS" | "DONE" = "IN_PROGRESS";
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", () =>
      ok(
        makeDetail({
          state: currentState,
          regrading: false,
          rubric: [],
          pointsMax: 10,
          activeGrade: makeEssayGrade({
            graderId: GRADER_1_ID,
            state: currentState === "DONE" ? "FINAL" : "DRAFT",
            points: 7,
            version: currentState === "DONE" ? 2 : 1,
          }),
        }),
      ),
    );
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/finalize", () => {
      currentState = "DONE";
      return ok({
        attemptId: "att-1",
        attemptState: "SCORED",
        grade: makeEssayGrade({ state: "FINAL", points: 7, version: 2 }),
      });
    });
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/reopen", () => {
      currentState = "IN_PROGRESS";
      return ok(makeEssayGrade({ id: "g-2", state: "DRAFT", points: 7, version: 3 }));
    });

    renderGradingUi(
      <>
        <CaptureClient />
        <GradingItemView itemId={ITEM_1_ID} />
      </>,
    );
    await screen.findByLabelText("Tổng điểm câu");
    await userEvent.click(screen.getByRole("button", { name: "Hoàn tất chấm" }));
    const confirmRegion = await screen.findByRole("region", { name: "Hoàn tất chấm câu tự luận?" });
    await userEvent.click(within(confirmRegion).getByRole("button", { name: "Xác nhận hoàn tất" }));

    const doneHeading = await screen.findByRole("heading", { level: 2, name: "Đã chốt điểm" });
    await waitFor(() => expect(document.activeElement).toBe(doneHeading));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: GRADING_QUERY_KEYS.detail(ITEM_1_ID) });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ATTEMPTS_QUERY_KEYS.all });

    invalidateSpy.mockClear();
    await userEvent.click(screen.getByRole("button", { name: "Mở lại để chấm lại" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.type(within(dialog).getByLabelText(/Lý do mở lại/), "Kiểm tra lại");
    await userEvent.click(within(dialog).getByRole("button", { name: "Xác nhận mở lại" }));
    await screen.findByLabelText("Tổng điểm câu");
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: GRADING_QUERY_KEYS.detail(ITEM_1_ID) });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ATTEMPTS_QUERY_KEYS.all });

    // Nếu item được chuyển về DONE qua refetch ngoài (không bấm Hoàn tất trên màn hình) -> KHÔNG auto-focus heading.
    currentState = "DONE";
    await qcRef!.invalidateQueries({ queryKey: GRADING_QUERY_KEYS.detail(ITEM_1_ID) });
    const reloadedDoneHeading = await screen.findByRole("heading", { level: 2, name: "Đã chốt điểm" });
    expect(document.activeElement).not.toBe(reloadedDoneHeading);
  });

  it("chỉ tắt badge Đang chấm lại khi attemptState === SCORED ngay trước khi refetch hoàn tất (R2-23)", async () => {
    const runCase = async (attemptState: "GRADING" | "SCORED") => {
      let releaseGet!: () => void;
      const pendingGet = new Promise<void>((resolve) => {
        releaseGet = resolve;
      });
      let getCount = 0;

      fakeApi.get("/api/v1/admin/grading/items/{itemId}", async () => {
        getCount += 1;
        if (getCount === 1) {
          return ok(
            makeDetail({
              state: "IN_PROGRESS",
              regrading: true,
              rubric: [],
              pointsMax: 10,
              activeGrade: makeEssayGrade({ graderId: GRADER_1_ID, points: 7, version: 1 }),
            }),
          );
        }
        await pendingGet;
        return ok(
          makeDetail({
            state: "DONE",
            regrading: attemptState !== "SCORED",
            rubric: [],
            pointsMax: 10,
            activeGrade: makeEssayGrade({ state: "FINAL", points: 7, version: 2 }),
          }),
        );
      });
      fakeApi.post("/api/v1/admin/grading/items/{itemId}/finalize", () =>
        ok({
          attemptId: "att-1",
          attemptState,
          grade: makeEssayGrade({ state: "FINAL", points: 7, version: 2 }),
        }),
      );

      const { unmount } = renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />);
      await screen.findByLabelText("Tổng điểm câu");
      await userEvent.click(screen.getByRole("button", { name: "Hoàn tất chấm" }));
      const confirmRegion = await screen.findByRole("region", { name: "Hoàn tất chấm câu tự luận?" });
      await userEvent.click(within(confirmRegion).getByRole("button", { name: "Xác nhận hoàn tất" }));

      expect(await screen.findByRole("heading", { level: 2, name: "Đã chốt điểm" })).toBeInTheDocument();
      // Kiểm tra TRƯỚC khi resolve GET refetch (đang bị giữ pending).
      if (attemptState === "SCORED") {
        expect(screen.queryByText("Đang chấm lại")).not.toBeInTheDocument();
      } else {
        expect(screen.getByText("Đang chấm lại")).toBeInTheDocument();
      }
      releaseGet();
      await waitFor(() => expect(getCount).toBe(2));
      unmount();
    };

    await runCase("GRADING");
    await runCase("SCORED");
  });

  it("Nhận câu để chấm invalidate detail (GET 2 lần) và reset claimAlert khi đổi itemId (R2-19, R2-26)", async () => {
    const OTHER_ITEM_ID = "88888888-8888-4888-8888-888888888888";
    const invalidateSpy = vi.spyOn(QueryClient.prototype, "invalidateQueries");
    let getCalls = 0;
    let claimed = false;

    fakeApi.get("/api/v1/admin/grading/items/{itemId}", (req) => {
      const pathParams = (req.options as { params?: { path?: { itemId?: string } } } | undefined)
        ?.params?.path;
      if (pathParams?.itemId === OTHER_ITEM_ID) {
        return ok(makeDetail({ itemId: OTHER_ITEM_ID, state: "WAITING" }));
      }
      getCalls += 1;
      return ok(
        claimed
          ? makeDetail({ state: "IN_PROGRESS", rubric: [], pointsMax: 10, activeGrade: makeEssayGrade({ graderId: GRADER_1_ID, version: 1 }) })
          : makeDetail({ state: "WAITING" }),
      );
    });
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/claim", () => {
      if (!claimed) {
        claimed = true;
        return ok(makeDetail({ state: "IN_PROGRESS" }));
      }
      return fail(409, { code: "GRADING_ITEM_ALREADY_CLAIMED" });
    });

    renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />);
    await userEvent.click(await screen.findByRole("button", { name: "Nhận câu để chấm" }));
    await screen.findByRole("button", { name: "Lưu nháp" });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: GRADING_QUERY_KEYS.detail(ITEM_1_ID) });
    expect(getCalls).toBe(2);

    // 409 khi claim -> hiện alert ở right column, đổi itemId reset alert qua key.
    claimed = false;
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", (req) => {
      const pathParams = (req.options as { params?: { path?: { itemId?: string } } } | undefined)
        ?.params?.path;
      if (pathParams?.itemId === OTHER_ITEM_ID) {
        return ok(makeDetail({ itemId: OTHER_ITEM_ID, state: "WAITING" }));
      }
      return ok(makeDetail({ state: "WAITING" }));
    });
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/claim", fail(409, { code: "GRADING_ITEM_ALREADY_CLAIMED" }));

    const { rerender } = renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />);
    await userEvent.click(await screen.findByRole("button", { name: "Nhận câu để chấm" }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();

    const session = makeSession();
    rerender(
      <SessionProvider loadSession={async () => session}>
        <GradingItemView itemId={OTHER_ITEM_ID} />
      </SessionProvider>,
    );
    await screen.findByRole("button", { name: "Nhận câu để chấm" });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("hiện trạng thái ngoại tuyến với nút Thử lại khi mất kết nối và Thử lại gọi lại detail + examKinds (R1-27, R2-29)", async () => {
    let catalogCalls = 0;
    let detailCalls = 0;
    let failInitial = false;
    fakeApi.get("/api/v1/catalog/options", () => {
      catalogCalls += 1;
      return failInitial ? fail(500, { code: "INTERNAL_ERROR" }) : ok(EXAM_KIND_CATALOG_FIXTURES);
    });
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", () => {
      detailCalls += 1;
      return failInitial ? fail(500, { code: "INTERNAL_ERROR" }) : ok(makeDetail({ state: "WAITING" }));
    });

    onlineManager.setOnline(false);
    let unmount!: () => void;
    try {
      unmount = renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />).unmount;
      expect(await screen.findByText("Không có kết nối")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
      expect(screen.queryByLabelText("Đang tải câu tự luận")).not.toBeInTheDocument();
    } finally {
      onlineManager.setOnline(true);
    }
    unmount();

    failInitial = true;
    renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />);
    expect(await screen.findByText("Không tải được câu tự luận")).toBeInTheDocument();
    expect(detailCalls).toBe(1);
    expect(catalogCalls).toBe(1);

    failInitial = false;
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByRole("button", { name: "Nhận câu để chấm" });
    expect(detailCalls).toBe(2);
    expect(catalogCalls).toBe(2);
  });
});
