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
  template?: ExamTemplate | null;
  lockedOmittedAsText?: boolean;
}

export function BlockSwitches({ block, onChange, template = null, lockedOmittedAsText = false }: BlockSwitchesProps) {
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
      {block.questionType === "ESSAY" && omittedSwitchLocked && lockedOmittedAsText && (
        <p className="flex flex-col gap-0.5 rounded-md border border-border p-4" data-omitted-locked>
          <span className="text-label font-semibold text-text">{t("omitted.label")}</span>
          <span className="text-caption text-text-muted">{t("omitted.lockedReason")}</span>
        </p>
      )}
      {block.questionType === "ESSAY" && !(omittedSwitchLocked && lockedOmittedAsText) && (
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
