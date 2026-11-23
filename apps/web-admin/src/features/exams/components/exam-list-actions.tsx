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
import { Ban, ClipboardCheck, Copy, Eye, History, Info, Layers, MoreHorizontal, Pencil, Tag } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { usePermissions } from "@/features/session/session-provider";
import { getErrorStatus } from "@/lib/api/query-client";
import { examTabHref } from "../editor-tabs";
import { useDiscontinueExam, useDuplicateExam } from "../queries";
import { useIdempotencyKey } from "../review-queries";
import type { ExamSummary } from "../types";

export function ExamListRowActions({ exam, onAnnounce }: { exam: ExamSummary; onAnnounce: (message: string) => void }) {
  const t = useTranslations("exams.list");
  const router = useRouter();
  const permissions = usePermissions();
  const canModify = !permissions.isLoading && permissions.can("EXAM.MODIFY");
  const canPublish = !permissions.isLoading && permissions.can("EXAM.PUBLISH");
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
    discontinue.mutate(
      { examId: exam.id, expectedRevision: exam.revision, idempotencyKey: idempotency.keyFor(`${exam.id}:${exam.revision}`) },
      {
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
      },
    );
  };

  return (
    <>
      <IconButton
        icon={canModify ? <Pencil /> : <Eye />}
        aria-label={t(canModify ? "actions.editAria" : "actions.viewAria", { code: exam.code })}
        tooltip={t(canModify ? "actions.edit" : "actions.view")}
        onClick={() => router.push(examTabHref(exam.id, "general"))}
      />
      {canModify && (
        <IconButton
          icon={<Copy />}
          aria-label={t("actions.duplicateAria", { code: exam.code })}
          tooltip={t("actions.duplicate")}
          aria-busy={duplicate.isPending || undefined}
          disabled={duplicate.isPending}
          onClick={runDuplicate}
        />
      )}
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <IconButton icon={<MoreHorizontal />} aria-label={t("actions.moreAria", { code: exam.code })} />
        </DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem onSelect={() => router.push(examTabHref(exam.id, "general"))}>
            <Info />
            <span>{t("actions.general")}</span>
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => router.push(examTabHref(exam.id, "structure"))}>
            <Layers />
            <span>{t("actions.structure")}</span>
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => router.push(examTabHref(exam.id, "listing"))}>
            <Tag />
            <span>{t("actions.listing")}</span>
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => router.push(examTabHref(exam.id, "review"))}>
            <ClipboardCheck />
            <span>{t("actions.review")}</span>
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => router.push(examTabHref(exam.id, "versions"))}>
            <History />
            <span>{t("actions.versions")}</span>
          </DropdownMenuItem>
          {exam.status === "PUBLISHED" && canPublish && (
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
