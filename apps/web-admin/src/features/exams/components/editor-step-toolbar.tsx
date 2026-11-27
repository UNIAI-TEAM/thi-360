import { cn } from "@thi360/ui";
import type { ReactNode } from "react";

/**
 * Hàng thao tác của các bước 1, 3, 4 trong trình soạn bài thi: dính đầu vùng soạn như thanh công cụ ở bước 2, để đi
 * tiếp, lưu nháp, công bố không phải cuộn hết trang (UNI-2876). `start` là phần bên trái (ghi chú, tab), `children` là
 * các nút bên phải theo thứ tự Quay lại → phụ → chính.
 */
export function EditorStepToolbar({
  start,
  children,
  className,
  testId,
}: {
  start?: ReactNode;
  children: ReactNode;
  className?: string;
  testId?: string;
}) {
  return (
    <div
      data-testid={testId}
      className={cn(
        "sticky top-0 z-20 flex flex-wrap items-center gap-3 rounded-lg border border-border bg-surface/95 px-4 py-3 shadow-xs backdrop-blur-xs sm:px-5",
        start ? "justify-between" : "justify-end",
        className,
      )}
    >
      {start}
      <div className="flex flex-wrap items-center justify-end gap-2">{children}</div>
    </div>
  );
}
