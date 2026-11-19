"use client";

import { Card, cn, focusRing } from "@thi360/ui";
import { Question, type AnswerValue } from "@thi360/ui/question";
import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import { useLocale } from "next-intl";
import { useState } from "react";
import { KIT_CHROME, KIT_SAMPLES } from "./samples";

type Mode = "answer" | "review";
type MatchUi = "select" | "pick";

/**
 * Trang thử bộ thành phần câu hỏi `@thi360/ui/question`: đủ 8 dạng, hai chế độ (làm bài, xem lại).
 * Chỉ có khi chạy dev (`page.tsx` gọi `notFound()` ở bản production). Giá trị trả ra hiện dưới từng câu.
 */
export function QuestionKitDemo() {
  const locale = normalizeUiLocale(useLocale());
  const chrome = KIT_CHROME[locale];
  const [mode, setMode] = useState<Mode>("answer");
  const [matchUi, setMatchUi] = useState<MatchUi>("select");
  const [answers, setAnswers] = useState<Record<string, AnswerValue | null>>({});
  const [flags, setFlags] = useState<Record<string, boolean>>({});

  return (
    <main className="mx-auto flex w-full max-w-[1280px] flex-col gap-6 px-6 py-6">
      <header className="flex flex-col gap-2">
        <h1 className="text-page-title font-bold text-text">{chrome.title}</h1>
        <p className="text-body text-text-secondary">{chrome.intro}</p>
      </header>
      <div className="flex flex-wrap items-center gap-x-8 gap-y-3">
        <Segmented
          label={chrome.modeLabel}
          value={mode}
          onChange={setMode}
          options={[
            { value: "answer", label: chrome.modeAnswer },
            { value: "review", label: chrome.modeReview },
          ]}
        />
        <Segmented
          label={chrome.matchUiLabel}
          value={matchUi}
          onChange={setMatchUi}
          options={[
            { value: "select", label: chrome.matchUiSelect },
            { value: "pick", label: chrome.matchUiPick },
          ]}
        />
      </div>
      {KIT_SAMPLES.map((sample) => {
        const value = mode === "review" ? sample.submitted : (answers[sample.key] ?? null);
        return (
          <Card key={sample.key} data-sample={sample.key} className="flex min-w-0 flex-col gap-4">
            <Question
              question={sample.question}
              mode={mode}
              value={value}
              onChange={(next) => setAnswers((current) => ({ ...current, [sample.key]: next }))}
              flagged={flags[sample.key] ?? false}
              onToggleFlag={() => setFlags((current) => ({ ...current, [sample.key]: !current[sample.key] }))}
              review={mode === "review" ? sample.review : null}
              matchUi={matchUi}
            >
              {sample.children}
            </Question>
            {mode === "answer" && (
              <div className="flex flex-col gap-1">
                <p className="text-caption font-semibold text-text-secondary">{chrome.valueLabel}</p>
                <pre
                  data-exam-content
                  data-testid={`value-${sample.key}`}
                  className="overflow-x-auto rounded-md bg-subtle p-3 text-caption text-text"
                >
                  {JSON.stringify(value)}
                </pre>
              </div>
            )}
          </Card>
        );
      })}
    </main>
  );
}

function Segmented<T extends string>({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: T;
  onChange: (value: T) => void;
  options: { value: T; label: string }[];
}) {
  return (
    <div role="group" aria-label={label} className="flex items-center gap-2">
      <span className="text-label font-medium text-text-secondary">{label}</span>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={value === option.value}
          onClick={() => onChange(option.value)}
          className={cn(
            "h-10 rounded-lg border border-border bg-surface px-3.5 text-label font-semibold text-text-secondary hover:border-border-strong aria-pressed:border-primary-text aria-pressed:bg-success-bg aria-pressed:text-primary-text",
            focusRing,
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
