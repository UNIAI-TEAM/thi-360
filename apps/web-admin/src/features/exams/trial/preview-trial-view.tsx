"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { Button, Card, ConfirmDialog, EmptyState, LiveStatus, Skeleton } from "@thi360/ui";
import type { AnswerValue as KitAnswerValue } from "@thi360/ui/question";
import { ArrowLeft, Clock, TriangleAlert, WifiOff } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
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
  useSetBreadcrumbLeaf(t("title"));
  const [announcement, setAnnouncement] = useState("");

  return (
    <>
      <LiveStatus message={announcement} />
      <PreviewTrialSession
        key={`${examId}:${previewId}`}
        examId={examId}
        previewId={previewId}
        initialExpiresAt={initialExpiresAt}
        deps={deps}
        announce={setAnnouncement}
      />
    </>
  );
}

function PreviewTrialSession({
  examId,
  previewId,
  initialExpiresAt,
  deps,
  announce,
}: {
  examId: string;
  previewId: string;
  initialExpiresAt: string | null;
  deps: PreviewTrialApiDeps;
  announce: (message: string) => void;
}) {
  const t = useTranslations("exams.trial");
  const router = useRouter();

  const [answers, setAnswers] = useState<Record<string, KitAnswerValue>>({});
  const [gradeResult, setGradeResult] = useState<PreviewGradeResponse | null>(null);
  const [expiredOnSubmit, setExpiredOnSubmit] = useState(false);
  const [submitErrorMsg, setSubmitErrorMsg] = useState<string | null>(null);
  const [pendingLeaveHref, setPendingLeaveHref] = useState<string | null>(null);
  const [focusResultHeading, setFocusResultHeading] = useState(false);
  const [retakeFocusCount, setRetakeFocusCount] = useState(0);
  const leaveBypassRef = useRef(false);

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
      setFocusResultHeading(true);
      setGradeResult(result);
      announce(t("live.graded"));
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
    if (!isInProgress) return;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (leaveBypassRef.current) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [isInProgress]);

  useEffect(() => {
    if (!isInProgress) return;
    const handleClick = (event: MouseEvent) => {
      if (leaveBypassRef.current || event.defaultPrevented || event.button !== 0) return;
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const target = event.target;
      if (!(target instanceof Element)) return;
      const anchor = target.closest("a[href]");
      if (!(anchor instanceof HTMLAnchorElement)) return;
      if (anchor.hasAttribute("download") || anchor.target === "_blank") return;
      const raw = anchor.getAttribute("href");
      if (!raw || raw.startsWith("#")) return;
      let url: URL;
      try {
        url = new URL(anchor.href, window.location.href);
      } catch {
        return;
      }
      if (url.origin !== window.location.origin) return;
      const next = `${url.pathname}${url.search}${url.hash}`;
      if (url.pathname === window.location.pathname && url.search === window.location.search) return;
      event.preventDefault();
      event.stopPropagation();
      setPendingLeaveHref(next);
    };
    document.addEventListener("click", handleClick, true);
    return () => document.removeEventListener("click", handleClick, true);
  }, [isInProgress]);

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

  const isOffline = paperQuery.fetchStatus === "paused" && paperQuery.isPending;

  if (isOffline) {
    return (
      <Card>
        <EmptyState
          headingLevel={1}
          icon={<WifiOff />}
          title={t("offline.title")}
          description={t("offline.description")}
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
        focusHeadingOnMount={focusResultHeading}
        onRetake={() => {
          setGradeResult(null);
          setAnswers({});
          setSubmitErrorMsg(null);
          setFocusResultHeading(false);
          setRetakeFocusCount((count) => count + 1);
          announce(t("live.retake"));
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
        onRequestLeave={() => setPendingLeaveHref(backHref)}
        submitting={gradeMutation.isPending}
        submitError={submitErrorMsg}
        initialExpiresAt={initialExpiresAt}
        focusHeadingKey={retakeFocusCount}
      />

      <ConfirmDialog
        open={pendingLeaveHref !== null}
        onOpenChange={(open) => {
          if (!open) setPendingLeaveHref(null);
        }}
        tone="danger"
        title={t("leaveDialog.title")}
        description={t("leaveDialog.description")}
        confirmLabel={t("leaveDialog.confirm")}
        cancelLabel={t("leaveDialog.cancel")}
        onConfirm={() => {
          if (!pendingLeaveHref) return;
          leaveBypassRef.current = true;
          const href = pendingLeaveHref;
          setPendingLeaveHref(null);
          router.push(href);
        }}
      />
    </>
  );
}
