import { cn } from "@thi360/ui";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { AuthBackdrop } from "@/features/auth/auth-backdrop";
import { PublicStudentHeader } from "./public-student-header";

function PublicFooter({ variant = "default" }: { variant?: "default" | "landing" }) {
  const common = useTranslations("common");
  const t = useTranslations("footer");
  const isLanding = variant === "landing";
  return (
    <footer className="bg-primary-fill text-on-primary">
      <div
        className={cn(
          "mx-auto flex flex-wrap items-center justify-between gap-2 px-4 py-5",
          isLanding ? "w-full sm:w-[90%] sm:max-w-[90%] sm:px-0" : "max-w-[1170px] sm:px-6",
        )}
      >
        <span className="font-semibold">{common("appName")}</span>
        <span>{t("note")}</span>
      </div>
    </footer>
  );
}

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
