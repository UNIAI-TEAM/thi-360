"use client";

import { Switch } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useId } from "react";
import { isSetBlock } from "../matrix";
import { isTemplateOmittedGroup } from "../omitted-part";
import type { ExamTemplate, QuestionBlock } from "../types";
import { blockFieldAnchor } from "./block-form-fields";

export interface BlockSwitchesProps {
  block: QuestionBlock;
  onChange: (patch: Partial<QuestionBlock>) => void;
  /** Mẫu của bài; khi mẫu đặt `omittedLocked` (H43), bản sao nhóm đã lược của mẫu không bật lại được. */
  template?: ExamTemplate | null;
}

/**
 * Hai công tắc của nhóm (2B 3.3, 15.3). "Đảo thứ tự các chùm" chỉ hiện khi mẫu đặt `shuffleEditable` (Văn HSA và Văn
 * QDA nhóm 5); nhóm khác luôn đảo và chỉ có dòng chú thích. "Tạm bỏ qua" chỉ cho nhóm Tự luận (K1). Khi mẫu khóa nhóm
 * đã lược (`omittedLocked`), chỉ bản sao của nhóm mà chính mẫu đã lược (cùng id, hoặc cùng mã nhóm mẫu duy nhất) đang tạm
 * bỏ qua là không bật lại được: công tắc khóa kèm lý do. Nhóm Tự luận khác, kể cả nhóm có mã nhóm mẫu nhưng mẫu không lược
 * (Đọc hiểu của H-SCA Văn đổi sang Tự luận), vẫn bật tắt tự do. Nhóm đã bật
 * (bản nháp lưu trước khi có khóa) vẫn tắt/bật được để sửa lại. Kiểm tra cấu hình (`OMITTED_BLOCK_LOCKED`) vẫn là lớp
 * chặn cuối cho ghi qua API và bản nháp cũ.
 */
export function BlockSwitches({ block, onChange, template = null }: BlockSwitchesProps) {
  const t = useTranslations("exams.structure.blockForm.switches");
  const shuffleId = useId();
  const omittedId = useId();
  // Cùng điều kiện với `ExamCheckRules.lockedBlockIssues`: chỉ bản sao của nhóm mẫu đã lược mới bị chặn khi bật lại.
  const omittedSwitchLocked = template?.omittedLocked === true && block.omitted === true && isTemplateOmittedGroup(template, block);
  const shuffleEditable = block.shuffleEditable === true;
  const shuffling = block.shuffleUnits !== false;

  return (
    <>
      {shuffleEditable ? (
        <div className="flex items-start justify-between gap-4 rounded-md border border-border p-4">
          <div className="flex min-w-0 flex-col gap-0.5">
            <span id={`${shuffleId}-label`} className="text-label font-semibold text-text">
              {t("shuffle.label")}
            </span>
            <span id={`${shuffleId}-description`} className="text-caption text-text-muted">
              {t("shuffle.description")}
            </span>
          </div>
          <Switch
            checked={shuffling}
            onCheckedChange={(shuffleUnits) => onChange({ shuffleUnits })}
            aria-labelledby={`${shuffleId}-label`}
            aria-describedby={`${shuffleId}-description`}
            data-field={blockFieldAnchor(block.id, "shuffle")}
          />
        </div>
      ) : (
        shuffling && <p className="text-caption text-text-muted">{t(isSetBlock(block) ? "alwaysShuffleSet" : "alwaysShuffle")}</p>
      )}
      {block.questionType === "ESSAY" && (
        <div className="flex items-start justify-between gap-4 rounded-md border border-border p-4">
          <div className="flex min-w-0 flex-col gap-0.5">
            <span id={`${omittedId}-label`} className="text-label font-semibold text-text">
              {t("omitted.label")}
            </span>
            <span id={`${omittedId}-description`} className="text-caption text-text-muted">
              {t("omitted.description")}
            </span>
            {omittedSwitchLocked && (
              <span id={`${omittedId}-locked`} className="text-caption text-text-muted">
                {t("omitted.lockedReason")}
              </span>
            )}
          </div>
          <Switch
            checked={block.omitted === true}
            onCheckedChange={(omitted) => onChange({ omitted })}
            disabled={omittedSwitchLocked}
            aria-labelledby={`${omittedId}-label`}
            aria-describedby={`${omittedId}-description${omittedSwitchLocked ? ` ${omittedId}-locked` : ""}`}
            data-field={blockFieldAnchor(block.id, "omitted")}
          />
        </div>
      )}
    </>
  );
}
