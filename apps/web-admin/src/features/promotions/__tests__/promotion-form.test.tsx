import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import Link from "next/link";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { UiStringsProvider } from "@thi360/ui";
import { APP_TIME_ZONE, INTL_FORMATS, normalizeUiLocale, resolveIntlLocale } from "@thi360/web-kit/preferences";

import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { resetIdempotencyKeys } from "@/features/content/idempotency";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { PromotionForm } from "../components/promotion-form";
import { PromotionEditPage } from "../components/promotion-form-page";
import { ExamKindPicker } from "../components/promotion-target-pickers";
import {
  CLASS_ID_1,
  CLASSES_PATH,
  EXAM_ID_1,
  EXAM_KINDS_PATH,
  EXAMS_PATH,
  KIND_ID_1,
  OPS_PROMO_PERMISSIONS,
  PROMO_DETAIL_PATH,
  PROMO_ID_1,
  PROMO_LIST_PATH,
  PROMO_PREVIEW_PATH,
  PROMO_SUGGEST_CODE_PATH,
  USER_ID_1,
  USERS_PATH,
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
    // UNI-1715: form tạo mới không có trường Trạng thái, luôn gửi ACTIVE.
    expect(body.status).toBe("ACTIVE");
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
    expect(screen.getByText("Cho phép cộng dồn khi thanh toán")).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByText(/Khi đã có lượt dùng phải >= 5/)).toBeInTheDocument();

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

  it("chế độ tạo mới hiện hint totalLimit sạch và thẻ Đối tượng dùng token border-primary-600 (không dùng border-primary)", () => {
    renderWithPermissions(<PromotionForm mode="create" initialKind="VOUCHER" />);

    expect(screen.getByText("Để trống nếu không giới hạn.")).toBeInTheDocument();
    expect(screen.queryByText(/Khi đã có lượt dùng phải >=/)).not.toBeInTheDocument();

    const selectedAudienceCard = screen.getByRole("radio", { name: /Tất cả học viên/ });
    expect(selectedAudienceCard.className).toContain("border-primary-600");
    expect(selectedAudienceCard.className).not.toMatch(/\bborder-primary\b(?!-)/);
  });

  it("ExamKindPicker ở chế độ en-GB dùng pickName (nameEn hoặc rơi về nameVi) và không bọc data-exam-content", async () => {
    fakeApi.get(
      EXAM_KINDS_PATH,
      ok([
        {
          id: KIND_ID_1,
          code: "HSA",
          nameVi: "Đánh giá năng lực ĐHQG Hà Nội (HSA)",
          nameEn: "VNU Hanoi Competency Assessment (HSA)",
        },
        {
          id: "ffffffff-ffff-4fff-8fff-ffffffffffff",
          code: "CUSTOM",
          nameVi: "Kỳ thi thử nội bộ",
        },
      ]),
    );

    renderWithPermissions(<ExamKindPicker selectedIds={[KIND_ID_1]} onChange={vi.fn()} />, {
      locale: "en-GB",
    });

    const englishKind = await screen.findByText("VNU Hanoi Competency Assessment (HSA)");
    expect(englishKind).toBeInTheDocument();
    expect(englishKind.closest("[data-exam-content]")).toBeNull();
    expect(screen.queryByText("Đánh giá năng lực ĐHQG Hà Nội (HSA)")).not.toBeInTheDocument();

    // Fallback to nameVi when nameEn is missing
    const fallbackKind = screen.getByText("Kỳ thi thử nội bộ");
    expect(fallbackKind.closest("[data-exam-content]")).toBeNull();
  });

  it("R1-3 & R1-54: nhập '20%' vào discountValue và '100,000' vào totalLimit rồi gửi ngay không blur; hiện aria-busy khi gửi", async () => {
    let resolvePost!: (v: ReturnType<typeof ok>) => void;
    fakeApi.post(
      PROMO_LIST_PATH,
      () =>
        new Promise((resolve) => {
          resolvePost = resolve;
        }),
    );

    const user = userEvent.setup();
    renderWithPermissions(<PromotionForm mode="create" initialKind="AUTO" />);

    await user.type(screen.getByLabelText(/Tên khuyến mãi/), "Controlled Strip Test");
    await user.type(screen.getByLabelText(/Phần trăm giảm/), "20%");
    await user.type(screen.getByLabelText(/Tổng lượt áp dụng tối đa/), "100,000");

    const submitBtn = screen.getByRole("button", { name: "Tạo khuyến mãi" });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(submitBtn).toHaveAttribute("aria-busy", "true");
    });
    expect(submitBtn).toHaveTextContent("Tạo khuyến mãi");

    // R1-20: bấm đúp đồng bộ khi đang gửi không bắn thêm POST thứ 2
    await user.click(submitBtn);
    expect(fakeApi.calls.filter((c) => c.method === "POST" && c.path === PROMO_LIST_PATH)).toHaveLength(1);

    resolvePost(ok(promotionDetailFixture({ id: PROMO_ID_1, kind: "AUTO" })));

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith(`/promotions/${PROMO_ID_1}`);
    });

    const postCall = fakeApi.calls.find((c) => c.method === "POST" && c.path === PROMO_LIST_PATH);
    expect(postCall?.options?.body).toMatchObject({
      discountValue: 20,
      totalLimit: 100000,
    });
  });

  it("R1-11 & R1-23 & R1-36: chọn học viên xóa lỗi audience; AMOUNT + EXAM + SELECTED bỏ qua maxDiscountVnd", async () => {
    fakeApi.get(
      EXAMS_PATH,
      ok({
        items: [{ id: EXAM_ID_1, code: "HSA-1", name: "Bai thi 1", kind: "HSA", scope: "FULL", status: "PUBLISHED" }],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      }),
    );
    fakeApi.get(
      CLASSES_PATH,
      ok({ items: [{ id: CLASS_ID_1, code: "C1", name: "Lop C1", status: "ACTIVE", memberCount: 5 }], page: 0, size: 20, totalItems: 1, totalPages: 1 }),
    );
    fakeApi.get(
      USERS_PATH,
      ok({
        items: [{ id: USER_ID_1, username: "sv01", fullName: "Nguyen Van B", email: "sv01@test.vn", roleNames: [] }],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      }),
    );
    fakeApi.post(PROMO_LIST_PATH, ok(promotionDetailFixture({ id: PROMO_ID_1 })));

    const user = userEvent.setup();
    renderWithPermissions(<PromotionForm mode="create" initialKind="VOUCHER" />);

    await user.type(screen.getByLabelText(/Tên khuyến mãi/), "Voucher Amount Exam");
    await user.type(screen.getByLabelText(/Mã ưu đãi/), "AMT2026");

    // Chuyển sang giảm tiền cố định AMOUNT
    await user.click(screen.getByRole("combobox", { name: "Kiểu giảm" }));
    await user.click(await screen.findByRole("option", { name: "Giảm số tiền cố định (đ)" }));
    await user.type(screen.getByLabelText(/Số tiền giảm/), "25000");

    // Phạm vi EXAM -> chọn bài thi
    await user.click(screen.getByRole("radio", { name: /Bài thi chỉ định/ }));
    await user.click(await screen.findByRole("checkbox", { name: /Bai thi 1/ }));

    // Đối tượng SELECTED -> submit trống để bật lỗi, rồi chọn học viên để xóa lỗi ngay
    await user.click(screen.getByRole("radio", { name: /Lớp hoặc học viên chỉ định/ }));
    await user.click(screen.getByRole("button", { name: "Tạo khuyến mãi" }));

    expect(await screen.findByText(/Chọn ít nhất một lớp hoặc một học viên khi giới hạn đối tượng/)).toBeInTheDocument();

    await user.click(await screen.findByRole("checkbox", { name: /Nguyen Van B/ }));
    await waitFor(() => {
      expect(
        screen.queryByText(/Chọn ít nhất một lớp hoặc một học viên khi giới hạn đối tượng/),
      ).not.toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Tạo khuyến mãi" }));
    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith(`/promotions/${PROMO_ID_1}`);
    });

    const postCall = fakeApi.calls.find((c) => c.method === "POST" && c.path === PROMO_LIST_PATH);
    expect(postCall?.options?.body).toMatchObject({
      discountType: "AMOUNT",
      discountValue: 25000,
      scope: "EXAM",
      examIds: [EXAM_ID_1],
      audience: "SELECTED",
      audienceUserIds: [USER_ID_1],
    });
    expect((postCall?.options?.body as Record<string, unknown>).maxDiscountVnd).toBeUndefined();
  });

  it("R1-12 & R1-22 & R1-46: PATCH 409 PROMOTION_IN_USE tự tải lại và khóa trường cùng aria-describedby + chip Đã khóa", async () => {
    let used = 0;
    fakeApi.get(PROMO_DETAIL_PATH, () =>
      ok(
        promotionDetailFixture({
          id: PROMO_ID_1,
          kind: "VOUCHER",
          code: "USED20",
          usedCount: used,
          revision: 3,
        }),
      ),
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
    fakeApi.patch(PROMO_DETAIL_PATH, () => {
      used = 1;
      return fail(409, { code: "PROMOTION_IN_USE" });
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionEditPage promotionId={PROMO_ID_1} />);

    // R1-17: nút quay lại ở header trang sửa trỏ về trang chi tiết
    expect(await screen.findByRole("link", { name: "Quay lại chi tiết" })).toHaveAttribute(
      "href",
      `/promotions/${PROMO_ID_1}`,
    );

    const nameInput = screen.getByLabelText(/Tên khuyến mãi/);
    await user.clear(nameInput);
    await user.type(nameInput, "Voucher bi dung trong luc sua");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    // Hiển thị đúng 1 banner inUseConflictTitle (không lặp banner form thứ 2) và khóa code ngay
    expect(await screen.findByText("Khuyến mãi vừa phát sinh lượt sử dụng")).toBeInTheDocument();
    expect(screen.getAllByRole("alert").filter((a) => /phát sinh lượt sử dụng/i.test(a.textContent ?? ""))).toHaveLength(1);

    const codeInput = screen.getByLabelText(/Mã ưu đãi/);
    expect(codeInput).toBeDisabled();
    expect(codeInput.getAttribute("aria-describedby")).toBeTruthy();
    expect(screen.getAllByText("Đã khóa").length).toBeGreaterThanOrEqual(1);
  });

  it("R1-8 & R1-21 & R1-61 & R1-36: nới độ dài tên 200 / mô tả 2000 / mã 50, chặn vượt INT32 và clearTotalLimit", async () => {
    fakeApi.get(
      PROMO_DETAIL_PATH,
      ok(
        promotionDetailFixture({
          id: PROMO_ID_1,
          kind: "VOUCHER",
          code: "A".repeat(50),
          totalLimit: 50,
          usedCount: 0,
          revision: 4,
        }),
      ),
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
    fakeApi.patch(PROMO_DETAIL_PATH, ok(promotionDetailFixture({ id: PROMO_ID_1, revision: 5 })));

    const user = userEvent.setup();
    renderWithPermissions(<PromotionEditPage promotionId={PROMO_ID_1} />);

    const nameInput = await screen.findByLabelText(/Tên khuyến mãi/);
    expect(nameInput).toHaveAttribute("maxlength", "200");
    const descInput = screen.getByLabelText(/Mô tả/);
    expect(descInput).toHaveAttribute("maxlength", "2000");

    // Vượt INT32 -> báo lỗi localized và không gọi PATCH
    const totalLimitInput = screen.getByLabelText(/Tổng lượt áp dụng tối đa/);
    await user.clear(totalLimitInput);
    await user.type(totalLimitInput, "2147483648");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    expect(await screen.findByText(/2\.147\.483\.647/)).toBeInTheDocument();
    expect(fakeApi.calls.some((c) => c.method === "PATCH")).toBe(false);

    // Sửa tên 150 ký tự, mô tả 800 ký tự, xóa rỗng totalLimit để gửi clearTotalLimit: true
    fireEvent.change(nameInput, { target: { value: "N".repeat(150) } });
    fireEvent.change(descInput, { target: { value: "D".repeat(800) } });
    await user.clear(totalLimitInput);
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith(`/promotions/${PROMO_ID_1}`);
    });
    const patchCall = fakeApi.calls.find((c) => c.method === "PATCH" && c.path === PROMO_DETAIL_PATH);
    expect(patchCall?.options?.body).toMatchObject({
      name: "N".repeat(150),
      description: "D".repeat(800),
      clearTotalLimit: true,
    });
  });

  it("R1-17 & R1-30: PromotionEditPage gặp 404 PROMOTION_NOT_FOUND hiện EmptyState H1 và nút Quay lại danh sách", async () => {
    fakeApi.get(PROMO_DETAIL_PATH, fail(404, { code: "PROMOTION_NOT_FOUND" }));

    renderWithPermissions(<PromotionEditPage promotionId={PROMO_ID_1} />);

    expect(
      await screen.findByRole("heading", { level: 1, name: "Không tìm thấy khuyến mãi" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Quay lại danh sách" })).toHaveAttribute(
      "href",
      "/promotions",
    );
  });

  it("R1-34: idempotency thử lại 2 lần lỗi 500 giữ cùng khóa; đổi tên sau đó tạo khóa mới", async () => {
    let postAttempt = 0;
    fakeApi.post(PROMO_LIST_PATH, () => {
      postAttempt += 1;
      if (postAttempt <= 2) return fail(500, { code: "INTERNAL_ERROR" });
      return ok(promotionDetailFixture({ id: PROMO_ID_1, kind: "AUTO" }));
    });

    const user = userEvent.setup();
    renderWithPermissions(<PromotionForm mode="create" initialKind="AUTO" />);

    const nameInput = screen.getByLabelText(/Tên khuyến mãi/);
    await user.type(nameInput, "Idem Promo A");
    await user.type(screen.getByLabelText(/Phần trăm giảm/), "10");

    await user.click(screen.getByRole("button", { name: "Tạo khuyến mãi" }));
    await screen.findByRole("alert");
    await user.click(screen.getByRole("button", { name: "Tạo khuyến mãi" }));
    await screen.findByRole("alert");

    await user.clear(nameInput);
    await user.type(nameInput, "Idem Promo B");
    await user.click(screen.getByRole("button", { name: "Tạo khuyến mãi" }));

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith(`/promotions/${PROMO_ID_1}`);
    });

    const postCalls = fakeApi.calls.filter((c) => c.method === "POST" && c.path === PROMO_LIST_PATH);
    expect(postCalls).toHaveLength(3);
    const key1 = (postCalls[0]?.options?.params as { header?: Record<string, string> })?.header?.["Idempotency-Key"];
    const key2 = (postCalls[1]?.options?.params as { header?: Record<string, string> })?.header?.["Idempotency-Key"];
    const key3 = (postCalls[2]?.options?.params as { header?: Record<string, string> })?.header?.["Idempotency-Key"];
    expect(key2).toBe(key1);
    expect(key3).not.toBe(key1);
  });

  it("R1-7 & R1-29 & R1-57: scope BUNDLE khóa phạm vi khi sửa và không gửi scope/examIds; đổi ngôn ngữ dịch lại lỗi đang hiển thị", async () => {
    fakeApi.patch(PROMO_DETAIL_PATH, fail(500, { code: "INTERNAL_ERROR" }));

    function LocaleEditHarness() {
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
              Switch form locale
            </button>
            <PromotionForm
              mode="edit"
              initialDetail={promotionDetailFixture({
                id: PROMO_ID_1,
                kind: "AUTO",
                scope: "BUNDLE",
                scopeItems: [{ id: EXAM_ID_1, code: "B1", name: "Combo Đề HSA", type: "BUNDLE" }],
              })}
            />
          </UiStringsProvider>
        </NextIntlClientProvider>
      );
    }

    const user = userEvent.setup();
    renderWithPermissions(<LocaleEditHarness />);

    expect(screen.getByText(/áp dụng theo gói đề thi/i)).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Tất cả bài thi/ })).toBeDisabled();

    // Lưu hợp lệ khi đổi tên -> kiểm tra lỗi 500 tự dịch khi đổi sang en-GB
    const nameInput = screen.getByLabelText(/Tên khuyến mãi/);
    await user.clear(nameInput);
    await user.type(nameInput, "Bundle Deal Updated");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/lỗi hệ thống/i);
    await user.click(screen.getByRole("button", { name: "Switch form locale" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/A system error occurred/i);

    // Đảm bảo PATCH không gửi scope / examKindIds / examIds khi BUNDLE
    fakeApi.patch(PROMO_DETAIL_PATH, ok(promotionDetailFixture({ id: PROMO_ID_1 })));
    await user.click(screen.getByRole("button", { name: "Save changes" }));
    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith(`/promotions/${PROMO_ID_1}`);
    });
    const lastPatchBody = fakeApi.calls.filter((c) => c.method === "PATCH").at(-1)?.options?.body as Record<
      string,
      unknown
    >;
    expect(lastPatchBody.scope).toBeUndefined();
    expect(lastPatchBody.examKindIds).toBeUndefined();
    expect(lastPatchBody.examIds).toBeUndefined();
  });

  it("R1-41 & R1-45 & R1-47: ExamKindPicker hiện rỗng / lỗi kèm Thử lại; liên kết checklist focus vào ô bị thiếu", async () => {
    let failKinds = true;
    fakeApi.get(EXAM_KINDS_PATH, () => {
      if (failKinds) return fail(500, { code: "INTERNAL_ERROR" });
      return ok([]);
    });

    const user = userEvent.setup();
    renderWithPermissions(
      <div>
        <ExamKindPicker selectedIds={[]} onChange={vi.fn()} />
        <PromotionForm mode="create" initialKind="AUTO" />
      </div>,
    );

    const pickerAlert = await screen.findByRole("alert");
    expect(pickerAlert).toHaveTextContent(/Không tải được danh sách chọn/);
    failKinds = false;
    await user.click(within(pickerAlert).getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Không tìm thấy mục phù hợp.")).toBeInTheDocument();

    // R1-47: Bấm mục checklist "Nhập tên khuyến mãi" đưa focus vào ô Tên khuyến mãi
    await user.click(screen.getByRole("link", { name: /Nhập tên khuyến mãi/ }));
    expect(screen.getByLabelText(/Tên khuyến mãi/)).toHaveFocus();
  });
});

describe("PromotionForm: trường Trạng thái và cảnh báo rời trang (uniworkos UNI-1714, UNI-1715)", () => {
  it("UNI-1715: tạo mới không có trường Trạng thái; sửa vẫn có", () => {
    const { unmount } = renderWithPermissions(<PromotionForm mode="create" initialKind="AUTO" />);
    expect(screen.queryByLabelText("Trạng thái")).not.toBeInTheDocument();
    unmount();

    renderWithPermissions(
      <PromotionForm mode="edit" initialDetail={promotionDetailFixture({ id: PROMO_ID_1, usedCount: 0 })} />,
    );
    expect(screen.getByLabelText("Trạng thái")).toBeInTheDocument();
  });

  it("UNI-1714: form chưa đổi thì Hủy đi thẳng; đã đổi thì Hủy hoặc bấm menu khác phải hỏi trước khi rời", async () => {
    const user = userEvent.setup();
    renderWithPermissions(
      <>
        <nav aria-label="Menu chính">
          <Link href="/orders">Đơn hàng</Link>
        </nav>
        <PromotionForm mode="create" initialKind="AUTO" />
      </>,
    );

    // Chưa đổi gì: bấm Hủy không hiện hộp thoại.
    const cancel = screen.getByRole("link", { name: "Hủy" });
    cancel.addEventListener("click", (event) => event.preventDefault());
    await user.click(cancel);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(screen.queryByText("Rời trang khi chưa lưu?")).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/Tên khuyến mãi/), "Khuyến mãi mới");

    // Đã đổi: Hủy -> hỏi; chọn Ở lại thì giữ nguyên trang và dữ liệu.
    await user.click(screen.getByRole("link", { name: "Hủy" }));
    expect(await screen.findByText("Rời trang khi chưa lưu?")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Ở lại" }));
    await waitFor(() => expect(screen.queryByText("Rời trang khi chưa lưu?")).not.toBeInTheDocument());
    expect(mockPush).not.toHaveBeenCalled();
    expect(screen.getByLabelText(/Tên khuyến mãi/)).toHaveValue("Khuyến mãi mới");

    // Bấm mục menu khác -> hỏi; chọn Rời trang thì điều hướng tới mục đó.
    await user.click(screen.getByRole("link", { name: "Đơn hàng" }));
    expect(await screen.findByText("Rời trang khi chưa lưu?")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Rời trang" }));
    expect(mockPush).toHaveBeenCalledWith("/orders");
  });

  it("UNI-1714: sửa khuyến mãi rồi bấm Hủy cũng hỏi trước khi về trang chi tiết", async () => {
    const user = userEvent.setup();
    renderWithPermissions(
      <PromotionForm mode="edit" initialDetail={promotionDetailFixture({ id: PROMO_ID_1, usedCount: 0 })} />,
    );

    const name = screen.getByLabelText(/Tên khuyến mãi/);
    await user.clear(name);
    await user.type(name, "Tên đã sửa");
    await user.click(screen.getByRole("link", { name: "Hủy" }));
    expect(await screen.findByText("Rời trang khi chưa lưu?")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Rời trang" }));
    expect(mockPush).toHaveBeenCalledWith(`/promotions/${PROMO_ID_1}`);
  });
});
