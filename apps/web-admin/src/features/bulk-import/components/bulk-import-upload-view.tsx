"use client";

import { useMutation } from "@tanstack/react-query";
import {
  Button,
  buttonVariants,
  Card,
  Checkbox,
  cn,
  EmptyState,
  IconButton,
  Label,
  Skeleton,
  StatusChip,
} from "@thi360/ui";
import { FileSpreadsheet, TriangleAlert, Upload, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import {
  type ChangeEvent,
  type DragEvent,
  useEffect,
  useId,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { ForbiddenView } from "@/features/session/forbidden-view";
import { usePermissions, useSession } from "@/features/session/session-provider";
import { type BulkImportApiDeps, defaultBulkImportApiDeps } from "../api";
import { resolveImportApiErrorMessage, validateSelectedExcelFile } from "../utils";
import { BulkImportHeader } from "./bulk-import-header";
import { BulkImportStepper } from "./bulk-import-stepper";

/**
 * Bề rộng thẻ (px) dưới đó bảng các cột của tệp mẫu thành danh sách thẻ: 32rem, chỗ ba cột Cột, Bắt buộc, Quy tắc
 * còn đọc được mà không ép quy tắc xuống mỗi chữ một dòng. Trên máy tính thẻ này hẹp nhất khoảng 513px (cửa sổ
 * khoảng 1210px, hai thẻ vừa đứng cạnh nhau ở `@4xl`), nên từ 1024px trở lên vẫn là bảng.
 */
const TEMPLATE_COLUMNS_CARD_BREAKPOINT = 512;

const useIsomorphicLayoutEffect = typeof window === "undefined" ? useEffect : useLayoutEffect;

/**
 * Khung có hẹp hơn `breakpoint` không, đo bằng `ResizeObserver` như `Table` của kit làm với `cards` (bảng này tự viết
 * bằng `<table>` nên không dùng được `Table`). Chỉ dựng một trong hai dạng, không dựng cả hai rồi ẩn bằng CSS, để
 * nội dung không lặp trong DOM và cây truy cập. Chưa đo được (máy chủ, jsdom, bề rộng 0) thì là bảng.
 */
function useNarrowContainer(breakpoint: number) {
  const ref = useRef<HTMLDivElement>(null);
  const [narrow, setNarrow] = useState(false);

  useIsomorphicLayoutEffect(() => {
    const element = ref.current;
    if (!element) return;
    const measure = () => {
      const width = element.getBoundingClientRect().width;
      if (width > 0) setNarrow(width < breakpoint);
    };
    measure();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [breakpoint]);

  return [ref, narrow] as const;
}

export interface BulkImportUploadViewProps {
  deps?: Partial<BulkImportApiDeps>;
}

export function BulkImportUploadView({ deps }: BulkImportUploadViewProps) {
  const t = useTranslations("bulkImport");
  const common = useTranslations("common");
  const sessionState = useSession();
  const { can } = usePermissions();
  // Giữ identity `deps` ổn định giữa các render (xem ghi chú tương tự ở batch view).
  const apiDeps = useMemo<BulkImportApiDeps>(
    () => ({ ...defaultBulkImportApiDeps, ...deps }),
    [deps],
  );

  if (sessionState.status === "loading") {
    return <BulkImportUploadSkeleton />;
  }

  if (sessionState.status === "error") {
    return (
      <SessionErrorView
        title={t("sessionError.title")}
        description={t("sessionError.description")}
        retryLabel={common("retry")}
        onRetry={sessionState.retry}
      />
    );
  }

  if (!can("USER.MODIFY")) {
    return <ForbiddenView />;
  }

  return <BulkImportUploadContent deps={apiDeps} />;
}

function SessionErrorView({
  title,
  description,
  retryLabel,
  onRetry,
}: {
  title: string;
  description: string;
  retryLabel: string;
  onRetry: () => void;
}) {
  return (
    <Card>
      <EmptyState
        icon={<TriangleAlert />}
        title={title}
        description={description}
        action={<Button onClick={onRetry}>{retryLabel}</Button>}
      />
    </Card>
  );
}

function BulkImportUploadSkeleton() {
  const common = useTranslations("common");
  return (
    <div role="status" aria-label={common("loading")} className="@container flex flex-col gap-4">
      <Skeleton className="h-12 w-72 max-w-full" />
      <Skeleton className="h-16 w-full" />
      <div className="grid gap-4 @4xl:grid-cols-12">
        <Skeleton className="h-80 w-full @4xl:col-span-5" />
        <Skeleton className="h-80 w-full @4xl:col-span-7" />
      </div>
    </div>
  );
}

function BulkImportUploadContent({ deps }: { deps: BulkImportApiDeps }) {
  const t = useTranslations("bulkImport");
  const router = useRouter();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const mustChangeId = useId();
  const fileInputId = useId();
  const chooseFileLabelId = useId();
  const dropzoneHintId = useId();
  const columnsHeadingId = useId();
  const [columnsFrameRef, columnsAsCards] = useNarrowContainer(TEMPLATE_COLUMNS_CARD_BREAKPOINT);

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [mustChangePassword, setMustChangePassword] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const uploadMutation = useMutation({
    mutationFn: ({ file, forceChange }: { file: File; forceChange: boolean }) =>
      deps.uploadImportBatch(file, forceChange),
    onSuccess: (batch) => {
      router.push(`/users/bulk-import/${batch.id}`);
    },
    onError: (error) => {
      setErrorMessage(
        resolveImportApiErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("upload.uploadErrorFallback"),
        ),
      );
    },
  });

  function acceptFile(file: File | undefined) {
    if (!file) return;
    const validationCode = validateSelectedExcelFile(file);
    if (validationCode) {
      setSelectedFile(null);
      setErrorMessage(t(`errorCodes.${validationCode}`));
      return;
    }
    setErrorMessage(null);
    setSelectedFile(file);
  }

  function handleFileInputChange(event: ChangeEvent<HTMLInputElement>) {
    acceptFile(event.target.files?.[0]);
    event.target.value = "";
  }

  function handleDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    setIsDragging(false);
    acceptFile(event.dataTransfer.files?.[0]);
  }

  function handleRemoveFile() {
    setSelectedFile(null);
    setErrorMessage(null);
    // Nút bỏ tệp biến mất cùng ô tệp: đưa focus về ô chọn tệp để chọn tệp khác ngay.
    fileInputRef.current?.focus();
  }

  function handleUploadSubmit() {
    if (!selectedFile || uploadMutation.isPending) return;
    setErrorMessage(null);
    uploadMutation.mutate({ file: selectedFile, forceChange: mustChangePassword });
  }

  const templateColumns = [
    {
      name: t("columns.USERNAME"),
      required: true,
      rules: t("upload.ruleUsername"),
    },
    {
      name: t("columns.PASSWORD"),
      required: false,
      rules: t("upload.rulePassword"),
    },
    {
      name: t("columns.FULL_NAME"),
      required: true,
      rules: t("upload.ruleFullName"),
    },
    {
      name: t("columns.EMAIL"),
      required: false,
      rules: t("upload.ruleEmail"),
    },
    {
      name: t("columns.PHONE"),
      required: false,
      rules: t("upload.rulePhone"),
    },
    {
      name: t("columns.CLASS_CODE"),
      required: false,
      rules: t("upload.ruleClassCode"),
    },
  ];

  return (
    <div className="@container flex flex-col gap-4">
      <BulkImportHeader deps={deps} />
      <BulkImportStepper currentStep={1} />

      {/*
        Hai thẻ cạnh nhau khi vùng nội dung đủ rộng cho bảng các cột (từ `@4xl`, theo bề rộng vùng chứa chứ không
        theo màn hình), dưới đó xếp chồng.
      */}
      <div className="grid items-start gap-4 @4xl:grid-cols-12">
        <Card padding="lg" className="flex flex-col gap-4 @4xl:col-span-5">
          <h2 className="text-title font-semibold text-text">{t("upload.sectionTitle")}</h2>

          {/*
            Một điều khiển duy nhất: ô chọn tệp thật (ẩn về hình ảnh, vẫn nhận Tab và Enter/Space) và cả vùng thả
            là nhãn của nó, nên bấm vào đâu trong vùng cũng mở hộp chọn tệp. Ô nhập đứng trước nhãn để lớp `peer`
            vẽ vòng focus lên vùng thả khi ô nhập nhận focus bằng bàn phím.
          */}
          <div className="relative">
            <input
              ref={fileInputRef}
              id={fileInputId}
              type="file"
              aria-labelledby={chooseFileLabelId}
              aria-describedby={dropzoneHintId}
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={handleFileInputChange}
              className="peer sr-only"
            />
            <label
              htmlFor={fileInputId}
              onDragOver={(event) => {
                event.preventDefault();
                setIsDragging(true);
              }}
              onDragLeave={() => setIsDragging(false)}
              onDrop={handleDrop}
              className={cn(
                "flex cursor-pointer flex-col items-center gap-2.5 rounded-xl border-2 border-dashed px-5 py-9 text-center transition-colors duration-150 motion-reduce:transition-none",
                "peer-focus-visible:outline-3 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary-text",
                isDragging
                  ? "border-primary-text bg-primary-50"
                  : "border-primary-text/40 bg-surface hover:bg-primary-50",
              )}
            >
              <span
                aria-hidden
                className="flex size-13 items-center justify-center rounded-xl bg-primary-50 text-primary-text"
              >
                <Upload className="size-6" />
              </span>
              <span className="text-body font-semibold text-text">{t("upload.dropzoneTitle")}</span>
              <span id={dropzoneHintId} className="text-body text-text-muted">
                {t("upload.dropzoneHint")}
              </span>
              <span
                id={chooseFileLabelId}
                className={cn(
                  buttonVariants({ variant: selectedFile ? "secondary" : "primary" }),
                  "gap-2",
                )}
              >
                <Upload aria-hidden className="size-4" />
                <span>{selectedFile ? t("upload.changeFile") : t("upload.chooseFile")}</span>
              </span>
            </label>
          </div>

          {selectedFile && (
            <div className="flex items-center justify-between gap-3 rounded-md border border-border bg-subtle px-3.5 py-2.5">
              <div className="flex min-w-0 items-center gap-2.5">
                <FileSpreadsheet aria-hidden className="size-5 shrink-0 text-primary-text" />
                <div className="min-w-0">
                  <div className="text-caption text-text-muted">{t("upload.selectedFileLabel")}</div>
                  {/* Tên tệp chỉ hiện ở đây: xuống dòng cho đủ, không cắt mất phần đuôi .xlsx (DESIGN.md 5.13). */}
                  <div className="text-body font-semibold wrap-anywhere text-text">
                    {selectedFile.name}
                  </div>
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-1">
                <span className="text-caption text-text-muted tabular-nums">
                  {t("upload.fileSizeKb", {
                    size: Math.max(1, Math.round(selectedFile.size / 1024)),
                  })}
                </span>
                <IconButton
                  icon={<X />}
                  aria-label={t("upload.removeFile", { name: selectedFile.name })}
                  tooltip={t("upload.removeFileTooltip")}
                  disabled={uploadMutation.isPending}
                  onClick={handleRemoveFile}
                />
              </div>
            </div>
          )}

          <div className="flex min-h-10 items-center gap-2">
            <Checkbox
              id={mustChangeId}
              checked={mustChangePassword}
              onCheckedChange={(checked) => setMustChangePassword(checked === true)}
            />
            <Label htmlFor={mustChangeId} className="cursor-pointer font-normal text-text">
              {t("upload.mustChangePassword")}
            </Label>
          </div>

          {errorMessage && (
            <div
              role="alert"
              className="rounded-md border border-danger/30 bg-danger-bg px-3.5 py-3 text-field-label text-danger"
            >
              {errorMessage}
            </div>
          )}

          <div className="flex justify-end">
            <Button
              type="button"
              disabled={!selectedFile}
              loading={uploadMutation.isPending}
              onClick={handleUploadSubmit}
            >
              <Upload />
              <span>{t("upload.submit")}</span>
            </Button>
          </div>
        </Card>

        <Card padding="none" className="overflow-hidden @4xl:col-span-7">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-5 py-4">
            <h2 id={columnsHeadingId} className="text-title font-semibold text-text">
              {t("upload.columnsHeader")}
            </h2>
            <span className="text-body text-text-muted">{t("upload.columnsHint")}</span>
          </div>

          {/*
            Thẻ hẹp (dưới 32rem): danh sách thẻ thay cho bảng (DESIGN.md mục 5.9), mỗi cột của tệp một thẻ gồm tên cột,
            chip Bắt buộc/Không bắt buộc và quy tắc.
          */}
          <div ref={columnsFrameRef} className="min-w-0">
            {columnsAsCards ? (
              // Tailwind bỏ dấu đầu dòng làm Safari/VoiceOver không coi là danh sách: ghi rõ vai trò.
              <ul role="list" aria-labelledby={columnsHeadingId} className="divide-y divide-border">
                {templateColumns.map((col) => (
                  <li key={col.name} className="flex flex-col gap-1.5 px-4 py-3">
                    <p className="line-clamp-2 font-semibold wrap-break-word text-text">{col.name}</p>
                    <div className="flex flex-wrap gap-1.5">
                      {col.required ? (
                        <StatusChip tone="info">{t("upload.columnsTable.colRequired")}</StatusChip>
                      ) : (
                        <StatusChip tone="neutral">{t("upload.columnsTable.optional")}</StatusChip>
                      )}
                    </div>
                    <p className="text-caption text-text-secondary">
                      {t("metaItem", { label: t("upload.columnsTable.colRules"), value: col.rules })}
                    </p>
                  </li>
                ))}
              </ul>
            ) : (
              <div className="overflow-x-auto">
                <table aria-labelledby={columnsHeadingId} className="w-full border-collapse text-body">
                  <thead>
                    <tr className="bg-table-head text-left text-field-label text-text-muted">
                      <th scope="col" className="px-4 py-3 font-semibold">
                        {t("upload.columnsTable.colName")}
                      </th>
                      <th scope="col" className="px-2 py-3 font-semibold whitespace-nowrap">
                        {t("upload.columnsTable.colRequired")}
                      </th>
                      <th scope="col" className="px-4 py-3 font-semibold">
                        {t("upload.columnsTable.colRules")}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {templateColumns.map((col) => (
                      <tr key={col.name} className="border-t border-border">
                        <td className="px-4 py-2.5 align-top font-semibold whitespace-nowrap text-text">
                          {col.name}
                        </td>
                        <td className="px-2 py-2.5 align-top text-text">
                          {col.required
                            ? t("upload.columnsTable.yes")
                            : t("upload.columnsTable.no")}
                        </td>
                        <td className="min-w-48 px-4 py-2.5 align-top text-text-secondary">{col.rules}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="border-t border-border px-5 py-3 text-caption text-text-muted">
            {t("upload.footerNote")}
          </div>
        </Card>
      </div>
    </div>
  );
}
