"use client";

import {
  Button,
  Card,
  cn,
  ConfirmDialog,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  EmptyState,
  LiveStatus,
  notify,
  Skeleton,
  StatusChip,
  type StatusTone,
} from "@thi360/ui";
import { ArrowRightLeft, Eye, History, Info, Lock, Pencil, RotateCcw, TriangleAlert } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useState } from "react";
import { RichTextView } from "@/components/rich-text";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import { MockApiError } from "@/lib/mock/content-mock";
import { QUESTIONS_QUERY_KEYS, toQuestionSummary } from "../api";
import type { QuestionVersion, QuestionVersionList, QuestionVersionState } from "../editor-types";
import { useQuestionVersions, useRestoreQuestionVersion } from "../editor-queries";
import { useQuestion } from "../queries";
import type { QuestionDetail } from "../types";
import { QuestionBodyView } from "./question-detail-body";
import { questionEditHref } from "./question-detail-layout";
import { MarkReadyDialog } from "./question-status-dialogs";
import { QuestionVersionsCompare } from "./question-versions-compare";

const STATE_TONE: Record<QuestionVersionState, StatusTone> = {
  WORKING: "neutral",
  CURRENT: "success",
  SUPERSEDED: "neutral",
  RETIRED: "warning",
};

/**
 * Trang con Lịch sử phiên bản (canvas `CauHoiPhienBan`): danh sách phiên bản (mới trước) với trạng thái, người lưu,
 * số lượt thi đã dùng; so sánh một phiên bản cũ với phiên bản mới nhất; xem lại; khôi phục nội dung một phiên bản
 * đã thay thế thành bản nháp. Phiên bản đã có lượt thi không sửa được (lượt thi giữ phiên bản đã dùng).
 */
export function QuestionVersionsView({ questionId }: { questionId: string }) {
  const t = useTranslations("questions.versions");
  const versionsQuery = useQuestionVersions(questionId);
  const questionQuery = useQuestion(questionId);

  if (versionsQuery.isPending || !questionQuery.data) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true" aria-label={t("loading")}>
        <Skeleton className="h-14" />
        <div className="grid gap-4 md:grid-cols-2">
          <Skeleton className="h-72" />
          <Skeleton className="h-72" />
        </div>
      </div>
    );
  }
  if (versionsQuery.isError) {
    return (
      <Card>
        <EmptyState
          icon={<TriangleAlert />}
          title={t("errorTitle")}
          description={t("errorDescription")}
          action={
            <Button variant="secondary" onClick={() => void versionsQuery.refetch()}>
              {t("retry")}
            </Button>
          }
        />
      </Card>
    );
  }
  if (versionsQuery.data.versions.length === 0) {
    return (
      <Card>
        <EmptyState icon={<History />} title={t("emptyTitle")} description={t("emptyDescription")} />
      </Card>
    );
  }
  return <VersionsContent list={versionsQuery.data} question={questionQuery.data} />;
}

function VersionsContent({ list, question }: { list: QuestionVersionList; question: QuestionDetail }) {
  const t = useTranslations("questions.versions");
  const latest = list.versions[0]!;
  const [compareVersion, setCompareVersion] = useState<number | null>(list.versions[1]?.version ?? null);
  const [viewing, setViewing] = useState<QuestionVersion | null>(null);
  const [restoring, setRestoring] = useState<QuestionVersion | null>(null);
  const [markReadyOpen, setMarkReadyOpen] = useState(false);
  const [liveMessage, setLiveMessage] = useState("");
  const [restoreError, setRestoreError] = useState<string | null>(null);
  const restore = useRestoreQuestionVersion(question.id);
  const queryClient = useQueryClient();
  const compareTarget = list.versions.find((version) => version.version === compareVersion) ?? null;

  const usageNotice =
    list.status === "DRAFT" && list.examCount === 0
      ? t("notice.draft", { version: list.latestVersion })
      : t("notice.used", { exams: list.examCount, attempts: list.attemptCount });

  return (
    <div className="flex flex-col gap-4">
      <p role="note" className="flex items-start gap-2.5 rounded-lg border border-info/30 bg-info-bg px-3.5 py-2.5 text-body text-info">
        <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
        <span>{usageNotice}</span>
      </p>
      <div className="@container">
        <div className="grid items-start gap-4 @4xl:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
          <Card padding="none" className="p-4 sm:p-5">
            <section aria-labelledby="question-versions-title" className="flex flex-col gap-3">
              <h2 id="question-versions-title" className="text-h3 font-semibold text-text">
                {t("listTitle")}
              </h2>
              <ol className="m-0 flex list-none flex-col gap-3 p-0">
                {list.versions.map((version) => (
                  <VersionItem
                    key={version.version}
                    version={version}
                    latestVersion={latest.version}
                    comparing={version.version === compareVersion}
                    questionId={question.id}
                    onCompare={() => {
                      setCompareVersion(version.version);
                      setLiveMessage(t("compareAnnounce", { from: version.version, to: latest.version }));
                    }}
                    onView={() => setViewing(version)}
                    onRestore={() => {
                      setRestoreError(null);
                      setRestoring(version);
                    }}
                    onMarkReady={() => setMarkReadyOpen(true)}
                  />
                ))}
              </ol>
              <p className="text-caption text-text-muted">{t("footnote")}</p>
            </section>
          </Card>
          <QuestionVersionsCompare before={compareTarget} after={latest} />
        </div>
      </div>
      <LiveStatus message={liveMessage} />

      <VersionViewDialog version={viewing} questionType={question.type} onClose={() => setViewing(null)} />

      <ConfirmDialog
        open={restoring !== null}
        onOpenChange={(open) => {
          if (!open) setRestoring(null);
        }}
        tone="primary"
        loading={restore.isPending}
        error={restoreError}
        title={t("restore.title", { version: restoring?.version ?? 0 })}
        description={
          question.status === "DRAFT"
            ? t("restore.descriptionDraft", { version: restoring?.version ?? 0, current: list.latestVersion })
            : t("restore.descriptionNew", {
                version: restoring?.version ?? 0,
                next: list.latestVersion + 1,
                current: list.latestVersion,
              })
        }
        confirmLabel={t("restore.confirm")}
        onConfirm={() => {
          if (!restoring) return;
          restore.mutate(
            { version: restoring.version, expectedRevision: question.revision },
            {
              onSuccess: ({ question: saved }) => {
                setRestoring(null);
                const message = t("restore.done", { version: restoring.version, current: saved.version });
                notify.success(message);
                setLiveMessage(message);
              },
              onError: (error) => {
                setRestoreError(
                  error instanceof MockApiError && error.code === "QUESTION_REVISION_CONFLICT"
                    ? t("restore.conflict")
                    : t("restore.failed"),
                );
              },
            },
          );
        }}
      />

      <MarkReadyDialog
        question={toQuestionSummary(question)}
        open={markReadyOpen}
        onOpenChange={setMarkReadyOpen}
        onDone={() => void queryClient.invalidateQueries({ queryKey: QUESTIONS_QUERY_KEYS.detail(question.id) })}
      />
    </div>
  );
}

function VersionItem({
  version,
  latestVersion,
  comparing,
  questionId,
  onCompare,
  onView,
  onRestore,
  onMarkReady,
}: {
  version: QuestionVersion;
  latestVersion: number;
  comparing: boolean;
  questionId: string;
  onCompare: () => void;
  onView: () => void;
  onRestore: () => void;
  onMarkReady: () => void;
}) {
  const t = useTranslations("questions.versions");
  const format = useFormatter();
  const isLatest = version.version === latestVersion;
  const titleId = `question-version-${version.version}`;
  return (
    <li
      aria-labelledby={titleId}
      className={cn(
        "flex flex-col gap-2 rounded-lg border p-3.5",
        version.state === "WORKING" || version.state === "CURRENT" ? "border-primary-600" : "border-border",
        comparing && "border-l-4 border-l-primary-text",
      )}
    >
      <div className="flex flex-wrap items-center gap-2">
        <h3 id={titleId} className="text-h3 font-bold text-text">
          {t("versionLabel", { version: version.version })}
        </h3>
        <StatusChip tone={STATE_TONE[version.state]}>{t(`state.${version.state}`)}</StatusChip>
        <span className="text-caption font-medium text-text-secondary">{t(`stateNote.${version.state}`)}</span>
        {comparing && (
          <span className="inline-flex items-center gap-1 text-caption font-semibold text-primary-text">
            <ArrowRightLeft aria-hidden className="size-3.5" />
            {t("comparingWith", { version: latestVersion })}
          </span>
        )}
      </div>
      <p className="text-caption text-text-muted">
        {t("meta", {
          name: version.savedBy.name,
          at: format.dateTime(new Date(version.savedAt), "dateTime"),
        })}
        {" · "}
        {version.attemptCount > 0 ? t("attempts", { count: version.attemptCount }) : t("noAttempts")}
      </p>
      {version.state === "SUPERSEDED" && version.attemptCount > 0 && (
        <p className="inline-flex items-center gap-1.5 text-caption text-text-secondary">
          <Lock aria-hidden className="size-3.5" />
          {t("locked")}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        {version.state === "WORKING" ? (
          <>
            <Button asChild size="sm" variant="outline">
              <Link href={questionEditHref(questionId)}>
                <Pencil aria-hidden />
                {t("actions.continueEditing")}
              </Link>
            </Button>
            <Button size="sm" onClick={onMarkReady}>
              {t("actions.markReady")}
            </Button>
          </>
        ) : (
          <Button size="sm" variant="secondary" onClick={onView}>
            <Eye aria-hidden />
            {t("actions.view", { version: version.version })}
          </Button>
        )}
        {!isLatest && !comparing && (
          <Button size="sm" variant="secondary" onClick={onCompare}>
            <ArrowRightLeft aria-hidden />
            {t("actions.compare", { version: version.version, latest: latestVersion })}
          </Button>
        )}
        {version.state === "SUPERSEDED" && (
          <Button size="sm" variant="secondary" onClick={onRestore}>
            <RotateCcw aria-hidden />
            {t("actions.restore", { version: version.version })}
          </Button>
        )}
      </div>
    </li>
  );
}

function VersionViewDialog({
  version,
  questionType,
  onClose,
}: {
  version: QuestionVersion | null;
  questionType: QuestionDetail["type"];
  onClose: () => void;
}) {
  const t = useTranslations("questions.versions");
  const referenceQuery = useContentReference();
  const lookup = useReferenceLookup(referenceQuery.data);
  return (
    <Dialog open={version !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent size="lg" closeLabel={t("view.close")} className="flex max-h-[85vh] flex-col overflow-hidden">
        {version && (
          <>
            <DialogHeader className="shrink-0">
              <DialogTitle>{t("view.title", { version: version.version })}</DialogTitle>
              <DialogDescription>
                {[
                  lookup.subject(version.snapshot.subjectCode),
                  lookup.grade(version.snapshot.gradeCode),
                  lookup.difficulty(version.snapshot.difficultyCode),
                  t("view.points", { points: version.snapshot.points }),
                ].join(" · ")}
              </DialogDescription>
            </DialogHeader>
            <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto pr-1">
              <QuestionBodyView question={{ ...version.snapshot, type: questionType }} />
              {questionType !== "QUESTION_SET" && (
                <div className="flex flex-col gap-1">
                  <h3 className="text-label font-semibold text-text">{t("view.solution")}</h3>
                  {version.snapshot.solution ? (
                    <RichTextView className="text-body text-text" html={version.snapshot.solution} />
                  ) : (
                    <p className="text-body text-text">{t("view.noSolution")}</p>
                  )}
                </div>
              )}
            </div>
            <DialogFooter className="shrink-0">
              <Button variant="secondary" onClick={onClose}>
                {t("view.close")}
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
