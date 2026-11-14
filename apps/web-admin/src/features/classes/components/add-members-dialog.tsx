"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import {
  Button,
  Checkbox,
  cn,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  EmptyState,
  Input,
  Label,
  Pagination,
  notify,
  Skeleton,
  StatusChip,
} from "@thi360/ui";
import { Search, Users } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useEffect, useMemo, useState } from "react";
import { usePermissions } from "@/features/session/session-provider";
import {
  listActiveRoles as defaultListActiveRoles,
  listUsers as defaultListUsers,
  type ListUsersQuery,
  type PageResponseUserSummary,
  type RoleSummary,
} from "@/features/users/api";
import {
  addClassMembers as defaultAddClassMembers,
  type AddClassMembersResponse,
} from "../api";
import { resolveClassesErrorMessage } from "../utils";

export const STUDENT_ROLE_ID = "00000000-0000-7000-8000-000000000004";
const ADD_CANDIDATES_PAGE_SIZE = 20;
const NO_MEMBER_IDS: readonly string[] = [];

export interface AddMembersDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  classId: string;
  classCode: string;
  onAdded: (result: AddClassMembersResponse) => void;
  addClassMembers?: typeof defaultAddClassMembers;
  listUsers?: (query: ListUsersQuery) => Promise<PageResponseUserSummary>;
  listActiveRoles?: () => Promise<RoleSummary[]>;
  /** Học viên đã là thành viên lớp theo dữ liệu đang tải ở trang chi tiết (không gọi thêm API); máy chủ vẫn là nguồn quyết định khi thêm. */
  existingMemberUserIds?: readonly string[];
}

export function AddMembersDialog({
  open,
  onOpenChange,
  classId,
  classCode,
  onAdded,
  addClassMembers = defaultAddClassMembers,
  listUsers = defaultListUsers,
  listActiveRoles = defaultListActiveRoles,
  existingMemberUserIds = NO_MEMBER_IDS,
}: AddMembersDialogProps) {
  const t = useTranslations("classes");
  const [submitting, setSubmitting] = useState(false);

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!submitting) onOpenChange(nextOpen);
      }}
    >
      <DialogContent
        size="lg"
        closeLabel={t("addMembersDialog.close")}
        className="flex max-h-[85vh] flex-col overflow-hidden"
      >
        <DialogHeader className="shrink-0">
          <DialogTitle>{t("addMembersDialog.title", { code: classCode })}</DialogTitle>
          <DialogDescription>{t("addMembersDialog.description")}</DialogDescription>
        </DialogHeader>

        {open && (
          <AddMembersBody
            classId={classId}
            onCancel={() => onOpenChange(false)}
            onSubmittingChange={setSubmitting}
            onAdded={(res) => {
              onOpenChange(false);
              onAdded(res);
            }}
            addClassMembers={addClassMembers}
            listUsers={listUsers}
            listActiveRoles={listActiveRoles}
            existingMemberUserIds={existingMemberUserIds}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function AddMembersBody({
  classId,
  onCancel,
  onSubmittingChange,
  onAdded,
  addClassMembers,
  listUsers,
  listActiveRoles,
  existingMemberUserIds,
}: {
  classId: string;
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onAdded: (result: AddClassMembersResponse) => void;
  addClassMembers: typeof defaultAddClassMembers;
  listUsers: (query: ListUsersQuery) => Promise<PageResponseUserSummary>;
  listActiveRoles: () => Promise<RoleSummary[]>;
  existingMemberUserIds: readonly string[];
}) {
  const t = useTranslations("classes");
  const common = useTranslations("common");
  const format = useFormatter();
  const { can } = usePermissions();
  const canViewUsers = can("USER.VIEW");
  const canViewRoles = can("ROLE.VIEW");

  const [searchInput, setSearchInput] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [page, setPage] = useState(1);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    const trimmed = searchInput.trim();
    if (trimmed === debouncedSearch) return;
    const timer = setTimeout(() => {
      setDebouncedSearch(trimmed);
      setPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchInput, debouncedSearch]);

  const rolesQuery = useQuery({
    queryKey: ["classes", "student-role"],
    queryFn: () => listActiveRoles(),
    enabled: canViewUsers && canViewRoles,
    staleTime: 60_000,
  });

  const studentRoleId = useMemo(() => {
    if (!canViewRoles) return STUDENT_ROLE_ID;
    return (
      rolesQuery.data?.find((role) => role.code.toUpperCase() === "STUDENT")?.id ??
      STUDENT_ROLE_ID
    );
  }, [canViewRoles, rolesQuery.data]);

  const usersQuery = useQuery({
    queryKey: ["classes", "add-member-candidates", debouncedSearch, studentRoleId, page],
    queryFn: () =>
      listUsers({
        ...(debouncedSearch ? { q: debouncedSearch } : {}),
        status: "ACTIVE",
        roleId: studentRoleId,
        page: page - 1,
        size: ADD_CANDIDATES_PAGE_SIZE,
        sort: "username,asc",
      }),
    enabled: canViewUsers && (!canViewRoles || !rolesQuery.isLoading),
  });

  const students = useMemo(() => {
    const items = usersQuery.data?.items ?? [];
    return items.filter(
      (user) =>
        !user.deletedAt &&
        user.status === "ACTIVE" &&
        (user.roles.length === 0 ||
          user.roles.some((role) => role.code.toUpperCase() === "STUDENT")),
    );
  }, [usersQuery.data]);
  const totalCandidates = usersQuery.data?.totalItems ?? 0;
  const selectedSet = useMemo(() => new Set(selectedIds), [selectedIds]);
  const memberIdSet = useMemo(() => new Set(existingMemberUserIds), [existingMemberUserIds]);

  const mutation = useMutation({
    mutationFn: () => {
      onSubmittingChange(true);
      return addClassMembers(classId, { userIds: selectedIds });
    },
    onSettled: () => {
      onSubmittingChange(false);
    },
    onSuccess: (res) => {
      if (res.addedCount > 0 && res.skippedCount > 0) {
        notify.success(
          t("addMembersDialog.partialSuccess", {
            added: res.addedCount,
            skipped: res.skippedCount,
          }),
        );
      } else if (res.addedCount === 0 && res.skippedCount > 0) {
        notify.info(t("addMembersDialog.allSkipped", { skipped: res.skippedCount }));
      } else {
        notify.success(t("addMembersDialog.success", { added: res.addedCount }));
      }
      onAdded(res);
    },
    onError: (error) => {
      const msg = resolveClassesErrorMessage(
        error,
        (code) => t(`errorCodes.${code}`),
        t("errorCodes.INTERNAL_ERROR"),
      );
      setSubmitError(msg);
      notify.error(msg);
    },
  });

  function toggleStudent(userId: string, checked: boolean) {
    setSubmitError(null);
    setSelectedIds((prev) =>
      checked
        ? prev.includes(userId)
          ? prev
          : [...prev, userId]
        : prev.filter((id) => id !== userId),
    );
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-3">
      {submitError && (
        <div
          role="alert"
          className="rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
        >
          {submitError}
        </div>
      )}

      {!canViewUsers ? (
        <div className="min-h-48 flex-1 rounded-lg border border-border">
          <EmptyState
            icon={<Users />}
            title={t("addMembersDialog.noPermissionTitle")}
            description={t("addMembersDialog.noPermissionDescription")}
          />
        </div>
      ) : (
        <>
          <Input
            type="search"
            leadingIcon={<Search />}
            aria-label={t("addMembersDialog.searchLabel")}
            placeholder={t("addMembersDialog.searchPlaceholder")}
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            disabled={mutation.isPending}
          />

          <div className="flex items-center justify-between text-caption text-text-muted">
            <span>{t("addMembersDialog.selectedCount", { count: selectedIds.length })}</span>
            {selectedIds.length > 0 && (
              <button
                type="button"
                className="font-medium text-primary-text hover:underline"
                onClick={() => setSelectedIds([])}
                disabled={mutation.isPending}
              >
                {t("addMembersDialog.clearSelection")}
              </button>
            )}
          </div>

          <div className="min-h-48 flex-1 overflow-y-auto rounded-lg border border-border">
            {usersQuery.isLoading ? (
              <div role="status" aria-label={common("loading")} className="flex flex-col gap-2 p-3">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            ) : usersQuery.isError ? (
              <EmptyState
                icon={<Users />}
                title={t("addMembersDialog.errorTitle")}
                description={t("addMembersDialog.errorDescription")}
                action={
                  <Button variant="secondary" size="sm" onClick={() => void usersQuery.refetch()}>
                    {common("retry")}
                  </Button>
                }
              />
            ) : students.length === 0 ? (
              <EmptyState
                icon={<Users />}
                title={t("addMembersDialog.emptyTitle")}
                description={t("addMembersDialog.emptyDescription")}
              />
            ) : (
              <ul
                className="divide-y divide-border"
                aria-label={t("addMembersDialog.listAriaLabel")}
              >
                {students.map((student) => {
                  const checkboxId = `add-student-${student.id}`;
                  const isMember = memberIdSet.has(student.id);
                  const checked = selectedSet.has(student.id);
                  return (
                    <li
                      key={student.id}
                      className="flex items-center gap-3 px-3 py-2.5 transition-colors hover:bg-subtle"
                    >
                      <Checkbox
                        id={checkboxId}
                        checked={checked}
                        onCheckedChange={(next) => toggleStudent(student.id, next === true)}
                        disabled={mutation.isPending || isMember}
                      />
                      <Label
                        htmlFor={checkboxId}
                        className={cn(
                          "flex min-w-0 flex-1 flex-col gap-0.5 font-normal",
                          isMember ? "cursor-default" : "cursor-pointer",
                        )}
                      >
                        <span className="truncate font-semibold text-text">{student.username}</span>
                        <span className="truncate text-caption text-text-muted">
                          {student.fullName}
                          {student.email ? ` · ${student.email}` : ""}
                        </span>
                      </Label>
                      {isMember && (
                        <StatusChip tone="neutral">{t("addMembersDialog.alreadyMember")}</StatusChip>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          {totalCandidates > ADD_CANDIDATES_PAGE_SIZE && (
            <Pagination
              page={page}
              pageSize={ADD_CANDIDATES_PAGE_SIZE}
              total={totalCandidates}
              onPageChange={setPage}
              labels={{
                nav: t("addMembersDialog.paginationNav"),
                previous: t("list.paginationPrevious"),
                next: t("list.paginationNext"),
                rowsPerPage: t("list.paginationRowsPerPage"),
                page: (pageNum) =>
                  t("list.paginationPage", { page: format.number(pageNum) }),
                range: ({ from, to, total }) =>
                  t("list.paginationRange", {
                    from: format.number(from),
                    to: format.number(to),
                    total: format.number(total),
                  }),
              }}
            />
          )}
        </>
      )}

      <DialogFooter className="shrink-0 pt-2">
        <Button
          type="button"
          variant="secondary"
          disabled={mutation.isPending}
          onClick={onCancel}
        >
          {t("addMembersDialog.cancel")}
        </Button>
        {canViewUsers && (
          <Button
            type="button"
            loading={mutation.isPending}
            disabled={selectedIds.length === 0}
            onClick={() => {
              setSubmitError(null);
              mutation.mutate();
            }}
          >
            {t("addMembersDialog.submit", { count: selectedIds.length })}
          </Button>
        )}
      </DialogFooter>
    </div>
  );
}
