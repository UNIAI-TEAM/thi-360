import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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
});
