import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";
import { GradingItemView } from "../components/grading-item-view";
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
    const expired = makeDetail({
      state: "WAITING",
      activeGrade: makeEssayGrade({
        graderId: GRADER_2_ID,
        graderName: "Giám khảo cũ",
        claimExpired: true,
      }),
    });
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", ok(expired));
    fakeApi.post(
      "/api/v1/admin/grading/items/{itemId}/claim",
      ok(makeEssayGrade({ graderId: GRADER_1_ID, graderName: "Grader One", claimExpired: false, version: 1 })),
    );

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
    const finalized = makeDetail({
      state: "DONE",
      activeGrade: makeEssayGrade({
        state: "FINAL",
        points: 8,
        criteria: [
          { id: "c1", points: 3 },
          { id: "c2", points: 5 },
        ],
        comment: "Initial grade",
        finalizedAt: "2026-11-20T03:30:00Z",
        version: 2,
      }),
    });
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", ok(finalized));
    fakeApi.post(
      "/api/v1/admin/grading/items/{itemId}/reopen",
      ok(
        makeEssayGrade({
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
        }),
      ),
    );

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

  it("thiếu quyền GRADING.MODIFY chỉ được xem (AC-9)", async () => {
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", ok(makeDetail({ state: "WAITING" })));

    renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />, {
      session: makeSession({ permissions: ["GRADING.VIEW"] }),
    });

    expect(await screen.findByText("Chế độ chỉ xem")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Nhận câu để chấm" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Lưu nháp" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Hoàn tất chấm" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mở lại để chấm lại" })).not.toBeInTheDocument();
  });

  it("hỗ trợ câu tự luận không có bảng tiêu chí (chỉ nhập tổng điểm)", async () => {
    fakeApi.get(
      "/api/v1/admin/grading/items/{itemId}",
      ok(
        makeDetail({
          state: "IN_PROGRESS",
          rubric: [],
          pointsMax: 10,
          activeGrade: makeEssayGrade({ graderId: GRADER_1_ID, version: 1 }),
        }),
      ),
    );
    fakeApi.post(
      "/api/v1/admin/grading/items/{itemId}/finalize",
      ok({
        attemptId: "att-1",
        attemptState: "SCORED",
        grade: makeEssayGrade({ state: "FINAL", points: 7.5, version: 2 }),
      }),
    );

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
});
