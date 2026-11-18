"use client";

import { createContext, useContext, type ReactNode } from "react";
import { createPortal } from "react-dom";

/** Chỗ ở hàng tiêu đề của trang soạn bài thi (bản vẽ `BaiThiBuoc2`) dành cho nút hành động của trang con. */
export const ExamEditorActionsSlot = createContext<HTMLElement | null>(null);

/**
 * Đặt nút hành động của trang con vào hàng tiêu đề do `ExamEditorLayout` dựng. Không có khung (hiển thị riêng
 * một trang con) thì nút nằm ngay tại chỗ gọi.
 */
export function ExamEditorActions({ children }: { children: ReactNode }) {
  const slot = useContext(ExamEditorActionsSlot);
  if (slot) return createPortal(children, slot);
  return <div className="flex flex-wrap items-center justify-end gap-2">{children}</div>;
}
