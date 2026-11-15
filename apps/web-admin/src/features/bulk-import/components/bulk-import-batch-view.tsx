"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Card,
  Checkbox,
  cn,
  ConfirmDialog,
  EmptyState,
  Label,
  notify,
  Pagination,
  Skeleton,
  StatusChip,
  Table,
} from "@thi360/ui";
import { AlertTriangle, ArrowLeft, CheckCircle2, Download, FileSpreadsheet, Trash2, TriangleAlert, Upload } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
import { ForbiddenView } from "@/features/session/forbidden-view";
import { usePermissions, useSession } from "@/features/session/session-provider";
import { getErrorStatus } from "@/lib/api/query-client";
import {
  BULK_IMPORT_QUERY_KEYS,
  type BulkImportApiDeps,
  BulkImportApiError,
  defaultBulkImportApiDeps,
  type ImportBatch,
  type ImportRow,
  type ListBatchRowsQuery,
} from "../api";
import {
  generateIdempotencyKey,
  isBatchInProgressStatus,
  isBatchTerminalStatus,
  mapImportColumnKey,
  mapImportFailureCode,
  mapImportRowErrorCode,
  resolveImportApiErrorMessage,
} from "../utils";
import { BulkImportHeader } from "./bulk-import-header";
import { BulkImportStepper, type BulkImportStepNumber } from "./bulk-import-stepper";

const DEFAULT_PAGE_SIZE = 20;
const PAGE_SIZE_OPTIONS = [10, 20, 50];
const POLL_INTERVAL_MS = 2_000;
/** Mảng rỗng dùng chung: `?? []` tạo identity mới mỗi render làm TanStack Table tính lại row model. */
const EMPTY_ROW_ITEMS: ImportRow[] = [];
/** `getRowId` ổn định ở module scope: inline arrow tạo hàm mới mỗi render. */
const getImportRowId = (row: ImportRow) => row.id;
const GUARD_STATE_KEY = "__thi360BulkImportLeaveGuard";

export interface BulkImportBatchViewProps {
  batchId: string;
  deps?: Partial<BulkImportApiDeps>;
}

export function BulkImportBatchView({ batchId, deps }: BulkImportBatchViewProps) {
  const t = useTranslations("bulkImport");
  const common = useTranslations("common");
  const sessionState = useSession();
  const { can } = usePermissions();
  // Giữ identity `deps` ổn định: object literal mới mỗi render làm mọi prop/hook
  // phía dưới (queryFn, Table inputs) đổi tham chiếu liên tục.
  const apiDeps = useMemo<BulkImportApiDeps>(
    () => ({ ...defaultBulkImportApiDeps, ...deps }),
    [deps],
  );

  if (sessionState.status === "loading") {
    return <BulkImportBatchSkeleton />;
  }

  if (sessionState.status === "error") {
    return (
      <div className="flex flex-col gap-4">
        <BulkImportHeader deps={apiDeps} />
        <BulkImportStepper currentStep={2} />
        <Card>
          <EmptyState
            icon={<TriangleAlert />}
            title={t("sessionError.title")}
            description={t("sessionError.description")}
            action={<Button onClick={sessionState.retry}>{common("retry")}</Button>}
          />
        </Card>
      </div>
    );
  }

  if (!can("USER.MODIFY")) {
    return <ForbiddenView />;
  }

  return <BulkImportBatchContent batchId={batchId} deps={apiDeps} />;
}

function BulkImportBatchSkeleton() {
  const common = useTranslations("common");
  return (
    <div role="status" aria-label={common("loading")} className="flex flex-col gap-4">
      <Skeleton className="h-12 w-72" />
      <Skeleton className="h-16 w-full" />
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
      <Skeleton className="h-72 w-full" />
    </div>
  );
}

function BulkImportBatchContent({
  batchId,
  deps,
}: {
  batchId: string;
  deps: BulkImportApiDeps;
}) {
  const t = useTranslations("bulkImport");
  const common = useTranslations("common");
  const format = useFormatter();
  const router = useRouter();
  const queryClient = useQueryClient();

  const num = useCallback(
    (value: number) => format.number(value),
    [format],
  );

  const [confirmStepActive, setConfirmStepActive] = useState(false);
  const [onlyErrors, setOnlyErrors] = useState(false);
  const [onlyFailed, setOnlyFailed] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [hasDownloadedResult, setHasDownloadedResult] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [pendingLeave, setPendingLeave] = useState<
    { kind: "href"; href: string } | { kind: "history" } | null
  >(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const idempotencyKeyRef = useRef<string | null>(null);
  const leaveGuardTokenRef = useRef<string | null>(null);
  const historyLeavingRef = useRef(false);

  const onlyErrorsId = useId();
  const onlyFailedId = useId();

  const batchQuery = useQuery({
    queryKey: BULK_IMPORT_QUERY_KEYS.detail(batchId),
    queryFn: () => deps.getImportBatch(batchId),
    refetchInterval: (query) =>
      isBatchInProgressStatus(query.state.data?.status) ? POLL_INTERVAL_MS : false,
  });

  const batch = batchQuery.data;

  const currentStep: BulkImportStepNumber = useMemo(() => {
    if (!batch) return 2;
    if (batch.status === "UPLOADED") return 2;
    if (batch.status === "VALIDATED") return confirmStepActive ? 3 : 2;
    return 4;
  }, [batch, confirmStepActive]);

  const rowFilterStatus: ListBatchRowsQuery["status"] = useMemo(() => {
    if (!batch) return undefined;
    if (batch.status === "VALIDATED" && onlyErrors) return "INVALID";
    if (isBatchTerminalStatus(batch.status) && onlyFailed) return "FAILED";
    return undefined;
  }, [batch, onlyErrors, onlyFailed]);

  // Khi không còn dòng lỗi (kiểm tra lại / đổi đợt) thì bỏ lọc "chỉ hiện dòng lỗi"
  // để bảng không rơi vào trạng thái lọc rỗng không thể bỏ tích. Điều chỉnh state
  // ngay trong render theo pattern "adjusting state when props change" của React.
  if (batch !== undefined && batch.invalidRows === 0 && onlyErrors) {
    setOnlyErrors(false);
  }

  const shouldLoadRows = Boolean(
    batch && (batch.status === "VALIDATED" || isBatchTerminalStatus(batch.status)),
  );

  const rowsQueryParams = useMemo<ListBatchRowsQuery>(
    () => ({
      ...(rowFilterStatus ? { status: rowFilterStatus } : {}),
      page: page - 1,
      size: pageSize,
      sort: "rowNo,asc",
    }),
    [rowFilterStatus, page, pageSize],
  );

  const rowsQuery = useQuery({
    queryKey: BULK_IMPORT_QUERY_KEYS.rows(batchId, rowsQueryParams),
    queryFn: () => deps.listImportBatchRows(batchId, rowsQueryParams),
    placeholderData: keepPreviousWhileSameScope(BULK_IMPORT_QUERY_KEYS.rows(batchId, rowsQueryParams)),
    enabled: shouldLoadRows && !(batch?.status === "VALIDATED" && confirmStepActive),
  });

  const isConfirmedOrTerminal =
    batch !== undefined &&
    (batch.status === "CONFIRMED" ||
      batch.status === "PROCESSING" ||
      isBatchTerminalStatus(batch.status));
  const isTerminal = batch !== undefined && isBatchTerminalStatus(batch.status);
  const shouldWarnOnLeave = Boolean(
    batch &&
      isConfirmedOrTerminal &&
      batch.hasGeneratedPasswords &&
      !hasDownloadedResult &&
      (!isTerminal || batch.resultAvailable),
  );

  useEffect(() => {
    if (!shouldWarnOnLeave) return;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [shouldWarnOnLeave]);

  // Chặn điều hướng Lùi/ Tiến của trình duyệt (cùng tài liệu, không qua click link):
  // đặt một mục history "sentinel" trùng URL hiện tại; khi popstate đưa vị trí ra khỏi
  // sentinel (người dùng bấm Back) thì đẩy lại sentinel và hỏi xác nhận thay vì để
  // trang unmount. Xác nhận "Vẫn rời trang" thì lùi hai bước (sentinel -> trang hiện
  // tại -> trang trước) qua history.go(-2) một lần.
  //
  // Khi cảnh báo hết hiệu lực (đã tải/xóa file kết quả) mà còn đứng trên mục
  // sentinel thì dọn nó bằng history.back(): Back tiếp theo của người dùng sẽ rời
  // trang ngay thay vì "đứng hình" trên mục trùng URL (N2-1). Chỉ dọn khi cảnh báo
  // vừa tắt CÓ CHỦ ĐÍCH (leaveWarningDisarmedRef — phân biệt disarm với unmount do
  // điều hướng) VÀ URL vẫn là trang đợt này — tránh nuốt một lượt Back thật.
  const leaveWarningDisarmedRef = useRef(false);
  useEffect(() => {
    if (!shouldWarnOnLeave) return;
    leaveWarningDisarmedRef.current = false;
    if (!leaveGuardTokenRef.current) {
      leaveGuardTokenRef.current = generateIdempotencyKey();
    }
    const token = leaveGuardTokenRef.current;
    const sentinelState = () => ({
      ...(window.history.state ?? {}),
      [GUARD_STATE_KEY]: token,
    });
    const isGuardedSameUrlEntry = () =>
      window.location.pathname.endsWith(`/users/bulk-import/${batchId}`) &&
      Boolean(
        (window.history.state as Record<string, unknown> | null)?.[GUARD_STATE_KEY],
      );
    const alreadyGuarded = Boolean(
      (window.history.state as Record<string, unknown> | null)?.[GUARD_STATE_KEY],
    );
    if (!alreadyGuarded) {
      window.history.pushState(sentinelState(), "");
    }

    const handlePopState = () => {
      if (leaveWarningDisarmedRef.current) return;
      const state = window.history.state as Record<string, unknown> | null;
      if (state?.[GUARD_STATE_KEY]) {
        // Đáp xuống một mục sentinel (của mount này hoặc mount trước cùng trang) ->
        // vẫn là URL được bảo vệ, không cần hỏi.
        return;
      }
      if (historyLeavingRef.current) {
        // Đang trong quá trình rời trang đã xác nhận -> không chặn nữa.
        historyLeavingRef.current = false;
        return;
      }
      // Người dùng vừa Back khỏi sentinel: khôi phục sentinel rồi hỏi xác nhận.
      window.history.pushState(sentinelState(), "");
      setPendingLeave({ kind: "history" });
    };

    window.addEventListener("popstate", handlePopState);
    return () => {
      window.removeEventListener("popstate", handlePopState);
      if (leaveWarningDisarmedRef.current && isGuardedSameUrlEntry()) {
        // Cảnh báo vừa được gỡ có chủ đích (đã tải/xóa file kết quả): bỏ mục
        // sentinel để Back tiếp theo hoạt động bình thường. popstate do
        // history.back() phát sinh chạy sau khi listener đã gỡ nên không bị xử lý
        // lại; nếu lỡ có mount khác đang nghe, state đáp xuống là entry thật
        // (không token).
        window.history.back();
      }
    };
  }, [shouldWarnOnLeave, batchId]);

  useEffect(() => {
    if (!shouldWarnOnLeave) return;
    const handleDocumentClick = (event: MouseEvent) => {
      if (
        event.defaultPrevented ||
        event.button !== 0 ||
        event.metaKey ||
        event.ctrlKey ||
        event.shiftKey ||
        event.altKey
      ) {
        return;
      }
      const target = event.target;
      if (!(target instanceof Element)) return;
      const anchor = target.closest("a[href]");
      if (!(anchor instanceof HTMLAnchorElement)) return;
      if (anchor.hasAttribute("download") || anchor.target === "_blank") return;
      const rawHref = anchor.getAttribute("href");
      if (!rawHref || rawHref.startsWith("#")) return;

      let resolvedUrl: URL;
      try {
        resolvedUrl = new URL(anchor.href, window.location.href);
      } catch {
        return;
      }
      if (resolvedUrl.origin !== window.location.origin) return;
      const nextRoute = `${resolvedUrl.pathname}${resolvedUrl.search}${resolvedUrl.hash}`;
      const currentRoute = `${window.location.pathname}${window.location.search}${window.location.hash}`;
      if (nextRoute === currentRoute) return;

      event.preventDefault();
      event.stopPropagation();
      setPendingLeave({ kind: "href", href: nextRoute });
    };

    document.addEventListener("click", handleDocumentClick, true);
    return () => document.removeEventListener("click", handleDocumentClick, true);
  }, [shouldWarnOnLeave]);

  const formatRowErrorText = useCallback(
    (row: ImportRow): string => {
      if (row.errors.length === 0) return "";
      return row.errors
        .map((err) => {
          const colKey = mapImportColumnKey(err.columnKey);
          const colLabel = colKey ? t(`columns.${colKey}`) : err.column;
          const codeKey = mapImportRowErrorCode(err.code);
          const msg = codeKey
            ? t(`rowErrorCodes.${codeKey}`, { column: colLabel })
            : t("errorCodes.VALIDATION_FAILED");
          return t("preview.rowErrorItem", { column: colLabel, message: msg });
        })
        .join(" · ");
    },
    [t],
  );

  const downloadErrorsMutation = useMutation({
    mutationFn: () => deps.downloadImportErrors(batchId),
    onSuccess: ({ blob, filename }) => {
      setActionError(null);
      deps.saveBlobToDisk(blob, filename);
    },
    onError: (error) => {
      const message = resolveImportApiErrorMessage(
        error,
        (code) => t(`errorCodes.${code}`),
        t("preview.downloadErrorsError"),
      );
      setActionError(message);
      notify.error(message);
    },
  });

  const confirmMutation = useMutation({
    mutationFn: () => {
      if (!idempotencyKeyRef.current) {
        idempotencyKeyRef.current = generateIdempotencyKey();
      }
      return deps.confirmImportBatch(batchId, idempotencyKeyRef.current);
    },
    onSuccess: (updatedBatch) => {
      setActionError(null);
      idempotencyKeyRef.current = null;
      setConfirmStepActive(false);
      setPage(1);
      queryClient.setQueryData(BULK_IMPORT_QUERY_KEYS.detail(batchId), updatedBatch);
    },
    onError: (error) => {
      const apiCode = error instanceof BulkImportApiError ? error.code : undefined;
      if (apiCode === "IMPORT_HAS_ERRORS") {
        idempotencyKeyRef.current = null;
        setConfirmStepActive(false);
        setPage(1);
        const message = t("confirm.errorsRemainWarning");
        setActionError(message);
        notify.error(message);
        void queryClient.invalidateQueries({ queryKey: BULK_IMPORT_QUERY_KEYS.detail(batchId) });
        void queryClient.invalidateQueries({ queryKey: ["bulk-import", "rows", batchId] });
        return;
      }
      if (apiCode === "IMPORT_ALREADY_CONFIRMED") {
        idempotencyKeyRef.current = null;
        setConfirmStepActive(false);
        void queryClient.invalidateQueries({ queryKey: BULK_IMPORT_QUERY_KEYS.detail(batchId) });
        return;
      }
      if (apiCode === "IDEMPOTENCY_KEY_REUSED") {
        idempotencyKeyRef.current = null;
      }
      const message = resolveImportApiErrorMessage(
        error,
        (code) => t(`errorCodes.${code}`),
        t("errorCodes.INTERNAL_ERROR"),
      );
      setActionError(message);
      notify.error(message);
    },
  });

  const downloadResultMutation = useMutation({
    mutationFn: () => deps.downloadImportResult(batchId),
    onSuccess: ({ blob, filename }) => {
      setActionError(null);
      setHasDownloadedResult(true);
      leaveWarningDisarmedRef.current = true;
      deps.saveBlobToDisk(blob, filename);
      notify.success(t("result.downloadResultSuccess"));
    },
    onError: (error) => {
      const message = resolveImportApiErrorMessage(
        error,
        (code) => t(`errorCodes.${code}`),
        t("result.downloadResultError"),
      );
      setActionError(message);
      notify.error(message);
    },
  });

  const deleteResultMutation = useMutation({
    mutationFn: () => deps.deleteImportResult(batchId),
    onSuccess: () => {
      setActionError(null);
      setDeleteConfirmOpen(false);
      leaveWarningDisarmedRef.current = true;
      queryClient.setQueryData<ImportBatch>(
        BULK_IMPORT_QUERY_KEYS.detail(batchId),
        (prev) => (prev ? { ...prev, resultAvailable: false } : prev),
      );
      notify.success(t("result.deleteResultSuccess"));
    },
    onError: (error) => {
      const message = resolveImportApiErrorMessage(
        error,
        (code) => t(`errorCodes.${code}`),
        t("result.deleteResultError"),
      );
      setActionError(message);
      notify.error(message);
    },
  });

  const previewColumns = useMemo<ColumnDef<ImportRow>[]>(
    () => [
      {
        accessorKey: "rowNo",
        header: t("preview.table.rowNo"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="text-text-muted tabular-nums">{num(row.original.rowNo)}</span>
        ),
      },
      {
        accessorKey: "username",
        header: t("preview.table.username"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="font-medium text-text">{row.original.username ?? "—"}</span>
        ),
      },
      {
        id: "password",
        header: t("preview.table.password"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="text-text-muted">
            {row.original.passwordGenerated
              ? t("preview.passwordGenerated")
              : t("preview.passwordProvided")}
          </span>
        ),
      },
      {
        accessorKey: "fullName",
        header: t("preview.table.fullName"),
        enableSorting: false,
        cell: ({ row }) => <span className="text-text">{row.original.fullName ?? "—"}</span>,
      },
      {
        accessorKey: "email",
        header: t("preview.table.email"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="text-text-secondary">{row.original.email ?? "—"}</span>
        ),
      },
      {
        accessorKey: "classCode",
        header: t("preview.table.classCode"),
        enableSorting: false,
        cell: ({ row }) => <span className="text-text">{row.original.classCode ?? "—"}</span>,
      },
      {
        id: "result",
        header: t("preview.table.result"),
        enableSorting: false,
        cell: ({ row }) => {
          const item = row.original;
          const isValid = item.status === "VALID" && item.errors.length === 0;
          if (isValid) {
            return <StatusChip tone="success">{t("preview.statusValid")}</StatusChip>;
          }
          return (
            <div className="flex flex-wrap items-center gap-2 py-1">
              <StatusChip tone="danger">{t("preview.statusInvalid")}</StatusChip>
              <span className="text-field-label text-danger">{formatRowErrorText(item)}</span>
            </div>
          );
        },
      },
    ],
    [t, num, formatRowErrorText],
  );

  const resultColumns = useMemo<ColumnDef<ImportRow>[]>(
    () => [
      {
        accessorKey: "rowNo",
        header: t("result.table.rowNo"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="text-text-muted tabular-nums">{num(row.original.rowNo)}</span>
        ),
      },
      {
        accessorKey: "username",
        header: t("result.table.username"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="font-medium text-text">{row.original.username ?? "—"}</span>
        ),
      },
      {
        accessorKey: "fullName",
        header: t("result.table.fullName"),
        enableSorting: false,
        cell: ({ row }) => <span className="text-text">{row.original.fullName ?? "—"}</span>,
      },
      {
        accessorKey: "classCode",
        header: t("result.table.classCode"),
        enableSorting: false,
        cell: ({ row }) => <span className="text-text">{row.original.classCode ?? "—"}</span>,
      },
      {
        id: "password",
        header: t("result.table.password"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="text-text-secondary">
            {row.original.passwordGenerated
              ? t("result.passwordGenerated")
              : t("result.passwordProvided")}
          </span>
        ),
      },
      {
        id: "result",
        header: t("result.table.result"),
        enableSorting: false,
        cell: ({ row }) => {
          const item = row.original;
          if (item.status === "CREATED") {
            return <StatusChip tone="success">{t("result.rowStatus.CREATED")}</StatusChip>;
          }
          if (item.status === "FAILED") {
            const failureKey = mapImportFailureCode(item.failureCode);
            return (
              <div className="flex flex-wrap items-center gap-2 py-1">
                <StatusChip tone="danger">{t("result.rowStatus.FAILED")}</StatusChip>
                <span className="text-field-label text-danger">
                  {t(`failureCodes.${failureKey}`)}
                </span>
              </div>
            );
          }
          if (item.status === "INVALID") {
            return (
              <div className="flex flex-wrap items-center gap-2 py-1">
                <StatusChip tone="danger">{t("result.rowStatus.INVALID")}</StatusChip>
                <span className="text-field-label text-danger">{formatRowErrorText(item)}</span>
              </div>
            );
          }
          return <StatusChip tone="neutral">{t("result.rowStatus.VALID")}</StatusChip>;
        },
      },
    ],
    [t, num, formatRowErrorText],
  );

  // `labels` của Pagination là object literal: memoize để prop identity ổn định
  // giữa các lần render (Pagination không memo nên chỉ là tránh cấp phát thừa,
  // nhưng giữ convention "mọi object prop đều ổn định").
  const paginationLabels = useMemo(
    () => ({
      nav: t("pagination.nav"),
      previous: t("pagination.previous"),
      next: t("pagination.next"),
      rowsPerPage: t("pagination.rowsPerPage"),
      page: (pageNum: number) => t("pagination.page", { page: pageNum }),
      range: ({ from, to, total }: { from: number; to: number; total: number }) =>
        t("pagination.range", { from, to, total }),
    }),
    [t],
  );

  if (batchQuery.isLoading) {
    return <BulkImportBatchSkeleton />;
  }

  if (batchQuery.isError) {
    const isNotFound = getErrorStatus(batchQuery.error) === 404;
    return (
      <div className="flex flex-col gap-4">
        <BulkImportHeader deps={deps} />
        <Card>
          <EmptyState
            icon={<FileSpreadsheet />}
            title={isNotFound ? t("batchError.notFoundTitle") : t("batchError.title")}
            description={
              isNotFound
                ? t("batchError.notFoundDescription")
                : resolveImportApiErrorMessage(
                    batchQuery.error,
                    (code) => t(`errorCodes.${code}`),
                    t("batchError.description"),
                  )
            }
            action={
              isNotFound ? (
                <Button asChild variant="secondary">
                  <Link href="/users/bulk-import">{t("batchError.startOver")}</Link>
                </Button>
              ) : (
                <Button variant="secondary" onClick={() => void batchQuery.refetch()}>
                  {common("retry")}
                </Button>
              )
            }
          />
        </Card>
      </div>
    );
  }

  if (!batch) return null;

  const rowItems = rowsQuery.data?.items ?? EMPTY_ROW_ITEMS;
  const totalRowItems = rowsQuery.data?.totalItems ?? 0;

  return (
    <div className="flex flex-col gap-4">
      <BulkImportHeader deps={deps} />
      <BulkImportStepper currentStep={currentStep} />

      {actionError && (
        <div
          role="alert"
          className="rounded-md border border-danger/30 bg-danger-bg px-4 py-3 text-body text-danger"
        >
          {actionError}
        </div>
      )}

      {/* Bước 2: Xem trước & lỗi theo dòng */}
      {(batch.status === "UPLOADED" || (batch.status === "VALIDATED" && !confirmStepActive)) && (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Card padding="md">
              <div className="text-body text-text-muted">{t("preview.cards.file")}</div>
              <div className="mt-1 truncate text-body font-semibold text-text">
                {batch.originalFilename ?? "—"}
              </div>
            </Card>
            <Card padding="md">
              <div className="text-body text-text-muted">{t("preview.cards.total")}</div>
              <div className="mt-1 text-page-title font-bold text-text tabular-nums">
                {num(batch.totalRows)}
              </div>
            </Card>
            <Card padding="md">
              <div className="text-body text-text-muted">{t("preview.cards.valid")}</div>
              <div className="mt-1 text-page-title font-bold text-success tabular-nums">
                {num(batch.validRows)}
              </div>
            </Card>
            <Card
              padding="md"
              className={batch.invalidRows > 0 ? "border-danger/40" : undefined}
            >
              <div className="text-body text-text-muted">{t("preview.cards.invalid")}</div>
              <div
                className={cn(
                  "mt-1 text-page-title font-bold tabular-nums",
                  batch.invalidRows > 0 ? "text-danger" : "text-text",
                )}
              >
                {num(batch.invalidRows)}
              </div>
            </Card>
          </div>

          <Card padding="md" className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2 text-body">
              <span className="text-text-muted">{t("preview.mustChangePasswordLabel")}</span>
              <span className="font-semibold text-text">
                {batch.mustChangePassword ? t("preview.yes") : t("preview.no")}
              </span>
            </div>
          </Card>

          <Card padding="none" className="overflow-hidden">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3">
              <strong className="text-body font-semibold text-text">
                {t("preview.sectionTitle")}
              </strong>
              <div className="flex flex-wrap items-center gap-3">
                {batch.invalidRows > 0 && (
                  <div className="flex items-center gap-2">
                    <Checkbox
                      id={onlyErrorsId}
                      checked={onlyErrors}
                      onCheckedChange={(checked) => {
                        setOnlyErrors(checked === true);
                        setPage(1);
                      }}
                    />
                    <Label
                      htmlFor={onlyErrorsId}
                      className="cursor-pointer font-normal text-text"
                    >
                      {t("preview.onlyErrors")}
                    </Label>
                  </div>
                )}

                {batch.invalidRows > 0 && batch.sourceAvailable && (
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    loading={downloadErrorsMutation.isPending}
                    onClick={() => downloadErrorsMutation.mutate()}
                    className="border-danger/40 text-danger hover:bg-danger-bg"
                  >
                    <Download />
                    <span>{t("preview.downloadErrors", { count: batch.invalidRows })}</span>
                  </Button>
                )}
              </div>
            </div>

            <Table
              aria-label={t("preview.tableAriaLabel")}
              columns={previewColumns}
              data={rowItems}
              getRowId={getImportRowId}
              loading={rowsQuery.isLoading}
              refreshing={rowsQuery.isPlaceholderData}
              error={
                rowsQuery.isError ? (
                  <EmptyState
                    icon={<FileSpreadsheet />}
                    title={t("preview.errorTitle")}
                    description={t("preview.errorDescription")}
                    action={
                      <Button variant="secondary" onClick={() => void rowsQuery.refetch()}>
                        {common("retry")}
                      </Button>
                    }
                  />
                ) : undefined
              }
              empty={
                <EmptyState
                  icon={<FileSpreadsheet />}
                  title={t("preview.emptyTitle")}
                  description={t("preview.emptyDescription")}
                />
              }
            />

            {totalRowItems > 0 && (
              <div className="border-t border-border px-4 py-3">
                <Pagination
                  page={page}
                  pageSize={pageSize}
                  total={totalRowItems}
                  onPageChange={setPage}
                  pageSizeOptions={PAGE_SIZE_OPTIONS}
                  onPageSizeChange={(nextSize) => {
                    setPageSize(nextSize);
                    setPage(1);
                  }}
                  labels={paginationLabels}
                />
              </div>
            )}

            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-4 py-3.5">
              <div
                className={cn(
                  "text-body",
                  batch.invalidRows > 0 ? "text-danger" : "text-success",
                )}
              >
                {batch.invalidRows > 0
                  ? t("preview.errorsRemainHint", { count: batch.invalidRows })
                  : t("preview.readyHint", { count: batch.validRows })}
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <Button asChild variant="secondary">
                  <Link href="/users/bulk-import">
                    <Upload />
                    <span>{t("preview.reupload")}</span>
                  </Link>
                </Button>
                <Button
                  type="button"
                  disabled={!batch.canConfirm || batch.invalidRows > 0}
                  onClick={() => {
                    setActionError(null);
                    setConfirmStepActive(true);
                  }}
                >
                  <CheckCircle2 />
                  <span>{t("preview.continueConfirm", { count: batch.validRows })}</span>
                </Button>
              </div>
            </div>
          </Card>

          <p className="text-caption text-text-muted">{t("preview.footnote")}</p>
        </>
      )}

      {/* Bước 3: Xác nhận */}
      {batch.status === "VALIDATED" && confirmStepActive && (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Card padding="md">
              <div className="text-body text-text-muted">{t("confirm.cards.file")}</div>
              <div className="mt-1 truncate text-body font-semibold text-text">
                {batch.originalFilename ?? "—"}
              </div>
            </Card>
            <Card padding="md">
              <div className="text-body text-text-muted">{t("confirm.cards.willCreate")}</div>
              <div className="mt-1 text-page-title font-bold text-primary-text tabular-nums">
                {t("confirm.cards.willCreateValue", { count: batch.validRows })}
              </div>
            </Card>
            <Card padding="md">
              <div className="text-body text-text-muted">{t("confirm.cards.passwordMode")}</div>
              <div className="mt-1 text-body font-semibold text-text">
                {batch.hasGeneratedPasswords
                  ? t("confirm.passwordHasGenerated")
                  : t("confirm.passwordAllFromFile")}
              </div>
            </Card>
            <Card padding="md">
              <div className="text-body text-text-muted">
                {t("confirm.cards.mustChangePassword")}
              </div>
              <div className="mt-1 text-body font-semibold text-text">
                {batch.mustChangePassword ? t("confirm.yes") : t("confirm.no")}
              </div>
            </Card>
          </div>

          <Card padding="lg" className="flex flex-col gap-4">
            <h2 className="text-title font-semibold text-text">{t("confirm.sectionTitle")}</h2>

            <dl className="grid grid-cols-1 gap-x-6 gap-y-2.5 sm:grid-cols-[200px_1fr]">
              <dt className="text-body text-text-muted">{t("confirm.fields.role")}</dt>
              <dd className="text-body font-medium text-text">{t("confirm.fields.roleValue")}</dd>
              <dt className="text-body text-text-muted">{t("confirm.fields.passwordMode")}</dt>
              <dd className="text-body font-medium text-text">
                {batch.hasGeneratedPasswords
                  ? t("confirm.passwordHasGenerated")
                  : t("confirm.passwordAllFromFile")}
              </dd>
              <dt className="text-body text-text-muted">
                {t("confirm.fields.mustChangePassword")}
              </dt>
              <dd className="text-body font-medium text-text">
                {batch.mustChangePassword ? t("confirm.yes") : t("confirm.no")}
              </dd>
              <dt className="text-body text-text-muted">{t("confirm.fields.invalidRows")}</dt>
              <dd className="text-body font-medium text-text">
                {t("confirm.fields.invalidRowsValue")}
              </dd>
            </dl>

            <div className="flex items-start gap-2.5 rounded-md border border-warning/30 bg-warning-bg px-3.5 py-3 text-field-label text-warning">
              <AlertTriangle aria-hidden className="mt-0.5 size-4 shrink-0" />
              <span>{t("confirm.warning")}</span>
            </div>

            <div className="flex flex-wrap justify-end gap-2">
              <Button
                type="button"
                variant="secondary"
                disabled={confirmMutation.isPending}
                onClick={() => {
                  setActionError(null);
                  setConfirmStepActive(false);
                }}
              >
                <ArrowLeft />
                <span>{t("confirm.backToPreview")}</span>
              </Button>
              <Button
                type="button"
                loading={confirmMutation.isPending}
                onClick={() => confirmMutation.mutate()}
              >
                <CheckCircle2 />
                <span>{t("confirm.confirmAction", { count: batch.validRows })}</span>
              </Button>
            </div>
          </Card>
        </>
      )}

      {/* Bước 4: Đang xử lý */}
      {(batch.status === "CONFIRMED" || batch.status === "PROCESSING") && (
        <Card padding="lg" className="flex flex-col gap-4">
          {(() => {
            const processed = batch.createdRows + batch.failedRows;
            const total = Math.max(batch.totalRows, 1);
            const remaining = Math.max(0, batch.totalRows - processed);
            const percent = Math.min(100, Math.round((processed / total) * 100));
            return (
              <>
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <h2 className="text-title font-semibold text-text">{t("processing.title")}</h2>
                  <div aria-live="polite" className="text-body font-semibold text-text tabular-nums">
                    {t("processing.progressValue", {
                      processed,
                      total: batch.totalRows,
                    })}
                  </div>
                </div>

                <div
                  role="progressbar"
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={percent}
                  aria-label={t("processing.progressAriaLabel")}
                  className="h-2.5 overflow-hidden rounded-full bg-subtle"
                >
                  <div
                    className="h-full bg-primary-fill transition-all duration-150 motion-reduce:transition-none"
                    style={{ width: `${percent}%` }}
                  />
                </div>

                <div className="text-body text-text-muted tabular-nums">
                  {t("processing.counts", {
                    created: batch.createdRows,
                    failed: batch.failedRows,
                    remaining,
                  })}
                </div>

                <div className="rounded-md border border-border bg-page px-3.5 py-3 text-field-label text-text-secondary">
                  {t("processing.awayNotice")}
                </div>
              </>
            );
          })()}
        </Card>
      )}

      {/* Bước 4: Kết quả */}
      {isTerminal && (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Card padding="md">
              <div className="text-body text-text-muted">{t("result.cards.file")}</div>
              <div className="mt-1 truncate text-body font-semibold text-text">
                {batch.originalFilename ?? "—"}
              </div>
            </Card>
            <Card padding="md">
              <div className="text-body text-text-muted">{t("result.cards.processed")}</div>
              <div className="mt-1 text-page-title font-bold text-text tabular-nums">
                {t("result.cards.processedValue", {
                  processed: batch.createdRows + batch.failedRows,
                  total: batch.totalRows,
                })}
              </div>
            </Card>
            <Card padding="md">
              <div className="text-body text-text-muted">{t("result.cards.created")}</div>
              <div className="mt-1 text-page-title font-bold text-success tabular-nums">
                {num(batch.createdRows)}
              </div>
            </Card>
            <Card padding="md" className={batch.failedRows > 0 ? "border-danger/40" : undefined}>
              <div className="text-body text-text-muted">{t("result.cards.failed")}</div>
              <div
                className={cn(
                  "mt-1 text-page-title font-bold tabular-nums",
                  batch.failedRows > 0 ? "text-danger" : "text-text",
                )}
              >
                {num(batch.failedRows)}
              </div>
            </Card>
          </div>

          {batch.status === "FAILED" && (
            <div
              role="alert"
              className="rounded-md border border-danger/30 bg-danger-bg px-4 py-3 text-body text-danger"
            >
              {t("result.failedBanner")}
            </div>
          )}

          {batch.resultAvailable ? (
            <div
              className={cn(
                "flex flex-wrap items-center justify-between gap-3 rounded-lg border px-4 py-3.5",
                hasDownloadedResult || !batch.hasGeneratedPasswords
                  ? "border-border bg-surface text-text"
                  : "border-warning/30 bg-warning-bg text-warning",
              )}
            >
              <div className="flex min-w-0 flex-1 items-start gap-2.5 text-body">
                {batch.hasGeneratedPasswords && !hasDownloadedResult && (
                  <AlertTriangle aria-hidden className="mt-0.5 size-5 shrink-0" />
                )}
                <span>
                  {hasDownloadedResult
                    ? t("result.downloadedNotice")
                    : batch.hasGeneratedPasswords
                      ? t("result.bannerGeneratedWarning")
                      : t("result.bannerReadyInfo")}
                </span>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <Button
                  type="button"
                  loading={downloadResultMutation.isPending}
                  onClick={() => downloadResultMutation.mutate()}
                >
                  <Download />
                  <span>{t("result.downloadResult")}</span>
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setDeleteConfirmOpen(true)}
                  className="border-danger/40 text-danger hover:bg-danger-bg"
                >
                  <Trash2 />
                  <span>{t("result.deleteResult")}</span>
                </Button>
              </div>
            </div>
          ) : (
            <Card padding="md" className="text-body text-text-muted">
              {t("result.deletedNotice")}
            </Card>
          )}

          <Card padding="none" className="overflow-hidden">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3">
              <strong className="text-body font-semibold text-text">
                {t("result.sectionTitle")}
              </strong>
              <div className="flex items-center gap-2">
                <Checkbox
                  id={onlyFailedId}
                  checked={onlyFailed}
                  onCheckedChange={(checked) => {
                    setOnlyFailed(checked === true);
                    setPage(1);
                  }}
                />
                <Label htmlFor={onlyFailedId} className="cursor-pointer font-normal text-text">
                  {t("result.onlyFailed")}
                </Label>
              </div>
            </div>

            <Table
              aria-label={t("result.tableAriaLabel")}
              columns={resultColumns}
              data={rowItems}
              getRowId={getImportRowId}
              loading={rowsQuery.isLoading}
              refreshing={rowsQuery.isPlaceholderData}
              error={
                rowsQuery.isError ? (
                  <EmptyState
                    icon={<FileSpreadsheet />}
                    title={t("result.errorTitle")}
                    description={t("result.errorDescription")}
                    action={
                      <Button variant="secondary" onClick={() => void rowsQuery.refetch()}>
                        {common("retry")}
                      </Button>
                    }
                  />
                ) : undefined
              }
              empty={
                <EmptyState
                  icon={<FileSpreadsheet />}
                  title={t("result.emptyTitle")}
                  description={t("result.emptyDescription")}
                />
              }
            />

            {totalRowItems > 0 && (
              <div className="border-t border-border px-4 py-3">
                <Pagination
                  page={page}
                  pageSize={pageSize}
                  total={totalRowItems}
                  onPageChange={setPage}
                  pageSizeOptions={PAGE_SIZE_OPTIONS}
                  onPageSizeChange={(nextSize) => {
                    setPageSize(nextSize);
                    setPage(1);
                  }}
                  labels={paginationLabels}
                />
              </div>
            )}

            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-4 py-3.5">
              <div className="text-body text-text-muted">
                {batch.failedRows > 0 ? t("result.failedHint") : t("result.allSucceededHint")}
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <Button asChild variant="secondary">
                  <Link href="/users/bulk-import">{t("result.startAnother")}</Link>
                </Button>
                <Button asChild variant="secondary">
                  <Link href="/users">{t("result.goToUsers")}</Link>
                </Button>
              </div>
            </div>
          </Card>
        </>
      )}

      <ConfirmDialog
        open={deleteConfirmOpen}
        onOpenChange={setDeleteConfirmOpen}
        title={t("result.deleteConfirm.title")}
        description={t("result.deleteConfirm.description")}
        confirmLabel={t("result.deleteConfirm.confirm")}
        cancelLabel={t("result.deleteConfirm.cancel")}
        closeLabel={t("result.deleteConfirm.close")}
        tone="danger"
        loading={deleteResultMutation.isPending}
        onConfirm={() => deleteResultMutation.mutate()}
      />

      <ConfirmDialog
        open={pendingLeave !== null}
        onOpenChange={(open) => {
          if (!open) setPendingLeave(null);
        }}
        title={t("leaveWarning.title")}
        description={t("leaveWarning.description")}
        confirmLabel={t("leaveWarning.confirm")}
        cancelLabel={t("leaveWarning.cancel")}
        closeLabel={t("leaveWarning.close")}
        tone="danger"
        onConfirm={() => {
          const leave = pendingLeave;
          setPendingLeave(null);
          if (!leave) return;
          if (leave.kind === "href") {
            router.push(leave.href);
            return;
          }
          // Rời bằng điều hướng lịch sử: đang đứng trên sentinel, phía dưới là trang
          // hiện tại rồi trang trước đó -> history.go(-2) tới đúng trang đích.
          historyLeavingRef.current = true;
          window.history.go(-2);
        }}
      />
    </div>
  );
}
