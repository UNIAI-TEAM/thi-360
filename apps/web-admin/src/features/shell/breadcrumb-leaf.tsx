"use client";

import { createContext, type ReactNode, useContext, useEffect, useState } from "react";

interface BreadcrumbLeafContextValue {
  leaf: string | null;
  setLeaf: (leaf: string | null) => void;
}

const BreadcrumbLeafContext = createContext<BreadcrumbLeafContextValue | null>(null);

/**
 * Mục cuối breadcrumb do trang con cung cấp (ví dụ mã lớp ở `/classes/[id]`), vì menu chỉ biết
 * mục cha. Topbar đọc giá trị này để thêm mục hiện tại sau mục menu.
 */
export function BreadcrumbLeafProvider({ children }: { children: ReactNode }) {
  const [leaf, setLeaf] = useState<string | null>(null);
  return (
    <BreadcrumbLeafContext.Provider value={{ leaf, setLeaf }}>
      {children}
    </BreadcrumbLeafContext.Provider>
  );
}

/** Mục cuối breadcrumb hiện tại, hoặc `null` khi trang không cung cấp. */
export function useBreadcrumbLeaf(): string | null {
  return useContext(BreadcrumbLeafContext)?.leaf ?? null;
}

/**
 * Trang con gọi hook này với nhãn mục cuối; nhãn được gỡ khi trang rời đi. Ngoài `AppShell` hook
 * không làm gì.
 */
export function useSetBreadcrumbLeaf(label: string | null | undefined): void {
  const setLeaf = useContext(BreadcrumbLeafContext)?.setLeaf;
  useEffect(() => {
    if (!setLeaf || !label) return;
    setLeaf(label);
    return () => setLeaf(null);
  }, [setLeaf, label]);
}
