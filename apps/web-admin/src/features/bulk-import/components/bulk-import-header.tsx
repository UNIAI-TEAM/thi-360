"use client";

import { useMutation } from "@tanstack/react-query";
import { Button, DetailHeader, notify } from "@thi360/ui";
import { Download } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useSetBreadcrumbLeaf } from "@/features/shell/breadcrumb-leaf";
import type { BulkImportApiDeps } from "../api";
import { resolveImportApiErrorMessage } from "../utils";

export interface BulkImportHeaderProps {
  deps: BulkImportApiDeps;
  /**
   * Tiêu đề riêng của trang (trang đợt cấp dùng tên tệp đã tải lên). Bỏ trống thì dùng tiêu đề chung
   * "Cấp tài khoản hàng loạt".
   */
  title?: string | null;
}

/**
 * Đầu trang của bước tải lên (`/users/bulk-import`) và trang đợt cấp (`/users/bulk-import/[id]`): cả hai mở từ danh
 * sách tài khoản nên lối quay lại về `/users` (DESIGN.md mục 5.8, `UNI-2825`).
 */
export function BulkImportHeader({ deps, title }: BulkImportHeaderProps) {
  const t = useTranslations("bulkImport");
  const tCommon = useTranslations("common");
  useSetBreadcrumbLeaf(t("breadcrumb"));

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
    <DetailHeader
      back={{ href: "/users", label: tCommon("backTo.users") }}
      linkComponent={Link}
      title={title || t("title")}
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
