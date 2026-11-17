"use client";

import { Skeleton, Tabs, TabsContent, TabsList, TabsTrigger } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { usePathname, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useExam } from "../queries";
import { useExamCheck } from "../review-queries";
import { ExamPreviewPanel } from "./exam-preview-panel";
import { ExamReviewCheck } from "./exam-review-check";

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

function readVersion(value: string | null): number | null {
  const parsed = Number(value);
  return value && Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

/**
 * Trang "Kiểm tra và xem trước" của bài thi: hai tab nội dung trong cùng trang, Kiểm tra cấu hình (`BaiThiKiemTra`)
 * và Đề xem trước (`BaiThiXemTruoc`). Bản vẽ vẽ hai màn riêng dưới cùng một tab điều hướng.
 */
export function ExamReviewView({ examId }: { examId: string }) {
  const t = useTranslations("exams.review");
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const examQuery = useExam(examId);
  const checkQuery = useExamCheck(examId);
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

  // Khung soạn bài thi (`ExamEditorLayout`) đã lo trạng thái tải và lỗi của bài thi.
  if (!examQuery.data) return <Skeleton className="h-96" />;
  const exam = examQuery.data;

  return (
    <Tabs value={state.tab} onValueChange={(value) => update({ ...state, tab: value as ReviewTab })}>
      <TabsList aria-label={t("tabs.label")}>
        <TabsTrigger value="check">{t("tabs.check")}</TabsTrigger>
        <TabsTrigger value="preview">{t("tabs.preview")}</TabsTrigger>
      </TabsList>
      {/* Giữ cả hai tab trong DOM để đề vừa sinh không mất khi xem lại kết quả kiểm tra. */}
      <TabsContent value="check" forceMount className="mt-5 data-[state=inactive]:hidden">
        <ExamReviewCheck
          exam={exam}
          active={state.tab === "check"}
          onPreview={(pathId) => {
            update({ tab: "preview", path: pathId ?? state.path, version: null });
            if (pathId) setPreviewRequest((value) => value + 1);
          }}
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
  );
}
