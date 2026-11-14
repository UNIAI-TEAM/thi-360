import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { UserDetailView } from "@/features/users/components/user-detail-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("users.detail");
  return { title: `${t("title")} · Thi 360` };
}

export default async function UserDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <UserDetailView userId={id} />;
}
