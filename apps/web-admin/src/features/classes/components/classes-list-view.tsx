"use client";

import type { ColumnDef, SortingState } from "@tanstack/react-table";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Card,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  EmptyState,
  FormField,
  IconButton,
  Input,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatusChip,
  Table,
  Tooltip,
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
import { useEffect, useMemo, useState } from "react";
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
import { type ClassEditableFields, ClassFormDialog } from "./class-form-dialog";

const ALL_VALUE = "ALL";
const DEFAULT_PAGE_SIZE = 20;
const PAGE_SIZE_OPTIONS = [10, 20, 50];

type StatusFilter = "ALL" | ClassStatus;

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

  const columns = useMemo<ColumnDef<ClassSummary>[]>(
    () => [
      {
        accessorKey: "code",
        header: t("table.code"),
        cell: ({ row }) => (
          <Link
            href={`/lop-hoc/${row.original.id}`}
            className="whitespace-nowrap font-semibold text-primary-text hover:underline"
          >
            {row.original.code}
          </Link>
        ),
      },
      {
        accessorKey: "name",
        header: t("table.name"),
        cell: ({ row }) => (
          <span className="block max-w-52 truncate font-medium text-text" title={row.original.name}>
            {row.original.name}
          </span>
        ),
      },
      {
        id: "description",
        header: t("table.description"),
        enableSorting: false,
        cell: ({ row }) => (
          <span
            className="block max-w-64 truncate text-text-secondary"
            title={row.original.description ?? undefined}
          >
            {row.original.description ?? "—"}
          </span>
        ),
      },
      {
        id: "owner",
        header: t("table.owner"),
        enableSorting: false,
        cell: ({ row }) => (
          <span
            className="whitespace-nowrap text-text"
            title={row.original.ownerFullName ?? undefined}
          >
            {row.original.ownerUsername ?? "—"}
          </span>
        ),
      },
      {
        id: "memberCount",
        header: t("table.memberCount"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="tabular-nums text-text">{row.original.memberCount}</span>
        ),
      },
      {
        accessorKey: "status",
        header: t("table.status"),
        cell: ({ row }) => (
          <StatusChip tone={row.original.status === "ACTIVE" ? "success" : "neutral"}>
            {t(`statuses.${row.original.status}`)}
          </StatusChip>
        ),
      },
      {
        id: "actions",
        header: () => <span className="block text-right">{t("table.actions")}</span>,
        enableSorting: false,
        cell: ({ row }) => {
          const item = row.original;
          return (
            <div className="flex items-center justify-end gap-0.5 whitespace-nowrap">
              <IconButton
                icon={<Eye />}
                aria-label={t("actions.openAria", { code: item.code })}
                tooltip={t("actions.openTooltip")}
                onClick={() => router.push(`/lop-hoc/${item.id}`)}
              />
              {canModify && (
                <>
                  <IconButton
                    icon={<Pencil />}
                    aria-label={t("actions.editAria", { code: item.code })}
                    tooltip={t("actions.editTooltip")}
                    onClick={() => {
                      setEditingClass(item);
                      setFormOpen(true);
                    }}
                  />
                  <DropdownMenu>
                    <Tooltip content={t("actions.moreAria", { code: item.code })}>
                      <DropdownMenuTrigger asChild>
                        <button
                          type="button"
                          aria-label={t("actions.moreAria", { code: item.code })}
                          className="inline-flex size-10 items-center justify-center rounded-lg text-text-secondary transition-colors duration-150 hover:bg-subtle focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600 motion-reduce:transition-none"
                        >
                          <MoreHorizontal aria-hidden className="size-[18px]" />
                        </button>
                      </DropdownMenuTrigger>
                    </Tooltip>
                    <DropdownMenuContent>
                      {item.status === "ACTIVE" ? (
                        <DropdownMenuItem
                          onSelect={() =>
                            actionController.openDeactivate({
                              id: item.id,
                              code: item.code,
                              name: item.name,
                              version: item.version,
                            })
                          }
                        >
                          <PowerOff />
                          <span>{t("actions.deactivate")}</span>
                        </DropdownMenuItem>
                      ) : (
                        <DropdownMenuItem
                          onSelect={() =>
                            actionController.openActivate({
                              id: item.id,
                              code: item.code,
                              name: item.name,
                              version: item.version,
                            })
                          }
                        >
                          <Power />
                          <span>{t("actions.activate")}</span>
                        </DropdownMenuItem>
                      )}
                      <DropdownMenuSeparator />
                      <DropdownMenuItem
                        destructive
                        onSelect={() =>
                          actionController.openDelete({
                            id: item.id,
                            code: item.code,
                            name: item.name,
                            version: item.version,
                          })
                        }
                      >
                        <Trash2 />
                        <span>{t("actions.delete")}</span>
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </>
              )}
            </div>
          );
        },
      },
    ],
    [t, canModify, router, actionController],
  );

  const items = classesQuery.data?.items ?? [];
  const totalItems = classesQuery.data?.totalItems ?? 0;

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

      <Card padding="md">
        <form
          aria-label={t("filters.ariaLabel")}
          onSubmit={(event) => event.preventDefault()}
          className="flex flex-wrap items-end gap-3"
        >
          <FormField label={t("filters.searchLabel")} className="min-w-60 flex-[2_1_240px]">
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

          <FormField label={t("filters.statusLabel")} className="min-w-40 flex-1">
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
            <Button type="button" variant="secondary" size="sm" onClick={resetFilters}>
              {t("filters.clear")}
            </Button>
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
        </div>

        <Table
          aria-label={t("list.tableAriaLabel")}
          columns={columns}
          data={items}
          getRowId={(row) => row.id}
          loading={classesQuery.isLoading}
          sorting={{
            state: sorting,
            manual: true,
            onChange: (updater) => {
              setSorting(updater);
              setPage(1);
            },
          }}
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
