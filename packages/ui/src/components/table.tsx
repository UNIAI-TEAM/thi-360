"use client";

import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type OnChangeFn,
  type RowSelectionState,
  type SortingState,
} from "@tanstack/react-table";
import { ArrowDown, ArrowUp, ChevronsUpDown } from "lucide-react";
import { useMemo, type ReactNode } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import { Checkbox } from "./checkbox";
import { Skeleton } from "./skeleton";

export type TableSelection<TData = unknown> = {
  /** Id dòng đã chọn. Có thể chứa dòng ở trang khác hoặc bộ lọc khác: ô chọn tất cả chỉ xét dòng đang hiện. */
  state: RowSelectionState;
  onChange: OnChangeFn<RowSelectionState>;
  /** Tên ô chọn tất cả, ví dụ "Chọn tất cả dòng". */
  selectAllLabel: string;
  /** Tên ô chọn một dòng, ví dụ `Chọn tài khoản ${row.name}`. */
  rowLabel: (index: number, row: TData) => string;
};

export type TableSorting = {
  state: SortingState;
  onChange: OnChangeFn<SortingState>;
  /** Sắp xếp ở máy chủ: bảng không tự sắp xếp lại `data`. */
  manual?: boolean;
};

export type TableProps<TData> = {
  /** Cột theo TanStack Table. Cột đặt `enableSorting: false` thì không sắp xếp được. */
  columns: ColumnDef<TData>[];
  data: TData[];
  getRowId: (row: TData) => string;
  /** Bật sắp xếp khi truyền. */
  sorting?: TableSorting;
  /** Bật cột chọn dòng khi truyền. */
  selection?: TableSelection<TData>;
  loading?: boolean;
  /** Số dòng khung xương khi đang tải. */
  loadingRows?: number;
  /** Có giá trị thì hiện thay cho dữ liệu, thường là thông báo kèm nút thử lại. */
  error?: ReactNode;
  /** Hiện khi không lỗi, không tải và không có dòng nào. */
  empty: ReactNode;
  /** Tên bảng cho trình đọc màn hình. */
  "aria-label": string;
  className?: string;
};

const SELECT_COLUMN_ID = "select";
/** Hằng ổn định: `?? []`/`?? {}` tạo đối tượng mới mỗi lần render làm TanStack tính lại hàng và tự đặt lại trang (vòng lặp render). */
const EMPTY_SORTING: SortingState = [];
const EMPTY_ROW_SELECTION: RowSelectionState = {};

const headCell = "h-11 bg-table-head px-4 text-left text-field-label font-semibold whitespace-nowrap text-text-muted";
const bodyCell = "h-12 px-4 align-middle text-body text-text tabular-nums";

/** Bọc TanStack Table: sắp xếp, chọn dòng, trạng thái tải/rỗng/lỗi (DESIGN.md mục 5.9). */
export function Table<TData>({
  columns,
  data,
  getRowId,
  sorting,
  selection,
  loading = false,
  loadingRows = 5,
  error,
  empty,
  className,
  "aria-label": ariaLabel,
}: TableProps<TData>) {
  const hasSelection = selection !== undefined;
  const allColumns = useMemo<ColumnDef<TData>[]>(
    () => (hasSelection ? [{ id: SELECT_COLUMN_ID, enableSorting: false }, ...columns] : columns),
    [columns, hasSelection],
  );

  const table = useReactTable({
    data,
    columns: allColumns,
    getRowId,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    manualSorting: sorting?.manual ?? false,
    enableSorting: sorting !== undefined,
    enableRowSelection: selection !== undefined,
    /** Bảng không phân trang nội bộ (trang do máy chủ/Pagination bên ngoài), nên không cần tự đặt lại chỉ số trang. */
    autoResetPageIndex: false,
    state: {
      sorting: sorting?.state ?? EMPTY_SORTING,
      rowSelection: selection?.state ?? EMPTY_ROW_SELECTION,
    },
    onSortingChange: sorting?.onChange,
    onRowSelectionChange: selection?.onChange,
  });

  const columnCount = allColumns.length;
  const rows = table.getRowModel().rows;
  const selectedOnPage = rows.reduce((count, row) => count + (row.getIsSelected() ? 1 : 0), 0);
  const selectAllChecked =
    rows.length > 0 && selectedOnPage === rows.length
      ? true
      : selectedOnPage > 0
        ? "indeterminate"
        : false;

  return (
    <div className={cn("overflow-x-auto", className)}>
      <table aria-label={ariaLabel} aria-busy={loading || undefined} className="w-full border-collapse">
        <thead>
          {table.getHeaderGroups().map((group) => (
            <tr key={group.id} className="border-b border-border">
              {group.headers.map((header) => {
                const sorted = header.column.getIsSorted();
                const sortable = header.column.getCanSort();
                return (
                  <th
                    key={header.id}
                    scope="col"
                    aria-sort={
                      sortable
                        ? sorted === "asc"
                          ? "ascending"
                          : sorted === "desc"
                            ? "descending"
                            : "none"
                        : undefined
                    }
                    className={cn(headCell, header.column.id === SELECT_COLUMN_ID && "w-12")}
                  >
                    {header.column.id === SELECT_COLUMN_ID && selection ? (
                      <Checkbox
                        aria-label={selection.selectAllLabel}
                        disabled={loading || Boolean(error) || rows.length === 0}
                        checked={selectAllChecked}
                        onCheckedChange={(checked) => table.toggleAllPageRowsSelected(checked === true)}
                      />
                    ) : header.isPlaceholder ? null : sortable ? (
                      <button
                        type="button"
                        onClick={header.column.getToggleSortingHandler()}
                        className={cn("inline-flex items-center gap-1.5 rounded-sm font-semibold", focusRing)}
                      >
                        {flexRender(header.column.columnDef.header, header.getContext())}
                        {sorted === "asc" ? (
                          <ArrowUp aria-hidden className="size-3.5" />
                        ) : sorted === "desc" ? (
                          <ArrowDown aria-hidden className="size-3.5" />
                        ) : (
                          <ChevronsUpDown aria-hidden className="size-3.5 opacity-60" />
                        )}
                      </button>
                    ) : (
                      flexRender(header.column.columnDef.header, header.getContext())
                    )}
                  </th>
                );
              })}
            </tr>
          ))}
        </thead>
        <tbody>
          {loading ? (
            Array.from({ length: loadingRows }, (_, rowIndex) => (
              <tr key={rowIndex} className="border-b border-border">
                {allColumns.map((_, cellIndex) => (
                  <td key={cellIndex} className={bodyCell}>
                    <Skeleton className="h-4 w-full max-w-40" />
                  </td>
                ))}
              </tr>
            ))
          ) : error ? (
            <tr>
              <td colSpan={columnCount} role="alert">
                {error}
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td colSpan={columnCount}>{empty}</td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr
                key={row.id}
                data-state={row.getIsSelected() ? "selected" : undefined}
                className="border-b border-border transition-colors duration-150 last:border-b-0 hover:bg-subtle data-[state=selected]:bg-primary-50 motion-reduce:transition-none"
              >
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id} className={bodyCell}>
                    {cell.column.id === SELECT_COLUMN_ID && selection ? (
                      <Checkbox
                        aria-label={selection.rowLabel(row.index, row.original)}
                        checked={row.getIsSelected()}
                        onCheckedChange={(checked) => row.toggleSelected(checked === true)}
                      />
                    ) : (
                      flexRender(cell.column.columnDef.cell, cell.getContext())
                    )}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
