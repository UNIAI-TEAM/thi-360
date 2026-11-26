/** Bốn bước thiết lập bài thi (NavTabs, DESIGN.md 5.5); Lịch sử phiên bản là thao tác riêng ở đầu trang. */
export const EXAM_EDITOR_TABS = ["general", "structure", "listing", "review"] as const;
export type ExamEditorTab = (typeof EXAM_EDITOR_TABS)[number];
export type ExamEditorRoute = ExamEditorTab | "versions";

export const FROM_REVIEW_PARAM = "from";
export const FROM_REVIEW_VALUE = "review";
export const RECHECK_PARAM = "recheck";

export function examTabHref(examId: string, tab: ExamEditorRoute): string {
  const base = `/exams/${encodeURIComponent(examId)}`;
  return tab === "general" ? base : `${base}/${tab}`;
}

export function examVersionsHref(examId: string): string {
  return `/exams/${encodeURIComponent(examId)}/versions`;
}

export function examTabFromPathname(pathname: string): ExamEditorRoute {
  const last = pathname.replace(/\/+$/, "").split("/").at(-1);
  if (last === "versions") return "versions";
  return (EXAM_EDITOR_TABS as readonly string[]).includes(last ?? "") ? (last as ExamEditorTab) : "general";
}

/** Gắn `?from=review` vào liên kết sửa lỗi từ bước 4 để bước đích đổi nút chính thành "Lưu và quay lại kiểm tra". */
export function withFromReview(href: string): string {
  const [pathAndQuery, hash] = href.split("#", 2);
  const [path, query] = (pathAndQuery ?? "").split("?", 2);
  const params = new URLSearchParams(query ?? "");
  params.set(FROM_REVIEW_PARAM, FROM_REVIEW_VALUE);
  const next = `${path}?${params.toString()}`;
  return hash ? `${next}#${hash}` : next;
}

export function isFromReview(search: Pick<URLSearchParams, "get"> | string | null | undefined): boolean {
  if (!search) return false;
  const params = typeof search === "string" ? new URLSearchParams(search) : search;
  return params.get(FROM_REVIEW_PARAM) === FROM_REVIEW_VALUE;
}

export function reviewReturnHref(examId: string): string {
  return `/exams/${encodeURIComponent(examId)}/review?${RECHECK_PARAM}=1`;
}
