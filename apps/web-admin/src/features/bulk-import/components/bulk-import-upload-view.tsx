"use client";

import { useMutation } from "@tanstack/react-query";
import { Button, Card, Checkbox, cn, EmptyState, Label, Skeleton } from "@thi360/ui";
import { FileSpreadsheet, TriangleAlert, Upload } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { type ChangeEvent, type DragEvent, useId, useMemo, useRef, useState } from "react";
import { ForbiddenView } from "@/features/session/forbidden-view";
import { usePermissions, useSession } from "@/features/session/session-provider";
import { type BulkImportApiDeps, defaultBulkImportApiDeps } from "../api";
import { resolveImportApiErrorMessage, validateSelectedExcelFile } from "../utils";
import { BulkImportHeader } from "./bulk-import-header";
import { BulkImportStepper } from "./bulk-import-stepper";

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
    <div role="status" aria-label={common("loading")} className="flex flex-col gap-4">
      <Skeleton className="h-12 w-72" />
      <Skeleton className="h-16 w-full" />
      <div className="grid gap-4 lg:grid-cols-12">
        <Skeleton className="h-80 w-full lg:col-span-5" />
        <Skeleton className="h-80 w-full lg:col-span-7" />
      </div>
    </div>
  );
}

function BulkImportUploadContent({ deps }: { deps: BulkImportApiDeps }) {
  const t = useTranslations("bulkImport");
  const router = useRouter();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const mustChangeId = useId();

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

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setIsDragging(false);
    acceptFile(event.dataTransfer.files?.[0]);
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
    <div className="flex flex-col gap-4">
      <BulkImportHeader deps={deps} />
      <BulkImportStepper currentStep={1} />

      <div className="grid items-start gap-4 lg:grid-cols-12">
        <Card padding="lg" className="flex flex-col gap-4 lg:col-span-5">
          <h2 className="text-title font-semibold text-text">{t("upload.sectionTitle")}</h2>

          <div
            onDragOver={(event) => {
              event.preventDefault();
              setIsDragging(true);
            }}
            onDragLeave={() => setIsDragging(false)}
            onDrop={handleDrop}
            className={cn(
              "flex flex-col items-center gap-2.5 rounded-xl border-2 border-dashed px-5 py-9 text-center transition-colors duration-150 motion-reduce:transition-none",
              isDragging
                ? "border-primary-text bg-primary-50"
                : "border-primary-text/40 bg-surface",
            )}
          >
            <span
              aria-hidden
              className="flex size-13 items-center justify-center rounded-xl bg-primary-50 text-primary-text"
            >
              <Upload className="size-6" />
            </span>
            <div className="text-body font-semibold text-text">{t("upload.dropzoneTitle")}</div>
            <div className="text-body text-text-muted">{t("upload.dropzoneHint")}</div>

            <input
              ref={fileInputRef}
              type="file"
              aria-label={t("upload.chooseFile")}
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={handleFileInputChange}
              className="sr-only"
            />
            <Button
              type="button"
              variant={selectedFile ? "secondary" : "primary"}
              onClick={() => fileInputRef.current?.click()}
            >
              <Upload />
              <span>{selectedFile ? t("upload.changeFile") : t("upload.chooseFile")}</span>
            </Button>
          </div>

          {selectedFile && (
            <div className="flex items-center justify-between gap-3 rounded-md border border-border bg-subtle px-3.5 py-2.5">
              <div className="flex min-w-0 items-center gap-2.5">
                <FileSpreadsheet aria-hidden className="size-5 shrink-0 text-primary-text" />
                <div className="min-w-0">
                  <div className="text-caption text-text-muted">{t("upload.selectedFileLabel")}</div>
                  <div className="truncate text-body font-semibold text-text">
                    {selectedFile.name}
                  </div>
                </div>
              </div>
              <span className="shrink-0 text-caption text-text-muted tabular-nums">
                {t("upload.fileSizeKb", {
                  size: Math.max(1, Math.round(selectedFile.size / 1024)),
                })}
              </span>
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

        <Card padding="none" className="overflow-hidden lg:col-span-7">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-5 py-4">
            <h2 className="text-title font-semibold text-text">{t("upload.columnsHeader")}</h2>
            <span className="text-body text-text-muted">{t("upload.columnsHint")}</span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-body">
              <thead>
                <tr className="bg-table-head text-left text-field-label text-text-muted">
                  <th scope="col" className="px-4 py-3 font-semibold">
                    {t("upload.columnsTable.colName")}
                  </th>
                  <th scope="col" className="px-2 py-3 font-semibold">
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
                    <td className="px-4 py-2.5 font-semibold text-text">{col.name}</td>
                    <td className="px-2 py-2.5 text-text">
                      {col.required
                        ? t("upload.columnsTable.yes")
                        : t("upload.columnsTable.no")}
                    </td>
                    <td className="px-4 py-2.5 text-text-secondary">{col.rules}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="border-t border-border px-5 py-3 text-caption text-text-muted">
            {t("upload.footerNote")}
          </div>
        </Card>
      </div>
    </div>
  );
}
