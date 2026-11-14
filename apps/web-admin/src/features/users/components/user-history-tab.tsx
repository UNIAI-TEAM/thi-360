"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Card,
  EmptyState,
  Pagination,
  StatusChip,
  Table,
} from "@thi360/ui";
import { History } from "lucide-react";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { type AuditEvent, type UsersApiDeps, USERS_QUERY_KEYS } from "../api";
import { formatUserDateTime } from "../utils";

const KNOWN_AUDIT_ACTIONS: Record<string, true> = {
  AUTHZ_BULK_APPLIED: true,
  CLASS_MEMBER_ADDED: true,
  CLASS_MEMBER_REMOVED: true,
  CLASS_MEMBER_TRANSFERRED: true,
  LOGIN: true,
  LOGIN_FAILED: true,
  LOGOUT: true,
  PASSWORD_CHANGED: true,
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
  });

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
          <span className="font-medium text-text">
            {row.original.actorUsername ?? t("history.systemActor")}
          </span>
        ),
      },
      {
        id: "action",
        header: t("history.action"),
        enableSorting: false,
        cell: ({ row }) => {
          const action = row.original.action;
          return (
            <span className="text-text">
              {Object.hasOwn(KNOWN_AUDIT_ACTIONS, action)
                ? t(`auditActions.${action}`)
                : action}
            </span>
          );
        },
      },
      {
        id: "outcome",
        header: t("history.outcome"),
        enableSorting: false,
        cell: ({ row }) => (
          <StatusChip tone={row.original.outcome === "SUCCESS" ? "success" : "danger"}>
            {t(`history.outcomes.${row.original.outcome}`)}
          </StatusChip>
        ),
      },
      {
        id: "reason",
        header: t("history.reason"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="text-text-secondary">{row.original.reason ?? "—"}</span>
        ),
      },
    ],
    [t],
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
              page: (pageNum) => t("list.paginationPage", { page: pageNum }),
              range: ({ from, to, total }) => t("list.paginationRange", { from, to, total }),
            }}
          />
        </div>
      )}
    </Card>
  );
}
