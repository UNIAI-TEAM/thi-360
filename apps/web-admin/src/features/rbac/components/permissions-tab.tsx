"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Card,
  EmptyState,
  Input,
  Select,
  Skeleton,
  StatusChip,
  cn,
  focusRing,
  useCardMode,
} from "@thi360/ui";
import { Info, KeyRound, Search, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useMemo, useState } from "react";
import { usePermissions } from "@/features/session/session-provider";
import { listPermissions as defaultListPermissions, rbacKeys } from "../api";
import {
  KNOWN_OBJECT_CODES,
  resolveObjectName,
  resolvePermissionName,
  resolvePermissionShortName,
  type RbacTranslator,
} from "../messages";
import { FILTER_GRID } from "../styles";
import type { AccessScope, PermissionResponse } from "../types";

export interface PermissionsTabProps {
  listPermissions?: () => Promise<PermissionResponse[]>;
  onSelectPermissionUsers?: (permissionCode: string) => void;
}

/**
 * Bề rộng vùng nội dung (px) dưới đó bảng quyền thành danh sách thẻ (DESIGN.md mục 5.9, UNI-2587). Bảng năm cột cần
 * 780px và đã cuộn ngang trong khung; dưới 672px (cùng ngưỡng `@2xl` của thanh lọc) cột Mã và Vai trò không còn chỗ,
 * nên mỗi quyền là một thẻ. Từ cửa sổ 1024px vùng nội dung rộng ít nhất 728px (1024 − 248 sidebar − 48 lề) nên vẫn
 * là bảng như trước; dưới 1024px là thẻ khi cửa sổ hẹp hơn khoảng 720px.
 */
const CARD_BREAKPOINT = 672;

interface PermissionGroupRows {
  objectCode: string;
  objectName: string;
  items: PermissionResponse[];
}

function getObjectRank(objectCode: string): number {
  const index = (KNOWN_OBJECT_CODES as readonly string[]).indexOf(objectCode);
  return index === -1 ? KNOWN_OBJECT_CODES.length : index;
}

export function PermissionsTab({
  listPermissions = defaultListPermissions,
  onSelectPermissionUsers,
}: PermissionsTabProps) {
  const t = useTranslations("rbac");
  const commonT = useTranslations("common");
  const rbacT: RbacTranslator = (key, values) => t(key, values);
  const { can } = usePermissions();

  const canOpenUsersTab = can("PERMISSION.VIEW") && can("USER.VIEW");

  const [search, setSearch] = useState("");
  const [objectFilter, setObjectFilter] = useState<string>("ALL");
  const [scopeFilter, setScopeFilter] = useState<"ALL" | AccessScope>("ALL");

  const permissionsQuery = useQuery({
    queryKey: rbacKeys.permissions(),
    queryFn: () => listPermissions(),
  });

  const objectOptions = useMemo(() => {
    const seen = new Map<string, string>();
    for (const code of KNOWN_OBJECT_CODES) {
      seen.set(code, resolveObjectName(rbacT, code));
    }
    for (const item of permissionsQuery.data ?? []) {
      if (!seen.has(item.groupCode)) {
        seen.set(item.groupCode, resolveObjectName(rbacT, item.groupCode, item.groupName));
      }
    }
    return [
      { value: "ALL", label: t("permissionsTab.filterObjectAll") },
      ...Array.from(seen.entries())
        .sort(([a], [b]) => getObjectRank(a) - getObjectRank(b) || a.localeCompare(b))
        .map(([value, label]) => ({ value, label })),
    ];
  }, [permissionsQuery.data, t]); // eslint-disable-line react-hooks/exhaustive-deps

  const scopeOptions = useMemo(
    () => [
      { value: "ALL", label: t("permissionsTab.filterScopeAll") },
      { value: "MANAGE", label: t("access.MANAGE") },
      { value: "EXAM", label: t("access.EXAM") },
    ],
    [t],
  );

  const groupedRows = useMemo<PermissionGroupRows[]>(() => {
    const items = permissionsQuery.data ?? [];
    const normalizedSearch = search.trim().toLowerCase();

    const filtered = items.filter((item) => {
      if (objectFilter !== "ALL" && item.groupCode !== objectFilter) return false;
      if (scopeFilter !== "ALL" && item.scope !== scopeFilter) return false;
      if (!normalizedSearch) return true;

      const objectName = resolveObjectName(rbacT, item.groupCode, item.groupName).toLowerCase();
      const permName = resolvePermissionName(rbacT, item.code, item.name).toLowerCase();
      const shortName = resolvePermissionShortName(rbacT, item.code, item.name).toLowerCase();
      return (
        item.code.toLowerCase().includes(normalizedSearch) ||
        item.name.toLowerCase().includes(normalizedSearch) ||
        objectName.includes(normalizedSearch) ||
        permName.includes(normalizedSearch) ||
        shortName.includes(normalizedSearch)
      );
    });

    const groups = new Map<string, PermissionGroupRows>();
    for (const item of filtered) {
      let group = groups.get(item.groupCode);
      if (!group) {
        group = {
          objectCode: item.groupCode,
          objectName: resolveObjectName(rbacT, item.groupCode, item.groupName),
          items: [],
        };
        groups.set(item.groupCode, group);
      }
      group.items.push(item);
    }

    return Array.from(groups.values())
      .sort(
        (a, b) =>
          getObjectRank(a.objectCode) - getObjectRank(b.objectCode) ||
          a.objectCode.localeCompare(b.objectCode),
      )
      .map((group) => ({
        ...group,
        items: [...group.items].sort((a, b) => a.bitIndex - b.bitIndex),
      }));
  }, [objectFilter, permissionsQuery.data, scopeFilter, search, t]); // eslint-disable-line react-hooks/exhaustive-deps

  const totalPermissions = groupedRows.reduce((sum, group) => sum + group.items.length, 0);
  // Đo vùng chứa của cả tab: luôn có mặt ở cả hai dạng (bảng, danh sách thẻ).
  const [containerRef, cardMode] = useCardMode<HTMLDivElement>(true, CARD_BREAKPOINT);

  return (
    // Vùng chứa cho thanh lọc và bảng: chia bố cục theo bề rộng vùng nội dung, không theo cửa sổ (DESIGN.md 5.13).
    <div ref={containerRef} className="@container flex flex-col gap-4">
      <div
        role="note"
        className="flex items-start gap-2.5 rounded-lg border border-info/30 bg-info-bg px-3.5 py-3 text-body text-info"
      >
        <Info aria-hidden="true" className="mt-0.5 size-[18px] shrink-0" />
        <span>{t("permissionsTab.bannerNote")}</span>
      </div>

      {/* Từ @2xl ô tìm kiếm và hai ô chọn vừa một dòng; hẹp hơn thì xếp lưới (FILTER_GRID). */}
      <Card
        padding="md"
        className={cn(FILTER_GRID, "@2xl:flex @2xl:flex-wrap @2xl:items-end")}
      >
        <label className="col-span-full flex flex-col gap-1.5 text-caption text-text-muted @2xl:min-w-[220px] @2xl:flex-[2_1_240px]">
          <span>{t("permissionsTab.filterSearchLabel")}</span>
          <Input
            type="search"
            value={search}
            leadingIcon={<Search />}
            placeholder={t("permissionsTab.filterSearchPlaceholder")}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>

        <div className="flex min-w-0 flex-col gap-1.5 @2xl:min-w-[160px] @2xl:flex-[1_1_160px]">
          <span className="text-caption text-text-muted">{t("permissionsTab.filterObjectLabel")}</span>
          <Select
            aria-label={t("permissionsTab.filterObjectLabel")}
            value={objectFilter}
            options={objectOptions}
            onValueChange={setObjectFilter}
          />
        </div>

        <div className="flex min-w-0 flex-col gap-1.5 @2xl:min-w-[160px] @2xl:flex-[1_1_160px]">
          <span className="text-caption text-text-muted">{t("permissionsTab.filterScopeLabel")}</span>
          <Select
            aria-label={t("permissionsTab.filterScopeLabel")}
            value={scopeFilter}
            options={scopeOptions}
            onValueChange={(next) => setScopeFilter(next as "ALL" | AccessScope)}
          />
        </div>
      </Card>

      {permissionsQuery.isPending ? (
        <Card padding="md" className="flex flex-col gap-3">
          <div role="status" aria-label={commonT("loading")} className="flex flex-col gap-3">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        </Card>
      ) : permissionsQuery.isError ? (
        <Card padding="md">
          <EmptyState
            icon={<TriangleAlert />}
            title={t("permissionsTab.errorTitle")}
            description={t("permissionsTab.errorDescription")}
            action={
              <Button variant="secondary" onClick={() => permissionsQuery.refetch()}>
                {t("actions.retry")}
              </Button>
            }
          />
        </Card>
      ) : groupedRows.length === 0 ? (
        <Card padding="md">
          <EmptyState
            icon={<KeyRound />}
            title={t("permissionsTab.emptyTitle")}
            description={t("permissionsTab.emptyDescription")}
          />
        </Card>
      ) : (
        <Card padding="none" className="overflow-hidden">
          {cardMode ? (
            groupedRows.map((group) => (
              <PermissionGroupCards
                key={group.objectCode}
                group={group}
                rbacT={rbacT}
                t={t}
                canOpenUsersTab={canOpenUsersTab}
                onSelectPermissionUsers={onSelectPermissionUsers}
              />
            ))
          ) : (
            // Bảng năm cột, rộng tối thiểu 780px và cuộn ngang trong khung khi vùng nội dung hẹp hơn.
            <div className="overflow-x-auto">
              <table className="w-full min-w-[780px] border-collapse">
                <thead>
                  <tr className="bg-table-head text-left text-field-label text-text-muted">
                    <th scope="col" className="px-4 py-3 font-semibold">
                      {t("permissionsTab.columns.name")}
                    </th>
                    <th scope="col" className="px-3 py-3 font-semibold">
                      {t("permissionsTab.columns.code")}
                    </th>
                    <th scope="col" className="px-3 py-3 font-semibold">
                      {t("permissionsTab.columns.scope")}
                    </th>
                    <th scope="col" className="px-3 py-3 font-semibold">
                      {t("permissionsTab.columns.roles")}
                    </th>
                    <th scope="col" className="px-4 py-3 font-semibold">
                      {t("permissionsTab.columns.users")}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {groupedRows.map((group) => (
                    <PermissionGroupSection
                      key={group.objectCode}
                      group={group}
                      rbacT={rbacT}
                      t={t}
                      canOpenUsersTab={canOpenUsersTab}
                      onSelectPermissionUsers={onSelectPermissionUsers}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="border-t border-border px-4 py-3 text-caption text-text-muted">
            {t("permissionsTab.footerSummary", {
              permissionCount: totalPermissions,
              objectCount: groupedRows.length,
            })}
          </div>
        </Card>
      )}
    </div>
  );
}

type PermissionGroupProps = {
  group: PermissionGroupRows;
  rbacT: RbacTranslator;
  t: ReturnType<typeof useTranslations<"rbac">>;
  canOpenUsersTab: boolean;
  onSelectPermissionUsers?: (permissionCode: string) => void;
};

/** Tên hiện của một quyền: mức (Xem, Sửa) giữ nguyên, quyền riêng có tiền tố "Quyền riêng:". */
function permissionDisplayName(
  item: PermissionResponse,
  rbacT: RbacTranslator,
  t: PermissionGroupProps["t"],
): string {
  const isLevel = item.code.endsWith(".VIEW") || item.code.endsWith(".MODIFY");
  const baseName = resolvePermissionName(rbacT, item.code, item.name);
  return isLevel ? baseName : t("permissionsTab.extraPrefix", { name: baseName });
}

/** Số tài khoản đang có quyền: nút mở tab Người dùng theo quyền khi được xem, không thì chỉ là chữ. */
function UserCount({
  item,
  rbacT,
  t,
  canOpenUsersTab,
  onSelectPermissionUsers,
}: Omit<PermissionGroupProps, "group"> & { item: PermissionResponse }) {
  const shortName = resolvePermissionShortName(rbacT, item.code, item.name);
  return canOpenUsersTab && onSelectPermissionUsers ? (
    <button
      type="button"
      aria-label={t("permissionsTab.userCountAria", { name: shortName })}
      onClick={() => onSelectPermissionUsers(item.code)}
      className={cn(
        "inline-flex min-h-11 cursor-pointer items-center font-semibold text-primary-text tabular-nums hover:underline focus-visible:rounded-sm",
        focusRing,
      )}
    >
      {t("permissionsTab.userCountValue", { count: item.userCount })}
    </button>
  ) : (
    <span className="font-medium text-text-secondary tabular-nums">
      {t("permissionsTab.userCountValue", { count: item.userCount })}
    </span>
  );
}

/**
 * Một nhóm quyền ở dạng thẻ (DESIGN.md mục 5.9): tiêu đề nhóm là `<h2>` đặt tên cho danh sách của nhóm; mỗi quyền
 * một thẻ gồm tên mức, mã (xuống dòng ở bất kỳ ký tự nào), chip phạm vi, rồi số vai trò và số tài khoản.
 */
function PermissionGroupCards({ group, ...props }: PermissionGroupProps) {
  const { rbacT, t } = props;
  const headingId = useId();
  return (
    <div className="border-b border-border last:border-b-0">
      <h2
        id={headingId}
        className="bg-page px-4 py-2 text-field-label font-semibold text-text-secondary"
      >
        {group.objectName}
      </h2>
      <ul
        // Tailwind bỏ dấu đầu dòng làm Safari/VoiceOver không coi là danh sách: ghi rõ vai trò (như `Table`).
        role="list"
        aria-labelledby={headingId}
        className="divide-y divide-border border-t border-border"
      >
        {group.items.map((item) => (
          <li key={item.id} className="relative flex items-start gap-3 px-4 py-3">
            <div className="min-w-0 flex-1">
              <p className="font-semibold wrap-break-word text-text">
                {permissionDisplayName(item, rbacT, t)}
              </p>
              <code className="block text-caption wrap-anywhere text-text-muted">{item.code}</code>
              <div className="mt-2 flex flex-wrap items-center gap-1.5">
                <StatusChip tone={item.scope === "MANAGE" ? "info" : "warning"}>
                  {t(`access.${item.scope}`)}
                </StatusChip>
              </div>
              <div className="mt-0.5 flex flex-wrap items-center gap-x-1.5 text-caption text-text-muted">
                <span className="tabular-nums">
                  {t("permissionsTab.roleCountValue", { count: item.roleCount })}
                </span>
                <span aria-hidden="true">·</span>
                <UserCount item={item} {...props} />
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}

function PermissionGroupSection({ group, ...props }: PermissionGroupProps) {
  const { rbacT, t } = props;
  return (
    <>
      <tr className="border-t border-border bg-page">
        <th
          scope="colgroup"
          colSpan={5}
          className="px-4 py-2 text-left text-field-label font-semibold text-text-secondary"
        >
          {group.objectName}
        </th>
      </tr>
      {group.items.map((item) => {
        const displayName = permissionDisplayName(item, rbacT, t);

        return (
          <tr key={item.id} className="h-12 border-t border-border bg-surface">
            <td className="px-4 py-2 font-medium text-text">
              <span className="wrap-break-word">{displayName}</span>
            </td>
            <td className="px-3 py-2">
              <code className="text-field-label text-text-muted">{item.code}</code>
            </td>
            <td className="px-3 py-2">
              <StatusChip tone={item.scope === "MANAGE" ? "info" : "warning"}>
                {t(`access.${item.scope}`)}
              </StatusChip>
            </td>
            <td className="px-3 py-2 text-text-secondary tabular-nums">
              {item.roleCount > 0
                ? t("permissionsTab.roleCountValue", { count: item.roleCount })
                : "—"}
            </td>
            <td className="px-4 py-2">
              <UserCount item={item} {...props} />
            </td>
          </tr>
        );
      })}
    </>
  );
}
