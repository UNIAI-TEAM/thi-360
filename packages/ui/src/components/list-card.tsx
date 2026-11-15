import { Fragment, type ComponentPropsWithRef, type ComponentType, type ReactNode } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import { Chip } from "./chip";
import { Skeleton } from "./skeleton";

/**
 * Danh sách thẻ thay cho bảng ở khung hẹp (DESIGN.md mục 5.9, "Bảng ở khung hẹp thành danh sách thẻ"). `Table` với
 * `cards` dùng sẵn; bảng tự viết bằng `<table>` dùng `CardList` + `CardListItem` + `ListCard` cùng `useCardMode`.
 *
 * Tailwind bỏ dấu đầu dòng (`list-style: none`) làm Safari/VoiceOver không coi là danh sách, nên ghi rõ `role="list"`.
 */
export function CardList({ className, ...props }: ComponentPropsWithRef<"ul">) {
  return <ul role="list" className={cn("divide-y divide-border", className)} {...props} />;
}

/**
 * Một thẻ của `CardList`: đệm 16px, ô chọn dòng hoặc nút sắp xếp (nếu có) đứng trước nội dung. Là `relative` để liên
 * kết tiêu đề phủ được cả thẻ.
 */
export function CardListItem({ className, ...props }: ComponentPropsWithRef<"li">) {
  return (
    <li
      className={cn(
        "relative flex items-start gap-3 px-4 py-3 transition-colors duration-150 motion-reduce:transition-none",
        className,
      )}
      {...props}
    />
  );
}

/** Thẻ khung xương khi đang tải: tiêu đề và một dòng phụ. */
export function CardListSkeletonItem() {
  return (
    <li className="flex flex-col gap-2 px-4 py-3.5">
      <Skeleton className="h-4 w-2/5" />
      <Skeleton className="h-3.5 w-3/5" />
    </li>
  );
}

/** Một mục "Nhãn: giá trị" của dòng thông tin thêm; `false`/`undefined` để bỏ qua theo điều kiện. */
export type ListCardMetaItem = { label: string; value: ReactNode } | false | undefined;

export type ListCardProps = {
  /** Chữ (tự cắt ở hai dòng), `ListCardTitleLink` hoặc `ListCardTitleButton`. */
  title: ReactNode;
  /** Nút "⋯" hoặc tối đa hai nút icon; tự nằm trên vùng bấm của tiêu đề. */
  actions?: ReactNode;
  /** Định danh thứ hai (họ tên, mã, email), chữ `caption`. */
  caption?: ReactNode;
  /** Chip trạng thái của dòng, luôn hiện kể cả trạng thái thường. Bỏ trống khi dòng không có trạng thái. */
  status?: ReactNode;
  /** Chip phân loại (vai trò, nguồn, kiểu), đứng sau chip trạng thái. */
  tags?: ReactNode;
  /** Nội dung thêm dưới hàng chip, ví dụ lý do lỗi của dòng. */
  children?: ReactNode;
  /** Thông tin thêm, nối bằng " · ". */
  meta?: ListCardMetaItem[];
};

/**
 * Bố cục một thẻ (DESIGN.md mục 5.9):
 * 1. hàng đầu: tiêu đề (tối đa hai dòng) và thao tác bên phải;
 * 2. dòng phụ `caption`;
 * 3. hàng chip: chip trạng thái, sau đó các chip phân loại;
 * 4. nội dung thêm (`children`), rồi thông tin "Nhãn: giá trị" nối bằng " · ".
 */
export function ListCard({ title, actions, caption, status, tags, children, meta }: ListCardProps) {
  const metaItems = (meta ?? []).filter((item): item is { label: string; value: ReactNode } => Boolean(item));
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
      {(status || tags) && (
        <div className="flex flex-wrap items-center gap-1.5">
          {status}
          {tags}
        </div>
      )}
      {children}
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

const titleTarget = "block rounded-sm text-start after:absolute after:inset-0";

type LinkComponentProps = { href: string; className?: string; children: ReactNode };

function DefaultLink(props: LinkComponentProps) {
  return <a {...props} />;
}

/**
 * Liên kết tiêu đề của thẻ: vùng bấm phủ cả thẻ (phần tử giả), tên truy cập vẫn chỉ là chữ của tiêu đề. Phần cắt hai
 * dòng nằm bên trong liên kết để `overflow: hidden` của nó không cắt vòng focus. Ứng dụng truyền liên kết của router
 * qua `linkComponent` (ví dụ `next/link`, như `Breadcrumb`); mặc định là `<a>`.
 */
export function ListCardTitleLink({
  href,
  linkComponent: LinkComponent = DefaultLink,
  children,
}: {
  href: string;
  linkComponent?: ComponentType<LinkComponentProps>;
  children: ReactNode;
}) {
  return (
    <LinkComponent href={href} className={cn(titleTarget, "text-primary-text hover:underline", focusRing)}>
      <span className="line-clamp-2">{children}</span>
    </LinkComponent>
  );
}

/** Tiêu đề của thẻ mở chi tiết trong ngăn hoặc hộp thoại (dòng không có trang riêng): nút phủ cả thẻ. */
export function ListCardTitleButton({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <button type="button" onClick={onClick} className={cn(titleTarget, "font-semibold text-text", focusRing)}>
      <span className="line-clamp-2">{children}</span>
    </button>
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
