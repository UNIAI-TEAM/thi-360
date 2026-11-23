/** Năm tab của trang soạn bài thi, là trang con riêng (NavTabs, DESIGN.md 5.5); Thông tin bán nằm giữa Cấu trúc và Kiểm tra. */
export const EXAM_EDITOR_TABS = ["general", "structure", "listing", "review", "versions"] as const;
export type ExamEditorTab = (typeof EXAM_EDITOR_TABS)[number];

export function examTabHref(examId: string, tab: ExamEditorTab): string {
  const base = `/exams/${encodeURIComponent(examId)}`;
  return tab === "general" ? base : `${base}/${tab}`;
}

export function examTabFromPathname(pathname: string): ExamEditorTab {
  const last = pathname.replace(/\/+$/, "").split("/").at(-1);
  return (EXAM_EDITOR_TABS as readonly string[]).includes(last ?? "") ? (last as ExamEditorTab) : "general";
}
