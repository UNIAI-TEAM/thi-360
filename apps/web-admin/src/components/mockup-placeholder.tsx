import { Card, EmptyState } from "@thi360/ui";
import { Construction } from "lucide-react";
import { useTranslations } from "next-intl";

/** Chỗ giữ của màn đang dựng trong đợt mockup; lane phụ trách thay bằng màn thật. */
export function MockupPlaceholder({ screen }: { screen: string }) {
  const t = useTranslations("mockData");
  return (
    <Card>
      <EmptyState icon={<Construction />} title={t("placeholderTitle")} description={t("placeholderDescription", { screen })} />
    </Card>
  );
}
