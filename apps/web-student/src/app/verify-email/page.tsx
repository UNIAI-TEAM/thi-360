import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { VerifyEmailView } from "@/features/account/verify-email-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("account.verifyEmail");
  return { title: t("title") };
}

export default function Page() {
  return (
    <Suspense>
      <VerifyEmailView />
    </Suspense>
  );
}
