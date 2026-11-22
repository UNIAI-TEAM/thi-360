"use client";

import { cn, focusRing, StatusChip, Tooltip, type StatusTone } from "@thi360/ui";
import { CircleCheck, CircleHelp, CircleX } from "lucide-react";
import { useLocale, useTranslations, type RichTranslationValues, type TranslationValues } from "next-intl";
import Link from "next/link";
import { cloneElement, Fragment, isValidElement, useCallback, useId, useMemo, type ReactNode } from "react";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import { blockFilterConfig, combinationLabel, findBlockContext, type ChoiceNameResolver } from "../check";
import { type ChangeLevel, parseChangeWhere } from "../change-where";
import { roundForDisplay } from "../display-number";
import { useExamStructure } from "../queries";
import type { BlockCheckResult, CheckIssue, CheckIssueCode, ExamPathRef, PathCheckResult } from "../review-types";
import { ExamContentText } from "./exam-content-text";
import { useMapidIssueText } from "./use-mapid-issue-text";
import type { ExamDetail, ExamPart, ExamSection } from "../types";

/**
 * Tên do mẫu hoặc người soạn đặt (phần, mục, khối, lựa chọn, tên bài) là nội dung đề: hiện đúng như đã lưu ở mọi ngôn
 * ngữ giao diện (DESIGN.md mục 8). Chỉ bọc phần tên, không bọc nhãn giao diện đi kèm.
 */
export function ExamName({ children }: { children: ReactNode }) {
  return <span data-exam-content>{children}</span>;
}

/** id DOM ghép từ khóa máy chủ; khóa có thể chứa khoảng trắng mà `aria-labelledby` tách id theo khoảng trắng. */
export function domId(...parts: string[]): string {
  return parts.join("-").replace(/\s+/g, "_");
}

type LabelTranslator = (key: string, values: TranslationValues) => string;

/** Câu dịch dựng với một dấu thay chỗ của tên: phần giao diện đứng trước và đứng sau tên. */
function splitAroundName(t: LabelTranslator, key: string, slot: string): { before: string; after: string } {
  const marker = "\u0001";
  const text = t(key, { [slot]: marker });
  const at = text.indexOf(marker);
  return { before: text.slice(0, at).trim(), after: text.slice(at + marker.length).trim() };
}

/**
 * Nhãn giao diện đứng trước tên đối tượng trong câu dịch ("Mở khối " của "Mở khối {block}"). Điều khiển ghép nhãn với tên
 * đang hiện trên màn bằng `aria-labelledby="<id điều khiển> <id của tên>"` (id của chính nó lấy `aria-label`), nên tên nằm
 * trong `ExamName` thay vì trong thuộc tính.
 */
export function labelBeforeName(t: LabelTranslator, key: string, slot: string): string {
  return splitAroundName(t, key, slot).before;
}

/** Phần giao diện đứng sau tên ("ở Cấu trúc và ma trận"); rỗng khi tên ở cuối câu. */
export function labelAfterName(t: LabelTranslator, key: string, slot: string): string {
  return splitAroundName(t, key, slot).after;
}

/**
 * `t.rich` với giá trị là nút React (tên trong `ExamName`): ICU chèn nút vào chỗ đối số đơn giản `{name}`, câu dịch giữ
 * nguyên. Kiểu của use-intl chỉ khai báo chuỗi, số, ngày cho đối số, nên ép kiểu ở đây một lần.
 */
export function richNodes(
  t: { rich: (key: string, values?: RichTranslationValues) => ReactNode },
  key: string,
  values: Record<string, ReactNode>,
): ReactNode {
  const keyedValues: Record<string, ReactNode> = {};
  for (const [slot, node] of Object.entries(values)) {
    if (isValidElement(node)) {
      keyedValues[slot] = node.key == null ? cloneElement(node, { key: slot }) : node;
    } else if (Array.isArray(node)) {
      const keyedItems = node.map((item, idx) =>
        isValidElement(item) && item.key == null ? cloneElement(item, { key: `${slot}-${idx}` }) : item,
      );
      keyedValues[slot] = <Fragment key={slot}>{keyedItems}</Fragment>;
    } else {
      keyedValues[slot] = node;
    }
  }
  const rendered = t.rich(key, keyedValues as RichTranslationValues);
  if (!Array.isArray(rendered)) return rendered;
  return rendered.map((part, index) =>
    isValidElement(part)
      ? cloneElement(part, { key: `rich-${index}` })
      : Array.isArray(part)
        ? <Fragment key={`rich-${index}`}>{part}</Fragment>
        : part,
  );
}

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

/** Một đoạn tên đường: tên lựa chọn của mẫu là nội dung đề (`exam`); tên tổ hợp theo danh mục thì không. */
interface PathLabelPart {
  text: string;
  exam: boolean;
}

function usePathLabelParts(examId?: string) {
  const t = useTranslations("exams.review");
  const locale = useLocale();
  const structure = useExamStructure(examId ?? "", undefined, Boolean(examId));
  const combinations = structure.data?.combinations;
  return useCallback(
    (path: ExamPathRef): PathLabelPart[] =>
      path.choices.length
        ? path.choices.map((choice) =>
            choice.kind === "COMBINATION"
              ? {
                  text: combinationLabel(choice.code, {
                    combination: combinations?.find((item) => item.code === choice.code),
                    locale,
                    fallback: choice.name,
                  }),
                  exam: false,
                }
              : { text: choice.name, exam: true },
          )
        : [{ text: t("path.single"), exam: false }],
    [t, locale, combinations],
  );
}

/**
 * Tên một đường: "QD02 · Hóa học – Sinh học", "Tiếng Anh"; bài không có phần tự chọn: "Đề chung". Có `examId` thì tên mã
 * lấy theo ngôn ngữ từ cấu trúc của bài; kết quả kiểm tra chỉ có tên = mã, không in lặp "QD01 · QD01". Dạng chuỗi,
 * cho chỗ cần chữ thuần; hiển thị dùng `usePathNode`.
 */
export function usePathLabel(examId?: string) {
  const parts = usePathLabelParts(examId);
  return useCallback((path: ExamPathRef) => parts(path).map((part) => part.text).join(" + "), [parts]);
}

/** Như `usePathLabel` nhưng tên lựa chọn của mẫu nằm trong `ExamName`. */
export function usePathNode(examId?: string) {
  const parts = usePathLabelParts(examId);
  return useCallback(
    (path: ExamPathRef): ReactNode =>
      parts(path).map((part, index) => (
        <Fragment key={index}>
          {index > 0 && " + "}
          {part.exam ? <ExamName>{part.text}</ExamName> : part.text}
        </Fragment>
      )),
    [parts],
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
 * Dựng câu mô tả một vấn đề lúc hiển thị để đổi theo ngôn ngữ đang chọn (DESIGN.md 5.13). Truyền `exam` thì vấn đề
 * chỉ có `blockId` (lỗi nguồn của bộ giải) lấy tên khối, tên phần và điều kiện lọc của khối từ cấu trúc. `text` trả chuỗi;
 * `node` trả nút React có tên phần, khối, phân đoạn nằm trong `ExamName` (nội dung đề), tên môn và danh mục ở ngoài.
 */
function useIssueTranslate(exam?: Pick<ExamDetail, "parts">, marked = false) {
  const t = useTranslations("exams.review.issue");
  const tNoCount = useTranslations("exams.review.issueNoCount");
  const tContent = useTranslations("content");
  const tFormat = useTranslations("questions.detail.body.answerFormats");
  const lookup = useLookup();
  const competencies = useContentReference().data?.competencies;
  const mapidIssue = useMapidIssueText(exam, marked);
  return useMemo(() => {
    const build = (issue: CheckIssue, rich: boolean, partOverride?: ReactNode) => {
      const where = exam ? findBlockContext(exam, issue.blockId) : null;
      const filter = blockFilterConfig(where?.block);
      const subjectCode = issue.subjectCode ?? where?.section?.subjectCode ?? null;
      const blockName = issue.blockName ?? where?.block.name ?? null;
      const partName = issue.partName ?? where?.part.name ?? "";
      const subject = subjectCode ? lookup.subject(subjectCode) : "";
      const named = (text: string): ReactNode => (rich ? <ExamName>{text}</ExamName> : text);
      const withSubject = (text: string): ReactNode =>
        rich ? (
          <>
            {subject} · <ExamName>{text}</ExamName>
          </>
        ) : (
          `${subject} · ${text}`
        );
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
      const blockText = blockName ? withSubject(blockName) : subject;
      const place = blockName || subject ? blockText : partName ? named(partName) : "";
      const required = issue.required ?? 0;
      const available = issue.available ?? 0;
      // Phân đoạn của DISPLAY_SEGMENT_MISMATCH: tên từ `displaySegments` của phần (BE-20; FE-6 giữ trường trên cấu trúc), không in mã.
      const segment = issue.segmentCode
        ? ((exam?.parts.find((part) => part.id === issue.partId)?.displaySegments ?? []).find((item) => item.code === issue.segmentCode)?.name ?? "")
        : "";
      const values: Record<string, ReactNode> = {
        block: blockText,
        subject,
        // Id đã lưu bị trùng (STORED_ID_COLLISION): máy chủ đặt id đó ở `blockId` của lỗi cấp cả bài.
        id: issue.blockId ?? "",
        part: partOverride ?? (partName ? named(partName) : ""),
        place,
        hasPlace: blockName || subject || partName ? "yes" : "no",
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
        excluded: roundForDisplay(issue.excludedPoints ?? 0),
        hasExcluded: issue.required == null || issue.available == null || issue.excludedPoints == null ? "no" : "yes",
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
        segment: segment ? named(segment) : "",
        hasSegment: segment ? "yes" : "no",
      };
      // Quy tắc MAPID (UNI-2758) có câu riêng dựng từ giá trị chuỗi; `block` ở đây là chuỗi, không phải nút.
      const plain: Record<string, string | number> = { block: blockName ? `${subject} · ${blockName}` : subject };
      for (const key of ["hasCount", "required", "available", "missing"]) plain[key] = values[key] as string | number;
      const mapid = mapidIssue(issue, where, plain);
      if (mapid.text !== null) return { text: mapid.text, translator: null, values: null };
      const withMapid = { ...values, ...mapid.values };
      // Máy chủ chưa điền số câu cần/có (null): không in "cần 0 câu, hiện có 0 câu", dùng câu không nêu số.
      const noCount = issue.required == null || issue.available == null;
      return { text: null, translator: noCount && COUNT_ISSUE_CODES[issue.code] ? tNoCount : t, values: withMapid };
    };
    // Giá trị nút React đi qua ICU như đối số đơn giản (use-intl giữ nguyên đối tượng, `t.rich` trả mảng); kiểu của
    // use-intl chỉ khai báo chuỗi, số, ngày, nên ép kiểu một chỗ này.
    return {
      text: (issue: CheckIssue) => {
        const built = build(issue, false);
        return built.text !== null ? built.text : built.translator(issue.code, built.values as TranslationValues);
      },
      node: (issue: CheckIssue, partOverride?: ReactNode): ReactNode => {
        const built = build(issue, true, partOverride);
        if (built.text !== null) return marked ? <ExamContentText text={built.text} /> : built.text;
        const rich = built.translator.rich(issue.code, built.values as RichTranslationValues);
        return marked ? markedContent(rich) : rich;
      },
    };
  }, [t, tNoCount, tContent, tFormat, lookup, competencies, exam, mapidIssue, marked]);
}

/** Chuỗi trong nút có tên đánh dấu (`markExamContent`, tên quy tắc và nhóm) hiện bằng `ExamContentText`. */
function markedContent(node: ReactNode): ReactNode {
  if (typeof node === "string") return <ExamContentText text={node} />;
  if (!Array.isArray(node)) return node;
  return node.map((item: ReactNode, index) =>
    typeof item === "string" ? (
      <Fragment key={`marked-${index}`}>
        <ExamContentText text={item} />
      </Fragment>
    ) : (
      item
    ),
  );
}

/** Câu mô tả một vấn đề dạng chuỗi (thuộc tính, chuỗi ghép, ô nhập); hiển thị trong cây giao diện dùng `useIssueNode`. `marked`: tên trong câu đánh dấu cho `ExamContentText`. */
export function useIssueText(exam?: Pick<ExamDetail, "parts">, marked = false) {
  return useIssueTranslate(exam, marked).text;
}

/** Câu mô tả một vấn đề để hiển thị: tên phần, khối, phân đoạn nằm trong `ExamName`. */
export function useIssueNode(exam?: Pick<ExamDetail, "parts">, marked = false) {
  const { node } = useIssueTranslate(exam, marked);
  return useCallback((issue: CheckIssue) => node(issue), [node]);
}

/** Vấn đề kèm tên đường khi vấn đề thuộc một lựa chọn của phần tự chọn. */
export function useIssueWithPath(paths: readonly ExamPathRef[], exam?: Pick<ExamDetail, "parts">, marked = false) {
  const t = useTranslations("exams.review");
  const { text, node } = useIssueTranslate(exam, marked);
  return useCallback(
    (issue: CheckIssue): ReactNode => {
      if (!issue.optionId) return node(issue);
      const path = paths.find((item) => item.choices.some((choice) => choice.optionId === issue.optionId));
      const choice = path?.choices.find((item) => item.optionId === issue.optionId);
      if (!choice) return node(issue);
      const combination = choice.kind === "COMBINATION";
      const label = combination ? choice.code : choice.name;
      const labelNode = combination ? label : <ExamName>{label}</ExamName>;
      // Câu của phần đã mở đầu bằng tên phần ("Khoa học hoặc Tiếng Anh: …"): không lặp tên lựa chọn nếu tên phần đã chứa nó.
      const partName = issue.partName;
      if (partName && text(issue).startsWith(`${partName}: `)) {
        if (partName.toLowerCase().includes(label.toLowerCase())) return node(issue);
        return node(
          issue,
          <>
            <ExamName>{partName}</ExamName> ({labelNode})
          </>,
        );
      }
      return t.rich("issueWithPath", { path: labelNode, issue: node(issue) } as RichTranslationValues);
    },
    [t, text, node, paths],
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

/** Như `pathLabelCodes` nhưng tên lựa chọn của mẫu nằm trong `ExamName`; tổ hợp và `single` giữ ngoài vùng nội dung đề. */
export function pathLabelCodeNodes(paths: readonly ExamPathRef[], single: string): ReactNode {
  if (paths.length === 0) return "";
  return paths.map((path, pathIndex) => (
    <Fragment key={path.pathId || pathIndex}>
      {pathIndex > 0 && ", "}
      {path.choices.length
        ? path.choices.map((choice, choiceIndex) => (
            <Fragment key={`${choice.partId}-${choice.optionId}-${choiceIndex}`}>
              {choiceIndex > 0 && " + "}
              {choice.kind === "COMBINATION" ? choice.code : <ExamName>{choice.name}</ExamName>}
            </Fragment>
          ))
        : single}
    </Fragment>
  ));
}

/**
 * Liên kết chỉ có icon trong cột Thao tác: cùng cỡ, vòng focus, tooltip với `IconButton` của kit (DESIGN.md 5.9),
 * nhưng là `<Link>` vì dẫn sang trang khác. `label` là phần giao diện của nhãn; `nameId` (id của tên đối tượng đang hiện
 * trên màn, trong `ExamName`) ghép tên vào nhãn bằng `aria-labelledby`.
 */
export function IconLink({
  href,
  label,
  nameId,
  labelAfter,
  tooltip,
  icon,
}: {
  href: string;
  label: string;
  nameId?: string;
  /** Phần giao diện của nhãn đứng sau tên; chỉ dùng khi có `nameId`. */
  labelAfter?: string;
  tooltip?: ReactNode;
  icon: ReactNode;
}) {
  const selfId = useId();
  const afterId = useId();
  const showAfter = Boolean(nameId && labelAfter);
  return (
    <Tooltip content={tooltip ?? label}>
      <Link
        id={selfId}
        href={href}
        aria-label={label}
        aria-labelledby={nameId ? [selfId, nameId, showAfter ? afterId : null].filter(Boolean).join(" ") : undefined}
        className={cn(
          "inline-flex size-10 shrink-0 items-center justify-center rounded-lg text-text-secondary transition-colors duration-150 hover:bg-subtle motion-reduce:transition-none [&_svg]:size-[18px]",
          focusRing,
        )}
      >
        <span aria-hidden className="contents">
          {icon}
        </span>
        {showAfter && (
          <span id={afterId} hidden>
            {labelAfter}
          </span>
        )}
      </Link>
    </Tooltip>
  );
}

type WherePart = { text: string; exam: boolean };

/** Tên các phần tử của cấu trúc hiện tại theo id, để dựng nhãn vị trí thay đổi. `null`: không có tên dùng được. */
function structureNames(
  parts: readonly ExamPart[],
  subjectName: (code: string) => string,
): Record<ChangeLevel, Map<string, WherePart | null>> {
  const names: Record<ChangeLevel, Map<string, WherePart | null>> = {
    part: new Map(),
    section: new Map(),
    block: new Map(),
    option: new Map(),
  };
  const visitBlocks = (blocks: readonly { id: string; name: string }[] | undefined) => {
    for (const block of blocks ?? []) {
      const text = block.name.trim();
      names.block.set(block.id, text ? { text, exam: true } : null);
    }
  };
  const visitSections = (sections: readonly ExamSection[]) => {
    for (const section of sections) {
      names.section.set(section.id, { text: subjectName(section.subjectCode), exam: false });
      visitBlocks(section.blocks);
    }
  };
  for (const part of parts) {
    const partText = part.name.trim();
    names.part.set(part.id, partText ? { text: partText, exam: true } : null);
    visitSections(part.sections);
    for (const option of part.options) {
      names.option.set(
        option.id,
        option.kind === "COMBINATION"
          ? { text: option.code, exam: false }
          : { text: option.name ?? option.code, exam: true },
      );
      visitSections(option.sections);
      visitBlocks(option.blocks);
    }
  }
  return names;
}

function useChangeWhereParts(exam: ExamDetail) {
  const t = useTranslations("exams.versions.changes.where");
  const lookup = useLookup();
  const names = useMemo(() => structureNames(exam.parts, lookup.subject), [exam.parts, lookup]);
  return useCallback(
    (where: string): WherePart[] => {
      const target = parseChangeWhere(where);
      if (!target) return where ? [{ text: where, exam: true }] : [];
      const labels: WherePart[] = target.steps.map((step) =>
        step.id === null
          ? { text: t(`all.${step.level}`), exam: false }
          : (names[step.level].get(step.id) ?? { text: t(`item.${step.level}`), exam: false }),
      );
      // Thông tin bán nằm trong bản chụp ở `config/listing/<trường>`: mở đầu bằng "Thông tin bán" thay vì "Cấu hình bài thi".
      if (target.config) labels.unshift({ text: t(target.fields[0] === "listing" ? "listing" : "config"), exam: false });
      const field = target.fields.find((name) => t.has(`field.${name}`));
      if (field) labels.push({ text: t(`field.${field}`), exam: false });
      return labels.length > 0 ? labels : [{ text: t("structure"), exam: false }];
    },
    [t, names],
  );
}

/**
 * Nhãn dễ đọc cho vị trí của một thay đổi (`ExamChange.where`): tên phần, môn, khối, lựa chọn lấy từ cấu trúc hiện tại,
 * không có thì chữ chung ("Khối"); sau đó là nhãn của trường. Không bao giờ in id. `where` không phải đường dẫn (dữ liệu
 * mẫu đã là chữ mô tả) giữ nguyên.
 */
export function useChangeWhere(exam: ExamDetail) {
  const parts = useChangeWhereParts(exam);
  return useCallback((where: string) => parts(where).map((item) => item.text).join(" · "), [parts]);
}

/** Như `useChangeWhere` nhưng tên phần, khối, lựa chọn của mẫu nằm trong `ExamName`. */
export function useChangeWhereNode(exam: ExamDetail) {
  const parts = useChangeWhereParts(exam);
  return useCallback(
    (where: string): ReactNode =>
      parts(where).map((item, index) => (
        <Fragment key={index}>
          {index > 0 && " · "}
          {item.exam ? <ExamName>{item.text}</ExamName> : item.text}
        </Fragment>
      )),
    [parts],
  );
}
