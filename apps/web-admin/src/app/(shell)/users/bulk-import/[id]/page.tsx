import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { BulkImportBatchView } from "@/features/bulk-import/components/bulk-import-batch-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("bulkImport");
  return { title: `${t("title")} · Thi 360` };
}

export default async function BulkImportBatchPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <BulkImportBatchView batchId={id} />;
}
