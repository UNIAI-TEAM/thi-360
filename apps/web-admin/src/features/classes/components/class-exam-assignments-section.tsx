"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Card,
  Checkbox,
  ConfirmDialog,
  EmptyState,
  Label,
  ListCard,
  notify,
  Skeleton,
  StatusChip,
  Table,
  type TableCards,
} from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import { ClipboardList, Plus, RotateCcw } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useCallback, useMemo, useState } from "react";
import { Can } from "@/features/session/can";
import { usePermissions } from "@/features/session/session-provider";
import {
  CLASSES_QUERY_KEYS,
  type ClassesApiDeps,
  type ClassExamAssignmentItem,
} from "../api";
import { formatClassDate, resolveClassesErrorMessage } from "../utils";
import { AssignExamsDialog } from "./assign-exams-dialog";

export interface ClassExamAssignmentsSectionProps {
  classId: string;
  classCode: string;
  isActive: boolean;
  deps: Pick<
    ClassesApiDeps,
    | "listClassExamAssignments"
    | "listAssignableClassExams"
    | "assignClassExams"
    | "updateClassExamAssignment"
    | "revokeClassExamAssignment"
  >;
}

export function ClassExamAssignmentsSection({
  classId,
  classCode,
  isActive,
  deps,
}: ClassExamAssignmentsSectionProps) {
  const t = useTranslations("classes");
  const common = useTranslations("common");
  const locale = useLocale();
  const { can } = usePermissions();
  const queryClient = useQueryClient();
  const canModify = can("CLASS.MODIFY");

  const [assignOpen, setAssignOpen] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<ClassExamAssignmentItem | null>(null);
  const [updatingId, setUpdatingId] = useState<string | null>(null);

  const assignmentsQuery = useQuery({
    queryKey: CLASSES_QUERY_KEYS.examAssignments(classId),
    queryFn: () => deps.listClassExamAssignments(classId),
  });

  const refreshAssignments = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: CLASSES_QUERY_KEYS.examAssignments(classId) });
    void queryClient.invalidateQueries({ queryKey: CLASSES_QUERY_KEYS.assignableExams(classId) });
  }, [classId, queryClient]);

  const updateMutation = useMutation({
    mutationFn: ({
      assignmentId,
      examCode,
      autoGrantNewMembers,
    }: {
      assignmentId: string;
      examCode: string;
      autoGrantNewMembers: boolean;
    }) => {
      setUpdatingId(assignmentId);
      return deps
        .updateClassExamAssignment(classId, assignmentId, { autoGrantNewMembers })
        .then((res) => ({ res, examCode }));
    },
    onSettled: () => {
      setUpdatingId(null);
    },
    onSuccess: ({ examCode }) => {
      notify.success(t("examAssignments.autoGrantUpdated", { code: examCode }));
      refreshAssignments();
    },
    onError: (error) => {
      notify.error(
        resolveClassesErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("errorCodes.INTERNAL_ERROR"),
        ),
      );
    },
  });

  const revokeMutation = useMutation({
    mutationFn: ({
      assignmentId,
      examCode,
      reason,
    }: {
      assignmentId: string;
      examCode: string;
      reason?: string;
    }) =>
      deps
        .revokeClassExamAssignment(
          classId,
          assignmentId,
          reason ? { reason } : {},
        )
        .then((res) => ({ res, examCode })),
    onSuccess: ({ res, examCode }) => {
      notify.success(
        t("revokeAssignmentDialog.success", {
          examCode,
          revoked: res.attemptsRevoked,
        }),
      );
      setRevokeTarget(null);
      refreshAssignments();
    },
    onError: (error) => {
      notify.error(
        resolveClassesErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("errorCodes.INTERNAL_ERROR"),
        ),
      );
    },
  });

  const formatResultView = useCallback(
    (view: string) => {
      if (view === "SCORE" || view === "SCORE_AND_ANSWERS" || view === "FULL_SOLUTION") {
        return t(`examAssignments.resultViews.${view}`);
      }
      return view;
    },
    [t],
  );

  const columns = useMemo<ColumnDef<ClassExamAssignmentItem>[]>(
    () => [
      {
        id: "exam",
        header: t("examAssignments.tableExam"),
        enableSorting: false,
        cell: ({ row }) => {
          const item = row.original;
          const kindLabel = pickName(item.examKind, locale) || item.examKind.code;
          return (
            <div className="flex min-w-0 flex-col gap-0.5">
              <span data-exam-content className="line-clamp-2 font-semibold text-text">
                {item.examName}
              </span>
              <span className="text-caption text-text-muted">
                {[
                  item.examCode,
                  kindLabel,
                  t("examAssignments.versionBadge", { version: item.examVersionNo }),
                ]
                  .filter(Boolean)
                  .join(" · ")}
              </span>
              {item.note && (
                <span className="line-clamp-1 text-caption text-text-secondary">{item.note}</span>
              )}
            </div>
          );
        },
      },
      {
        id: "attemptsGranted",
        header: t("examAssignments.tableAttempts"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="font-semibold text-text tabular-nums">
            {row.original.attemptsGranted}
          </span>
        ),
      },
      {
        id: "resultView",
        header: t("examAssignments.tableResultView"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="text-text-secondary">{formatResultView(row.original.resultView)}</span>
        ),
      },
      {
        id: "usage",
        header: t("examAssignments.tableUsage"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text-secondary tabular-nums">
            {t("examAssignments.usageSummary", {
              members: row.original.grantedMembersCount,
              used: row.original.totalAttemptsUsed,
              left: row.original.totalAttemptsLeft,
            })}
          </span>
        ),
      },
      {
        id: "autoGrant",
        header: t("examAssignments.tableAutoGrant"),
        enableSorting: false,
        cell: ({ row }) => {
          const item = row.original;
          const editable = canModify && isActive && item.status === "ACTIVE";
          const checkboxId = `auto-grant-${item.id}`;
          if (!editable) {
            return (
              <span className="text-caption text-text-muted">
                {item.autoGrantNewMembers
                  ? t("examAssignments.autoGrantOn")
                  : t("examAssignments.autoGrantOff")}
              </span>
            );
          }
          return (
            <div className="flex items-center gap-2">
              <Checkbox
                id={checkboxId}
                aria-label={t("examAssignments.autoGrantAria", { code: item.examCode })}
                checked={item.autoGrantNewMembers}
                disabled={updatingId === item.id}
                onCheckedChange={(next) =>
                  updateMutation.mutate({
                    assignmentId: item.id,
                    examCode: item.examCode,
                    autoGrantNewMembers: next === true,
                  })
                }
              />
              <Label htmlFor={checkboxId} className="cursor-pointer text-caption font-normal">
                {item.autoGrantNewMembers
                  ? t("examAssignments.autoGrantOn")
                  : t("examAssignments.autoGrantOff")}
              </Label>
            </div>
          );
        },
      },
      {
        id: "assignedAt",
        header: t("examAssignments.tableAssignedAt"),
        enableSorting: false,
        cell: ({ row }) => {
          const item = row.original;
          return (
            <div className="flex flex-col text-caption text-text-secondary">
              <span className="whitespace-nowrap">
                {t("examAssignments.assignedMeta", {
                  date: formatClassDate(item.assignedAt),
                  actor: item.assignedByUsername ?? "—",
                })}
              </span>
              {item.status === "REVOKED" && item.revokedAt && (
                <span className="whitespace-nowrap text-text-muted">
                  {t("examAssignments.revokedMeta", {
                    date: formatClassDate(item.revokedAt),
                  })}
                </span>
              )}
            </div>
          );
        },
      },
      {
        id: "status",
        header: t("examAssignments.tableStatus"),
        enableSorting: false,
        cell: ({ row }) => (
          <StatusChip tone={row.original.status === "ACTIVE" ? "success" : "neutral"}>
            {t(`examAssignments.statuses.${row.original.status}`)}
          </StatusChip>
        ),
      },
      ...(canModify
        ? [
            {
              id: "actions",
              header: () => <span className="block text-right">{t("examAssignments.tableActions")}</span>,
              enableSorting: false,
              cell: ({ row }: { row: { original: ClassExamAssignmentItem } }) => {
                const item = row.original;
                if (item.status !== "ACTIVE") return null;
                return (
                  <div className="flex items-center justify-end">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="text-danger hover:text-danger"
                      aria-label={t("examAssignments.revokeAria", { code: item.examCode })}
                      onClick={() => setRevokeTarget(item)}
                    >
                      <RotateCcw />
                      <span>{t("examAssignments.revokeButton")}</span>
                    </Button>
                  </div>
                );
              },
            },
          ]
        : []),
    ],
    [t, locale, canModify, isActive, updatingId, updateMutation, formatResultView],
  );

  const cards = useMemo<TableCards<ClassExamAssignmentItem>>(
    () => ({
      render: (item) => {
        const kindLabel = pickName(item.examKind, locale) || item.examKind.code;
        return (
          <ListCard
            title={<span data-exam-content>{item.examName}</span>}
            caption={[
              item.examCode,
              kindLabel,
              t("examAssignments.versionBadge", { version: item.examVersionNo }),
            ]
              .filter(Boolean)
              .join(" · ")}
            status={
              <StatusChip tone={item.status === "ACTIVE" ? "success" : "neutral"}>
                {t(`examAssignments.statuses.${item.status}`)}
              </StatusChip>
            }
            actions={
              canModify && item.status === "ACTIVE" ? (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="text-danger hover:text-danger"
                  aria-label={t("examAssignments.revokeAria", { code: item.examCode })}
                  onClick={() => setRevokeTarget(item)}
                >
                  <RotateCcw />
                  <span>{t("examAssignments.revokeButton")}</span>
                </Button>
              ) : undefined
            }
            meta={[
              {
                label: t("examAssignments.tableAttempts"),
                value: String(item.attemptsGranted),
              },
              {
                label: t("examAssignments.tableResultView"),
                value: formatResultView(item.resultView),
              },
              {
                label: t("examAssignments.tableUsage"),
                value: t("examAssignments.usageSummary", {
                  members: item.grantedMembersCount,
                  used: item.totalAttemptsUsed,
                  left: item.totalAttemptsLeft,
                }),
              },
            ]}
          />
        );
      },
      sortLabels: {
        label: common("table.sortBy"),
        ascending: common("table.ascending"),
        descending: common("table.descending"),
      },
    }),
    [t, common, locale, canModify, formatResultView],
  );

  const assignments = assignmentsQuery.data ?? [];

  return (
    <>
      <Card padding="none" className="@container overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3">
          <div className="flex items-center gap-2">
            <h2 className="text-title font-semibold text-text">{t("examAssignments.title")}</h2>
            {assignmentsQuery.data ? (
              <span className="rounded-full border border-border px-2.5 py-0.5 text-caption font-medium text-text-muted tabular-nums">
                {t("examAssignments.countBadge", { count: assignments.length })}
              </span>
            ) : (
              <Skeleton className="h-5 w-20 rounded-full" />
            )}
          </div>

          <Can permission="CLASS.MODIFY">
            <Button
              type="button"
              size="sm"
              disabled={!isActive}
              title={!isActive ? t("examAssignments.assignDisabledTooltip") : undefined}
              onClick={() => setAssignOpen(true)}
            >
              <Plus />
              <span>{t("examAssignments.assignButton")}</span>
            </Button>
          </Can>
        </div>

        <Table
          aria-label={t("examAssignments.tableAriaLabel")}
          columns={columns}
          data={assignments}
          getRowId={(row) => row.id}
          loading={assignmentsQuery.isLoading}
          cards={cards}
          error={
            assignmentsQuery.isError ? (
              <EmptyState
                icon={<ClipboardList />}
                title={t("examAssignments.errorTitle")}
                description={t("examAssignments.errorDescription")}
                action={
                  <Button variant="secondary" onClick={() => void assignmentsQuery.refetch()}>
                    {common("retry")}
                  </Button>
                }
              />
            ) : undefined
          }
          empty={
            <EmptyState
              icon={<ClipboardList />}
              title={t("examAssignments.emptyTitle")}
              description={t("examAssignments.emptyDescription")}
              action={
                canModify && isActive ? (
                  <Button onClick={() => setAssignOpen(true)}>
                    <Plus />
                    <span>{t("examAssignments.assignButton")}</span>
                  </Button>
                ) : undefined
              }
            />
          }
        />

        <p className="border-t border-border px-4 py-3 text-caption text-text-muted">
          {t("examAssignments.footnote")}
        </p>
      </Card>

      <AssignExamsDialog
        open={assignOpen}
        onOpenChange={setAssignOpen}
        classId={classId}
        classCode={classCode}
        listAssignableClassExams={deps.listAssignableClassExams}
        assignClassExams={deps.assignClassExams}
        onAssigned={refreshAssignments}
      />

      <ConfirmDialog
        open={Boolean(revokeTarget)}
        onOpenChange={(open) => {
          if (!open) setRevokeTarget(null);
        }}
        title={t("revokeAssignmentDialog.title")}
        description={
          revokeTarget
            ? t("revokeAssignmentDialog.description", {
                examCode: revokeTarget.examCode,
                examName: revokeTarget.examName,
                classCode,
              })
            : null
        }
        reasonLabel={t("revokeAssignmentDialog.reasonLabel")}
        confirmLabel={t("revokeAssignmentDialog.confirm")}
        cancelLabel={t("revokeAssignmentDialog.cancel")}
        closeLabel={t("revokeAssignmentDialog.close")}
        tone="danger"
        loading={revokeMutation.isPending}
        onConfirm={(reason) => {
          if (revokeTarget) {
            revokeMutation.mutate({
              assignmentId: revokeTarget.id,
              examCode: revokeTarget.examCode,
              ...(reason ? { reason } : {}),
            });
          }
        }}
      />
    </>
  );
}
