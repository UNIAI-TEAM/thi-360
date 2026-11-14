"use client";

import { Button, cn, EmptyState, IconButton, Input, Skeleton } from "@thi360/ui";
import { GitBranch, Link2, List, Lock, Plus, Search, TriangleAlert } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import type { CatalogGroupKind, CatalogGroupResponse } from "../api";
import { pickName } from "../utils";

export type GroupSidebarProps = {
  groups: CatalogGroupResponse[];
  selectedGroupId: string | null;
  loading: boolean;
  error: boolean;
  canModify: boolean;
  onRetry: () => void;
  onSelectGroup: (group: CatalogGroupResponse) => void;
  onAddGroup: () => void;
};

function groupKindIcon(kind: CatalogGroupKind) {
  switch (kind) {
    case "COMBINATION":
      return Link2;
    case "TREE":
      return GitBranch;
    default:
      return List;
  }
}

export function GroupSidebar({
  groups,
  selectedGroupId,
  loading,
  error,
  canModify,
  onRetry,
  onSelectGroup,
  onAddGroup,
}: GroupSidebarProps) {
  const t = useTranslations("catalog");
  const commonT = useTranslations("common");
  const locale = useLocale();
  const [search, setSearch] = useState("");

  const { systemGroups, customGroups } = useMemo(() => {
    const term = search.trim().toLowerCase();
    const matches = (group: CatalogGroupResponse) => {
      if (!term) return true;
      return (
        group.code.toLowerCase().includes(term) ||
        group.nameVi.toLowerCase().includes(term) ||
        (group.nameEn ?? "").toLowerCase().includes(term)
      );
    };
    const filtered = groups.filter(matches);
    return {
      systemGroups: filtered.filter((g) => g.type === "SYSTEM" || g.system),
      customGroups: filtered.filter((g) => g.type === "CUSTOM" && !g.system),
    };
  }, [groups, search]);

  const totalMatches = systemGroups.length + customGroups.length;

  return (
    <section
      aria-labelledby="catalog-groups-heading"
      className="flex w-full shrink-0 flex-col overflow-hidden rounded-lg border border-border bg-surface shadow-xs lg:w-66"
    >
      <div className="flex items-center justify-between gap-2 px-3.5 pt-3.5 pb-2.5">
        <h2 id="catalog-groups-heading" className="text-body font-semibold text-text">
          {t("groupsTitle")}{" "}
          <span className="font-normal text-text-muted tabular-nums">· {groups.length}</span>
        </h2>
        {canModify && (
          <IconButton
            icon={<Plus />}
            aria-label={t("addGroup")}
            tooltip={t("addGroup")}
            onClick={onAddGroup}
            className="size-9 bg-primary-50 text-primary-text hover:bg-primary-100"
          />
        )}
      </div>

      <div className="px-3.5 pb-3">
        <Input
          type="search"
          aria-label={t("searchGroups")}
          placeholder={t("searchGroups")}
          leadingIcon={<Search />}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>

      {loading ? (
        <div role="status" aria-label={commonT("loading")} className="flex flex-col gap-2 px-3 pb-3">
          {Array.from({ length: 6 }, (_, index) => (
            <Skeleton key={index} className="h-14 w-full" />
          ))}
        </div>
      ) : error ? (
        <EmptyState
          className="px-4 py-8"
          icon={<TriangleAlert />}
          title={t("groupsErrorTitle")}
          description={t("groupsErrorDescription")}
          action={
            <Button size="sm" variant="secondary" onClick={onRetry}>
              {commonT("retry")}
            </Button>
          }
        />
      ) : groups.length === 0 ? (
        <EmptyState
          className="px-4 py-8"
          icon={<List />}
          title={t("noGroupsTitle")}
          description={t("noGroupsDescription")}
        />
      ) : totalMatches === 0 ? (
        <p className="px-4 py-6 text-center text-body text-text-muted">{t("noGroupMatches")}</p>
      ) : (
        <div className="flex flex-col gap-1 px-2 pb-2">
          {systemGroups.length > 0 && (
            <GroupSection
              title={t("systemSection")}
              count={systemGroups.length}
              locked
              groups={systemGroups}
              selectedGroupId={selectedGroupId}
              locale={locale}
              inactiveLabel={t("statuses.INACTIVE")}
              onSelectGroup={onSelectGroup}
            />
          )}
          {customGroups.length > 0 && (
            <GroupSection
              title={t("customSection")}
              count={customGroups.length}
              locked={false}
              groups={customGroups}
              selectedGroupId={selectedGroupId}
              locale={locale}
              inactiveLabel={t("statuses.INACTIVE")}
              onSelectGroup={onSelectGroup}
            />
          )}
        </div>
      )}
    </section>
  );
}

function GroupSection({
  title,
  count,
  locked,
  groups,
  selectedGroupId,
  locale,
  inactiveLabel,
  onSelectGroup,
}: {
  title: string;
  count: number;
  locked: boolean;
  groups: CatalogGroupResponse[];
  selectedGroupId: string | null;
  locale: string;
  inactiveLabel: string;
  onSelectGroup: (group: CatalogGroupResponse) => void;
}) {
  return (
    <div className="flex flex-col gap-0.5">
      <div className="flex items-center gap-1.5 px-2.5 pt-2.5 pb-1 text-caption font-semibold tracking-overline text-text-muted uppercase">
        {locked && <Lock aria-hidden className="size-3 shrink-0" />}
        <span>
          {title} · {count}
        </span>
      </div>
      <ul className="flex flex-col gap-0.5">
        {groups.map((group) => {
          const selected = group.id === selectedGroupId;
          const Icon = groupKindIcon(group.kind);
          const displayName = pickName(group, locale);

          return (
            <li key={group.id}>
              <button
                type="button"
                aria-pressed={selected}
                onClick={() => onSelectGroup(group)}
                className={cn(
                  "relative flex min-h-14 w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left transition-colors duration-150 motion-reduce:transition-none",
                  "focus-visible:ring-3 focus-visible:ring-primary-text focus-visible:outline-none",
                  selected
                    ? "bg-primary-50 text-primary-text before:absolute before:top-2 before:bottom-2 before:left-0 before:w-[3px] before:rounded-full before:bg-primary-600"
                    : "text-text hover:bg-subtle",
                )}
              >
                <span
                  aria-hidden
                  className={cn(
                    "flex size-8 shrink-0 items-center justify-center rounded-sm",
                    selected
                      ? "bg-surface text-primary-text"
                      : "bg-subtle text-text-secondary",
                  )}
                >
                  <Icon className="size-4" />
                </span>
                <span className="min-w-0 flex-1">
                  <span
                    className={cn(
                      "block truncate text-body",
                      selected ? "font-semibold" : "font-medium",
                    )}
                  >
                    {displayName}
                  </span>
                  <span className="flex items-center gap-1.5 truncate text-caption text-text-muted">
                    <span className="truncate">{group.code}</span>
                    {group.status === "INACTIVE" && (
                      <span className="shrink-0 rounded-full bg-neutral-bg px-1.5 py-0.25 text-caption text-neutral">
                        {inactiveLabel}
                      </span>
                    )}
                  </span>
                </span>
                <span className="shrink-0 text-caption text-text-muted tabular-nums">
                  {group.itemCount}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
