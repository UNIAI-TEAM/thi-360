"use client";

import { Button, Card, cn, PageHeader } from "@thi360/ui";
import {
  ArrowLeftRight,
  ArrowRight,
  Brackets,
  Check,
  CircleDot,
  Layers,
  ListChecks,
  PenLine,
  SquareCheckBig,
  TextCursorInput,
  type LucideIcon,
} from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { MockDataBanner } from "@/components/mock-data-banner";
import { QUESTION_TYPES, type QuestionType } from "@/features/content/types";

const TYPE_ICON: Record<QuestionType, LucideIcon> = {
  SINGLE_CHOICE: CircleDot,
  MULTIPLE_CHOICE: SquareCheckBig,
  TRUE_FALSE: ListChecks,
  SHORT_ANSWER: TextCursorInput,
  CLOZE: Brackets,
  MATCHING: ArrowLeftRight,
  QUESTION_SET: Layers,
  ESSAY: PenLine,
};

export function questionCreateHref(type: QuestionType): string {
  return `/questions/new?type=${type}`;
}

/**
 * Bước chọn dạng câu của `/questions/new` (canvas `CauHoiChonDang`). Bản vẽ là hộp thoại trên danh sách; mockup làm
 * thành trang riêng vì `/questions/new` là một đường dẫn (mở được ở tab mới, nút Back đúng nghĩa). Chọn một trong tám
 * dạng là một nhóm radio (DESIGN.md 5.2), "Tiếp tục" mở biểu mẫu `?type=…`.
 */
export function QuestionTypePicker({ initialType = "SINGLE_CHOICE" }: { initialType?: QuestionType }) {
  const t = useTranslations("questions.editor.typePicker");
  const tType = useTranslations("content.questionType");
  const router = useRouter();
  const groupId = useId();
  const [selected, setSelected] = useState<QuestionType>(initialType);

  return (
    <div className="flex flex-col gap-5">
      <PageHeader title={t("title")} description={t("description")} />
      <MockDataBanner />
      <Card padding="none" className="@container p-4 sm:p-6">
        <form
          className="flex flex-col gap-5"
          onSubmit={(event) => {
            event.preventDefault();
            router.push(questionCreateHref(selected));
          }}
        >
          <fieldset className="m-0 min-w-0 border-0 p-0">
            <legend className="mb-3 text-h3 font-semibold text-text">{t("legend")}</legend>
            <div className="grid gap-3 @xl:grid-cols-2 @4xl:grid-cols-3">
              {QUESTION_TYPES.map((type) => {
                const Icon = TYPE_ICON[type];
                const checked = selected === type;
                return (
                  <label
                    key={type}
                    className={cn(
                      "relative flex min-w-0 cursor-pointer items-start gap-3 rounded-lg border p-3.5 transition-colors duration-150 motion-reduce:transition-none",
                      "has-focus-visible:outline-3 has-focus-visible:outline-primary-text",
                      checked ? "border-primary-600 bg-primary-50" : "border-border bg-surface hover:bg-subtle",
                    )}
                  >
                    <input
                      type="radio"
                      name={`${groupId}-type`}
                      value={type}
                      checked={checked}
                      onChange={() => setSelected(type)}
                      aria-describedby={`${groupId}-${type}-desc`}
                      className="sr-only"
                    />
                    <span
                      aria-hidden
                      className="grid size-10 shrink-0 place-items-center rounded-md bg-primary-100 text-primary-text"
                    >
                      <Icon className="size-5" />
                    </span>
                    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="font-semibold text-text">{tType(type)}</span>
                      <span id={`${groupId}-${type}-desc`} className="text-caption text-text-muted">
                        {t(`descriptions.${type}`)}
                      </span>
                    </span>
                    <Check
                      aria-hidden
                      className={cn("size-[18px] shrink-0 text-primary-text", checked ? "visible" : "invisible")}
                    />
                  </label>
                );
              })}
            </div>
          </fieldset>
          <div className="flex flex-wrap justify-end gap-3">
            <Button asChild variant="secondary">
              <Link href="/questions">{t("cancel")}</Link>
            </Button>
            <Button type="submit">
              {t("continue")}
              <ArrowRight aria-hidden />
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
