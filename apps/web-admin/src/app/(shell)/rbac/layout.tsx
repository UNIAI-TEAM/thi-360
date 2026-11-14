import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense, type ReactNode } from "react";
import { Skeleton } from "@thi360/ui";
import { RbacLayoutView } from "@/features/rbac";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("rbac");
  return { title: `${t("title")} · Thi 360` };
}

/** Tiêu đề và thanh tab dùng chung cho `/rbac/roles`, `/permissions`, `/users`, `/bulk-assign`. */
export default function RbacLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense
      fallback={
        <div className="flex flex-col gap-4">
          <Skeleton className="h-8 w-56" />
          <Skeleton className="h-72" />
        </div>
      }
    >
      <RbacLayoutView>{children}</RbacLayoutView>
    </Suspense>
  );
}
