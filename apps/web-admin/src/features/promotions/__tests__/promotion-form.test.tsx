import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { resetIdempotencyKeys } from "@/features/content/idempotency";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { PromotionForm } from "../components/promotion-form";
import { PromotionEditPage } from "../components/promotion-form-page";
import {
  EXAM_KINDS_PATH,
  EXAMS_PATH,
  OPS_PROMO_PERMISSIONS,
  PROMO_DETAIL_PATH,
  PROMO_ID_1,
  PROMO_LIST_PATH,
  PROMO_PREVIEW_PATH,
  PROMO_SUGGEST_CODE_PATH,
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
  usePathname: () => "/promotions/new",
  useSearchParams: () => new URLSearchParams(),
}));

withRealApi();

beforeEach(() => {
  mockPush.mockReset();
  resetIdempotencyKeys();
  fakeApi.get(EXAM_KINDS_PATH, ok([]));
  fakeApi.get(EXAMS_PATH, ok({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }));
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("PromotionForm & PromotionEditPage (AC-2, AC-3, AC-4, AC-9, AC-10)", () => {
  it("AC-2: tạo khuyến mãi tự động không gửi field voucher, giữ Idempotency-Key khi thử lại lỗi 500 và đổi khóa khi đổi thân", async () => {
    let attempts = 0;
    fakeApi.post(PROMO_LIST_PATH, () => {
      attempts += 1;
      if (attempts === 1) return fail(500, { code: "INTERNAL_ERROR" });
      return ok(promotionDetailFixture({ id: PROMO_ID_1, kind: "AUTO" }));
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionForm mode="create" initialKind="AUTO" />, {
      permissions: OPS_PROMO_PERMISSIONS,
    });

    await user.type(screen.getByLabelText(/Tên khuyến mãi/), "Giam gia tu dong 20%");
    await user.type(screen.getByLabelText(/Phần trăm giảm/), "20");

    // ops.admin không có EXAM.VIEW -> không gọi /api/v1/admin/exams khi render tạo mới
    expect(fakeApi.calls.some((c) => c.path === EXAMS_PATH)).toBe(false);

    await user.click(screen.getByRole("button", { name: "Tạo khuyến mãi" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/lỗi hệ thống/i);

    // Đổi thân nhẹ -> không thử lại với thân cũ, nhưng nếu bấm lại y nguyên trước khi đổi sẽ giữ đúng khóa
    await user.click(screen.getByRole("button", { name: "Tạo khuyến mãi" }));

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith(`/promotions/${PROMO_ID_1}`);
    });

    const postCalls = fakeApi.calls.filter((c) => c.method === "POST" && c.path === PROMO_LIST_PATH);
    expect(postCalls).toHaveLength(2);
    const key1 = (postCalls[0]?.options?.params as { header?: Record<string, string> })?.header?.[
      "Idempotency-Key"
    ];
    const key2 = (postCalls[1]?.options?.params as { header?: Record<string, string> })?.header?.[
      "Idempotency-Key"
    ];
    expect(key1).toMatch(/^[0-9a-f-]{36}$/);
    expect(key2).toBe(key1);

    const body = postCalls[0]?.options?.body as Record<string, unknown>;
    expect(body.kind).toBe("AUTO");
    expect(body.code).toBeUndefined();
    expect(body.perUserLimit).toBeUndefined();
    expect(body.audience).toBeUndefined();
    expect(body.combinable).toBeUndefined();
  });

  it("AC-3 & AC-4: Gợi ý mã điền vào ô Mã ưu đãi; 409 VOUCHER_CODE_TAKEN giữ dữ liệu và focus vào ô Mã ưu đãi", async () => {
    fakeApi.get(PROMO_SUGGEST_CODE_PATH, ok({ code: "PROMO88A" }));
    fakeApi.post(PROMO_LIST_PATH, fail(409, { code: "VOUCHER_CODE_TAKEN" }));

    const user = userEvent.setup();
    renderWithPermissions(<PromotionForm mode="create" initialKind="VOUCHER" />);

    await user.type(screen.getByLabelText(/Tên khuyến mãi/), "Voucher khai giang");
    await user.type(screen.getByLabelText(/Phần trăm giảm/), "15");

    await user.click(screen.getByRole("button", { name: "Gợi ý mã" }));
    const codeInput = screen.getByLabelText(/Mã ưu đãi/);
    await waitFor(() => {
      expect(codeInput).toHaveValue("PROMO88A");
    });

    await user.click(screen.getByRole("button", { name: "Tạo khuyến mãi" }));

    await waitFor(() => {
      expect(screen.getByText(/Mã ưu đãi này đã tồn tại/)).toBeInTheDocument();
    });
    expect(codeInput).toHaveFocus();
    expect(screen.getByLabelText(/Tên khuyến mãi/)).toHaveValue("Voucher khai giang");
  });

  it("AC-9: khuyến mãi đã có lượt dùng khóa toàn bộ trường bị cấm, gửi PATCH chỉ gồm trường cho phép và kiểm tra totalLimit >= usedCount", async () => {
    fakeApi.get(
      PROMO_DETAIL_PATH,
      ok(
        promotionDetailFixture({
          id: PROMO_ID_1,
          kind: "VOUCHER",
          name: "Voucher da dung",
          code: "USED10",
          usedCount: 5,
          confirmedCount: 5,
          totalLimit: 20,
          revision: 4,
        }),
      ),
    );
    fakeApi.post(PROMO_PREVIEW_PATH, ok({
      attemptsGranted: 1,
      changed: false,
      discountApplied: "NONE",
      finalPrice: 100000,
      listPrice: 100000,
      productCode: "E1",
      productName: "Exam 1",
      versionNo: 1,
      voucherNotBetter: false,
    }));
    fakeApi.patch(PROMO_DETAIL_PATH, ok(promotionDetailFixture({ id: PROMO_ID_1, revision: 5 })));

    const user = userEvent.setup();
    renderWithPermissions(<PromotionEditPage promotionId={PROMO_ID_1} />);

    expect(await screen.findByText(/Một số trường đã bị khóa/)).toBeInTheDocument();

    expect(screen.getByLabelText(/Mã ưu đãi/)).toBeDisabled();
    expect(screen.getByLabelText(/Bắt đầu lúc/)).toBeDisabled();
    expect(screen.getByLabelText(/Phần trăm giảm/)).toBeDisabled();
    expect(screen.getByLabelText(/Giới hạn mỗi học viên/)).toBeDisabled();
    expect(screen.getByRole("radio", { name: /Tất cả bài thi/ })).toBeDisabled();
    expect(screen.getByRole("radio", { name: /Tất cả học viên/ })).toBeDisabled();

    // Sửa totalLimit < usedCount (5) -> báo lỗi tại chỗ
    const totalLimitInput = screen.getByLabelText(/Tổng lượt áp dụng tối đa/);
    await user.clear(totalLimitInput);
    await user.type(totalLimitInput, "3");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    expect(await screen.findByText(/không được nhỏ hơn số lượt đã sử dụng/)).toBeInTheDocument();
    expect(fakeApi.calls.some((c) => c.method === "PATCH")).toBe(false);

    // Sửa hợp lệ: totalLimit = 30, name mới
    await user.clear(totalLimitInput);
    await user.type(totalLimitInput, "30");
    const nameInput = screen.getByLabelText(/Tên khuyến mãi/);
    await user.clear(nameInput);
    await user.type(nameInput, "Voucher da gia han");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith(`/promotions/${PROMO_ID_1}`);
    });

    const patchCall = fakeApi.calls.find((c) => c.method === "PATCH" && c.path === PROMO_DETAIL_PATH);
    expect(patchCall?.options?.body).toEqual({
      expectedRevision: 4,
      name: "Voucher da gia han",
      description: "Applies to K12 learners",
      totalLimit: 30,
      status: "ACTIVE",
    });
  });

  it("AC-10: 412 VERSION_CONFLICT hiện biểu ngữ xung đột và nút Tải bản mới nhất lấy revision mới để lưu lại", async () => {
    let currentDetail = promotionDetailFixture({
      id: PROMO_ID_1,
      name: "Ban cu",
      usedCount: 0,
      revision: 2,
    });
    fakeApi.get(PROMO_DETAIL_PATH, () => ok(currentDetail));

    let patchAttempt = 0;
    fakeApi.patch(PROMO_DETAIL_PATH, () => {
      patchAttempt += 1;
      if (patchAttempt === 1) {
        currentDetail = promotionDetailFixture({
          id: PROMO_ID_1,
          name: "Ban nguoi khac luu",
          usedCount: 0,
          revision: 3,
          updatedAt: "2026-11-20T05:00:00Z",
        });
        return fail(412, { code: "VERSION_CONFLICT" });
      }
      return ok(promotionDetailFixture({ id: PROMO_ID_1, revision: 4 }));
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionEditPage promotionId={PROMO_ID_1} />);

    const nameInput = await screen.findByLabelText(/Tên khuyến mãi/);
    await user.clear(nameInput);
    await user.type(nameInput, "Toi sua de");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    expect(await screen.findByText("Xung đột phiên bản (412)")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tải bản mới nhất" }));

    await waitFor(() => {
      expect(screen.getByLabelText(/Tên khuyến mãi/)).toHaveValue("Ban nguoi khac luu");
    });

    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith(`/promotions/${PROMO_ID_1}`);
    });
    const patchCalls = fakeApi.calls.filter((c) => c.method === "PATCH");
    expect(patchCalls[0]?.options?.body).toMatchObject({ expectedRevision: 2 });
    expect(patchCalls[1]?.options?.body).toMatchObject({ expectedRevision: 3 });
  });
});
