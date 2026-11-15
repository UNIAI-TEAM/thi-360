"use client";

import {
  Button,
  buttonVariants,
  Card,
  CardList,
  CardListItem,
  cn,
  IconButton,
  ListCard,
  StatusChip,
  useCardMode,
} from "@thi360/ui";
import { FileSpreadsheet, Upload, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { type ChangeEvent, type DragEvent, type RefObject, useId, useRef, useState } from "react";
import { importApiErrorCode, type QuestionImportApiDeps } from "../api";
import { checkImportFile, isImportFileErrorCode, MAX_IMPORT_FILE_BYTES, MAX_IMPORT_ROWS } from "../files";
import { useUploadQuestionFile } from "../queries";
import type { ImportUpload } from "../types";

/** Bề rộng thẻ (px) dưới đó bảng các cột của tệp mẫu thành danh sách thẻ, như màn cấp tài khoản hàng loạt. */
const TEMPLATE_COLUMNS_CARD_BREAKPOINT = 512;

type TemplateRowKey =
  | "sourceCode"
  | "type"
  | "content"
  | "options"
  | "correctAnswer"
  | "solution"
  | "subject"
  | "grade"
  | "difficulty"
  | "knowledge"
  | "tags"
  | "setCode";

const TEMPLATE_ROWS: ReadonlyArray<{ key: TemplateRowKey; required: boolean }> = [
  { key: "sourceCode", required: false },
  { key: "type", required: true },
  { key: "content", required: true },
  { key: "options", required: false },
  { key: "correctAnswer", required: true },
  { key: "solution", required: false },
  { key: "subject", required: true },
  { key: "grade", required: true },
  { key: "difficulty", required: true },
  { key: "knowledge", required: false },
  { key: "tags", required: false },
  { key: "setCode", required: false },
];

export interface UploadStepProps {
  deps: QuestionImportApiDeps;
  headingRef: RefObject<HTMLHeadingElement | null>;
  onUploaded: (upload: ImportUpload) => void;
}

/**
 * Bước 1: chọn tệp. Theo màn cấp tài khoản hàng loạt bước 1 (`CapBuoc1TaiTep`): vùng thả là nhãn của ô chọn tệp,
 * kiểm tra loại và cỡ tệp ngay khi chọn, bên phải là các cột của tệp mẫu.
 */
export function UploadStep({ deps, headingRef, onUploaded }: UploadStepProps) {
  const t = useTranslations("questionImport");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const fileInputId = useId();
  const chooseFileLabelId = useId();
  const dropzoneHintId = useId();
  const columnsHeadingId = useId();
  const [columnsFrameRef, columnsAsCards] = useCardMode(true, TEMPLATE_COLUMNS_CARD_BREAKPOINT);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const uploadMutation = useUploadQuestionFile(deps);

  function acceptFile(file: File | undefined) {
    if (!file) return;
    const code = checkImportFile(file);
    if (code) {
      setSelectedFile(null);
      setErrorMessage(t(`fileErrors.${code}`));
      return;
    }
    setErrorMessage(null);
    setSelectedFile(file);
  }

  function handleInputChange(event: ChangeEvent<HTMLInputElement>) {
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
    fileInputRef.current?.focus();
  }

  function handleSubmit() {
    if (!selectedFile || uploadMutation.isPending) return;
    setErrorMessage(null);
    uploadMutation.mutate(selectedFile, {
      onSuccess: onUploaded,
      onError: (error) => {
        const code = importApiErrorCode(error);
        setErrorMessage(isImportFileErrorCode(code) ? t(`fileErrors.${code}`) : t("upload.uploadErrorFallback"));
      },
    });
  }

  const templateRows = TEMPLATE_ROWS.map((row) => ({
    ...row,
    name: row.key === "options" ? t("upload.optionsRow") : t(`fields.${row.key}`),
    rules: t(`upload.rules.${row.key}`),
  }));

  return (
    <div className="grid items-start gap-4 @4xl:grid-cols-12">
      <Card padding="lg" className="flex flex-col gap-4 @4xl:col-span-5">
        <h2 ref={headingRef} tabIndex={-1} className="text-title font-semibold text-text outline-hidden">
          {t("upload.heading")}
        </h2>

        {/* Một điều khiển: ô chọn tệp ẩn về hình ảnh, cả vùng thả là nhãn của nó (DESIGN.md mục 5.2). */}
        <div className="relative">
          <input
            ref={fileInputRef}
            id={fileInputId}
            type="file"
            aria-labelledby={chooseFileLabelId}
            aria-describedby={dropzoneHintId}
            accept=".xlsx,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/csv"
            onChange={handleInputChange}
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
              isDragging ? "border-primary-text bg-primary-50" : "border-primary-text/40 bg-surface hover:bg-primary-50",
            )}
          >
            <span aria-hidden className="flex size-13 items-center justify-center rounded-xl bg-primary-50 text-primary-text">
              <Upload className="size-6" />
            </span>
            <span className="text-body font-semibold text-text">{t("upload.dropzoneTitle")}</span>
            <span id={dropzoneHintId} className="text-body text-text-muted">
              {t("upload.dropzoneHint", {
                maxRows: MAX_IMPORT_ROWS,
                maxMb: MAX_IMPORT_FILE_BYTES / (1024 * 1024),
              })}
            </span>
            <span
              id={chooseFileLabelId}
              className={cn(buttonVariants({ variant: selectedFile ? "secondary" : "primary" }), "gap-2")}
            >
              <Upload aria-hidden className="size-4" />
              <span>{selectedFile ? t("upload.changeFile") : t("upload.chooseFile")}</span>
            </span>
          </label>
        </div>

        <p className="text-caption text-text-muted">{t("upload.mockHint")}</p>

        {selectedFile && (
          <div className="flex items-center justify-between gap-3 rounded-md border border-border bg-subtle px-3.5 py-2.5">
            <div className="flex min-w-0 items-center gap-2.5">
              <FileSpreadsheet aria-hidden className="size-5 shrink-0 text-primary-text" />
              <div className="min-w-0">
                <div className="text-caption text-text-muted">{t("upload.selectedFileLabel")}</div>
                <div className="text-body font-semibold wrap-anywhere text-text">{selectedFile.name}</div>
              </div>
            </div>
            <div className="flex shrink-0 items-center gap-1">
              <span className="text-caption text-text-muted tabular-nums">
                {t("upload.fileSizeKb", { size: Math.max(1, Math.round(selectedFile.size / 1024)) })}
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

        {errorMessage && (
          <div
            role="alert"
            className="rounded-md border border-danger/30 bg-danger-bg px-3.5 py-3 text-field-label text-danger"
          >
            {errorMessage}
          </div>
        )}

        <div className="flex justify-end">
          <Button type="button" disabled={!selectedFile} loading={uploadMutation.isPending} onClick={handleSubmit}>
            <Upload />
            <span>{t("upload.submit")}</span>
          </Button>
        </div>
      </Card>

      <Card padding="none" className="overflow-hidden @4xl:col-span-7">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-5 py-4">
          <h2 id={columnsHeadingId} className="text-title font-semibold text-text">
            {t("upload.columnsHeading")}
          </h2>
          <span className="text-body text-text-muted">{t("upload.columnsHint")}</span>
        </div>
        <div ref={columnsFrameRef} className="min-w-0">
          {columnsAsCards ? (
            <CardList aria-labelledby={columnsHeadingId}>
              {templateRows.map((row) => (
                <CardListItem key={row.key}>
                  <ListCard
                    title={row.name}
                    status={
                      row.required ? (
                        <StatusChip tone="info">{t("upload.colRequired")}</StatusChip>
                      ) : (
                        <StatusChip tone="neutral">{t("upload.optional")}</StatusChip>
                      )
                    }
                    meta={[{ label: t("upload.colRules"), value: row.rules }]}
                  />
                </CardListItem>
              ))}
            </CardList>
          ) : (
            <div className="overflow-x-auto">
              <table aria-labelledby={columnsHeadingId} className="w-full border-collapse text-body">
                <thead>
                  <tr className="bg-table-head text-left text-field-label text-text-muted">
                    <th scope="col" className="px-4 py-3 font-semibold">
                      {t("upload.colName")}
                    </th>
                    <th scope="col" className="px-2 py-3 font-semibold whitespace-nowrap">
                      {t("upload.colRequired")}
                    </th>
                    <th scope="col" className="px-4 py-3 font-semibold">
                      {t("upload.colRules")}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {templateRows.map((row) => (
                    <tr key={row.key} className="border-t border-border">
                      <th scope="row" className="px-4 py-2.5 text-left align-top font-semibold whitespace-nowrap text-text">
                        {row.name}
                      </th>
                      <td className="px-2 py-2.5 align-top text-text">
                        {row.required ? t("upload.yes") : t("upload.no")}
                      </td>
                      <td className="min-w-48 px-4 py-2.5 align-top text-text-secondary">{row.rules}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        <div className="border-t border-border px-5 py-3 text-caption text-text-muted">{t("upload.footerNote")}</div>
      </Card>
    </div>
  );
}
