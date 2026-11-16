import { cn } from "@thi360/ui";
import type { ReactNode } from "react";

export type PreviewDevice = "web" | "phone";

/**
 * Khung hiển thị của xem trước câu hỏi (UniNote PBI-03 mục 8.5). `web`: cửa sổ trình duyệt rộng, nội dung dàn hai cột
 * khi đủ chỗ. `phone`: khung điện thoại 360px, nội dung xếp một cột. Nội dung bên trong đo theo bề rộng khung
 * (container query), không theo cửa sổ admin, nên hai khung khác nhau dù cùng một màn hình.
 */
export function PreviewFrame({ device, label, children }: { device: PreviewDevice; label: string; children: ReactNode }) {
  if (device === "phone") {
    return (
      <div
        role="group"
        aria-label={label}
        data-device="phone"
        className="mx-auto flex w-90 max-w-full flex-col gap-3 rounded-2xl border-4 border-border-strong bg-surface p-3 shadow-md"
      >
        <span aria-hidden className="mx-auto h-1.5 w-16 rounded-full bg-border-strong" />
        <div className="@container min-w-0 rounded-lg bg-subtle p-3">{children}</div>
      </div>
    );
  }
  return (
    <div
      role="group"
      aria-label={label}
      data-device="web"
      className="flex w-full flex-col overflow-hidden rounded-xl border border-border bg-surface shadow-md"
    >
      <div aria-hidden className="flex items-center gap-1.5 border-b border-border bg-subtle px-3 py-2.5">
        {[0, 1, 2].map((dot) => (
          <span key={dot} className="size-2.5 rounded-full bg-border-strong" />
        ))}
        <span className="ms-3 h-5 flex-1 rounded-full bg-surface" />
      </div>
      <div className={cn("@container min-w-0 bg-subtle p-6")}>
        <div className="mx-auto max-w-3xl rounded-lg border border-border bg-surface p-5">{children}</div>
      </div>
    </div>
  );
}
