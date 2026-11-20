import { onlineManager } from "@tanstack/react-query";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { UiStringsProvider } from "@thi360/ui";
import { APP_TIME_ZONE, INTL_FORMATS, normalizeUiLocale, resolveIntlLocale } from "@thi360/web-kit/preferences";

import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { PromotionDetailView } from "../components/promotion-detail-view";
import {
  CLASS_ID_1,
  CLASSES_PATH,
  EXAMS_PATH,
  OPS_PROMO_PERMISSIONS,
  PROMO_DETAIL_PATH,
  PROMO_ID_1,
  PROMO_PREVIEW_PATH,
  PROMO_RESUME_PATH,
  PROMO_STOP_PATH,
  VIEW_ONLY_PROMO_PERMISSIONS,
  promotionDetailFixture,
  renderWithPermissions,
} from "./promotion-fixtures";

const mockPush = vi.fn();

vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn() }),
  usePathname: () => `/promotions/${PROMO_ID_1}`,
  useSearchParams: () => new URLSearchParams(),
}));

withRealApi();

beforeEach(() => {
  mockPush.mockReset();
  fakeApi.get(
    EXAMS_PATH,
    ok({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }),
  );
  fakeApi.post(
    PROMO_PREVIEW_PATH,
    ok({
      attemptsGranted: 1,
      changed: false,
      discountApplied: "NONE",
      finalPrice: 100000,
      listPrice: 100000,
      productCode: "E1",
      productName: "Exam 1",
      versionNo: 1,
      voucherNotBetter: false,
    }),
  );
});

afterEach(() => {
  onlineManager.setOnline(true);
  vi.restoreAllMocks();
});

describe("PromotionDetailView (AC-6, AC-7, AC-11, AC-12)", () => {
  it("AC-6: hiện bảng lượt sử dụng, lọc trạng thái và phân biệt rỗng chưa dùng vs rỗng theo bộ lọc", async () => {
    fakeApi.get(PROMO_DETAIL_PATH, (call) => {
      const q = (call.options?.params as { query?: Record<string, unknown> })?.query ?? {};
      if (q.redemptionState === "CONFIRMED") {
        return ok(
          promotionDetailFixture({
            id: PROMO_ID_1,
            usedCount: 2,
            confirmedCount: 1,
            reservedCount: 1,
            redemptions: {
              items: [],
              page: 0,
              size: 20,
              totalItems: 0,
              totalPages: 0,
            },
          }),
        );
      }
      return ok(
        promotionDetailFixture({
          id: PROMO_ID_1,
          usedCount: 2,
          confirmedCount: 1,
          reservedCount: 1,
          redemptions: {
            items: [
              {
                id: "r1",
                orderId: "o1",
                orderCode: "ORD-1001",
                userId: "u1",
                username: "learner01",
                fullName: "Nguyen Van A",
                discountVnd: 30000,
                state: "RESERVED",
                createdAt: "2026-11-20T03:00:00Z",
                updatedAt: "2026-11-20T03:00:00Z",
              },
            ],
            page: 0,
            size: 20,
            totalItems: 1,
            totalPages: 1,
          },
        }),
      );
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionDetailView promotionId={PROMO_ID_1} />);

    expect(await screen.findByText("Nguyen Van A")).toBeInTheDocument();
    expect(screen.getByText("ORD-1001")).toBeInTheDocument();

    // Nút Xóa bị khóa kèm lý do gắn aria-describedby vì usedCount = 2 > 0
    const deleteBtn = screen.getByRole("button", { name: "Xóa" });
    expect(deleteBtn).toBeDisabled();
    const descId = deleteBtn.getAttribute("aria-describedby");
    expect(descId).toBeTruthy();
    expect(document.getElementById(descId!)).toHaveTextContent(/đã có lượt sử dụng/i);

    // Lọc CONFIRMED trả về 0 dòng -> hiện "Không có lượt khớp bộ lọc" + nút Xóa bộ lọc
    await user.click(screen.getByRole("radio", { name: "Đã xác nhận" }));
    expect(await screen.findByText("Không có lượt khớp bộ lọc")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xóa bộ lọc" })).toBeInTheDocument();
  });

  it("hiện trạng thái chưa có lượt sử dụng và xử lý 412 / 409 khi dừng hoặc xóa", async () => {
    let revision = 3;
    fakeApi.get(PROMO_DETAIL_PATH, () =>
      ok(
        promotionDetailFixture({
          id: PROMO_ID_1,
          usedCount: 0,
          revision,
        }),
      ),
    );
    fakeApi.post(PROMO_STOP_PATH, () => {
      revision = 4;
      return fail(412, { code: "VERSION_CONFLICT" });
    });
    fakeApi.delete(PROMO_DETAIL_PATH, fail(409, { code: "PROMOTION_IN_USE" }));

    const user = userEvent.setup();
    renderWithPermissions(<PromotionDetailView promotionId={PROMO_ID_1} />);

    expect(await screen.findByText("Chưa có lượt sử dụng")).toBeInTheDocument();

    // Thử dừng gặp 412 -> hiện alert xung đột và giữ dialog báo lỗi
    await user.click(screen.getByRole("button", { name: "Dừng" }));
    const stopDialog = await screen.findByRole("dialog");
    await user.click(within(stopDialog).getByRole("button", { name: "Dừng khuyến mãi" }));

    expect(await within(stopDialog).findByRole("alert")).toHaveTextContent(/người khác cập nhật/i);
    await user.click(within(stopDialog).getByRole("button", { name: "Hủy" }));

    // Thử xóa gặp 409 PROMOTION_IN_USE -> báo inline trong hộp thoại
    await user.click(screen.getByRole("button", { name: "Xóa" }));
    const deleteDialog = await screen.findByRole("dialog");
    await user.click(within(deleteDialog).getByRole("button", { name: "Xóa khuyến mãi" }));
    expect(await within(deleteDialog).findByRole("alert")).toHaveTextContent(/phát sinh lượt sử dụng/i);
  });

  it("AC-7 & AC-12 & R1-12: chỉ xem ẩn toàn bộ nút sửa/dừng/xóa; ops.admin không gọi /exams và xử lý lớp rỗng", async () => {
    fakeApi.get(
      PROMO_DETAIL_PATH,
      ok(
        promotionDetailFixture({
          id: PROMO_ID_1,
          kind: "VOUCHER",
          audience: "SELECTED",
          audienceClasses: [{ id: CLASS_ID_1 }],
          audienceUsers: [],
        }),
      ),
    );
    fakeApi.get(
      CLASSES_PATH,
      ok({
        items: [{ id: CLASS_ID_1, code: "12A1", name: "Lop 12A1 On Thi", status: "ACTIVE", memberCount: 0 }],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      }),
    );

    const { unmount } = renderWithPermissions(<PromotionDetailView promotionId={PROMO_ID_1} />, {
      permissions: VIEW_ONLY_PROMO_PERMISSIONS,
    });

    expect(await screen.findByText("Welcome K12 Voucher")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Chỉnh sửa" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Dừng" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xóa" })).not.toBeInTheDocument();
    expect(screen.getByText(/Lớp #/)).toBeInTheDocument();
    unmount();

    // Với OPS_PROMO_PERMISSIONS (có CLASS.VIEW, không có EXAM.VIEW): tự giải tên lớp 12A1 và không gọi /exams
    fakeApi.calls.length = 0;
    renderWithPermissions(<PromotionDetailView promotionId={PROMO_ID_1} />, {
      permissions: OPS_PROMO_PERMISSIONS,
    });

    expect(await screen.findByText("12A1 - Lop 12A1 On Thi")).toBeInTheDocument();
    expect(fakeApi.calls.some((c) => c.path === EXAMS_PATH)).toBe(false);
  });

  it("AC-11: hiện màn không tìm thấy khi API trả về 404 PROMOTION_NOT_FOUND", async () => {
    fakeApi.get(PROMO_DETAIL_PATH, fail(404, { code: "PROMOTION_NOT_FOUND", message: "Raw missing" }));

    renderWithPermissions(<PromotionDetailView promotionId={PROMO_ID_1} />);

    expect(await screen.findByText("Không tìm thấy khuyến mãi")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Quay lại danh sách" })).toHaveAttribute(
      "href",
      "/promotions",
    );
    expect(screen.queryByText("Raw missing")).not.toBeInTheDocument();
  });

  it("R1-6 & R1-48 & R1-53: hiển thị dòng lượt sử dụng (-30.000 đ, @learner01, Đang giữ chỗ), tìm kiếm redemptionQ, phân trang page=1 và thứ bậc heading", async () => {
    fakeApi.get(PROMO_DETAIL_PATH, (call) => {
      const q = (call.options?.params as { query?: Record<string, unknown> })?.query ?? {};
      const pageNum = typeof q.redemptionPage === "number" ? q.redemptionPage : 0;
      return ok(
        promotionDetailFixture({
          id: PROMO_ID_1,
          name: "KM Full Redemptions",
          usedCount: 25,
          confirmedCount: 24,
          reservedCount: 1,
          redemptions: {
            items: [
              {
                id: `r-${pageNum}`,
                orderId: `o-${pageNum}`,
                orderCode: pageNum === 1 ? "ORD-2001" : "ORD-1001",
                userId: "u1",
                username: "learner01",
                fullName: "Nguyen Van A",
                discountVnd: 30000,
                state: "RESERVED",
                createdAt: "2026-11-20T03:00:00Z",
                updatedAt: "2026-11-20T03:00:00Z",
              },
            ],
            page: pageNum,
            size: 20,
            totalItems: 25,
            totalPages: 2,
          },
        }),
      );
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionDetailView promotionId={PROMO_ID_1} />);

    const h1 = await screen.findByRole("heading", { level: 1, name: "KM Full Redemptions" });
    expect(h1).toHaveAttribute("data-exam-content");
    expect(screen.getByRole("heading", { level: 2, name: "Lượt sử dụng" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 3, name: "Chưa có lịch sử thay đổi" })).toBeInTheDocument();

    const table = screen.getByRole("table", { name: "Lượt sử dụng" });
    expect(within(table).getByText("-30.000 đ")).toBeInTheDocument();
    expect(within(table).getByText("@learner01")).toBeInTheDocument();
    expect(within(table).getByText("Đang giữ chỗ")).toBeInTheDocument();

    const searchInput = screen.getByRole("textbox", { name: "Tìm lượt sử dụng theo học viên hoặc mã đơn" });
    await user.type(searchInput, "ORD-2");

    await waitFor(() => {
      const searchCall = fakeApi.calls.find(
        (c) =>
          c.path === PROMO_DETAIL_PATH &&
          (c.options?.params as { query?: Record<string, unknown> })?.query?.redemptionQ === "ORD-2",
      );
      expect(searchCall?.options?.params).toMatchObject({
        query: { redemptionQ: "ORD-2", redemptionPage: 0, redemptionSize: 20 },
      });
    });

    await user.click(screen.getByRole("button", { name: "Xóa từ khóa tìm kiếm" }));
    await user.click(screen.getByRole("button", { name: "Trang 2" }));

    await waitFor(() => {
      expect(screen.getByText("ORD-2001")).toBeInTheDocument();
    });
    const page2Call = fakeApi.calls.find(
      (c) =>
        c.path === PROMO_DETAIL_PATH &&
        (c.options?.params as { query?: Record<string, unknown> })?.query?.redemptionPage === 1,
    );
    expect(page2Call?.options?.params).toMatchObject({
      query: { redemptionPage: 1, redemptionSize: 20 },
    });
  });

  it("R1-5 & R1-27: Dừng/Tiếp tục gửi đúng expectedRevision, xử lý 412 conflictAlert và 409 PROMOTION_ENDED tự tải lại", async () => {
    let detailState = promotionDetailFixture({
      id: PROMO_ID_1,
      status: "ACTIVE",
      state: "RUNNING",
      revision: 3,
      usedCount: 0,
    });
    fakeApi.get(PROMO_DETAIL_PATH, () => ok(detailState));

    let stopCalls = 0;
    fakeApi.post(PROMO_STOP_PATH, () => {
      stopCalls += 1;
      if (stopCalls === 1) {
        detailState = promotionDetailFixture({
          id: PROMO_ID_1,
          status: "ACTIVE",
          state: "RUNNING",
          revision: 4,
          usedCount: 0,
        });
        return fail(412, { code: "VERSION_CONFLICT" });
      }
      detailState = promotionDetailFixture({
        id: PROMO_ID_1,
        status: "STOPPED",
        state: "UPCOMING",
        revision: 5,
        usedCount: 0,
      });
      return ok(detailState);
    });

    let resumeCalls = 0;
    fakeApi.post(PROMO_RESUME_PATH, () => {
      resumeCalls += 1;
      if (resumeCalls === 1) {
        detailState = promotionDetailFixture({
          id: PROMO_ID_1,
          status: "STOPPED",
          state: "UPCOMING",
          revision: 6,
          usedCount: 0,
        });
        return fail(412, { code: "VERSION_CONFLICT" });
      }
      return fail(409, { code: "PROMOTION_ENDED" });
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionDetailView promotionId={PROMO_ID_1} />);

    // 1) Dừng: gửi 3 -> 412 + conflictAlert -> gửi lại 4 -> thành công (chuyển sang Đã dừng)
    await user.click(await screen.findByRole("button", { name: "Dừng" }));
    const stopDialog = await screen.findByRole("dialog");
    await user.click(within(stopDialog).getByRole("button", { name: "Dừng khuyến mãi" }));
    expect(await within(stopDialog).findByRole("alert")).toHaveTextContent(/người khác cập nhật/i);
    await user.click(within(stopDialog).getByRole("button", { name: "Hủy" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/người khác cập nhật/i);

    await user.click(screen.getByRole("button", { name: "Dừng" }));
    const stopDialog2 = await screen.findByRole("dialog");
    await user.click(within(stopDialog2).getByRole("button", { name: "Dừng khuyến mãi" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    const sentStopCalls = fakeApi.calls.filter((c) => c.path === PROMO_STOP_PATH);
    expect(sentStopCalls[0]?.options?.body).toEqual({ expectedRevision: 3 });
    expect(sentStopCalls[1]?.options?.body).toEqual({ expectedRevision: 4 });

    // 2) Tiếp tục: gửi 5 -> 412 -> tải lại 6 -> gửi lại 6 gặp 409 PROMOTION_ENDED tự tải lại chi tiết
    await user.click(await screen.findByRole("button", { name: "Tiếp tục" }));
    const resumeDialog1 = await screen.findByRole("dialog");
    await user.click(within(resumeDialog1).getByRole("button", { name: "Tiếp tục" }));
    expect(await within(resumeDialog1).findByRole("alert")).toHaveTextContent(/người khác cập nhật/i);
    await user.click(within(resumeDialog1).getByRole("button", { name: "Hủy" }));

    const getsBeforeEnded = fakeApi.calls.filter((c) => c.path === PROMO_DETAIL_PATH && c.method === "GET").length;
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    const resumeDialog2 = await screen.findByRole("dialog");
    await user.click(within(resumeDialog2).getByRole("button", { name: "Tiếp tục" }));
    expect(await within(resumeDialog2).findByRole("alert")).toHaveTextContent(/đã kết thúc/i);

    const sentResumeCalls = fakeApi.calls.filter((c) => c.path === PROMO_RESUME_PATH);
    expect(sentResumeCalls[0]?.options?.body).toEqual({ expectedRevision: 5 });
    expect(sentResumeCalls[1]?.options?.body).toEqual({ expectedRevision: 6 });

    await waitFor(() => {
      const getsAfterEnded = fakeApi.calls.filter((c) => c.path === PROMO_DETAIL_PATH && c.method === "GET").length;
      expect(getsAfterEnded).toBeGreaterThan(getsBeforeEnded);
    });
  });

  it("R1-10/R1-26/R1-39: lỗi tải lại lượt sử dụng giữ nguyên trang chi tiết và hiện nút Thử lại + Xóa bộ lọc nội tuyến", async () => {
    let failFiltered = true;
    fakeApi.get(PROMO_DETAIL_PATH, (call) => {
      const q = (call.options?.params as { query?: Record<string, unknown> })?.query ?? {};
      if (q.redemptionState === "CONFIRMED" && failFiltered) {
        return fail(500, { code: "INTERNAL_ERROR" });
      }
      return ok(
        promotionDetailFixture({
          id: PROMO_ID_1,
          name: "KM Retained Detail",
          usedCount: 1,
          confirmedCount: 1,
        }),
      );
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionDetailView promotionId={PROMO_ID_1} />);

    expect(await screen.findByRole("heading", { level: 1, name: "KM Retained Detail" })).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "Đã xác nhận" }));

    expect(await screen.findByRole("heading", { level: 3, name: "Không tải được lượt sử dụng" })).toBeInTheDocument();
    // Chi tiết chính vẫn được giữ nguyên (không bị thay bởi EmptyState toàn trang)
    expect(screen.getByRole("heading", { level: 1, name: "KM Retained Detail" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xóa bộ lọc" })).toBeInTheDocument();

    failFiltered = false;
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => {
      expect(screen.queryByRole("heading", { level: 3, name: "Không tải được lượt sử dụng" })).not.toBeInTheDocument();
    });
  });

  it("R1-35: xóa khuyến mãi thành công chuyển hướng về /promotions", async () => {
    fakeApi.get(
      PROMO_DETAIL_PATH,
      ok(promotionDetailFixture({ id: PROMO_ID_1, usedCount: 0, revision: 3 })),
    );
    fakeApi.delete(PROMO_DETAIL_PATH, ok(undefined as never));

    const user = userEvent.setup();
    renderWithPermissions(<PromotionDetailView promotionId={PROMO_ID_1} />);

    await user.click(await screen.findByRole("button", { name: "Xóa" }));
    const deleteDialog = await screen.findByRole("dialog");
    await user.click(within(deleteDialog).getByRole("button", { name: "Xóa khuyến mãi" }));

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith("/promotions");
    });
  });

  it("R1-57: đổi ngôn ngữ vi -> en-GB dịch lại ngay lỗi xung đột/hộp thoại đang hiển thị", async () => {
    fakeApi.get(
      PROMO_DETAIL_PATH,
      ok(
        promotionDetailFixture({
          id: PROMO_ID_1,
          status: "STOPPED",
          state: "UPCOMING",
          revision: 2,
          usedCount: 0,
        }),
      ),
    );
    fakeApi.post(PROMO_RESUME_PATH, fail(409, { code: "PROMOTION_ENDED" }));

    function LocaleHarness() {
      const [loc, setLoc] = useState<"vi" | "en-GB">("vi");
      const uiLoc = normalizeUiLocale(loc);
      return (
        <NextIntlClientProvider
          locale={resolveIntlLocale(uiLoc)}
          messages={uiLoc === "en" ? en : viMessages}
          timeZone={APP_TIME_ZONE}
          formats={INTL_FORMATS}
        >
          <UiStringsProvider locale={uiLoc}>
            <button type="button" onClick={() => setLoc("en-GB")}>
              Switch locale
            </button>
            <PromotionDetailView promotionId={PROMO_ID_1} />
          </UiStringsProvider>
        </NextIntlClientProvider>
      );
    }

    const user = userEvent.setup();
    renderWithPermissions(<LocaleHarness />);

    await user.click(await screen.findByRole("button", { name: "Tiếp tục" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Tiếp tục" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Khuyến mãi đã kết thúc nên không thể tiếp tục.",
    );

    await user.click(within(dialog).getByRole("button", { name: "Hủy" }));
    await user.click(screen.getByRole("button", { name: "Switch locale" }));

    await user.click(await screen.findByRole("button", { name: "Resume" }));
    const enDialog = await screen.findByRole("dialog");
    await user.click(within(enDialog).getByRole("button", { name: "Resume" }));
    expect(await within(enDialog).findByRole("alert")).toHaveTextContent(
      "This promotion has ended and cannot be resumed.",
    );
  });
});
