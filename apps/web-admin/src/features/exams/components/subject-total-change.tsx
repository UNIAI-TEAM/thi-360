"use client";

import { useTranslations } from "next-intl";
import { useRef, type ReactNode, type RefObject } from "react";
import type { QuestionType } from "@/features/content/types";
import { applyTopicTotal, convertTopicEntry, topicCountFor, totalChangePending } from "../allocation";
import { apportionByPercent, computeAllocation, convertMatrixUnit, type MatrixAxes } from "../matrix";
import { convertDeclaredDimensions } from "../subject-forms/matrix-form-model";
import type { AnswerQuotas, ExamSection } from "../types";
import type { MatrixLabels } from "./block-matrix-editor";
import { TotalChangeNotice } from "./subject-allocation-extras";

/** Chỉ tiêu dạng trả lời nhập theo tỷ lệ quy về số câu của tổng `total` (phần dư lớn nhất); nhập theo số câu giữ nguyên. */
function toCountQuotas(quotas: AnswerQuotas | null, total: number): AnswerQuotas | null {
  if (quotas?.unit !== "PERCENT") return quotas;
  const counts = apportionByPercent(
    quotas.items.map((item) => item.value),
    total,
  ).results;
  return { unit: "COUNT", items: quotas.items.map((item, index) => ({ ...item, value: counts[index] ?? 0 })) };
}

export interface SubjectTotalChangeOptions {
  section: ExamSection;
  axes: MatrixAxes;
  labels: MatrixLabels;
  /** Số câu của mục đã lưu hoặc đã xác nhận gần nhất, để phát hiện tổng đổi (R-121). */
  confirmedCount: number | null;
  onConfirmCount?: (count: number) => void;
  onChange: (update: (section: ExamSection) => ExamSection) => void;
  /**
   * "Giữ số cũ" đổi ma trận theo tỷ lệ về số câu: màn MD đổi mọi ô của hai chiều (chiều chưa khai thành ô 0); biểu mẫu MT chỉ đổi ô đã khai,
   * chiều chưa khai giữ mảng rỗng (R-4, R-8). Mặc định giữ cách của màn MD.
   */
  declaredOnly?: boolean;
}

export interface SubjectTotalChange {
  /** Tổng đã đổi mà bảng nhập theo tỷ lệ chưa được xác nhận. */
  totalChanged: boolean;
  /** Thông báo "Tổng đổi thành n câu" với hai nút Áp dụng số mới, Giữ số cũ; `null` khi không có gì chờ xác nhận. */
  notice: ReactNode;
  /** Gắn vào khối chứa ô nhập đầu tiên của môn: hai nút của thông báo biến mất khi bấm nên tiêu điểm về đó (R1-26). */
  countRef: RefObject<HTMLDivElement | null>;
}

/**
 * Tổng của mục đổi (FTM R-9 "Đổi tổng", R-121): bảng nhập theo tỷ lệ hiện số câu mới và chờ xác nhận, bảng nhập theo số câu giữ số và báo
 * thiếu hoặc vượt. "Áp dụng số mới" tính lại chỉ tiêu chủ đề theo tổng mới; "Giữ số cũ" đổi các bảng tỷ lệ về số câu của tổng cũ. Màn môn
 * mặc định (MD) và biểu mẫu MT dùng chung đúng một cách tính.
 */
export function useSubjectTotalChange({ section, axes, labels, confirmedCount, onConfirmCount, onChange, declaredOnly = false }: SubjectTotalChangeOptions): SubjectTotalChange {
  const t = useTranslations("exams.structure.subject");
  const tContent = useTranslations("content");
  const typeLabel = (type: QuestionType) => tContent(`questionType.${type}`);
  const sectionCount = section.questionCount;
  const quotas = section.answerQuotas;
  const topicQuotas = section.topicQuotas ?? [];
  const totalChanged = totalChangePending(section, confirmedCount);
  const noticeLines: string[] = [];
  if (totalChanged && sectionCount !== null) {
    const grade = (code: string) => labels.grade(code);
    const difficulty = (code: string) => labels.difficulty(code);
    if (section.matrix?.unit === "PERCENT") {
      const next = computeAllocation({ questionCount: sectionCount, matrix: section.matrix }, axes);
      noticeLines.push(t("totalChange.grades", { values: axes.grades.map((code, index) => `${grade(code)} ${next.byGrade[index] ?? 0}`).join(" · ") }));
      noticeLines.push(t("totalChange.difficulties", { values: axes.difficulties.map((code, index) => `${difficulty(code)} ${next.byDifficulty[index] ?? 0}`).join(" · ") }));
    }
    if (quotas?.unit === "PERCENT") {
      const next = apportionByPercent(
        quotas.items.map((item) => item.value),
        sectionCount,
      ).results;
      noticeLines.push(t("totalChange.answerTypes", { values: quotas.items.map((item, index) => `${typeLabel(item.answerType)} ${next[index] ?? 0}`).join(" · ") }));
    }
    if (topicQuotas.some((quota) => quota.percent !== undefined && quota.percent !== null)) {
      noticeLines.push(
        t("totalChange.topics", {
          values: topicQuotas.map((quota) => `${quota.name || t("totalChange.untitled")} ${topicCountFor(quota, sectionCount)}`).join(" · "),
        }),
      );
    }
  }
  // Hai nút của thông báo biến mất khi bấm: đưa tiêu điểm về ô số câu để người dùng bàn phím không mất chỗ (R1-26).
  const countRef = useRef<HTMLDivElement>(null);
  const focusCount = () => requestAnimationFrame(() => countRef.current?.querySelector("input")?.focus());
  const applyNewTotal = () => {
    if (sectionCount === null) return;
    onChange((current) => ({ ...current, topicQuotas: applyTopicTotal(current.topicQuotas ?? [], sectionCount) }));
    onConfirmCount?.(sectionCount);
    focusCount();
  };
  const keepOldTotal = () => {
    if (sectionCount === null || confirmedCount === null) return;
    onChange((current) => ({
      ...current,
      matrix:
        current.matrix?.unit === "PERCENT"
          ? declaredOnly
            ? convertDeclaredDimensions(current.matrix, "COUNT", confirmedCount)
            : convertMatrixUnit(current.matrix, "COUNT", confirmedCount, axes)
          : current.matrix,
      answerQuotas: toCountQuotas(current.answerQuotas, confirmedCount),
      topicQuotas: convertTopicEntry(applyTopicTotal(current.topicQuotas ?? [], confirmedCount), "COUNT", confirmedCount),
    }));
    onConfirmCount?.(sectionCount);
    focusCount();
  };

  const notice =
    totalChanged && sectionCount !== null && confirmedCount !== null ? (
      <TotalChangeNotice oldTotal={confirmedCount} newTotal={sectionCount} lines={noticeLines} onApply={applyNewTotal} onKeep={keepOldTotal} />
    ) : null;
  return { totalChanged, notice, countRef };
}
