import type { components } from "@thi360/api-client";

export type AdminOrderListResponse = components["schemas"]["AdminOrderListResponse"];
export type AdminOrderSummary = components["schemas"]["AdminOrderSummary"];
export type AdminOrderDetailResponse = components["schemas"]["AdminOrderDetailResponse"];
export type OrderAttentionSummary = components["schemas"]["OrderAttentionSummary"];
export type LatestReconciliationReportResponse =
  components["schemas"]["LatestReconciliationReportResponse"];
export type ReconciliationReportResponse = components["schemas"]["ReconciliationReportResponse"];
export type ExamBuyersResponse = components["schemas"]["ExamBuyersResponse"];
export type ExamBuyerRow = components["schemas"]["ExamBuyerRow"];
export type ExamBuyerOrderRef = components["schemas"]["ExamBuyerOrderRef"];
export type PaymentDetailView = components["schemas"]["PaymentDetailView"];
export type RefundResponse = components["schemas"]["RefundResponse"];
export type GrantView = components["schemas"]["GrantView"];
export type HistoryView = components["schemas"]["HistoryView"];
export type AuditEventItem = components["schemas"]["AuditEventItem"];
export type OrderItemView = components["schemas"]["OrderItemView"];
export type BuyerInfo = components["schemas"]["BuyerInfo"];
export type RecoverOrderRequest = components["schemas"]["RecoverOrderRequest"];
export type LateSuccessReviewRequest = components["schemas"]["LateSuccessReviewRequest"];
export type CreateRefundRequest = components["schemas"]["CreateRefundRequest"];
export type MarkRefundFailedRequest = components["schemas"]["MarkRefundFailedRequest"];

export type OrderState = AdminOrderSummary["orderState"];
export type AdminPaymentStatus = AdminOrderSummary["paymentStatus"];
export type AdminRefundStatus = AdminOrderSummary["refundStatus"];
export type PaymentMethod = AdminOrderSummary["paymentMethod"];
export type OrderAttentionType = AdminOrderSummary["attentionTypes"][number];
export type RefundState = RefundResponse["state"];
export type RefundKind = RefundResponse["kind"];
export type LateSuccessDecision = LateSuccessReviewRequest["decision"];
export type PaymentState = PaymentDetailView["state"];
export type ExcessReason = NonNullable<PaymentDetailView["excessReason"]>;
export type LateSuccessReviewStatus = NonNullable<PaymentDetailView["lateSuccessReview"]>;

export interface OrdersListQueryParams {
  q: string;
  examId: string;
  fromDate: string;
  toDate: string;
  paymentStatus: "ALL" | AdminPaymentStatus;
  refundStatus: "ALL" | AdminRefundStatus;
  paymentMethod: "ALL" | PaymentMethod;
  attention: "" | OrderAttentionType;
  page: number;
  size: number;
  sort: string;
}

export interface ExamBuyersQueryParams {
  page: number;
  size: number;
  sort: string;
}

export interface ExamSelectOption {
  id: string;
  code: string;
  name: string;
}
