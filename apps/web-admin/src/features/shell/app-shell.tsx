import type { ReactNode } from "react";
import { BreadcrumbLeafProvider } from "./breadcrumb-leaf";
import { ShellMainProvider } from "./shell-main";
import { Sidebar } from "./sidebar";
import { Topbar } from "./topbar";

/**
 * Khung trang quản trị: sidebar 248px, thanh trên 56px, vùng nội dung nền `page` (DESIGN 5.8).
 *
 * Từ `lg` (1024px) sidebar cố định bên trái; dưới `lg` sidebar ẩn và menu mở trong ngăn kéo từ nút ở thanh trên.
 * Cột nội dung co theo cửa sổ (`min-w-0`), không có bề rộng tối thiểu và không tự cuộn ngang: nội dung rộng (bảng)
 * cuộn trong khung của chính nó, trang không bao giờ cuộn ngang. Lề nội dung 16px dưới `sm`, 24px từ `sm`.
 */
export function AppShell({ children, skipLabel }: { children: ReactNode; skipLabel: string }) {
  return (
    <BreadcrumbLeafProvider>
      <div className="flex min-h-screen bg-page text-text">
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:m-2 focus:rounded-sm focus:bg-surface focus:px-3 focus:py-2"
        >
          {skipLabel}
        </a>
        <Sidebar />
        <div data-testid="content-column" className="flex min-w-0 flex-1 flex-col">
          <Topbar />
          <main id="main" className="flex min-w-0 flex-col gap-5 p-4 sm:p-6">
            <ShellMainProvider>{children}</ShellMainProvider>
          </main>
        </div>
      </div>
    </BreadcrumbLeafProvider>
  );
}
