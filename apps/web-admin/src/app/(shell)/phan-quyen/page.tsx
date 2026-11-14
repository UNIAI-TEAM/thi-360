import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { Skeleton } from "@thi360/ui";
import { RbacPageView } from "@/features/rbac";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("rbac");
  return { title: `${t("title")} · Thi 360` };
}

export default function RbacPage() {
  return (
    <Suspense
      fallback={
        <div className="flex flex-col gap-4">
          <Skeleton className="h-8 w-56" />
          <Skeleton className="h-72" />
        </div>
      }
    >
      <RbacPageView />
    </Suspense>
  );
}
