"use client";

import { createContext, useContext, type ReactNode } from "react";

const InsideShellMainContext = createContext(false);

/**
 * Đánh dấu phần nội dung nằm trong `<main>` của khung trang quản trị. Thành phần dựng được cả ở trang riêng lẫn trong
 * khung (trang 403) dựa vào đây để không dựng thêm một `<main>` thứ hai lồng bên trong.
 */
export function ShellMainProvider({ children }: { children: ReactNode }) {
  return <InsideShellMainContext.Provider value>{children}</InsideShellMainContext.Provider>;
}

export function useInsideShellMain(): boolean {
  return useContext(InsideShellMainContext);
}
