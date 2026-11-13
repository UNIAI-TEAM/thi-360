import type { ReactNode } from "react";
import { Sidebar } from "./sidebar";
import { Topbar } from "./topbar";

/**
 * Khung trang quản trị: sidebar 248px, thanh trên 56px, vùng nội dung nền `page` (DESIGN 5.8).
 * DESIGN không định nghĩa breakpoint hẹp: sidebar giữ 248px, vùng nội dung có độ rộng tối thiểu
 * và cuộn ngang khi cửa sổ hẹp hơn, thay vì bị ép lại.
 */
export function AppShell({ children, skipLabel }: { children: ReactNode; skipLabel: string }) {
  return (
    <div className="flex min-h-screen bg-page text-text">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:m-2 focus:rounded-sm focus:bg-surface focus:px-3 focus:py-2"
      >
        {skipLabel}
      </a>
      <Sidebar />
      <div data-testid="content-scroll" className="min-w-0 flex-1 overflow-x-auto">
        <div className="flex min-h-full min-w-120 flex-col">
          <Topbar />
          <main id="main" className="flex flex-col gap-5 p-6">
            {children}
          </main>
        </div>
      </div>
    </div>
  );
}
