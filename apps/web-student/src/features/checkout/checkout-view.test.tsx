import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  type CreateOrderResponse,
  OrderConflictError,
  type PriceQuote,
  ProductGoneError,
} from "@/features/checkout/api";
import type { OrderDetail } from "@/features/orders/api";
import type * as SessionApi from "@/features/session/api";
import type { ProductDetail } from "@/features/storefront/types";
import { ApiStatusError } from "@/lib/api/client";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { QueryProvider } from "@/providers/query-provider";
import { renderWithProviders } from "@/test/render";
import { ADMIN_SESSION, STUDENT_SESSION } from "@/test/storefront-fixtures";
import { CheckoutView } from "./checkout-view";

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  refreshSession: vi.fn(),
  clearSessionBestEffort: vi.fn(),
}));

const navState = {
  pathname: "/checkout/HSA-FULL-01",
  search: "",
};

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => navState.pathname,
  useSearchParams: () => new URLSearchParams(navState.search),
}));

vi.mock("@/features/session/api", async (importOriginal) => {
  const actual = await importOriginal<typeof SessionApi>();
  return {
    ...actual,
    refreshSession: mocks.refreshSession,
    clearSessionBestEffort: mocks.clearSessionBestEffort,
  };
});

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
    mocks.replace.mockReset();
    mocks.refreshSession.mockReset();
    mocks.refreshSession.mockResolvedValue(undefined);
    mocks.clearSessionBestEffort.mockReset();
    mocks.clearSessionBestEffort.mockResolvedValue(undefined);
    window.sessionStorage.clear();
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
    // R1-63: quyền lợi hiển thị rõ Có / Không cho trình đọc màn hình
    expect(screen.getByText("Giải thích bằng AI: Có")).toBeInTheDocument();
    expect(screen.getByText("Đánh giá điểm mạnh, điểm yếu: Có")).toBeInTheDocument();
    expect(screen.getByText("Ôn luyện bổ sung: Không")).toBeInTheDocument();

    const payButton = await screen.findByRole("button", { name: "Thanh toán qua VNPAY" });
    await user.dblClick(payButton);

    // R1-65: giữ nguyên nhãn nút khi loading
    expect(payButton).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByRole("button", { name: "Đang tạo đơn hàng..." })).toBeNull();

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

  it("chuyển sang /checkout/result khi đơn 0 đ voucher (FULFILLED), next=CHECKING với 0 đ voucher, hoặc 0 đ không voucher (AC-4, AC-5, R1-8, R1-48)", async () => {
    const user = userEvent.setup();
    const navigateFree = vi.fn();

    const freeVoucherQuote = makeQuote({
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
    });

    const freeView = renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => freeVoucherQuote}
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

    // AC-5: 0 đ voucher trả next=CHECKING
    const navigateChecking = vi.fn();
    const checkingView = renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => freeVoucherQuote}
        submitOrder={vi.fn().mockResolvedValue({
          next: "CHECKING",
          order: makeOrderDetail({ code: "DHCHK01", state: "PAID", paymentMethod: "FREE", amountDueVnd: 0 }),
        })}
        navigate={navigateChecking}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Dùng voucher và nhận bài thi" }));
    await waitFor(() =>
      expect(navigateChecking).toHaveBeenCalledWith("/checkout/result?order=DHCHK01&state=checking"),
    );
    checkingView.unmount();

    // R1-8: đơn 0 đ KHÔNG có voucher hiện nhãn trung lập
    renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct({ listPrice: 0, price: 0 }) }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () =>
          makeQuote({
            listPrice: 0,
            finalPrice: 0,
            discountApplied: "NONE",
            promotion: undefined,
            voucher: undefined,
          })
        }
      />,
    );

    expect(await screen.findByRole("button", { name: "Nhận bài thi miễn phí" })).toBeInTheDocument();
    expect(screen.queryByText("0 đ (đã áp dụng voucher)")).toBeNull();
  });

  it("hiện banner giá thay đổi khi lệch ?v/&p và sau 409 PRICE_CHANGED (AC-8)", async () => {
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

    await screen.findByRole("heading", { name: "Giá hoặc quyền lợi đã thay đổi" });
    const statusBanner = screen.getByRole("heading", { name: "Giá hoặc quyền lợi đã thay đổi" }).closest('[role="status"]');
    expect(statusBanner).not.toBeNull();
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

  it("409 PRICE_CHANGED không cần ?v/&p vẫn hiện giá cũ và giá mới trong role=status và reset VoucherBox (R1-4, R1-28, R1-49)", async () => {
    const user = userEvent.setup();
    navState.search = "";

    const requestQuote = vi
      .fn()
      .mockResolvedValueOnce(makeQuote({ versionNo: 2, finalPrice: 250000, changed: false }))
      .mockResolvedValueOnce(
        makeQuote({
          versionNo: 2,
          finalPrice: 200000,
          discountApplied: "VOUCHER_ONLY",
          promotion: undefined,
          voucher: {
            id: "v-1",
            code: "GIAM50K",
            name: "50k",
            discountType: "AMOUNT",
            discountValue: 50000,
            discountVnd: 50000,
            combinable: false,
          },
        }),
      );

    const submitOrder = vi.fn().mockRejectedValueOnce(
      new OrderConflictError(
        "PRICE_CHANGED",
        "Price changed",
        makeQuote({ finalPrice: 280000, versionNo: 3, changed: false, voucher: undefined }),
      ),
    );

    renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={requestQuote}
        submitOrder={submitOrder}
      />,
    );

    await user.type(await screen.findByLabelText("Nhập mã giảm giá"), "GIAM50K");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));
    expect(await screen.findByText(/Đã áp dụng mã GIAM50K/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Thanh toán qua VNPAY" }));

    const statusBanner = await screen.findByRole("status");
    expect(statusBanner).toHaveTextContent("Giá hoặc quyền lợi đã thay đổi");
    expect(statusBanner).toHaveTextContent("200.000 đ");
    expect(statusBanner).toHaveTextContent("280.000 đ");
    // R1-4 / R1-28: VoucherBox phải xóa input và banner voucher cũ
    expect(screen.queryByText(/Đã áp dụng mã GIAM50K/)).toBeNull();
    expect(screen.getByLabelText("Nhập mã giảm giá")).toHaveValue("");
  });

  it("VoucherBox trả changed:true / lệch versionNo kích hoạt hasBaselineMismatch (R1-26)", async () => {
    const user = userEvent.setup();
    const requestQuote = vi
      .fn()
      .mockResolvedValueOnce(makeQuote({ versionNo: 2, finalPrice: 250000, changed: false }))
      .mockResolvedValueOnce(
        makeQuote({
          versionNo: 3,
          finalPrice: 260000,
          changed: true,
        }),
      );

    renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={requestQuote}
      />,
    );

    await user.type(await screen.findByLabelText("Nhập mã giảm giá"), "TESTCODE");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));

    const statusBanner = await screen.findByRole("status");
    expect(statusBanner).toHaveTextContent("Giá hoặc quyền lợi đã thay đổi");
    expect(statusBanner).toHaveTextContent("260.000 đ");
  });

  it("giữ Idempotency-Key khi lỗi 500 và tạo khóa mới khi 409/422; xử lý 422 VOUCHER_*, PAYMENT_ATTEMPTS_EXCEEDED và 401 (R1-2, R1-19, R1-20, R1-21, R1-51)", async () => {
    const user = userEvent.setup();
    const loadCurrentSession = vi.fn().mockResolvedValue(STUDENT_SESSION);
    const submitOrder = vi
      .fn()
      .mockRejectedValueOnce(new ApiStatusError(500, "Server error"))
      .mockRejectedValueOnce(new ApiStatusError(422, "Exhausted", { code: "VOUCHER_EXHAUSTED" }))
      .mockRejectedValueOnce(new ApiStatusError(422, "Exceeded", { code: "PAYMENT_ATTEMPTS_EXCEEDED" }))
      .mockRejectedValueOnce(new ApiStatusError(401, "Unauthorized"));

    renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={loadCurrentSession}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeQuote()}
        submitOrder={submitOrder}
      />,
    );

    const payBtn = await screen.findByRole("button", { name: "Thanh toán qua VNPAY" });

    // Lần 1: 500 -> giữ key
    await user.click(payBtn);
    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tạo đơn hàng");
    const firstKey = submitOrder.mock.calls[0]![1];

    // Lần 2: retry sau 500 phải dùng CÙNG Idempotency-Key, lần 2 trả 422 VOUCHER_EXHAUSTED -> xóa key
    await user.click(payBtn);
    expect(submitOrder.mock.calls[1]![1]).toBe(firstKey);
    expect(await screen.findByRole("alert")).toHaveTextContent("Mã giảm giá đã hết lượt sử dụng.");

    // Lần 3: sau 422 phải tạo key MỚI, trả 422 PAYMENT_ATTEMPTS_EXCEEDED
    await user.click(payBtn);
    const thirdKey = submitOrder.mock.calls[2]![1];
    expect(thirdKey).not.toBe(firstKey);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Đơn đã vượt quá số lần tạo giao dịch thanh toán",
    );

    // Lần 4: 401 trên submitOrder kích hoạt sessionQuery.refetch()
    const callsBefore = loadCurrentSession.mock.calls.length;
    await user.click(payBtn);
    await waitFor(() => expect(loadCurrentSession.mock.calls.length).toBeGreaterThan(callsBefore));
  });

  it("cảnh báo ACCOUNT_CHANGED khi đổi tài khoản tab khác, reset VoucherBox và không fallback về phiên cũ khi refetch lỗi (AC-7, R1-3, R1-22)", async () => {
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

    const firstAccountView = renderWithProviders(
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

    await user.type(await screen.findByLabelText("Nhập mã giảm giá"), "OLDCODE");
    await user.click(screen.getByRole("button", { name: "Thanh toán qua VNPAY" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Tài khoản đăng nhập đã thay đổi");
    expect(alert).toHaveTextContent("student02");
    expect(screen.getByRole("button", { name: "Thanh toán qua VNPAY" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Tiếp tục với student02" }));
    expect(screen.getByLabelText("Nhập mã giảm giá")).toHaveValue("");

    const payBtn = screen.getByRole("button", { name: "Thanh toán qua VNPAY" });
    expect(payBtn).toBeEnabled();
    await user.click(payBtn);

    expect(submitOrder).toHaveBeenLastCalledWith(
      expect.objectContaining({
        expectedAccountId: "user-other-2",
      }),
      expect.any(String),
    );
    firstAccountView.unmount();

    // R1-3 / R1-22: nếu refetch thất bại sau 409 ACCOUNT_CHANGED thì chuyển về /login, không hiện xung đột giả student01 -> student01
    const failRefetchSession = vi
      .fn()
      .mockResolvedValueOnce(STUDENT_SESSION)
      .mockRejectedValueOnce(new ApiStatusError(500, "Session error"));
    const navFail = vi.fn();

    renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={failRefetchSession}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeQuote()}
        submitOrder={vi.fn().mockRejectedValueOnce(new OrderConflictError("ACCOUNT_CHANGED", "Changed"))}
        navigate={navFail}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Thanh toán qua VNPAY" }));

    await waitFor(() =>
      expect(navFail).toHaveBeenCalledWith("/login?callbackUrl=%2Fcheckout%2FHSA-FULL-01"),
    );
    expect(screen.queryByText(/nhưng phiên hiện tại đang là student01/)).toBeNull();
  });

  it("hiện banner đang có lượt, khóa mua khi checking / access loading / access error, và xử lý IN_PROGRESS với 0 lượt (AC-9, R1-5, R1-25, R1-27)", async () => {
    const user = userEvent.setup();

    // R1-25: accessQuery đang loading -> nút mua bị khóa
    const pendingView = renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={() => new Promise(() => {})}
        requestQuote={async () => makeQuote()}
      />,
    );
    expect(await screen.findByRole("button", { name: "Thanh toán qua VNPAY" })).toBeDisabled();
    pendingView.unmount();

    // R1-25: accessQuery lỗi -> nút mua bị khóa + có alert và nút Thử lại
    const loadAccessFail = vi
      .fn()
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce({
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
      });
    const errAccessView = renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={loadAccessFail}
        requestQuote={async () => makeQuote()}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("Chưa kiểm tra được quyền sử dụng");
    expect(screen.getByRole("button", { name: "Thanh toán qua VNPAY" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Bạn đang có 2 lượt với bài thi này")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Vào bài thi" })).toHaveAttribute("href", "/prepare/ent-1");
    expect(screen.getByRole("button", { name: "Thanh toán qua VNPAY" })).toBeEnabled();
    errAccessView.unmount();

    // R1-5 / R1-27: IN_PROGRESS với attemptsLeft: 0 dùng existingInProgressTitle và encodeURIComponent
    const inProgressView = renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({
          kind: "rows",
          rows: [
            {
              code: "HSA-FULL-01",
              state: "IN_PROGRESS",
              attemptsLeft: 0,
              activeAttemptId: "att/1?x",
              hasPendingFulfillment: false,
            },
          ],
        })}
        requestQuote={async () => makeQuote()}
      />,
    );
    expect(await screen.findByText("Bạn đang có lượt làm dở (0 lượt với bài thi này)")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tiếp tục" })).toHaveAttribute("href", "/attempts/att%2F1%3Fx");
    inProgressView.unmount();

    // R1-5: số ít trong tiếng Anh ("1 attempt")
    const enSingularView = renderWithProviders(
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
              attemptsLeft: 1,
              entitlementId: "ent-1",
              hasPendingFulfillment: false,
            },
          ],
        })}
        requestQuote={async () => makeQuote()}
      />,
      { locale: "en", messages: enMessages },
    );
    expect(await screen.findByText("You currently have 1 attempt for this exam")).toBeInTheDocument();
    enSingularView.unmount();

    // CHECKING banner khóa mua
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

  it("hiện lỗi phiên 500 với initialProduct và cho phép Thử lại (R1-1, R1-23, R1-60)", async () => {
    const user = userEvent.setup();
    const loadCurrentSession = vi
      .fn()
      .mockRejectedValueOnce(new ApiStatusError(500, "Session down"))
      .mockResolvedValueOnce(STUDENT_SESSION);

    renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={loadCurrentSession}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeQuote()}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Không thể tải thông tin thanh toán" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { level: 1, name: "Xác nhận mua" })).toBeInTheDocument();
  });

  it("hiển thị 410 ngừng bán khi load hoặc khi bấm thanh toán trả 410 và bọc data-exam-content cho gone/promotion (AC-10, R1-59)", async () => {
    const user = userEvent.setup();
    const goneView = renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct() }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeQuote()}
        submitOrder={vi.fn().mockRejectedValueOnce(new ProductGoneError({ code: "HSA-FULL-01", name: "Đề HSA" }))}
      />,
    );

    expect(await screen.findByText("Ưu đãi tháng 11")).toHaveAttribute("data-exam-content");
    await user.click(screen.getByRole("button", { name: "Thanh toán qua VNPAY" }));
    expect(await screen.findByRole("heading", { level: 1, name: "Bài thi này hiện đã ngừng bán" })).toBeInTheDocument();
    expect(screen.getByText("Đề HSA")).toHaveAttribute("data-exam-content");
    expect(screen.getByRole("link", { name: "Về danh mục bài thi" })).toHaveAttribute("href", "/#exams");
    goneView.unmount();
  });

  it("reset state khi đổi prop code và cô lập cache quote theo userId (R1-7, R1-29)", async () => {
    const user = userEvent.setup();
    const requestQuote = vi.fn(async ({ code }: { code: string }) =>
      makeQuote({ productCode: code, versionNo: code === "HSA-FULL-01" ? 2 : 5 }),
    );
    const submitOrder = vi.fn().mockRejectedValue(new ApiStatusError(500, "Server down"));

    const view = renderWithProviders(
      <CheckoutView
        code="HSA-FULL-01"
        initialProduct={{ status: "ok", product: makeProduct({ code: "HSA-FULL-01" }) }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={requestQuote}
        submitOrder={submitOrder}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Thanh toán qua VNPAY" }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();

    view.rerender(
      <CheckoutView
        code="HSA-FULL-02"
        initialProduct={{ status: "ok", product: makeProduct({ code: "HSA-FULL-02", name: "Đề HSA số 2" }) }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={requestQuote}
        submitOrder={submitOrder}
      />,
    );

    await waitFor(() => expect(screen.queryByRole("alert")).toBeNull());
    expect(await screen.findByText("Phiên bản v5")).toBeInTheDocument();
  });

  it("401 qua QueryProvider làm mới phiên trước khi đẩy về /login, chặn admin và tuân thủ song ngữ en (AC-12, R1-24, R1-59, R1-63)", async () => {
    const navigateRefresh = vi.fn();
    navState.search = "v=2&p=250000";

    // 401 khi refreshSession thành công -> giữ skeleton và reload URL hiện tại, không đẩy /login
    const refreshView = render(
      <NextIntlClientProvider locale="vi" messages={viMessages} timeZone="Asia/Ho_Chi_Minh">
        <QueryProvider navigate={navigateRefresh}>
          <CheckoutView
            code="HSA-FULL-01"
            loadCurrentSession={vi
              .fn()
              .mockRejectedValue(new ApiStatusError(401, "Unauthorized", { code: "UNAUTHORIZED" }))}
          />
        </QueryProvider>
      </NextIntlClientProvider>,
    );

    expect(screen.getByRole("status", { name: "Đang tải" })).toHaveAttribute("aria-busy", "true");
    await waitFor(() => expect(mocks.refreshSession).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(navigateRefresh).toHaveBeenCalledWith(window.location.href));
    refreshView.unmount();

    // 401 khi refreshSession cũng 401 -> QueryProvider chuyển về /login kèm callbackUrl
    mocks.refreshSession.mockRejectedValueOnce(new ApiStatusError(401, "Expired"));
    const navigateGuest = vi.fn();
    const guestView = render(
      <NextIntlClientProvider locale="vi" messages={viMessages} timeZone="Asia/Ho_Chi_Minh">
        <QueryProvider navigate={navigateGuest}>
          <CheckoutView
            code="HSA-FULL-01"
            loadCurrentSession={vi
              .fn()
              .mockRejectedValue(new ApiStatusError(401, "Unauthorized", { code: "UNAUTHORIZED" }))}
          />
        </QueryProvider>
      </NextIntlClientProvider>,
    );

    await waitFor(() =>
      expect(navigateGuest).toHaveBeenCalledWith(
        `http://localhost:3000/login?callbackUrl=${encodeURIComponent(window.location.href)}`,
      ),
    );
    guestView.unmount();

    const adminView = renderWithProviders(
      <CheckoutView code="HSA-FULL-01" loadCurrentSession={async () => ADMIN_SESSION} />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Tài khoản quản trị" })).toBeInTheDocument();
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
    expect(await screen.findByText("Ưu đãi tháng 11")).toHaveAttribute("data-exam-content");
    expect(screen.getByText("AI explanations: Yes")).toBeInTheDocument();
    expect(screen.getByText("Extra practice: No")).toBeInTheDocument();

    const mainEl = screen.getByRole("main");
    expect(mainEl).toHaveClass("max-w-[1170px]");
    expect(within(mainEl).getByRole("link", { name: "Back to exam" })).toHaveClass("focus-visible:ring-primary-text");
    expect(within(mainEl).getByRole("link", { name: "Sign in with another account" })).toHaveClass(
      "focus-visible:ring-primary-text",
    );
  });
});
