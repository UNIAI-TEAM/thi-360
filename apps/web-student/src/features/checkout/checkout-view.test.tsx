import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  type CreateOrderResponse,
  OrderConflictError,
  type PriceQuote,
  ProductGoneError,
} from "@/features/checkout/api";
import type { OrderDetail } from "@/features/orders/api";
import type { ProductDetail } from "@/features/storefront/types";
import { ApiStatusError } from "@/lib/api/client";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { ADMIN_SESSION, STUDENT_SESSION } from "@/test/storefront-fixtures";
import { CheckoutView } from "./checkout-view";

const navState = {
  pathname: "/checkout/HSA-FULL-01",
  search: "",
};

vi.mock("next/navigation", () => ({
  usePathname: () => navState.pathname,
  useSearchParams: () => new URLSearchParams(navState.search),
}));

function makeProduct(overrides: Partial<ProductDetail> = {}): ProductDetail {
  return {
    code: "HSA-FULL-01",
    name: "Đề thi thử ĐGNL HSA số 1",
    format: "FULL",
    examKind: { code: "HSA", nameVi: "Đánh giá năng lực HSA", nameEn: "HSA Aptitude Exam" },
    buildMode: "STANDARD",
    attemptsGranted: 2,
    listPrice: 300000,
    price: 250000,
    versionNo: 2,
    questionCount: { min: 150, max: 150 },
    durationMinutes: { min: 195, max: 195 },
    benefits: { aiExplanation: true, strengthWeakness: true, practice: false, note: "Ghi chú từ đề" },
    resultView: "FULL_SOLUTION",
    choices: [],
    omittedEssayGroups: [],
    parts: [],
    scoring: { maxScore: 150 },
    ...overrides,
  };
}

function makeQuote(overrides: Partial<PriceQuote> = {}): PriceQuote {
  return {
    productCode: "HSA-FULL-01",
    productName: "Đề thi thử ĐGNL HSA số 1",
    listPrice: 300000,
    finalPrice: 250000,
    attemptsGranted: 2,
    discountApplied: "PROMOTION_ONLY",
    versionNo: 2,
    changed: false,
    voucherNotBetter: false,
    promotion: {
      id: "promo-1",
      name: "Ưu đãi tháng 11",
      discountType: "AMOUNT",
      discountValue: 50000,
      discountVnd: 50000,
      endsAt: "2026-12-01T00:00:00Z",
    },
    ...overrides,
  };
}

function makeOrderDetail(overrides: Partial<OrderDetail> = {}): OrderDetail {
  return {
    id: "ord-1",
    code: "DH261121NEW",
    userId: STUDENT_SESSION.id,
    buyerUsername: STUDENT_SESSION.username,
    buyerFullName: STUDENT_SESSION.fullName,
    productType: "EXAM",
    examId: "exam-1",
    examVersionNo: 2,
    optionCodes: [],
    productCode: "HSA-FULL-01",
    productName: "Đề thi thử ĐGNL HSA số 1",
    listPriceVnd: 300000,
    promotionDiscountVnd: 50000,
    voucherDiscountVnd: 0,
    discountApplied: "PROMOTION_ONLY",
    amountDueVnd: 250000,
    attemptsGranted: 2,
    benefits: { aiExplanation: true, strengthWeakness: true, practice: false } as unknown as OrderDetail["benefits"],
    resultView: "FULL_SOLUTION",
    paymentMethod: "VNPAY",
    state: "PENDING_PAYMENT",
    createdAt: "2026-11-21T03:00:00Z",
    updatedAt: "2026-11-21T03:00:00Z",
    items: [],
    payments: [],
    history: [],
    ...overrides,
  };
}

describe("CheckoutView", () => {
  beforeEach(() => {
    navState.pathname = "/checkout/HSA-FULL-01";
    navState.search = "";
  });

  it("tạo đơn VNPAY và chuyển tới paymentUrl với đúng Idempotency-Key + chống click đúp (AC-11)", async () => {
    const user = userEvent.setup();
    let resolveOrder!: (value: CreateOrderResponse) => void;
    const submitOrder = vi.fn(
      () =>
        new Promise<CreateOrderResponse>((resolve) => {
          resolveOrder = resolve;
        }),
    );
    const navigate = vi.fn();

    renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeQuote()}
        submitOrder={submitOrder}
        navigate={navigate}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Xác nhận mua" })).toBeInTheDocument();
    expect(screen.getByText("Đề thi thử ĐGNL HSA số 1")).toHaveAttribute("data-exam-content");
    expect(screen.getByText(/Sau khi hoàn tất, bài thi sẽ được cấp cho tài khoản/)).toHaveTextContent(
      STUDENT_SESSION.username,
    );

    const payButton = await screen.findByRole("button", { name: "Thanh toán qua VNPAY" });
    await user.dblClick(payButton);

    expect(submitOrder).toHaveBeenCalledTimes(1);
    expect(submitOrder).toHaveBeenCalledWith(
      {
        code: "HSA-FULL-01",
        expectedAccountId: STUDENT_SESSION.id,
        expectedAmountDue: 250000,
        expectedVersionNo: 2,
      },
      expect.any(String),
    );

    resolveOrder({
      next: "REDIRECT",
      order: makeOrderDetail(),
      paymentUrl: "http://localhost:8080/mock-vnpay?vnp_TxnRef=DH261121NEW01",
    });

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith("http://localhost:8080/mock-vnpay?vnp_TxnRef=DH261121NEW01"),
    );
  });

  it("chuyển sang /checkout/result khi đơn 0 đ voucher (FULFILLED) hoặc next=CHECKING (AC-4, AC-5)", async () => {
    const user = userEvent.setup();
    const navigateFree = vi.fn();

    const requestQuote = vi
      .fn()
      .mockResolvedValueOnce(
        makeQuote({
          finalPrice: 0,
          discountApplied: "VOUCHER_ONLY",
          promotion: undefined,
          voucher: {
            id: "v-free",
            code: "MIENPHI",
            name: "100%",
            discountType: "PERCENT",
            discountValue: 100,
            discountVnd: 300000,
            combinable: false,
          },
        }),
      );

    const freeView = renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={requestQuote}
        submitOrder={vi.fn().mockResolvedValue({
          next: "FULFILLED",
          order: makeOrderDetail({ code: "DHFREE01", state: "FULFILLED", amountDueVnd: 0, voucherDiscountVnd: 300000 }),
        })}
        navigate={navigateFree}
      />,
    );

    expect(await screen.findByText("0 đ (đã áp dụng voucher)")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Dùng voucher và nhận bài thi" }));
    await waitFor(() => expect(navigateFree).toHaveBeenCalledWith("/checkout/result?order=DHFREE01"));
    freeView.unmount();

    const navigateChecking = vi.fn();
    renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeQuote()}
        submitOrder={vi.fn().mockResolvedValue({
          next: "CHECKING",
          order: makeOrderDetail({ code: "DHCHK01", state: "PENDING_PAYMENT" }),
        })}
        navigate={navigateChecking}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Thanh toán qua VNPAY" }));
    await waitFor(() =>
      expect(navigateChecking).toHaveBeenCalledWith("/checkout/result?order=DHCHK01&state=checking"),
    );
  });

  it("hiện banner giá thay đổi khi lệch ?v/&p hoặc 409 PRICE_CHANGED (AC-8)", async () => {
    const user = userEvent.setup();
    navState.search = "v=1&p=200000";

    const submitOrder = vi
      .fn()
      .mockRejectedValueOnce(
        new OrderConflictError(
          "PRICE_CHANGED",
          "Giá thay đổi",
          makeQuote({ finalPrice: 280000, versionNo: 3, changed: true }),
        ),
      )
      .mockResolvedValueOnce({
        next: "REDIRECT",
        order: makeOrderDetail({ amountDueVnd: 280000 }),
        paymentUrl: "http://mock/pay",
      });

    renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeQuote({ versionNo: 2, finalPrice: 250000, changed: true })}
        submitOrder={submitOrder}
        navigate={vi.fn()}
      />,
    );

    const statusBanner = await screen.findByRole("status");
    expect(statusBanner).toHaveTextContent("Giá hoặc quyền lợi đã thay đổi");
    expect(statusBanner).toHaveTextContent("250.000 đ");
    expect(statusBanner).toHaveTextContent("200.000 đ");

    await user.click(screen.getByRole("button", { name: "Thanh toán qua VNPAY" }));
    await waitFor(() => expect(screen.getAllByText(/280\.000 đ/).length).toBeGreaterThan(0));

    await user.click(screen.getByRole("button", { name: "Thanh toán qua VNPAY" }));
    expect(submitOrder).toHaveBeenLastCalledWith(
      expect.objectContaining({
        expectedAmountDue: 280000,
        expectedVersionNo: 3,
      }),
      expect.any(String),
    );
  });

  it("cảnh báo ACCOUNT_CHANGED khi đổi tài khoản tab khác và yêu cầu xác nhận trước khi mua (AC-7)", async () => {
    const user = userEvent.setup();
    const otherStudent = { ...STUDENT_SESSION, id: "user-other-2", username: "student02", fullName: "Học viên 02" };
    const loadCurrentSession = vi
      .fn()
      .mockResolvedValueOnce(STUDENT_SESSION)
      .mockResolvedValue(otherStudent);

    const submitOrder = vi
      .fn()
      .mockRejectedValueOnce(new OrderConflictError("ACCOUNT_CHANGED", "Account changed"))
      .mockResolvedValueOnce({
        next: "REDIRECT",
        order: makeOrderDetail({ userId: otherStudent.id, buyerUsername: otherStudent.username }),
        paymentUrl: "http://mock/pay-2",
      });

    renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={loadCurrentSession}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeQuote()}
        submitOrder={submitOrder}
        navigate={vi.fn()}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Thanh toán qua VNPAY" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Tài khoản đăng nhập đã thay đổi");
    expect(alert).toHaveTextContent("student02");
    expect(screen.getByRole("button", { name: "Thanh toán qua VNPAY" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Tiếp tục với student02" }));

    const payBtn = screen.getByRole("button", { name: "Thanh toán qua VNPAY" });
    expect(payBtn).toBeEnabled();
    await user.click(payBtn);

    expect(submitOrder).toHaveBeenLastCalledWith(
      expect.objectContaining({
        expectedAccountId: "user-other-2",
      }),
      expect.any(String),
    );
  });

  it("hiện banner đang có lượt và khóa mua khi có đơn CHECKING (AC-9)", async () => {
    const existingView = renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({
          kind: "rows",
          rows: [
            {
              code: "HSA-FULL-01",
              state: "CAN_START",
              attemptsLeft: 2,
              entitlementId: "ent-1",
              hasPendingFulfillment: false,
            },
          ],
        })}
        requestQuote={async () => makeQuote()}
      />,
    );

    expect(await screen.findByText("Bạn đang có 2 lượt với bài thi này")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Vào bài thi" })).toHaveAttribute("href", "/prepare/ent-1");
    expect(screen.getByRole("button", { name: "Mua thêm lượt" })).toBeInTheDocument();
    existingView.unmount();

    renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({
          kind: "rows",
          rows: [
            {
              code: "HSA-FULL-01",
              state: "CHECKING",
              attemptsLeft: 0,
              hasPendingFulfillment: true,
              pendingOrderCode: "DH_CHECKING_9",
            },
          ],
        })}
        requestQuote={async () => makeQuote()}
      />,
    );

    expect(await screen.findByText("Đang kiểm tra đơn DH_CHECKING_9")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem trạng thái thanh toán" })).toHaveAttribute(
      "href",
      "/checkout/result?order=DH_CHECKING_9&state=checking",
    );
    expect(screen.getByRole("button", { name: "Thanh toán qua VNPAY" })).toBeDisabled();
  });

  it("hiển thị 410 ngừng bán khi load hoặc khi bấm thanh toán trả 410 (AC-10)", async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeQuote()}
        submitOrder={vi.fn().mockRejectedValueOnce(new ProductGoneError({ code: "HSA-FULL-01", name: "Đề HSA" }))}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Thanh toán qua VNPAY" }));
    expect(await screen.findByRole("heading", { name: "Bài thi này hiện đã ngừng bán" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Về danh mục bài thi" })).toHaveAttribute("href", "/#exams");
  });

  it("chuyển về login cho guest 401, chặn admin và tuân thủ quy tắc song ngữ en (AC-12)", async () => {
    const navigateGuest = vi.fn();
    navState.search = "v=2&p=250000";

    const guestView = renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        loadCurrentSession={vi.fn().mockRejectedValue(new ApiStatusError(401, "Unauthorized", { code: "UNAUTHORIZED" }))}
        navigate={navigateGuest}
      />,
    );

    await waitFor(() =>
      expect(navigateGuest).toHaveBeenCalledWith(
        "/login?callbackUrl=%2Fcheckout%2FHSA-FULL-01%3Fv%3D2%26p%3D250000",
      ),
    );
    guestView.unmount();

    const adminView = renderWithProviders(
      <CheckoutView code="HSA-FULL-01" loadCurrentSession={async () => ADMIN_SESSION} />,
    );
    expect(await screen.findByRole("heading", { name: "Tài khoản quản trị" })).toBeInTheDocument();
    adminView.unmount();

    renderWithProviders(
      <CheckoutView
        code="HSA-SUBJ-MATH"
        initialProduct={{
          status: "ok",
          product: makeProduct({
            code: "HSA-SUBJ-MATH",
            format: "SUBJECT",
            scopeLabel: { nameVi: "Toán học", nameEn: "Mathematics" },
          }),
        }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeQuote({ productCode: "HSA-SUBJ-MATH" })}
      />,
      { locale: "en", messages: enMessages },
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Confirm purchase" })).toBeInTheDocument();
    expect(screen.getByText("HSA Aptitude Exam")).not.toHaveAttribute("data-exam-content");
    expect(screen.getByText("Đề thi thử ĐGNL HSA số 1")).toHaveAttribute("data-exam-content");
  });
});
