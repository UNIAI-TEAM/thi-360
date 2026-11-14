"use client";
import { useMutation } from "@tanstack/react-query";
import {
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
  cn,
} from "@thi360/ui";
import { Check, Download, FileSpreadsheet, TriangleAlert, Upload } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useState, type ChangeEvent } from "react";
import { getErrorStatus } from "@/lib/api/query-client";
import {
  type CatalogApi,
  type CatalogGroupResponse,
  type CatalogImportPreviewResponse,
  type CatalogImportResultResponse,
} from "../api";
import {
  MAX_IMPORT_FILE_BYTES,
  buildImportErrorCsv,
  pickName,
  resolveCatalogErrorMessage,
  triggerBlobDownload,
} from "../utils";

export type ExcelImportDialogProps = {
  open: boolean;
  group: CatalogGroupResponse;
  api: CatalogApi;
  onOpenChange: (open: boolean) => void;
  onImported: (result: CatalogImportResultResponse) => Promise<void>;
  onDownloadTemplate: () => Promise<void>;
};

type ImportStep = "upload" | "preview" | "result";

export function ExcelImportDialog({
  open,
  group,
  api,
  onOpenChange,
  onImported,
  onDownloadTemplate,
}: ExcelImportDialogProps) {
  const t = useTranslations("catalog");
  const locale = useLocale();
  const [busy, setBusy] = useState(false);

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (busy && !next) return;
        if (!next) setBusy(false);
        onOpenChange(next);
      }}
    >
      {open && (
        <DialogContent closeLabel={t("close")} size="lg">
          <DialogHeader>
            <DialogTitle>{t("importDialog.title")}</DialogTitle>
            <DialogDescription>
              {pickName(group, locale)} · {t("importDialog.stepSummary")}
            </DialogDescription>
          </DialogHeader>
          <ExcelImportBody
            key={group.id}
            group={group}
            api={api}
            onBusyChange={setBusy}
            onClose={() => onOpenChange(false)}
            onImported={onImported}
            onDownloadTemplate={onDownloadTemplate}
          />
        </DialogContent>
      )}
    </Dialog>
  );
}

function ExcelImportBody({
  group,
  api,
  onBusyChange,
  onClose,
  onImported,
  onDownloadTemplate,
}: {
  group: CatalogGroupResponse;
  api: CatalogApi;
  onBusyChange: (busy: boolean) => void;
  onClose: () => void;
  onImported: (result: CatalogImportResultResponse) => Promise<void>;
  onDownloadTemplate: () => Promise<void>;
}) {
  const t = useTranslations("catalog");
  const [step, setStep] = useState<ImportStep>("upload");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);

  const previewMutation = useMutation({
    mutationFn: (file: File) => api.previewImport(group.id, file),
  });
  const confirmMutation = useMutation({
    mutationFn: ({ previewId, key }: { previewId: string; key: string }) =>
      api.confirmImport(group.id, previewId, key),
  });
  const submitting = previewMutation.isPending || confirmMutation.isPending;
  const [preview, setPreview] = useState<CatalogImportPreviewResponse | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState<string>("");
  const [result, setResult] = useState<CatalogImportResultResponse | null>(null);

  function validateFileSelection(file: File): string | null {
    const lowerName = file.name.toLowerCase();
    if (lowerName.endsWith(".xlsm")) {
      return t("errorCodes.IMPORT_MACRO_NOT_ALLOWED");
    }
    if (!lowerName.endsWith(".xlsx")) {
      return t("importDialog.wrongExtension");
    }
    if (file.size === 0) {
      return t("errorCodes.IMPORT_EMPTY_FILE");
    }
    if (file.size > MAX_IMPORT_FILE_BYTES) {
      return t("errorCodes.IMPORT_FILE_TOO_LARGE");
    }
    return null;
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    setServerError(null);
    const file = event.target.files?.[0] ?? null;
    setSelectedFile(file);
    if (!file) {
      setFileError(null);
      return;
    }
    setFileError(validateFileSelection(file));
  }

  async function handleTemplateClick() {
    if (downloadingTemplate) return;
    setDownloadingTemplate(true);
    try {
      await onDownloadTemplate();
    } finally {
      setDownloadingTemplate(false);
    }
  }

  async function handlePreview() {
    if (!selectedFile || submitting) return;
    const validationError = validateFileSelection(selectedFile);
    if (validationError) {
      setFileError(validationError);
      return;
    }

    setServerError(null);
    onBusyChange(true);
    try {
      const nextPreview = await previewMutation.mutateAsync(selectedFile);
      setPreview(nextPreview);
      setIdempotencyKey(
        typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
          ? crypto.randomUUID()
          : `import-${Date.now()}`,
      );
      setStep("preview");
    } catch (error) {
      const status = getErrorStatus(error);
      if (status === 401 || status === 403) return;
      if (status === 413) {
        setServerError(t("errorCodes.IMPORT_FILE_TOO_LARGE"));
        return;
      }
      setServerError(resolveCatalogErrorMessage(error, (code) => t(`errorCodes.${code}`)));
    } finally {
      onBusyChange(false);
    }
  }

  async function handleConfirm() {
    if (!preview || !preview.canConfirm || submitting) return;
    setServerError(null);
    onBusyChange(true);
    try {
      const nextResult = await confirmMutation.mutateAsync({
        previewId: preview.previewId,
        key: idempotencyKey,
      });
      setResult(nextResult);
      setStep("result");
      await onImported(nextResult);
    } catch (error) {
      const status = getErrorStatus(error);
      if (status === 401 || status === 403) return;
      setServerError(resolveCatalogErrorMessage(error, (code) => t(`errorCodes.${code}`)));
    } finally {
      onBusyChange(false);
    }
  }

  function handleDownloadErrorReport() {
    if (!preview || preview.errors.length === 0) return;
    const localizedErrors = preview.errors.map((err) => ({
      ...err,
      message: resolveCatalogErrorMessage({ code: err.code }, (code) => t(`errorCodes.${code}`)),
    }));
    const blob = buildImportErrorCsv(
      {
        row: t("importDialog.errorRowCol"),
        column: t("importDialog.errorColumnCol"),
        code: t("importDialog.errorCodeCol"),
        message: t("importDialog.errorMessageCol"),
      },
      localizedErrors,
    );
    triggerBlobDownload(blob, `${group.code.toLowerCase()}-import-errors.csv`);
  }

  const steps: Array<{ id: ImportStep; index: number; label: string }> = [
    { id: "upload", index: 1, label: t("importDialog.uploadStep") },
    { id: "preview", index: 2, label: t("importDialog.previewStep") },
    { id: "result", index: 3, label: t("importDialog.resultStep") },
  ];
  const currentStepIndex = steps.findIndex((s) => s.id === step);

  return (
    <div className="flex flex-col gap-4">
      {/* 3-step stepper */}
      <ol className="flex flex-wrap items-center gap-5">
        {steps.map((s, idx) => {
          const completed = idx < currentStepIndex;
          const active = idx === currentStepIndex;
          return (
            <li key={s.id} className="flex items-center gap-2">
              <span
                aria-hidden="true"
                className={cn(
                  "flex size-6 items-center justify-center rounded-full text-caption font-semibold",
                  completed
                    ? "bg-primary-fill text-on-primary"
                    : active
                      ? "bg-primary-50 text-primary-text"
                      : "bg-subtle text-text-muted",
                )}
              >
                {completed ? <Check className="size-3.5" /> : s.index}
              </span>
              <span
                className={cn(
                  "text-field-label",
                  active
                    ? "font-semibold text-primary-text"
                    : completed
                      ? "font-medium text-text"
                      : "font-medium text-text-muted",
                )}
              >
                {s.label}
              </span>
            </li>
          );
        })}
      </ol>

      {serverError && (
        <div
          role="alert"
          className="rounded-md border border-danger bg-danger-bg px-3.5 py-2.5 text-field-label font-medium text-danger"
        >
          {serverError}
        </div>
      )}

      {step === "upload" && (
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border bg-page px-3.5 py-2.5">
            <span className="text-caption text-text-secondary">
              {group.kind === "TREE"
                ? t("importDialog.treeRuleHint")
                : t("importDialog.simpleRuleHint")}
            </span>
            <Button
              variant="secondary"
              size="sm"
              loading={downloadingTemplate}
              onClick={() => void handleTemplateClick()}
            >
              <Download aria-hidden="true" className="size-4" />
              {t("importDialog.downloadTemplate")}
            </Button>
          </div>

          <FormField
            label={t("importDialog.fileLabel")}
            required
            hint={t("importDialog.dropHint")}
            error={fileError ?? undefined}
          >
            {(fieldProps) => (
              <div className="flex flex-col gap-2">
                <input
                  {...fieldProps}
                  type="file"
                  accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                  onChange={handleFileChange}
                  disabled={submitting}
                  className="block w-full cursor-pointer rounded-md border border-border bg-surface px-3 py-2 text-field-label text-text file:mr-3 file:cursor-pointer file:rounded-sm file:border-0 file:bg-primary-50 file:px-3 file:py-1.5 file:text-caption file:font-semibold file:text-primary-text hover:file:bg-primary-100"
                />
                {selectedFile && !fileError && (
                  <div className="flex items-center gap-2 text-caption text-text-secondary">
                    <FileSpreadsheet aria-hidden="true" className="size-4 text-primary-text" />
                    <span className="font-medium text-text">{selectedFile.name}</span>
                    <span>
                      ({t("importDialog.fileSizeNote", {
                        size: Math.max(1, Math.round(selectedFile.size / 1024)),
                      })})
                    </span>
                  </div>
                )}
              </div>
            )}
          </FormField>

          <DialogFooter>
            <Button variant="secondary" onClick={onClose} disabled={submitting}>
              {t("cancel")}
            </Button>
            <Button
              variant="primary"
              loading={submitting}
              disabled={!selectedFile || Boolean(fileError)}
              onClick={() => void handlePreview()}
            >
              <Upload aria-hidden="true" className="size-4" />
              {t("importDialog.uploadButton")}
            </Button>
          </DialogFooter>
        </div>
      )}

      {step === "preview" && preview && (
        <div className="flex flex-col gap-4">
          <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3">
            <div className="rounded-md border border-border bg-page px-3.5 py-2.5">
              <div className="text-h3 font-bold tabular-nums text-text">
                {preview.totalRows}
              </div>
              <div className="text-caption font-medium text-text-secondary">
                {t("importDialog.summaryTotal")}
              </div>
            </div>

            <div className="rounded-md border border-border bg-page px-3.5 py-2.5">
              <div className="text-h3 font-bold tabular-nums text-primary-text">
                {preview.validRows}
              </div>
              <div className="text-caption font-medium text-text-secondary">
                {t("importDialog.summaryValid")}
              </div>
            </div>

            <div className="rounded-md border border-border bg-page px-3.5 py-2.5">
              <div
                className={cn(
                  "text-h3 font-bold tabular-nums",
                  preview.errorRows > 0 ? "text-danger" : "text-text-secondary",
                )}
              >
                {preview.errorRows}
              </div>
              <div className="text-caption font-medium text-text-secondary">
                {t("importDialog.errorsLabel")}
              </div>
            </div>
          </div>

          {preview.errorRows > 0 ? (
            <div
              role="alert"
              className="flex items-start gap-2 rounded-md border border-danger bg-danger-bg px-3.5 py-2.5 text-field-label text-danger"
            >
              <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              <span>{t("importDialog.errorFileBanner", { count: preview.errorRows })}</span>
            </div>
          ) : (
            <div className="rounded-md border border-primary-200 bg-primary-50 px-3.5 py-2.5 text-field-label font-medium text-primary-text">
              {t("importDialog.validBanner", { count: preview.validRows })}
            </div>
          )}

          {preview.errors.length > 0 && (
            <div className="max-h-64 overflow-auto rounded-md border border-border">
              <table className="w-full border-collapse text-left text-caption">
                <thead className="sticky top-0 bg-page text-text-secondary">
                  <tr>
                    <th scope="col" className="px-3 py-2 font-semibold">
                      {t("importDialog.errorRowCol")}
                    </th>
                    <th scope="col" className="px-3 py-2 font-semibold">
                      {t("importDialog.errorColumnCol")}
                    </th>
                    <th scope="col" className="px-3 py-2 font-semibold">
                      {t("importDialog.errorCodeCol")}
                    </th>
                    <th scope="col" className="px-3 py-2 font-semibold">
                      {t("importDialog.errorMessageCol")}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {preview.errors.map((err, idx) => (
                    <tr
                      key={`${err.rowNo}-${err.column}-${idx}`}
                      className="border-t border-border bg-danger-bg/40"
                    >
                      <td className="px-3 py-2 font-mono tabular-nums text-text">
                        {err.rowNo}
                      </td>
                      <td className="px-3 py-2 font-medium text-text">{err.column}</td>
                      <td className="px-3 py-2">
                        <Badge>{err.code}</Badge>
                      </td>
                      <td className="px-3 py-2 text-danger">
                        {resolveCatalogErrorMessage(
                          { code: err.code },
                          (code) => t(`errorCodes.${code}`),
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <p className="text-caption text-text-muted">
            {group.kind === "TREE"
              ? t("importDialog.treeRuleHint")
              : t("importDialog.simpleRuleHint")}
          </p>

          <DialogFooter className="flex flex-wrap items-center gap-2">
            {preview.errors.length > 0 && (
              <Button
                variant="secondary"
                className="mr-auto"
                onClick={handleDownloadErrorReport}
                disabled={submitting}
              >
                <Download aria-hidden="true" className="size-4" />
                {t("importDialog.downloadErrorFile")}
              </Button>
            )}
            <Button
              variant="secondary"
              onClick={() => {
                setServerError(null);
                setPreview(null);
                setStep("upload");
              }}
              disabled={submitting}
            >
              {t("importDialog.chooseAnotherFile")}
            </Button>
            <Button
              variant="primary"
              loading={submitting}
              disabled={!preview.canConfirm}
              onClick={() => void handleConfirm()}
            >
              {t("importDialog.confirmButton", { count: preview.validRows })}
            </Button>
          </DialogFooter>
        </div>
      )}

      {step === "result" && result && (
        <div className="flex flex-col gap-4">
          <div className="rounded-md border border-primary-200 bg-primary-50 px-3.5 py-3 text-field-label font-medium text-primary-text">
            {t("importDialog.resultBanner", {
              created: result.createdCount,
              reused: result.reusedCount,
            })}
          </div>

          <DialogFooter>
            <Button variant="primary" onClick={onClose}>
              {t("close")}
            </Button>
          </DialogFooter>
        </div>
      )}
    </div>
  );
}
