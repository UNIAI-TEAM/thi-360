import { cn } from "@thi360/ui";
import type { ReactNode } from "react";
import { AuthBackdrop } from "@/features/auth/auth-backdrop";
import { PublicFooter } from "./public-footer";
import { PublicStudentHeader } from "./public-student-header";

/** Khung chung của trang công khai (`/`, `/exams/<code>`): thanh đầu trang theo phiên tùy chọn, một `<main>`, chân trang. */
export function PublicShell({
  children,
  mainClassName,
  variant = "default",
}: {
  children: ReactNode;
  mainClassName?: string;
  variant?: "default" | "landing";
}) {
  const isLanding = variant === "landing";
  return (
    <div className={cn("flex min-h-screen flex-col text-text", isLanding ? "relative isolate" : "bg-page")}>
      {isLanding && <AuthBackdrop className="fixed" />}
      <PublicStudentHeader variant={variant} />
      <main className={cn("flex flex-1 flex-col", mainClassName)}>{children}</main>
      <PublicFooter variant={variant} />
    </div>
  );
}
