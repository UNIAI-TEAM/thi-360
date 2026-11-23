"use client";

import {
  ConfirmDialog,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  IconButton,
  notify,
} from "@thi360/ui";
import { ClipboardCheck, Copy, History, Info, MoreHorizontal, Pencil, Ban } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { getErrorStatus } from "@/lib/api/query-client";
import { examTabHref } from "../editor-tabs";
import { useDiscontinueExam, useDuplicateExam } from "../queries";
import { useIdempotencyKey } from "../review-queries";
import type { ExamSummary } from "../types";

/**
 * Thao tác của một dòng (cột Thao tác và thẻ ở khung hẹp): Sửa (mở Cấu trúc và ma trận), Sao chép, menu "⋯" (Thông tin
 * chung, Kiểm tra và xem trước, Phiên bản và công bố, Ngừng bán). `onAnnounce` báo kết quả qua LiveStatus của danh sách.
 */
export function ExamListRowActions({ exam, onAnnounce }: { exam: ExamSummary; onAnnounce: (message: string) => void }) {
  const t = useTranslations("exams.list");
  const router = useRouter();
  const duplicate = useDuplicateExam();
  const discontinue = useDiscontinueExam();
  const idempotency = useIdempotencyKey();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [discontinueError, setDiscontinueError] = useState<string | null>(null);

  const runDuplicate = () => {
    duplicate.mutate(
      { id: exam.id, name: t("copyName", { name: exam.name }) },
      {
        onSuccess: (created) => {
          const message = t("duplicateSuccess", { code: created.code });
          notify.success(message);
          onAnnounce(message);
        },
        onError: () => notify.error(t("duplicateError")),
      },
    );
  };

  const runDiscontinue = () => {
    setDiscontinueError(null);
    // Một khóa cho mỗi lần ngừng bán của bài này: bấm lại sau lỗi mạng dùng lại khóa; xong thì thả khóa.
    discontinue.mutate({ examId: exam.id, expectedRevision: exam.revision, idempotencyKey: idempotency.keyFor(`${exam.id}:${exam.revision}`) }, {
      onSuccess: () => {
        idempotency.release();
        setConfirmOpen(false);
        const message = t("discontinueSuccess", { code: exam.code });
        notify.success(message);
        onAnnounce(message);
      },
      onError: (error) => {
        const status = getErrorStatus(error);
        setDiscontinueError(status === 409 || status === 412 ? t("discontinueConflict") : t("discontinueError"));
      },
    });
  };

  return (
    <>
      <IconButton
        icon={<Pencil />}
        aria-label={t("actions.editAria", { code: exam.code })}
        tooltip={t("actions.edit")}
        onClick={() => router.push(examTabHref(exam.id, "structure"))}
      />
      <IconButton
        icon={<Copy />}
        aria-label={t("actions.duplicateAria", { code: exam.code })}
        tooltip={t("actions.duplicate")}
        aria-busy={duplicate.isPending || undefined}
        disabled={duplicate.isPending}
        onClick={runDuplicate}
      />
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <IconButton icon={<MoreHorizontal />} aria-label={t("actions.moreAria", { code: exam.code })} />
        </DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem onSelect={() => router.push(examTabHref(exam.id, "general"))}>
            <Info />
            <span>{t("actions.general")}</span>
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => router.push(examTabHref(exam.id, "review"))}>
            <ClipboardCheck />
            <span>{t("actions.review")}</span>
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => router.push(examTabHref(exam.id, "versions"))}>
            <History />
            <span>{t("actions.versions")}</span>
          </DropdownMenuItem>
          {exam.status === "PUBLISHED" && (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuItem destructive onSelect={() => setConfirmOpen(true)}>
                <Ban />
                <span>{t("actions.discontinue")}</span>
              </DropdownMenuItem>
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={(open) => {
          setConfirmOpen(open);
          if (!open) setDiscontinueError(null);
        }}
        title={t("discontinueTitle", { code: exam.code })}
        description={t("discontinueDescription", { name: exam.name, version: exam.servingVersion ?? 0 })}
        confirmLabel={t("actions.discontinue")}
        cancelLabel={t("cancel")}
        closeLabel={t("cancel")}
        tone="danger"
        loading={discontinue.isPending}
        error={discontinueError ?? undefined}
        onConfirm={runDiscontinue}
      />
    </>
  );
}
