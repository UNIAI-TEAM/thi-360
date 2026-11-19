import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { RegisterView } from "@/features/account/register-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("account.register");
  return { title: t("title") };
}

export default function Page() {
  return (
    <Suspense>
      <RegisterView />
    </Suspense>
  );
}
