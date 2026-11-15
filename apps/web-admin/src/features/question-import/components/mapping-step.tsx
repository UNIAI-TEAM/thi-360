"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import type { ColumnDef } from "@tanstack/react-table";
import {
  Button,
  Card,
  cn,
  ListCard,
  Select,
  type SelectOption,
  StatusChip,
  type StatusTone,
  Table,
  type TableCards,
} from "@thi360/ui";
import { ArrowLeft, ArrowRight, Check, FileSpreadsheet, TriangleAlert, Upload, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { type FormEvent, type RefObject, useEffect, useId, useMemo, useRef, useState } from "react";
import { Controller, type Control, useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { checkMapping, isRequiredField, type MappingCheck } from "../mapping";
import {
  type ColumnMapping,
  type ColumnTarget,
  DUPLICATE_POLICIES,
  type DuplicatePolicy,
  IGNORE_COLUMN,
  type ImportColumn,
  IMPORT_FIELDS,
  type ImportUpload,
  REQUIRED_IMPORT_FIELDS,
  UNMAPPED_COLUMN,
} from "../types";

const COLUMN_TARGETS = [UNMAPPED_COLUMN, IGNORE_COLUMN, ...IMPORT_FIELDS] as const;

interface MappingFormValues {
  mapping: ColumnMapping;
  duplicatePolicy: DuplicatePolicy;
}

function buildSchema(columns: readonly ImportColumn[]) {
  return z
    .object({
      mapping: z.record(z.string(), z.enum(COLUMN_TARGETS)),
      duplicatePolicy: z.enum(DUPLICATE_POLICIES),
    })
    .superRefine((values, ctx) => {
      const check = checkMapping(columns, values.mapping);
      for (const key of check.duplicateColumnKeys) {
        ctx.addIssue({ code: "custom", path: ["mapping", key], message: "DUPLICATE_FIELD" });
      }
      if (check.missingRequired.length > 0) {
        ctx.addIssue({ code: "custom", path: ["mapping"], message: "MISSING_REQUIRED" });
      }
    });
}

type ColumnState = "mapped" | "unmapped" | "ignored" | "duplicate";

const STATE_TONE: Record<ColumnState, StatusTone> = {
  mapped: "success",
  unmapped: "warning",
  ignored: "neutral",
  duplicate: "danger",
};

function columnState(target: ColumnTarget, key: string, check: MappingCheck): ColumnState {
  if (check.duplicateColumnKeys.has(key)) return "duplicate";
  if (target === UNMAPPED_COLUMN) return "unmapped";
  if (target === IGNORE_COLUMN) return "ignored";
  return "mapped";
}

export interface MappingStepProps {
  upload: ImportUpload;
  initialMapping: ColumnMapping;
  initialPolicy: DuplicatePolicy;
  headingRef: RefObject<HTMLHeadingElement | null>;
  submitting: boolean;
  submitError: string | null;
  onChangeFile: () => void;
  onSubmit: (mapping: ColumnMapping, policy: DuplicatePolicy) => void;
}

/** Bước 2: ánh xạ cột của tệp vào trường của hệ thống (bản vẽ `NhapCauHoiAnhXa`). */
export function MappingStep({
  upload,
  initialMapping,
  initialPolicy,
  headingRef,
  submitting,
  submitError,
  onChangeFile,
  onSubmit,
}: MappingStepProps) {
  const t = useTranslations("questionImport");
  const schema = useMemo(() => buildSchema(upload.columns), [upload.columns]);
  const form = useForm<MappingFormValues>({
    resolver: zodResolver(schema),
    mode: "onTouched",
    defaultValues: { mapping: initialMapping, duplicatePolicy: initialPolicy },
  });
  const mapping = useWatch({ control: form.control, name: "mapping" });
  const check = useMemo(() => checkMapping(upload.columns, mapping), [upload.columns, mapping]);
  const [showSummary, setShowSummary] = useState(false);
  const summaryRef = useRef<HTMLDivElement>(null);
  const focusSummary = useRef(false);
  const headingId = useId();
  const requiredHeadingId = useId();
  const statusHeadingId = useId();

  useEffect(() => {
    if (focusSummary.current) {
      focusSummary.current = false;
      summaryRef.current?.focus();
    }
  });

  const fieldOptions = useMemo<SelectOption[]>(
    () => [
      { value: UNMAPPED_COLUMN, label: t("mapping.unmappedOption") },
      { value: IGNORE_COLUMN, label: t("mapping.ignoreOption") },
      ...IMPORT_FIELDS.map((field) => ({
        value: field,
        label: isRequiredField(field)
          ? t("mapping.requiredOption", { field: t(`fields.${field}`) })
          : t(`fields.${field}`),
      })),
    ],
    [t],
  );

  function handleFormSubmit(event: FormEvent<HTMLFormElement>) {
    void form.handleSubmit(
      (values) => {
        setShowSummary(false);
        onSubmit(values.mapping, values.duplicatePolicy);
      },
      () => {
        setShowSummary(true);
        focusSummary.current = true;
        // Lần gửi lỗi liên tiếp không đổi state: vẫn đưa focus về bảng tóm tắt lỗi.
        summaryRef.current?.focus();
      },
    )(event);
  }

  const fileMeta = upload.sheetName
    ? t("mapping.fileMetaSheet", { sheet: upload.sheetName, rows: upload.rowCount })
    : t("mapping.fileMetaCsv", { rows: upload.rowCount });

  return (
    <form noValidate onSubmit={handleFormSubmit} aria-labelledby={headingId} className="flex flex-col gap-4">
      <div className="grid items-start gap-4 @5xl:grid-cols-12">
        <div className="flex min-w-0 flex-col gap-4 @5xl:col-span-8">
          <Card padding="md" className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-3">
              <span aria-hidden className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary-50 text-primary-text">
                <FileSpreadsheet className="size-5" />
              </span>
              <div className="min-w-0">
                <div className="text-body font-semibold wrap-anywhere text-text">{upload.fileName}</div>
                <div className="text-caption text-text-muted">{fileMeta}</div>
              </div>
            </div>
            <Button type="button" variant="secondary" size="sm" onClick={onChangeFile}>
              <Upload />
              <span>{t("mapping.changeFile")}</span>
            </Button>
          </Card>

          <Card padding="none" className="overflow-hidden">
            <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-border px-5 py-4">
              <h2 ref={headingRef} id={headingId} tabIndex={-1} className="text-title font-semibold text-text outline-hidden">
                {t("mapping.heading")}
              </h2>
              <p className="text-body text-text-muted">
                {t("mapping.counts", {
                  mapped: check.mappedCount,
                  unmapped: check.unmappedCount,
                  ignored: check.ignoredCount,
                })}
              </p>
            </div>
            <MappingTable
              columns={upload.columns}
              control={form.control}
              mapping={mapping}
              check={check}
              options={fieldOptions}
            />
          </Card>
        </div>

        <div className="flex min-w-0 flex-col gap-4 @5xl:col-span-4">
          <Card padding="md" className="flex flex-col gap-3">
            <section aria-labelledby={requiredHeadingId} className="flex flex-col gap-3">
              <h2 id={requiredHeadingId} className="text-label font-semibold text-text">
                {t("mapping.requiredHeading")}
              </h2>
              <ul className="flex flex-col gap-2">
                {REQUIRED_IMPORT_FIELDS.map((field) => {
                  const mapped = check.columnByField.has(field);
                  return (
                    <li key={field} className="flex items-center gap-2 text-body text-text">
                      {mapped ? (
                        <Check aria-hidden className="size-4 shrink-0 text-success" />
                      ) : (
                        <X aria-hidden className="size-4 shrink-0 text-danger" />
                      )}
                      <span>{t(`fields.${field}`)}</span>
                      <span className={cn("text-caption", mapped ? "sr-only" : "text-danger")}>
                        {mapped ? t("mapping.requiredMapped") : t("mapping.requiredMissing")}
                      </span>
                    </li>
                  );
                })}
              </ul>
              {!check.columnByField.has("knowledge") && (
                <p className="flex items-start gap-2 rounded-md border border-warning/30 bg-warning-bg px-3 py-2.5 text-caption text-warning">
                  <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
                  <span>{t.rich("mapping.knowledgeWarning", { b: (chunks) => <strong className="font-semibold">{chunks}</strong> })}</span>
                </p>
              )}
            </section>
          </Card>

          <Card padding="md">
            <fieldset className="flex flex-col gap-3">
              <legend className="mb-1 text-label font-semibold text-text">{t("mapping.duplicateLegend")}</legend>
              <p className="text-caption text-text-muted">{t("mapping.duplicateHint")}</p>
              {DUPLICATE_POLICIES.map((policy) => (
                <label
                  key={policy}
                  className="flex cursor-pointer items-start gap-2.5 rounded-md border border-border px-3 py-2.5 has-checked:border-primary-600 has-checked:bg-primary-50"
                >
                  <input
                    type="radio"
                    value={policy}
                    {...form.register("duplicatePolicy")}
                    className="mt-1 size-4 shrink-0 accent-primary-fill"
                  />
                  <span className="flex flex-col gap-0.5">
                    <span className="text-body font-medium text-text">{t(`mapping.policy.${policy}`)}</span>
                    <span className="text-caption text-text-muted">{t(`mapping.policy.${policy}Hint`)}</span>
                  </span>
                </label>
              ))}
            </fieldset>
          </Card>

          <Card padding="md">
            <section aria-labelledby={statusHeadingId} className="flex flex-col gap-2">
              <h2 id={statusHeadingId} className="text-label font-semibold text-text">
                {t("mapping.statusHeading")}
              </h2>
              <p className="text-body text-text-secondary">
                {t.rich("mapping.statusNote", { b: (chunks) => <strong className="font-semibold text-text">{chunks}</strong> })}
              </p>
            </section>
          </Card>
        </div>
      </div>

      {/* Tóm tắt lỗi ánh xạ: hiện sau khi bấm Tiếp tục, nhận focus (DESIGN.md mục 5.2). */}
      {showSummary && !check.valid && (
        <div
          ref={summaryRef}
          tabIndex={-1}
          role="alert"
          className="rounded-md border border-danger/30 bg-danger-bg px-4 py-3 text-body text-danger outline-hidden"
        >
          <p className="font-semibold">{t("mapping.errorSummaryTitle")}</p>
          <ul className="mt-1 list-disc pl-5">
            {check.missingRequired.length > 0 && (
              <li>
                {t("mapping.missingRequired", {
                  fields: check.missingRequired.map((field) => t(`fields.${field}`)).join(", "),
                })}
              </li>
            )}
            {check.duplicateFields.map((field) => (
              <li key={field}>{t("mapping.duplicateField", { field: t(`fields.${field}`) })}</li>
            ))}
          </ul>
        </div>
      )}
      {submitError && (
        <div role="alert" className="rounded-md border border-danger/30 bg-danger-bg px-4 py-3 text-body text-danger">
          {submitError}
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button type="button" variant="secondary" onClick={onChangeFile}>
          <ArrowLeft />
          <span>{t("mapping.back")}</span>
        </Button>
        <Button type="submit" loading={submitting}>
          <span>{t("mapping.next")}</span>
          <ArrowRight />
        </Button>
      </div>
    </form>
  );
}

interface MappingRow {
  column: ImportColumn;
  target: ColumnTarget;
  state: ColumnState;
}

function MappingTable({
  columns,
  control,
  mapping,
  check,
  options,
}: {
  columns: readonly ImportColumn[];
  control: Control<MappingFormValues>;
  mapping: ColumnMapping;
  check: MappingCheck;
  options: SelectOption[];
}) {
  const t = useTranslations("questionImport.mapping");
  const rows = useMemo<MappingRow[]>(
    () =>
      columns.map((column) => {
        const target = mapping[column.key] ?? UNMAPPED_COLUMN;
        return { column, target, state: columnState(target, column.key, check) };
      }),
    [columns, mapping, check],
  );

  const fieldSelect = (row: MappingRow) => (
    <Controller
      control={control}
      name={`mapping.${row.column.key}`}
      render={({ field }) => (
        <Select
          aria-label={t("selectLabel", { column: row.column.header })}
          aria-invalid={row.state === "duplicate" || undefined}
          value={field.value}
          onValueChange={(value) => {
            field.onChange(value);
            field.onBlur();
          }}
          options={options}
          className={cn("min-w-52", row.state === "unmapped" && "border-warning")}
        />
      )}
    />
  );
  const stateChip = (row: MappingRow) => (
    <StatusChip tone={STATE_TONE[row.state]}>{t(`status.${row.state}`)}</StatusChip>
  );
  const example = (row: MappingRow) => row.column.example || t("emptyExample");

  const tableColumns: ColumnDef<MappingRow>[] = [
    {
      id: "column",
      header: t("colColumn"),
      cell: ({ row }) => <span className="font-semibold whitespace-nowrap text-text">{row.original.column.header}</span>,
    },
    {
      id: "example",
      header: t("colExample"),
      cell: ({ row }) => (
        <span className="line-clamp-2 max-w-64 min-w-36 text-text-secondary">{example(row.original)}</span>
      ),
    },
    { id: "field", header: t("colField"), cell: ({ row }) => fieldSelect(row.original) },
    { id: "status", header: t("colStatus"), cell: ({ row }) => stateChip(row.original) },
  ];

  const cards: TableCards<MappingRow> = {
    render: (row) => (
      <ListCard title={row.column.header} caption={example(row)} status={stateChip(row)}>
        <div className="relative z-1 mt-1">{fieldSelect(row)}</div>
      </ListCard>
    ),
  };

  return (
    <Table
      aria-label={t("tableLabel")}
      columns={tableColumns}
      data={rows}
      getRowId={(row) => row.column.key}
      cards={cards}
      empty={null}
    />
  );
}
