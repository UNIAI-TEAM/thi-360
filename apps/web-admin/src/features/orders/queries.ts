import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";

import {
  createOrderPaymentRefund,
  getAdminOrderAttentionSummary,
  getAdminOrderDetail,
  getLatestReconciliationReport,
  listAdminExamsForFilter,
  listAdminOrders,
  listExamBuyers,
  listOrderDerivedExamsForFilter,
  markRefundFailed,
  queryRefund,
  recoverOrderFulfillment,
  reviewLateSuccessPayment,
} from "./api";
import { orderKeys } from "./keys";
import type {
  CreateRefundRequest,
  ExamBuyersQueryParams,
  LateSuccessReviewRequest,
  MarkRefundFailedRequest,
  OrdersListQueryParams,
  RecoverOrderRequest,
} from "./types";

export function useAdminOrdersListQuery(params: OrdersListQueryParams) {
  const key = orderKeys.list(params);
  return useQuery({
    queryKey: key,
    queryFn: ({ signal }) => listAdminOrders(params, signal),
    placeholderData: keepPreviousWhileSameScope(key),
  });
}

export function useAdminOrderAttentionQuery(enabled = true) {
  return useQuery({
    queryKey: orderKeys.attention(),
    queryFn: ({ signal }) => getAdminOrderAttentionSummary(signal),
    enabled,
    retry: false,
  });
}

export function useLatestReconciliationReportQuery(enabled = true) {
  return useQuery({
    queryKey: orderKeys.reconciliationLatest(),
    queryFn: ({ signal }) => getLatestReconciliationReport(signal),
    enabled,
    retry: false,
  });
}

export function useAdminOrderDetailQuery(code: string, enabled = true) {
  return useQuery({
    queryKey: orderKeys.detail(code),
    queryFn: ({ signal }) => getAdminOrderDetail(code, signal),
    enabled: Boolean(code) && enabled,
  });
}

export function useExamBuyersQuery(
  examId: string,
  params: ExamBuyersQueryParams,
  enabled = true,
) {
  const key = orderKeys.buyers(examId, params);
  return useQuery({
    queryKey: key,
    queryFn: ({ signal }) => listExamBuyers(examId, params, signal),
    enabled: Boolean(examId) && enabled,
    placeholderData: keepPreviousWhileSameScope(key),
  });
}

export function useAdminExamsForFilterQuery(canViewExams: boolean) {
  return useQuery({
    queryKey: orderKeys.examFilterAdmin(),
    queryFn: ({ signal }) => listAdminExamsForFilter(signal),
    enabled: canViewExams,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}

export function useOrderDerivedExamsForFilterQuery(enabled: boolean) {
  return useQuery({
    queryKey: orderKeys.examFilterOrders(),
    queryFn: ({ signal }) => listOrderDerivedExamsForFilter(signal),
    enabled,
    retry: false,
    staleTime: 60 * 1000,
  });
}

export function useRecoverOrderMutation(code: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ body, idempotencyKey }: { body: RecoverOrderRequest; idempotencyKey: string }) =>
      recoverOrderFulfillment(code, body, idempotencyKey),
    onSuccess: (updated) => {
      queryClient.setQueryData(orderKeys.detail(code), updated);
      void queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: orderKeys.attention() });
      void queryClient.invalidateQueries({ queryKey: orderKeys.buyersAll() });
    },
  });
}

export function useReviewLateSuccessMutation(code: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      paymentId,
      body,
      idempotencyKey,
    }: {
      paymentId: string;
      body: LateSuccessReviewRequest;
      idempotencyKey: string;
    }) => reviewLateSuccessPayment(code, paymentId, body, idempotencyKey),
    onSuccess: (updated) => {
      queryClient.setQueryData(orderKeys.detail(code), updated);
      void queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: orderKeys.attention() });
      void queryClient.invalidateQueries({ queryKey: orderKeys.buyersAll() });
    },
  });
}

export function useCreateRefundMutation(orderCode: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      paymentId,
      body,
      idempotencyKey,
    }: {
      paymentId: string;
      body: CreateRefundRequest;
      idempotencyKey: string;
    }) => createOrderPaymentRefund(orderCode, paymentId, body, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: orderKeys.detail(orderCode) });
      void queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: orderKeys.attention() });
      void queryClient.invalidateQueries({ queryKey: orderKeys.buyersAll() });
    },
  });
}

export function useQueryRefundMutation(orderCode: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (refundId: string) => queryRefund(refundId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: orderKeys.detail(orderCode) });
      void queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: orderKeys.attention() });
      void queryClient.invalidateQueries({ queryKey: orderKeys.buyersAll() });
    },
  });
}

export function useMarkRefundFailedMutation(orderCode: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ refundId, body }: { refundId: string; body: MarkRefundFailedRequest }) =>
      markRefundFailed(refundId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: orderKeys.detail(orderCode) });
      void queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: orderKeys.attention() });
    },
  });
}
