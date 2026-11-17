"use client";

import type { ColumnDef } from "@tanstack/react-table";
import {
  Button,
  Card,
  Checkbox,
  cn,
  EmptyState,
  focusRing,
  IconButton,
  Label,
  ListCard,
  ListCardTitleButton,
  Pagination,
  StatusChip,
  Table,
  type TableCards,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@thi360/ui";
import { ArrowLeft, ArrowRight, CircleCheck, CircleX, Copy, Download, Eye, Rows3, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { type ReactNode, type RefObject, useId, useMemo, useRef, useState } from "react";
import { useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import { importApiErrorCode, type QuestionImportApiDeps } from "../api";
import { collectErrorLines } from "../error-report";
import { useCommitImport } from "../queries";
import type {
  ColumnMapping,
  DuplicatePolicy,
  ImportHistoryEntry,
  ImportRowIssue,
  ImportRowResult,
  ImportUpload,
  ImportValidation,
} from "../types";
import { QuestionPreview, ROW_STATUS_TONE } from "./question-preview";
import { useErrorReportDownload, useIssueText } from "./use-issue-text";

const PAGE_SIZE = 20;

export const CHECK_TABS = ["errors", "duplicates", "valid"] as const;
export type CheckTab = (typeof CHECK_TABS)[number];
const TAB_PARAM = "tab";

/** Tab đầu tiên: tab ghi trên URL nếu có, không thì Lỗi khi có lỗi, rồi Trùng, rồi Hợp lệ. */
function initialTab(validation: ImportValidation): CheckTab {
  if (typeof window !== "undefined") {
    const fromUrl = new URLSearchParams(window.location.search).get(TAB_PARAM);
    if ((CHECK_TABS as readonly string[]).includes(fromUrl ?? "")) return fromUrl as CheckTab;
  }
  if (validation.summary.error > 0) return "errors";
  if (validation.summary.duplicate > 0) return "duplicates";
  return "valid";
}

/** Ghi tab vào `?tab=` để tải lại vẫn mở đúng tab (DESIGN.md mục 5.5); giữ các tham số khác của URL. */
export function writeTabToUrl(tab: CheckTab | null) {
  if (typeof window === "undefined") return;
  const params = new URLSearchParams(window.location.search);
  if (tab) params.set(TAB_PARAM, tab);
  else params.delete(TAB_PARAM);
  const query = params.toString();
  window.history.replaceState(null, "", query ? `${window.location.pathname}?${query}` : window.location.pathname);
}

interface ErrorTableRow {
  id: string;
  row: ImportRowResult;
  issue: ImportRowIssue;
}

export interface CheckStepProps {
  deps: QuestionImportApiDeps;
  upload: ImportUpload;
  mapping: ColumnMapping;
  policy: DuplicatePolicy;
  validation: ImportValidation;
  reference: ContentReference | undefined;
  headingRef: RefObject<HTMLHeadingElement | null>;
  onBack: () => void;
  onCommitted: (result: ImportHistoryEntry) => void;
  onAnnounce: (message: string) => void;
}

/** Bước 3: kết quả kiểm tra theo dòng, lọc theo trạng thái, xem trước (bản vẽ `NhapCauHoiKiemTra`). */
export function CheckStep({
  deps,
  upload,
  mapping,
  policy,
  validation,
  reference,
  headingRef,
  onBack,
  onCommitted,
  onAnnounce,
}: CheckStepProps) {
  const t = useTranslations("questionImport");
  const tc = useTranslations("content");
  const lookup = useReferenceLookup(reference);
  const describe = useIssueText(reference);
  const downloadErrors = useErrorReportDownload(reference);
  const commitMutation = useCommitImport(deps);
  const skipErrorsId = useId();
  const previewHeadingRef = useRef<HTMLHeadingElement>(null);
  const skipErrorsRef = useRef<HTMLButtonElement>(null);
  const { summary, rows } = validation;

  const [tab, setTab] = useState<CheckTab>(() => initialTab(validation));
  const [pages, setPages] = useState<Record<CheckTab, number>>({ errors: 1, duplicates: 1, valid: 1 });
  const [previewRowNumber, setPreviewRowNumber] = useState<number | null>(null);
  const [skipErrors, setSkipErrors] = useState(true);
  const [actionError, setActionError] = useState<string | null>(null);

  const errorRows = useMemo<ErrorTableRow[]>(
    () =>
      rows.flatMap((row) =>
        row.status === "ERROR"
          ? row.issues
              .filter((issue) => issue.severity === "error")
              .map((issue, index) => ({ id: `${row.rowNumber}-${index}`, row, issue }))
          : [],
      ),
    [rows],
  );
  const duplicateRows = useMemo(() => rows.filter((row) => row.status === "DUPLICATE"), [rows]);
  const validRows = useMemo(() => rows.filter((row) => row.status === "VALID"), [rows]);

  const tabRows: Record<CheckTab, ImportRowResult[]> = {
    errors: errorRows.map((item) => item.row),
    duplicates: duplicateRows,
    valid: validRows,
  };
  const previewRow =
    rows.find((row) => row.rowNumber === previewRowNumber) ?? tabRows[tab][0] ?? null;

  function openPreview(rowNumber: number) {
    setPreviewRowNumber(rowNumber);
    // Ở khung hẹp khung xem trước nằm dưới bảng: đưa focus (và cuộn) tới tiêu đề của nó.
    requestAnimationFrame(() => {
      previewHeadingRef.current?.focus();
      previewHeadingRef.current?.scrollIntoView?.({ block: "nearest" });
    });
  }

  function changeTab(next: string) {
    const value = next as CheckTab;
    setTab(value);
    setPreviewRowNumber(null);
    writeTabToUrl(value);
  }

  const importCount = summary.valid + (policy === "NEW_VERSION" ? summary.duplicate : 0);

  function handleCommit() {
    setActionError(null);
    if (summary.error > 0 && !skipErrors) {
      setActionError(t("check.skipErrorsRequired"));
      skipErrorsRef.current?.focus();
      return;
    }
    if (importCount === 0) {
      setActionError(t("check.nothingToImport"));
      return;
    }
    commitMutation.mutate(
      { uploadId: upload.id, mapping, duplicatePolicy: policy, skipErrors },
      {
        onSuccess: (result) => {
          writeTabToUrl(null);
          onCommitted(result);
        },
        onError: (error) => {
          const code = importApiErrorCode(error);
          setActionError(
            code === "IMPORT_HAS_ERRORS"
              ? t("check.skipErrorsRequired")
              : code === "IMPORT_SOURCE_GONE"
                ? t("mapping.sourceGone")
                : t("check.commitError"),
          );
        },
      },
    );
  }

  function handleDownloadErrors() {
    downloadErrors(collectErrorLines(rows), upload.fileName);
    onAnnounce(t("live.errorsDownloaded"));
  }

  const viewing = (rowNumber: number) =>
    previewRow?.rowNumber === rowNumber ? (
      <span className="ms-2 inline-flex items-center gap-1 text-caption font-medium text-primary-text">
        <Eye aria-hidden className="size-3.5" />
        {t("check.viewing")}
      </span>
    ) : null;
  const previewButton = (rowNumber: number) => (
    <IconButton
      icon={<Eye />}
      aria-label={t("check.previewRow", { row: rowNumber })}
      onClick={() => openPreview(rowNumber)}
    />
  );
  const lineCell = (rowNumber: number) => (
    <span className="whitespace-nowrap font-semibold text-text">
      {rowNumber}
      {viewing(rowNumber)}
    </span>
  );
  const metaText = (row: ImportRowResult) =>
    [row.draft.subjectCode, row.draft.gradeCode, row.draft.difficultyCode]
      .map((code, index) =>
        code ? (index === 0 ? lookup.subject(code) : index === 1 ? lookup.grade(code) : lookup.difficulty(code)) : "—",
      )
      .join(" · ");
  const typeText = (row: ImportRowResult) => {
    if (!row.draft.type) return "—";
    if (row.isSetPassage) {
      return t("check.setPassage", {
        count: rows.filter((child) => child.parentRowNumber === row.rowNumber && child.status !== "ERROR").length,
      });
    }
    return tc(`questionType.${row.draft.type}`);
  };
  const knowledgeText = (row: ImportRowResult) =>
    row.draft.knowledge.length > 0 ? row.draft.knowledge.map((ref) => ref.code).join(", ") : t("check.noKnowledge");
  const handling = policy === "NEW_VERSION" ? t("check.handlingVersion") : t("check.handlingSkip");

  const errorColumns: ColumnDef<ErrorTableRow>[] = [
    { id: "row", header: t("check.colRow"), cell: ({ row }) => lineCell(row.original.row.rowNumber) },
    {
      id: "column",
      header: t("check.colColumn"),
      cell: ({ row }) => <span className="whitespace-nowrap">{row.original.issue.column ?? "—"}</span>,
    },
    {
      id: "sourceCode",
      header: t("check.colSourceCode"),
      cell: ({ row }) => <span className="whitespace-nowrap">{row.original.row.sourceCode ?? "—"}</span>,
    },
    {
      id: "error",
      header: t("check.colError"),
      cell: ({ row }) => (
        <span className="flex min-w-48 items-start gap-1.5 text-danger">
          <CircleX aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>{describe(row.original.issue).message}</span>
        </span>
      ),
    },
    {
      id: "fix",
      header: t("check.colFix"),
      cell: ({ row }) => <span className="block min-w-48 text-text-secondary">{describe(row.original.issue).fix}</span>,
    },
    {
      id: "actions",
      header: () => <span className="sr-only">{t("check.colActions")}</span>,
      cell: ({ row }) => <div className="flex justify-end">{previewButton(row.original.row.rowNumber)}</div>,
    },
  ];

  const duplicateColumns: ColumnDef<ImportRowResult>[] = [
    { id: "row", header: t("check.colRow"), cell: ({ row }) => lineCell(row.original.rowNumber) },
    {
      id: "sourceCode",
      header: t("check.colSourceCode"),
      cell: ({ row }) => <span className="whitespace-nowrap">{row.original.sourceCode ?? "—"}</span>,
    },
    {
      id: "existing",
      header: t("check.colExisting"),
      cell: ({ row }) =>
        row.original.duplicateOf ? (
          <Link
            href={`/questions/${row.original.duplicateOf.id}`}
            className={cn("rounded-sm font-semibold whitespace-nowrap text-primary-text hover:underline", focusRing)}
          >
            {row.original.duplicateOf.code}
          </Link>
        ) : (
          "—"
        ),
    },
    {
      id: "handling",
      header: t("check.colHandling"),
      cell: () => <span className="block min-w-48 text-text-secondary">{handling}</span>,
    },
    {
      id: "actions",
      header: () => <span className="sr-only">{t("check.colActions")}</span>,
      cell: ({ row }) => <div className="flex justify-end">{previewButton(row.original.rowNumber)}</div>,
    },
  ];

  const validColumns: ColumnDef<ImportRowResult>[] = [
    { id: "row", header: t("check.colRow"), cell: ({ row }) => lineCell(row.original.rowNumber) },
    {
      id: "sourceCode",
      header: t("check.colSourceCode"),
      cell: ({ row }) => <span className="whitespace-nowrap">{row.original.sourceCode ?? "—"}</span>,
    },
    {
      id: "type",
      header: t("check.colType"),
      cell: ({ row }) => (
        <span className="block min-w-36">
          {typeText(row.original)}
          {row.original.parentRowNumber !== null && (
            <span className="block text-caption text-text-muted">
              {t("check.setChild", { row: row.original.parentRowNumber })}
            </span>
          )}
        </span>
      ),
    },
    {
      id: "meta",
      header: t("check.colMeta"),
      cell: ({ row }) => <span className="block min-w-44">{metaText(row.original)}</span>,
    },
    {
      id: "knowledge",
      header: t("check.colKnowledge"),
      cell: ({ row }) =>
        row.original.draft.knowledge.length > 0 ? (
          <span className="whitespace-nowrap">{knowledgeText(row.original)}</span>
        ) : (
          <StatusChip tone="warning">{t("check.noKnowledge")}</StatusChip>
        ),
    },
    {
      id: "actions",
      header: () => <span className="sr-only">{t("check.colActions")}</span>,
      cell: ({ row }) => <div className="flex justify-end">{previewButton(row.original.rowNumber)}</div>,
    },
  ];

  const titleButton = (row: ImportRowResult) => (
    <ListCardTitleButton onClick={() => openPreview(row.rowNumber)}>
      {t("check.rowLabel", { row: row.rowNumber })}
      {row.sourceCode ? ` · ${row.sourceCode}` : ""}
    </ListCardTitleButton>
  );
  const errorCards: TableCards<ErrorTableRow> = {
    render: (item) => (
      <ListCard
        title={titleButton(item.row)}
        caption={item.issue.column ?? undefined}
        status={<StatusChip tone="danger">{t("preview.status.ERROR")}</StatusChip>}
      >
        <p className="text-body text-danger">{describe(item.issue).message}</p>
        <p className="text-caption text-text-secondary">{describe(item.issue).fix}</p>
        {viewing(item.row.rowNumber)}
      </ListCard>
    ),
  };
  const duplicateCards: TableCards<ImportRowResult> = {
    render: (row) => (
      <ListCard
        title={titleButton(row)}
        caption={row.duplicateOf ? `${t("check.colExisting")}: ${row.duplicateOf.code}` : undefined}
        status={<StatusChip tone={ROW_STATUS_TONE.DUPLICATE}>{t("preview.status.DUPLICATE")}</StatusChip>}
        meta={[{ label: t("check.colHandling"), value: handling }]}
      >
        {viewing(row.rowNumber)}
      </ListCard>
    ),
  };
  const validCards: TableCards<ImportRowResult> = {
    render: (row) => (
      <ListCard
        title={titleButton(row)}
        caption={typeText(row)}
        status={<StatusChip tone={ROW_STATUS_TONE.VALID}>{t("preview.status.VALID")}</StatusChip>}
        meta={[
          { label: t("check.colMeta"), value: metaText(row) },
          { label: t("check.colKnowledge"), value: knowledgeText(row) },
        ]}
      >
        {viewing(row.rowNumber)}
      </ListCard>
    ),
  };

  function pageOf<T>(tabKey: CheckTab, items: T[]): T[] {
    const page = pages[tabKey];
    return items.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  }
  function pager(tabKey: CheckTab, total: number): ReactNode {
    if (total <= PAGE_SIZE) return null;
    return (
      <div className="border-t border-border px-4 py-3">
        <Pagination
          page={pages[tabKey]}
          pageSize={PAGE_SIZE}
          total={total}
          onPageChange={(page) => setPages((current) => ({ ...current, [tabKey]: page }))}
        />
      </div>
    );
  }

  const summaryItems = [
    { key: "rows", icon: <Rows3 aria-hidden className="size-5" />, value: summary.total, label: t("check.rowsRead"), tone: "text-text" },
    {
      key: "valid",
      icon: <CircleCheck aria-hidden className="size-5" />,
      value: summary.valid,
      label: t("check.valid"),
      detail: summary.validSets > 0 ? t("check.validSets", { sets: summary.validSets, children: summary.validSetChildren }) : null,
      tone: "text-success",
    },
    { key: "error", icon: <CircleX aria-hidden className="size-5" />, value: summary.error, label: t("check.error"), tone: "text-danger" },
    {
      key: "duplicate",
      icon: <Copy aria-hidden className="size-5" />,
      value: summary.duplicate,
      label: policy === "NEW_VERSION" ? t("check.duplicateVersion") : t("check.duplicateSkip"),
      tone: "text-warning",
    },
  ];

  return (
    <div className="flex flex-col gap-4">
      <h2 ref={headingRef} tabIndex={-1} className="text-title font-semibold text-text outline-hidden">
        {t("check.heading")}
      </h2>

      <dl aria-label={t("check.summaryLabel")} className="grid grid-cols-[repeat(auto-fit,minmax(min(12rem,100%),1fr))] gap-3">
        {summaryItems.map((item) => (
          <Card key={item.key} padding="md" className="flex flex-col-reverse gap-1">
            <dt className="text-caption text-text-muted">
              {item.label}
              {item.detail && <span className="block">{item.detail}</span>}
            </dt>
            <dd className={cn("flex items-center gap-2 text-h2 font-semibold tabular-nums", item.tone)}>
              {item.icon}
              {item.value}
            </dd>
          </Card>
        ))}
      </dl>

      <div className="grid items-start gap-4 @5xl:grid-cols-12">
        <div className="min-w-0 @5xl:col-span-7">
          <Tabs value={tab} onValueChange={changeTab}>
            <TabsList aria-label={t("check.tabsLabel")}>
              <TabsTrigger value="errors">{t("check.tabErrors", { count: summary.error })}</TabsTrigger>
              <TabsTrigger value="duplicates">{t("check.tabDuplicates", { count: summary.duplicate })}</TabsTrigger>
              <TabsTrigger value="valid">{t("check.tabValid", { count: summary.valid })}</TabsTrigger>
            </TabsList>
            <TabsContent value="errors">
              <Card padding="none" className="overflow-hidden">
                <Table
                  aria-label={t("check.errorsTable")}
                  columns={errorColumns}
                  data={pageOf("errors", errorRows)}
                  getRowId={(item) => item.id}
                  cards={errorCards}
                  empty={<EmptyState icon={<CircleCheck />} headingLevel={3} title={t("check.emptyErrors")} />}
                />
                {pager("errors", errorRows.length)}
              </Card>
            </TabsContent>
            <TabsContent value="duplicates">
              <Card padding="none" className="overflow-hidden">
                <Table
                  aria-label={t("check.duplicatesTable")}
                  columns={duplicateColumns}
                  data={pageOf("duplicates", duplicateRows)}
                  getRowId={(row) => String(row.rowNumber)}
                  cards={duplicateCards}
                  empty={<EmptyState icon={<CircleCheck />} headingLevel={3} title={t("check.emptyDuplicates")} />}
                />
                {pager("duplicates", duplicateRows.length)}
              </Card>
            </TabsContent>
            <TabsContent value="valid">
              <Card padding="none" className="overflow-hidden">
                <Table
                  aria-label={t("check.validTable")}
                  columns={validColumns}
                  data={pageOf("valid", validRows)}
                  getRowId={(row) => String(row.rowNumber)}
                  cards={validCards}
                  empty={<EmptyState icon={<TriangleAlert />} headingLevel={3} title={t("check.emptyValid")} />}
                />
                {pager("valid", validRows.length)}
              </Card>
            </TabsContent>
          </Tabs>
        </div>

        <div className="min-w-0 @5xl:col-span-5">
          <QuestionPreview
            row={previewRow}
            rows={rows}
            reference={reference}
            headingRef={previewHeadingRef}
            describe={describe}
          />
        </div>
      </div>

      <Card padding="md" className="flex flex-col gap-3">
        {(summary.error > 0 || summary.duplicate > 0) && (
          <p className="flex items-start gap-2 text-body text-text-secondary">
            <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
            <span>
              {summary.error > 0 && t("check.noteErrors", { count: summary.error })}{" "}
              {summary.duplicate > 0 &&
                (policy === "NEW_VERSION"
                  ? t("check.noteDuplicatesVersion", { count: summary.duplicate })
                  : t("check.noteDuplicatesSkip", { count: summary.duplicate }))}
            </span>
          </p>
        )}
        {summary.error > 0 && (
          <div className="flex min-h-10 items-center gap-2">
            <Checkbox
              ref={skipErrorsRef}
              id={skipErrorsId}
              checked={skipErrors}
              onCheckedChange={(checked) => {
                setSkipErrors(checked === true);
                setActionError(null);
              }}
            />
            <Label htmlFor={skipErrorsId} className="cursor-pointer font-normal text-text">
              {t("check.skipErrors", { count: summary.error })}
            </Label>
          </div>
        )}
        {actionError && (
          <div role="alert" className="rounded-md border border-danger/30 bg-danger-bg px-3.5 py-3 text-body text-danger">
            {actionError}
          </div>
        )}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Button type="button" variant="secondary" onClick={onBack} disabled={commitMutation.isPending}>
            <ArrowLeft />
            <span>{t("check.back")}</span>
          </Button>
          <div className="flex flex-wrap items-center justify-end gap-2">
            {summary.error > 0 && (
              <Button type="button" variant="secondary" onClick={handleDownloadErrors}>
                <Download />
                <span>{t("check.downloadErrors")}</span>
              </Button>
            )}
            <Button type="button" loading={commitMutation.isPending} onClick={handleCommit}>
              <span>{t("check.commit", { count: importCount })}</span>
              <ArrowRight />
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
}
