"use client";

import type { ColumnDef, RowSelectionState, SortingState, Updater } from "@tanstack/react-table";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Card,
  Checkbox,
  cn,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  EmptyState,
  focusRing,
  FormField,
  Input,
  Label,
  ListCard,
  ListCardTag,
  ListCardTitleLink,
  LiveStatus,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatusChip,
  Table,
  type TableCards,
  Tooltip,
} from "@thi360/ui";
import {
  Eye,
  KeyRound,
  MoreHorizontal,
  Plus,
  Power,
  PowerOff,
  Search,
  Trash2,
  Upload,
  Users,
  WifiOff,
} from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useId, useMemo, useRef, useState } from "react";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
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
import { FILTER_FORM, FILTER_SEARCH, FILTER_SELECT, FILTER_TOGGLES } from "./list-filters";
import {
  type UserActionController,
  UserActionDialogs,
  useUserActionController,
} from "./user-action-dialogs";
import { UserBulkBar } from "./user-bulk-bar";

const ALL_VALUE = "ALL";
const DEFAULT_PAGE_SIZE = 20;
const PAGE_SIZE_OPTIONS = [10, 20, 50];

/**
 * Cột ưu tiên thấp tự ẩn theo bề rộng của khung chứa bảng (container query, không theo bề rộng màn hình vì
 * vùng nội dung hẹp hơn màn hình một sidebar): Nguồn ẩn trước (khung dưới 74rem, tức cửa sổ dưới khoảng 1500px,
 * để Email không bị ép xuống dòng giữa chữ), rồi tới Lớp, Vai trò, để cột Trạng thái còn nằm trong khung ở cửa sổ
 * 1024–1440px; tiếp theo là Email. Khung hẹp hơn 40rem thì bảng thành danh sách thẻ
 * (`cards` của `Table`), thẻ có đủ các thông tin này. Ô tiêu đề và ô dữ liệu của cột được nhận ra qua `data-col`
 * đặt trên nội dung do màn này vẽ, không phụ thuộc thứ tự cột.
 */
const DROP_LOW_PRIORITY_COLUMNS = [
  "@container",
  "@max-[74rem]:[&_:is(th,td):has([data-col=source])]:hidden",
  "@max-[63rem]:[&_:is(th,td):has([data-col=classes])]:hidden",
  "@max-[58rem]:[&_:is(th,td):has([data-col=roles])]:hidden",
  "@max-3xl:[&_:is(th,td):has([data-col=email])]:hidden",
].join(" ");

/** Mã các lớp hiện tại của tài khoản, cách nhau bằng dấu phẩy; không có lớp thì "—". */
function formatClassCodes(classes: UserSummary["classes"]): string {
  return classes.length > 0 ? classes.map((c) => c.code).join(", ") : "—";
}

/** Cột sắp xếp được và khóa nhãn của nó, dùng cho câu thông báo sau khi đổi sắp xếp. */
const SORTABLE_COLUMN_LABEL_KEYS = {
  username: "table.username",
  fullName: "table.fullName",
} as const;

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
  const searchInputRef = useRef<HTMLInputElement>(null);
  const tableAreaRef = useRef<HTMLDivElement>(null);

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
  // Tên đăng nhập của các dòng đang chọn, ghi lại lúc tích: dòng đã chọn có thể không còn ở trang đang hiện.
  const [selectedUsernames, setSelectedUsernames] = useState<Record<string, string>>({});
  const [createOpen, setCreateOpen] = useState(false);
  // Câu đang nằm trong vùng thông báo của danh sách (số kết quả, sắp xếp, số dòng đang chọn): chỉ một vùng, đổi nội dung.
  const [announcement, setAnnouncement] = useState("");
  const [announcedFilterKey, setAnnouncedFilterKey] = useState<string | null>(null);

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
    placeholderData: keepPreviousWhileSameScope(USERS_QUERY_KEYS.list(queryParams)),
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
    // Nút "Xóa bộ lọc" biến mất ngay sau khi bấm: đưa focus về ô tìm kiếm thay vì để rơi về <body>.
    searchInputRef.current?.focus();
  }

  /**
   * Sau "Bỏ chọn" thanh thao tác hàng loạt không còn nhận focus: đưa focus về ô chọn tất cả (điểm bắt đầu để chọn
   * lại): ở tiêu đề bảng, hoặc ở hàng trên danh sách thẻ khi khung hẹp, tức là ô chọn đầu tiên không nằm trong một
   * dòng hay một thẻ. Ô đó bị khóa hoặc không có khi trang đang hiện không có dòng nào thì về ô tìm kiếm.
   */
  function focusAfterClearSelection() {
    const checkboxes = tableAreaRef.current?.querySelectorAll<HTMLElement>("[role='checkbox']:not([disabled])");
    const selectAll = Array.from(checkboxes ?? []).find((checkbox) => !checkbox.closest("tbody, li"));
    (selectAll ?? searchInputRef.current)?.focus();
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
            href={`/users/${row.original.id}`}
            className={cn(
              "rounded-sm font-semibold whitespace-nowrap text-primary-text hover:underline",
              focusRing,
            )}
          >
            {row.original.username}
          </Link>
        ),
      },
      {
        accessorKey: "fullName",
        header: t("table.fullName"),
        cell: ({ row }) => (
          <span className="line-clamp-2 min-w-32 font-medium text-text">
            {row.original.fullName}
          </span>
        ),
      },
      {
        id: "email",
        header: () => <span data-col="email">{t("table.email")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="email" className="line-clamp-2 min-w-36 wrap-anywhere text-text-secondary">
            {row.original.email ?? "—"}
          </span>
        ),
      },
      {
        id: "roles",
        header: () => <span data-col="roles">{t("table.roles")}</span>,
        enableSorting: false,
        cell: ({ row }) => {
          const roleText =
            row.original.roles.length > 0
              ? row.original.roles
                  .map((r) => formatRoleLabel(r, (code) => rbacT(`systemRoles.${code}`)))
                  .join(", ")
              : "—";
          return (
            <span data-col="roles" className="line-clamp-2 min-w-24 text-text">
              {roleText}
            </span>
          );
        },
      },
      {
        id: "classes",
        header: () => <span data-col="classes">{t("table.classes")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="classes" className="line-clamp-2 min-w-16 text-text">
            {formatClassCodes(row.original.classes)}
          </span>
        ),
      },
      {
        id: "source",
        header: () => <span data-col="source">{t("table.source")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="source" className="whitespace-nowrap text-text-secondary">
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
          return (
            <StatusChip tone={display.tone}>
              {t(`statuses.${display.status}`)}
            </StatusChip>
          );
        },
      },
      ...(can("USER.MODIFY")
        ? [
            {
              id: "actions",
              header: () => <span className="block text-right">{t("table.actions")}</span>,
              enableSorting: false,
              cell: ({ row }: { row: { original: UserSummary } }) => (
                <div className="flex justify-end">
                  <UserRowActions item={row.original} controller={actionController} />
                </div>
              ),
            },
          ]
        : []),
    ],
    [t, rbacT, can, actionController],
  );

  /** Khung hẹp (dưới 640px): mỗi tài khoản là một thẻ, bố cục theo DESIGN.md mục 5.9. */
  const cards = useMemo<TableCards<UserSummary>>(
    () => ({
      render: (item) => {
        const display = getUserDisplayStatus(item);
        return (
          <ListCard
            title={
              <ListCardTitleLink linkComponent={Link} href={`/users/${item.id}`}>
                {item.username}
              </ListCardTitleLink>
            }
            actions={can("USER.MODIFY") ? <UserRowActions item={item} controller={actionController} /> : undefined}
            caption={item.fullName}
            status={<StatusChip tone={display.tone}>{t(`statuses.${display.status}`)}</StatusChip>}
            tags={
              <>
                {item.roles.map((role) => (
                  <ListCardTag key={role.id}>
                    {formatRoleLabel(role, (code) => rbacT(`systemRoles.${code}`))}
                  </ListCardTag>
                ))}
                <ListCardTag>{t(`sources.${item.source}`)}</ListCardTag>
              </>
            }
            meta={[
              Boolean(item.email) && { label: t("table.email"), value: item.email },
              item.classes.length > 0 && { label: t("table.classes"), value: formatClassCodes(item.classes) },
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
    [t, rbacT, common, can, actionController],
  );

  const canBulkOperate = can("USER.MODIFY") || can("USER.ASSIGN");
  const selectedAccounts = useMemo(
    () =>
      Object.keys(rowSelection)
        .filter((id) => rowSelection[id])
        .map((id) => ({ id, username: selectedUsernames[id] ?? id })),
    [rowSelection, selectedUsernames],
  );
  const hasSelection = canBulkOperate && selectedAccounts.length > 0;

  // Mất mạng: TanStack Query tạm dừng truy vấn (`fetchStatus` "paused") thay vì báo lỗi, nên các dòng giữ chỗ của
  // bộ lọc cũ (hoặc trạng thái rỗng ở lần tải đầu) sẽ đứng mãi. Khi đó báo mất kết nối thay cho dữ liệu sai bộ lọc.
  // Dữ liệu đã đúng bộ lọc hiện tại mà chỉ lượt tải lại nền bị dừng thì vẫn hiện bình thường.
  const isOfflineStale =
    usersQuery.fetchStatus === "paused" && (usersQuery.isPlaceholderData || !usersQuery.data);
  const items = isOfflineStale ? [] : (usersQuery.data?.items ?? []);
  const totalItems = isOfflineStale ? 0 : (usersQuery.data?.totalItems ?? 0);

  // Số kết quả chỉ được đọc khi dữ liệu của MỘT BỘ LỌC KHÁC vừa về (không đọc lần tải đầu, đổi trang hay sắp xếp).
  // Điều chỉnh state ngay trong render theo mẫu "adjusting state when props change" của React.
  const filterKey = JSON.stringify([
    debouncedSearch,
    source,
    status,
    roleId,
    canViewClasses ? classId : ALL_VALUE,
    neverLoggedIn,
    includeDeleted,
  ]);
  if (usersQuery.isSuccess && !usersQuery.isPlaceholderData && announcedFilterKey !== filterKey) {
    setAnnouncedFilterKey(filterKey);
    if (announcedFilterKey !== null) {
      setAnnouncement(t("list.filteredCount", { count: format.number(totalItems) }));
    }
  }

  function handleSelectionChange(updater: Updater<RowSelectionState>) {
    const next = typeof updater === "function" ? updater(rowSelection) : updater;
    setRowSelection(next);
    setSelectedUsernames((previous) => {
      const usernames: Record<string, string> = {};
      for (const id of Object.keys(next)) {
        if (!next[id]) continue;
        usernames[id] = previous[id] ?? items.find((item) => item.id === id)?.username ?? id;
      }
      return usernames;
    });
    const count = Object.values(next).filter(Boolean).length;
    setAnnouncement(
      count > 0
        ? t("bulk.selectedCount", { count: format.number(count) })
        : t("announce.selectionCleared"),
    );
  }

  function handleSortingChange(updater: Updater<SortingState>) {
    const next = typeof updater === "function" ? updater(sorting) : updater;
    setSorting(next);
    setPage(1);
    const first = next[0];
    const labelKey = first
      ? SORTABLE_COLUMN_LABEL_KEYS[first.id as keyof typeof SORTABLE_COLUMN_LABEL_KEYS]
      : undefined;
    setAnnouncement(
      first && labelKey
        ? t(first.desc ? "announce.sortedDesc" : "announce.sortedAsc", { column: t(labelKey) })
        : t("announce.sortCleared"),
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title={t("list.title")}
        description={t("list.description")}
        actions={
          <Can permission="USER.MODIFY">
            <Button asChild variant="secondary">
              <Link href="/users/bulk-import">
                <Upload />
                <span>{t("actions.bulkImport")}</span>
              </Link>
            </Button>
            <Button onClick={() => setCreateOpen(true)}>
              <Plus />
              <span>{t("actions.create").replace(/^\+\s*/, "")}</span>
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
                ref={searchInputRef}
                type="search"
                autoCapitalize="none"
                autoCorrect="off"
                spellCheck={false}
                leadingIcon={<Search />}
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                placeholder={t("filters.searchPlaceholder")}
              />
            )}
          </FormField>

          <FormField label={t("filters.sourceLabel")} className={cn(FILTER_SELECT, "@3xl:min-w-36")}>
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

          <FormField label={t("filters.statusLabel")} className={cn(FILTER_SELECT, "@3xl:min-w-36")}>
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
            <FormField label={t("filters.roleLabel")} className={cn(FILTER_SELECT, "@3xl:min-w-40")}>
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
            <FormField label={t("filters.classLabel")} className={cn(FILTER_SELECT, "@3xl:min-w-44")}>
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

          <div className={FILTER_TOGGLES}>
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
          </div>
        </form>
      </Card>

      <Card padding="none" className="overflow-hidden">
        <LiveStatus message={announcement} />
        {/*
          Một ô lưới cho hai lớp chồng nhau: dải "N tài khoản / gợi ý" và thanh thao tác hàng loạt. Ô luôn cao bằng
          lớp cao hơn, nên tích hay bỏ tích dòng chỉ đổi lớp đang hiện, bảng bên dưới không nhảy. Ô là vùng truy
          vấn bề rộng: ở khung hẹp thanh thao tác gọn lại còn hai hàng (xem `UserBulkBar`) để hai lớp cao xấp xỉ
          nhau, không để lại khoảng trống lớn phía trên bảng.
        */}
        <div
          className={cn(
            // `grid-cols-1` (= minmax(0, 1fr)): cột lưới không nở theo hàng nút không xuống dòng của thanh thao tác hàng
            // loạt ở khung hẹp, nên câu gợi ý xuống dòng trong thẻ thay vì bị thẻ cắt mất.
            "@container grid grid-cols-1 border-b",
            hasSelection ? "border-primary-200" : "border-border",
          )}
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
            {isOfflineStale
              ? t("list.offlineTitle")
              : usersQuery.isLoading
              ? t("list.loadingCount")
              : usersQuery.isError
                ? t("list.errorTitle")
                : t("list.filteredCount", { count: format.number(totalItems) })}
          </p>
          <p className="text-caption text-text-muted">{t("list.hint")}</p>
        </div>

        {canBulkOperate && (
          <UserBulkBar
            className="col-start-1 row-start-1 min-w-0"
            selected={selectedAccounts}
            onClearSelection={() => {
              handleSelectionChange({});
              focusAfterClearSelection();
            }}
            onBulkApplied={() => {
              void queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEYS.lists() });
            }}
            previewBulkUserActions={deps.previewBulkUserActions}
            applyBulkUserActions={deps.applyBulkUserActions}
          />
        )}
        </div>

        <div ref={tableAreaRef} className={DROP_LOW_PRIORITY_COLUMNS}>
        <Table
          aria-label={t("list.tableAriaLabel")}
          stickyColumns={{ start: true, end: true }}
          columns={columns}
          data={items}
          getRowId={(row) => row.id}
          loading={usersQuery.isLoading}
          refreshing={usersQuery.isPlaceholderData && !isOfflineStale}
          selection={
            canBulkOperate
              ? {
                  state: rowSelection,
                  onChange: handleSelectionChange,
                  selectAllLabel: t("table.selectAll"),
                  rowLabel: (_index, row) =>
                    t("table.selectRow", { username: row.username }),
                }
              : undefined
          }
          sorting={{
            state: sorting,
            manual: true,
            onChange: handleSortingChange,
          }}
          cards={cards}
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
            ) : isOfflineStale ? (
              <EmptyState
                icon={<WifiOff />}
                title={t("list.offlineTitle")}
                description={t("list.offlineDescription")}
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
        </div>

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

/** Nút "⋯" và menu thao tác của một tài khoản, dùng chung cho cột Thao tác của bảng và thẻ ở khung hẹp. */
function UserRowActions({ item, controller }: { item: UserSummary; controller: UserActionController }) {
  const t = useTranslations("users");
  const isDeleted = Boolean(item.deletedAt);
  const target = { id: item.id, username: item.username };
  return (
    <DropdownMenu>
      <Tooltip content={t("actions.rowMenu", { username: item.username })}>
        <DropdownMenuTrigger asChild>
          <button
            type="button"
            aria-label={t("actions.rowMenu", { username: item.username })}
            className={cn(
              "inline-flex size-10 items-center justify-center rounded-lg text-text-secondary transition-colors duration-150 hover:bg-subtle motion-reduce:transition-none",
              focusRing,
            )}
          >
            <MoreHorizontal aria-hidden className="size-[18px]" />
          </button>
        </DropdownMenuTrigger>
      </Tooltip>
      <DropdownMenuContent>
        <DropdownMenuItem asChild>
          <Link href={`/users/${item.id}`}>
            <Eye />
            <span>{t("actions.viewDetail")}</span>
          </Link>
        </DropdownMenuItem>
        {!isDeleted && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={() => controller.openResetPassword(target)}>
              <KeyRound />
              <span>{t("actions.resetPassword")}</span>
            </DropdownMenuItem>
            {item.status === "ACTIVE" ? (
              <DropdownMenuItem onSelect={() => void controller.openDeactivate(target)}>
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
            <DropdownMenuItem destructive onSelect={() => void controller.openDelete(target)}>
              <Trash2 />
              <span>{t("actions.delete")}</span>
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
