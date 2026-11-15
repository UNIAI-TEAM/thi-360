"use client";

import type { ColumnDef } from "@tanstack/react-table";
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  IconButton,
  ListCard,
  notify,
  Skeleton,
  StatusChip,
  Table,
  type TableCards,
} from "@thi360/ui";
import {
  ArrowLeftRight,
  ArrowRight,
  ArrowUp,
  CircleOff,
  Eye,
  FileText,
  History,
  Info,
  Pencil,
  Plus,
  Replace,
  TriangleAlert,
} from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useMemo, useState } from "react";
import { useExam } from "../queries";
import { useDiscontinueExam, useExamVersions } from "../review-queries";
import type { ExamChange, ExamChangeKind, ExamVersionList, ExamVersionRow } from "../review-types";
import { EXAM_CHECK_TONE } from "../status";
import type { ExamDetail } from "../types";
import { ExamVersionDetailDialog } from "./exam-versions-detail-dialog";
import { IconLink, pathLabelCodes, reviewHref, structureHref } from "./exam-review-shared";
import { PublishExamDialog } from "./publish-exam-dialog";

const CHANGE_ICON: Record<ExamChangeKind, typeof Plus> = {
  OPTION_ADDED: Plus,
  OPTION_REMOVED: CircleOff,
  BLOCK_ADDED: Plus,
  BLOCK_REMOVED: CircleOff,
  BLOCK_CHANGED: Pencil,
  MATRIX_CHANGED: Pencil,
  ORDER_RULE_CHANGED: ArrowLeftRight,
  QUESTIONS_REPLACED: Replace,
};

/**
 * Phiên bản và công bố (bản vẽ `BaiThiPhienBan`): bản làm việc, thay đổi so với bản đang phục vụ, lịch sử công bố,
 * công bố (`CongBoBaiThi`), ngừng bán, xem cấu hình một phiên bản (chỉ đọc).
 */
export function ExamVersionsView({ examId }: { examId: string }) {
  const t = useTranslations("exams.versions");
  const examQuery = useExam(examId);
  const versionsQuery = useExamVersions(examId);
  const discontinue = useDiscontinueExam(examId);
  const [publishOpen, setPublishOpen] = useState(false);
  const [viewVersion, setViewVersion] = useState<number | null>(null);
  const [discontinueOpen, setDiscontinueOpen] = useState(false);
  const [discontinueError, setDiscontinueError] = useState<string | null>(null);

  // Khung soạn bài thi (`ExamEditorLayout`) đã lo trạng thái tải và lỗi của bài thi.
  if (!examQuery.data) return <Skeleton className="h-96" />;
  const exam = examQuery.data;

  if (versionsQuery.isPending) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-32" />
        <Skeleton className="h-48" />
        <Skeleton className="h-64" />
      </div>
    );
  }
  if (versionsQuery.isError) {
    return (
      <Card>
        <EmptyState
          icon={<TriangleAlert />}
          title={t("errorTitle")}
          description={t("errorDescription")}
          action={
            <Button variant="secondary" onClick={() => void versionsQuery.refetch()}>
              {t("retry")}
            </Button>
          }
        />
      </Card>
    );
  }
  const data = versionsQuery.data;

  return (
    <div className="flex flex-col gap-6">
      <WorkingCopySection exam={exam} data={data} onPublish={() => setPublishOpen(true)} />
      {data.changes.length > 0 && <ChangesSection exam={exam} data={data} />}
      <HistorySection
        exam={exam}
        data={data}
        onView={setViewVersion}
        onDiscontinue={() => {
          setDiscontinueError(null);
          setDiscontinueOpen(true);
        }}
      />
      <p className="flex items-start gap-2.5 rounded-lg border border-border bg-subtle px-3.5 py-2.5 text-body text-text-secondary">
        <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
        {t("snapshotNote")}
      </p>

      <PublishExamDialog
        open={publishOpen}
        onOpenChange={setPublishOpen}
        exam={exam}
        versions={data}
        onPublished={(response) =>
          notify.success(t("published", { version: response.version, count: response.publishedPathIds.length }))
        }
      />
      <ExamVersionDetailDialog
        examId={exam.id}
        version={viewVersion}
        onOpenChange={(open) => !open && setViewVersion(null)}
      />
      <ConfirmDialog
        open={discontinueOpen}
        onOpenChange={(open) => {
          setDiscontinueOpen(open);
          if (!open) setDiscontinueError(null);
        }}
        title={t("discontinue.title", { code: exam.code })}
        description={t("discontinue.description")}
        confirmLabel={t("discontinue.confirm")}
        cancelLabel={t("discontinue.cancel")}
        loading={discontinue.isPending}
        error={discontinueError}
        onConfirm={() =>
          discontinue.mutate(undefined, {
            onSuccess: () => {
              setDiscontinueOpen(false);
              notify.success(t("discontinue.done", { code: exam.code }));
            },
            onError: () => setDiscontinueError(t("discontinue.error")),
          })
        }
      />
    </div>
  );
}

function WorkingCopySection({
  exam,
  data,
  onPublish,
}: {
  exam: ExamDetail;
  data: ExamVersionList;
  onPublish: () => void;
}) {
  const t = useTranslations("exams.versions");
  const checkStatus = useTranslations("exams.checkStatus");
  const format = useFormatter();
  const working = data.workingCopy;

  return (
    <section aria-labelledby="versions-working-title">
      <Card padding="md" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <h2 id="versions-working-title" className="text-h3 font-bold text-text">
            {t("working.title")}
          </h2>
          {working && (
            <Button onClick={onPublish}>
              <ArrowUp />
              {t("working.publish")}
            </Button>
          )}
        </div>
        {working ? (
          <>
            <dl className="flex flex-wrap gap-x-6 gap-y-2 text-body">
              <div className="flex items-baseline gap-1.5">
                <dt className="text-text-muted">{t("working.editedBy")}</dt>
                <dd className="m-0 font-semibold text-text">{working.editedBy.name}</dd>
              </div>
              <div className="flex items-baseline gap-1.5">
                <dt className="text-text-muted">{t("working.savedAt")}</dt>
                <dd className="m-0 font-semibold text-text">
                  {format.dateTime(new Date(working.savedAt), "dateTime")}
                </dd>
              </div>
              <div className="flex items-center gap-1.5">
                <dt className="text-text-muted">{t("working.lastCheck")}</dt>
                <dd className="m-0">
                  <Link
                    href={reviewHref(exam.id)}
                    className="inline-flex items-center gap-1.5 rounded-sm hover:underline"
                  >
                    <StatusChip tone={EXAM_CHECK_TONE[working.check.status]}>
                      {working.check.status === "INVALID"
                        ? checkStatus("issues", { count: working.check.issueCount })
                        : checkStatus(working.check.status)}
                    </StatusChip>
                    {working.check.checkedAt && (
                      <span className="text-text-secondary">
                        {format.dateTime(new Date(working.check.checkedAt), "dateTime")}
                      </span>
                    )}
                  </Link>
                </dd>
              </div>
            </dl>
            {working.note && <p className="text-body text-text-secondary">{working.note}</p>}
            {data.items.length > 0 && working.unpublishedPaths.length > 0 && data.changes.length === 0 && (
              <p className="text-body text-text-secondary">
                {t("working.pendingPaths", { paths: pathLabelCodes(working.unpublishedPaths, t("singlePath")) })}
              </p>
            )}
            <p className="flex items-start gap-2 text-body text-text-muted">
              <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
              {working.baseVersion !== null
                ? t("working.servingNote", { version: working.baseVersion })
                : t("working.firstPublishNote")}
            </p>
          </>
        ) : (
          <p className="text-body text-text-secondary">
            {t("working.none")}{" "}
            <Link href={structureHref(exam.id)} className="rounded-sm font-medium text-primary-text hover:underline">
              {t("working.toStructure")}
            </Link>
          </p>
        )}
      </Card>
    </section>
  );
}

function ChangesSection({ exam, data }: { exam: ExamDetail; data: ExamVersionList }) {
  const t = useTranslations("exams.versions");
  const base = data.workingCopy?.baseVersion ?? null;
  return (
    <section aria-labelledby="versions-changes-title" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <h2 id="versions-changes-title" className="text-h3 font-bold text-text">
          {base !== null ? t("changes.title", { version: base }) : t("changes.titleFirst")}
        </h2>
        <Badge>{t("changes.count", { count: data.changes.length })}</Badge>
      </div>
      <ul className="flex flex-col gap-3">
        {data.changes.map((change) => (
          <ChangeItem key={change.id} exam={exam} change={change} base={base} />
        ))}
      </ul>
    </section>
  );
}

function ChangeItem({ exam, change, base }: { exam: ExamDetail; change: ExamChange; base: number | null }) {
  const t = useTranslations("exams.versions.changes");
  const Icon = CHANGE_ICON[change.kind];
  return (
    <li className="flex items-start gap-3 rounded-lg border border-border bg-surface p-4">
      <span aria-hidden className="grid size-9 shrink-0 place-items-center rounded-md bg-primary-50 text-primary-text">
        <Icon className="size-[18px]" />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-semibold text-text">{t(`kind.${change.kind}`)}</span>
          <span className="text-body text-text-secondary">{change.where}</span>
          {change.warning && (
            <StatusChip tone={change.warning === "INVALID" ? "danger" : "warning"}>
              {t(`warning.${change.warning}`)}
            </StatusChip>
          )}
        </div>
        <div className="grid gap-2 @container sm:grid-cols-2">
          {change.before && (
            <div className="flex flex-col gap-0.5 rounded-md bg-subtle px-3 py-2">
              <span className="text-caption text-text-muted">
                {base !== null ? t("before", { version: base }) : t("beforeFirst")}
              </span>
              <del className="text-body text-text-secondary">{change.before}</del>
            </div>
          )}
          {change.after && (
            <div className="flex flex-col gap-0.5 rounded-md bg-primary-50 px-3 py-2">
              <span className="text-caption text-text-muted">{t("after")}</span>
              <ins className="text-body text-text no-underline">{change.after}</ins>
            </div>
          )}
        </div>
      </div>
      <IconLink
        href={structureHref(exam.id, change.blockId)}
        label={t("openAria", { where: change.where })}
        tooltip={t("openTooltip")}
        icon={<ArrowRight />}
      />
    </li>
  );
}

function HistorySection({
  exam,
  data,
  onView,
  onDiscontinue,
}: {
  exam: ExamDetail;
  data: ExamVersionList;
  onView: (version: number) => void;
  onDiscontinue: () => void;
}) {
  const t = useTranslations("exams.versions");
  const format = useFormatter();
  const single = t("singlePath");

  // Giữ định nghĩa cột ổn định: bảng dựng lại ô khi cột đổi, nút đang focus (và đích trả focus của hộp thoại) sẽ mất.
  const { columns, cards } = useMemo(() => {
    const status = (row: ExamVersionRow) => {
      if (row.state !== "SERVING") return <StatusChip tone="neutral">{t("state.RETIRED")}</StatusChip>;
      const partial = row.servingPaths.length < row.publishedPaths.length;
      return (
        <StatusChip tone="success">
          {partial ? t("state.servingPaths", { paths: pathLabelCodes(row.servingPaths, single) }) : t("state.SERVING")}
        </StatusChip>
      );
    };
    const actions = (row: ExamVersionRow) => (
      <div className="flex items-center justify-end gap-0.5">
        <IconButton
          icon={<FileText />}
          aria-label={t("actions.viewAria", { version: row.version })}
          tooltip={t("actions.viewTooltip")}
          onClick={() => onView(row.version)}
        />
        <IconLink
          href={reviewHref(exam.id, { tab: "preview", version: String(row.version) })}
          label={t("actions.previewAria", { version: row.version })}
          tooltip={t("actions.previewTooltip")}
          icon={<Eye />}
        />
      </div>
    );
    const publishedAt = (row: ExamVersionRow) =>
      row.publishedAt ? format.dateTime(new Date(row.publishedAt), "dateTime") : "—";

    const columns: ColumnDef<ExamVersionRow>[] = [
      {
        id: "version",
        header: t("columns.version"),
        cell: ({ row }) => (
          <span className="font-semibold text-text">{t("versionName", { version: row.original.version })}</span>
        ),
      },
      {
        id: "publishedAt",
        header: t("columns.publishedAt"),
        cell: ({ row }) => <span className="whitespace-nowrap">{publishedAt(row.original)}</span>,
      },
      { id: "publishedBy", header: t("columns.publishedBy"), cell: ({ row }) => row.original.publishedBy?.name ?? "—" },
      {
        id: "paths",
        header: t("columns.paths"),
        cell: ({ row }) => (
          <span className="block min-w-36">{pathLabelCodes(row.original.publishedPaths, single)}</span>
        ),
      },
      {
        id: "attempts",
        header: t("columns.attempts"),
        cell: ({ row }) => <span className="tabular-nums">{format.number(row.original.attemptCount)}</span>,
      },
      { id: "status", header: t("columns.status"), cell: ({ row }) => status(row.original) },
      {
        id: "actions",
        header: () => <span className="block text-right">{t("columns.actions")}</span>,
        cell: ({ row }) => actions(row.original),
      },
    ];

    const cards: TableCards<ExamVersionRow> = {
      render: (row) => (
        <ListCard
          title={t("versionName", { version: row.version })}
          caption={pathLabelCodes(row.publishedPaths, single)}
          status={status(row)}
          actions={actions(row)}
          meta={[
            { label: t("columns.publishedAt"), value: publishedAt(row) },
            { label: t("columns.publishedBy"), value: row.publishedBy?.name ?? "—" },
            { label: t("columns.attempts"), value: format.number(row.attemptCount) },
          ]}
        >
          {row.note && <p className="text-body text-text-secondary">{row.note}</p>}
        </ListCard>
      ),
    };
    return { columns, cards };
  }, [t, format, single, exam.id, onView]);

  return (
    <section aria-labelledby="versions-history-title" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="versions-history-title" className="text-h3 font-bold text-text">
          {t("history.title")}
        </h2>
        {exam.status === "PUBLISHED" && (
          <Button variant="secondary" onClick={onDiscontinue}>
            <CircleOff />
            {t("discontinue.action")}
          </Button>
        )}
        {exam.status === "DISCONTINUED" && <StatusChip tone="neutral">{t("history.discontinued")}</StatusChip>}
      </div>
      <Card padding="none" className="overflow-hidden">
        <Table
          aria-label={t("history.tableLabel", { code: exam.code })}
          columns={columns}
          data={data.items}
          getRowId={(row) => String(row.version)}
          stickyColumns={{ start: true, end: true }}
          empty={
            <EmptyState
              headingLevel={3}
              icon={<History />}
              title={t("history.emptyTitle")}
              description={t("history.emptyDescription")}
            />
          }
          cards={cards}
        />
      </Card>
    </section>
  );
}
