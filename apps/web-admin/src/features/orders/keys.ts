import type { ExamBuyersQueryParams, OrdersListQueryParams } from "./types";

export const orderKeys = {
  all: ["orders"] as const,
  lists: () => [...orderKeys.all, "list"] as const,
  list: (params: OrdersListQueryParams) => [...orderKeys.lists(), params] as const,
  attention: () => [...orderKeys.all, "attention"] as const,
  reconciliationLatest: () => [...orderKeys.all, "reconciliation-latest"] as const,
  details: () => [...orderKeys.all, "detail"] as const,
  detail: (code: string) => [...orderKeys.details(), code] as const,
  buyersAll: () => [...orderKeys.all, "buyers"] as const,
  buyers: (examId: string, params: ExamBuyersQueryParams) =>
    [...orderKeys.buyersAll(), examId, params] as const,
  examFilterAdmin: () => [...orderKeys.all, "exam-filter-admin"] as const,
  examFilterOrders: () => [...orderKeys.all, "exam-filter-orders"] as const,
};
