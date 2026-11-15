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
import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { cn } from "../lib/cn";
import { focusRing, focusRingInset } from "../lib/styles";
import { Checkbox } from "./checkbox";
import { Select } from "./select";
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

/**
 * Chế độ danh sách thẻ cho khung hẹp (DESIGN.md mục 5.9). Khi khung chứa bảng hẹp hơn `breakpoint`, bảng hiện
 * thành danh sách (`<ul role="list">`), mỗi dòng dữ liệu là một thẻ do `render` vẽ; rộng hơn thì là bảng như cũ.
 */
export type TableCards<TData> = {
  /**
   * Nội dung một thẻ: dòng tiêu đề (thường là liên kết tới chi tiết), các dòng phụ, chip trạng thái, nút thao tác.
   * Ô chọn dòng (khi bảng có `selection`) do `Table` vẽ ở đầu thẻ, không vẽ lại ở đây.
   *
   * Cả thẻ bấm được để mở chi tiết: cho liên kết tiêu đề lớp `after:absolute after:inset-0` (thẻ đã là `relative`);
   * nút và menu trong thẻ thêm `relative z-1` để nằm trên vùng bấm đó.
   */
  render: (row: TData, index: number) => ReactNode;
  /** Bề rộng khung (px) dưới đó hiện thẻ. Mặc định 640 (40rem). */
  breakpoint?: number;
  /**
   * Nhãn cho ô chọn sắp xếp, cần khi bảng có `sorting`: trên thẻ không có tiêu đề cột để bấm, nên các cột sắp xếp
   * được thành một ô chọn "‹cột› · ‹chiều›". Tên cột lấy từ `header` (chuỗi) hoặc `meta.label` của cột.
   */
  sortLabels?: { label: string; ascending: string; descending: string };
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
  /**
   * Số dòng khung xương khi đang tải (mặc định 5). Bảng có phân trang nên truyền số dòng mỗi trang
   * (`loadingRows={pageSize}`), để thẻ chứa bảng không đổi chiều cao khi dữ liệu về.
   */
  loadingRows?: number;
  /**
   * Đang tải lại trong khi vẫn hiện dữ liệu cũ (đổi bộ lọc, trang, sắp xếp). Bảng giữ nguyên các dòng,
   * làm mờ nhẹ và báo `aria-busy`; không thay bằng khung xương nên bố cục không nhảy.
   */
  refreshing?: boolean;
  /**
   * Ghim cột khi bảng phải cuộn ngang, để không mất ngữ cảnh của dòng: `start` ghim cột dữ liệu đầu tiên
   * (tên/mã của dòng) vào mép đầu dòng, kèm cột chọn dòng đứng trước nó nếu có; `end` ghim cột cuối (cột Thao tác)
   * vào mép cuối dòng. Ô ghim có nền đặc đi theo trạng thái của dòng (thường, hover, đang chọn) và nằm trên các ô
   * cuộn qua bên dưới; mép trong của cột ghim hiện một vạch kẻ chỉ khi có nội dung nằm khuất dưới nó. Bảng vừa
   * khung thì ô ghim đứng đúng vị trí thường, không che gì. Bảng chỉ có một cột dữ liệu thì `end` không có tác dụng.
   *
   * Khi bật, khung cuộn của bảng là vùng truy vấn trạng thái cuộn (`container-type: scroll-state`): cần truy vấn
   * theo bề rộng thì đặt `@container` lên phần tử bọc ngoài, không truyền qua `className` của bảng.
   */
  stickyColumns?: { start?: boolean; end?: boolean };
  /** Có giá trị thì hiện thay cho dữ liệu, thường là thông báo kèm nút thử lại. */
  error?: ReactNode;
  /** Hiện khi không lỗi, không tải và không có dòng nào. */
  empty: ReactNode;
  /** Bật chế độ danh sách thẻ khi khung hẹp. */
  cards?: TableCards<TData>;
  /** Tên bảng cho trình đọc màn hình. Khi bảng tràn ngang, vùng cuộn bọc ngoài cũng mang tên này. */
  "aria-label": string;
  className?: string;
};

const SELECT_COLUMN_ID = "select";

/**
 * Ô thông báo rỗng/lỗi trải hết các cột (`colSpan`), nên khi các cột có bề rộng tối thiểu làm bảng rộng hơn khung
 * (màn hẹp), nội dung canh giữa theo cả bảng và bị khung cắt mất một nửa. Phần bên trong dính mép đầu khung
 * (`sticky start-0`) và rộng đúng phần nhìn thấy của khung (`--table-frame-width`, đo trong `useInlineOverflow`):
 * thông báo luôn nằm giữa phần đang thấy, cuộn ngang cũng không trôi đi. Chưa đo được (lần dựng đầu) thì rộng theo ô.
 */
const frameWideCell = "sticky start-0 w-[var(--table-frame-width,100%)]";
/** Hằng ổn định: `?? []`/`?? {}` tạo đối tượng mới mỗi lần render làm TanStack tính lại hàng và tự đặt lại trang (vòng lặp render). */
const EMPTY_SORTING: SortingState = [];
const EMPTY_ROW_SELECTION: RowSelectionState = {};

/*
 * Đường kẻ ngang nằm ở ô, không ở dòng, và bảng dùng `border-separate`: với `border-collapse`, đường kẻ thuộc về
 * lưới của bảng nên không đi theo ô `position: sticky`, nền đặc của ô ghim sẽ che mất đường kẻ dưới nó.
 */
const cellBorder = "border-b border-border";
const headCell = cn(
  "h-11 bg-table-head px-4 text-left text-field-label font-semibold whitespace-nowrap text-text-muted",
  cellBorder,
);
const bodyCell = cn("h-12 px-4 align-middle text-body text-text tabular-nums", cellBorder);
/** Cột chọn dòng: đệm ngang 2rem + ô chọn 1.25rem. Bề rộng này cũng là độ lệch của cột ghim đứng sau nó (`start-13`). */
const selectCell = "w-13";

/*
 * Ô ghim (`stickyColumns`). `z-1` để nằm trên cả phần tử có `position` trong các ô cuộn qua bên dưới. Nền của ô
 * thân bảng lấy từ dòng (`bg-inherit`), nên dòng phải có nền đặc (`pinnedRow`) và ô không tự chuyển màu: màu nền
 * thừa kế đã đổi dần theo dòng.
 */
const pinnedCell = "sticky z-1";
const pinnedBodyCell = "bg-inherit";
const pinnedRow = "bg-surface";
/*
 * Vạch kẻ ở mép trong của cột ghim, vẽ bằng viền của phần tử giả (không chiếm chỗ, còn thấy ở chế độ tương phản
 * cao). Viền chỉ tồn tại khi khung cuộn còn cuộn được về phía cột ghim đó, tức là đang có nội dung khuất dưới nó.
 * Trình duyệt chưa có truy vấn trạng thái cuộn thì hiện vạch kẻ suốt lúc bảng tràn ngang (`data-overflows`).
 */
const pinnedEdge = "after:pointer-events-none after:absolute after:inset-y-0 after:border-border-strong";
const pinnedEdgeStart =
  "after:end-0 [@container_scroll-state(scrollable:inline-start)]:after:border-e not-supports-[container-type:scroll-state]:group-data-[overflows]/table:after:border-e";
const pinnedEdgeEnd =
  "after:start-0 [@container_scroll-state(scrollable:inline-end)]:after:border-s not-supports-[container-type:scroll-state]:group-data-[overflows]/table:after:border-s";

/**
 * Chừa chỗ cho cột ghim khi trình duyệt tự cuộn tới phần tử vừa nhận focus: không có `scroll-padding`, một liên
 * kết hay nút trong ô thường có thể dừng lại ngay dưới cột ghim và bị che (WCAG 2.4.11). Bề rộng cột ghim theo
 * nội dung nên phải đo ở hàng tiêu đề; bảng không ghim cột thì hai giá trị được xóa.
 */
function syncPinnedScrollPadding(scroller: HTMLElement) {
  const padding = { start: 0, end: 0 };
  for (const cell of scroller.querySelectorAll<HTMLElement>("thead [data-pinned]")) {
    padding[cell.dataset.pinned === "end" ? "end" : "start"] += cell.offsetWidth;
  }
  scroller.style.setProperty("scroll-padding-inline-start", padding.start > 0 ? `${padding.start}px` : "");
  scroller.style.setProperty("scroll-padding-inline-end", padding.end > 0 ? `${padding.end}px` : "");
}

/**
 * Theo dõi khung bọc có đang tràn ngang hay không. Chỉ khi tràn, khung mới cần là điểm dừng Tab và là vùng có
 * tên: người chỉ dùng bàn phím cuộn ngang được bằng phím mũi tên, còn bảng vừa khung thì không thêm điểm dừng
 * vô ích và trình đọc màn hình không đọc tên bảng hai lần. Tràn hay không phụ thuộc cả bề rộng khung (cửa sổ,
 * sidebar) lẫn bề rộng bảng (dữ liệu), CSS không tự đặt `tabindex` được, nên dùng `ResizeObserver` trên cả hai.
 */
function useInlineOverflow() {
  const ref = useRef<HTMLDivElement>(null);
  const [overflows, setOverflows] = useState(false);

  useEffect(() => {
    const scroller = ref.current;
    if (!scroller || typeof ResizeObserver === "undefined") return;
    // Lệch 1px là do làm tròn khi thu phóng lẻ, chưa phải tràn thật.
    const observer = new ResizeObserver(() => {
      setOverflows(scroller.scrollWidth - scroller.clientWidth > 1);
      // Bề rộng phần nhìn thấy của khung, cho thông báo rỗng/lỗi đứng yên giữa khung khi bảng rộng hơn khung.
      scroller.style.setProperty("--table-frame-width", `${scroller.clientWidth}px`);
      syncPinnedScrollPadding(scroller);
    });
    observer.observe(scroller);
    if (scroller.firstElementChild) observer.observe(scroller.firstElementChild);
    return () => observer.disconnect();
  }, []);

  return [ref, overflows] as const;
}

const useIsomorphicLayoutEffect = typeof window === "undefined" ? useEffect : useLayoutEffect;

/**
 * Khung chứa có hẹp hơn `breakpoint` không, đo bằng `ResizeObserver` (đổi theo cửa sổ, sidebar, bố cục của màn).
 * Chọn một trong hai dạng để dựng chứ không dựng cả hai rồi ẩn bằng CSS: nội dung không bị lặp trong DOM và cây
 * truy cập. Chưa đo được (máy chủ, jsdom, bề rộng 0) thì là bảng.
 *
 * Xuất ra cho bảng tự viết bằng `<table>` (DESIGN.md mục 5.9): gắn `ref` lên một phần tử luôn có mặt ở cả hai dạng
 * (khối bọc ngoài bảng, không phải chính bảng), vì phần tử đo chỉ được lấy một lần khi `enabled`/`breakpoint` đổi.
 */
export function useCardMode<TElement extends HTMLElement = HTMLDivElement>(enabled: boolean, breakpoint: number) {
  const ref = useRef<TElement>(null);
  const [cards, setCards] = useState(false);

  useIsomorphicLayoutEffect(() => {
    const element = ref.current;
    if (!enabled || !element) return;
    const measure = () => {
      const width = element.getBoundingClientRect().width;
      if (width > 0) setCards(width < breakpoint);
    };
    measure();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [enabled, breakpoint]);

  return [ref, enabled && cards] as const;
}

/** Bọc TanStack Table: sắp xếp, chọn dòng, trạng thái tải/rỗng/lỗi (DESIGN.md mục 5.9). */
export function Table<TData>({
  columns,
  data,
  getRowId,
  sorting,
  selection,
  loading = false,
  loadingRows = 5,
  refreshing = false,
  stickyColumns,
  error,
  empty,
  cards,
  className,
  "aria-label": ariaLabel,
}: TableProps<TData>) {
  const [modeRef, cardMode] = useCardMode(cards !== undefined, cards?.breakpoint ?? 640);
  const selectAllId = useId();
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

  const [scrollerRef, overflows] = useInlineOverflow();

  const columnCount = allColumns.length;
  const rows = table.getRowModel().rows;
  const selectedOnPage = rows.reduce((count, row) => count + (row.getIsSelected() ? 1 : 0), 0);
  const selectAllChecked =
    rows.length > 0 && selectedOnPage === rows.length
      ? true
      : selectedOnPage > 0
        ? "indeterminate"
        : false;

  const firstDataIndex = hasSelection ? 1 : 0;
  const lastIndex = columnCount - 1;
  const pinStart = stickyColumns?.start === true;
  const pinEnd = stickyColumns?.end === true && lastIndex > firstDataIndex;
  const pinned = pinStart || pinEnd;
  /** Cột thứ `index` (tính cả cột chọn dòng) ghim về mép nào; `undefined` là không ghim. */
  function pinnedSide(index: number): "start" | "end" | undefined {
    if (pinStart && index <= firstDataIndex) return "start";
    if (pinEnd && index === lastIndex) return "end";
    return undefined;
  }
  /** Lớp ghim của ô ở cột thứ `index`; cột không ghim thì không có lớp nào. */
  function pinnedClass(index: number): string | false {
    const side = pinnedSide(index);
    if (side === "end") return cn(pinnedCell, pinnedEdge, pinnedEdgeEnd, "end-0");
    if (side !== "start") return false;
    // Cột chọn dòng: bề rộng cố định để cột dữ liệu ghim sau nó biết lệch bao nhiêu; vạch kẻ chỉ ở cột dữ liệu.
    if (index < firstDataIndex) return cn(pinnedCell, "start-0 min-w-13 max-w-13");
    return cn(pinnedCell, pinnedEdge, pinnedEdgeStart, hasSelection ? "start-13" : "start-0");
  }
  /** Lớp của ô thân bảng ở cột thứ `index`: ô ghim thêm vị trí ghim và lấy nền từ dòng. */
  function bodyCellClass(index: number, extra?: string): string {
    const pin = pinnedClass(index);
    return cn(bodyCell, extra, pin && [pin, pinnedBodyCell]);
  }

  const tableView = (
    <div
      ref={scrollerRef}
      {...(overflows ? { role: "region", "aria-label": ariaLabel, tabIndex: 0 } : null)}
      data-overflows={overflows || undefined}
      className={cn(
        "overflow-x-auto",
        // `isolate`: ô ghim (`z-1`) không trồi lên phần tử bên ngoài bảng, và vòng focus của khung vẽ trên ô ghim.
        pinned && "group/table isolate [container-type:scroll-state]",
        focusRingInset,
        className,
      )}
    >
      <table
        aria-label={ariaLabel}
        aria-busy={loading || refreshing || undefined}
        className="w-full border-separate border-spacing-0"
      >
        <thead>
          {table.getHeaderGroups().map((group) => (
            <tr key={group.id}>
              {group.headers.map((header, headerIndex) => {
                const sorted = header.column.getIsSorted();
                const sortable = header.column.getCanSort();
                return (
                  <th
                    key={header.id}
                    scope="col"
                    // Chỉ cột đang sắp xếp mới mang `aria-sort`; cột sắp xếp được nhưng chưa sắp xếp thì nút trong ô
                    // đã cho biết là bấm được, `aria-sort="none"` chỉ làm trình đọc màn hình đọc thừa ở mọi cột.
                    aria-sort={sorted === "asc" ? "ascending" : sorted === "desc" ? "descending" : undefined}
                    data-pinned={pinnedSide(headerIndex)}
                    className={cn(
                      headCell,
                      // Mốc cho vùng bấm của nút sắp xếp (`after:inset-0`); ô ghim đã là `sticky` nên không cần.
                      sortable && "relative",
                      header.column.id === SELECT_COLUMN_ID && selectCell,
                      pinnedClass(headerIndex),
                    )}
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
                        // Vùng bấm phủ hết ô tiêu đề (phần tử giả), không chỉ dòng chữ cao 20px; bố cục ô không đổi.
                        className={cn(
                          "inline-flex items-center gap-1.5 rounded-sm font-semibold after:absolute after:inset-0",
                          focusRing,
                        )}
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
        <tbody
          className={cn(
            "transition-opacity duration-150 motion-reduce:transition-none",
            refreshing && !loading && "opacity-60",
          )}
        >
          {loading ? (
            Array.from({ length: loadingRows }, (_, rowIndex) => (
              <tr key={rowIndex} className={cn(pinned && pinnedRow)}>
                {allColumns.map((_, cellIndex) => (
                  <td key={cellIndex} className={bodyCellClass(cellIndex)}>
                    <Skeleton className="h-4 w-full max-w-40" />
                  </td>
                ))}
              </tr>
            ))
          ) : error ? (
            <tr>
              {/* `role="alert"` nằm ở phần tử bọc: đặt lên `<td>` thì ô mất vai trò ô của bảng. */}
              <td colSpan={columnCount} className="p-0">
                <div role="alert" className={frameWideCell}>
                  {error}
                </div>
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td colSpan={columnCount} className="p-0">
                <div className={frameWideCell}>{empty}</div>
              </td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr
                key={row.id}
                data-state={row.getIsSelected() ? "selected" : undefined}
                className={cn(
                  "group/row transition-colors duration-150 hover:bg-subtle data-[state=selected]:bg-primary-50 motion-reduce:transition-none",
                  pinned && pinnedRow,
                )}
              >
                {row.getVisibleCells().map((cell, cellIndex) => (
                  <td key={cell.id} className={bodyCellClass(cellIndex, "group-last/row:border-b-0")}>
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

  if (!cards) return tableView;

  /** Ô chọn sắp xếp của dạng thẻ: mỗi cột sắp xếp được có hai lựa chọn, tăng dần và giảm dần. */
  const sortLabels = cards.sortLabels;
  const sortOptions =
    sorting && sortLabels
      ? table.getAllLeafColumns().flatMap((column) => {
          if (!column.getCanSort()) return [];
          const header = column.columnDef.header;
          const label =
            typeof header === "string" ? header : (column.columnDef.meta as { label?: string } | undefined)?.label;
          if (!label) return [];
          return [
            { value: `${column.id}:asc`, label: `${label} · ${sortLabels.ascending}` },
            { value: `${column.id}:desc`, label: `${label} · ${sortLabels.descending}` },
          ];
        })
      : [];
  const currentSort = sorting?.state[0];
  const showSelectAll = selection !== undefined && !loading && !error && rows.length > 0;

  const cardView = (
    <div>
      {(showSelectAll || sortOptions.length > 0) && (
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-b border-border px-4 py-2.5">
          {showSelectAll ? (
            <div className="flex min-h-11 items-center gap-3">
              <Checkbox
                id={selectAllId}
                checked={selectAllChecked}
                onCheckedChange={(checked) => table.toggleAllPageRowsSelected(checked === true)}
              />
              <label htmlFor={selectAllId} className="text-label font-medium text-text-secondary">
                {selection.selectAllLabel}
              </label>
            </div>
          ) : (
            <span />
          )}
          {sortLabels && sortOptions.length > 0 && (
            <Select
              aria-label={sortLabels.label}
              placeholder={sortLabels.label}
              options={sortOptions}
              value={currentSort ? `${currentSort.id}:${currentSort.desc ? "desc" : "asc"}` : undefined}
              onValueChange={(value) => {
                const separator = value.lastIndexOf(":");
                table.setSorting([{ id: value.slice(0, separator), desc: value.slice(separator + 1) === "desc" }]);
              }}
              className="w-auto max-w-full min-w-48"
            />
          )}
        </div>
      )}
      <ul
        // Tailwind bỏ dấu đầu dòng (`list-style: none`) làm Safari/VoiceOver không coi là danh sách: ghi rõ vai trò.
        role="list"
        aria-label={ariaLabel}
        aria-busy={loading || refreshing || undefined}
        className={cn(
          "divide-y divide-border transition-opacity duration-150 motion-reduce:transition-none",
          refreshing && !loading && "opacity-60",
        )}
      >
        {loading ? (
          Array.from({ length: loadingRows }, (_, index) => (
            <li key={index} className="flex flex-col gap-2 px-4 py-3.5">
              <Skeleton className="h-4 w-2/5" />
              <Skeleton className="h-3.5 w-3/5" />
            </li>
          ))
        ) : error ? (
          <li>
            <div role="alert">{error}</div>
          </li>
        ) : rows.length === 0 ? (
          <li>{empty}</li>
        ) : (
          rows.map((row) => (
            <li
              key={row.id}
              data-state={row.getIsSelected() ? "selected" : undefined}
              className="relative flex items-start gap-3 px-4 py-3 transition-colors duration-150 hover:bg-subtle data-[state=selected]:bg-primary-50 motion-reduce:transition-none"
            >
              {selection && (
                <div className="relative z-1 flex min-h-6 items-center">
                  <Checkbox
                    aria-label={selection.rowLabel(row.index, row.original)}
                    checked={row.getIsSelected()}
                    onCheckedChange={(checked) => row.toggleSelected(checked === true)}
                  />
                </div>
              )}
              <div className="min-w-0 flex-1">{cards.render(row.original, row.index)}</div>
            </li>
          ))
        )}
      </ul>
    </div>
  );

  return (
    // Phần tử đo bề rộng: cùng một phần tử ở cả hai dạng, để đổi dạng không làm mất điểm đo.
    <div ref={modeRef} className="min-w-0">
      {cardMode ? cardView : tableView}
    </div>
  );
}
