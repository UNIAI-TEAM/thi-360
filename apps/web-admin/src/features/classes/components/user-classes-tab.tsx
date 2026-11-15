"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Card,
  EmptyState,
  SectionTitle,
  Skeleton,
  StatusChip,
  Table,
} from "@thi360/ui";
import { GraduationCap, History } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useMemo } from "react";
import { usePermissions } from "@/features/session/session-provider";
import {
  CLASSES_QUERY_KEYS,
  getUserClasses as defaultGetUserClasses,
  type UserClassHistory,
  type UserCurrentClass,
} from "../api";
import { formatClassDate, formatClassDateTime } from "../utils";

export interface UserClassesTabProps {
  userId: string;
  getUserClasses?: typeof defaultGetUserClasses;
}

export function UserClassesTab({
  userId,
  getUserClasses = defaultGetUserClasses,
}: UserClassesTabProps) {
  const t = useTranslations("classes");
  const common = useTranslations("common");
  const { can } = usePermissions();
  const canViewClasses = can("CLASS.VIEW");

  const query = useQuery({
    queryKey: CLASSES_QUERY_KEYS.userClasses(userId),
    queryFn: () => getUserClasses(userId),
    enabled: canViewClasses,
  });

  const currentColumns = useMemo<ColumnDef<UserCurrentClass>[]>(
    () => [
      {
        id: "classCode",
        header: t("userTab.currentTableCode"),
        enableSorting: false,
        cell: ({ row }) => (
          <Link
            href={`/classes/${row.original.classId}`}
            className="whitespace-nowrap font-semibold text-primary-text hover:underline"
          >
            {row.original.classCode}
          </Link>
        ),
      },
      {
        id: "className",
        header: t("userTab.currentTableName"),
        enableSorting: false,
        cell: ({ row }) => <span className="font-medium text-text">{row.original.className}</span>,
      },
      {
        id: "classStatus",
        header: t("userTab.currentTableStatus"),
        enableSorting: false,
        cell: ({ row }) => (
          <StatusChip tone={row.original.classStatus === "ACTIVE" ? "success" : "neutral"}>
            {t(`statuses.${row.original.classStatus}`)}
          </StatusChip>
        ),
      },
      {
        id: "joinedAt",
        header: t("userTab.currentTableJoinedAt"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text-secondary">
            {formatClassDate(row.original.joinedAt)}
          </span>
        ),
      },
      {
        id: "addedBy",
        header: t("userTab.currentTableAddedBy"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text-secondary">
            {row.original.addedByUsername ?? t("userTab.systemActor")}
          </span>
        ),
      },
    ],
    [t],
  );

  const historyColumns = useMemo<ColumnDef<UserClassHistory>[]>(
    () => [
      {
        id: "occurredAt",
        header: t("userTab.historyTableTime"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text-secondary">
            {formatClassDateTime(row.original.occurredAt)}
          </span>
        ),
      },
      {
        id: "action",
        header: t("userTab.historyTableAction"),
        enableSorting: false,
        cell: ({ row }) => {
          const action = row.original.action;
          const tone =
            action === "JOIN" || action === "TRANSFER_IN"
              ? "success"
              : action === "TRANSFER_OUT"
                ? "info"
                : "neutral";
          return <StatusChip tone={tone}>{t(`userTab.historyActions.${action}`)}</StatusChip>;
        },
      },
      {
        id: "class",
        header: t("userTab.historyTableClass"),
        enableSorting: false,
        cell: ({ row }) => (
          <Link
            href={`/classes/${row.original.classId}`}
            className="font-medium text-primary-text hover:underline"
          >
            {row.original.classCode} — {row.original.className}
          </Link>
        ),
      },
      {
        id: "relatedClass",
        header: t("userTab.historyTableRelatedClass"),
        enableSorting: false,
        cell: ({ row }) =>
          row.original.relatedClassId && row.original.relatedClassCode ? (
            <Link
              href={`/classes/${row.original.relatedClassId}`}
              className="text-primary-text hover:underline"
            >
              {row.original.relatedClassCode}
              {row.original.relatedClassName ? ` — ${row.original.relatedClassName}` : ""}
            </Link>
          ) : (
            <span className="text-text-muted">—</span>
          ),
      },
      {
        id: "actor",
        header: t("userTab.historyTableActor"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text-secondary">
            {row.original.actorUsername ?? t("userTab.systemActor")}
          </span>
        ),
      },
      {
        id: "reason",
        header: t("userTab.historyTableReason"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="text-text-secondary">{row.original.reason ?? "—"}</span>
        ),
      },
    ],
    [t],
  );

  if (!canViewClasses) {
    return (
      <Card>
        <EmptyState
          icon={<GraduationCap />}
          title={t("userTab.noPermissionTitle")}
          description={t("userTab.noPermissionDescription")}
        />
      </Card>
    );
  }

  if (query.isLoading) {
    return (
      <div role="status" aria-label={common("loading")} className="flex flex-col gap-4">
        <Skeleton className="h-44 w-full" />
        <Skeleton className="h-56 w-full" />
      </div>
    );
  }

  if (query.isError) {
    return (
      <Card>
        <EmptyState
          icon={<GraduationCap />}
          title={t("userTab.errorTitle")}
          description={t("userTab.errorDescription")}
          action={
            <Button variant="secondary" onClick={() => void query.refetch()}>
              {common("retry")}
            </Button>
          }
        />
      </Card>
    );
  }

  const currentClasses = query.data?.currentClasses ?? [];
  const history = query.data?.history ?? [];

  return (
    <div className="flex flex-col gap-5" data-slot="user-classes-tab" data-user-id={userId}>
      <Card padding="none" className="overflow-hidden">
        <div className="border-b border-border px-4 py-3">
          <SectionTitle title={t("userTab.currentTitle")} count={String(currentClasses.length)} />
        </div>

        <Table
          aria-label={t("userTab.currentTableAriaLabel")}
          columns={currentColumns}
          data={currentClasses}
          getRowId={(row) => row.classId}
          empty={
            <EmptyState
              icon={<GraduationCap />}
              title={t("userTab.currentEmptyTitle")}
              description={t("userTab.currentEmptyDescription")}
            />
          }
        />
      </Card>

      <Card padding="none" className="overflow-hidden">
        <div className="border-b border-border px-4 py-3">
          <SectionTitle title={t("userTab.historyTitle")} count={String(history.length)} />
        </div>

        <Table
          aria-label={t("userTab.historyTableAriaLabel")}
          columns={historyColumns}
          data={history}
          getRowId={(row) => row.id}
          empty={
            <EmptyState
              icon={<History />}
              title={t("userTab.historyEmptyTitle")}
              description={t("userTab.historyEmptyDescription")}
            />
          }
        />
      </Card>
    </div>
  );
}
