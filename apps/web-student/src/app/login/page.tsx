import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { LoginView } from "@/features/auth/login-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("login");
  const common = await getTranslations("common");
  return { title: `${t("title")} · ${common("appName")}` };
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginView />
    </Suspense>
  );
}
