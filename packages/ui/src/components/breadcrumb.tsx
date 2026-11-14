import { ChevronRight } from "lucide-react";
import { Fragment, type ComponentType, type ReactNode } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";

export type BreadcrumbItem = { label: string; href?: string };

type LinkComponentProps = { href: string; className?: string; children: ReactNode };

function DefaultLink(props: LinkComponentProps) {
  return <a {...props} />;
}

export type BreadcrumbProps = {
  items: BreadcrumbItem[];
  /** Tên vùng điều hướng cho trình đọc màn hình. */
  "aria-label": string;
  /** Thành phần liên kết của router (ví dụ `next/link`) để chuyển trang không tải lại. Mặc định là thẻ `a`. */
  linkComponent?: ComponentType<LinkComponentProps>;
  className?: string;
};

/**
 * Mục cuối là trang hiện tại, không là liên kết. Không có mục nào thì không hiện gì (tránh vùng điều hướng rỗng).
 *
 * Luôn một dòng: thanh trên cùng cao cố định, xuống dòng sẽ tràn ra ngoài. Thiếu chỗ thì các mục co lại và cắt bằng
 * dấu ba chấm theo thứ tự: mục giữa trước, rồi mục đầu, mục cuối (trang hiện tại) sau cùng. Chữ đầy đủ vẫn nằm
 * trong DOM (tên của liên kết không đổi) và tên trang hiện tại có ở tiêu đề trang ngay bên dưới (DESIGN.md mục 5.13).
 */
export function Breadcrumb({
  items,
  linkComponent: LinkComponent = DefaultLink,
  className,
  "aria-label": ariaLabel,
}: BreadcrumbProps) {
  if (items.length === 0) return null;
  const lastIndex = items.length - 1;
  return (
    // `min-w-0`: trong một hàng flex (thanh trên cùng), vùng điều hướng phải co được thì các mục mới cắt chữ.
    <nav aria-label={ariaLabel} className={cn("min-w-0", className)}>
      <ol className="flex min-w-0 items-center gap-1.5 text-body">
        {items.map((item, index) => (
          <Fragment key={`${index}-${item.label}`}>
            {index > 0 && (
              <li aria-hidden className="shrink-0 text-text-placeholder">
                <ChevronRight className="size-4" />
              </li>
            )}
            <li
              className={cn(
                // Hệ số co chênh nhau rất xa để tạo thứ tự ưu tiên: mục nào hệ số lớn thì nhường chỗ trước.
                index === lastIndex ? "min-w-0 shrink" : "min-w-6",
                index !== lastIndex && (index === 0 ? "shrink-[100]" : "shrink-[10000]"),
              )}
            >
              {item.href && index < lastIndex ? (
                <LinkComponent
                  href={item.href}
                  className={cn(
                    "block truncate rounded-sm text-text-muted transition-colors duration-150 hover:text-primary-text motion-reduce:transition-none",
                    focusRing,
                  )}
                >
                  {item.label}
                </LinkComponent>
              ) : (
                <span
                  aria-current={index === lastIndex ? "page" : undefined}
                  className={cn("block truncate", index === lastIndex ? "font-medium text-text" : "text-text-muted")}
                >
                  {item.label}
                </span>
              )}
            </li>
          </Fragment>
        ))}
      </ol>
    </nav>
  );
}
