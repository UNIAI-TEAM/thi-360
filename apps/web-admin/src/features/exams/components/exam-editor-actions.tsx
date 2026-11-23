"use client";

import { createContext, useContext, type ReactNode } from "react";
import { createPortal } from "react-dom";

export const ExamEditorActionsSlot = createContext<HTMLElement | null | undefined>(undefined);

export function ExamEditorActions({ children }: { children: ReactNode }) {
  const slot = useContext(ExamEditorActionsSlot);
  if (slot) return createPortal(children, slot);
  // Có khung mà chỗ đặt chưa gắn: chưa vẽ gì, nếu không hàng nút nháy một khung dưới nội dung rồi nhảy lên hàng tiêu đề.
  if (slot === null) return null;
  return <div className="flex flex-wrap items-center justify-end gap-2">{children}</div>;
}
