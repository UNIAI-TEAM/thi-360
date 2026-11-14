"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Badge,
  Button,
  Chip,
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
  EmptyState,
  Pagination,
  Skeleton,
  StatusChip,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@thi360/ui";
import { Ban, History, Layers, Link2, TriangleAlert } from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import {
  catalogQueryKeys,
  type AuditEventResponse,
  type CatalogApi,
  type CatalogGroupResponse,
  type CatalogItemResponse,
  type CombinationVersionResponse,
} from "../api";
import {
  pickName,
  resolveCatalogAuditActionKey,
  resolveCatalogUsageTypeKey,
} from "../utils";

export type ItemDetailDrawerProps = {
  open: boolean;
  group: CatalogGroupResponse;
  item: CatalogItemResponse | null;
  api: CatalogApi;
  canModify: boolean;
  canAudit: boolean;
  onOpenChange: (open: boolean) => void;
  onStopItem: (item: CatalogItemResponse) => void;
};

type DrawerTab = "info" | "usages" | "audit" | "versions";

export function ItemDetailDrawer({
  open,
  group,
  item,
  api,
  canModify,
  canAudit,
  onOpenChange,
  onStopItem,
}: ItemDetailDrawerProps) {
  const t = useTranslations("catalog");
  const locale = useLocale();

  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      {open && item && (
        <DrawerContent closeLabel={t("close")}>
          <DrawerHeader>
            <div className="flex flex-wrap items-center gap-2">
              <DrawerTitle>{pickName(item, locale)}</DrawerTitle>
              <StatusChip tone={item.status === "ACTIVE" ? "success" : "neutral"}>
                {t(`statuses.${item.status}`)}
              </StatusChip>
            </div>
            <DrawerDescription>
              <span className="inline-flex flex-wrap items-center gap-2">
                <span className="rounded-sm bg-subtle px-2 py-0.5 text-caption font-semibold text-text">
                  {item.code}
                </span>
                <span>
                  {pickName(group, locale)} ({group.code})
                </span>
              </span>
            </DrawerDescription>
          </DrawerHeader>

          {canModify && item.status === "ACTIVE" && (
            <div className="flex flex-wrap items-center gap-2">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => onStopItem(item)}
              >
                <Ban aria-hidden className="mr-1.5 size-4 text-warning" />
                {t("detailDrawer.deactivateAction")}
              </Button>
            </div>
          )}

          <ItemDetailBody
            key={item.id}
            group={group}
            item={item}
            api={api}
            canAudit={canAudit}
          />
        </DrawerContent>
      )}
    </Drawer>
  );
}

function ItemDetailBody({
  group,
  item,
  api,
  canAudit,
}: {
  group: CatalogGroupResponse;
  item: CatalogItemResponse;
  api: CatalogApi;
  canAudit: boolean;
}) {
  const t = useTranslations("catalog");
  const commonT = useTranslations("common");
  const locale = useLocale();
  const [activeTab, setActiveTab] = useState<DrawerTab>("info");
  const [auditPage, setAuditPage] = useState(0);

  const isCombination = group.kind === "COMBINATION";

  const usagesQuery = useQuery({
    queryKey: catalogQueryKeys.itemUsages(item.id),
    queryFn: () => api.getItemUsages(item.id),
    enabled: activeTab === "usages",
  });

  const auditQuery = useQuery({
    queryKey: catalogQueryKeys.itemAudit(item.id, auditPage),
    queryFn: () => api.listItemAuditEvents(item.id, { page: auditPage, size: 10 }),
    enabled: activeTab === "audit" && canAudit,
  });

  const versionsQuery = useQuery({
    queryKey: catalogQueryKeys.itemVersions(item.id),
    queryFn: () => api.listItemVersions(item.id),
    enabled: activeTab === "versions" && isCombination,
  });

  const orderedCombinationSubjects = useMemo(
    () =>
      item.combination
        ? [...item.combination.subjects].sort((a, b) => a.position - b.position)
        : [],
    [item.combination],
  );

  const sortedVersions = useMemo(
    () =>
      [...(versionsQuery.data ?? [])].sort((a, b) => b.versionNo - a.versionNo),
    [versionsQuery.data],
  );

  const format = useFormatter();

  function formatDate(iso: string): string {
    const parsed = new Date(iso);
    if (Number.isNaN(parsed.getTime())) return iso;
    // English locales default to month-first; the catalog contract requires
    // dd/MM/yyyy for every locale, so rebuild the day-first string from parts.
    if (locale.toLowerCase().startsWith("en")) {
      const parts = new Intl.DateTimeFormat("en", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23",
        timeZone: "Asia/Ho_Chi_Minh",
      }).formatToParts(parsed);
      const pick = (type: string) =>
        parts.find((part) => part.type === type)?.value ?? "";
      return `${pick("day")}/${pick("month")}/${pick("year")} ${pick("hour")}:${pick("minute")}`;
    }
    return format.dateTime(parsed, {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  function resolveActorLabel(event: AuditEventResponse): string {
    if (event.actorUsername) return event.actorUsername;
    if (event.actorType === "PLATFORM") return t("detailDrawer.auditActorPlatform");
    return t("detailDrawer.auditActorSystem");
  }

  return (
    <Tabs
      variant="content"
      value={activeTab}
      onValueChange={(value) => setActiveTab(value as DrawerTab)}
      className="flex min-h-0 flex-1 flex-col"
    >
      <TabsList aria-label={t("detailDrawer.tabsLabel")} className="flex-wrap">
        <TabsTrigger value="info">{t("detailDrawer.infoTab")}</TabsTrigger>
        <TabsTrigger value="usages">{t("detailDrawer.usagesTab")}</TabsTrigger>
        <TabsTrigger value="audit">{t("detailDrawer.auditTab")}</TabsTrigger>
        {isCombination && (
          <TabsTrigger value="versions">{t("detailDrawer.versionsTab")}</TabsTrigger>
        )}
      </TabsList>

      <TabsContent value="info" className="flex flex-col gap-4">
        <dl className="grid grid-cols-1 gap-3 rounded-md border border-border bg-page p-4 sm:grid-cols-2">
          <DetailField label={t("columns.code")} value={item.code} />
          <DetailField
            label={t("detailDrawer.valueLabel")}
            value={String(item.value)}
            tabularNums
          />
          <DetailField label={t("columns.nameVi")} value={item.nameVi} />
          <DetailField label={t("columns.nameEn")} value={item.nameEn || "—"} />
          <DetailField
            label={t("detailDrawer.displayOrderLabel")}
            value={String(item.displayOrder)}
            tabularNums
          />
          <DetailField
            label={t("detailDrawer.originLabel")}
            value={
              item.system
                ? t("detailDrawer.originSystem")
                : t("detailDrawer.originCustom")
            }
          />
          <DetailField
            label={t("detailDrawer.createdAtLabel")}
            value={formatDate(item.createdAt)}
          />
          <DetailField
            label={t("detailDrawer.updatedAtLabel")}
            value={formatDate(item.updatedAt)}
          />
          <div className="sm:col-span-2">
            <DetailField
              label={t("detailDrawer.descriptionLabel")}
              value={item.description || "—"}
            />
          </div>
        </dl>

        {isCombination && item.combination && (
          <div className="flex flex-col gap-3 rounded-md border border-border bg-surface p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-body font-semibold text-text">
                {t("detailDrawer.combinationSectionTitle")}
              </h3>
              <Badge>{t("versionBadge", { version: item.combination.versionNo })}</Badge>
            </div>

            <div className="flex flex-col gap-1">
              <span className="text-caption font-medium text-text-muted">
                {t("detailDrawer.examLabel")}
              </span>
              <span className="text-body font-medium text-text">
                {item.combination.examCode} —{" "}
                {pickName(
                  {
                    nameVi: item.combination.examNameVi,
                    nameEn: item.combination.examNameEn,
                  },
                  locale,
                )}
                {item.combination.examStatus === "INACTIVE"
                  ? ` (${t("statuses.INACTIVE")})`
                  : ""}
              </span>
            </div>

            <div className="flex flex-col gap-1.5">
              <span className="text-caption font-medium text-text-muted">
                {t("detailDrawer.subjectsLabel")}
              </span>
              <div className="flex flex-wrap gap-1.5">
                {orderedCombinationSubjects.map((subject, idx) => (
                  <Chip key={subject.subjectItemId} size="sm">
                    {idx + 1}. {pickName(subject, locale)} ({subject.code})
                    {subject.status === "INACTIVE" ? ` · ${t("statuses.INACTIVE")}` : ""}
                  </Chip>
                ))}
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <span className="text-caption font-medium text-text-muted">
                {t("detailDrawer.sourceNoteLabel")}
              </span>
              <span className="text-body text-text-secondary">
                {item.combination.sourceNote || "—"}
              </span>
            </div>
          </div>
        )}
      </TabsContent>

      <TabsContent value="usages" className="flex flex-col gap-3">
        {usagesQuery.isPending ? (
          <div className="flex flex-col gap-2.5" role="status" aria-label={commonT("loading")}>
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-16 w-full" />
          </div>
        ) : usagesQuery.isError ? (
          <EmptyState
            icon={<TriangleAlert />}
            title={t("detailDrawer.usagesError")}
            action={
              <Button variant="secondary" onClick={() => void usagesQuery.refetch()}>
                {commonT("retry")}
              </Button>
            }
          />
        ) : !usagesQuery.data || usagesQuery.data.usages.length === 0 ? (
          <EmptyState icon={<Link2 />} title={t("detailDrawer.usagesEmpty")} />
        ) : (
          <>
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border bg-page px-3.5 py-2.5">
              <span className="text-field-label font-semibold text-text">
                {t("detailDrawer.usagesTotalSummary", { count: usagesQuery.data.totalCount })}
              </span>
              <StatusChip tone={usagesQuery.data.blocking ? "danger" : "neutral"}>
                {usagesQuery.data.blocking
                  ? t("detailDrawer.usagesBlocking")
                  : t("detailDrawer.usagesNonBlocking")}
              </StatusChip>
            </div>
            <ul className="divide-y divide-border overflow-hidden rounded-md border border-border bg-surface">
              {usagesQuery.data.usages.map((usage) => (
                <li
                  key={`${usage.source}-${usage.objectType}`}
                  className="flex flex-col gap-1.5 px-3.5 py-3"
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="min-w-0">
                      <p className="font-medium text-text">
                        {t(`usageTypes.${resolveCatalogUsageTypeKey(usage.objectType)}`)}
                      </p>
                      <p className="text-caption text-text-muted">
                        {t("detailDrawer.usageCount", { count: usage.count })}
                      </p>
                    </div>
                    <StatusChip tone={usage.blocking ? "danger" : "neutral"}>
                      {usage.blocking
                        ? t("detailDrawer.usagesBlocking")
                        : t("detailDrawer.usagesNonBlocking")}
                    </StatusChip>
                  </div>
                  {usage.sampleLabels.length > 0 && (
                    <div className="flex flex-wrap items-center gap-1.5 text-caption text-text-secondary">
                      <span>{t("detailDrawer.usagesSamplesLabel")}</span>
                      {usage.sampleLabels.map((label) => (
                        <Badge key={label}>{label}</Badge>
                      ))}
                    </div>
                  )}
                </li>
              ))}
            </ul>
          </>
        )}
      </TabsContent>

      <TabsContent value="audit" className="flex flex-col gap-3">
        {!canAudit ? (
          <EmptyState icon={<History />} title={t("detailDrawer.auditPermissionDenied")} />
        ) : auditQuery.isPending ? (
          <div className="flex flex-col gap-2.5" role="status" aria-label={commonT("loading")}>
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
          </div>
        ) : auditQuery.isError ? (
          <EmptyState
            icon={<TriangleAlert />}
            title={t("detailDrawer.auditError")}
            action={
              <Button variant="secondary" onClick={() => void auditQuery.refetch()}>
                {commonT("retry")}
              </Button>
            }
          />
        ) : !auditQuery.data || auditQuery.data.items.length === 0 ? (
          <EmptyState icon={<History />} title={t("detailDrawer.auditEmpty")} />
        ) : (
          <>
            <ul className="divide-y divide-border overflow-hidden rounded-md border border-border bg-surface">
              {auditQuery.data.items.map((event) => {
                const actionKey = resolveCatalogAuditActionKey(event.action);
                return (
                  <li key={event.id} className="flex flex-col gap-1 px-3.5 py-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="font-medium text-text">
                        {t(`auditActions.${actionKey}`)}
                      </span>
                      <span className="text-caption text-text-muted tabular-nums">
                        {formatDate(event.occurredAt)}
                      </span>
                    </div>
                    <p className="text-caption text-text-secondary">
                      {resolveActorLabel(event)}
                    </p>
                    {event.reason && (
                      <p className="text-caption text-text-muted">
                        {t("detailDrawer.auditReasonLabel", { reason: event.reason })}
                      </p>
                    )}
                  </li>
                );
              })}
            </ul>
            {auditQuery.data.totalPages > 1 && (
              <Pagination
                page={auditPage + 1}
                pageSize={auditQuery.data.size || 10}
                total={auditQuery.data.totalItems}
                onPageChange={(nextOneBased) => setAuditPage(nextOneBased - 1)}
                labels={{
                  nav: t("detailDrawer.auditPaginationNav"),
                  previous: t("pagination.previous"),
                  next: t("pagination.next"),
                  rowsPerPage: t("pagination.rowsPerPage"),
                  page: (pageNo) => t("pagination.page", { page: pageNo }),
                  range: ({ from, to, total }) => t("pagination.range", { from, to, total }),
                }}
              />
            )}
          </>
        )}
      </TabsContent>

      {isCombination && (
        <TabsContent value="versions" className="flex flex-col gap-3">
          {versionsQuery.isPending ? (
            <div className="flex flex-col gap-2.5" role="status" aria-label={commonT("loading")}>
              <Skeleton className="h-20 w-full" />
              <Skeleton className="h-20 w-full" />
            </div>
          ) : versionsQuery.isError ? (
            <EmptyState
              icon={<TriangleAlert />}
              title={t("detailDrawer.versionsError")}
              action={
                <Button variant="secondary" onClick={() => void versionsQuery.refetch()}>
                  {commonT("retry")}
                </Button>
              }
            />
          ) : sortedVersions.length === 0 ? (
            <EmptyState icon={<Layers />} title={t("detailDrawer.versionsEmpty")} />
          ) : (
            <ul className="flex flex-col gap-2.5">
              {sortedVersions.map((version) => (
                <VersionCard
                  key={version.id}
                  version={version}
                  locale={locale}
                  formattedDate={formatDate(version.createdAt)}
                />
              ))}
            </ul>
          )}
        </TabsContent>
      )}
    </Tabs>
  );
}

function VersionCard({
  version,
  locale,
  formattedDate,
}: {
  version: CombinationVersionResponse;
  locale: string;
  formattedDate: string;
}) {
  const t = useTranslations("catalog");
  const orderedSubjects = useMemo(
    () => [...version.subjects].sort((a, b) => a.position - b.position),
    [version.subjects],
  );

  return (
    <li className="flex flex-col gap-2 rounded-md border border-border bg-surface p-3.5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="font-semibold text-text">
            {t("detailDrawer.versionHeading", { version: version.versionNo })}
          </span>
          {version.isCurrent && (
            <StatusChip tone="success">{t("detailDrawer.currentVersionBadge")}</StatusChip>
          )}
        </div>
        <span className="text-caption text-text-muted tabular-nums">{formattedDate}</span>
      </div>

      <p className="text-field-label text-text-secondary">
        {t("detailDrawer.examLabel")}: {version.examCode} —{" "}
        {pickName(
          { nameVi: version.examNameVi, nameEn: version.examNameEn },
          locale,
        )}
        {version.examStatus === "INACTIVE" ? ` (${t("statuses.INACTIVE")})` : ""}
      </p>

      <div className="flex flex-wrap gap-1.5">
        {orderedSubjects.map((subject, idx) => (
          <Chip key={subject.subjectItemId} size="sm">
            {idx + 1}. {pickName(subject, locale)}
            {subject.status === "INACTIVE" ? ` (${t("statuses.INACTIVE")})` : ""}
          </Chip>
        ))}
      </div>

      {version.sourceNote && (
        <p className="text-caption text-text-muted">
          {t("detailDrawer.sourceNoteLabel")}: {version.sourceNote}
        </p>
      )}
    </li>
  );
}

function DetailField({
  label,
  value,
  tabularNums = false,
}: {
  label: string;
  value: string;
  tabularNums?: boolean;
}) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-caption font-medium text-text-muted">{label}</dt>
      <dd className={tabularNums ? "font-medium text-text tabular-nums" : "font-medium text-text"}>
        {value}
      </dd>
    </div>
  );
}
