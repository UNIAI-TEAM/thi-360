"use client";

import type { ColumnDef, RowSelectionState, SortingState } from "@tanstack/react-table";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Card,
  Checkbox,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  EmptyState,
  FormField,
  Input,
  Label,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatusChip,
  Table,
  Tooltip,
} from "@thi360/ui";
import { Eye, KeyRound, MoreHorizontal, Plus, Power, PowerOff, Search, Trash2, Users } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useId, useMemo, useState } from "react";
import { CLASSES_QUERY_KEYS } from "@/features/classes/api";
import { Can } from "@/features/session/can";
import { ForbiddenView } from "@/features/session/forbidden-view";
import { usePermissions, useSession } from "@/features/session/session-provider";
import {
  defaultUsersApiDeps,
  type ListUsersQuery,
  type UsersApiDeps,
  type UserSummary,
  USERS_QUERY_KEYS,
} from "../api";
import { formatRoleLabel, getUserDisplayStatus } from "../utils";
import { CreateUserDialog } from "./create-user-dialog";
import { UserActionDialogs, useUserActionController } from "./user-action-dialogs";
import { UserBulkBar } from "./user-bulk-bar";

const ALL_VALUE = "ALL";
const DEFAULT_PAGE_SIZE = 20;
const PAGE_SIZE_OPTIONS = [10, 20, 50];

type SourceFilter = "ALL" | "ADMIN_CREATED" | "BULK_IMPORT" | "SELF_REGISTERED" | "GOOGLE";
type StatusFilter = "ALL" | "ACTIVE" | "INACTIVE";

export interface UsersListViewProps {
  deps?: Partial<UsersApiDeps>;
  /** Hien bo loc Lop hoc (mac dinh bat; can quyen CLASS.VIEW de xem). */
  showClassFilter?: boolean;
}

export function UsersListView({ deps, showClassFilter = true }: UsersListViewProps) {
  const sessionState = useSession();
  const { can } = usePermissions();

  if (sessionState.status === "loading") {
    return <UsersListSkeleton />;
  }

  if (sessionState.status === "ready" && !can("USER.VIEW")) {
    return <ForbiddenView />;
  }

  return (
    <UsersListContent
      deps={{ ...defaultUsersApiDeps, ...deps }}
      showClassFilter={showClassFilter}
    />
  );
}

function UsersListSkeleton() {
  const common = useTranslations("common");
  return (
    <div role="status" aria-label={common("loading")} className="flex flex-col gap-4">
      <Skeleton className="h-12 w-72" />
      <Skeleton className="h-20 w-full" />
      <Skeleton className="h-80 w-full" />
    </div>
  );
}

function UsersListContent({
  deps,
  showClassFilter,
}: {
  deps: UsersApiDeps;
  showClassFilter: boolean;
}) {
  const t = useTranslations("users");
  const rbacT = useTranslations("rbac");
  const common = useTranslations("common");
  const format = useFormatter();
  const { can } = usePermissions();
  const queryClient = useQueryClient();
  const neverLoggedInId = useId();
  const includeDeletedId = useId();

  const [searchInput, setSearchInput] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [source, setSource] = useState<SourceFilter>(ALL_VALUE);
  const [status, setStatus] = useState<StatusFilter>(ALL_VALUE);
  const [roleId, setRoleId] = useState<string>(ALL_VALUE);
  const [classId, setClassId] = useState<string>(ALL_VALUE);
  const [neverLoggedIn, setNeverLoggedIn] = useState(false);
  const [includeDeleted, setIncludeDeleted] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [sorting, setSorting] = useState<SortingState>([]);
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
  const [createOpen, setCreateOpen] = useState(false);

  const actionController = useUserActionController({
    deps,
    onMutated: () => {
      void queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEYS.lists() });
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

  const canViewRoles = can("ROLE.VIEW");
  const rolesQuery = useQuery({
    queryKey: USERS_QUERY_KEYS.roles(),
    queryFn: () => deps.listActiveRoles(),
    enabled: canViewRoles,
    staleTime: 60_000,
  });

  const canViewClasses = showClassFilter && can("CLASS.VIEW");
  const classesQuery = useQuery({
    queryKey: CLASSES_QUERY_KEYS.options(),
    queryFn: () => deps.listAllClasses(),
    enabled: canViewClasses,
    staleTime: 60_000,
  });

  const sortParam = useMemo(() => {
    const first = sorting[0];
    if (!first) return "createdAt,desc";
    return `${first.id},${first.desc ? "desc" : "asc"}`;
  }, [sorting]);

  const queryParams = useMemo<ListUsersQuery>(
    () => ({
      ...(debouncedSearch ? { q: debouncedSearch } : {}),
      ...(source !== ALL_VALUE ? { source } : {}),
      ...(status !== ALL_VALUE ? { status } : {}),
      ...(roleId !== ALL_VALUE ? { roleId } : {}),
      ...(canViewClasses && classId !== ALL_VALUE ? { classId } : {}),
      ...(neverLoggedIn ? { neverLoggedIn: true } : {}),
      ...(includeDeleted ? { includeDeleted: true } : {}),
      page: page - 1,
      size: pageSize,
      sort: sortParam,
    }),
    [debouncedSearch, source, status, roleId, canViewClasses, classId, neverLoggedIn, includeDeleted, page, pageSize, sortParam],
  );

  const usersQuery = useQuery({
    queryKey: USERS_QUERY_KEYS.list(queryParams),
    queryFn: () => deps.listUsers(queryParams),
  });

  const hasActiveFilters = Boolean(
    debouncedSearch ||
      source !== ALL_VALUE ||
      status !== ALL_VALUE ||
      roleId !== ALL_VALUE ||
      (canViewClasses && classId !== ALL_VALUE) ||
      neverLoggedIn ||
      includeDeleted,
  );

  function resetFilters() {
    setSearchInput("");
    setDebouncedSearch("");
    setSource(ALL_VALUE);
    setStatus(ALL_VALUE);
    setRoleId(ALL_VALUE);
    setClassId(ALL_VALUE);
    setNeverLoggedIn(false);
    setIncludeDeleted(false);
    setPage(1);
  }

  const sourceOptions = useMemo(
    () => [
      { value: ALL_VALUE, label: t("filters.all") },
      { value: "ADMIN_CREATED", label: t("sources.ADMIN_CREATED") },
      { value: "BULK_IMPORT", label: t("sources.BULK_IMPORT") },
      { value: "SELF_REGISTERED", label: t("sources.SELF_REGISTERED") },
      { value: "GOOGLE", label: t("sources.GOOGLE") },
    ],
    [t],
  );

  const statusOptions = useMemo(
    () => [
      { value: ALL_VALUE, label: t("filters.all") },
      { value: "ACTIVE", label: t("statuses.ACTIVE") },
      { value: "INACTIVE", label: t("statuses.INACTIVE") },
    ],
    [t],
  );

  const roleOptions = useMemo(
    () => [
      { value: ALL_VALUE, label: t("filters.all") },
      ...(rolesQuery.data ?? []).map((r) => ({
        value: r.id,
        label: formatRoleLabel(r, (code) => rbacT(`systemRoles.${code}`)),
      })),
    ],
    [rolesQuery.data, t, rbacT],
  );
  const classOptions = useMemo(
    () => [
      { value: ALL_VALUE, label: t("filters.all") },
      ...(classesQuery.data ?? []).map((cls) => ({
        value: cls.id,
        label: `${cls.code} — ${cls.name}`,
      })),
    ],
    [classesQuery.data, t],
  );


  const columns = useMemo<ColumnDef<UserSummary>[]>(
    () => [
      {
        accessorKey: "username",
        header: t("table.username"),
        cell: ({ row }) => (
          <Link
            href={`/tai-khoan/${row.original.id}`}
            className="whitespace-nowrap font-semibold text-primary-text hover:underline"
          >
            {row.original.username}
          </Link>
        ),
      },
      {
        accessorKey: "fullName",
        header: t("table.fullName"),
        cell: ({ row }) => (
          <span className="block max-w-44 truncate font-medium text-text" title={row.original.fullName}>
            {row.original.fullName}
          </span>
        ),
      },
      {
        id: "email",
        header: t("table.email"),
        enableSorting: false,
        cell: ({ row }) => (
          <span
            className="block max-w-52 truncate text-text-secondary"
            title={row.original.email ?? undefined}
          >
            {row.original.email ?? "—"}
          </span>
        ),
      },
      {
        id: "roles",
        header: t("table.roles"),
        enableSorting: false,
        cell: ({ row }) => {
          const roleText =
            row.original.roles.length > 0
              ? row.original.roles
                  .map((r) => formatRoleLabel(r, (code) => rbacT(`systemRoles.${code}`)))
                  .join(", ")
              : "—";
          return (
            <span className="block max-w-44 truncate text-text" title={roleText !== "—" ? roleText : undefined}>
              {roleText}
            </span>
          );
        },
      },
      {
        id: "source",
        header: t("table.source"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text-secondary">
            {t(`sources.${row.original.source}`)}
          </span>
        ),
      },
      {
        id: "status",
        header: t("table.status"),
        enableSorting: false,
        cell: ({ row }) => {
          const display = getUserDisplayStatus(row.original);
          return <StatusChip tone={display.tone}>{t(`statuses.${display.status}`)}</StatusChip>;
        },
      },
      ...(can("USER.MODIFY")
        ? [
            {
              id: "actions",
              header: () => <span className="block text-right">{t("table.actions")}</span>,
              enableSorting: false,
              cell: ({ row }: { row: { original: UserSummary } }) => {
                const item = row.original;
                const isDeleted = Boolean(item.deletedAt);
                return (
                  <div className="flex justify-end">
                    <DropdownMenu>
                      <Tooltip content={t("actions.rowMenu", { username: item.username })}>
                        <DropdownMenuTrigger asChild>
                          <button
                            type="button"
                            aria-label={t("actions.rowMenu", { username: item.username })}
                            className="inline-flex size-10 items-center justify-center rounded-lg text-text-secondary transition-colors duration-150 hover:bg-subtle focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600 motion-reduce:transition-none"
                          >
                            <MoreHorizontal aria-hidden className="size-[18px]" />
                          </button>
                        </DropdownMenuTrigger>
                      </Tooltip>
                      <DropdownMenuContent>
                        <DropdownMenuItem asChild>
                          <Link href={`/tai-khoan/${item.id}`}>
                            <Eye />
                            <span>{t("actions.viewDetail")}</span>
                          </Link>
                        </DropdownMenuItem>
                        {!isDeleted && (
                          <>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              onSelect={() =>
                                actionController.openResetPassword({
                                  id: item.id,
                                  username: item.username,
                                })
                              }
                            >
                              <KeyRound />
                              <span>{t("actions.resetPassword")}</span>
                            </DropdownMenuItem>
                            {item.status === "ACTIVE" ? (
                              <DropdownMenuItem
                                onSelect={() =>
                                  void actionController.openDeactivate({
                                    id: item.id,
                                    username: item.username,
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
                                    username: item.username,
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
                                void actionController.openDelete({
                                  id: item.id,
                                  username: item.username,
                                })
                              }
                            >
                              <Trash2 />
                              <span>{t("actions.delete")}</span>
                            </DropdownMenuItem>
                          </>
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                );
              },
            },
          ]
        : []),
    ],
    [t, rbacT, can, actionController],
  );

  const canBulkOperate = can("USER.MODIFY") || can("USER.ASSIGN");
  const selectedIds = useMemo(
    () => Object.keys(rowSelection).filter((id) => rowSelection[id]),
    [rowSelection],
  );

  const items = usersQuery.data?.items ?? [];
  const totalItems = usersQuery.data?.totalItems ?? 0;

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title={t("list.title")}
        description={t("list.description")}
        actions={
          <Can permission="USER.MODIFY">
            <Button onClick={() => setCreateOpen(true)}>
              <Plus />
              <span>{t("actions.create").replace(/^\+\s*/, "")}</span>
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

          <FormField label={t("filters.sourceLabel")} className="min-w-36 flex-1">
            {(controlProps) => (
              <Select
                {...controlProps}
                value={source}
                onValueChange={(value) => {
                  setSource(value as SourceFilter);
                  setPage(1);
                }}
                options={sourceOptions}
              />
            )}
          </FormField>

          <FormField label={t("filters.statusLabel")} className="min-w-36 flex-1">
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

          {canViewRoles && (
            <FormField label={t("filters.roleLabel")} className="min-w-40 flex-1">
              {(controlProps) => (
                <Select
                  {...controlProps}
                  value={roleId}
                  onValueChange={(value) => {
                    setRoleId(value);
                    setPage(1);
                  }}
                  options={roleOptions}
                />
              )}
            </FormField>
          )}

          {canViewClasses && (
            <FormField label={t("filters.classLabel")} className="min-w-44 flex-1">
              {(controlProps) => (
                <Select
                  {...controlProps}
                  value={classId}
                  onValueChange={(value) => {
                    setClassId(value);
                    setPage(1);
                  }}
                  options={classOptions}
                />
              )}
            </FormField>
          )}

          <div className="flex min-h-10 items-center gap-2">
            <Checkbox
              id={neverLoggedInId}
              checked={neverLoggedIn}
              onCheckedChange={(checked) => {
                setNeverLoggedIn(checked === true);
                setPage(1);
              }}
            />
            <Label htmlFor={neverLoggedInId} className="cursor-pointer font-normal text-text">
              {t("filters.neverLoggedIn")}
            </Label>
          </div>

          <div className="flex min-h-10 items-center gap-2">
            <Checkbox
              id={includeDeletedId}
              checked={includeDeleted}
              onCheckedChange={(checked) => {
                setIncludeDeleted(checked === true);
                setPage(1);
              }}
            />
            <Label htmlFor={includeDeletedId} className="cursor-pointer font-normal text-text">
              {t("filters.includeDeleted")}
            </Label>
          </div>

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
            {usersQuery.isLoading
              ? t("list.loadingCount")
              : usersQuery.isError
                ? t("list.errorTitle")
                : t("list.filteredCount", { count: format.number(totalItems) })}
          </p>
          <p className="text-caption text-text-muted">{t("list.hint")}</p>
        </div>

        {canBulkOperate && (
          <UserBulkBar
            selectedIds={selectedIds}
            onClearSelection={() => setRowSelection({})}
            onBulkApplied={() => {
              void queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEYS.lists() });
            }}
            previewBulkUserActions={deps.previewBulkUserActions}
            applyBulkUserActions={deps.applyBulkUserActions}
          />
        )}

        <Table
          aria-label={t("list.tableAriaLabel")}
          columns={columns}
          data={items}
          getRowId={(row) => row.id}
          loading={usersQuery.isLoading}
          selection={
            canBulkOperate
              ? {
                  state: rowSelection,
                  onChange: setRowSelection,
                  selectAllLabel: t("table.selectAll"),
                  rowLabel: (_index, row) =>
                    t("table.selectRow", { username: row.username }),
                }
              : undefined
          }
          sorting={{
            state: sorting,
            manual: true,
            onChange: (updater) => {
              setSorting(updater);
              setPage(1);
            },
          }}
          error={
            usersQuery.isError ? (
              <EmptyState
                icon={<Users />}
                title={t("list.errorTitle")}
                description={t("list.errorDescription")}
                action={
                  <Button variant="secondary" onClick={() => void usersQuery.refetch()}>
                    {common("retry")}
                  </Button>
                }
              />
            ) : undefined
          }
          empty={
            hasActiveFilters ? (
              <EmptyState
                icon={<Users />}
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
                icon={<Users />}
                title={t("list.emptyTitle")}
                description={t("list.emptyDescription")}
                action={
                  <Can permission="USER.MODIFY">
                    <Button onClick={() => setCreateOpen(true)}>
                      <Plus />
                      <span>{t("actions.create").replace(/^\+\s*/, "")}</span>
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
              page={page}
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
      </Card>

      <CreateUserDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        roles={rolesQuery.data ?? []}
        createUser={deps.createUser}
        checkUsernameAvailability={deps.checkUsernameAvailability}
        listActiveClasses={() => deps.listAllClasses("ACTIVE")}
        onCreated={() => {
          void queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEYS.lists() });
          void queryClient.invalidateQueries({ queryKey: CLASSES_QUERY_KEYS.all });
        }}
      />

      <UserActionDialogs controller={actionController} />
    </div>
  );
}
