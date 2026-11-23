"use client";

import { StatusChip } from "@thi360/ui";
import { Headphones, HeadphoneOff } from "lucide-react";
import { useTranslations } from "next-intl";
import type { AudioCountQuery } from "../api";
import { ratePercent } from "../allocation";
import { groupCountScope } from "../mapid-rules";
import type { ExamSection, QuestionBlock } from "../types";

/** Điều kiện audio của phần: chỉ `REQUIRED` và `FORBIDDEN` có nhãn; `ANY` hoặc bỏ trống là phần không phân biệt audio. */
export function sectionAudio(section: ExamSection): "REQUIRED" | "FORBIDDEN" | null {
  return section.audioRequirement === "REQUIRED" || section.audioRequirement === "FORBIDDEN" ? section.audioRequirement : null;
}

export function blockAvailableInput(block: QuestionBlock, section: ExamSection, subjectCode: string): AudioCountQuery {
  const quotaTypes = section.answerQuotas?.items.map((item) => item.answerType) ?? [];
  return {
    subjectCode,
    questionType: block.questionType,
    answerTypes: block.questionType === null ? quotaTypes : [],
    childAnswerType: block.childAnswerType,
    knowledgeScope: groupCountScope(block, section),
    tagCodes: block.tagCodes,
    requiredPointsPerQuestion: block.requiredPointsPerQuestion,
    itemShape: block.itemShape,
    answerFormats: block.answerFormats,
    childConditions: block.childConditions,
    audioRequirement: section.audioRequirement,
  };
}

/** Tỷ lệ của dòng trong phần (%), làm tròn một chữ số; `null` khi phần chưa có tổng hoặc dòng chưa có số câu. */
export function blockShare(count: number | null, partTotal: number | null): number | null {
  const total = partTotal !== null && partTotal > 0 ? partTotal : null;
  return total !== null && count !== null && count > 0 ? ratePercent(count, total) : null;
}

/** Nhãn audio của phần: biểu tượng tai nghe và chip "Có audio" hoặc "Không có audio". */
export function AudioChip({ audio }: { audio: "REQUIRED" | "FORBIDDEN" }) {
  const t = useTranslations("exams.structure.blockForm.rows");
  return (
    <span className="inline-flex items-center gap-2">
      {audio === "REQUIRED" ? <Headphones aria-hidden className="size-4 text-info" /> : <HeadphoneOff aria-hidden className="size-4 text-text-muted" />}
      <StatusChip tone={audio === "REQUIRED" ? "info" : "neutral"}>{t(`audio.${audio}`)}</StatusChip>
    </span>
  );
}
