import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { ForgotPasswordView } from "@/features/account/forgot-password-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("account.forgotPassword");
  return { title: t("title") };
}

export default function Page() {
  return (
    <Suspense>
      <ForgotPasswordView />
    </Suspense>
  );
}
