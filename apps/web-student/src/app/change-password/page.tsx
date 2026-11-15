import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { ChangePasswordView } from "@/features/auth/change-password-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("changePassword");
  return { title: t("title") };
}

export default function ChangePasswordPage() {
  return (
    <Suspense>
      <ChangePasswordView />
    </Suspense>
  );
}
