import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "../lib/cn";

export interface StatusPageProps {
  icon: LucideIcon;
  /** Dòng mã lỗi phía trên tiêu đề, ví dụ "MÃ LỖI 403". */
  eyebrow: string;
  title: string;
  description: ReactNode;
  actions: ReactNode;
  meta?: ReactNode;
  /**
   * Nhúng vào một trang đã có `<main>` (màn trong khung admin hiện trạng thái không có quyền): không dựng `<main>`
   * thứ hai, không chiếm trọn chiều cao màn hình và không tô nền trang. Mặc định `false`: trang trạng thái đứng
   * riêng (403, 404, 500 toàn trang) tự là `<main>` của trang.
   */
  embedded?: boolean;
}

/** Khuôn trang trạng thái (403, 404, 500), theo `Forbidden.dc.html`. Toàn trang, hoặc nhúng trong khung với `embedded`. */
export function StatusPage({
  icon: Icon,
  eyebrow,
  title,
  description,
  actions,
  meta,
  embedded = false,
}: StatusPageProps) {
  const Landmark = embedded ? "div" : "main";
  return (
    <Landmark
      className={cn("flex items-center justify-center p-4 sm:p-6", !embedded && "min-h-screen bg-page")}
    >
      {/* `@container`: hàng nút xếp theo bề rộng của thẻ, không theo màn hình (thẻ còn nằm trong khung admin). */}
      <div className="@container flex w-full max-w-130 flex-col items-center gap-4 rounded-2xl border border-border bg-surface px-6 py-10 text-center shadow-xl sm:px-10 sm:py-12">
        <span aria-hidden className="flex size-14 items-center justify-center rounded-xl bg-danger-bg text-danger">
          <Icon className="size-7" />
        </span>
        <p className="text-overline font-semibold tracking-[0.08em] text-danger">{eyebrow}</p>
        <h1 className="text-page-title font-bold text-text">{title}</h1>
        <p className="text-text-muted">{description}</p>
        {meta && <p className="text-caption text-text-muted">{meta}</p>}
        {/*
         * Thẻ hẹp (điện thoại 320–360px): nút xếp chồng, rộng hết thẻ, nhãn dài được xuống dòng và nút cao theo
         * nhãn, thay vì một dòng tràn ra ngoài thẻ. Thẻ đủ rộng thì các nút đứng cạnh nhau như thường.
         */}
        <div className="mt-2 flex flex-wrap justify-center gap-3 @max-md:w-full @max-md:flex-col @max-md:items-stretch @max-md:[&>*]:h-auto @max-md:[&>*]:min-h-12 @max-md:[&>*]:py-3 @max-md:[&>*]:whitespace-normal">
          {actions}
        </div>
      </div>
    </Landmark>
  );
}
