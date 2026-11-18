"use client";

import {
  Button,
  Checkbox,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  FormField,
} from "@thi360/ui";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import type { ContentReference, QuestionType } from "@/features/content/types";
import { roundForDisplay } from "../display-number";
import type { ItemShape, QuestionBlock } from "../types";
import { blockFieldAnchor, type BlockField } from "./block-form-fields";
import { ANSWER_FORMATS, CONDITION_KINDS, conditionState, sourceType, type ConditionKind } from "./block-form";
import { BlockNumberInput } from "./block-number-input";
import { BlockTagPicker } from "./block-tag-picker";
import { OptionalNumberInput } from "./create-exam-fields";

export interface BlockConditionsProps {
  block: QuestionBlock;
  reference: ContentReference | undefined;
  onChange: (patch: Partial<QuestionBlock>) => void;
  /** Menu "Thêm điều kiện" → MAPID: MAPID là phạm vi kiến thức của nhóm, nên chuyển sang trường đó. */
  onAddMapid: () => void;
}

/** Điều kiện đang hiện: có giá trị, hoặc vừa thêm và chưa điền. MAPID nằm ở trường Phạm vi kiến thức, không ở đây. */
function initialShown(block: QuestionBlock): Set<ConditionKind> {
  const none = new Set<ConditionKind>();
  return new Set(CONDITION_KINDS.filter((kind) => kind !== "MAPID" && conditionState(block, kind, none) === "added"));
}

/**
 * "Điều kiện của nhóm" (2B 15.3): menu "Thêm điều kiện" gồm MAPID, nhãn (chọn nhiều, khớp một trong các nhãn, H22),
 * hình dạng câu, định dạng đáp án, điểm mỗi câu. Mỗi điều kiện thêm và bỏ riêng; mọi câu của nhóm phải thỏa các điều kiện.
 * Điều kiện không áp cho loại câu của nhóm (hình dạng câu, định dạng đáp án) bị khóa trong menu.
 */
export function BlockConditions({ block, reference, onChange, onAddMapid }: BlockConditionsProps) {
  const t = useTranslations("exams.structure.blockForm.conditions");
  const [shown, setShown] = useState(() => initialShown(block));
  const type = sourceType(block);
  const questionCount = block.questionCount ?? 0;
  const anchor = (field: BlockField) => blockFieldAnchor(block.id, field);

  const stateOf = (kind: ConditionKind) => conditionState(block, kind, shown);
  const visible = CONDITION_KINDS.filter((kind) => kind !== "MAPID" && stateOf(kind) === "added");

  const add = (kind: ConditionKind) => {
    if (kind === "MAPID") {
      onAddMapid();
      return;
    }
    setShown((current) => new Set(current).add(kind));
  };
  const remove = (kind: ConditionKind) => {
    setShown((current) => {
      const next = new Set(current);
      next.delete(kind);
      return next;
    });
    if (kind === "TAGS") onChange({ tagCodes: [] });
    else if (kind === "ITEM_SHAPE") onChange({ itemShape: undefined });
    else if (kind === "ANSWER_FORMATS") onChange({ answerFormats: undefined });
    else if (kind === "POINTS") onChange({ requiredPointsPerQuestion: null });
  };

  const unavailableHint = (kind: ConditionKind) => (kind === "ITEM_SHAPE" ? t("unavailable.ITEM_SHAPE") : t("unavailable.ANSWER_FORMATS"));

  return (
    <section className="flex flex-col gap-3 rounded-md border border-border p-4" aria-label={t("title")}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h3 className="text-label font-semibold text-text">{t("title")}</h3>
          <p className="text-caption text-text-muted">{t("hint")}</p>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button type="button" variant="secondary" size="sm" data-field={anchor("conditions")}>
              <Plus />
              <span>{t("add")}</span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent aria-label={t("menuLabel")} className="w-80 max-w-[calc(100vw-2rem)]">
            {CONDITION_KINDS.map((kind) => {
              const state = stateOf(kind);
              return (
                <DropdownMenuItem
                  key={kind}
                  disabled={state !== "available"}
                  className="h-auto min-h-10 flex-col items-start gap-0 py-1.5"
                  onSelect={() => add(kind)}
                >
                  <span>{t(`kind.${kind}`)}</span>
                  <span className="text-caption font-normal text-text-muted">
                    {state === "added" ? t("added") : state === "unavailable" ? unavailableHint(kind) : t(`kindHint.${kind}`)}
                  </span>
                </DropdownMenuItem>
              );
            })}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {visible.length === 0 && <p className="text-body text-text-muted">{t("empty")}</p>}

      {visible.map((kind) => (
        <div key={kind} className="flex flex-col gap-2 rounded-md border border-border bg-subtle/40 p-3" data-condition={kind}>
          <div className="flex items-center justify-between gap-2">
            <h4 className="text-label font-semibold text-text">{t(`kind.${kind}`)}</h4>
            <Button type="button" variant="link" size="sm" aria-label={t("remove", { kind: t(`kind.${kind}`) })} onClick={() => remove(kind)}>
              {t("removeLabel")}
            </Button>
          </div>
          {kind === "TAGS" && (
            <BlockTagPicker
              value={block.tagCodes}
              onChange={(tagCodes) => onChange({ tagCodes })}
              options={reference?.tags ?? []}
              scopeName={block.name || t("groupName")}
              anchor={anchor("tags")}
            />
          )}
          {kind === "ITEM_SHAPE" && <ItemShapeFields block={block} type={type} onChange={onChange} anchor={anchor("itemShape")} />}
          {kind === "ANSWER_FORMATS" && (
            <AnswerFormatFields
              value={block.answerFormats ?? []}
              onChange={(answerFormats) => onChange({ answerFormats: answerFormats.length > 0 ? answerFormats : undefined })}
              anchor={anchor("answerFormats")}
            />
          )}
          {kind === "POINTS" && (
            <FormField
              label={t("points.label")}
              hint={
                block.requiredPointsPerQuestion === null
                  ? t("points.openHint")
                  : t("points.total", {
                      points: roundForDisplay(block.requiredPointsPerQuestion),
                      total: roundForDisplay(questionCount * block.requiredPointsPerQuestion),
                    })
              }
            >
              {(controlProps) => (
                <OptionalNumberInput
                  {...controlProps}
                  data-field={anchor("points")}
                  value={block.requiredPointsPerQuestion}
                  onValueChange={(requiredPointsPerQuestion) => onChange({ requiredPointsPerQuestion })}
                />
              )}
            </FormField>
          )}
        </div>
      ))}
    </section>
  );
}

function ItemShapeFields({
  block,
  type,
  onChange,
  anchor,
}: {
  block: QuestionBlock;
  type: QuestionType | null;
  onChange: (patch: Partial<QuestionBlock>) => void;
  anchor: string;
}) {
  const t = useTranslations("exams.structure.blockForm.conditions.itemShape");
  const shape = block.itemShape ?? {};
  const set = (key: "statements" | "leftItems" | "rightItems", value: number) => {
    const next: ItemShape = { ...shape, [key]: value };
    if (value <= 0) delete next[key];
    onChange({ itemShape: Object.keys(next).length > 0 ? next : undefined });
  };
  const fields: Array<{ key: "statements" | "leftItems" | "rightItems"; label: string }> =
    type === "TRUE_FALSE"
      ? [{ key: "statements", label: t("statements") }]
      : [
          { key: "leftItems", label: t("leftItems") },
          { key: "rightItems", label: t("rightItems") },
        ];
  return (
    <div className="flex flex-col gap-2">
      <div className="grid grid-cols-[repeat(auto-fill,minmax(min(12rem,100%),1fr))] gap-3">
        {fields.map((field, index) => (
          <FormField
            key={field.key}
            label={field.label}
            error={(shape[field.key] ?? 0) > 0 ? undefined : t("required")}
          >
            {(controlProps) => (
              <BlockNumberInput
                {...controlProps}
                data-field={index === 0 ? anchor : undefined}
                value={shape[field.key] ?? 0}
                onValueChange={(value) => set(field.key, value)}
              />
            )}
          </FormField>
        ))}
      </div>
      <p className="text-caption text-text-muted">{type === "TRUE_FALSE" ? t("noteTrueFalse") : t("noteMatching")}</p>
    </div>
  );
}

function AnswerFormatFields({
  value,
  onChange,
  anchor,
}: {
  value: readonly string[];
  onChange: (formats: string[]) => void;
  anchor: string;
}) {
  const t = useTranslations("exams.structure.blockForm.conditions.answerFormats");
  const groupId = useId();
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="sr-only">{t("legend")}</legend>
      <div className="flex flex-wrap gap-x-5 gap-y-2">
        {ANSWER_FORMATS.map((format, index) => {
          const id = `${groupId}-${format}`;
          return (
            <div key={format} className="flex items-center gap-2">
              <Checkbox
                id={id}
                data-field={index === 0 ? anchor : undefined}
                checked={value.includes(format)}
                onCheckedChange={(checked) =>
                  onChange(
                    checked === true
                      ? ANSWER_FORMATS.filter((item) => item === format || value.includes(item))
                      : value.filter((item) => item !== format),
                  )
                }
              />
              <label htmlFor={id} className="cursor-pointer text-body text-text">
                {t(`format.${format}`)}
              </label>
            </div>
          );
        })}
      </div>
      <p className="text-caption text-text-muted">{t("note")}</p>
    </fieldset>
  );
}
