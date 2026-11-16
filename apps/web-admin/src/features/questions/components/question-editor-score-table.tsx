"use client";

import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
  Input,
} from "@thi360/ui";
import { ChevronDown, Scale } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useEffect, useId, useRef } from "react";
import { useFormContext, useWatch } from "react-hook-form";
import { useContentReference } from "@/features/content/queries";
import {
  DEFAULT_POINTS,
  evenScoreTableText,
  formatPoints,
  parsePoints,
  samePoints,
  scaleScoreTable,
  type QuestionEditorValues,
} from "../editor-form";
import { FieldErrorText, pointsPathOf, useAnnounce, useEditorError, type BodyPath } from "./question-editor-shared";

/**
 * Bảng điểm câu Đúng/Sai theo số ý đúng (quyết định UNI-2599 mục 2.2): ô thứ i là điểm khi học viên đúng i ý. Bảng có
 * số ô bằng số ý; thêm, xóa ý thì bảng chia đều lại. Đổi điểm của câu thì bảng nhân theo tỉ lệ (khi ô cuối đang bằng
 * điểm cũ). "Theo mẫu kỳ thi…" nạp bảng mặc định của kỳ thi (`examKinds[].scoringDefaults.trueFalseTable`, tính theo
 * câu 1 điểm) và đổi theo điểm của câu.
 */
export function ScoreTableEditor({ prefix }: { prefix: BodyPath }) {
  const t = useTranslations("questions.editor.scoreTable");
  const format = useFormatter();
  const announce = useAnnounce();
  const groupId = useId();
  const pointsPath = pointsPathOf(prefix);
  const { control, getValues, setValue, formState, trigger } = useFormContext<QuestionEditorValues>();
  const statements = useWatch({ control, name: `${prefix}.statements` }) ?? [];
  const pointsText = useWatch({ control, name: pointsPath }) ?? "";
  const points = parsePoints(pointsText);
  const referenceQuery = useContentReference();
  const templates = (referenceQuery.data?.examKinds ?? []).flatMap((kind) =>
    kind.scoringDefaults.trueFalse.table.length ? [{ code: kind.code, name: kind.name, table: kind.scoringDefaults.trueFalse.table }] : [],
  );
  const count = statements.length;

  const applyTable = (values: string[], message: string) => {
    setValue(`${prefix}.scoreTable`, values, { shouldDirty: true });
    if (formState.isSubmitted) void trigger(`${prefix}.scoreTable`);
    announce(message);
  };

  // Điểm của câu đổi: bảng đang khớp điểm cũ thì nhân theo tỉ lệ để ô cuối vẫn bằng điểm của câu.
  const previousPoints = useRef(points);
  useEffect(() => {
    // Ô Điểm đang gõ dở (trống, chưa phải số) thì giữ điểm hợp lệ trước đó để so khi gõ xong.
    if (!(points > 0)) return;
    const before = previousPoints.current;
    previousPoints.current = points;
    if (!(before > 0) || samePoints(before, points)) return;
    const current = getValues(`${prefix}.scoreTable`).map(parsePoints);
    const last = current.at(-1);
    if (last === undefined || current.some((value) => !Number.isFinite(value)) || !samePoints(last, before)) return;
    setValue(`${prefix}.scoreTable`, scaleScoreTable(current, points).map(formatPoints), { shouldDirty: true });
  }, [points, prefix, getValues, setValue]);

  const basePoints = points > 0 ? points : DEFAULT_POINTS;

  return (
    <fieldset className="m-0 flex min-w-0 flex-col gap-3 rounded-lg border border-border p-3" aria-describedby={`${groupId}-hint`}>
      <legend className="px-1 text-label font-semibold text-text">{t("legend")}</legend>
      <p id={`${groupId}-hint`} className="text-caption text-text-muted">
        {t("hint", { points: points > 0 ? format.number(points) : "?" })}
      </p>
      <ol className="m-0 grid list-none grid-cols-2 gap-2 p-0 @md:grid-cols-4">
        {Array.from({ length: count }, (_, index) => (
          <ScoreCell key={index} prefix={prefix} index={index} />
        ))}
      </ol>
      <div className="flex flex-wrap items-center gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={count === 0}
          onClick={() => applyTable(evenScoreTableText(count, basePoints), t("evenApplied"))}
        >
          <Scale aria-hidden />
          {t("even")}
        </Button>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button type="button" variant="outline" size="sm" disabled={templates.length === 0 || count === 0}>
              {t("fromExamKind")}
              <ChevronDown aria-hidden />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            <DropdownMenuLabel>{t("fromExamKindLabel", { count })}</DropdownMenuLabel>
            {templates.map((template) => {
              const fits = template.table.length === count;
              const values = template.table.map((value) => format.number(value)).join(" / ");
              return (
                <DropdownMenuItem
                  key={template.code}
                  disabled={!fits}
                  onSelect={() =>
                    applyTable(
                      scaleScoreTable(template.table, basePoints).map(formatPoints),
                      t("templateApplied", { kind: template.code }),
                    )
                  }
                >
                  <span className="flex flex-col">
                    <span>{t("templateItem", { kind: template.code, values })}</span>
                    {!fits && (
                      <span className="text-caption text-text-muted">
                        {t("templateNeeds", { count: template.table.length })}
                      </span>
                    )}
                  </span>
                </DropdownMenuItem>
              );
            })}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </fieldset>
  );
}

function ScoreCell({ prefix, index }: { prefix: BodyPath; index: number }) {
  const t = useTranslations("questions.editor.scoreTable");
  const { register } = useFormContext<QuestionEditorValues>();
  const inputId = useId();
  const error = useEditorError(`${prefix}.scoreTable.${index}`);
  return (
    <li className="flex flex-col gap-1">
      <label htmlFor={inputId} className="text-caption font-medium text-text-secondary">
        {t("cellLabel", { count: index + 1 })}
      </label>
      <Input
        id={inputId}
        inputMode="decimal"
        autoComplete="off"
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${inputId}-error` : undefined}
        {...register(`${prefix}.scoreTable.${index}`)}
      />
      <FieldErrorText id={`${inputId}-error`} message={error} />
    </li>
  );
}
