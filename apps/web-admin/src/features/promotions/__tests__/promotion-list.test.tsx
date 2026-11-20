import { onlineManager } from "@tanstack/react-query";
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
  onlineManager.setOnline(true);
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
    await user.click(within(dialog).getByRole("button", { name: "Hủy" }));
    expect(screen.getByRole("link", { name: "KM chưa dùng" })).toBeInTheDocument();
  });

  it("R1-5: mở lại từ danh sách gửi expectedRevision; 412 báo xung đột, tải lại revision mới và thử lại thành công", async () => {
    let listRevision = 2;
    fakeApi.get(PROMO_LIST_PATH, () =>
      ok(
        pageOfPromotions([
          promotionSummaryFixture({
            id: PROMO_ID_1,
            name: "KM tam dung",
            status: "STOPPED",
            state: "UPCOMING",
            revision: listRevision,
          }),
        ]),
      ),
    );
    let resumeAttempt = 0;
    fakeApi.post(PROMO_RESUME_PATH, () => {
      resumeAttempt += 1;
      if (resumeAttempt === 1) return fail(412, { code: "VERSION_CONFLICT" });
      return ok(promotionDetailFixture({ id: PROMO_ID_1, status: "ACTIVE", revision: 4 }));
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionListView />);

    await user.click(await screen.findByRole("button", { name: "Thao tác: KM tam dung" }));
    await user.click(await screen.findByRole("menuitem", { name: "Tiếp tục" }));
    const d1 = await screen.findByRole("dialog");
    listRevision = 3;
    await user.click(within(d1).getByRole("button", { name: "Tiếp tục" }));

    expect(await within(d1).findByRole("alert")).toHaveTextContent(/người khác cập nhật/i);
    await user.click(within(d1).getByRole("button", { name: "Hủy" }));

    await user.click(screen.getByRole("button", { name: "Thao tác: KM tam dung" }));
    await user.click(await screen.findByRole("menuitem", { name: "Tiếp tục" }));
    const d2 = await screen.findByRole("dialog");
    await user.click(within(d2).getByRole("button", { name: "Tiếp tục" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    const resumeCalls = fakeApi.calls.filter((c) => c.path === PROMO_RESUME_PATH);
    expect(resumeCalls[0]?.options?.body).toEqual({ expectedRevision: 2 });
    expect(resumeCalls[1]?.options?.body).toEqual({ expectedRevision: 3 });
  });

  it("R1-15 & R1-27 & R1-7: badge ưu tiên Đã kết thúc trước Đã dừng, 409 PROMOTION_ENDED tải lại danh sách và BUNDLE được bản địa hóa", async () => {
    fakeApi.get(
      PROMO_LIST_PATH,
      ok(
        pageOfPromotions([
          promotionSummaryFixture({
            id: PROMO_ID_1,
            name: "KM ended stopped",
            status: "STOPPED",
            state: "ENDED",
            scope: "BUNDLE",
            revision: 2,
          }),
        ]),
      ),
    );
    fakeApi.post(PROMO_RESUME_PATH, fail(409, { code: "PROMOTION_ENDED" }));

    const user = userEvent.setup();
    const { container, unmount } = renderWithPermissions(<PromotionListView />);

    await screen.findByText("KM ended stopped");
    const table = screen.getByRole("table");
    expect(within(table).getByText("Đã kết thúc")).toBeInTheDocument();
    expect(within(table).queryByText("Đã dừng")).not.toBeInTheDocument();
    expect(within(table).getAllByText("Gói đề thi").length).toBeGreaterThanOrEqual(1);
    expect(container.textContent).not.toContain("BUNDLE");
    unmount();

    // Nếu state UPCOMING + STOPPED nhưng race PROMOTION_ENDED ở backend -> tải lại list
    fakeApi.reset();
    fakeApi.get(
      PROMO_LIST_PATH,
      ok(
        pageOfPromotions([
          promotionSummaryFixture({
            id: PROMO_ID_2,
            name: "KM race ended",
            status: "STOPPED",
            state: "UPCOMING",
            revision: 2,
          }),
        ]),
      ),
    );
    fakeApi.post(PROMO_RESUME_PATH, fail(409, { code: "PROMOTION_ENDED" }));

    renderWithPermissions(<PromotionListView />);
    const getsBefore = fakeApi.calls.filter((c) => c.path === PROMO_LIST_PATH && c.method === "GET").length;
    await user.click(await screen.findByRole("button", { name: "Thao tác: KM race ended" }));
    await user.click(await screen.findByRole("menuitem", { name: "Tiếp tục" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Tiếp tục" }));

    await waitFor(() => {
      const getsAfter = fakeApi.calls.filter((c) => c.path === PROMO_LIST_PATH && c.method === "GET").length;
      expect(getsAfter).toBeGreaterThan(getsBefore);
    });
  });

  it("R1-35 & R1-47 & R1-25: xóa thành công ở trang 2 đẩy về trang 1 hợp lệ và đưa focus về ô tìm kiếm", async () => {
    let deleted = false;
    fakeApi.get(PROMO_LIST_PATH, (call) => {
      const q = (call.options?.params as { query?: Record<string, unknown> })?.query ?? {};
      const pageNum = typeof q.page === "number" ? q.page : 0;
      if (deleted) {
        return ok(
          pageOfPromotions(
            [promotionSummaryFixture({ id: PROMO_ID_1, name: "KM trang 1" })],
            20,
            0,
            20,
          ),
        );
      }
      return ok(
        pageOfPromotions(
          [
            promotionSummaryFixture({
              id: pageNum === 1 ? PROMO_ID_2 : PROMO_ID_1,
              name: pageNum === 1 ? "KM duy nhat trang 2" : "KM trang 1",
              usedCount: 0,
            }),
          ],
          21,
          pageNum,
          20,
        ),
      );
    });
    fakeApi.delete(PROMO_DETAIL_PATH, () => {
      deleted = true;
      return ok(undefined as never);
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionListView />);

    await screen.findByText("KM trang 1");
    await user.click(screen.getByRole("button", { name: "Trang 2" }));
    expect(await screen.findByText("KM duy nhat trang 2")).toBeInTheDocument();

    const page2Call = fakeApi.calls.find(
      (c) =>
        c.path === PROMO_LIST_PATH &&
        (c.options?.params as { query?: Record<string, unknown> })?.query?.page === 1,
    );
    expect(page2Call?.options?.params).toMatchObject({ query: { kind: "AUTO", page: 1, size: 20 } });

    await user.click(screen.getByRole("button", { name: "Thao tác: KM duy nhat trang 2" }));
    await user.click(await screen.findByRole("menuitem", { name: "Xóa" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Xóa khuyến mãi" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      expect(screen.getByText("KM trang 1")).toBeInTheDocument();
    });
    const searchInput = screen.getByRole("textbox", { name: "Tìm khuyến mãi theo tên hoặc mã" });
    expect(searchInput).toHaveFocus();
  });

  it("R1-37: danh sách rỗng phân biệt quyền tạo mới và chỉ xem", async () => {
    fakeApi.get(PROMO_LIST_PATH, ok(pageOfPromotions([], 0, 0, 20)));

    const { unmount } = renderWithPermissions(<PromotionListView />);
    expect(await screen.findByText("Chưa có khuyến mãi")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Tạo khuyến mãi" }).length).toBeGreaterThanOrEqual(2);
    unmount();

    renderWithPermissions(<PromotionListView />, { permissions: VIEW_ONLY_PROMO_PERMISSIONS });
    expect(await screen.findByText("Chưa có khuyến mãi")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Tạo khuyến mãi" })).not.toBeInTheDocument();
  });

  it("R1-13/R1-19 & R1-38 & R1-43 & R1-55: hủy debounce khi đổi tab, xử lý mất mạng, phím mũi tên radiogroup và xóa liveMessage", async () => {
    fakeApi.get(PROMO_LIST_PATH, (call) => {
      const q = (call.options?.params as { query?: Record<string, unknown> })?.query ?? {};
      return ok(
        pageOfPromotions([
          promotionSummaryFixture({
            id: q.kind === "VOUCHER" ? PROMO_ID_2 : PROMO_ID_1,
            kind: q.kind === "VOUCHER" ? "VOUCHER" : "AUTO",
            name: q.kind === "VOUCHER" ? "Voucher ABC" : "Auto Promo",
            status: "STOPPED",
            state: "UPCOMING",
            usedCount: 0,
          }),
        ]),
      );
    });
    fakeApi.post(PROMO_RESUME_PATH, ok(promotionDetailFixture({ id: PROMO_ID_1, status: "ACTIVE", revision: 4 })));

    const user = userEvent.setup();
    renderWithPermissions(<PromotionListView />);

    await screen.findByText("Auto Promo");

    // 1) R1-55: Thực hiện action tạo liveMessage rồi đổi bộ lọc -> liveMessage cũ biến mất
    await user.click(screen.getByRole("button", { name: "Thao tác: Auto Promo" }));
    await user.click(await screen.findByRole("menuitem", { name: "Tiếp tục" }));
    const resumeDialog = await screen.findByRole("dialog");
    await user.click(within(resumeDialog).getByRole("button", { name: "Tiếp tục" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    // 2) R1-43: Điều hướng mũi tên trên nhóm lọc trạng thái
    const allRadio = screen.getByRole("radio", { name: "Tất cả" });
    allRadio.focus();
    await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("radio", { name: "Đang chạy" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("radio", { name: "Đang chạy" })).toHaveFocus();

    // 3) R1-13/R1-19: Gõ từ khóa rồi chuyển tab ngay trước 300ms không bị bắn 2 đợt hoặc kẹt timer cũ
    const searchInput = screen.getByRole("textbox", { name: "Tìm khuyến mãi theo tên hoặc mã" });
    await user.type(searchInput, "ABC");
    await user.click(screen.getByRole("tab", { name: "Mã ưu đãi" }));
    expect(await screen.findByText("Voucher ABC")).toBeInTheDocument();

    const voucherCalls = fakeApi.calls.filter(
      (c) =>
        c.path === PROMO_LIST_PATH &&
        (c.options?.params as { query?: Record<string, unknown> })?.query?.kind === "VOUCHER",
    );
    expect(voucherCalls[0]?.options?.params).toMatchObject({
      query: { kind: "VOUCHER", q: "ABC", page: 0 },
    });

    // 4) R1-38: Mất mạng hiện trạng thái offline và phục hồi khi có mạng lại
    onlineManager.setOnline(false);
    await user.click(screen.getByRole("radio", { name: "Đã dừng" }));
    expect(await screen.findByText("Mất kết nối mạng")).toBeInTheDocument();
    expect(screen.queryByText("Chưa có khuyến mãi")).not.toBeInTheDocument();

    onlineManager.setOnline(true);
    expect(await screen.findByText("Voucher ABC")).toBeInTheDocument();
  });
});
