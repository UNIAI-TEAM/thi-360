"use client";

import { cn, focusRing, StatusChip, Tooltip, type StatusTone } from "@thi360/ui";
import { CircleCheck, CircleHelp, CircleX } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useCallback, useMemo, type ReactNode } from "react";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import { blockFilterConfig, combinationLabel, findBlockContext, type ChoiceNameResolver } from "../check";
import { type ChangeLevel, parseChangeWhere } from "../change-where";
import { roundForDisplay } from "../display-number";
import { useExamStructure } from "../queries";
import type { BlockCheckResult, CheckIssue, CheckIssueCode, ExamPathRef, PathCheckResult } from "../review-types";
import type { ExamDetail, ExamPart, ExamSection } from "../types";

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

/**
 * Tên một đường: "QD02 · Hóa học – Sinh học", "Tiếng Anh"; bài không có phần tự chọn: "Đề chung". Có `examId` thì tên mã
 * lấy theo ngôn ngữ từ cấu trúc của bài; kết quả kiểm tra chỉ có tên = mã, không in lặp "QD01 · QD01".
 */
export function usePathLabel(examId?: string) {
  const t = useTranslations("exams.review");
  const locale = useLocale();
  const structure = useExamStructure(examId ?? "", undefined, Boolean(examId));
  const combinations = structure.data?.combinations;
  return useCallback(
    (path: ExamPathRef) =>
      path.choices.length
        ? path.choices
            .map((choice) =>
              choice.kind === "COMBINATION"
                ? combinationLabel(choice.code, {
                    combination: combinations?.find((item) => item.code === choice.code),
                    locale,
                    fallback: choice.name,
                  })
                : choice.name,
            )
            .join(" + ")
        : t("path.single"),
    [t, locale, combinations],
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

/** Mã vấn đề có câu mô tả riêng khi máy chủ không trả số câu cần/có (`exams.review.issueNoCount`). */
const COUNT_ISSUE_CODES: Partial<Record<CheckIssueCode, true>> = {
  CELL_SHORTAGE: true,
  DIMENSION_SHORTAGE: true,
  COMPETENCY_SHORTAGE: true,
  ANSWER_TYPE_SHORTAGE: true,
  SUBJECT_QUOTA_INFEASIBLE: true,
};

/** Tên nhiều mã nối bằng "+" (chỉ tiêu cấp bài gộp nhiều khối lớp: `G10+G11`) thành "tên + tên". */
function joinedNames(value: string | null, nameOf: (code: string) => string): string {
  return value ? value.split("+").map(nameOf).join(" + ") : "";
}

/**
 * Câu mô tả một vấn đề, dựng lúc hiển thị để đổi theo ngôn ngữ đang chọn (DESIGN.md 5.13). Truyền `exam` thì vấn đề
 * chỉ có `blockId` (lỗi nguồn của bộ giải) lấy tên khối, tên phần và điều kiện lọc của khối từ cấu trúc.
 */
export function useIssueText(exam?: Pick<ExamDetail, "parts">) {
  const t = useTranslations("exams.review.issue");
  const tNoCount = useTranslations("exams.review.issueNoCount");
  const tContent = useTranslations("content");
  const tFormat = useTranslations("questions.detail.body.answerFormats");
  const lookup = useLookup();
  const competencies = useContentReference().data?.competencies;
  return useCallback(
    (issue: CheckIssue) => {
      const where = exam ? findBlockContext(exam, issue.blockId) : null;
      const filter = blockFilterConfig(where?.block);
      const subjectCode = issue.subjectCode ?? where?.section?.subjectCode ?? null;
      const blockName = issue.blockName ?? where?.block.name ?? null;
      const subject = subjectCode ? lookup.subject(subjectCode) : "";
      const codes = issue.questionCodes ?? [];
      const competency = issue.competencyCode
        ? (competencies?.find((item) => item.code === issue.competencyCode)?.name ?? issue.competencyCode)
        : "";
      const grade = joinedNames(issue.gradeCode, lookup.grade);
      const difficulty = joinedNames(issue.difficultyCode, lookup.difficulty);
      const level =
        issue.dimension === "GRADE" && issue.gradeCode
          ? grade
          : issue.dimension === "COMPETENCY"
            ? competency
            : difficulty;
      // Ô chỉ tiêu bị thiếu nguồn: "lớp/độ khó", hoặc một chiều; chiều không có tên thì bỏ cả cụm, không in "/".
      const bucket =
        issue.code === "DIMENSION_SHORTAGE" || issue.dimension === "GRADE" || issue.dimension === "DIFFICULTY"
          ? level
          : [grade, difficulty].filter(Boolean).join("/");
      const shape = filter.itemShape;
      const shapeKind =
        shape?.leftItems != null && shape.rightItems != null ? "MATCHING" : shape?.statements != null ? "STATEMENTS" : "NONE";
      const positions = issue.childPosition
        ? String(issue.childPosition)
        : filter.childPositions.map((range) => (range.from === range.to ? `${range.from}` : `${range.from}–${range.to}`)).join(", ");
      const required = issue.required ?? 0;
      const available = issue.available ?? 0;
      const values = {
        block: blockName ? `${subject} · ${blockName}` : subject,
        subject,
        part: issue.partName ?? where?.part.name ?? "",
        place: (blockName ? `${subject} · ${blockName}` : subject) || (issue.partName ?? where?.part.name ?? ""),
        grade,
        difficulty,
        level,
        bucket,
        hasBucket: bucket ? "yes" : "no",
        dimension: issue.dimension ?? "NONE",
        required: roundForDisplay(required),
        available: roundForDisplay(available),
        missing: roundForDisplay(Math.max(0, required - available)),
        hasCount: issue.required == null || issue.available == null ? "no" : "yes",
        seconds: issue.timeLimitSeconds ?? 0,
        answerType: issue.answerType ? tContent(`questionType.${issue.answerType}`) : "",
        count: issue.subjectCount ?? 0,
        competency,
        codes: codes.slice(0, QUESTION_CODES_SHOWN).join(", ") + (codes.length > QUESTION_CODES_SHOWN ? ", …" : ""),
        slot: issue.slotIndex ?? 0,
        hasSlot: issue.slotIndex ? "yes" : "no",
        shapeKind,
        left: shape?.leftItems ?? 0,
        right: shape?.rightItems ?? 0,
        statements: shape?.statements ?? 0,
        formats: filter.answerFormats.map((format) => tFormat(format)).join(", "),
        hasFormats: filter.answerFormats.length > 0 ? "yes" : "no",
        positions,
        hasPositions: positions ? "yes" : "no",
      };
      // Máy chủ chưa điền số câu cần/có (null): không in "cần 0 câu, hiện có 0 câu", dùng câu không nêu số.
      const noCount = issue.required == null || issue.available == null;
      if (noCount && COUNT_ISSUE_CODES[issue.code]) return tNoCount(issue.code, values);
      return t(issue.code, values);
    },
    [t, tNoCount, tContent, tFormat, lookup, competencies, exam],
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
      // Câu của phần đã mở đầu bằng tên phần ("Khoa học hoặc Tiếng Anh: …"): không lặp tên lựa chọn nếu tên phần đã chứa nó.
      const partName = issue.partName;
      if (partName && text.startsWith(`${partName}: `)) {
        if (partName.toLowerCase().includes(label.toLowerCase())) return text;
        return `${partName} (${label})${text.slice(partName.length)}`;
      }
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

/** Tên các phần tử của cấu trúc hiện tại theo id, để dựng nhãn vị trí thay đổi. `null`: không có tên dùng được. */
function structureNames(
  parts: readonly ExamPart[],
  subjectName: (code: string) => string,
): Record<ChangeLevel, Map<string, string | null>> {
  const names: Record<ChangeLevel, Map<string, string | null>> = {
    part: new Map(),
    section: new Map(),
    block: new Map(),
    option: new Map(),
  };
  const visitBlocks = (blocks: readonly { id: string; name: string }[] | undefined) => {
    for (const block of blocks ?? []) names.block.set(block.id, block.name.trim() || null);
  };
  const visitSections = (sections: readonly ExamSection[]) => {
    for (const section of sections) {
      names.section.set(section.id, subjectName(section.subjectCode));
      visitBlocks(section.blocks);
    }
  };
  for (const part of parts) {
    names.part.set(part.id, part.name.trim() || null);
    visitSections(part.sections);
    for (const option of part.options) {
      names.option.set(option.id, option.kind === "COMBINATION" ? option.code : (option.name ?? option.code));
      visitSections(option.sections);
      visitBlocks(option.blocks);
    }
  }
  return names;
}

/**
 * Nhãn dễ đọc cho vị trí của một thay đổi (`ExamChange.where`): tên phần, môn, khối, lựa chọn lấy từ cấu trúc hiện tại,
 * không có thì chữ chung ("Khối"); sau đó là nhãn của trường. Không bao giờ in id. `where` không phải đường dẫn (dữ liệu
 * mẫu đã là chữ mô tả) giữ nguyên.
 */
export function useChangeWhere(exam: ExamDetail) {
  const t = useTranslations("exams.versions.changes.where");
  const lookup = useLookup();
  const names = useMemo(() => structureNames(exam.parts, lookup.subject), [exam.parts, lookup]);
  return useCallback(
    (where: string) => {
      const target = parseChangeWhere(where);
      if (!target) return where;
      const labels = target.steps.map((step) =>
        step.id === null ? t(`all.${step.level}`) : (names[step.level].get(step.id) ?? t(`item.${step.level}`)),
      );
      if (target.config) labels.unshift(t("config"));
      const field = target.fields.find((name) => t.has(`field.${name}`));
      if (field) labels.push(t(`field.${field}`));
      return labels.length > 0 ? labels.join(" · ") : t("structure");
    },
    [t, names],
  );
}
