import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  type CreateOrderResponse,
  loadProductForCheckout,
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

  afterEach(() => {
    vi.unstubAllGlobals();
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

    const itemsList = screen.getByRole("list", { name: "Các bài trong bộ và quyền nhận được" });
    expect(screen.queryByRole("table", { name: "Các bài trong bộ và quyền nhận được" })).toBeNull();
    expect(within(itemsList).getByText("SP2E Toán – Đề luyện số 1")).toHaveAttribute("data-exam-content");
    expect(within(itemsList).getByText("SP2E Vật lí – Ôn tập")).toHaveAttribute("data-exam-content");
    expect(within(itemsList).getByText("Bài ôn")).toBeInTheDocument();
    expect(within(itemsList).getByText("Toán học")).toBeInTheDocument();
    expect(within(itemsList).getByText("40 câu")).toBeInTheDocument();
    expect(within(itemsList).getByText("90 phút")).toBeInTheDocument();
    expect(within(itemsList).getAllByText("10 điểm")).toHaveLength(2);
    expect(within(itemsList).getAllByText("2 lượt")).toHaveLength(2);
    expect(within(itemsList).getAllByText("Điểm và đáp án")).toHaveLength(2);
    expect(await within(itemsList).findByText("Còn 1 lượt")).toBeInTheDocument();

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

    // 3. Khi initialProduct ở trạng thái unavailable không kèm detail (R1-45: link quay lại thống nhất nhãn bộ đề)
    const unavailNoProductView = renderWithProviders(
      <CheckoutView
        code="BO-SP2E-001"
        initialProduct={{ status: "unavailable" }}
        loadCurrentSession={async () => STUDENT_SESSION}
      />,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("Bộ đề tạm thời chưa bán được.");
    expect(screen.getByRole("link", { name: "Quay lại chi tiết bộ đề" })).toHaveAttribute("href", "/exams/BO-SP2E-001");
    expect(screen.queryByRole("link", { name: "Quay lại bài thi" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Thanh toán qua VNPAY" })).toBeNull();
    unavailNoProductView.unmount();

    // 4. R1-4 / R1-6: loadProductForCheckout nhận 409 PRODUCT_UNAVAILABLE kèm stub { code, name } -> dựng fallback an toàn, không TypeError
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValueOnce(
        new Response(JSON.stringify({ code: "PRODUCT_UNAVAILABLE", product: { code: "BO-SP2E-001", name: "Bộ đề" } }), {
          status: 409,
          headers: { "content-type": "application/json" },
        }),
      ),
    );
    renderWithProviders(
      <CheckoutView
        code="BO-SP2E-001"
        loadProduct={loadProductForCheckout}
        loadCurrentSession={async () => STUDENT_SESSION}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("Bộ đề tạm thời chưa bán được.");
    expect(screen.getByRole("link", { name: "Về chi tiết bộ đề" })).toHaveAttribute("href", "/exams/BO-SP2E-001");
  });

  it("R1-25 & R1-42..44: không hiện '0 lượt' cho bài IN_PROGRESS lượt cuối và vẫn hiện banner CHECKING của bộ khi có accessRow.items", async () => {
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
              state: "CHECKING",
              attemptsLeft: 0,
              entitlementId: null,
              activeAttemptId: null,
              hasPendingFulfillment: true,
              pendingOrderCode: "DH_BUNDLE_CHK",
              items: [
                {
                  examId: "ex-math",
                  code: "SP2E-TOAN-01",
                  name: "SP2E Toán – Đề luyện số 1",
                  state: "IN_PROGRESS",
                  attemptsLeft: 0,
                  entitlementId: "ent-1",
                  activeAttemptId: "att-1",
                  hasPendingFulfillment: false,
                  pendingOrderCode: null,
                },
              ],
            },
          ],
        })}
        requestQuote={async () => makeBundleQuote()}
      />,
    );

    expect(await screen.findByText("Đang kiểm tra đơn DH_BUNDLE_CHK")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem trạng thái thanh toán" })).toHaveAttribute(
      "href",
      "/checkout/result?order=DH_BUNDLE_CHK&state=checking",
    );
    expect(screen.queryByText(/Bạn đã có 0 lượt/)).toBeNull();
    expect(screen.getByRole("button", { name: "Thanh toán qua VNPAY" })).toBeDisabled();
  });

  it("R1-38: VoucherBox nhận ProductUnavailableError khi áp dụng và khi gỡ mã khóa checkout ngay", async () => {
    const user = userEvent.setup();

    // 1. Apply trả ProductUnavailableError
    const applyQuote = vi
      .fn()
      .mockResolvedValueOnce(makeBundleQuote())
      .mockRejectedValueOnce(new ProductUnavailableError("Unavailable on apply"));
    const applyView = renderWithProviders(
      <CheckoutView
        code="BO-SP2E-001"
        initialProduct={{ status: "ok", product: BUNDLE_PRODUCT }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={applyQuote}
      />,
    );

    await user.type(await screen.findByLabelText("Nhập mã giảm giá"), "TEST10");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Bộ đề tạm thời chưa bán được.");
    expect(screen.getByLabelText("Nhập mã giảm giá")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Áp dụng" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Thanh toán qua VNPAY" })).toBeDisabled();
    applyView.unmount();

    // 2. Remove trả ProductUnavailableError
    const quoteWithVoucher = makeBundleQuote({
      finalPrice: 249000,
      discountApplied: "VOUCHER_ONLY",
      voucher: {
        id: "v-1",
        code: "GIAM50K",
        name: "50k",
        discountType: "AMOUNT",
        discountValue: 50000,
        discountVnd: 50000,
        combinable: false,
      },
    });
    const removeQuote = vi
      .fn()
      .mockResolvedValueOnce(quoteWithVoucher)
      .mockRejectedValueOnce(new ProductUnavailableError("Unavailable on remove"));
    renderWithProviders(
      <CheckoutView
        code="BO-SP2E-001"
        initialProduct={{ status: "ok", product: BUNDLE_PRODUCT }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={removeQuote}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Gỡ mã" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Bộ đề tạm thời chưa bán được.");
    expect(screen.getByRole("button", { name: "Thanh toán qua VNPAY" })).toBeDisabled();
  });

  it("R1-50: các dòng bài trong bộ ưu tiên activeQuote.attemptsGranted và bài lẻ định dạng số theo locale", async () => {
    const bundleView = renderWithProviders(
      <CheckoutView
        code="BO-SP2E-001"
        initialProduct={{ status: "ok", product: BUNDLE_PRODUCT }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeBundleQuote({ attemptsGranted: 5 })}
      />,
    );

    const itemsList = await screen.findByRole("list", { name: "Các bài trong bộ và quyền nhận được" });
    await waitFor(() => expect(within(itemsList).getAllByText("5 lượt")).toHaveLength(2));
    bundleView.unmount();

    const standaloneDecimal: ProductDetail = {
      ...BUNDLE_PRODUCT,
      code: "HSA-FULL-99",
      name: "Đề HSA lẻ",
      productType: "EXAM",
      format: "FULL",
      items: undefined,
      questionCount: { min: 1200, max: 1200 },
      durationMinutes: { min: 9.5, max: 9.5 },
    };

    const viStandalone = renderWithProviders(
      <CheckoutView
        code="HSA-FULL-99"
        initialProduct={{ status: "ok", product: standaloneDecimal }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeBundleQuote({ productCode: "HSA-FULL-99" })}
      />,
    );
    expect(await screen.findByText("1.200 câu")).toBeInTheDocument();
    expect(screen.getByText("9,5 phút")).toBeInTheDocument();
    viStandalone.unmount();

    renderWithProviders(
      <CheckoutView
        code="HSA-FULL-99"
        initialProduct={{ status: "ok", product: standaloneDecimal }}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadAccess={async () => ({ kind: "rows", rows: [] })}
        requestQuote={async () => makeBundleQuote({ productCode: "HSA-FULL-99" })}
      />,
      { locale: "en", messages: enMessages },
    );
    expect(await screen.findByText("1,200 questions")).toBeInTheDocument();
    expect(screen.getByText("9.5 minutes")).toBeInTheDocument();
  });
});
