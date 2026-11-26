import { ArrowLeft } from "lucide-react";
import type { ComponentType, HTMLAttributes, ReactNode } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";

type LinkComponentProps = { href: string; className?: string; children: ReactNode };

function DefaultLink(props: LinkComponentProps) {
  return <a {...props} />;
}

export type DetailHeaderProps = {
  /** Lối quay lại, đặt trên tiêu đề, bên trái: "Danh sách ‹mục›" (hoặc "Chi tiết ‹đối tượng›" ở trang sửa). */
  back?: { href: string; label: string };
  /** Thành phần liên kết của router (ví dụ `next/link`). Mặc định là thẻ `a`. */
  linkComponent?: ComponentType<LinkComponentProps>;
  /** Tên đối tượng (h1). */
  title: ReactNode;
  /** Thuộc tính thêm cho h1, ví dụ `{ "data-exam-content": "" }` khi tiêu đề là nội dung đề thi. */
  titleProps?: HTMLAttributes<HTMLHeadingElement> & Record<`data-${string}`, string | boolean | undefined>;
  /** Chip trạng thái, nằm ngay cạnh tiêu đề và xuống dòng cùng tiêu đề khi hẹp. */
  chips?: ReactNode;
  /** Dòng phụ dưới tiêu đề: mã và thông tin chính. */
  description?: ReactNode;
  /** Nút của màn, bên phải hàng tiêu đề: nút phụ trước, nút chính, nút Xóa (đỏ) cuối cùng. */
  actions?: ReactNode;
  /** Phần ngay dưới hàng tiêu đề: dải thông tin `<dl>`, ghi chú khóa thao tác… Tab và nội dung nằm ngoài đầu trang. */
  children?: ReactNode;
  className?: string;
};

/**
 * Đầu trang của mọi màn chi tiết web quản trị (DESIGN.md mục 5.8, `UNI-2825`). Nằm trên nền trang, không đặt trong thẻ.
 *
 * Thứ tự: lối quay lại → hàng tiêu đề (tiêu đề + chip bên trái, nút bên phải) → dòng phụ → phần thêm (`children`).
 * Màn hẹp hơn `sm`: nút xuống dưới tiêu đề, căn trái, tự xuống dòng; từ `sm` nút cùng hàng với tiêu đề, căn phải.
 */
export function DetailHeader({
  back,
  linkComponent: LinkComponent = DefaultLink,
  title,
  titleProps,
  chips,
  description,
  actions,
  children,
  className,
}: DetailHeaderProps) {
  return (
    <header data-slot="detail-header" className={cn("flex flex-col gap-3", className)}>
      {back && (
        <LinkComponent
          href={back.href}
          className={cn(
            "inline-flex min-h-6 w-fit items-center gap-1.5 rounded-sm pointer-coarse:min-h-11 text-label font-medium text-text-secondary hover:text-text",
            focusRing,
          )}
        >
          <ArrowLeft aria-hidden className="size-4 shrink-0" />
          {back.label}
        </LinkComponent>
      )}
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-start sm:justify-between sm:gap-x-4">
        <div className="min-w-0 max-w-full">
          <div className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-2">
            <h1
              {...titleProps}
              className={cn("min-w-0 text-page-title font-bold wrap-break-word text-text", titleProps?.className)}
            >
              {title}
            </h1>
            {chips}
          </div>
          {description && <div className="mt-1 text-body wrap-break-word text-text-muted">{description}</div>}
        </div>
        {actions && (
          <div data-slot="page-actions" className="flex max-w-full flex-wrap items-center gap-3 empty:hidden">
            {actions}
          </div>
        )}
      </div>
      {children}
    </header>
  );
}
