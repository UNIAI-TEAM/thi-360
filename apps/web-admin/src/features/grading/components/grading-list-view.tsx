"use client";

import type { ColumnDef, SortingState, Updater } from "@tanstack/react-table";
import {
  Button,
  Card,
  cn,
  EmptyState,
  focusRing,
  IconButton,
  ListCard,
  ListCardTag,
  ListCardTitleLink,
  LiveStatus,
  PageHeader,
  Pagination,
  Skeleton,
  StatusChip,
  Table,
  type TableCards,
} from "@thi360/ui";
import { ClipboardCheck, Eye, PenLine, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { MockDataBanner } from "@/components/mock-data-banner";
import { useContentReference } from "@/features/content/queries";
import { defaultGradingApiDeps, type GradingApiDeps } from "../api";
import { useGradingAttempts, useGradingFilterOptions, useGradingStatusCounts } from "../queries";
import { GRADING_STATUS_TONE, gradingAttemptHref } from "../status";
import type { GradingAttemptSummary } from "../types";
import { GradingFiltersBar } from "./grading-filters";
import {
  GRADING_PAGE_SIZES,
  hasActiveGradingFilters,
  toListGradingQuery,
  useGradingFiltersUrlState,
} from "./grading-filters-state";

/**
 * Cột ưu tiên thấp tự ẩn theo bề rộng khung chứa bảng (DESIGN.md mục 5.9): Người chấm ẩn trước, rồi Tự luận (số câu
 * đã chấm hiện ở dòng phụ dưới điểm). Khung hẹp hơn 40rem thì bảng thành danh sách thẻ.
 */
const DROP_LOW_PRIORITY_COLUMNS = [
  "@container",
  "@max-[72rem]:[&_:is(th,td):has([data-col=grader])]:hidden",
  "@max-[64rem]:[&_:is(th,td):has([data-col=progress])]:hidden",
].join(" ");

/** Dòng phụ dưới điểm: chỉ hiện khi cột Tự luận đã ẩn. */
const NARROW_PROGRESS = "hidden @max-[64rem]:block";

export interface GradingListViewProps {
  deps?: Partial<GradingApiDeps>;
}

/** Hàng chờ chấm tự luận `/grading` (spec UNI-2599 mục 4, Lane J). */
export function GradingListView({ deps }: GradingListViewProps) {
  const t = useTranslations("grading");
  const format = useFormatter();
  const router = useRouter();
  const api = useMemo(() => ({ ...defaultGradingApiDeps, ...deps }), [deps]);
  const [filters, setFilters] = useGradingFiltersUrlState();
  const query = useMemo(() => toListGradingQuery(filters), [filters]);

  const listQuery = useGradingAttempts(query, api);
  const countsQuery = useGradingStatusCounts(query, api);
  const optionsQuery = useGradingFilterOptions(api);
  const referenceQuery = useContentReference();

  const [announcement, setAnnouncement] = useState("");
  const [announcedKey, setAnnouncedKey] = useState<string | null>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  const items = useMemo(() => listQuery.data?.content ?? [], [listQuery.data]);
  const totalItems = listQuery.data?.totalElements ?? 0;
  const hasFilters = hasActiveGradingFilters(filters);

  const totalPages = listQuery.data?.totalPages ?? 1;
  const pageOutOfRange = listQuery.isSuccess && !listQuery.isPlaceholderData && filters.page > totalPages;
  useEffect(() => {
    if (pageOutOfRange) setFilters({ page: totalPages });
  }, [pageOutOfRange, totalPages, setFilters]);

  // Đọc số kết quả khi dữ liệu của một bộ lọc khác vừa về (không đọc lần tải đầu).
  const filterKey = JSON.stringify({ ...query, page: undefined, size: undefined, sort: undefined });
  if (listQuery.isSuccess && !listQuery.isPlaceholderData && announcedKey !== filterKey) {
    setAnnouncedKey(filterKey);
    if (announcedKey !== null) setAnnouncement(t("list.filteredCount", { count: totalItems }));
  }

  const sorting = useMemo<SortingState>(() => {
    if (!filters.sort) return [];
    const [id = "", direction] = filters.sort.split(",");
    return [{ id, desc: direction === "desc" }];
  }, [filters.sort]);

  function handleSortingChange(updater: Updater<SortingState>) {
    const next = typeof updater === "function" ? updater(sorting) : updater;
    const first = next[0];
    setFilters({ sort: first ? `${first.id},${first.desc ? "desc" : "asc"}` : null });
  }

  const columns = useMemo<ColumnDef<GradingAttemptSummary>[]>(
    () => [
      {
        id: "learner",
        accessorFn: (row) => row.learner.name,
        header: t("list.table.learner"),
        meta: { label: t("list.table.learner") },
        cell: ({ row }) => {
          const item = row.original;
          return (
            <div className="flex min-w-40 flex-col gap-0.5">
              <Link
                href={gradingAttemptHref(item.id)}
                className={cn("w-fit rounded-sm font-medium text-text hover:text-primary-text hover:underline", focusRing)}
              >
                {item.learner.name}
              </Link>
              <span className="text-caption text-text-muted">{item.learner.code}</span>
            </div>
          );
        },
      },
      {
        id: "exam",
        header: t("list.table.exam"),
        enableSorting: false,
        cell: ({ row }) => <ExamCell item={row.original} />,
      },
      {
        id: "submittedAt",
        accessorKey: "submittedAt",
        header: t("list.table.submittedAt"),
        meta: { label: t("list.table.submittedAt") },
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text tabular-nums">
            {format.dateTime(new Date(row.original.submittedAt), "dateTime")}
          </span>
        ),
      },
      {
        id: "progress",
        header: () => <span data-col="progress">{t("list.table.progress")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="progress" className="block min-w-24 whitespace-nowrap text-text tabular-nums">
            {t("list.progress", { graded: row.original.gradedEssayCount, count: row.original.essayCount })}
          </span>
        ),
      },
      {
        id: "score",
        header: () => <span className="block text-end">{t("list.table.score")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex min-w-36 flex-col items-end gap-0.5 text-end">
            <ScoreText item={row.original} />
            <span className={cn("text-caption text-text-muted tabular-nums", NARROW_PROGRESS)}>
              {t("list.progress", { graded: row.original.gradedEssayCount, count: row.original.essayCount })}
            </span>
          </div>
        ),
      },
      {
        id: "grader",
        header: () => <span data-col="grader">{t("list.table.grader")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="grader" className={cn("block min-w-28", row.original.grader ? "text-text" : "text-text-muted")}>
            {row.original.grader?.name ?? t("list.noGrader")}
          </span>
        ),
      },
      {
        id: "status",
        header: t("list.table.status"),
        enableSorting: false,
        cell: ({ row }) => (
          <StatusChip tone={GRADING_STATUS_TONE[row.original.status]}>{t(`status.${row.original.status}`)}</StatusChip>
        ),
      },
      {
        id: "actions",
        header: () => <span className="block text-end">{t("list.table.actions")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex items-center justify-end">
            <RowAction item={row.original} onOpen={(id) => router.push(gradingAttemptHref(id))} />
          </div>
        ),
      },
    ],
    [t, format, router],
  );

  /**
   * Khung hẹp: mỗi lượt một thẻ. Tên học viên là tiêu đề dẫn tới màn chấm (cả thẻ bấm được, không thêm nút riêng);
   * dòng phụ là mã học viên và mã bài; chip trạng thái rồi kỳ thi; thông tin thêm gồm bài thi, nộp lúc, số câu đã chấm,
   * điểm, người chấm.
   */
  const cards = useMemo<TableCards<GradingAttemptSummary>>(
    () => ({
      render: (item) => (
        <ListCard
          title={
            <ListCardTitleLink linkComponent={Link} href={gradingAttemptHref(item.id)}>
              {item.learner.name}
            </ListCardTitleLink>
          }
          caption={`${item.learner.code} · ${item.exam.code}`}
          status={<StatusChip tone={GRADING_STATUS_TONE[item.status]}>{t(`status.${item.status}`)}</StatusChip>}
          tags={<ListCardTag>{item.exam.kindCode}</ListCardTag>}
          meta={[
            { label: t("list.table.exam"), value: item.exam.name },
            { label: t("list.table.submittedAt"), value: format.dateTime(new Date(item.submittedAt), "dateTime") },
            {
              label: t("list.table.progress"),
              value: t("list.progress", { graded: item.gradedEssayCount, count: item.essayCount }),
            },
            { label: t("list.table.score"), value: <ScoreText item={item} inline /> },
            { label: t("list.table.grader"), value: item.grader?.name ?? t("list.noGrader") },
          ]}
        />
      ),
      sortLabels: {
        label: t("list.sortBy"),
        ascending: t("list.ascending"),
        descending: t("list.descending"),
      },
    }),
    [t, format],
  );

  return (
    <div className="flex flex-col gap-5">
      <PageHeader title={t("list.title")} description={t("list.description")} />
      <MockDataBanner />

      <GradingFiltersBar
        filters={filters}
        onChange={setFilters}
        reference={referenceQuery.data}
        options={optionsQuery.data}
        counts={countsQuery.data}
        searchInputRef={searchInputRef}
      />

      <Card padding="none" className="overflow-hidden">
        <LiveStatus message={announcement} />
        <div className="flex min-w-0 flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3">
          <p className="text-body font-medium text-text tabular-nums">
            {listQuery.isLoading
              ? t("list.loadingCount")
              : listQuery.isError
                ? t("list.errorCount")
                : t.rich("list.filteredCountRich", {
                    count: totalItems,
                    strong: (chunks) => <strong className="font-semibold">{chunks}</strong>,
                  })}
          </p>
          <p className="text-caption text-text-muted">{t("list.hint")}</p>
        </div>

        <div className={DROP_LOW_PRIORITY_COLUMNS}>
          <Table
            aria-label={t("list.tableAriaLabel")}
            stickyColumns={{ start: true, end: true }}
            columns={columns}
            data={items}
            getRowId={(row) => row.id}
            loading={listQuery.isLoading}
            loadingRows={filters.size}
            refreshing={listQuery.isPlaceholderData}
            sorting={{ state: sorting, manual: true, onChange: handleSortingChange }}
            cards={cards}
            error={
              listQuery.isError ? (
                <EmptyState
                  icon={<TriangleAlert />}
                  title={t("list.errorTitle")}
                  description={t("list.errorDescription")}
                  headingLevel={2}
                  action={
                    <Button variant="secondary" onClick={() => void listQuery.refetch()}>
                      {t("list.retry")}
                    </Button>
                  }
                />
              ) : undefined
            }
            empty={
              <EmptyState
                icon={<ClipboardCheck />}
                title={hasFilters ? t("list.emptyFilteredTitle") : t("list.emptyTitle")}
                description={hasFilters ? t("list.emptyFilteredDescription") : t("list.emptyDescription")}
                headingLevel={2}
              />
            }
          />
        </div>

        {totalItems > 0 && (
          <div className="border-t border-border px-4 py-3">
            <Pagination
              page={filters.page}
              pageSize={filters.size}
              total={totalItems}
              onPageChange={(page) => setFilters({ page })}
              pageSizeOptions={[...GRADING_PAGE_SIZES]}
              onPageSizeChange={(size) => setFilters({ size })}
              labels={{
                nav: t("list.paginationNav"),
                previous: t("list.paginationPrevious"),
                next: t("list.paginationNext"),
                rowsPerPage: t("list.paginationRowsPerPage"),
                page: (page) => t("list.paginationPage", { page: format.number(page) }),
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
    </div>
  );
}

/** Ô Bài thi: mã bài và kỳ thi trên một dòng, tên bài bên dưới (tối đa hai dòng). */
function ExamCell({ item }: { item: GradingAttemptSummary }) {
  return (
    <div className="flex min-w-48 max-w-72 flex-col gap-0.5">
      <span className="flex flex-wrap items-center gap-1.5">
        <span className="font-medium wrap-anywhere text-text">{item.exam.code}</span>
        <span className="rounded-full bg-neutral-bg px-2 text-caption text-neutral">{item.exam.kindCode}</span>
      </span>
      <span className="line-clamp-2 text-caption text-text-muted">{item.exam.name}</span>
    </div>
  );
}

/**
 * Điểm của lượt: đã có điểm thì tổng điểm (trắc nghiệm + tự luận); đang chấm thì điểm trắc nghiệm kèm "Tự luận đang
 * chấm". Đề chỉ có tự luận thì không nêu phần trắc nghiệm.
 */
function ScoreText({ item, inline = false }: { item: GradingAttemptSummary; inline?: boolean }) {
  const t = useTranslations("grading.list");
  const format = useFormatter();
  const max = item.objectiveMaxScore + item.essayMaxScore;
  if (item.status === "SCORED" && item.totalScore !== null) {
    const total = t("score.total", { score: format.number(item.totalScore), max: format.number(max) });
    const parts =
      item.objectiveMaxScore > 0
        ? t("score.parts", {
            objective: format.number(item.objectiveScore),
            essay: format.number(item.essayScore ?? 0),
          })
        : null;
    if (inline) return <>{parts ? `${total} (${parts})` : total}</>;
    return (
      <>
        <span className="font-semibold whitespace-nowrap text-text tabular-nums">{total}</span>
        {parts && <span className="text-caption whitespace-nowrap text-text-muted tabular-nums">{parts}</span>}
      </>
    );
  }
  const objective =
    item.objectiveMaxScore > 0
      ? t("score.objective", {
          score: format.number(item.objectiveScore),
          max: format.number(item.objectiveMaxScore),
        })
      : null;
  if (inline) return <>{objective ? `${objective} · ${t("score.essayPending")}` : t("score.essayPending")}</>;
  return (
    <>
      {objective && <span className="whitespace-nowrap text-text tabular-nums">{objective}</span>}
      <span className="text-caption whitespace-nowrap text-text-muted">{t("score.essayPending")}</span>
    </>
  );
}

/** Nút mở màn chấm: "Chấm bài" khi đang chấm, "Xem kết quả chấm" khi đã có điểm. */
function RowAction({ item, onOpen }: { item: GradingAttemptSummary; onOpen: (id: string) => void }) {
  const t = useTranslations("grading.list.actions");
  const scored = item.status === "SCORED";
  return (
    <IconButton
      icon={scored ? <Eye /> : <PenLine />}
      aria-label={scored ? t("viewAria", { name: item.learner.name }) : t("gradeAria", { name: item.learner.name })}
      tooltip={scored ? t("viewTooltip") : t("gradeTooltip")}
      onClick={() => onOpen(item.id)}
    />
  );
}

/** Khung chờ của trang khi `useSearchParams` chưa sẵn (Suspense của route). */
export function GradingListSkeleton() {
  return (
    <div className="flex flex-col gap-5" aria-busy="true">
      <Skeleton className="h-12 w-80 max-w-full" />
      <Skeleton className="h-10" />
      <Skeleton className="h-36" />
      <Skeleton className="h-96" />
    </div>
  );
}
