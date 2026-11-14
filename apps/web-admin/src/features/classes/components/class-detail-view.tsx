"use client";

import type { ColumnDef, SortingState } from "@tanstack/react-table";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  IconButton,
  Input,
  notify,
  Pagination,
  Skeleton,
  StatusChip,
  Table,
} from "@thi360/ui";
import {
  ArrowLeft,
  ArrowLeftRight,
  GraduationCap,
  Pencil,
  Plus,
  Power,
  PowerOff,
  Search,
  Trash2,
  UserMinus,
  Users,
} from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Can } from "@/features/session/can";
import { ForbiddenView } from "@/features/session/forbidden-view";
import { usePermissions, useSession } from "@/features/session/session-provider";
import { useSetBreadcrumbLeaf } from "@/features/shell/breadcrumb-leaf";
import type { UserSummary } from "@/features/users/api";
import type { ListUsersQuery, PageResponseUserSummary, RoleSummary } from "@/features/users/api";
import { getErrorStatus } from "@/lib/api/query-client";
import {
  CLASSES_QUERY_KEYS,
  type ClassesApiDeps,
  type ClassMember,
  defaultClassesApiDeps,
  type ListClassMembersQuery,
} from "../api";
import { formatClassDate, resolveClassesErrorMessage } from "../utils";
import { AddMembersDialog } from "./add-members-dialog";
import {
  ClassActionDialogs,
  useClassActionController,
} from "./class-action-dialogs";
import { ClassFormDialog } from "./class-form-dialog";
import { TransferMemberDialog } from "./transfer-member-dialog";

const DEFAULT_PAGE_SIZE = 20;
const PAGE_SIZE_OPTIONS = [10, 20, 50];

export interface ClassDetailViewProps {
  classId: string;
  deps?: Partial<ClassesApiDeps>;
  listActiveUsers?: () => Promise<UserSummary[]>;
  listUsersForAdd?: (query: ListUsersQuery) => Promise<PageResponseUserSummary>;
  listActiveRolesForAdd?: () => Promise<RoleSummary[]>;
}

export function ClassDetailView({
  classId,
  deps,
  listActiveUsers,
  listUsersForAdd,
  listActiveRolesForAdd,
}: ClassDetailViewProps) {
  const sessionState = useSession();
  const { can } = usePermissions();

  if (sessionState.status === "loading") {
    return <ClassDetailSkeleton />;
  }

  if (sessionState.status === "ready" && !can("CLASS.VIEW")) {
    return <ForbiddenView />;
  }

  return (
    <ClassDetailContent
      classId={classId}
      deps={{ ...defaultClassesApiDeps, ...deps }}
      listActiveUsers={listActiveUsers}
      listUsersForAdd={listUsersForAdd}
      listActiveRolesForAdd={listActiveRolesForAdd}
    />
  );
}

function ClassDetailSkeleton() {
  const common = useTranslations("common");
  return (
    <div role="status" aria-label={common("loading")} className="flex flex-col gap-4">
      <Skeleton className="h-8 w-44" />
      <Skeleton className="h-28 w-full" />
      <Skeleton className="h-80 w-full" />
    </div>
  );
}

function ClassDetailContent({
  classId,
  deps,
  listActiveUsers,
  listUsersForAdd,
  listActiveRolesForAdd,
}: {
  classId: string;
  deps: ClassesApiDeps;
  listActiveUsers?: () => Promise<UserSummary[]>;
  listUsersForAdd?: (query: ListUsersQuery) => Promise<PageResponseUserSummary>;
  listActiveRolesForAdd?: () => Promise<RoleSummary[]>;
}) {
  const t = useTranslations("classes");
  const common = useTranslations("common");
  const format = useFormatter();
  const { can } = usePermissions();
  const router = useRouter();
  const queryClient = useQueryClient();

  const [editOpen, setEditOpen] = useState(false);
  const [addMembersOpen, setAddMembersOpen] = useState(false);
  const [transferTarget, setTransferTarget] = useState<ClassMember | null>(null);
  const [removeTarget, setRemoveTarget] = useState<ClassMember | null>(null);

  const [memberSearch, setMemberSearch] = useState("");
  const [debouncedMemberSearch, setDebouncedMemberSearch] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [sorting, setSorting] = useState<SortingState>([]);

  useEffect(() => {
    const trimmed = memberSearch.trim();
    if (trimmed === debouncedMemberSearch) return;
    const timer = setTimeout(() => {
      setDebouncedMemberSearch(trimmed);
      setPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [memberSearch, debouncedMemberSearch]);

  const classQuery = useQuery({
    queryKey: CLASSES_QUERY_KEYS.detail(classId),
    queryFn: () => deps.getClass(classId),
  });
  useSetBreadcrumbLeaf(classQuery.data?.code);

  const sortParam = useMemo(() => {
    const first = sorting[0];
    if (!first) return "joinedAt,desc";
    return `${first.id},${first.desc ? "desc" : "asc"}`;
  }, [sorting]);

  const membersQueryParams = useMemo<ListClassMembersQuery>(
    () => ({
      ...(debouncedMemberSearch ? { q: debouncedMemberSearch } : {}),
      page: page - 1,
      size: pageSize,
      sort: sortParam,
    }),
    [debouncedMemberSearch, page, pageSize, sortParam],
  );

  const membersQuery = useQuery({
    queryKey: CLASSES_QUERY_KEYS.members(classId, membersQueryParams),
    queryFn: () => deps.listClassMembers(classId, membersQueryParams),
    enabled: Boolean(classQuery.data),
  });

  // Điều chỉnh trang trong render (thay cho effect): khi đang ở trang >1 mà trang đó
  // rỗng (vd sau khi xóa/transfer phần tử cuối), lùi về trang cuối hợp lệ ngay lập tức.
  const membersData = membersQuery.data;
  const effectiveMembersPage =
    page > 1 &&
    membersData &&
    membersData.items.length === 0 &&
    membersData.totalItems > 0
      ? Math.max(1, membersData.totalPages)
      : page;
  if (effectiveMembersPage !== page) {
    setPage(effectiveMembersPage);
  }

  function refreshClassAndMembers() {
    void queryClient.invalidateQueries({ queryKey: CLASSES_QUERY_KEYS.detail(classId) });
    void queryClient.invalidateQueries({ queryKey: CLASSES_QUERY_KEYS.membersRoot(classId) });
    void queryClient.invalidateQueries({ queryKey: CLASSES_QUERY_KEYS.lists() });
    void queryClient.invalidateQueries({ queryKey: CLASSES_QUERY_KEYS.options() });
  }

  const actionController = useClassActionController({
    deps,
    onMutated: refreshClassAndMembers,
    onDeleted: () => {
      router.push("/lop-hoc");
    },
  });

  const removeMutation = useMutation({
    mutationFn: ({ userId, reason }: { userId: string; username: string; reason?: string }) =>
      deps.removeClassMember(classId, userId, reason),
    onSuccess: (_data, variables) => {
      notify.success(t("removeDialog.success", { username: variables.username }));
      setRemoveTarget(null);
      refreshClassAndMembers();
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

  const canModify = can("CLASS.MODIFY");

  const columns = useMemo<ColumnDef<ClassMember>[]>(
    () => [
      {
        accessorKey: "username",
        header: t("members.tableStudent"),
        cell: ({ row }) => (
          <div className="flex flex-col">
            <Link
              href={`/tai-khoan/${row.original.userId}`}
              className="whitespace-nowrap font-semibold text-primary-text hover:underline"
            >
              {row.original.username}
            </Link>
            <span className="text-caption text-text-muted">{row.original.fullName}</span>
          </div>
        ),
      },
      {
        accessorKey: "joinedAt",
        header: t("members.tableJoinedAt"),
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text-secondary">
            {formatClassDate(row.original.joinedAt)}
          </span>
        ),
      },
      {
        id: "userStatus",
        header: t("members.tableStatus"),
        enableSorting: false,
        cell: ({ row }) => (
          <StatusChip tone={row.original.userStatus === "ACTIVE" ? "success" : "danger"}>
            {row.original.userStatus === "ACTIVE"
              ? t("members.userStatusActive")
              : t("members.userStatusInactive")}
          </StatusChip>
        ),
      },
      ...(canModify
        ? [
            {
              id: "actions",
              header: () => <span className="block text-right">{t("members.tableActions")}</span>,
              enableSorting: false,
              cell: ({ row }: { row: { original: ClassMember } }) => {
                const member = row.original;
                return (
                  <div className="flex items-center justify-end gap-0.5 whitespace-nowrap">
                    <IconButton
                      icon={<ArrowLeftRight />}
                      aria-label={t("members.transferAria", { username: member.username })}
                      tooltip={t("members.transferTooltip")}
                      onClick={() => setTransferTarget(member)}
                    />
                    <IconButton
                      icon={<UserMinus />}
                      tone="danger"
                      aria-label={t("members.removeAria", { username: member.username })}
                      tooltip={t("members.removeTooltip")}
                      onClick={() => setRemoveTarget(member)}
                    />
                  </div>
                );
              },
            },
          ]
        : []),
    ],
    [t, canModify],
  );

  if (classQuery.isLoading) {
    return <ClassDetailSkeleton />;
  }

  if (classQuery.isError) {
    const isNotFound = getErrorStatus(classQuery.error) === 404;
    return (
      <div className="flex flex-col gap-4">
        <Link
          href="/lop-hoc"
          className="inline-flex items-center gap-1.5 self-start text-body font-medium text-primary-text hover:underline"
        >
          <ArrowLeft className="size-4" />
          <span>{t("detail.backToList")}</span>
        </Link>
        <Card>
          <EmptyState
            icon={<GraduationCap />}
            title={isNotFound ? t("detail.notFoundTitle") : t("detail.errorTitle")}
            description={isNotFound ? t("detail.notFoundDescription") : t("detail.errorDescription")}
            action={
              isNotFound ? (
                <Button asChild variant="secondary">
                  <Link href="/lop-hoc">{t("detail.backToList")}</Link>
                </Button>
              ) : (
                <Button variant="secondary" onClick={() => void classQuery.refetch()}>
                  {common("retry")}
                </Button>
              )
            }
          />
        </Card>
      </div>
    );
  }

  const classDetail = classQuery.data;
  if (!classDetail) {
    return null;
  }

  const isActive = classDetail.status === "ACTIVE";
  const members = membersQuery.data?.items ?? [];
  const totalMembers = membersQuery.data?.totalItems ?? 0;
  const badgeCount = membersQuery.data
    ? membersQuery.data.totalItems
    : debouncedMemberSearch
      ? null
      : classDetail.memberCount;

  return (
    <div className="flex flex-col gap-5">
      <Link
        href="/lop-hoc"
        className="inline-flex items-center gap-1.5 self-start text-body font-medium text-primary-text hover:underline"
      >
        <ArrowLeft className="size-4" />
        <span>{t("detail.backToList")}</span>
      </Link>

      <Card className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex min-w-0 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-page-title font-bold text-text">{classDetail.name}</h1>
            <StatusChip tone={isActive ? "success" : "neutral"}>
              {t(`statuses.${classDetail.status}`)}
            </StatusChip>
          </div>
          <p className="text-body text-text-secondary">
            {t("detail.subtitle", {
              code: classDetail.code,
              descriptionPart: classDetail.description ? `${classDetail.description} · ` : "",
              owner: classDetail.ownerUsername ?? "—",
              count: classDetail.memberCount,
            })}
          </p>
        </div>

        <Can permission="CLASS.MODIFY">
          <div className="flex flex-wrap items-center gap-2">
            <Button type="button" variant="secondary" onClick={() => setEditOpen(true)}>
              <Pencil />
              <span>{t("detail.editInfo")}</span>
            </Button>

            {isActive ? (
              <Button
                type="button"
                variant="secondary"
                onClick={() =>
                  actionController.openDeactivate({
                    id: classDetail.id,
                    code: classDetail.code,
                    name: classDetail.name,
                    version: classDetail.version,
                  })
                }
              >
                <PowerOff />
                <span>{t("actions.deactivate")}</span>
              </Button>
            ) : (
              <Button
                type="button"
                variant="secondary"
                onClick={() =>
                  actionController.openActivate({
                    id: classDetail.id,
                    code: classDetail.code,
                    name: classDetail.name,
                    version: classDetail.version,
                  })
                }
              >
                <Power />
                <span>{t("actions.activate")}</span>
              </Button>
            )}

            <Button
              type="button"
              variant="danger"
              onClick={() =>
                actionController.openDelete({
                  id: classDetail.id,
                  code: classDetail.code,
                  name: classDetail.name,
                  version: classDetail.version,
                })
              }
            >
              <Trash2 />
              <span>{t("actions.delete")}</span>
            </Button>

            <Button
              type="button"
              disabled={!isActive}
              title={!isActive ? t("detail.addDisabledTooltip") : undefined}
              onClick={() => setAddMembersOpen(true)}
            >
              <Plus />
              <span>{t("detail.addMembers")}</span>
            </Button>
          </div>
        </Can>
      </Card>

      {!isActive && (
        <div
          role="status"
          className="rounded-lg border border-border bg-neutral-bg px-4 py-3 text-body text-neutral"
        >
          {t("detail.inactiveBanner")}
        </div>
      )}

      <Card padding="none" className="overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3">
          <div className="flex items-center gap-2">
            <h2 className="text-title font-semibold text-text">{t("members.title")}</h2>
            {badgeCount !== null ? (
              <span className="rounded-full border border-border px-2.5 py-0.5 text-caption font-medium text-text-muted tabular-nums">
                {t("members.countBadge", { count: badgeCount })}
              </span>
            ) : (
              <Skeleton className="h-5 w-20 rounded-full" />
            )}
          </div>

          <div className="w-full sm:w-72">
            <Input
              type="search"
              leadingIcon={<Search />}
              aria-label={t("members.searchLabel")}
              placeholder={t("members.searchPlaceholder")}
              value={memberSearch}
              onChange={(event) => setMemberSearch(event.target.value)}
            />
          </div>
        </div>

        <Table
          aria-label={t("members.tableAriaLabel")}
          columns={columns}
          data={members}
          getRowId={(row) => row.userId}
          loading={membersQuery.isLoading}
          sorting={{
            state: sorting,
            manual: true,
            onChange: (updater) => {
              setSorting(updater);
              setPage(1);
            },
          }}
          error={
            membersQuery.isError ? (
              <EmptyState
                icon={<Users />}
                title={t("members.errorTitle")}
                description={t("members.errorDescription")}
                action={
                  <Button variant="secondary" onClick={() => void membersQuery.refetch()}>
                    {common("retry")}
                  </Button>
                }
              />
            ) : undefined
          }
          empty={
            debouncedMemberSearch ? (
              <EmptyState
                icon={<Users />}
                title={t("members.emptyFilteredTitle")}
                description={t("members.emptyFilteredDescription")}
                action={
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setMemberSearch("");
                      setDebouncedMemberSearch("");
                      setPage(1);
                    }}
                  >
                    {t("filters.clear")}
                  </Button>
                }
              />
            ) : (
              <EmptyState
                icon={<Users />}
                title={t("members.emptyTitle")}
                description={t("members.emptyDescription")}
                action={
                  canModify && isActive ? (
                    <Button onClick={() => setAddMembersOpen(true)}>
                      <Plus />
                      <span>{t("detail.addMembers")}</span>
                    </Button>
                  ) : undefined
                }
              />
            )
          }
        />

        {totalMembers > 0 && (
          <div className="border-t border-border px-4 py-3">
            <Pagination
              page={effectiveMembersPage}
              pageSize={pageSize}
              total={totalMembers}
              onPageChange={setPage}
              pageSizeOptions={PAGE_SIZE_OPTIONS}
              onPageSizeChange={(nextSize) => {
                setPageSize(nextSize);
                setPage(1);
              }}
              labels={{
                nav: t("members.paginationNav"),
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
          {t("members.footnote")}
        </p>
      </Card>

      <ClassFormDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        initialClass={classDetail}
        createClass={deps.createClass}
        updateClass={deps.updateClass}
        listActiveUsers={listActiveUsers}
        onSaved={refreshClassAndMembers}
      />

      <AddMembersDialog
        open={addMembersOpen}
        onOpenChange={setAddMembersOpen}
        classId={classDetail.id}
        classCode={classDetail.code}
        addClassMembers={deps.addClassMembers}
        listUsers={listUsersForAdd}
        listActiveRoles={listActiveRolesForAdd}
        onAdded={refreshClassAndMembers}
      />

      <TransferMemberDialog
        open={Boolean(transferTarget)}
        onOpenChange={(open) => {
          if (!open) setTransferTarget(null);
        }}
        sourceClassId={classDetail.id}
        sourceClassCode={classDetail.code}
        member={transferTarget}
        listAllClasses={deps.listAllClasses}
        transferClassMember={deps.transferClassMember}
        onTransferred={refreshClassAndMembers}
      />

      <ConfirmDialog
        open={Boolean(removeTarget)}
        onOpenChange={(open) => {
          if (!open) setRemoveTarget(null);
        }}
        title={t("removeDialog.title")}
        description={
          removeTarget
            ? t("removeDialog.description", {
                username: removeTarget.username,
                code: classDetail.code,
              })
            : null
        }
        reasonLabel={t("removeDialog.reasonLabel")}
        confirmLabel={t("removeDialog.confirm")}
        cancelLabel={t("removeDialog.cancel")}
        closeLabel={t("removeDialog.close")}
        tone="danger"
        loading={removeMutation.isPending}
        onConfirm={(reason) => {
          if (removeTarget) {
            removeMutation.mutate({
              userId: removeTarget.userId,
              username: removeTarget.username,
              ...(reason ? { reason } : {}),
            });
          }
        }}
      />

      <ClassActionDialogs controller={actionController} />
    </div>
  );
}
