import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { PromotionListView } from "../components/promotion-list-view";
import {
  PROMO_DETAIL_PATH,
  PROMO_ID_1,
  PROMO_ID_2,
  PROMO_LIST_PATH,
  PROMO_RESUME_PATH,
  PROMO_STOP_PATH,
  VIEW_ONLY_PROMO_PERMISSIONS,
  pageOfPromotions,
  promotionDetailFixture,
  promotionSummaryFixture,
  renderWithPermissions,
} from "./promotion-fixtures";

const mockReplace = vi.fn();
const mockPush = vi.fn();

vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
  usePathname: () => "/promotions",
  useSearchParams: () => new URLSearchParams(window.location.search),
}));

withRealApi();

beforeEach(() => {
  mockReplace.mockReset();
  mockPush.mockReset();
  window.history.replaceState(null, "", "/promotions");
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("PromotionListView (AC-1, AC-7, AC-13, AC-14)", () => {
  it("AC-1: chuyển tab Tự động / Mã ưu đãi, lọc trạng thái và debounce tìm kiếm 300ms (xóa tức thì)", async () => {
    fakeApi.get(PROMO_LIST_PATH, (call) => {
      const q = (call.options?.params as { query?: Record<string, unknown> })?.query ?? {};
      if (q.kind === "VOUCHER") {
        return ok(
          pageOfPromotions([
            promotionSummaryFixture({
              id: PROMO_ID_2,
              kind: "VOUCHER",
              name: "Voucher Tháng 11",
              code: "NOV2026",
              audience: "ALL",
            }),
          ]),
        );
      }
      return ok(
        pageOfPromotions([
          promotionSummaryFixture({
            id: PROMO_ID_1,
            kind: "AUTO",
            name: "Tự động HSA",
          }),
        ]),
      );
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionListView />);

    expect(await screen.findByText("Tự động HSA")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tạo khuyến mãi" })).toBeInTheDocument();
    expect(fakeApi.calls[0]?.options).toMatchObject({
      params: { query: { kind: "AUTO", page: 0, size: 20 } },
    });

    await user.click(screen.getByRole("tab", { name: "Mã ưu đãi" }));
    expect(await screen.findByText("Voucher Tháng 11")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tạo voucher" })).toBeInTheDocument();
    expect(screen.getByText("NOV2026")).toBeInTheDocument();

    // Lọc trạng thái: Sắp diễn ra -> gửi status=ACTIVE & state=UPCOMING
    await user.click(screen.getByRole("radio", { name: "Sắp diễn ra" }));
    await waitFor(() => {
      const lastCall = fakeApi.calls.at(-1);
      expect(lastCall?.options).toMatchObject({
        params: { query: { kind: "VOUCHER", state: "UPCOMING", status: "ACTIVE", page: 0, size: 20 } },
      });
    });

    // Tìm kiếm debounce 300ms, xóa rỗng gửi ngay (0ms)
    const searchInput = screen.getByRole("textbox", { name: "Tìm khuyến mãi theo tên hoặc mã" });
    const countBeforeSearch = fakeApi.calls.length;
    vi.useFakeTimers();
    fireEvent.change(searchInput, { target: { value: "NOV" } });
    expect(fakeApi.calls.length).toBe(countBeforeSearch);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(299);
    });
    expect(fakeApi.calls.length).toBe(countBeforeSearch);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    vi.useRealTimers();

    await waitFor(() => {
      const lastCall = fakeApi.calls.at(-1);
      expect(lastCall?.options).toMatchObject({
        params: { query: { kind: "VOUCHER", q: "NOV", state: "UPCOMING", status: "ACTIVE", page: 0 } },
      });
    });

    // Xóa rỗng ô tìm kiếm -> gọi lại ngay không đợi
    fireEvent.change(searchInput, { target: { value: "" } });
    await waitFor(() => {
      const lastCall = fakeApi.calls.at(-1);
      const q = (lastCall?.options?.params as { query?: Record<string, unknown> })?.query ?? {};
      expect(q.q).toBeUndefined();
    });
  });

  it("hiện đủ 4 trạng thái: đang tải, lỗi kèm thử lại, rỗng theo bộ lọc và thẻ trên màn hẹp", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);

    // 1. Đang tải
    fakeApi.pending(PROMO_LIST_PATH, "GET");
    const { unmount } = renderWithPermissions(<PromotionListView />);
    expect(document.querySelector('[aria-busy="true"]')).not.toBeNull();
    unmount();

    // 2. Lỗi -> Thử lại ra danh sách thẻ hẹp -> lọc ra rỗng
    fakeApi.reset();
    let attempt = 0;
    fakeApi.get(PROMO_LIST_PATH, (call) => {
      attempt += 1;
      const q = (call.options?.params as { query?: Record<string, unknown> })?.query ?? {};
      if (attempt === 1) return fail(500, { code: "INTERNAL_ERROR", message: "Raw boom" });
      if (q.status === "STOPPED") return ok(pageOfPromotions([]));
      return ok(pageOfPromotions([promotionSummaryFixture({ id: PROMO_ID_1, name: "Mobile Card Promo" })]));
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionListView />);

    expect(await screen.findByText("Không tải được danh sách")).toBeInTheDocument();
    expect(screen.queryByText("Raw boom")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Mobile Card Promo")).toBeInTheDocument();
    expect(screen.getByRole("list")).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "Đã dừng" }));
    expect(await screen.findByText("Không có kết quả phù hợp")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xóa bộ lọc" })).toBeInTheDocument();
  });

  it("AC-7: vai trò chỉ có PROMOTION.VIEW ẩn CTA tạo mới và toàn bộ nút sửa/dừng/xóa", async () => {
    fakeApi.get(
      PROMO_LIST_PATH,
      ok(
        pageOfPromotions([
          promotionSummaryFixture({ id: PROMO_ID_1, name: "Khuyến mãi đọc" }),
        ]),
      ),
    );

    renderWithPermissions(<PromotionListView />, { permissions: VIEW_ONLY_PROMO_PERMISSIONS });

    expect(await screen.findByRole("link", { name: "Khuyến mãi đọc" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Tạo khuyến mãi" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Chỉnh sửa/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Thao tác/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/Quản trị chỉ đọc/i)).not.toBeInTheDocument();
  });

  it("AC-13: dừng khuyến mãi gửi expectedRevision; gặp 412 báo xung đột trong hộp thoại và tải lại danh sách", async () => {
    let listRevision = 5;
    fakeApi.get(PROMO_LIST_PATH, () =>
      ok(
        pageOfPromotions([
          promotionSummaryFixture({ id: PROMO_ID_1, name: "KM cần dừng", revision: listRevision }),
        ]),
      ),
    );
    let stopAttempts = 0;
    fakeApi.post(PROMO_STOP_PATH, () => {
      stopAttempts += 1;
      if (stopAttempts === 1) return fail(412, { code: "VERSION_CONFLICT" });
      return ok(promotionDetailFixture({ id: PROMO_ID_1, status: "STOPPED", revision: 7 }));
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionListView />);

    await user.click(await screen.findByRole("button", { name: "Thao tác: KM cần dừng" }));
    await user.click(await screen.findByRole("menuitem", { name: "Dừng" }));

    const dialog = await screen.findByRole("dialog");
    listRevision = 6;
    await user.click(within(dialog).getByRole("button", { name: "Dừng khuyến mãi" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(/người khác cập nhật/i);
    expect(fakeApi.calls.find((c) => c.path === PROMO_STOP_PATH)?.options).toMatchObject({
      params: { path: { id: PROMO_ID_1 } },
      body: { expectedRevision: 5 },
    });

    // Đóng rồi mở lại sau khi danh sách cập nhật revision 6 -> xác nhận lần 2 dùng revision 6
    await user.click(within(dialog).getByRole("button", { name: "Hủy" }));
    await user.click(screen.getByRole("button", { name: "Thao tác: KM cần dừng" }));
    await user.click(await screen.findByRole("menuitem", { name: "Dừng" }));
    const dialog2 = await screen.findByRole("dialog");
    await user.click(within(dialog2).getByRole("button", { name: "Dừng khuyến mãi" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    const stopCalls = fakeApi.calls.filter((c) => c.path === PROMO_STOP_PATH);
    expect(stopCalls[1]?.options).toMatchObject({ body: { expectedRevision: 6 } });
  });

  it("AC-13: mở lại khuyến mãi đã kết thúc gặp 409 PROMOTION_ENDED hiển thị thông báo rõ ràng trong hộp thoại", async () => {
    fakeApi.get(
      PROMO_LIST_PATH,
      ok(
        pageOfPromotions([
          promotionSummaryFixture({
            id: PROMO_ID_1,
            name: "KM tạm dừng",
            status: "STOPPED",
            state: "UPCOMING",
            revision: 2,
          }),
        ]),
      ),
    );
    fakeApi.post(PROMO_RESUME_PATH, fail(409, { code: "PROMOTION_ENDED" }));

    const user = userEvent.setup();
    renderWithPermissions(<PromotionListView />);

    await user.click(await screen.findByRole("button", { name: "Thao tác: KM tạm dừng" }));
    await user.click(await screen.findByRole("menuitem", { name: "Tiếp tục" }));

    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Tiếp tục" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(/đã kết thúc/i);
  });

  it("AC-14: khóa xóa khi usedCount > 0 và báo 409 PROMOTION_IN_USE khi xóa khuyến mãi vừa phát sinh lượt dùng", async () => {
    let used = 0;
    fakeApi.get(PROMO_LIST_PATH, () =>
      ok(
        pageOfPromotions([
          promotionSummaryFixture({ id: PROMO_ID_1, name: "KM chưa dùng", usedCount: used }),
          promotionSummaryFixture({ id: PROMO_ID_2, name: "KM đã dùng", usedCount: 2 }),
        ]),
      ),
    );
    fakeApi.delete(PROMO_DETAIL_PATH, () => {
      used = 1;
      return fail(409, { code: "PROMOTION_IN_USE" });
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionListView />);

    // Dòng đã dùng -> menuitem Xóa bị disabled
    await user.click(await screen.findByRole("button", { name: "Thao tác: KM đã dùng" }));
    const disabledDelete = await screen.findByRole("menuitem", { name: "Xóa" });
    expect(disabledDelete).toHaveAttribute("aria-disabled", "true");
    await user.keyboard("{Escape}");

    // Dòng chưa dùng -> bấm xóa nhưng race 409 PROMOTION_IN_USE
    await user.click(screen.getByRole("button", { name: "Thao tác: KM chưa dùng" }));
    await user.click(await screen.findByRole("menuitem", { name: "Xóa" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Xóa khuyến mãi" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(/phát sinh lượt sử dụng/i);
  });
});
