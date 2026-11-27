"use client";

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  notify,
  Skeleton,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@thi360/ui";
import { ArrowLeft, ArrowUp, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { usePathname, useSearchParams } from "next/navigation";
import { useCallback, useState } from "react";
import { useOptionalPermissions } from "@/features/session/session-provider";
import { collectCheckIssues, splitBySeverity } from "../check";
import { examTabHref, RECHECK_PARAM } from "../editor-tabs";
import { useExam } from "../queries";
import { useExamCheck, useExamVersions } from "../review-queries";
import { examNeedsPathChoice } from "../review-scope";
import type { ExamDetail } from "../types";
import { EditorStepToolbar } from "./editor-step-toolbar";
import { ExamPreviewPanel } from "./exam-preview-panel";
import { ExamReviewCheck } from "./exam-review-check";
import { listingErrorCount } from "./exam-review-listing";
import { PublishExamDialog } from "./publish-exam-dialog";

const REVIEW_TABS = ["check", "preview"] as const;
type ReviewTab = (typeof REVIEW_TABS)[number];

interface ReviewUrlState {
  tab: ReviewTab;
  path: string | null;
  version: number | null;
}

/** Ghi trạng thái trang vào URL tại chỗ (DESIGN.md 5.5: tab nội dung ghi `?tab=`), giữ các tham số khác. */
function writeUrl(pathname: string, state: ReviewUrlState): void {
  if (typeof window === "undefined") return;
  const params = new URLSearchParams(window.location.search);
  const set = (key: string, value: string | null) => (value === null ? params.delete(key) : params.set(key, value));
  set("tab", state.tab === "check" ? null : state.tab);
  set("path", state.tab === "preview" ? state.path : null);
  set("version", state.tab === "preview" && state.version !== null ? String(state.version) : null);
  const query = params.toString();
  window.history.replaceState(null, "", query ? `${pathname}?${query}` : pathname);
}

function clearRecheckFromUrl(pathname: string): void {
  if (typeof window === "undefined") return;
  const params = new URLSearchParams(window.location.search);
  if (!params.has(RECHECK_PARAM)) return;
  params.delete(RECHECK_PARAM);
  const query = params.toString();
  window.history.replaceState(null, "", query ? `${pathname}?${query}` : pathname);
}

function readVersion(value: string | null): number | null {
  const parsed = Number(value);
  return value && Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

export function ExamReviewView({ examId }: { examId: string }) {
  const t = useTranslations("exams.review");
  const tVersions = useTranslations("exams.versions");
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const permissions = useOptionalPermissions();
  const examQuery = useExam(examId);
  const checkQuery = useExamCheck(examId);
  const [publishOpen, setPublishOpen] = useState(false);
  const [checkBusy, setCheckBusy] = useState(false);
  const [autoRecheck, setAutoRecheck] = useState(() => searchParams.get(RECHECK_PARAM) === "1");
  const [state, setState] = useState<ReviewUrlState>(() => {
    const tab = searchParams.get("tab");
    return {
      tab: (REVIEW_TABS as readonly string[]).includes(tab ?? "") ? (tab as ReviewTab) : "check",
      path: searchParams.get("path"),
      version: readVersion(searchParams.get("version")),
    };
  });
  // Mỗi lần mở xem trước từ bảng kiểm tra là một lần sinh mới: đổi `key` để khung xem trước dựng lại và tự sinh.
  const [previewRequest, setPreviewRequest] = useState(0);

  function update(next: ReviewUrlState) {
    setState(next);
    writeUrl(pathname, next);
  }

  const handleAutoRecheckStarted = useCallback(() => {
    setAutoRecheck(false);
    clearRecheckFromUrl(pathname);
  }, [pathname]);

  // Khung soạn bài thi (`ExamEditorLayout`) đã lo trạng thái tải và lỗi của bài thi.
  if (!examQuery.data) return <Skeleton className="h-96" />;
  const exam = examQuery.data;

  const canPublishPermission = !permissions.isLoading && permissions.can("EXAM.PUBLISH");
  const checkData = checkQuery.data ?? null;
  const checkIssues = checkData ? collectCheckIssues(checkData) : [];
  const { errors, unconfirmed } = splitBySeverity(checkIssues);
  const listingErrors = checkData ? listingErrorCount(checkData.listingCheck) : 0;
  const listingUnchecked = !checkData || checkData.listingCheck == null;
  const stale = !checkData || checkData.stale || exam.check.status === "NEEDS_RECHECK";
  const allPathsValid = Boolean(checkData && checkData.paths.length > 0 && checkData.paths.every((path) => path.result === "VALID"));
  const canPublishNow =
    canPublishPermission &&
    exam.status !== "DISCONTINUED" &&
    !checkBusy &&
    !stale &&
    errors.length === 0 &&
    unconfirmed.length === 0 &&
    listingErrors === 0 &&
    !listingUnchecked &&
    allPathsValid;

  const openPreview = (pathId: string | null) => {
    update({ tab: "preview", path: pathId ?? state.path, version: null });
    if (pathId || (!examNeedsPathChoice(exam.parts) && previewRequest === 0)) {
      setPreviewRequest((value) => value + 1);
    }
  };

  return (
    <>
      <Tabs value={state.tab} onValueChange={(value) => update({ ...state, tab: value as ReviewTab })}>
        <EditorStepToolbar
          testId="review-toolbar"
          // Đệm ngang hẹp ở màn nhỏ: hai tab tiếng Anh (~325px) phải vừa một hàng ở 390px mà không cuộn ngang.
          className="px-2 py-2 sm:px-5"
          start={
            <TabsList aria-label={t("tabs.label")}>
              <TabsTrigger value="check">{t("tabs.check")}</TabsTrigger>
              <TabsTrigger value="preview">{t("tabs.preview")}</TabsTrigger>
            </TabsList>
          }
        >
          <Button asChild variant="secondary" size="sm">
            <Link href={examTabHref(exam.id, "listing")}>
              <ArrowLeft aria-hidden />
              {t("backToListing")}
            </Link>
          </Button>
          {canPublishPermission && exam.status !== "DISCONTINUED" && (
            <Button type="button" size="sm" disabled={!canPublishNow} onClick={() => setPublishOpen(true)}>
              <ArrowUp aria-hidden />
              {exam.servingVersion !== null ? t("publishUpdate") : t("publishFirst")}
            </Button>
          )}
        </EditorStepToolbar>
        {/* Giữ cả hai tab trong DOM để đề vừa sinh không mất khi xem lại kết quả kiểm tra. */}
        <TabsContent value="check" forceMount className="mt-5 data-[state=inactive]:hidden">
          <ExamReviewCheck
            exam={exam}
            active={state.tab === "check"}
            autoRecheck={autoRecheck}
            onAutoRecheckStarted={handleAutoRecheckStarted}
            onBusyChange={setCheckBusy}
            onPreview={openPreview}
          />
        </TabsContent>
        <TabsContent value="preview" forceMount className="mt-5 data-[state=inactive]:hidden">
          <ExamPreviewPanel
            key={`${state.version ?? "working"}-${previewRequest}`}
            exam={exam}
            check={checkQuery.data ?? null}
            initialPathId={state.path}
            autoGenerate={previewRequest > 0}
            version={state.version}
            onClearVersion={() => update({ ...state, version: null })}
            onShowCheck={() => update({ ...state, tab: "check" })}
          />
        </TabsContent>
      </Tabs>
      {publishOpen && (
        <Step4PublishDialog
          exam={exam}
          open={publishOpen}
          onOpenChange={setPublishOpen}
          onPublished={(response) =>
            notify.success(tVersions("published", { version: response.version, count: response.publishedPathIds.length }))
          }
        />
      )}
    </>
  );
}

function Step4PublishDialog({
  exam,
  open,
  onOpenChange,
  onPublished,
}: {
  exam: ExamDetail;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onPublished: (response: { version: number; publishedPathIds: string[] }) => void;
}) {
  const t = useTranslations("exams.publish");
  const tVersions = useTranslations("exams.versions");
  const versionsQuery = useExamVersions(exam.id, open);

  if (versionsQuery.data) {
    return (
      <PublishExamDialog
        open={open}
        onOpenChange={onOpenChange}
        exam={exam}
        versions={versionsQuery.data}
        onPublished={onPublished}
      />
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="md" closeLabel={t("close")}>
        <DialogHeader>
          <DialogTitle>{exam.servingVersion !== null ? tVersions("working.publish") : t("title", { version: 1 })}</DialogTitle>
          <DialogDescription>
            {versionsQuery.isError ? tVersions("errorDescription") : tVersions("loading")}
          </DialogDescription>
        </DialogHeader>
        {versionsQuery.isPending ? (
          <div className="flex flex-col gap-3 py-2" aria-busy="true">
            <Skeleton className="h-14" />
            <Skeleton className="h-28" />
          </div>
        ) : (
          <div className="flex items-start gap-2 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-2.5 text-body text-danger">
            <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
            <span>{tVersions("errorTitle")}</span>
          </div>
        )}
        <DialogFooter>
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            {t("cancel")}
          </Button>
          {versionsQuery.isError && (
            <Button type="button" onClick={() => void versionsQuery.refetch()}>
              {tVersions("retry")}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
