"use client";

import {
  Badge,
  Button,
  Card,
  Chip,
  DetailHeader,
  EmptyState,
  NavTab,
  NavTabs,
  notify,
  Skeleton,
  StatusChip,
} from "@thi360/ui";
import { Ban, CircleCheckBig, Copy, Pencil, TriangleAlert } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import { QUESTION_STATUS_TONE } from "@/features/content/status";
import { useSetBreadcrumbLeaf } from "@/features/shell/breadcrumb-leaf";
import { getErrorStatus } from "@/lib/api/query-client";
import { QUESTIONS_QUERY_KEYS, toQuestionSummary } from "../api";
import { useDuplicateQuestion } from "../editor-queries";
import { useQuestion } from "../queries";
import type { QuestionDetail } from "../types";
import { ManualGradingBadge } from "./question-detail-body";
import { MarkReadyDialog, RetireQuestionDialog } from "./question-status-dialogs";

/** Bốn trang con của chi tiết câu hỏi, mỗi trang một đường dẫn (NavTabs, DESIGN.md 5.5). */
export const QUESTION_DETAIL_TABS = ["content", "preview", "usage", "versions"] as const;
export type QuestionDetailTab = (typeof QUESTION_DETAIL_TABS)[number];

export function questionTabHref(questionId: string, tab: QuestionDetailTab): string {
  const base = `/questions/${encodeURIComponent(questionId)}`;
  return tab === "content" ? base : `${base}/${tab}`;
}

export function questionTabFromPathname(pathname: string): QuestionDetailTab {
  const last = pathname.replace(/\/+$/, "").split("/").at(-1);
  return last === "preview" || last === "usage" || last === "versions" ? last : "content";
}

export function questionEditHref(questionId: string): string {
  return `/questions/${encodeURIComponent(questionId)}/edit`;
}

/**
 * Khung chung của `/questions/[id]`, `/usage`, `/versions` (canvas `CauHoi*ChiTiet`, `CauHoiPhienBan`): đầu trang
 * chi tiết `DetailHeader` trên nền trang (mã, dạng, trạng thái theo UniNote mục 9 kèm phiên bản, môn · khối · mức độ),
 * thao tác Sửa, Nhân bản, Chuyển sang Sẵn sàng / Ngừng sử dụng, rồi thanh NavTabs. Trang con đọc câu bằng `useQuestion(id)` (cùng khóa).
 * Bản vẽ vẽ ba tab là `role="tablist"`, nhưng đây là ba trang riêng nên dùng tab điều hướng.
 */
export function QuestionDetailLayout({ questionId, children }: { questionId: string; children: ReactNode }) {
  const t = useTranslations("questions.detail");
  const pathname = usePathname();
  const activeTab = questionTabFromPathname(pathname);
  const questionQuery = useQuestion(questionId);

  if (questionQuery.isPending) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true" aria-label={t("loading")}>
        <Skeleton className="h-5 w-40" />
        <Skeleton className="h-28" />
        <Skeleton className="h-10 w-full max-w-md" />
        <Skeleton className="h-80" />
      </div>
    );
  }

  if (questionQuery.isError) {
    const notFound = getErrorStatus(questionQuery.error) === 404;
    return (
      <div className="flex flex-col gap-5">
        <h1 className="text-page-title font-bold text-text">{t("title")}</h1>
        <Card>
          <EmptyState
            icon={<TriangleAlert />}
            title={notFound ? t("notFoundTitle") : t("errorTitle")}
            description={notFound ? t("notFoundDescription") : t("errorDescription")}
            action={
              notFound ? (
                <Button asChild variant="secondary">
                  <Link href="/questions">{t("backToList")}</Link>
                </Button>
              ) : (
                <Button variant="secondary" onClick={() => void questionQuery.refetch()}>
                  {t("retry")}
                </Button>
              )
            }
          />
        </Card>
      </div>
    );
  }

  const question = questionQuery.data;
  return (
    <div className="flex flex-col gap-5">
      <QuestionDetailHeader question={question} />
      <NavTabs aria-label={t("tabsLabel")}>
        {QUESTION_DETAIL_TABS.map((tab) => (
          <NavTab key={tab} asChild current={tab === activeTab}>
            <Link href={questionTabHref(question.id, tab)}>
              {t(`tabs.${tab}`)}
              {tab === "versions" && <Badge className="ms-2">{question.version}</Badge>}
            </Link>
          </NavTab>
        ))}
      </NavTabs>
      <div key={activeTab} className="animate-route-in motion-reduce:animate-none">
        {children}
      </div>
    </div>
  );
}

/** Câu Tự luận hoặc câu chùm có câu con Tự luận: chấm tay. */
function hasEssay(question: QuestionDetail): boolean {
  return question.type === "ESSAY" || question.children.some((child) => child.type === "ESSAY");
}

function QuestionDetailHeader({ question }: { question: QuestionDetail }) {
  const t = useTranslations("questions.detail");
  const tCommon = useTranslations("common");
  const tType = useTranslations("content.questionType");
  const tStatus = useTranslations("content.questionStatus");
  const router = useRouter();
  useSetBreadcrumbLeaf(question.code);
  const referenceQuery = useContentReference();
  const lookup = useReferenceLookup(referenceQuery.data);
  const duplicate = useDuplicateQuestion();
  const [dialog, setDialog] = useState<"ready" | "retire" | null>(null);
  const summary = toQuestionSummary(question);
  const queryClient = useQueryClient();
  // Hộp thoại đổi trạng thái (UNI-2592) ghi vào kho; làm mới câu này và các trang con của nó.
  const refresh = () => void queryClient.invalidateQueries({ queryKey: QUESTIONS_QUERY_KEYS.detail(question.id) });

  return (
    <>
      <DetailHeader
        back={{ href: "/questions", label: tCommon("backTo.questions") }}
        linkComponent={Link}
        title={t("heading", { code: question.code })}
        chips={
          <>
            <Chip variant="tag" className="h-6">
              {tType(question.type)}
            </Chip>
            {hasEssay(question) && <ManualGradingBadge />}
            <StatusChip tone={QUESTION_STATUS_TONE[question.status]}>
              {t("statusVersion", { status: tStatus(question.status), version: question.version })}
            </StatusChip>
          </>
        }
        description={[
          lookup.subject(question.subjectCode),
          lookup.grade(question.gradeCode),
          lookup.difficulty(question.difficultyCode),
        ].join(" · ")}
        actions={
          <>
            <Button asChild variant="secondary">
              <Link href={questionEditHref(question.id)}>
                <Pencil aria-hidden />
                {t("actions.edit")}
              </Link>
            </Button>
            <Button
              variant="secondary"
              loading={duplicate.isPending}
              onClick={() =>
                duplicate.mutate(question.id, {
                  onSuccess: (copy) => {
                    notify.success(t("actions.duplicated", { code: copy.code, source: question.code }));
                    router.push(questionEditHref(copy.id));
                  },
                  onError: () => notify.error(t("actions.duplicateFailed")),
                })
              }
            >
              <Copy aria-hidden />
              {t("actions.duplicate")}
            </Button>
            {question.status === "DRAFT" && (
              <Button onClick={() => setDialog("ready")}>
                <CircleCheckBig aria-hidden />
                {t("actions.markReady")}
              </Button>
            )}
            {question.status === "READY" && (
              <Button variant="secondary" className="text-danger" onClick={() => setDialog("retire")}>
                <Ban aria-hidden />
                {t("actions.retire")}
              </Button>
            )}
          </>
        }
      />
      <MarkReadyDialog
        question={summary}
        open={dialog === "ready"}
        onOpenChange={(open) => setDialog(open ? "ready" : null)}
        onDone={refresh}
      />
      <RetireQuestionDialog
        question={summary}
        open={dialog === "retire"}
        onOpenChange={(open) => setDialog(open ? "retire" : null)}
        onDone={refresh}
      />
    </>
  );
}
