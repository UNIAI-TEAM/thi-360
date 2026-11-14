"use client";

import { useMutation } from "@tanstack/react-query";
import { Button, notify, PageHeader } from "@thi360/ui";
import { Download } from "lucide-react";
import { useTranslations } from "next-intl";
import type { BulkImportApiDeps } from "../api";
import { resolveImportApiErrorMessage } from "../utils";

export interface BulkImportHeaderProps {
  deps: BulkImportApiDeps;
}

export function BulkImportHeader({ deps }: BulkImportHeaderProps) {
  const t = useTranslations("bulkImport");

  const downloadTemplateMutation = useMutation({
    mutationFn: () => deps.downloadImportTemplate(),
    onSuccess: ({ blob, filename }) => {
      deps.saveBlobToDisk(blob, filename);
    },
    onError: (error) => {
      notify.error(
        resolveImportApiErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("downloadTemplateError"),
        ),
      );
    },
  });

  return (
    <PageHeader
      title={t("title")}
      description={t("description")}
      actions={
        <Button
          variant="secondary"
          loading={downloadTemplateMutation.isPending}
          onClick={() => downloadTemplateMutation.mutate()}
        >
          <Download />
          <span>{t("downloadTemplate")}</span>
        </Button>
      }
    />
  );
}
