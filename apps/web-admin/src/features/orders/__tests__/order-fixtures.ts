import {
  type ComponentType,
  createElement,
  type ReactElement,
  type ReactNode,
  useMemo,
  useSyncExternalStore,
} from "react";

import { SessionProvider } from "@/features/session/session-provider";
import en from "@/messages/en.json";
import vi from "@/messages/vi.json";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";

import type {
  AdminOrderDetailResponse,
  AdminOrderListResponse,
  AdminOrderSummary,
  ExamBuyerRow,
  ExamBuyersResponse,
  GrantView,
  LatestReconciliationReportResponse,
  OrderAttentionSummary,
  PaymentDetailView,
  RefundResponse,
} from "../types";

export const DEFAULT_ADMIN_PERMISSIONS = [
  "ORDER.VIEW",
  "ORDER.RECOVER",
  "ORDER.REFUND",
  "EXAM.VIEW",
] as const;

export function makeAttentionSummary(
  overrides: Partial<OrderAttentionSummary> = {},
): OrderAttentionSummary {
  return {
    lateSuccessPending: 0,
    paidUnfulfilled: 0,
    paymentUnknownOver24h: 0,
    duplicateUnrefunded: 0,
    needsReview: 0,
    refundUnknown: 0,
    totalAttentionOrders: 0,
    ...overrides,
  };
}

export function makeOrderSummary(overrides: Partial<AdminOrderSummary> = {}): AdminOrderSummary {
  return {
    orderId: "019a0000-0000-7000-8000-000000000001",
    code: "DH-261121-0001",
    buyerUserId: "019a0000-0000-7000-8000-000000000101",
    buyerUsername: "an.nguyen",
    buyerFullName: "Nguyen Van An",
    productType: "EXAM",
    examId: "019a0000-0000-7000-8000-000000000201",
    productCode: "IELTS-AC-01",
    productName: "IELTS Academic Practice Test 01",
    listPriceVnd: 350000,
    amountDueVnd: 299000,
    paymentMethod: "VNPAY",
    orderState: "FULFILLED",
    paymentStatus: "SUCCEEDED",
    refundStatus: "NONE",
    needsReview: false,
    attentionTypes: [],
    createdAt: "2026-11-21T02:00:00Z",
    paidAt: "2026-11-21T02:05:00Z",
    fulfilledAt: "2026-11-21T02:05:05Z",
    ...overrides,
  };
}

export function makeOrderListResponse(
  items: AdminOrderSummary[],
  overrides: Partial<AdminOrderListResponse> = {},
): AdminOrderListResponse {
  return {
    items,
    attention: makeAttentionSummary(),
    page: 0,
    size: 20,
    totalItems: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / 20)),
    ...overrides,
  };
}

export function makeRefund(overrides: Partial<RefundResponse> = {}): RefundResponse {
  return {
    id: "019a0000-0000-7000-8000-000000000401",
    orderId: "019a0000-0000-7000-8000-000000000001",
    orderCode: "DH-261121-0001",
    paymentId: "019a0000-0000-7000-8000-000000000301",
    paymentTxnRef: "TXN-261121-0001-01",
    kind: "API",
    state: "SUCCEEDED",
    amountVnd: 299000,
    reason: "Double charge on replaced order",
    verifiedSystemError: true,
    requestedBy: "admin.finance",
    requestedAt: "2026-11-21T03:00:00Z",
    finishedAt: "2026-11-21T03:00:02Z",
    createdAt: "2026-11-21T03:00:00Z",
    updatedAt: "2026-11-21T03:00:02Z",
    ...overrides,
  };
}

export function makePayment(overrides: Partial<PaymentDetailView> = {}): PaymentDetailView {
  return {
    id: "019a0000-0000-7000-8000-000000000301",
    attemptSeq: 1,
    txnRef: "TXN-261121-0001-01",
    vnpTransactionNo: "14550001",
    amountVnd: 299000,
    state: "SUCCEEDED",
    bankCode: "NCB",
    cardType: "ATM",
    payDate: "20261121090500",
    responseCode: "00",
    transactionStatus: "00",
    duplicate: false,
    refundableAmountVnd: 299000,
    refundedAmountVnd: 0,
    inProgressRefundAmountVnd: 0,
    refunds: [],
    createdAt: "2026-11-21T02:01:00Z",
    ...overrides,
  };
}

export function makeGrant(overrides: Partial<GrantView> = {}): GrantView {
  return {
    grantId: "019a0000-0000-7000-8000-000000000501",
    entitlementId: "019a0000-0000-7000-8000-000000000502",
    examId: "019a0000-0000-7000-8000-000000000201",
    attempts: 3,
    attemptsLeft: 2,
    attemptsUsed: 1,
    attemptsReserved: 0,
    attemptsRevoked: 0,
    grantedBy: "SYSTEM",
    grantedAt: "2026-11-21T02:05:05Z",
    ...overrides,
  };
}

export function makeOrderDetail(
  overrides: Partial<AdminOrderDetailResponse> = {},
): AdminOrderDetailResponse {
  return {
    orderId: "019a0000-0000-7000-8000-000000000001",
    code: "DH-261121-0001",
    buyer: {
      userId: "019a0000-0000-7000-8000-000000000101",
      username: "an.nguyen",
      fullName: "Nguyen Van An",
    },
    productType: "EXAM",
    examId: "019a0000-0000-7000-8000-000000000201",
    examVersionNo: 1,
    productCode: "IELTS-AC-01",
    productName: "IELTS Academic Practice Test 01",
    optionCodes: [],
    listPriceVnd: 350000,
    promotionDiscountVnd: 51000,
    voucherDiscountVnd: 0,
    discountApplied: "PROMOTION_ONLY",
    amountDueVnd: 299000,
    attemptsGranted: 3,
    benefits: {},
    resultView: "FULL",
    paymentMethod: "VNPAY",
    state: "FULFILLED",
    paymentStatus: "SUCCEEDED",
    refundStatus: "NONE",
    needsReview: false,
    attentionTypes: [],
    expiresAt: "2026-11-21T02:15:00Z",
    paidAt: "2026-11-21T02:05:00Z",
    fulfilledAt: "2026-11-21T02:05:05Z",
    fulfillmentTries: 1,
    createdAt: "2026-11-21T02:00:00Z",
    updatedAt: "2026-11-21T02:05:05Z",
    items: [],
    payments: [makePayment()],
    grants: [makeGrant()],
    orderHistory: [
      {
        id: "019a0000-0000-7000-8000-000000000601",
        fromState: "PENDING_PAYMENT",
        toState: "FULFILLED",
        reason: "FULFILLED:TXN-261121-0001-01",
        actor: "SYSTEM",
        at: "2026-11-21T02:05:05Z",
      },
    ],
    auditEvents: [],
    ...overrides,
  };
}

export function makeReconciliationLatest(
  overrides: Partial<LatestReconciliationReportResponse> = {},
): LatestReconciliationReportResponse {
  return {
    available: true,
    report: {
      id: "019a0000-0000-7000-8000-000000000701",
      reportDate: "2026-11-20",
      generatedAt: "2026-11-20T17:05:00Z",
      createdAt: "2026-11-20T17:05:00Z",
      paidUnfulfilledCount: 1,
      unknownPaymentCount: 2,
      unrefundedExcessCount: 3,
      pendingLateSuccessCount: 4,
      details: {},
    },
    ...overrides,
  };
}

export function makeBuyerRow(overrides: Partial<ExamBuyerRow> = {}): ExamBuyerRow {
  return {
    userId: "019a0000-0000-7000-8000-000000000101",
    username: "an.nguyen",
    fullName: "Nguyen Van An",
    purchaseCount: 2,
    attemptsTotal: 6,
    attemptsUsed: 1,
    attemptsRemaining: 5,
    attemptsReserved: 0,
    attemptsRevoked: 0,
    lastPaidAt: "2026-11-21T02:05:00Z",
    orders: [
      {
        orderId: "019a0000-0000-7000-8000-000000000002",
        orderCode: "DH-261121-0002",
        amountDueVnd: 299000,
        state: "FULFILLED",
        createdAt: "2026-11-21T02:00:00Z",
        paidAt: "2026-11-21T02:05:00Z",
      },
      {
        orderId: "019a0000-0000-7000-8000-000000000001",
        orderCode: "DH-261120-0001",
        amountDueVnd: 299000,
        state: "FULFILLED",
        createdAt: "2026-11-20T02:00:00Z",
        paidAt: "2026-11-20T02:05:00Z",
      },
    ],
    ...overrides,
  };
}

export function makeBuyersResponse(
  items: ExamBuyerRow[],
  overrides: Partial<ExamBuyersResponse> = {},
): ExamBuyersResponse {
  return {
    items,
    page: 0,
    size: 20,
    totalBuyers: items.length,
    totalItems: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / 20)),
    ...overrides,
  };
}

let mockNavPathname = "/orders";
let mockNavSearch = "";
const mockNavListeners = new Set<() => void>();

export function resetMockNavigation(pathname = "/orders", search = "") {
  mockNavPathname = pathname;
  mockNavSearch = search.startsWith("?") ? search.slice(1) : search;
  for (const listener of mockNavListeners) listener();
}

function navigateMock(href: string) {
  const qIndex = href.indexOf("?");
  mockNavPathname = qIndex === -1 ? href : href.slice(0, qIndex);
  mockNavSearch = qIndex === -1 ? "" : href.slice(qIndex + 1);
  for (const listener of mockNavListeners) listener();
}

export function createNextNavigationMock() {
  return {
    usePathname: () =>
      useSyncExternalStore(
        (listener) => {
          mockNavListeners.add(listener);
          return () => mockNavListeners.delete(listener);
        },
        () => mockNavPathname,
        () => mockNavPathname,
      ),
    useSearchParams: () => {
      const raw = useSyncExternalStore(
        (listener) => {
          mockNavListeners.add(listener);
          return () => mockNavListeners.delete(listener);
        },
        () => mockNavSearch,
        () => mockNavSearch,
      );
      return useMemo(() => new URLSearchParams(raw), [raw]);
    },
    useRouter: () => ({
      push: navigateMock,
      replace: navigateMock,
      prefetch: () => Promise.resolve(),
      back: () => {},
      forward: () => {},
      refresh: () => {},
    }),
  };
}

export function renderOrdersUi(
  ui: ReactElement,
  options?: { permissions?: string[]; locale?: "vi" | "en" },
) {
  const perms = options?.permissions ?? [...DEFAULT_ADMIN_PERMISSIONS];
  const locale = options?.locale ?? "vi";
  const messages = locale === "en" ? en : vi;
  const SessionWrapper = SessionProvider as ComponentType<{
    loadSession?: () => Promise<typeof TEST_ADMIN_SESSION>;
    children?: ReactNode;
  }>;
  return renderWithProviders(
    createElement(
      SessionWrapper,
      {
        loadSession: async () => ({
          ...TEST_ADMIN_SESSION,
          permissions: perms,
        }),
      },
      ui,
    ),
    { locale, messages },
  );
}
