import { Chip, cn, focusRing } from "@thi360/ui";
import Link from "next/link";
import { Fragment, type ReactNode } from "react";

/**
 * Bố cục một thẻ của bảng ở khung hẹp (DESIGN.md mục 5.9, "Bảng ở khung hẹp thành danh sách thẻ"), dùng cho
 * `cards.render` của `Table` ở các màn Tài khoản và Lớp học:
 * 1. hàng đầu: tiêu đề (tối đa hai dòng) và thao tác bên phải;
 * 2. dòng phụ `caption`;
 * 3. hàng chip: chip trạng thái luôn có, sau đó các chip phân loại;
 * 4. thông tin thêm "Nhãn: giá trị", nối bằng " · ".
 *
 * Thao tác nằm trên vùng bấm của liên kết tiêu đề (`relative z-1`). Thẻ (`<li>` của `Table`) đã là `relative`.
 */
export function ListCard({
  title,
  actions,
  caption,
  status,
  tags,
  meta,
}: {
  /** Chữ (tự cắt ở hai dòng) hoặc `ListCardTitleLink`. */
  title: ReactNode;
  actions?: ReactNode;
  caption?: ReactNode;
  /** Chip trạng thái của dòng, luôn hiện kể cả khi là trạng thái thường. */
  status: ReactNode;
  /** Chip phân loại (vai trò, nguồn, kiểu), đứng sau chip trạng thái. */
  tags?: ReactNode;
  meta?: ListCardMetaItem[];
}) {
  const metaItems = meta?.filter((item) => item !== false) ?? [];
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <div className="flex items-start justify-between gap-2">
        <div className="min-h-6 min-w-0 flex-1 py-0.5 text-body font-semibold wrap-anywhere text-text">
          {typeof title === "string" ? <span className="line-clamp-2">{title}</span> : title}
        </div>
        {/* Nút 40px chỉ chiếm 24px của hàng đầu (lề âm), để hàng đầu cao bằng tiêu đề như ô chọn dòng bên trái. */}
        {actions && <div className="relative z-1 -my-2 -me-2 flex shrink-0 items-center gap-0.5">{actions}</div>}
      </div>
      {caption && <p className="line-clamp-2 text-caption wrap-anywhere text-text-muted">{caption}</p>}
      <div className="flex flex-wrap items-center gap-1.5">
        {status}
        {tags}
      </div>
      {metaItems.length > 0 && (
        <p className="text-caption text-text-muted tabular-nums">
          {metaItems.map((item, index) => (
            <Fragment key={item.label}>
              {index > 0 && " · "}
              <span className="wrap-anywhere">
                {item.label}: <span className="text-text-secondary">{item.value}</span>
              </span>
            </Fragment>
          ))}
        </p>
      )}
    </div>
  );
}

/** Một mục "Nhãn: giá trị" của dòng thông tin thêm; `false` để bỏ qua theo điều kiện. */
export type ListCardMetaItem = { label: string; value: ReactNode } | false;

/**
 * Liên kết tiêu đề của thẻ: vùng bấm phủ cả thẻ (phần tử giả), tên truy cập vẫn chỉ là chữ của tiêu đề. Phần cắt
 * hai dòng nằm ở bên trong liên kết để `overflow: hidden` của nó không cắt mất vòng focus.
 */
export function ListCardTitleLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link
      href={href}
      className={cn("block rounded-sm text-primary-text after:absolute after:inset-0 hover:underline", focusRing)}
    >
      <span className="line-clamp-2">{children}</span>
    </Link>
  );
}

/** Chip phân loại chỉ để đọc, màu trung tính, cao bằng chip trạng thái (DESIGN.md mục 5.3). */
export function ListCardTag({ children }: { children: ReactNode }) {
  return (
    <Chip variant="tag" className="h-6 border-border bg-surface px-2.5 text-text-secondary">
      {children}
    </Chip>
  );
}
