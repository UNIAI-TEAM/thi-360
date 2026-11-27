"use client";

import { useQuery } from "@tanstack/react-query";
import { type ColumnDef } from "@tanstack/react-table";
import {
  Button,
  Card,
  Chip,
  cn,
  EmptyState,
  focusRing,
  focusRingInset,
  Input,
  ListCard,
  ListCardTag,
  ListCardTitleLink,
  Pagination,
  Select,
  Skeleton,
  StatusChip,
  Table,
  type TableCards,
} from "@thi360/ui";
import { Search, TriangleAlert, Users } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useMemo, useState } from "react";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
import {
  listAllRoles,
  listRoles as defaultListRoles,
  listUsers as defaultListUsers,
  rbacKeys,
  type ListRolesParams,
  type ListUsersParams,
} from "../api";
import { resolveRoleName, type RbacTranslator } from "../messages";
import { FILTER_GRID } from "../styles";
import type {
  PageResponseRoleSummaryResponse,
  PageResponseUserSummaryResponse,
  RoleSummaryResponse,
  UserStatus,
  UserSummaryResponse,
} from "../types";

/** Cùng ngưỡng với tab "Người dùng theo quyền": khung phải hẹp hơn 36rem thì bảng thành danh sách thẻ. */
const USERS_CARD_BREAKPOINT = 576;

export interface RoleUsersTabProps {
  /** Mã vai trò đang chọn trên URL (`?role=CODE`); không có hoặc không khớp thì chọn vai trò đầu tiên. */
  selectedRoleCode?: string | null;
  onSelectRole?: (roleCode: string) => void;
  listRoles?: (params?: ListRolesParams) => Promise<PageResponseRoleSummaryResponse>;
  listUsers?: (params?: ListUsersParams) => Promise<PageResponseUserSummaryResponse>;
}

/**
 * Tab "Người dùng theo vai trò" (UNI-2892): chọn một vai trò ở khung trái, khung phải liệt kê tài khoản đang giữ vai
 * trò đó. Bố cục hai khung giống tab "Người dùng theo quyền". Tên tài khoản dẫn tới trang chi tiết, tab Vai trò & quyền.
 */
export function RoleUsersTab({
  selectedRoleCode,
  onSelectRole,
  listRoles = defaultListRoles,
  listUsers = defaultListUsers,
}: RoleUsersTabProps) {
  const t = useTranslations("rbac");
  const commonT = useTranslations("common");
  const formatter = useFormatter();
  const rbacT: RbacTranslator = (key, values) => t(key, values);

  const [roleSearch, setRoleSearch] = useState("");
  const [accountSearch, setAccountSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<"ALL" | UserStatus>("ALL");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const rolesQuery = useQuery({
    queryKey: rbacKeys.allRoles(),
    queryFn: () => listAllRoles(listRoles),
  });

  const roleName = (role: Pick<RoleSummaryResponse, "code" | "name" | "system">) =>
    resolveRoleName(rbacT, role.code, role.name, role.system);

  const roles = useMemo(() => rolesQuery.data ?? [], [rolesQuery.data]);

  const filteredRoles = useMemo(() => {
    const query = roleSearch.trim().toLowerCase();
    if (!query) return roles;
    return roles.filter(
      (role) =>
        role.code.toLowerCase().includes(query) ||
        role.name.toLowerCase().includes(query) ||
        roleName(role).toLowerCase().includes(query),
    );
  }, [roleSearch, roles, t]); // eslint-disable-line react-hooks/exhaustive-deps

  const activeRole = useMemo(() => {
    if (roles.length === 0) return null;
    if (selectedRoleCode) {
      const matched = roles.find((role) => role.code.toUpperCase() === selectedRoleCode.toUpperCase());
      if (matched) return matched;
    }
    return roles[0]!;
  }, [roles, selectedRoleCode]);

  const userQueryParams: ListUsersParams = useMemo(
    () => ({
      ...(activeRole ? { roleId: activeRole.id } : {}),
      ...(statusFilter !== "ALL" ? { status: statusFilter } : {}),
      ...(accountSearch.trim() ? { q: accountSearch.trim() } : {}),
      page: page - 1,
      size: pageSize,
    }),
    [accountSearch, activeRole, page, pageSize, statusFilter],
  );

  const usersKey = activeRole ? rbacKeys.users(userQueryParams) : (["rbac", "users", "none"] as const);
  const usersQuery = useQuery({
    queryKey: usersKey,
    queryFn: () => listUsers(userQueryParams),
    placeholderData: keepPreviousWhileSameScope(usersKey),
    enabled: Boolean(activeRole),
  });

  const statusOptions = useMemo(
    () => [
      { value: "ALL", label: t("permissionUsers.filterStatusAll") },
      { value: "ACTIVE", label: t("permissionUsers.filterStatusActive") },
      { value: "INACTIVE", label: t("permissionUsers.filterStatusInactive") },
    ],
    [t],
  );

  const userHref = (user: UserSummaryResponse) => `/users/${user.id}?tab=roles`;

  const statusChip = (user: UserSummaryResponse) => (
    <StatusChip
      tone={user.status === "ACTIVE" ? "success" : user.status === "INACTIVE" ? "neutral" : "info"}
    >
      {t(`userStatuses.${user.status}`)}
    </StatusChip>
  );

  /** Các vai trò khác tài khoản đang giữ (bỏ vai trò đang xem). */
  const otherRoles = (user: UserSummaryResponse) =>
    user.roles.filter((role) => role.id !== activeRole?.id);

  const lastLogin = (user: UserSummaryResponse) =>
    user.lastLoginAt
      ? formatter.dateTime(new Date(user.lastLoginAt), {
          day: "2-digit",
          month: "2-digit",
          year: "numeric",
          hour: "2-digit",
          minute: "2-digit",
        })
      : t("roleUsers.neverLoggedIn");

  const columns = useMemo<ColumnDef<UserSummaryResponse>[]>(
    () => [
      {
        id: "account",
        header: t("roleUsers.columns.account"),
        enableSorting: false,
        cell: ({ row }) => {
          const u = row.original;
          return (
            <div className="py-2">
              <Link
                href={userHref(u)}
                className={cn(
                  "rounded-sm font-semibold wrap-anywhere text-primary-text hover:underline",
                  focusRing,
                )}
              >
                {u.username}
              </Link>
              <div className="text-caption wrap-break-word text-text-muted">
                {u.fullName} · {u.code}
              </div>
            </div>
          );
        },
      },
      {
        id: "otherRoles",
        header: t("roleUsers.columns.otherRoles"),
        enableSorting: false,
        cell: ({ row }) => {
          const others = otherRoles(row.original);
          return others.length === 0 ? (
            <span className="text-text-muted">—</span>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {others.map((role) => (
                <Chip key={role.id} variant="tag">
                  {resolveRoleName(rbacT, role.code, role.name)}
                </Chip>
              ))}
            </div>
          );
        },
      },
      {
        id: "lastLogin",
        header: t("roleUsers.columns.lastLogin"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text-secondary tabular-nums">{lastLogin(row.original)}</span>
        ),
      },
      {
        id: "status",
        header: t("roleUsers.columns.status"),
        enableSorting: false,
        cell: ({ row }) => statusChip(row.original),
      },
    ],
    [activeRole?.id, formatter, t], // eslint-disable-line react-hooks/exhaustive-deps
  );

  /** Dạng thẻ khi khung phải hẹp (DESIGN.md mục 5.9): tên đăng nhập là liên kết, họ tên và mã, trạng thái, vai trò khác. */
  const cards: TableCards<UserSummaryResponse> = {
    breakpoint: USERS_CARD_BREAKPOINT,
    render: (user) => {
      const others = otherRoles(user);
      return (
        <ListCard
          title={
            <ListCardTitleLink linkComponent={Link} href={userHref(user)}>
              {user.username}
            </ListCardTitleLink>
          }
          caption={`${user.fullName} · ${user.code}`}
          status={statusChip(user)}
          tags={
            others.length > 0 ? (
              <>
                {others.map((role) => (
                  <ListCardTag key={role.id}>{resolveRoleName(rbacT, role.code, role.name)}</ListCardTag>
                ))}
              </>
            ) : undefined
          }
        >
          <p className="text-caption text-text-muted tabular-nums">
            {t("roleUsers.lastLoginInline", { value: lastLogin(user) })}
          </p>
        </ListCard>
      );
    },
  };

  const users = usersQuery.data?.items ?? [];
  const totalUsers = usersQuery.data?.totalItems ?? 0;

  return (
    // Hai khung theo bề rộng vùng chứa như tab "Người dùng theo quyền" (`@4xl` đứng cạnh nhau, hẹp hơn thì xếp chồng).
    <div className="@container flex flex-wrap items-start gap-4">
      <Card
        padding="none"
        role="region"
        aria-labelledby="rbac-role-picker-heading"
        className="w-full overflow-hidden @4xl:w-[280px] @4xl:shrink-0 @5xl:w-[320px]"
      >
        <div className="flex flex-col gap-2.5 p-4">
          <h2 id="rbac-role-picker-heading" className="text-title font-semibold text-text">
            {t("roleUsers.pickerLabel")}
          </h2>
          <label className="flex flex-col gap-1.5 text-caption text-text-muted">
            <span>{t("roleUsers.pickerSearchLabel")}</span>
            <Input
              type="search"
              value={roleSearch}
              leadingIcon={<Search />}
              placeholder={t("roleUsers.pickerSearchPlaceholder")}
              onChange={(event) => setRoleSearch(event.target.value)}
            />
          </label>
        </div>

        {rolesQuery.isPending ? (
          <div role="status" aria-label={commonT("loading")} className="flex flex-col gap-2 p-4 pt-0">
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
          </div>
        ) : rolesQuery.isError ? (
          <div className="p-4 pt-0">
            <EmptyState
              icon={<TriangleAlert />}
              title={t("rolesTab.errorTitle")}
              description={t("rolesTab.errorDescription")}
              action={
                <Button variant="secondary" onClick={() => rolesQuery.refetch()}>
                  {t("actions.retry")}
                </Button>
              }
            />
          </div>
        ) : filteredRoles.length === 0 ? (
          <div className="p-4 pt-0">
            <EmptyState
              icon={<Search />}
              title={t("roleUsers.pickerEmptyTitle")}
              description={t("roleUsers.pickerEmptyDescription")}
            />
          </div>
        ) : (
          <ul className="scroll-shadows-y m-0 max-h-80 list-none overflow-y-auto p-0 @4xl:max-h-[640px]">
            {filteredRoles.map((role) => {
              const isSelected = activeRole?.id === role.id;
              return (
                <li key={role.id} className="border-t border-border">
                  <button
                    type="button"
                    aria-pressed={isSelected}
                    onClick={() => {
                      setPage(1);
                      onSelectRole?.(role.code);
                    }}
                    className={cn(
                      "flex min-h-[60px] w-full cursor-pointer items-center gap-2.5 px-4 py-2 text-left transition-colors duration-150",
                      focusRingInset,
                      isSelected ? "border-l-[3px] border-l-primary-600 bg-primary-50" : "hover:bg-subtle",
                    )}
                  >
                    <span className="min-w-0 flex-1">
                      <span className="line-clamp-2 font-semibold wrap-anywhere text-text">{roleName(role)}</span>
                      <span className="block text-caption wrap-anywhere text-text-muted">{role.code}</span>
                    </span>
                    <Chip variant="tag" className="shrink-0 tabular-nums">
                      {t("permissionUsers.userCountChip", { count: role.userCount })}
                    </Chip>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </Card>

      <Card
        padding="none"
        role="region"
        aria-labelledby="rbac-role-users-heading"
        className="@container min-w-0 flex-[1_1_100%] overflow-hidden @4xl:flex-1"
      >
        <div className="border-b border-border px-4 py-3.5">
          <h2 id="rbac-role-users-heading" className="text-title font-semibold wrap-anywhere text-text">
            {activeRole ? (
              <>
                {roleName(activeRole)} · <span className="font-normal text-text-muted">{activeRole.code}</span>
              </>
            ) : (
              t("tabs.role-users")
            )}
          </h2>
          {activeRole && (
            <p className="mt-0.5 text-caption text-text-muted">
              {t("roleUsers.summaryCount", { count: activeRole.userCount })}
            </p>
          )}
        </div>

        {/* Từ @xl (576px) ô tìm kiếm và ô trạng thái vừa một dòng; hẹp hơn thì xếp lưới (FILTER_GRID). */}
        <div className={cn(FILTER_GRID, "border-b border-border px-4 py-3 @xl:flex @xl:flex-wrap @xl:items-end")}>
          <label className="col-span-full flex flex-col gap-1.5 text-caption text-text-muted @xl:min-w-[200px] @xl:flex-[2_1_220px]">
            <span>{t("permissionUsers.filterSearchLabel")}</span>
            <Input
              type="search"
              value={accountSearch}
              leadingIcon={<Search />}
              placeholder={t("permissionUsers.filterAccountPlaceholder")}
              onChange={(event) => {
                setAccountSearch(event.target.value);
                setPage(1);
              }}
            />
          </label>
          <div className="flex min-w-0 flex-col gap-1.5 @xl:min-w-[150px] @xl:flex-[1_1_150px]">
            <span className="text-caption text-text-muted">{t("permissionUsers.filterStatusLabel")}</span>
            <Select
              aria-label={t("permissionUsers.filterStatusLabel")}
              value={statusFilter}
              options={statusOptions}
              onValueChange={(next) => {
                setStatusFilter(next as "ALL" | UserStatus);
                setPage(1);
              }}
            />
          </div>
        </div>

        <Table
          data={users}
          columns={columns}
          cards={cards}
          getRowId={(row) => row.id}
          loading={rolesQuery.isPending || (Boolean(activeRole) && usersQuery.isPending)}
          refreshing={usersQuery.isPlaceholderData}
          aria-label={t("tabs.role-users")}
          error={
            usersQuery.isError ? (
              <EmptyState
                icon={<TriangleAlert />}
                title={t("roleUsers.errorTitle")}
                description={t("roleUsers.errorDescription")}
                action={
                  <Button variant="secondary" onClick={() => usersQuery.refetch()}>
                    {t("actions.retry")}
                  </Button>
                }
              />
            ) : undefined
          }
          empty={
            <EmptyState
              icon={<Users />}
              title={t("roleUsers.emptyTitle")}
              description={t("roleUsers.emptyDescription")}
            />
          }
        />

        {totalUsers > 0 && (
          <div className="border-t border-border px-4 py-3">
            <Pagination
              page={page}
              pageSize={pageSize}
              total={totalUsers}
              onPageChange={setPage}
              onPageSizeChange={(nextSize) => {
                setPageSize(nextSize);
                setPage(1);
              }}
              labels={{
                nav: t("rolesTab.pagination.nav"),
                previous: t("rolesTab.pagination.previous"),
                next: t("rolesTab.pagination.next"),
                rowsPerPage: t("rolesTab.pagination.rowsPerPage"),
                page: (num) => t("rolesTab.pagination.page", { page: num }),
                range: ({ from, to, total: rangeTotal }) =>
                  t("rolesTab.pagination.range", { from, to, total: rangeTotal }),
              }}
            />
          </div>
        )}

        <div className="border-t border-border px-4 py-3 text-caption text-text-muted">
          {t("roleUsers.footnote")}
        </div>
      </Card>
    </div>
  );
}
