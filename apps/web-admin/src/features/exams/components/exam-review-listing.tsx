"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { Badge, Button, Card, EmptyState, ListCard, StatusChip, Table, type StatusTone, type TableCards } from "@thi360/ui";
import { ArrowRight, CircleAlert, CircleCheck, PenLine, RefreshCw, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useMemo } from "react";
import { collectCheckIssues, findBlockContext } from "../check";
import { examTabHref } from "../editor-tabs";
import { LISTING_CHECK_ROWS, useListingIssueText } from "../listing/issue-text";
import type { ExamCheckResult, ListingCheckGroup, ListingCheckIssue } from "../review-types";
import type { ExamDetail } from "../types";
import { domId, IconLink, labelAfterName, labelBeforeName, structureHref, useLookup } from "./exam-review-shared";

export const REVIEW_LISTING_ANCHOR = "review-listing";
export const REVIEW_MANUAL_ANCHOR = "review-manual-grading";

const LISTING_TONE: Record<ListingCheckGroup["status"], StatusTone> = { PASS: "success", WARN: "warning", FAIL: "danger" };

/** Số lỗi chặn công bố của nhóm Thông tin bán; thiếu nhóm (máy chủ chưa tính được) là 0. */
export function listingErrorCount(group: ListingCheckGroup | null | undefined): number {
  return group?.issues.filter((issue) => issue.severity === "ERROR").length ?? 0;
}

/** Chip tổng của nhóm: "Đạt", "n cảnh báo" hoặc "n lỗi". */
export function useListingStatusText() {
  const t = useTranslations("exams.review.listing.status");
  return (group: ListingCheckGroup): string =>
    group.status === "PASS"
      ? t("PASS")
      : group.status === "FAIL"
        ? t("FAIL", { count: listingErrorCount(group) })
        : t("WARN", { count: group.issues.length });
}

interface ListingCheckSectionProps {
  exam: ExamDetail;
  group: ListingCheckGroup | null | undefined;
  /** Tải lại kết quả kiểm tra khi nhóm chưa tính được. */
  onReload: () => void;
  reloading: boolean;
}

/**
 * Nhóm "Thông tin bán" của tab Kiểm tra (bản vẽ `BaiThiKiemTraThuongMai`): đọc từ `listingCheck`, không qua `groups[]`.
 * Bốn hàng Giá bán, Số lượt, Mô tả, Ảnh bìa; lỗi chặn công bố có icon lỗi, thiếu ảnh bìa chỉ là cảnh báo. Máy chủ không
 * trả nhóm này thì hiện trạng thái lỗi kèm nút thử lại, các nhóm còn lại vẫn dùng được.
 */
export function ListingCheckSection({ exam, group, onReload, reloading }: ListingCheckSectionProps) {
  const t = useTranslations("exams.review.listing");
  const issueText = useListingIssueText();
  const statusText = useListingStatusText();

  if (!group) {
    return (
      <section id={REVIEW_LISTING_ANCHOR} aria-label={t("title")}>
        <Card>
          <EmptyState
            headingLevel={2}
            icon={<TriangleAlert />}
            title={t("unavailableTitle")}
            description={t("unavailable")}
            action={
              <Button variant="secondary" onClick={onReload} loading={reloading}>
                <RefreshCw />
                {t("retry")}
              </Button>
            }
          />
        </Card>
      </section>
    );
  }

  const covered = new Set<ListingCheckIssue>();
  const rows = LISTING_CHECK_ROWS.map((row) => {
    const issue = group.issues.find((item) => item.field === row.field);
    if (issue) covered.add(issue);
    return { id: row.id, issue };
  });
  const extras = group.issues.filter((issue) => !covered.has(issue));

  return (
    <section id={REVIEW_LISTING_ANCHOR} aria-labelledby={`${REVIEW_LISTING_ANCHOR}-title`} className="overflow-hidden rounded-lg border border-border bg-surface">
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2 border-b border-border p-4">
        <div className="min-w-0 flex-1 basis-96">
          <div className="flex flex-wrap items-center gap-2">
            <h2 id={`${REVIEW_LISTING_ANCHOR}-title`} className="text-h3 font-bold text-text">
              {t("title")}
            </h2>
            <StatusChip tone={LISTING_TONE[group.status]}>{statusText(group)}</StatusChip>
          </div>
          <p className="mt-0.5 text-body text-text-muted">{t("description")}</p>
        </div>
        <Link
          href={examTabHref(exam.id, "listing")}
          className="inline-flex min-h-11 items-center gap-1.5 rounded-sm font-semibold whitespace-nowrap text-primary-text hover:underline"
        >
          {t("edit")}
          <ArrowRight aria-hidden className="size-4" />
        </Link>
      </div>
      <ul className="m-0 grid list-none grid-cols-[repeat(auto-fit,minmax(min(26rem,100%),1fr))] gap-x-6 px-4 py-0">
        {rows.map(({ id, issue }) => (
          <ListingRow
            key={id}
            name={t(`rows.${id}.name`)}
            issue={issue}
            detail={issue ? issueText(issue) : t(`rows.${id}.pass`)}
          />
        ))}
        {extras.map((issue) => (
          <ListingRow key={issue.key} name={t("title")} issue={issue} detail={issueText(issue)} />
        ))}
      </ul>
    </section>
  );
}

function ListingRow({ name, issue, detail }: { name: string; issue: ListingCheckIssue | undefined; detail: string }) {
  const t = useTranslations("exams.review.listing.rowStatus");
  const state = !issue ? "pass" : issue.severity === "ERROR" ? "error" : "warning";
  const tone: StatusTone = state === "pass" ? "success" : state === "error" ? "danger" : "warning";
  return (
    <li className="flex items-start gap-3 border-b border-border py-3">
      {state === "pass" ? (
        <CircleCheck aria-hidden className="mt-0.5 size-[18px] shrink-0 text-success" />
      ) : state === "error" ? (
        <CircleAlert aria-hidden className="mt-0.5 size-[18px] shrink-0 text-danger" />
      ) : (
        <TriangleAlert aria-hidden className="mt-0.5 size-[18px] shrink-0 text-warning" />
      )}
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-semibold text-text">{name}</span>
          <StatusChip tone={tone}>{t(state)}</StatusChip>
        </div>
        <p className="text-body text-text-muted tabular-nums">{detail}</p>
      </div>
    </li>
  );
}

interface ManualRow {
  key: string;
  part: string;
  subject: string;
  block: string;
  paths: string;
  blockId: string | null;
}

/**
 * Cảnh báo "Có câu chấm tay" (`MANUAL_GRADING_REQUIRED`, 2B H18; LT 9.3, AC-12.08) theo bản vẽ
 * `BaiThiKiemTraThuongMai`: tiêu đề, câu "Bài có câu tự luận. Điểm tổng hiện sau khi người chấm chấm xong." và bảng phần,
 * môn, khối có câu tự luận. Cảnh báo không chặn công bố; không có câu tự luận thì không hiện mục này.
 */
export function ManualGradingSection({ exam, result }: { exam: ExamDetail; result: ExamCheckResult }) {
  const t = useTranslations("exams.review.manual");
  const lookup = useLookup();
  const manual = useMemo(
    () => collectCheckIssues(result).filter((issue) => issue.code === "MANUAL_GRADING_REQUIRED"),
    [result],
  );

  const { columns, cards, rows } = useMemo(() => {
    const none = "–";
    const rows: ManualRow[] = manual.map((issue) => {
      const where = findBlockContext(exam, issue.blockId);
      const subjectCode = issue.subjectCode ?? where?.section?.subjectCode ?? null;
      return {
        key: issue.key,
        part: issue.partName ?? where?.part.name ?? exam.parts.find((part) => part.id === issue.partId)?.name ?? none,
        subject: subjectCode ? lookup.subject(subjectCode) : none,
        block: issue.blockName ?? where?.block.name ?? none,
        paths: issue.affectedCodes && issue.affectedCodes.length > 0 ? issue.affectedCodes.join(", ") : t("allPaths"),
        blockId: issue.blockId,
      };
    });
    const blockNameId = (row: ManualRow) => domId(REVIEW_MANUAL_ANCHOR, "block", row.key);
    const location = (row: ManualRow) => (
      <IconLink
        href={structureHref(exam.id, row.blockId)}
        label={labelBeforeName(t, "openAria", "block")}
        nameId={blockNameId(row)}
        labelAfter={labelAfterName(t, "openAria", "block")}
        tooltip={t("openTooltip")}
        icon={<ArrowRight />}
      />
    );
    const blockCell = (row: ManualRow) => (
      <span className="inline-flex flex-wrap items-center gap-2">
        <span id={blockNameId(row)} data-exam-content>
          {row.block}
        </span>
        <Badge className="gap-1">
          <PenLine aria-hidden className="size-3" />
          {t("badge")}
        </Badge>
      </span>
    );
    const columns: ColumnDef<ManualRow>[] = [
      { id: "part", header: t("columns.part"), cell: ({ row }) => <span className="font-semibold text-text" data-exam-content>{row.original.part}</span> },
      { id: "subject", header: t("columns.subject"), cell: ({ row }) => row.original.subject },
      { id: "block", header: t("columns.block"), cell: ({ row }) => blockCell(row.original) },
      { id: "paths", header: t("columns.paths"), cell: ({ row }) => <span data-exam-content>{row.original.paths}</span> },
      {
        id: "location",
        header: () => <span className="block text-right">{t("columns.location")}</span>,
        cell: ({ row }) => <span className="flex justify-end">{location(row.original)}</span>,
      },
    ];
    const cards: TableCards<ManualRow> = {
      render: (row) => (
        <ListCard
          title={<span data-exam-content className="line-clamp-2">{row.part}</span>}
          caption={row.subject}
          status={<StatusChip tone="warning">{t("badge")}</StatusChip>}
          actions={location(row)}
          meta={[
            {
              label: t("columns.block"),
              value: (
                <span id={blockNameId(row)} data-exam-content>
                  {row.block}
                </span>
              ),
            },
            { label: t("columns.paths"), value: <span data-exam-content>{row.paths}</span> },
          ]}
        />
      ),
    };
    return { columns, cards, rows };
  }, [manual, exam, lookup, t]);

  if (manual.length === 0) return null;

  return (
    <section
      id={REVIEW_MANUAL_ANCHOR}
      aria-labelledby={`${REVIEW_MANUAL_ANCHOR}-title`}
      className="overflow-hidden rounded-lg border border-warning/30 bg-surface"
    >
      <div className="flex items-start gap-3 border-b border-warning/30 bg-warning-bg p-4">
        <PenLine aria-hidden className="mt-1 size-[22px] shrink-0 text-warning" />
        <div className="flex min-w-0 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 id={`${REVIEW_MANUAL_ANCHOR}-title`} className="text-h3 font-bold text-text">
              {t("title")}
            </h2>
            <StatusChip tone="warning">{t("tag")}</StatusChip>
          </div>
          <p className="text-body text-text-secondary">{t("description")}</p>
        </div>
      </div>
      <Table
        aria-label={t("caption")}
        columns={columns}
        data={rows}
        getRowId={(row) => row.key}
        empty={null}
        cards={cards}
      />
      <ul className="m-0 flex list-disc flex-col gap-1 py-3 pr-4 pl-9 text-body text-text-secondary">
        <li>{t("notes.queue")}</li>
        <li>{t("notes.source")}</li>
      </ul>
    </section>
  );
}
