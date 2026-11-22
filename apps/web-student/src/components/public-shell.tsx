import { cn } from "@thi360/ui";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { PublicStudentHeader } from "./public-student-header";

function PublicFooter() {
  const common = useTranslations("common");
  const t = useTranslations("footer");
  return (
    <footer className="bg-primary-fill text-on-primary">
      <div className="mx-auto flex max-w-[1170px] flex-wrap items-center justify-between gap-2 px-4 py-5 sm:px-6">
        <span className="font-semibold">{common("appName")}</span>
        <span>{t("note")}</span>
      </div>
    </footer>
  );
}

/** Khung chung của trang công khai (`/`, `/exams/<code>`): thanh đầu trang theo phiên tùy chọn, một `<main>`, chân trang. */
export function PublicShell({ children, mainClassName }: { children: ReactNode; mainClassName?: string }) {
  return (
    <div className="flex min-h-screen flex-col bg-page text-text">
      <PublicStudentHeader />
      <main className={cn("flex flex-1 flex-col", mainClassName)}>{children}</main>
      <PublicFooter />
    </div>
  );
}
