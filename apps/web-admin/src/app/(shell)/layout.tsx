import { getTranslations } from "next-intl/server";
import type { ReactNode } from "react";
import { AppShell } from "@/features/shell/app-shell";

export default async function ShellLayout({ children }: { children: ReactNode }) {
  const t = await getTranslations("shell");
  return <AppShell skipLabel={t("skipToContent")}>{children}</AppShell>;
}
