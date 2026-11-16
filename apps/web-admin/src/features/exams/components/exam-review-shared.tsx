"use client";

import { cn, focusRing, StatusChip, Tooltip, type StatusTone } from "@thi360/ui";
import { CircleCheck, CircleHelp, CircleX } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useCallback, useMemo, type ReactNode } from "react";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import type { ChoiceNameResolver } from "../check";
import type { BlockCheckResult, CheckIssue, ExamPathRef, PathCheckResult } from "../review-types";

/** Chip và icon cho ba kết quả kiểm tra (DESIGN.md 2.4); chữ luôn đi kèm màu. */
export const PATH_RESULT_TONE: Record<PathCheckResult, StatusTone> = {
  VALID: "success",
  INVALID: "danger",
  UNCONFIRMED: "warning",
};

export const BLOCK_RESULT_TONE: Record<BlockCheckResult, StatusTone> = {
  FOUND: "success",
  NOT_FOUND: "danger",
  UNCONFIRMED: "warning",
};

export function PathResultIcon({ result, className }: { result: PathCheckResult; className?: string }) {
  const Icon = result === "VALID" ? CircleCheck : result === "INVALID" ? CircleX : CircleHelp;
  const tone = result === "VALID" ? "text-success" : result === "INVALID" ? "text-danger" : "text-warning";
  return <Icon aria-hidden className={`${tone} ${className ?? "size-4 shrink-0"}`} />;
}

export function PathResultChip({ result }: { result: PathCheckResult }) {
  const t = useTranslations("exams.review");
  return <StatusChip tone={PATH_RESULT_TONE[result]}>{t(`result.${result}`)}</StatusChip>;
}

/** Đường dẫn tới trang Cấu trúc và ma trận, mở đúng khối khi biết (`?block=`, lane Cấu trúc đọc tham số này). */
export function structureHref(examId: string, blockId?: string | null): string {
  const base = `/exams/${encodeURIComponent(examId)}/structure`;
  return blockId ? `${base}?block=${encodeURIComponent(blockId)}` : base;
}

export function reviewHref(examId: string, params: Record<string, string> = {}): string {
  const query = new URLSearchParams(params).toString();
  const base = `/exams/${encodeURIComponent(examId)}/review`;
  return query ? `${base}?${query}` : base;
}

/** Tên một đường: "KH-1 · Vật lí – Hóa học – Sinh học", "Tiếng Anh"; bài không có phần tự chọn: "Đề chung". */
export function usePathLabel() {
  const t = useTranslations("exams.review");
  return useCallback(
    (path: ExamPathRef) =>
      path.choices.length
        ? path.choices
            .map((choice) => (choice.kind === "COMBINATION" ? `${choice.code} · ${choice.name}` : choice.name))
            .join(" + ")
        : t("path.single"),
    [t],
  );
}

/** Tên môn, tổ hợp theo danh mục, để liệt kê đường lựa chọn khi chưa có kết quả kiểm tra. */
export function useChoiceNameResolver(): ChoiceNameResolver {
  const reference = useContentReference().data;
  return useMemo(() => {
    const combinations = new Map((reference?.combinations ?? []).map((item) => [item.code, item.name]));
    const subjects = new Map((reference?.subjects ?? []).map((item) => [item.code, item.name]));
    return (kind, code) => (kind === "COMBINATION" ? combinations.get(code) : subjects.get(code)) ?? code;
  }, [reference]);
}

export function useLookup() {
  const reference = useContentReference().data;
  return useReferenceLookup(reference);
}

/** Số mã câu nguồn nêu trong một vấn đề; còn lại ghi "…". */
const QUESTION_CODES_SHOWN = 5;

/** Câu mô tả một vấn đề, dựng lúc hiển thị để đổi theo ngôn ngữ đang chọn (DESIGN.md 5.13). */
export function useIssueText() {
  const t = useTranslations("exams.review.issue");
  const tContent = useTranslations("content");
  const lookup = useLookup();
  const competencies = useContentReference().data?.competencies;
  return useCallback(
    (issue: CheckIssue) => {
      const subject = issue.subjectCode ? lookup.subject(issue.subjectCode) : "";
      const codes = issue.questionCodes ?? [];
      const competency = issue.competencyCode
        ? (competencies?.find((item) => item.code === issue.competencyCode)?.name ?? issue.competencyCode)
        : "";
      const values = {
        block: issue.blockName ? `${subject} · ${issue.blockName}` : subject,
        subject,
        part: issue.partName ?? "",
        grade: issue.gradeCode ? lookup.grade(issue.gradeCode) : "",
        difficulty: issue.difficultyCode ? lookup.difficulty(issue.difficultyCode) : "",
        level:
          issue.dimension === "GRADE" && issue.gradeCode
            ? lookup.grade(issue.gradeCode)
            : issue.dimension === "COMPETENCY"
              ? competency
              : issue.difficultyCode
                ? lookup.difficulty(issue.difficultyCode)
                : "",
        dimension: issue.dimension ?? "NONE",
        required: issue.required ?? 0,
        available: issue.available ?? 0,
        seconds: issue.timeLimitSeconds ?? 0,
        answerType: issue.answerType ? tContent(`questionType.${issue.answerType}`) : "",
        count: issue.subjectCount ?? 0,
        competency,
        codes: codes.slice(0, QUESTION_CODES_SHOWN).join(", ") + (codes.length > QUESTION_CODES_SHOWN ? ", …" : ""),
      };
      return t(issue.code, values);
    },
    [t, tContent, lookup, competencies],
  );
}

/** Vấn đề kèm tên đường khi vấn đề thuộc một lựa chọn của phần tự chọn. */
export function useIssueWithPath(paths: readonly ExamPathRef[]) {
  const t = useTranslations("exams.review");
  const issueText = useIssueText();
  return useCallback(
    (issue: CheckIssue) => {
      const text = issueText(issue);
      if (!issue.optionId) return text;
      const path = paths.find((item) => item.choices.some((choice) => choice.optionId === issue.optionId));
      const choice = path?.choices.find((item) => item.optionId === issue.optionId);
      if (!choice) return text;
      const label = choice.kind === "COMBINATION" ? choice.code : choice.name;
      return t("issueWithPath", { path: label, issue: text });
    },
    [t, issueText, paths],
  );
}

export function blockLabel(subjectName: string, blockName: string): string {
  return `${subjectName} · ${blockName}`;
}

/** Liệt kê mã ngắn của các đường ("Tiếng Anh, KH-1, KH-2") cho bảng phiên bản. */
export function pathLabelCodes(paths: readonly ExamPathRef[], single: string): string {
  if (paths.length === 0) return "";
  return paths
    .map((path) =>
      path.choices.length
        ? path.choices.map((choice) => (choice.kind === "COMBINATION" ? choice.code : choice.name)).join(" + ")
        : single,
    )
    .join(", ");
}

/**
 * Liên kết chỉ có icon trong cột Thao tác: cùng cỡ, vòng focus, tooltip với `IconButton` của kit (DESIGN.md 5.9),
 * nhưng là `<Link>` vì dẫn sang trang khác.
 */
export function IconLink({ href, label, tooltip, icon }: { href: string; label: string; tooltip?: string; icon: ReactNode }) {
  return (
    <Tooltip content={tooltip ?? label}>
      <Link
        href={href}
        aria-label={label}
        className={cn(
          "inline-flex size-10 shrink-0 items-center justify-center rounded-lg text-text-secondary transition-colors duration-150 hover:bg-subtle motion-reduce:transition-none [&_svg]:size-[18px]",
          focusRing,
        )}
      >
        <span aria-hidden className="contents">
          {icon}
        </span>
      </Link>
    </Tooltip>
  );
}
