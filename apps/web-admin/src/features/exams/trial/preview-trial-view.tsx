"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { Button, Card, ConfirmDialog, EmptyState, Skeleton } from "@thi360/ui";
import type { AnswerValue as KitAnswerValue } from "@thi360/ui/question";
import { ArrowLeft, Clock, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useSetBreadcrumbLeaf } from "@/features/shell/breadcrumb-leaf";
import { getErrorStatus } from "@/lib/api/query-client";
import { errorCodeOf } from "../review-api";
import {
  defaultPreviewTrialApiDeps,
  PREVIEW_TRIAL_QUERY_KEYS,
  reviewPreviewHref,
  type PreviewTrialApiDeps,
} from "./api";
import { toApiTrialAnswers } from "./mappers";
import { TrialPaperView } from "./trial-paper-view";
import { TrialResultView } from "./trial-result-view";
import type { PreviewGradeResponse } from "./types";

export interface PreviewTrialViewProps {
  examId: string;
  previewId: string;
  initialExpiresAt?: string | null;
  deps?: PreviewTrialApiDeps;
}

export function PreviewTrialView({
  examId,
  previewId,
  initialExpiresAt = null,
  deps = defaultPreviewTrialApiDeps,
}: PreviewTrialViewProps) {
  const t = useTranslations("exams.trial");
  const router = useRouter();
  useSetBreadcrumbLeaf(t("title"));

  const [answers, setAnswers] = useState<Record<string, KitAnswerValue>>({});
  const [gradeResult, setGradeResult] = useState<PreviewGradeResponse | null>(null);
  const [expiredOnSubmit, setExpiredOnSubmit] = useState(false);
  const [submitErrorMsg, setSubmitErrorMsg] = useState<string | null>(null);
  const [leaveDialogOpen, setLeaveDialogOpen] = useState(false);
  const [leaveBypass, setLeaveBypass] = useState(false);

  const paperQuery = useQuery({
    queryKey: PREVIEW_TRIAL_QUERY_KEYS.paper(examId, previewId),
    queryFn: ({ signal }) => deps.getPreviewTrialPaper({ examId, previewId, signal }),
    retry: false,
  });

  const gradeMutation = useMutation({
    mutationFn: async () => {
      if (!paperQuery.data) {
        throw new Error("Trial paper is not loaded");
      }
      const apiAnswers = toApiTrialAnswers(paperQuery.data, answers);
      return deps.gradePreviewTrial({ examId, previewId, answers: apiAnswers });
    },
    onSuccess: (result) => {
      setSubmitErrorMsg(null);
      setExpiredOnSubmit(false);
      setGradeResult(result);
    },
    onError: (error) => {
      if (getErrorStatus(error) === 410 || errorCodeOf(error) === "PREVIEW_EXPIRED") {
        setExpiredOnSubmit(true);
        setSubmitErrorMsg(null);
      } else {
        setSubmitErrorMsg(t("submitError"));
      }
    },
  });

  const isInProgress = Boolean(paperQuery.data) && gradeResult === null && !expiredOnSubmit;

  useEffect(() => {
    if (!isInProgress || leaveBypass) return;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [isInProgress, leaveBypass]);

  const backHref = reviewPreviewHref(examId);

  const isExpiredOnLoad =
    paperQuery.isError &&
    (getErrorStatus(paperQuery.error) === 410 || errorCodeOf(paperQuery.error) === "PREVIEW_EXPIRED");

  if (isExpiredOnLoad || expiredOnSubmit) {
    return (
      <Card>
        <div role="alert">
          <EmptyState
            headingLevel={1}
            icon={<Clock />}
            title={t("expired.title")}
            description={t("expired.description")}
            action={
              <Button asChild variant="primary">
                <Link href={backHref}>
                  <ArrowLeft aria-hidden />
                  {t("expired.backAction")}
                </Link>
              </Button>
            }
          />
        </div>
      </Card>
    );
  }

  if (paperQuery.isPending) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true" aria-label={t("loading")}>
        <Skeleton className="h-16" />
        <Skeleton className="h-96" />
      </div>
    );
  }

  if (paperQuery.isError || !paperQuery.data) {
    return (
      <Card>
        <div role="alert">
          <EmptyState
            headingLevel={1}
            icon={<TriangleAlert />}
            title={t("error.title")}
            description={t("error.description")}
            action={
              <div className="flex flex-wrap items-center justify-center gap-2">
                <Button type="button" variant="secondary" onClick={() => void paperQuery.refetch()}>
                  {t("error.retry")}
                </Button>
                <Button asChild variant="primary">
                  <Link href={backHref}>
                    <ArrowLeft aria-hidden />
                    {t("backToPreview")}
                  </Link>
                </Button>
              </div>
            }
          />
        </div>
      </Card>
    );
  }

  if (gradeResult) {
    return (
      <TrialResultView
        examId={examId}
        paper={paperQuery.data}
        grade={gradeResult}
        answers={answers}
        onRetake={() => {
          setGradeResult(null);
          setAnswers({});
          setSubmitErrorMsg(null);
        }}
      />
    );
  }

  return (
    <>
      <TrialPaperView
        paper={paperQuery.data}
        answers={answers}
        onAnswerChange={(questionId, value) => {
          setAnswers((prev) => ({ ...prev, [questionId]: value }));
          if (submitErrorMsg) setSubmitErrorMsg(null);
        }}
        onSubmit={() => {
          if (gradeMutation.isPending) return;
          void gradeMutation.mutateAsync().catch(() => {});
        }}
        onRequestLeave={() => setLeaveDialogOpen(true)}
        submitting={gradeMutation.isPending}
        submitError={submitErrorMsg}
        initialExpiresAt={initialExpiresAt}
      />

      <ConfirmDialog
        open={leaveDialogOpen}
        onOpenChange={setLeaveDialogOpen}
        tone="danger"
        title={t("leaveDialog.title")}
        description={t("leaveDialog.description")}
        confirmLabel={t("leaveDialog.confirm")}
        cancelLabel={t("leaveDialog.cancel")}
        onConfirm={() => {
          setLeaveBypass(true);
          setLeaveDialogOpen(false);
          router.push(backHref);
        }}
      />
    </>
  );
}
