import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  type CreateOrderResponse,
  OrderConflictError,
  type PriceQuote,
  ProductUnavailableError,
} from "@/features/checkout/api";
import type * as SessionApi from "@/features/session/api";
import type { ProductDetail } from "@/features/storefront/types";
import { ApiStatusError } from "@/lib/api/client";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { STUDENT_SESSION } from "@/test/storefront-fixtures";
import { CheckoutView } from "./checkout-view";

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  refreshSession: vi.fn(),
  clearSessionBestEffort: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => "/checkout/BO-SP2E-001",
  useSearchParams: () => new URLSearchParams("v=2&p=299000"),
}));

vi.mock("@/features/session/api", async (importOriginal) => {
  const actual = await importOriginal<typeof SessionApi>();
  return {
    ...actual,
    refreshSession: mocks.refreshSession,
    clearSessionBestEffort: mocks.clearSessionBestEffort,
  };
});

const BUNDLE_PRODUCT: ProductDetail = {
  code: "BO-SP2E-001",
  name: "Bộ đề SP2E khối A00",
  productType: "BUNDLE",
  format: "BUNDLE",
  examKind: { code: "SP2E", nameVi: "ĐHSP Hà Nội 2", nameEn: "HNUE2" },
  buildMode: "STANDARD",
  attemptsGranted: 2,
  listPrice: 349000,
  price: 299000,
  versionNo: 2,
  questionCount: { min: 96, max: 96 },
  durationMinutes: { min: 210, max: 210 },
  benefits: { aiExplanation: true, strengthWeakness: true, practice: false, note: "Ưu tiên AI cho cả 3 môn" },
  resultView: "SCORE_AND_ANSWERS",
  choices: [],
  omittedEssayGroups: [],
  parts: [],
  scoring: {},
  items: [
    {
      examId: "ex-math",
      code: "SP2E-TOAN-01",
      name: "SP2E Toán – Đề luyện số 1",
      scope: "SUBJECT",
      subjectName: { nameVi: "Toán học", nameEn: "Mathematics" },
      buildMode: "STANDARD",
      questionCount: { min: 40, max: 40 },
      durationMinutes: { min: 90, max: 90 },
      maxPoints: { min: 10, max: 10 },
      attemptsGranted: 2,
      resultView: "SCORE_AND_ANSWERS",
      benefits: { aiExplanation: true, strengthWeakness: true, practice: false },
      versionNo: 1,
    },
    {
      examId: "ex-phys",
      code: "SP2E-LY-01",
      name: "SP2E Vật lí – Ôn tập",
      scope: "SUBJECT",
      subjectName: { nameVi: "Vật lí", nameEn: "" },
      buildMode: "REVIEW",
      questionCount: { min: 28, max: 28 },
      durationMinutes: { min: 60, max: 60 },
      maxPoints: { min: 10, max: 10 },
      attemptsGranted: 2,
      resultView: "SCORE_AND_ANSWERS",
      benefits: { aiExplanation: true, strengthWeakness: true, practice: false },
      versionNo: 1,
    },
  ],
};

function makeBundleQuote(overrides: Partial<PriceQuote> = {}): PriceQuote {
  return {
    productCode: "BO-SP2E-001",
    productName: "Bộ đề SP2E khối A00",
    listPrice: 349000,
    finalPrice: 299000,
    attemptsGranted: 2,
    discountApplied: "PROMOTION_ONLY",
    versionNo: 2,
    changed: false,
    voucherNotBetter: false,
    promotion: {
      id: "promo-1",
      name: "Ưu đãi bộ đề",
      discountType: "AMOUNT",
      discountValue: 50000,
      discountVnd: 50000,
      endsAt: "2026-12-01T00:00:00Z",
    },
    ...overrides,
  };
}

describe("Bundle CheckoutView (AC-13)", () => {
  beforeEach(() => {
    mocks.replace.mockReset();
    mocks.refreshSession.mockReset();
    mocks.clearSessionBestEffort.mockReset();
  });

  it("AC-13: hiện danh sách bài trong bộ, thông báo lượt đã có từ accessRow.items, tính giá và tạo đơn bằng mã bộ", async () => {
    const user = userEvent.setup();
    const requestQuote = vi.fn().mockResolvedValue(makeBundleQuote());
    const submitOrder = vi.fn().mockResolvedValue({
      next: "REDIRECT",
      order: {
        id: "ord-b1",
        code: "DH261121BND",
        userId: STUDENT_SESSION.id,
        buyerUsername: STUDENT_SESSION.username,
        buyerFullName: STUDENT_SESSION.fullName,
        productType: "BUNDLE",
        bundleId: "bundle-1",
        bundleVersionNo: 2,
        optionCodes: [],
        productCode: "BO-SP2E-001",
        productName: "Bộ đề SP2E khối A00",
        listPriceVnd: 349000,
        promotionDiscountVnd: 50000,
        voucherDiscountVnd: 0,
        discountApplied: "PROMOTION_ONLY",
        amountDueVnd: 299000,
        attemptsGranted: 2,
        benefits: {},
        resultView: "SCORE_AND_ANSWERS",
        paymentMethod: "VNPAY",
        state: "PENDING_PAYMENT",
        createdAt: "2026-11-21T03:00:00Z",
        updatedAt: "2026-11-21T03:00:00Z",
        items: [],
        payments: [],
        history: [],
      },
      paymentUrl: "http://localhost:8080/mock-vnpay?vnp_TxnRef=DH261121BND",
    } satisfies CreateOrderResponse);
    const navigate = vi.fn();

    renderWithProviders(
      <CheckoutView
        code="BO-SP2E-001"
        initialProduct={{ status: "ok", product: BUNDLE_PRODUCT }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({
          kind: "rows",
          rows: [
            {
              code: "BO-SP2E-001",
              state: "NONE",
              attemptsLeft: 0,
              entitlementId: null,
              activeAttemptId: null,
              hasPendingFulfillment: false,
              pendingOrderCode: null,
              items: [
                {
                  examId: "ex-math",
                  code: "SP2E-TOAN-01",
                  name: "SP2E Toán – Đề luyện số 1",
                  state: "CAN_START",
                  attemptsLeft: 1,
                  entitlementId: "ent-1",
                  activeAttemptId: null,
                  hasPendingFulfillment: false,
                  pendingOrderCode: null,
                },
              ],
            },
          ],
        })}
        requestQuote={requestQuote}
        submitOrder={submitOrder}
        navigate={navigate}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Xác nhận mua" })).toBeInTheDocument();
    expect(screen.getByText("Bộ đề SP2E khối A00")).toHaveAttribute("data-exam-content");
    expect(screen.getByRole("link", { name: "Quay lại chi tiết bộ đề" })).toHaveAttribute("href", "/exams/BO-SP2E-001");

    const itemsTable = screen.getByRole("table", { name: "Các bài trong bộ và quyền nhận được" });
    expect(within(itemsTable).getByText("SP2E Toán – Đề luyện số 1")).toHaveAttribute("data-exam-content");
    expect(within(itemsTable).getByText("SP2E Vật lí – Ôn tập")).toHaveAttribute("data-exam-content");
    expect(within(itemsTable).getByText("Bài ôn")).toBeInTheDocument();
    expect(within(itemsTable).getByText("Toán học")).toBeInTheDocument();

    // Thông báo đã có lượt bài trong bộ
    expect(await screen.findByText(/Bạn đã có 1 lượt bài/)).toHaveTextContent(
      "Bạn đã có 1 lượt bài SP2E Toán – Đề luyện số 1; mua bộ sẽ cộng thêm lượt",
    );

    // accessRow.state === "NONE" vẫn cho phép bấm thanh toán qua mã bộ
    const payButton = await screen.findByRole("button", { name: "Thanh toán qua VNPAY" });
    expect(payButton).toBeEnabled();
    await user.click(payButton);

    await waitFor(() =>
      expect(submitOrder).toHaveBeenCalledWith(
        {
          code: "BO-SP2E-001",
          expectedAccountId: STUDENT_SESSION.id,
          expectedAmountDue: 299000,
          expectedVersionNo: 2,
        },
        expect.any(String),
      ),
    );
    expect(navigate).toHaveBeenCalledWith("http://localhost:8080/mock-vnpay?vnp_TxnRef=DH261121BND");
  });

  it("xử lý lỗi voucher không áp cho bộ đề với thông báo riêng của bộ (README line 102)", async () => {
    const user = userEvent.setup();
    const requestQuote = vi
      .fn()
      .mockResolvedValueOnce(makeBundleQuote())
      .mockRejectedValueOnce(new ApiStatusError(422, "Not applicable", { code: "VOUCHER_NOT_APPLICABLE" }));

    const viView = renderWithProviders(
      <CheckoutView
        code="BO-SP2E-001"
        initialProduct={{ status: "ok", product: BUNDLE_PRODUCT }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={requestQuote}
      />,
    );

    await user.type(await screen.findByLabelText("Nhập mã giảm giá"), "ONLYEXAM");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Mã này chỉ áp cho một số bài thi, không áp cho bộ đề.",
    );
    viView.unmount();

    const enQuote = vi
      .fn()
      .mockResolvedValueOnce(makeBundleQuote())
      .mockRejectedValueOnce(new ApiStatusError(422, "Not applicable", { code: "VOUCHER_NOT_APPLICABLE" }));

    renderWithProviders(
      <CheckoutView
        code="BO-SP2E-001"
        initialProduct={{ status: "ok", product: BUNDLE_PRODUCT }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={enQuote}
      />,
      { locale: "en", messages: enMessages },
    );

    await user.type(await screen.findByLabelText("Enter voucher code"), "ONLYEXAM");
    await user.click(screen.getByRole("button", { name: "Apply" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This voucher code only applies to selected exams, not to exam bundles.",
    );
  });

  it("xử lý 409 PRODUCT_UNAVAILABLE từ initialProduct, fetchPriceQuote và createOrder: hiện cảnh báo và khóa nút thanh toán", async () => {
    const user = userEvent.setup();

    // 1. Khi createOrder trả 409 PRODUCT_UNAVAILABLE
    const submitUnavailable = vi
      .fn()
      .mockRejectedValueOnce(new OrderConflictError("PRODUCT_UNAVAILABLE", "Bundle unavailable"));
    const onSubmitView = renderWithProviders(
      <CheckoutView
        code="BO-SP2E-001"
        initialProduct={{ status: "ok", product: BUNDLE_PRODUCT }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeBundleQuote()}
        submitOrder={submitUnavailable}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Thanh toán qua VNPAY" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Bộ đề tạm thời chưa bán được.");
    expect(screen.getByRole("button", { name: "Thanh toán qua VNPAY" })).toBeDisabled();
    onSubmitView.unmount();

    // 2. Khi fetchPriceQuote trả 409 PRODUCT_UNAVAILABLE
    const quoteUnavailableView = renderWithProviders(
      <CheckoutView
        code="BO-SP2E-001"
        initialProduct={{ status: "ok", product: BUNDLE_PRODUCT }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={vi.fn().mockRejectedValueOnce(new ProductUnavailableError("Bundle unavailable"))}
      />,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("Bộ đề tạm thời chưa bán được.");
    expect(screen.getByRole("button", { name: "Thanh toán qua VNPAY" })).toBeDisabled();
    quoteUnavailableView.unmount();

    // 3. Khi initialProduct ở trạng thái unavailable không kèm detail
    renderWithProviders(
      <CheckoutView
        code="BO-SP2E-001"
        initialProduct={{ status: "unavailable" }}
        loadCurrentSession={async () => STUDENT_SESSION}
      />,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("Bộ đề tạm thời chưa bán được.");
    expect(screen.queryByRole("button", { name: "Thanh toán qua VNPAY" })).toBeNull();
  });
});
