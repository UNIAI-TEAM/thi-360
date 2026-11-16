"use client";

import type { ColumnDef, RowSelectionState, SortingState, Updater } from "@tanstack/react-table";
import {
  Button,
  Card,
  cn,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
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
import {
  CircleCheck,
  Eye,
  FileQuestion,
  MoreHorizontal,
  Pencil,
  Plus,
  PowerOff,
  TriangleAlert,
  Upload,
} from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ContentMockBanner } from "@/features/content/components/content-mock-banner";
import { indexKnowledge, knowledgeName, type KnowledgeIndexEntry } from "@/features/content/knowledge-tree";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import { QUESTION_STATUS_TONE } from "@/features/content/status";
import { defaultQuestionBankApiDeps, type QuestionBankApiDeps } from "../api";
import type { QuestionListItem } from "../types";
import { useQuestionAuthors, useQuestionList, useQuestionStatusCounts } from "../queries";
import type { QuestionSummary } from "../types";
import { QuestionBankBulkBar, type SelectedQuestion } from "./question-bank-bulk-bar";
import { QuestionFiltersBar } from "./question-filters";
import {
  hasActiveQuestionFilters,
  QUESTION_PAGE_SIZES,
  toListQuestionsQuery,
  useQuestionFiltersUrlState,
} from "./question-filters-state";
import { MarkReadyDialog, RetireQuestionDialog, useReadinessIssueText } from "./question-status-dialogs";

/**
 * Cột ưu tiên thấp tự ẩn theo bề rộng khung chứa bảng (DESIGN.md mục 5.9): Cập nhật ẩn trước, rồi Người tạo, Điểm
 * (đã có ở dòng phụ dưới nội dung), Dạng câu và Độ khó (hiện thành dòng tóm tắt dưới nội dung). Khung hẹp hơn 40rem
 * thì bảng thành danh sách thẻ.
 *
 * Mỗi ngưỡng xấp xỉ bề rộng tối thiểu của bảng khi còn cột đó cộng khoảng dư 3rem, để bảng vừa khung và cột Thao tác
 * ghim không đè lên Cập nhật hay MAPID lúc mới mở. Đổi bề rộng tối thiểu của một cột (cột mới, `min-w-*`) thì phải
 * đo lại các ngưỡng này trên trình duyệt ở các bề rộng 1440 và 1920.
 */
const DROP_LOW_PRIORITY_COLUMNS = [
  "@container",
  "@max-[111rem]:[&_:is(th,td):has([data-col=updated])]:hidden",
  "@max-[103rem]:[&_:is(th,td):has([data-col=author])]:hidden",
  "@max-[93rem]:[&_:is(th,td):has([data-col=points])]:hidden",
  "@max-[87rem]:[&_:is(th,td):has([data-col=type])]:hidden",
  "@max-[87rem]:[&_:is(th,td):has([data-col=difficulty])]:hidden",
].join(" ");

/** Dòng tóm tắt dưới nội dung: chỉ hiện khi cột Dạng câu và Độ khó đã ẩn. */
const NARROW_SUMMARY = "hidden @max-[87rem]:block";

type DialogState = { kind: "ready" | "retire"; question: QuestionSummary } | null;

export interface QuestionBankViewProps {
  deps?: Partial<QuestionBankApiDeps>;
}

/** Màn danh sách Ngân hàng câu hỏi `/questions` (canvas `NganHangCauHoi`). */
export function QuestionBankView({ deps }: QuestionBankViewProps) {
  const t = useTranslations("questions");
  const contentT = useTranslations("content");
  const format = useFormatter();
  const router = useRouter();
  const api = useMemo(() => ({ ...defaultQuestionBankApiDeps, ...deps }), [deps]);
  const [filters, setFilters] = useQuestionFiltersUrlState();
  const query = useMemo(() => toListQuestionsQuery(filters), [filters]);

  const listQuery = useQuestionList(query, api);
  const countsQuery = useQuestionStatusCounts(query, api);
  const authorsQuery = useQuestionAuthors(api);
  const referenceQuery = useContentReference();
  const lookup = useReferenceLookup(referenceQuery.data);
  const knowledgeIndex = useMemo(() => indexKnowledge(referenceQuery.data?.knowledgeTree ?? []), [referenceQuery.data]);
  const issueText = useReadinessIssueText();

  const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
  const [selectedMeta, setSelectedMeta] = useState<Record<string, SelectedQuestion>>({});
  const [dialog, setDialog] = useState<DialogState>(null);
  const [announcement, setAnnouncement] = useState("");
  const [announcedKey, setAnnouncedKey] = useState<string | null>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const tableAreaRef = useRef<HTMLDivElement>(null);

  const items = useMemo(() => listQuery.data?.content ?? [], [listQuery.data]);
  const totalItems = listQuery.data?.totalElements ?? 0;
  const hasFilters = hasActiveQuestionFilters(filters);

  // Trang vượt quá số trang (sau khi chuyển trạng thái hàng loạt hoặc sửa URL): lùi về trang cuối hợp lệ.
  const totalPages = listQuery.data?.totalPages ?? 1;
  const pageOutOfRange = listQuery.isSuccess && !listQuery.isPlaceholderData && filters.page > totalPages;
  useEffect(() => {
    if (pageOutOfRange) setFilters({ page: totalPages });
  }, [pageOutOfRange, totalPages, setFilters]);

  // Đọc số kết quả khi dữ liệu của MỘT BỘ LỌC KHÁC vừa về (không đọc lần tải đầu). Điều chỉnh state trong render.
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

  function handleSelectionChange(updater: Updater<RowSelectionState>) {
    const next = typeof updater === "function" ? updater(rowSelection) : updater;
    setRowSelection(next);
    setSelectedMeta((previous) => {
      const meta: Record<string, SelectedQuestion> = {};
      for (const id of Object.keys(next)) {
        if (!next[id]) continue;
        const row = items.find((item) => item.id === id);
        const known = previous[id];
        if (row) meta[id] = { id, code: row.code, usedInExamCount: row.usedInExamCount };
        else if (known) meta[id] = known;
      }
      return meta;
    });
    const count = Object.values(next).filter(Boolean).length;
    setAnnouncement(count > 0 ? t("bulk.selectedCount", { count }) : t("list.selectionCleared"));
  }

  const selected = useMemo(
    () => Object.keys(rowSelection).filter((id) => rowSelection[id] && selectedMeta[id]).map((id) => selectedMeta[id]!),
    [rowSelection, selectedMeta],
  );

  /** Thanh chọn đã ẩn: focus về ô chọn tất cả (hoặc ô tìm), không để rơi về `<body>`. */
  const focusAfterClearSelection = useCallback(() => {
    requestAnimationFrame(() => {
      const selectAll = tableAreaRef.current?.querySelector<HTMLElement>('[role="checkbox"], input[type="checkbox"]');
      (selectAll ?? searchInputRef.current)?.focus();
    });
  }, []);

  const openDialog = useCallback((kind: "ready" | "retire", question: QuestionSummary) => {
    setDialog({ kind, question });
  }, []);

  const subjectGrade = useCallback(
    (item: QuestionSummary) =>
      t("list.subjectGrade", { subject: lookup.subject(item.subjectCode), grade: lookup.grade(item.gradeCode) }),
    [t, lookup],
  );

  const columns = useMemo<ColumnDef<QuestionListItem>[]>(
    () => [
      {
        id: "content",
        header: t("list.table.content"),
        enableSorting: false,
        cell: ({ row }) => {
          const item = row.original;
          return (
            <div className="flex min-w-64 max-w-md flex-col gap-0.5">
              <Link
                href={`/questions/${encodeURIComponent(item.id)}`}
                className={cn("w-fit rounded-sm font-medium text-text hover:text-primary-text hover:underline", focusRing)}
              >
                <span className="line-clamp-2">{item.contentPreview}</span>
              </Link>
              <span className="text-caption text-text-muted tabular-nums">
                {t("list.rowMeta", { code: item.code, points: item.points })}
              </span>
              <span className={cn("text-caption text-text-muted", NARROW_SUMMARY)}>
                {contentT(`questionType.${item.type}`)} · {lookup.difficulty(item.difficultyCode)}
              </span>
              {item.readinessHint && (
                <span className="flex items-start gap-1.5 text-caption text-warning">
                  <TriangleAlert aria-hidden className="mt-0.5 size-3.5 shrink-0" />
                  <span>
                    <span className="sr-only">{t("list.readinessHintPrefix")} </span>
                    {issueText(item.readinessHint)}
                  </span>
                </span>
              )}
            </div>
          );
        },
      },
      {
        id: "type",
        header: () => <span data-col="type">{t("list.table.type")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="type" className="block min-w-28 text-text">
            {contentT(`questionType.${row.original.type}`)}
          </span>
        ),
      },
      {
        id: "subject",
        header: t("list.table.subjectGrade"),
        enableSorting: false,
        cell: ({ row }) => <span className="whitespace-nowrap text-text">{subjectGrade(row.original)}</span>,
      },
      {
        id: "difficulty",
        header: () => <span data-col="difficulty">{t("list.table.difficulty")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="difficulty" className="whitespace-nowrap text-text">
            {lookup.difficulty(row.original.difficultyCode)}
          </span>
        ),
      },
      {
        id: "knowledge",
        header: t("list.table.knowledge"),
        enableSorting: false,
        cell: ({ row }) => <KnowledgeCell item={row.original} index={knowledgeIndex} />,
      },
      {
        id: "points",
        accessorKey: "points",
        header: () => <span data-col="points">{t("list.table.points")}</span>,
        meta: { label: t("list.table.points") },
        cell: ({ row }) => (
          <span data-col="points" className="block text-end tabular-nums text-text">
            {format.number(row.original.points)}
          </span>
        ),
      },
      {
        id: "status",
        accessorKey: "status",
        header: t("list.table.status"),
        cell: ({ row }) => (
          <StatusChip tone={QUESTION_STATUS_TONE[row.original.status]}>
            {contentT(`questionStatus.${row.original.status}`)}
          </StatusChip>
        ),
      },
      {
        id: "author",
        header: () => <span data-col="author">{t("list.table.author")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="author" className="block min-w-28 text-text">
            {row.original.createdBy.name}
          </span>
        ),
      },
      {
        id: "updatedAt",
        accessorKey: "updatedAt",
        header: () => <span data-col="updated">{t("list.table.updated")}</span>,
        meta: { label: t("list.table.updated") },
        cell: ({ row }) => (
          <span data-col="updated" className="whitespace-nowrap text-text tabular-nums">
            {format.dateTime(new Date(row.original.updatedAt), "date")}
          </span>
        ),
      },
      {
        id: "actions",
        header: () => <span className="block text-end">{t("list.table.actions")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex items-center justify-end gap-0.5 whitespace-nowrap">
            <IconButton
              icon={<Eye />}
              aria-label={t("list.actions.viewAria", { code: row.original.code })}
              tooltip={t("list.actions.viewTooltip")}
              onClick={() => router.push(`/questions/${encodeURIComponent(row.original.id)}`)}
            />
            <RowActions item={row.original} onOpen={openDialog} />
          </div>
        ),
      },
    ],
    [t, contentT, format, lookup, knowledgeIndex, router, issueText, subjectGrade, openDialog],
  );

  /**
   * Khung hẹp: mỗi câu một thẻ. Nội dung là tiêu đề dẫn tới chi tiết (cả thẻ bấm được, không cần nút Xem); mã và
   * dạng câu ở dòng phụ; chip trạng thái rồi môn · khối, độ khó; gợi ý điều kiện còn thiếu; MAPID, điểm, người tạo,
   * ngày cập nhật ở dòng thông tin thêm. Sửa và "⋯" như cột Thao tác.
   */
  const cards = useMemo<TableCards<QuestionListItem>>(
    () => ({
      render: (item) => {
        const primary = item.knowledge.find((ref) => ref.primary) ?? item.knowledge[0];
        return (
          <ListCard
            title={
              <ListCardTitleLink linkComponent={Link} href={`/questions/${encodeURIComponent(item.id)}`}>
                {item.contentPreview}
              </ListCardTitleLink>
            }
            actions={<RowActions item={item} onOpen={openDialog} />}
            caption={`${item.code} · ${contentT(`questionType.${item.type}`)}`}
            status={
              <StatusChip tone={QUESTION_STATUS_TONE[item.status]}>
                {contentT(`questionStatus.${item.status}`)}
              </StatusChip>
            }
            tags={
              <>
                <ListCardTag>{subjectGrade(item)}</ListCardTag>
                <ListCardTag>{lookup.difficulty(item.difficultyCode)}</ListCardTag>
              </>
            }
            meta={[
              {
                label: t("list.table.knowledgeShort"),
                value: primary
                  ? item.knowledge.length > 1
                    ? t("list.knowledgeWithMore", { code: primary.code, count: item.knowledge.length - 1 })
                    : primary.code
                  : t("list.noKnowledge"),
              },
              { label: t("list.table.points"), value: format.number(item.points) },
              { label: t("list.table.author"), value: item.createdBy.name },
              { label: t("list.table.updated"), value: format.dateTime(new Date(item.updatedAt), "date") },
            ]}
          >
            {item.readinessHint && (
              <p className="flex items-start gap-1.5 text-caption text-warning">
                <TriangleAlert aria-hidden className="mt-0.5 size-3.5 shrink-0" />
                <span>
                  <span className="sr-only">{t("list.readinessHintPrefix")} </span>
                  {issueText(item.readinessHint)}
                </span>
              </p>
            )}
          </ListCard>
        );
      },
      sortLabels: {
        label: t("list.sortBy"),
        ascending: t("list.ascending"),
        descending: t("list.descending"),
      },
    }),
    [t, contentT, format, lookup, issueText, subjectGrade, openDialog],
  );

  const hasSelection = selected.length > 0;
  const createButton = (
    <Button asChild>
      <Link href="/questions/new">
        <Plus />
        <span>{t("list.create")}</span>
      </Link>
    </Button>
  );

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title={t("list.title")}
        description={t("list.description")}
        actions={
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="secondary">
              <Link href="/questions/import">
                <Upload />
                <span>{t("list.import")}</span>
              </Link>
            </Button>
            {createButton}
          </div>
        }
      />
      <ContentMockBanner />

      <QuestionFiltersBar
        filters={filters}
        onChange={setFilters}
        reference={referenceQuery.data}
        authors={authorsQuery.data ?? []}
        counts={countsQuery.data}
        searchInputRef={searchInputRef}
      />

      <Card padding="none" className="overflow-hidden">
        <LiveStatus message={announcement} />
        {/* Một ô lưới cho hai lớp chồng nhau: dải số kết quả và thanh thao tác hàng loạt (DESIGN.md mục 5.9). */}
        <div
          className={cn("@container grid grid-cols-1 border-b", hasSelection ? "border-primary-200" : "border-border")}
        >
          <div
            aria-hidden={hasSelection || undefined}
            inert={hasSelection}
            className={cn(
              "col-start-1 row-start-1 flex min-w-0 flex-wrap items-center justify-between gap-2 px-4 py-3",
              hasSelection && "invisible",
            )}
          >
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
          <QuestionBankBulkBar
            className="col-start-1 row-start-1 min-w-0"
            selected={selected}
            deps={api}
            onClearSelection={() => {
              handleSelectionChange({});
              focusAfterClearSelection();
            }}
            onApplied={() => {
              setRowSelection({});
              setSelectedMeta({});
              focusAfterClearSelection();
            }}
          />
        </div>

        <div ref={tableAreaRef} className={DROP_LOW_PRIORITY_COLUMNS}>
          <Table
            aria-label={t("list.tableAriaLabel")}
            stickyColumns={{ start: true, end: true }}
            columns={columns}
            data={items}
            getRowId={(row) => row.id}
            loading={listQuery.isLoading}
            loadingRows={filters.size}
            refreshing={listQuery.isPlaceholderData}
            selection={{
              state: rowSelection,
              onChange: handleSelectionChange,
              selectAllLabel: t("list.selectAll"),
              rowLabel: (_index, row) => t("list.selectRow", { code: row.code }),
            }}
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
              hasFilters ? (
                <EmptyState
                  icon={<FileQuestion />}
                  title={t("list.emptyFilteredTitle")}
                  description={t("list.emptyFilteredDescription")}
                  headingLevel={2}
                />
              ) : (
                <EmptyState
                  icon={<FileQuestion />}
                  title={t("list.emptyTitle")}
                  description={t("list.emptyDescription")}
                  headingLevel={2}
                  action={createButton}
                />
              )
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
              pageSizeOptions={[...QUESTION_PAGE_SIZES]}
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

      <MarkReadyDialog
        question={dialog?.kind === "ready" ? dialog.question : null}
        open={dialog?.kind === "ready"}
        onOpenChange={(open) => !open && setDialog(null)}
      />
      <RetireQuestionDialog
        question={dialog?.kind === "retire" ? dialog.question : null}
        open={dialog?.kind === "retire"}
        onOpenChange={(open) => !open && setDialog(null)}
      />
    </div>
  );
}

/** Ô MAPID: kiến thức chính (một dòng, cắt dấu ba chấm; đủ ở trang chi tiết) và "+n" khi gắn nhiều kiến thức. */
function KnowledgeCell({ item, index }: { item: QuestionSummary; index: ReadonlyMap<string, KnowledgeIndexEntry> }) {
  const t = useTranslations("questions.list");
  const primary = item.knowledge.find((ref) => ref.primary) ?? item.knowledge[0];
  if (!primary) return <span className="text-text-muted">{t("noKnowledge")}</span>;
  const more = item.knowledge.length - 1;
  return (
    <div className="flex max-w-60 min-w-40 items-center gap-1.5">
      <span className="min-w-0 truncate text-text">
        <span className="font-semibold">{primary.code}</span> {knowledgeName(index, primary)}
      </span>
      {more > 0 && (
        <span className="shrink-0 rounded-full bg-neutral-bg px-2 text-caption text-neutral tabular-nums">
          <span aria-hidden>+{more}</span>
          <span className="sr-only">{t("moreKnowledge", { count: more })}</span>
        </span>
      )}
    </div>
  );
}

/** Sửa và menu "⋯" (Xem trước, Chuyển sang Sẵn sàng, Ngừng sử dụng) của một câu, dùng chung cho bảng và thẻ. */
function RowActions({
  item,
  onOpen,
}: {
  item: QuestionSummary;
  onOpen: (kind: "ready" | "retire", question: QuestionSummary) => void;
}) {
  const t = useTranslations("questions.list.actions");
  const router = useRouter();
  return (
    <>
      <IconButton
        icon={<Pencil />}
        aria-label={t("editAria", { code: item.code })}
        tooltip={t("editTooltip")}
        onClick={() => router.push(`/questions/${encodeURIComponent(item.id)}/edit`)}
      />
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <IconButton icon={<MoreHorizontal />} aria-label={t("moreAria", { code: item.code })} tooltip={t("moreTooltip")} />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => router.push(`/questions/${encodeURIComponent(item.id)}/preview`)}>
            <Eye />
            <span>{t("preview")}</span>
          </DropdownMenuItem>
          {item.status !== "READY" && (
            <DropdownMenuItem onSelect={() => onOpen("ready", item)}>
              <CircleCheck />
              <span>{t("markReady")}</span>
            </DropdownMenuItem>
          )}
          {item.status !== "RETIRED" && (
            <DropdownMenuItem destructive onSelect={() => onOpen("retire", item)}>
              <PowerOff />
              <span>{t("retire")}</span>
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
    </>
  );
}

/** Khung chờ của trang khi `useSearchParams` chưa sẵn (Suspense của route). */
export function QuestionBankSkeleton() {
  return (
    <div className="flex flex-col gap-5" aria-busy="true">
      <Skeleton className="h-12 w-80 max-w-full" />
      <Skeleton className="h-10" />
      <Skeleton className="h-36" />
      <Skeleton className="h-96" />
    </div>
  );
}
