"use client";

import { Button, Checkbox, cn } from "@thi360/ui";
import { Lock, Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useId, useMemo, useRef, useState } from "react";
import { useFieldArray, useFormContext, useWatch } from "react-hook-form";
import { questionHtmlText } from "@/components/rich-text";
import { choiceHtmlOfPhrase, phraseTextByLabel } from "@/components/rich-text/insert/underline-label/labels";
import { MULTIPLE_CHOICE_RULES } from "@/features/content/types";
import { emptyOption, isDraftRowId, type QuestionEditorValues } from "../editor-form";
import {
  FieldErrorText,
  GroupError,
  optionLetter,
  RowActions,
  useAnnounce,
  useEditorError,
  type BodyPath,
  type ContentPath,
} from "./question-editor-shared";
import { RichTextField } from "./question-editor-rich-field";

/**
 * Phương án tự lấy theo các cụm gạch chân (spec 4.8; chỉ Trắc nghiệm 1 đáp án, trạng thái không lưu thành trường
 * riêng): bật khi tạo cụm thứ hai trong câu mới, và khi mở câu đã lưu chỉ bật nếu mọi phương án đang có đúng chữ của cụm
 * cùng nhãn (phương án thứ n ↔ nhãn thứ n: A, B, C…). Bật thì phương án lấy chữ thuần của cụm và theo kịp khi sửa cụm.
 */
function useAutoChoices(prefix: BodyPath, multiple: boolean) {
  const { control, getValues, setValue, formState, trigger } = useFormContext<QuestionEditorValues>();
  const contentPath = prefix.replace(/body$/, "content") as ContentPath;
  const stem = useWatch({ control, name: contentPath });
  const options = useWatch({ control, name: `${prefix}.options` }) ?? [];
  const phraseByLabel = useMemo(
    () => (multiple || typeof stem !== "string" ? new Map<string, string>() : phraseTextByLabel(stem)),
    [multiple, stem],
  );
  const [isNewQuestion] = useState(() => options.every((option) => isDraftRowId(option.id)));
  const [auto, setAuto] = useState(
    () =>
      !multiple &&
      options.length > 0 &&
      options.every((option, index) => phraseByLabel.get(optionLetter(index)) === questionHtmlText(option.content).trim()),
  );
  const phraseCount = phraseByLabel.size;
  const previousCount = useRef(phraseCount);
  useEffect(() => {
    if (!multiple && isNewQuestion && previousCount.current < 2 && phraseCount >= 2) setAuto(true);
    previousCount.current = phraseCount;
  }, [multiple, isNewQuestion, phraseCount]);

  const optionCount = options.length;
  useEffect(() => {
    if (!auto) return;
    getValues(`${prefix}.options`).forEach((option, index) => {
      const text = phraseByLabel.get(optionLetter(index));
      if (text === undefined || questionHtmlText(option.content).trim() === text) return;
      setValue(`${prefix}.options.${index}.content`, choiceHtmlOfPhrase(text), { shouldDirty: true });
    });
    if (formState.isSubmitted) void trigger(`${prefix}.options`);
  }, [auto, phraseByLabel, optionCount, prefix, getValues, setValue, trigger, formState.isSubmitted]);

  const missing = auto ? options.flatMap((_, index) => (phraseByLabel.has(optionLetter(index)) ? [] : [optionLetter(index)])) : [];
  const extra = auto ? [...phraseByLabel.keys()].filter((label) => label.charCodeAt(0) - 65 >= optionCount) : [];
  return { auto, setAuto, phraseByLabel, visible: !multiple && (phraseCount > 0 || auto), missing, extra };
}

/**
 * Phương án lấy theo vị trí (A, B, C…) nên đổi chỗ hoặc xóa phương án đang theo cụm sẽ đẩy chữ cụm sang hàng khác và đổi
 * đáp án đúng mà không ai thấy: khóa nút (kèm lý do) thay vì làm im lặng. Đổi chỗ bị khóa khi cả hai phương án đều theo cụm;
 * xóa bị khóa khi phương án đó hoặc một phương án sau nó theo cụm (các hàng sau sẽ dồn lên).
 */
function autoLocks(
  index: number,
  count: number,
  phraseByLabel: ReadonlyMap<string, string>,
  moveReason: string,
  removeReason: string,
): { up?: string; down?: string; remove?: string } {
  const bound = (at: number) => phraseByLabel.has(optionLetter(at));
  const locks: { up?: string; down?: string; remove?: string } = {};
  if (bound(index) && index > 0 && bound(index - 1)) locks.up = moveReason;
  if (bound(index) && index < count - 1 && bound(index + 1)) locks.down = moveReason;
  for (let at = index; at < count; at += 1) {
    if (bound(at)) {
      locks.remove = removeReason;
      break;
    }
  }
  return locks;
}

/**
 * Phương án của Trắc nghiệm 1 đáp án (ô tròn, đúng một đáp án) và nhiều đáp án (ô vuông, ít nhất một),
 * canvas `CauHoi1DapAnForm`, `CauHoiNhieuDapAnForm`. Đổi thứ tự bằng nút lên/xuống thay cho tay kéo của bản vẽ.
 * Nhiều đáp án có thêm cách chấm: đúng hết mới có điểm (mặc định) hoặc chấm từng phần (UNI-2599 mục 2.3).
 */
export function ChoiceOptionsEditor({ prefix, multiple }: { prefix: BodyPath; multiple: boolean }) {
  const t = useTranslations("questions.editor.choice");
  const announce = useAnnounce();
  const groupId = useId();
  const addRef = useRef<HTMLButtonElement>(null);
  const { control, setValue, getValues, formState, trigger } = useFormContext<QuestionEditorValues>();
  const { fields, append, remove, move } = useFieldArray({
    control,
    name: `${prefix}.options`,
    keyName: "rowKey",
  });
  const options = useWatch({ control, name: `${prefix}.options` }) ?? [];
  const correctCount = options.filter((option) => option.correct).length;
  const groupError = useEditorError(`${prefix}.options`);
  const autoChoices = useAutoChoices(prefix, multiple);
  const { auto, phraseByLabel } = autoChoices;

  const chooseSingle = (index: number) => {
    getValues(`${prefix}.options`).forEach((_, other) => {
      setValue(`${prefix}.options.${other}.correct`, other === index, { shouldDirty: true });
    });
    if (formState.isSubmitted) void trigger(`${prefix}.options`);
    announce(t("correctChosen", { letter: optionLetter(index) }));
  };

  return (
    <>
      <fieldset className="m-0 flex min-w-0 flex-col gap-3 border-0 p-0" aria-describedby={`${groupId}-hint`}>
        <legend className="sr-only">{multiple ? t("legendMultiple") : t("legendSingle")}</legend>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p id={`${groupId}-hint`} className="text-body text-text-muted">
            {multiple ? t("hintMultiple") : t("hintSingle")}
          </p>
          {multiple && (
            <span className="text-caption font-medium text-text-secondary">{t("correctCount", { count: correctCount })}</span>
          )}
        </div>
        <GroupError id={`${groupId}-error`} message={groupError} />
        <ol className="m-0 flex list-none flex-col gap-2 p-0">
          {fields.map((field, index) => (
            <OptionRow
              key={field.rowKey}
              prefix={prefix}
              index={index}
              count={fields.length}
              multiple={multiple}
              correct={options[index]?.correct ?? false}
              lockedText={auto ? phraseByLabel.get(optionLetter(index)) : undefined}
              locked={auto ? autoLocks(index, fields.length, phraseByLabel, t("autoMoveLocked"), t("autoRemoveLocked")) : undefined}
              groupName={groupId}
              onChooseSingle={() => chooseSingle(index)}
              onMove={(from, to) => {
                move(from, to);
                announce(t("moved", { letter: optionLetter(from), position: to + 1 }));
              }}
              onRemove={() => {
                remove(index);
                announce(t("removed", { letter: optionLetter(index) }));
                addRef.current?.focus();
              }}
            />
          ))}
        </ol>
        {(autoChoices.missing.length > 0 || autoChoices.extra.length > 0) && (
          <p role="status" className="text-caption text-warning">
            {[
              autoChoices.missing.length > 0 && t("autoMissing", { letters: autoChoices.missing.join(", ") }),
              autoChoices.extra.length > 0 && t("autoExtra", { labels: autoChoices.extra.join(", ") }),
            ]
              .filter(Boolean)
              .join(" ")}
          </p>
        )}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Button
            ref={addRef}
            type="button"
            variant="outline"
            size="sm"
            onClick={() => {
              append(emptyOption(), { shouldFocus: true, focusName: `${prefix}.options.${fields.length}.content` });
              announce(t("added", { letter: optionLetter(fields.length) }));
            }}
          >
            <Plus aria-hidden />
            {t("add")}
          </Button>
          {autoChoices.visible && (
            <label className="flex min-h-10 items-center gap-2 text-body text-text">
              <Checkbox checked={auto} onCheckedChange={(checked) => autoChoices.setAuto(checked === true)} />
              {t("autoLabel")}
            </label>
          )}
        </div>
      </fieldset>
      {multiple && <ScoringRuleField prefix={prefix} />}
    </>
  );
}

/** Cách chấm câu nhiều đáp án: hai lựa chọn hiện sẵn (radio), mỗi lựa chọn kèm giải thích ngắn. */
function ScoringRuleField({ prefix }: { prefix: BodyPath }) {
  const t = useTranslations("questions.editor.scoringRule");
  const announce = useAnnounce();
  const groupId = useId();
  const { register } = useFormContext<QuestionEditorValues>();
  const field = register(`${prefix}.scoringRule`);
  return (
    <fieldset className="m-0 flex min-w-0 flex-col gap-2 rounded-lg border border-border p-3">
      <legend className="px-1 text-label font-semibold text-text">{t("legend")}</legend>
      {MULTIPLE_CHOICE_RULES.map((rule) => (
        <div key={rule} className="flex items-start gap-2.5">
          <input
            id={`${groupId}-${rule}-input`}
            type="radio"
            value={rule}
            className="mt-3 size-4 shrink-0 accent-primary-fill"
            aria-describedby={`${groupId}-${rule}`}
            {...field}
            onChange={(event) => {
              void field.onChange(event);
              announce(t("chosen", { rule: t(`${rule}.label`) }));
            }}
          />
          <div className="flex flex-col">
            <label htmlFor={`${groupId}-${rule}-input`} className="flex min-h-10 items-center text-body font-medium text-text">
              {t(`${rule}.label`)}
            </label>
            <p id={`${groupId}-${rule}`} className="text-caption text-text-muted">
              {t(`${rule}.description`)}
            </p>
          </div>
        </div>
      ))}
    </fieldset>
  );
}

function OptionRow({
  prefix,
  index,
  count,
  multiple,
  correct,
  lockedText,
  locked,
  groupName,
  onChooseSingle,
  onMove,
  onRemove,
}: {
  prefix: BodyPath;
  index: number;
  count: number;
  multiple: boolean;
  correct: boolean;
  /** Phương án đang lấy chữ từ cụm gạch chân: hiện chữ cụm, không soạn tay. */
  lockedText?: string;
  /** Lý do khóa từng nút lên/xuống/xóa khi phương án đang lấy theo cụm gạch chân. */
  locked?: { up?: string; down?: string; remove?: string };
  groupName: string;
  onChooseSingle: () => void;
  onMove: (from: number, to: number) => void;
  onRemove: () => void;
}) {
  const t = useTranslations("questions.editor.choice");
  const { setValue, formState, trigger } = useFormContext<QuestionEditorValues>();
  const letter = optionLetter(index);
  const inputId = useId();
  const error = useEditorError(`${prefix}.options.${index}.content`);
  return (
    <li
      className={cn(
        "flex flex-col gap-1.5 rounded-lg border p-2.5",
        correct ? "border-primary-600 bg-primary-50" : "border-border bg-surface",
      )}
    >
      <div className="flex flex-wrap items-center gap-2 @md:flex-nowrap">
        <label className="flex min-h-10 shrink-0 items-center gap-2 font-semibold text-text">
          <input
            type={multiple ? "checkbox" : "radio"}
            name={`${groupName}-correct`}
            checked={correct}
            onChange={(event) => {
              if (multiple) {
                setValue(`${prefix}.options.${index}.correct`, event.target.checked, { shouldDirty: true });
                if (formState.isSubmitted) void trigger(`${prefix}.options`);
              } else {
                onChooseSingle();
              }
            }}
            aria-label={t("correctLabel", { letter })}
            className="size-4 accent-primary-fill"
          />
          <span aria-hidden>{letter}.</span>
        </label>
        <div className="min-w-0 flex-1 basis-48">
          {lockedText !== undefined ? (
            <div
              role="group"
              aria-label={t("contentLabel", { letter })}
              className="flex min-h-10 flex-wrap items-center gap-2 rounded-md border border-border bg-subtle px-3 py-1.5 text-body text-text"
            >
              <span className="min-w-0 flex-1 wrap-anywhere">{lockedText}</span>
              <span className="inline-flex items-center gap-1 text-caption text-text-muted">
                <Lock aria-hidden className="size-3.5" />
                {t("fromPhrase")}
              </span>
            </div>
          ) : (
            <RichTextField
              id={inputId}
              aria-label={t("contentLabel", { letter })}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? `${inputId}-error` : undefined}
              name={`${prefix}.options.${index}.content`}
            />
          )}
        </div>
        <RowActions index={index} count={count} name={t("rowName", { letter })} locked={locked} onMove={onMove} onRemove={onRemove} />
      </div>
      {correct && <span className="ps-7 text-caption font-medium text-primary-text">{t("correctBadge")}</span>}
      <FieldErrorText id={`${inputId}-error`} message={error} />
    </li>
  );
}
