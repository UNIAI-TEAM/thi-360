import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { ProfileView } from "@/features/account/profile-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("account.profile");
  return { title: t("title") };
}

export default function Page() {
  return (
    <Suspense>
      <ProfileView />
    </Suspense>
  );
}
