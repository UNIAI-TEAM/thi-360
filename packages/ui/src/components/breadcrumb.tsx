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

/** Mục cuối là trang hiện tại, không là liên kết. Không có mục nào thì không hiện gì (tránh vùng điều hướng rỗng). */
export function Breadcrumb({
  items,
  linkComponent: LinkComponent = DefaultLink,
  className,
  "aria-label": ariaLabel,
}: BreadcrumbProps) {
  if (items.length === 0) return null;
  const lastIndex = items.length - 1;
  return (
    <nav aria-label={ariaLabel} className={className}>
      <ol className="flex flex-wrap items-center gap-1.5 text-body">
        {items.map((item, index) => (
          <Fragment key={`${index}-${item.label}`}>
            {index > 0 && (
              <li aria-hidden className="text-text-placeholder">
                <ChevronRight className="size-4" />
              </li>
            )}
            <li>
              {item.href && index < lastIndex ? (
                <LinkComponent
                  href={item.href}
                  className={cn(
                    "rounded-sm text-text-muted transition-colors duration-150 hover:text-primary-600 motion-reduce:transition-none",
                    focusRing,
                  )}
                >
                  {item.label}
                </LinkComponent>
              ) : (
                <span
                  aria-current={index === lastIndex ? "page" : undefined}
                  className={index === lastIndex ? "font-medium text-text" : "text-text-muted"}
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
