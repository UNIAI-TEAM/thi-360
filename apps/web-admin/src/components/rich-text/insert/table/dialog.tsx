"use client";

import { Button, Checkbox, cn, Input } from "@thi360/ui";
import { Merge, Minus, Plus, Split } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRef, useState, type ClipboardEvent, type KeyboardEvent, type MouseEvent } from "react";
import { DATA_TABLE_NODE } from "../../extensions";
import { sanitizeQuestionHtml } from "../../sanitize";
import type { InsertDialogProps } from "../registry";
import { InsertDialogShell } from "../shared/insert-dialog-shell";
import { selectedAtom, upsertAtom } from "../shared/upsert-atom";
import {
  addCol,
  addRow,
  canMerge,
  colCount,
  createDraft,
  deleteCol,
  deleteRow,
  expandRect,
  hasContent,
  isCovered,
  MAX_COLS,
  MAX_ROWS,
  mergeAt,
  mergeCells,
  mergeInRect,
  originOf,
  parseClipboardGrid,
  parseTable,
  pasteGrid,
  rowCount,
  serializeTable,
  setCellText,
  unmergeCells,
  unwrapFigure,
  type TableDraft,
} from "./table-model";

interface Position {
  row: number;
  col: number;
}

/** Vùng chọn: `anchor` là ô bắt đầu, `focus` là ô đang hoạt động (đích của thêm/xóa hàng cột). */
interface Selection {
  anchor: Position;
  focus: Position;
}

const ARROW_STEP: Record<string, Position> = {
  ArrowUp: { row: -1, col: 0 },
  ArrowDown: { row: 1, col: 0 },
  ArrowLeft: { row: 0, col: -1 },
  ArrowRight: { row: 0, col: 1 },
};

/**
 * Hộp "Bảng số liệu" (spec 4.5, bản vẽ `SoanThaoBang`): tên bảng, đơn vị, nguồn, lưới ô sửa trực tiếp,
 * thêm/xóa hàng cột, gộp ô, hàng/cột tiêu đề, căn phải cột số, dán vùng ô từ Excel; tối đa 30×15. Lưu thành
 * `figure.data-table` (node atom `dataTable`); đang chọn một bảng số liệu thì mở lại với dữ liệu hiện có.
 */
export default function TableInsertDialog({ editor, open, onOpenChange }: InsertDialogProps) {
  const t = useTranslations("richText.insertDialog.table");
  const [initial] = useState(() => {
    const existing = selectedAtom(editor, DATA_TABLE_NODE);
    return { existing, draft: existing ? parseTable(String(existing.node.attrs.html ?? "")) : createDraft() };
  });
  const [draft, setDraft] = useState<TableDraft>(initial.draft);
  const [selection, setSelection] = useState<Selection>({ anchor: { row: 0, col: 0 }, focus: { row: 0, col: 0 } });
  const [truncated, setTruncated] = useState(false);
  const gridRef = useRef<HTMLTableElement>(null);

  const rows = rowCount(draft);
  const cols = colCount(draft);
  const rect = expandRect(draft, {
    row1: selection.anchor.row,
    col1: selection.anchor.col,
    row2: selection.focus.row,
    col2: selection.focus.col,
  });
  const multiple = rect.row1 !== rect.row2 || rect.col1 !== rect.col2;
  const active = selection.focus;

  function update(next: TableDraft) {
    setDraft(next);
    setTruncated(false);
    // Hàng/cột bị xóa hoặc ô bị che: đưa vùng chọn về ô còn tồn tại.
    const clamp = (position: Position): Position => ({
      row: Math.min(position.row, rowCount(next) - 1),
      col: Math.min(position.col, colCount(next) - 1),
    });
    setSelection((current) => ({ anchor: clamp(current.anchor), focus: clamp(current.focus) }));
  }

  function focusCell(position: Position) {
    const target = originOf(draft, position.row, position.col);
    gridRef.current?.querySelector<HTMLInputElement>(`[data-cell="${target.row}-${target.col}"]`)?.focus();
  }

  function moveBy(step: Position, from: Position) {
    const next = {
      row: Math.min(Math.max(from.row + step.row, 0), rows - 1),
      col: Math.min(Math.max(from.col + step.col, 0), cols - 1),
    };
    focusCell(next);
  }

  function onCellKeyDown(event: KeyboardEvent<HTMLInputElement>, position: Position) {
    // Enter trong ô là xuống ô dưới, không gửi biểu mẫu (nút Chèn).
    if (event.key === "Enter") {
      event.preventDefault();
      moveBy({ row: 1, col: 0 }, position);
      return;
    }
    const step = ARROW_STEP[event.key];
    if (!step) return;
    if (event.altKey && event.shiftKey) {
      // Mở rộng vùng chọn bằng bàn phím để gộp ô không cần chuột.
      event.preventDefault();
      setSelection((current) => ({
        anchor: current.anchor,
        focus: {
          row: Math.min(Math.max(current.focus.row + step.row, 0), rows - 1),
          col: Math.min(Math.max(current.focus.col + step.col, 0), cols - 1),
        },
      }));
      return;
    }
    // Mũi tên lên/xuống đổi ô; trái/phải chỉ đổi ô khi con trỏ đã ở mép ô đang sửa.
    const input = event.currentTarget;
    const atStart = input.selectionStart === 0 && input.selectionEnd === 0;
    const atEnd = input.selectionStart === input.value.length && input.selectionEnd === input.value.length;
    if (step.row !== 0 || (step.col < 0 && atStart) || (step.col > 0 && atEnd)) {
      event.preventDefault();
      moveBy(step, position);
    }
  }

  function onCellMouseDown(event: MouseEvent<HTMLInputElement>, position: Position) {
    if (!event.shiftKey) return;
    event.preventDefault();
    setSelection((current) => ({ anchor: current.anchor, focus: position }));
  }

  function onCellPaste(event: ClipboardEvent<HTMLInputElement>, position: Position) {
    const text = event.clipboardData.getData("text/plain");
    if (!/[\t\r\n]/.test(text)) return;
    event.preventDefault();
    const result = pasteGrid(draft, position.row, position.col, parseClipboardGrid(text));
    update(result.draft);
    setTruncated(result.truncated);
  }

  function submit() {
    if (!hasContent(draft)) return;
    const figure = serializeTable(draft, { unitPrefix: t("unitPrefix"), sourcePrefix: t("sourcePrefix") });
    const inner = unwrapFigure(sanitizeQuestionHtml(figure).html);
    if (inner === null) return;
    upsertAtom(editor, DATA_TABLE_NODE, { html: inner }, initial.existing);
    onOpenChange(false);
  }

  const mergeEnabled = canMerge(draft, rect);
  const splitEnabled = mergeInRect(draft, rect) !== undefined;

  return (
    <InsertDialogShell
      open={open}
      onOpenChange={onOpenChange}
      title={t("title")}
      description={t("description")}
      onSubmit={submit}
      submitLabel={initial.existing ? t("update") : t("submit")}
      submitDisabled={!hasContent(draft)}
    >
      <div className="mb-4 flex max-h-[70dvh] flex-col gap-4 overflow-y-auto">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1.5 block text-field-label text-text-secondary">{t("titleLabel")}</span>
            <Input value={draft.title} onChange={(event) => setDraft({ ...draft, title: event.target.value })} />
          </label>
          <label className="block">
            <span className="mb-1.5 block text-field-label text-text-secondary">{t("unitLabel")}</span>
            <Input value={draft.unit} onChange={(event) => setDraft({ ...draft, unit: event.target.value })} />
          </label>
        </div>

        <div className="flex flex-wrap items-center gap-2" role="toolbar" aria-label={t("toolsLabel")}>
          <Button type="button" variant="secondary" size="sm" disabled={rows >= MAX_ROWS} onClick={() => update(addRow(draft, active.row))}>
            <Plus aria-hidden />
            {t("addRow")}
          </Button>
          <Button type="button" variant="secondary" size="sm" disabled={cols >= MAX_COLS} onClick={() => update(addCol(draft, active.col))}>
            <Plus aria-hidden />
            {t("addCol")}
          </Button>
          <Button type="button" variant="secondary" size="sm" disabled={rows <= 1} onClick={() => update(deleteRow(draft, active.row))}>
            <Minus aria-hidden />
            {t("deleteRow")}
          </Button>
          <Button type="button" variant="secondary" size="sm" disabled={cols <= 1} onClick={() => update(deleteCol(draft, active.col))}>
            <Minus aria-hidden />
            {t("deleteCol")}
          </Button>
          <Button type="button" variant="secondary" size="sm" disabled={!mergeEnabled} onClick={() => update(mergeCells(draft, rect))}>
            <Merge aria-hidden />
            {t("merge")}
          </Button>
          <Button type="button" variant="secondary" size="sm" disabled={!splitEnabled} onClick={() => update(unmergeCells(draft, rect))}>
            <Split aria-hidden />
            {t("unmerge")}
          </Button>
        </div>

        <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
          <label className="flex items-center gap-2 text-body text-text-secondary">
            <Checkbox checked={draft.headerRow} onCheckedChange={(value) => setDraft({ ...draft, headerRow: value === true })} />
            {t("headerRow")}
          </label>
          <label className="flex items-center gap-2 text-body text-text-secondary">
            <Checkbox checked={draft.headerCol} onCheckedChange={(value) => setDraft({ ...draft, headerCol: value === true })} />
            {t("headerCol")}
          </label>
          <label className="flex items-center gap-2 text-body text-text-secondary">
            <Checkbox checked={draft.alignNumbers} onCheckedChange={(value) => setDraft({ ...draft, alignNumbers: value === true })} />
            {t("alignNumbers")}
          </label>
        </div>

        <div className="overflow-auto rounded-lg border border-border">
          <table ref={gridRef} aria-label={t("gridLabel")} className="w-full border-collapse">
            <tbody>
              {draft.cells.map((cellsRow, row) => (
                <tr key={row}>
                  {cellsRow.map((cell, col) => {
                    if (isCovered(draft, row, col)) return null;
                    const merge = mergeAt(draft, row, col);
                    const isHeader = (draft.headerRow && row === 0) || (draft.headerCol && col === 0);
                    const selected =
                      multiple && row >= rect.row1 && row <= rect.row2 && col >= rect.col1 && col <= rect.col2;
                    return (
                      <td
                        key={col}
                        colSpan={merge?.colSpan}
                        rowSpan={merge?.rowSpan}
                        className={cn("border border-border p-0", isHeader && "bg-subtle", selected && "bg-primary-50")}
                      >
                        <Input
                          data-cell={`${row}-${col}`}
                          data-rich={cell.rich ? "true" : undefined}
                          aria-label={t("cellLabel", { row: row + 1, col: col + 1 })}
                          value={cell.text}
                          dir="ltr"
                          className={cn(
                            "h-9 min-w-24 rounded-none border-0 bg-transparent px-2",
                            isHeader && "font-semibold",
                            cell.rich && "font-mono text-caption",
                          )}
                          onChange={(event) => update(setCellText(draft, row, col, event.target.value))}
                          onFocus={() => setSelection({ anchor: { row, col }, focus: { row, col } })}
                          onMouseDown={(event) => onCellMouseDown(event, { row, col })}
                          onKeyDown={(event) => onCellKeyDown(event, { row, col })}
                          onPaste={(event) => onCellPaste(event, { row, col })}
                        />
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <label className="block">
          <span className="mb-1.5 block text-field-label text-text-secondary">{t("sourceLabel")}</span>
          <Input value={draft.source} onChange={(event) => setDraft({ ...draft, source: event.target.value })} />
        </label>

        <p className="text-caption text-text-muted">{t("pasteHint")}</p>
        <p className="text-caption text-text-muted">{t("selectHint")}</p>
        <p className="text-caption text-text-muted">{t("limitNote", { rows: MAX_ROWS, cols: MAX_COLS })}</p>
        {truncated && (
          <p role="status" className="text-caption text-warning">
            {t("truncated", { rows: MAX_ROWS, cols: MAX_COLS })}
          </p>
        )}
      </div>
    </InsertDialogShell>
  );
}
