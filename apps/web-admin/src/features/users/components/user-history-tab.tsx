"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Card,
  EmptyState,
  ListCard,
  Pagination,
  StatusChip,
  Table,
  type TableCards,
} from "@thi360/ui";
import { History } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useCallback, useMemo, useState } from "react";
import { type AuditEvent, type UsersApiDeps, USERS_QUERY_KEYS } from "../api";
import { formatUserDateTime } from "../utils";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";

const KNOWN_AUDIT_ACTIONS: Record<string, true> = {
  AUTHZ_BULK_APPLIED: true,
  BUNDLE_CREATED: true,
  BUNDLE_DISCONTINUED: true,
  BUNDLE_PUBLISHED: true,
  BUNDLE_UPDATED: true,
  CLASS_MEMBER_ADDED: true,
  CLASS_MEMBER_REMOVED: true,
  CLASS_MEMBER_TRANSFERRED: true,
  ENTITLEMENT_REVOKED: true,
  LOGIN: true,
  LOGIN_FAILED: true,
  LOGOUT: true,
  ORDER_FULFILLED_MANUALLY: true,
  PASSWORD_CHANGED: true,
  PAYMENT_LATE_SUCCESS_ACCEPTED: true,
  PAYMENT_LATE_SUCCESS_REJECTED: true,
  REFUND_FAILED: true,
  REFUND_RECORDED_MANUALLY: true,
  REFUND_REQUESTED: true,
  REFUND_SUCCEEDED: true,
  SESSION_REVOKED: true,
  USER_ACTIVATED: true,
  USER_CREATED: true,
  USER_DEACTIVATED: true,
  USER_DELETED: true,
  USER_PASSWORD_RESET: true,
  USER_PERMISSIONS_CHANGED: true,
  USER_ROLES_CHANGED: true,
  USER_UPDATED: true,
};

const PAGE_SIZE = 20;

export interface UserHistoryTabProps {
  userId: string;
  listUserAuditEvents: UsersApiDeps["listUserAuditEvents"];
}

export function UserHistoryTab({ userId, listUserAuditEvents }: UserHistoryTabProps) {
  const t = useTranslations("users");
  const common = useTranslations("common");
  const format = useFormatter();
  const [page, setPage] = useState(1);

  const queryParams = useMemo(
    () => ({
      page: page - 1,
      size: PAGE_SIZE,
      sort: "occurredAt,desc",
    }),
    [page],
  );

  const historyQuery = useQuery({
    queryKey: USERS_QUERY_KEYS.audit(userId, queryParams),
    queryFn: () => listUserAuditEvents(userId, queryParams),
    placeholderData: keepPreviousWhileSameScope(USERS_QUERY_KEYS.audit(userId, queryParams)),
  });

  /** Tên hành động hiển thị; mã lạ (sự kiện mới chưa có bản dịch) hiện nguyên mã. */
  const actionLabel = useCallback(
    (action: string) => (Object.hasOwn(KNOWN_AUDIT_ACTIONS, action) ? t(`auditActions.${action}`) : action),
    [t],
  );

  const columns = useMemo<ColumnDef<AuditEvent>[]>(
    () => [
      {
        id: "occurredAt",
        header: t("history.occurredAt"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text-secondary tabular-nums">
            {formatUserDateTime(row.original.occurredAt)}
          </span>
        ),
      },
      {
        id: "actor",
        header: t("history.actor"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="font-medium whitespace-nowrap text-text">
            {row.original.actorUsername ?? t("history.systemActor")}
          </span>
        ),
      },
      {
        id: "action",
        header: t("history.action"),
        enableSorting: false,
        cell: ({ row }) => <span className="block min-w-40 text-text">{actionLabel(row.original.action)}</span>,
      },
      {
        id: "outcome",
        header: t("history.outcome"),
        enableSorting: false,
        cell: ({ row }) => <OutcomeChip outcome={row.original.outcome} />,
      },
      {
        id: "reason",
        header: t("history.reason"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="block min-w-48 text-text-secondary">{row.original.reason ?? "—"}</span>
        ),
      },
    ],
    [t, actionLabel],
  );

  /**
   * Khung hẹp (dưới 640px): mỗi sự kiện một thẻ, hành động là tiêu đề, thời gian ở dòng phụ, kết quả là chip
   * trạng thái, người thực hiện và lý do ở dòng thông tin thêm. Sự kiện không có trang chi tiết nên tiêu đề là chữ.
   */
  const cards = useMemo<TableCards<AuditEvent>>(
    () => ({
      render: (event) => (
        <ListCard
          title={actionLabel(event.action)}
          caption={formatUserDateTime(event.occurredAt)}
          status={<OutcomeChip outcome={event.outcome} />}
          meta={[
            { label: t("history.actor"), value: event.actorUsername ?? t("history.systemActor") },
            Boolean(event.reason) && { label: t("history.reason"), value: event.reason },
          ]}
        />
      ),
    }),
    [t, actionLabel],
  );

  const items = historyQuery.data?.items ?? [];
  const totalItems = historyQuery.data?.totalItems ?? 0;

  return (
    <Card padding="none" className="overflow-hidden">
      <Table
        aria-label={t("history.tableAriaLabel")}
        columns={columns}
        data={items}
        getRowId={(row) => row.id}
        loading={historyQuery.isLoading}
        refreshing={historyQuery.isPlaceholderData}
        cards={cards}
        error={
          historyQuery.isError ? (
            <EmptyState
              icon={<History />}
              title={t("history.errorTitle")}
              description={t("history.errorDescription")}
              action={
                <Button variant="secondary" onClick={() => void historyQuery.refetch()}>
                  {common("retry")}
                </Button>
              }
            />
          ) : undefined
        }
        empty={
          <EmptyState
            icon={<History />}
            title={t("history.emptyTitle")}
            description={t("history.emptyDescription")}
          />
        }
      />

      {totalItems > 0 && (
        <div className="border-t border-border px-4 py-3">
          <Pagination
            page={page}
            pageSize={PAGE_SIZE}
            total={totalItems}
            onPageChange={setPage}
            labels={{
              nav: t("history.paginationNav"),
              previous: t("list.paginationPrevious"),
              next: t("list.paginationNext"),
              rowsPerPage: t("list.paginationRowsPerPage"),
              page: (pageNum) => t("list.paginationPage", { page: format.number(pageNum) }),
              range: ({ from, to, total }) =>
                t("list.paginationRange", {
                  from: format.number(from),
                  to: format.number(to),
                  total: format.number(total),
                }),
            }}
          />
        </div>
      )}
    </Card>
  );
}

function OutcomeChip({ outcome }: { outcome: AuditEvent["outcome"] }) {
  const t = useTranslations("users");
  return (
    <StatusChip tone={outcome === "SUCCESS" ? "success" : "danger"}>{t(`history.outcomes.${outcome}`)}</StatusChip>
  );
}
