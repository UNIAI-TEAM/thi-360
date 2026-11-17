"use client";

import { Button, Input, Select } from "@thi360/ui";
import { Plus, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { Fragment, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from "react";
import type { InsertDialogProps } from "../registry";
import { InsertDialogShell } from "../shared/insert-dialog-shell";
import {
  cellSegments,
  crossRows,
  crossToHtml,
  emptyCrossModel,
  getCell,
  htmlToCross,
  setCell,
  type CellId,
  type CrossModel,
  type RowLabel,
  type Sex,
} from "./cross-model";
import { selectedCross, upsertCross } from "./upsert-cross";

const SEX_OPTION_SIGNS: Record<Exclude<Sex, "none">, string> = { male: "♂", female: "♀" };

function segmentsToNodes(text: string): ReactNode {
  return cellSegments(text).map((segment, index) => {
    if (segment.kind === "sub") return <sub key={index}>{segment.value}</sub>;
    if (segment.kind === "sup") return <sup key={index}>{segment.value}</sup>;
    return <Fragment key={index}>{segment.value}</Fragment>;
  });
}

function RowLabelView({ label }: { label: RowLabel }) {
  return typeof label === "string" ? label : <>F<sub>{label.filial}</sub></>;
}

/**
 * Hộp "Sơ đồ lai" (spec 4.9, bản vẽ `SoanThaoSinhHoc`): lưới thế hệ P (giới tính × kiểu gen hai bên), G (giao tử hai
 * bên), F1 (tỉ lệ kiểu gen, tỉ lệ kiểu hình tùy chọn), "Thêm thế hệ" thêm F2, F3…; chỉ số dưới/trên gõ `_`/`^` hoặc
 * bấm nút. Lưu thành `figure.cross`; mở lại (chọn sơ đồ trong nội dung) đọc ngược HTML về lưới.
 */
export default function CrossInsertDialog({ editor, open, onOpenChange }: InsertDialogProps) {
  const t = useTranslations("richText.insertDialog.cross");
  const existing = useMemo(() => selectedCross(editor), [editor]);
  const [model, setModel] = useState<CrossModel>(() => (existing ? htmlToCross(existing.html) : emptyCrossModel()));
  const gridRef = useRef<HTMLDivElement>(null);
  const lastCell = useRef<CellId>("genotypeA");
  const pendingCaret = useRef<{ cell: CellId; caret: number } | null>(null);

  const html = crossToHtml(model);
  const rows = crossRows(model);

  // Sau khi nút chỉ số ghi ký hiệu vào ô, trả focus về ô và đặt con trỏ ngay sau ký hiệu.
  useLayoutEffect(() => {
    const pending = pendingCaret.current;
    if (!pending) return;
    pendingCaret.current = null;
    const input = gridRef.current?.querySelector<HTMLInputElement>(`[data-cell="${pending.cell}"]`);
    input?.focus();
    input?.setSelectionRange(pending.caret, pending.caret);
  });

  function updateCell(cell: CellId, value: string) {
    setModel((current) => setCell(current, cell, value));
  }

  function insertScriptMark(mark: "_" | "^") {
    const cell = lastCell.current;
    const input = gridRef.current?.querySelector<HTMLInputElement>(`[data-cell="${cell}"]`);
    const value = getCell(model, cell);
    const start = input?.selectionStart ?? value.length;
    const end = input?.selectionEnd ?? start;
    updateCell(cell, value.slice(0, start) + mark + value.slice(end));
    pendingCaret.current = { cell, caret: start + 1 };
  }

  function removeGeneration(index: number) {
    setModel((current) => ({ ...current, filial: current.filial.filter((_, position) => position !== index) }));
    lastCell.current = "genotypeA";
  }

  function submit() {
    if (!html) return;
    upsertCross(editor, html, existing);
    onOpenChange(false);
  }

  function cellInput(cell: CellId, ariaLabel: string, placeholder?: string) {
    return (
      <Input
        data-cell={cell}
        value={getCell(model, cell)}
        onChange={(event) => updateCell(cell, event.target.value)}
        aria-label={ariaLabel}
        placeholder={placeholder}
        autoComplete="off"
        spellCheck={false}
        className="min-w-0 flex-1"
      />
    );
  }

  function sexSelect(side: "sexA" | "sexB", ariaLabel: string) {
    return (
      <Select
        aria-label={ariaLabel}
        value={model[side]}
        onValueChange={(value) => setModel((current) => ({ ...current, [side]: value as Sex }))}
        options={[
          { value: "male", label: SEX_OPTION_SIGNS.male },
          { value: "female", label: SEX_OPTION_SIGNS.female },
          { value: "none", label: t("sexNone") },
        ]}
        className="w-24 shrink-0"
      />
    );
  }

  return (
    <InsertDialogShell
      open={open}
      onOpenChange={onOpenChange}
      title={t("title")}
      description={t("description")}
      submitLabel={existing ? t("update") : t("insert")}
      submitDisabled={!html}
      onSubmit={submit}
    >
      <div className="flex flex-col gap-4">
        <div
          ref={gridRef}
          onFocusCapture={(event) => {
            const cell = (event.target as HTMLElement).dataset.cell;
            if (cell) lastCell.current = cell as CellId;
          }}
          className="flex flex-col gap-3"
        >
          <div className="flex flex-wrap items-center gap-2">
            <strong className="w-10 shrink-0 text-body text-text">P</strong>
            {sexSelect("sexA", t("fields.sexA"))}
            {cellInput("genotypeA", t("fields.genotypeA"))}
            <span aria-hidden="true" className="text-body text-text-muted">
              ×
            </span>
            {sexSelect("sexB", t("fields.sexB"))}
            {cellInput("genotypeB", t("fields.genotypeB"))}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <strong className="w-10 shrink-0 text-body text-text">G</strong>
            {cellInput("gametesA", t("fields.gametesA"))}
            {cellInput("gametesB", t("fields.gametesB"))}
          </div>
          {model.filial.map((_, index) => (
            <div key={index} className="flex flex-col gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <strong className="w-10 shrink-0 text-body text-text">
                  F<sub>{index + 1}</sub>
                </strong>
                {cellInput(`filial.${index}.genotype`, t("fields.filialGenotype", { n: index + 1 }))}
                {index > 0 && (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    aria-label={t("removeGeneration", { n: index + 1 })}
                    onClick={() => removeGeneration(index)}
                  >
                    <Trash2 aria-hidden />
                  </Button>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <span className="w-10 shrink-0" aria-hidden="true" />
                {cellInput(
                  `filial.${index}.phenotype`,
                  t("fields.filialPhenotype", { n: index + 1 }),
                  t("phenotypePlaceholder"),
                )}
              </div>
            </div>
          ))}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() =>
              setModel((current) => ({ ...current, filial: [...current.filial, { genotype: "", phenotype: "" }] }))
            }
          >
            <Plus aria-hidden />
            {t("addGeneration")}
          </Button>
          <Button type="button" variant="secondary" onClick={() => insertScriptMark("_")}>
            {t("subscript")}
          </Button>
          <Button type="button" variant="secondary" onClick={() => insertScriptMark("^")}>
            {t("superscript")}
          </Button>
        </div>
        <p className="text-caption text-text-muted">{t("scriptHint")}</p>

        <section aria-label={t("previewLabel")} className="flex flex-col gap-1.5">
          <span className="text-field-label text-text">{t("previewLabel")}</span>
          <div
            data-testid="cross-preview"
            className="min-h-14 rounded-xl border border-border bg-subtle px-4 py-3 text-body text-text"
          >
            {rows.length === 0 ? (
              <span className="text-text-placeholder">{t("previewEmpty")}</span>
            ) : (
              <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                {rows.map((row, index) => (
                  <Fragment key={index}>
                    <dt className="font-semibold">
                      <RowLabelView label={row.label} />:
                    </dt>
                    <dd>{segmentsToNodes(row.text)}</dd>
                  </Fragment>
                ))}
              </dl>
            )}
          </div>
        </section>

        <p className="text-caption text-text-muted">{t("symbolsNote")}</p>
      </div>
    </InsertDialogShell>
  );
}
