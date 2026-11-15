"use client";

import type { ColumnDef, SortingState } from "@tanstack/react-table";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Card,
  cn,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  EmptyState,
  focusRing,
  FormField,
  IconButton,
  Input,
  LiveStatus,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatusChip,
  Table,
  type TableCards,
} from "@thi360/ui";
import {
  Eye,
  GraduationCap,
  MoreHorizontal,
  Pencil,
  Plus,
  Power,
  PowerOff,
  Search,
  Trash2,
  TriangleAlert,
} from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
import { Can } from "@/features/session/can";
import { ForbiddenView } from "@/features/session/forbidden-view";
import { usePermissions, useSession } from "@/features/session/session-provider";
import type { UserSummary } from "@/features/users/api";
import {
  CLASSES_QUERY_KEYS,
  type ClassesApiDeps,
  type ClassStatus,
  type ClassSummary,
  defaultClassesApiDeps,
  type ListClassesQuery,
} from "../api";
import {
  ClassActionDialogs,
  useClassActionController,
} from "./class-action-dialogs";
import {
  FILTER_FORM,
  FILTER_SEARCH,
  FILTER_SELECT,
  FILTER_TOGGLES,
} from "@/features/users/components/list-filters";
import { ListCard, ListCardTitleLink } from "@/features/users/components/list-cards";
import { type ClassEditableFields, ClassFormDialog } from "./class-form-dialog";

const ALL_VALUE = "ALL";
const DEFAULT_PAGE_SIZE = 20;
const PAGE_SIZE_OPTIONS = [10, 20, 50];

type StatusFilter = "ALL" | ClassStatus;

/**
 * Cột ưu tiên thấp tự ẩn theo bề rộng của khung chứa bảng (container query, cùng cách với danh sách tài khoản):
 * Mô tả ẩn trước, rồi Người phụ trách, Số học viên. Khung hẹp hơn 40rem thì bảng thành danh sách thẻ (`cards` của
 * `Table`). Đầy đủ thông tin có ở trang chi tiết lớp.
 */
const DROP_LOW_PRIORITY_COLUMNS = [
  "@container",
  "@max-[64rem]:[&_:is(th,td):has([data-col=description])]:hidden",
  "@max-[56rem]:[&_:is(th,td):has([data-col=owner])]:hidden",
  "@max-3xl:[&_:is(th,td):has([data-col=memberCount])]:hidden",
].join(" ");

export interface ClassesListViewProps {
  deps?: Partial<ClassesApiDeps>;
  listActiveUsers?: () => Promise<UserSummary[]>;
}

export function ClassesListView({ deps, listActiveUsers }: ClassesListViewProps) {
  const sessionState = useSession();
  const { can } = usePermissions();

  if (sessionState.status === "loading") {
    return <ClassesListSkeleton />;
  }

  if (sessionState.status === "ready" && !can("CLASS.VIEW")) {
    return <ForbiddenView />;
  }

  return (
    <ClassesListContent
      deps={{ ...defaultClassesApiDeps, ...deps }}
      listActiveUsers={listActiveUsers}
    />
  );
}

function ClassesListSkeleton() {
  const common = useTranslations("common");
  return (
    <div role="status" aria-label={common("loading")} className="flex flex-col gap-4">
      <Skeleton className="h-12 w-72" />
      <Skeleton className="h-20 w-full" />
      <Skeleton className="h-80 w-full" />
    </div>
  );
}

function ClassesListContent({
  deps,
  listActiveUsers,
}: {
  deps: ClassesApiDeps;
  listActiveUsers?: () => Promise<UserSummary[]>;
}) {
  const t = useTranslations("classes");
  const common = useTranslations("common");
  const format = useFormatter();
  const { can } = usePermissions();
  const router = useRouter();
  const queryClient = useQueryClient();

  const [searchInput, setSearchInput] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [status, setStatus] = useState<StatusFilter>(ALL_VALUE);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [sorting, setSorting] = useState<SortingState>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [editingClass, setEditingClass] = useState<ClassEditableFields | null>(null);

  const actionController = useClassActionController({
    deps,
    onMutated: () => {
      void queryClient.invalidateQueries({ queryKey: CLASSES_QUERY_KEYS.all });
    },
  });

  useEffect(() => {
    const trimmed = searchInput.trim();
    if (trimmed === debouncedSearch) return;
    const timer = setTimeout(() => {
      setDebouncedSearch(trimmed);
      setPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchInput, debouncedSearch]);

  const sortParam = useMemo(() => {
    const first = sorting[0];
    if (!first) return "createdAt,desc";
    return `${first.id},${first.desc ? "desc" : "asc"}`;
  }, [sorting]);

  const queryParams = useMemo<ListClassesQuery>(
    () => ({
      ...(debouncedSearch ? { q: debouncedSearch } : {}),
      ...(status !== ALL_VALUE ? { status } : {}),
      page: page - 1,
      size: pageSize,
      sort: sortParam,
    }),
    [debouncedSearch, status, page, pageSize, sortParam],
  );

  const classesQuery = useQuery({
    queryKey: CLASSES_QUERY_KEYS.list(queryParams),
    queryFn: () => deps.listClasses(queryParams),
    placeholderData: keepPreviousWhileSameScope(CLASSES_QUERY_KEYS.list(queryParams)),
  });

  // Dieu chinh trang trong render (thay cho effect): khi dang o trang >1 ma trang do
  // rong (vd sau khi xoa phan tu cuoi hoac trang vuot qua totalPages), lui ve trang
  // cuoi hop le ngay lap tuc thay vi cho effect.
  const classesData = classesQuery.data;
  const effectivePage =
    page > 1 &&
    classesData &&
    classesData.items.length === 0 &&
    classesData.totalItems > 0
      ? Math.max(1, classesData.totalPages)
      : page;
  if (effectivePage !== page) {
    setPage(effectivePage);
  }

  const hasActiveFilters = Boolean(debouncedSearch || status !== ALL_VALUE);

  function resetFilters() {
    setSearchInput("");
    setDebouncedSearch("");
    setStatus(ALL_VALUE);
    setPage(1);
  }

  const statusOptions = useMemo(
    () => [
      { value: ALL_VALUE, label: t("filters.all") },
      { value: "ACTIVE", label: t("statuses.ACTIVE") },
      { value: "INACTIVE", label: t("statuses.INACTIVE") },
    ],
    [t],
  );

  const canModify = can("CLASS.MODIFY");
  const openEdit = useCallback((item: ClassSummary) => {
    setEditingClass(item);
    setFormOpen(true);
  }, []);

  const columns = useMemo<ColumnDef<ClassSummary>[]>(
    () => [
      {
        accessorKey: "code",
        header: t("table.code"),
        cell: ({ row }) => (
          <Link
            href={`/classes/${row.original.id}`}
            className={cn(
              "rounded-sm font-semibold whitespace-nowrap text-primary-text hover:underline",
              focusRing,
            )}
          >
            {row.original.code}
          </Link>
        ),
      },
      {
        accessorKey: "name",
        header: t("table.name"),
        cell: ({ row }) => (
          <span className="line-clamp-2 min-w-36 font-medium text-text">
            {row.original.name}
          </span>
        ),
      },
      {
        id: "description",
        header: () => <span data-col="description">{t("table.description")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="description" className="line-clamp-2 min-w-48 text-text-secondary">
            {row.original.description ?? "—"}
          </span>
        ),
      },
      {
        id: "owner",
        header: () => <span data-col="owner">{t("table.owner")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="owner" className="whitespace-nowrap text-text">
            {row.original.ownerUsername ?? "—"}
          </span>
        ),
      },
      {
        id: "memberCount",
        header: () => <span data-col="memberCount">{t("table.memberCount")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="memberCount" className="tabular-nums text-text">
            {row.original.memberCount}
          </span>
        ),
      },
      {
        accessorKey: "status",
        header: t("table.status"),
        cell: ({ row }) => <ClassStatusChip status={row.original.status} />,
      },
      {
        id: "actions",
        header: () => <span className="block text-right">{t("table.actions")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex items-center justify-end gap-0.5 whitespace-nowrap">
            <IconButton
              icon={<Eye />}
              aria-label={t("actions.openAria", { code: row.original.code })}
              tooltip={t("actions.openTooltip")}
              onClick={() => router.push(`/classes/${row.original.id}`)}
            />
            {canModify && <ClassModifyActions item={row.original} onEdit={openEdit} controller={actionController} />}
          </div>
        ),
      },
    ],
    [t, canModify, router, actionController, openEdit],
  );

  /**
   * Khung hẹp (dưới 640px): mỗi lớp một thẻ. Mã lớp là tiêu đề dẫn tới chi tiết (cả thẻ bấm được, nên không cần nút
   * Xem), tên lớp ở dòng phụ, chip trạng thái, số học viên và người phụ trách ở dòng thông tin thêm; Sửa và "⋯" như
   * cột Thao tác.
   */
  const cards = useMemo<TableCards<ClassSummary>>(
    () => ({
      render: (item) => (
        <ListCard
          title={<ListCardTitleLink href={`/classes/${item.id}`}>{item.code}</ListCardTitleLink>}
          actions={
            canModify ? <ClassModifyActions item={item} onEdit={openEdit} controller={actionController} /> : undefined
          }
          caption={item.name}
          status={<ClassStatusChip status={item.status} />}
          meta={[
            { label: t("table.memberCount"), value: format.number(item.memberCount) },
            { label: t("table.owner"), value: item.ownerUsername ?? "—" },
          ]}
        />
      ),
      sortLabels: {
        label: common("table.sortBy"),
        ascending: common("table.ascending"),
        descending: common("table.descending"),
      },
    }),
    [t, common, format, canModify, actionController, openEdit],
  );

  const items = classesQuery.data?.items ?? [];
  const totalItems = classesQuery.data?.totalItems ?? 0;
  // Câu đọc cho trình đọc màn hình sau khi tìm/lọc xong (WCAG 4.1.3). Để rỗng trong lúc đang tải lại: khi kết
  // quả về, vùng status đổi từ rỗng sang có chữ nên được đọc, kể cả khi số lượng trùng với lần trước.
  const resultStatus =
    classesQuery.isLoading || classesQuery.isPlaceholderData || classesQuery.isError
      ? ""
      : t("list.resultStatus", { count: totalItems });

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title={t("list.title")}
        description={t("list.description")}
        actions={
          <Can permission="CLASS.MODIFY">
            <Button
              onClick={() => {
                setEditingClass(null);
                setFormOpen(true);
              }}
            >
              <Plus />
              <span>{t("actions.create")}</span>
            </Button>
          </Can>
        }
      />

      <Card padding="md" className="@container">
        <form
          aria-label={t("filters.ariaLabel")}
          onSubmit={(event) => event.preventDefault()}
          className={FILTER_FORM}
        >
          <FormField label={t("filters.searchLabel")} className={FILTER_SEARCH}>
            {(controlProps) => (
              <Input
                {...controlProps}
                type="search"
                leadingIcon={<Search />}
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                placeholder={t("filters.searchPlaceholder")}
              />
            )}
          </FormField>

          <FormField label={t("filters.statusLabel")} className={cn(FILTER_SELECT, "@3xl:min-w-40")}>
            {(controlProps) => (
              <Select
                {...controlProps}
                value={status}
                onValueChange={(value) => {
                  setStatus(value as StatusFilter);
                  setPage(1);
                }}
                options={statusOptions}
              />
            )}
          </FormField>

          {hasActiveFilters && (
            <div className={FILTER_TOGGLES}>
              <Button type="button" variant="secondary" size="sm" onClick={resetFilters}>
                {t("filters.clear")}
              </Button>
            </div>
          )}
        </form>
      </Card>

      <Card padding="none" className="overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3">
          <p className="text-body font-medium text-text tabular-nums">
            {classesQuery.isLoading
              ? t("list.loadingCount")
              : classesQuery.isError
                ? t("list.errorCount")
                : t("list.filteredCount", { count: totalItems })}
          </p>
          <LiveStatus message={resultStatus} />
        </div>

        <div className={DROP_LOW_PRIORITY_COLUMNS}>
        <Table
          aria-label={t("list.tableAriaLabel")}
          columns={columns}
          data={items}
          getRowId={(row) => row.id}
          loading={classesQuery.isLoading}
          refreshing={classesQuery.isPlaceholderData}
          sorting={{
            state: sorting,
            manual: true,
            onChange: (updater) => {
              setSorting(updater);
              setPage(1);
            },
          }}
          cards={cards}
          error={
            classesQuery.isError ? (
              <EmptyState
                icon={<TriangleAlert />}
                title={t("list.errorTitle")}
                description={t("list.errorDescription")}
                action={
                  <Button variant="secondary" onClick={() => void classesQuery.refetch()}>
                    {common("retry")}
                  </Button>
                }
              />
            ) : undefined
          }
          empty={
            hasActiveFilters ? (
              <EmptyState
                icon={<GraduationCap />}
                title={t("list.emptyFilteredTitle")}
                description={t("list.emptyFilteredDescription")}
                action={
                  <Button variant="secondary" onClick={resetFilters}>
                    {t("filters.clear")}
                  </Button>
                }
              />
            ) : (
              <EmptyState
                icon={<GraduationCap />}
                title={t("list.emptyTitle")}
                description={t("list.emptyDescription")}
                action={
                  <Can permission="CLASS.MODIFY">
                    <Button
                      onClick={() => {
                        setEditingClass(null);
                        setFormOpen(true);
                      }}
                    >
                      <Plus />
                      <span>{t("actions.create")}</span>
                    </Button>
                  </Can>
                }
              />
            )
          }
        />
        </div>

        {totalItems > 0 && (
          <div className="border-t border-border px-4 py-3">
            <Pagination
              page={effectivePage}
              pageSize={pageSize}
              total={totalItems}
              onPageChange={setPage}
              pageSizeOptions={PAGE_SIZE_OPTIONS}
              onPageSizeChange={(nextSize) => {
                setPageSize(nextSize);
                setPage(1);
              }}
              labels={{
                nav: t("list.paginationNav"),
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

        <p className="border-t border-border px-4 py-3 text-caption text-text-muted">
          {t("list.footnote")}
        </p>
      </Card>

      <ClassFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        initialClass={editingClass}
        createClass={deps.createClass}
        updateClass={deps.updateClass}
        listActiveUsers={listActiveUsers}
        onSaved={() => {
          void queryClient.invalidateQueries({ queryKey: CLASSES_QUERY_KEYS.all });
        }}
      />

      <ClassActionDialogs controller={actionController} />
    </div>
  );
}

function ClassStatusChip({ status }: { status: ClassStatus }) {
  const t = useTranslations("classes");
  return <StatusChip tone={status === "ACTIVE" ? "success" : "neutral"}>{t(`statuses.${status}`)}</StatusChip>;
}

/** Nút Sửa và menu "⋯" (ngừng/kích hoạt lại, xóa) của một lớp, dùng chung cho cột Thao tác và thẻ ở khung hẹp. */
function ClassModifyActions({
  item,
  onEdit,
  controller,
}: {
  item: ClassSummary;
  onEdit: (item: ClassSummary) => void;
  controller: ReturnType<typeof useClassActionController>;
}) {
  const t = useTranslations("classes");
  const target = { id: item.id, code: item.code, name: item.name, version: item.version };
  return (
    <>
      <IconButton
        icon={<Pencil />}
        aria-label={t("actions.editAria", { code: item.code })}
        tooltip={t("actions.editTooltip")}
        onClick={() => onEdit(item)}
      />
      <DropdownMenu>
        {/* C-21: IconButton của kit (vòng focus, cỡ, tooltip chung) thay cho nút tự viết. */}
        <DropdownMenuTrigger asChild>
          <IconButton icon={<MoreHorizontal />} aria-label={t("actions.moreAria", { code: item.code })} />
        </DropdownMenuTrigger>
        <DropdownMenuContent>
          {item.status === "ACTIVE" ? (
            <DropdownMenuItem onSelect={() => controller.openDeactivate(target)}>
              <PowerOff />
              <span>{t("actions.deactivate")}</span>
            </DropdownMenuItem>
          ) : (
            <DropdownMenuItem onSelect={() => controller.openActivate(target)}>
              <Power />
              <span>{t("actions.activate")}</span>
            </DropdownMenuItem>
          )}
          <DropdownMenuSeparator />
          <DropdownMenuItem destructive onSelect={() => controller.openDelete(target)}>
            <Trash2 />
            <span>{t("actions.delete")}</span>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </>
  );
}
