import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

export interface StatusPageProps {
  icon: LucideIcon;
  /** Dòng mã lỗi phía trên tiêu đề, ví dụ "MÃ LỖI 403". */
  eyebrow: string;
  title: string;
  description: ReactNode;
  actions: ReactNode;
  meta?: ReactNode;
}

/** Khuôn trang trạng thái toàn trang (403, 404, 500), theo `Forbidden.dc.html`. */
export function StatusPage({ icon: Icon, eyebrow, title, description, actions, meta }: StatusPageProps) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-page p-6">
      <div className="flex w-full max-w-130 flex-col items-center gap-4 rounded-2xl border border-border bg-surface px-10 py-12 text-center shadow-xl">
        <span aria-hidden className="flex size-14 items-center justify-center rounded-xl bg-danger-bg text-danger">
          <Icon className="size-7" />
        </span>
        <p className="text-overline font-semibold tracking-[0.08em] text-danger">{eyebrow}</p>
        <h1 className="text-page-title font-bold text-text">{title}</h1>
        <p className="text-text-muted">{description}</p>
        {meta && <p className="text-caption text-text-muted">{meta}</p>}
        <div className="mt-2 flex flex-wrap justify-center gap-3">{actions}</div>
      </div>
    </main>
  );
}
