import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { DeleteAccountView } from "@/features/account/delete-account-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("account.deletion");
  return { title: t("title") };
}

export default function Page() {
  return (
    <Suspense>
      <DeleteAccountView />
    </Suspense>
  );
}
