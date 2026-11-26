import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { BulkImportUploadView } from "@/features/bulk-import/components/bulk-import-upload-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("bulkImport");
  return { title: `${t("title")} · Thi360` };
}

export default function BulkImportPage() {
  return <BulkImportUploadView />;
}
