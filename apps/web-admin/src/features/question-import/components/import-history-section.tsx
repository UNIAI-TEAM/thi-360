"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { Button, Card, EmptyState, IconButton, ListCard, Table, type TableCards } from "@thi360/ui";
import { Download, History, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId, useMemo } from "react";
import type { ContentReference } from "@/features/content/types";
import type { QuestionImportApiDeps } from "../api";
import { useImportHistory } from "../queries";
import type { ImportHistoryEntry } from "../types";
import { useErrorReportDownload } from "./use-issue-text";

export interface ImportHistorySectionProps {
  deps: QuestionImportApiDeps;
  reference: ContentReference | undefined;
  onDownloaded?: () => void;
}

/** Lịch sử nhập (bản vẽ `NhapCauHoiKetQua`), hiện ở bước 1 và bước 4. */
export function ImportHistorySection({ deps, reference, onDownloaded }: ImportHistorySectionProps) {
  const t = useTranslations("questionImport.history");
  const format = useFormatter();
  const headingId = useId();
  const historyQuery = useImportHistory(deps);
  const downloadErrors = useErrorReportDownload(reference);

  const columns = useMemo<ColumnDef<ImportHistoryEntry>[]>(() => {
    const when = (entry: ImportHistoryEntry) => format.dateTime(new Date(entry.importedAt), "dateTime");
    const actions = (entry: ImportHistoryEntry) =>
      entry.errors.length > 0 ? (
        <IconButton
          icon={<Download />}
          aria-label={t("downloadErrors", { time: when(entry) })}
          tooltip={t("downloadErrorsTooltip")}
          onClick={() => {
            downloadErrors(entry.errors, entry.fileName);
            onDownloaded?.();
          }}
        />
      ) : (
        <IconButton
          icon={<Download />}
          aria-label={t("noErrors", { time: when(entry) })}
          tooltip={t("noErrorsTooltip")}
          disabled
        />
      );
    return [
      {
        id: "importedAt",
        header: t("colTime"),
        cell: ({ row }) => <span className="whitespace-nowrap font-medium">{when(row.original)}</span>,
      },
      {
        id: "importedBy",
        header: t("colUser"),
        cell: ({ row }) => <span className="whitespace-nowrap">{row.original.importedBy.name}</span>,
      },
      {
        id: "fileName",
        header: t("colFile"),
        cell: ({ row }) => <span className="block min-w-40 wrap-anywhere">{row.original.fileName}</span>,
      },
      {
        id: "result",
        header: t("colResult"),
        cell: ({ row }) => <span className="block min-w-48 text-text-secondary">{resultText(row.original)}</span>,
      },
      {
        id: "actions",
        header: () => <span className="block text-right">{t("colActions")}</span>,
        cell: ({ row }) => <div className="flex justify-end">{actions(row.original)}</div>,
      },
    ];

    function resultText(entry: ImportHistoryEntry) {
      return t("resultValue", {
        created: entry.createdCount + entry.newVersionCount,
        duplicates: entry.skippedDuplicateCount + entry.newVersionCount,
        errors: entry.errorRowCount,
      });
    }
  }, [t, format, downloadErrors, onDownloaded]);

  const cards = useMemo<TableCards<ImportHistoryEntry>>(
    () => ({
      render: (entry) => {
        const when = format.dateTime(new Date(entry.importedAt), "dateTime");
        return (
          <ListCard
            title={<span className="wrap-anywhere">{entry.fileName}</span>}
            caption={`${when} · ${entry.importedBy.name}`}
            actions={
              entry.errors.length > 0 ? (
                <IconButton
                  icon={<Download />}
                  aria-label={t("downloadErrors", { time: when })}
                  tooltip={t("downloadErrorsTooltip")}
                  onClick={() => {
                    downloadErrors(entry.errors, entry.fileName);
                    onDownloaded?.();
                  }}
                />
              ) : undefined
            }
            meta={[
              {
                label: t("colResult"),
                value: t("resultValue", {
                  created: entry.createdCount + entry.newVersionCount,
                  duplicates: entry.skippedDuplicateCount + entry.newVersionCount,
                  errors: entry.errorRowCount,
                }),
              },
            ]}
          />
        );
      },
    }),
    [t, format, downloadErrors, onDownloaded],
  );

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3">
      <h2 id={headingId} className="text-title font-semibold text-text">
        {t("heading")}
      </h2>
      <Card padding="none" className="overflow-hidden">
        <Table
          aria-label={t("tableLabel")}
          columns={columns}
          data={historyQuery.data ?? []}
          getRowId={(entry) => entry.id}
          loading={historyQuery.isPending}
          loadingRows={3}
          cards={cards}
          error={
            historyQuery.isError ? (
              <EmptyState
                icon={<TriangleAlert />}
                headingLevel={3}
                title={t("error")}
                action={
                  <Button variant="secondary" onClick={() => void historyQuery.refetch()}>
                    {t("retry")}
                  </Button>
                }
              />
            ) : undefined
          }
          empty={<EmptyState icon={<History />} headingLevel={3} title={t("empty")} />}
        />
      </Card>
    </section>
  );
}
