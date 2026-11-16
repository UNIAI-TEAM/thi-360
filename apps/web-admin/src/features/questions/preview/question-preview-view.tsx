"use client";

import { Button, Card, Skeleton, StatusChip, Tabs, TabsList, TabsTrigger } from "@thi360/ui";
import { Eraser, ListChecks, Play } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { useQuestion } from "../queries";
import type { QuestionDetail } from "../types";
import {
  correctAnswer,
  emptyAnswer,
  gradePreview,
  type QuestionResult,
  type QuestionResultState,
  type PreviewAnswer,
} from "./grading";
import { PreviewFrame, type PreviewDevice } from "./preview-frame";
import { QuestionPlayer, ResultMark } from "./question-player";
import { useScoreFormat } from "./score-format";

const RESULT_TONE = {
  correct: "success",
  partial: "warning",
  wrong: "danger",
  blank: "neutral",
  manual: "info",
} as const satisfies Record<QuestionResultState, "success" | "warning" | "danger" | "neutral" | "info">;

/**
 * Trang con Xem trước của chi tiết câu hỏi (UniNote PBI-03 mục 8.5): câu hiện như học viên thấy, trong khung web
 * hoặc khung điện thoại. Người soạn thử chọn, đổi hoặc bỏ trống đáp án rồi bấm Chấm thử để xem điểm từng ý và cả câu
 * (quy tắc chấm của spec mục 8). Bài làm thử chỉ nằm trong trang, không ghi vào kho dữ liệu.
 */
export function QuestionPreviewView({ questionId }: { questionId: string }) {
  const questionQuery = useQuestion(questionId);
  if (!questionQuery.data) return <Skeleton className="h-80" />;
  // Đổi câu hoặc phiên bản thì bắt đầu bài làm thử mới.
  const question = questionQuery.data;
  return <PreviewWorkspace key={`${question.id}-${question.version}-${question.revision}`} question={question} />;
}

function PreviewWorkspace({ question }: { question: QuestionDetail }) {
  const t = useTranslations("questions.preview");
  const [device, setDevice] = useState<PreviewDevice>("web");
  const [answer, setAnswer] = useState<PreviewAnswer>(() => emptyAnswer(question));
  const [result, setResult] = useState<QuestionResult | null>(null);

  const change = (next: PreviewAnswer) => {
    setAnswer(next);
    // Đổi đáp án sau khi chấm thì kết quả cũ không còn đúng: ẩn tới lần Chấm thử sau.
    setResult(null);
  };

  return (
    <div className="@container">
      <div className="grid items-start gap-4 @4xl:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="flex min-w-0 flex-col gap-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Tabs variant="segmented" value={device} onValueChange={(value) => setDevice(value as PreviewDevice)}>
              <TabsList aria-label={t("deviceLabel")}>
                <TabsTrigger value="web">{t("device.web")}</TabsTrigger>
                <TabsTrigger value="phone">{t("device.phone")}</TabsTrigger>
              </TabsList>
            </Tabs>
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="secondary" onClick={() => change(correctAnswer(question))}>
                <ListChecks aria-hidden className="size-4" />
                {t("actions.fillCorrect")}
              </Button>
              <Button
                variant="secondary"
                onClick={() => {
                  setAnswer(emptyAnswer(question));
                  setResult(null);
                }}
              >
                <Eraser aria-hidden className="size-4" />
                {t("actions.reset")}
              </Button>
              <Button onClick={() => setResult(gradePreview(question, answer))}>
                <Play aria-hidden className="size-4" />
                {t("actions.grade")}
              </Button>
            </div>
          </div>
          <PreviewFrame device={device} label={t(`frame.${device}`)}>
            <QuestionPlayer question={question} answer={answer} onChange={change} result={result} />
          </PreviewFrame>
        </div>
        <ResultPanel question={question} result={result} />
      </div>
    </div>
  );
}

function ResultPanel({ question, result }: { question: QuestionDetail; result: QuestionResult | null }) {
  const t = useTranslations("questions.preview.result");
  const scoreText = useScoreFormat();
  return (
    <Card padding="none" className="flex flex-col gap-3 p-4 sm:p-5">
      <h2 className="text-h3 font-semibold text-text">{t("title")}</h2>
      <div aria-live="polite" className="flex flex-col gap-3">
        {result === null ? (
          <p className="text-body text-text-secondary">{t("idle")}</p>
        ) : (
          <>
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <p className="text-h3 font-bold tabular-nums text-text">
                {t("score", { earned: scoreText(result.earned), max: scoreText(result.max) })}
              </p>
              <StatusChip tone={RESULT_TONE[result.state]}>{t(`state.${result.state}`)}</StatusChip>
            </div>
            {result.hasManualPart && <p className="text-caption text-text-secondary">{t("manualPending")}</p>}
            <ResultRows question={question} result={result} />
          </>
        )}
      </div>
    </Card>
  );
}

function ResultRows({ question, result }: { question: QuestionDetail; result: QuestionResult }) {
  const t = useTranslations("questions.preview.result");
  const scoreText = useScoreFormat();
  const body = question.body;

  if (body.type === "QUESTION_SET") {
    return (
      <ul className="m-0 flex list-none flex-col gap-2 border-t border-border p-0 pt-3">
        {question.children.map((child, index) => {
          const childResult = result.children[child.id];
          if (!childResult) return null;
          return (
            <li key={child.id} className="flex flex-wrap items-center justify-between gap-2 text-body text-text">
              <span>{t("rowChild", { number: index + 1 })}</span>
              <span className="flex items-center gap-2">
                <span className="text-caption text-text-secondary tabular-nums">
                  {t("childPoints", { earned: scoreText(childResult.earned), max: scoreText(childResult.max) })}
                </span>
                <StatusChip tone={RESULT_TONE[childResult.state]}>{t(`state.${childResult.state}`)}</StatusChip>
              </span>
            </li>
          );
        })}
      </ul>
    );
  }

  if (result.parts.length === 0) return null;
  const label = (index: number): string =>
    body.type === "CLOZE"
      ? t("rowBlank", { number: index + 1 })
      : body.type === "MATCHING"
        ? t("rowPair", { number: index + 1 })
        : t("rowStatement", { letter: String.fromCharCode(97 + index) });
  return (
    <ul className="m-0 flex list-none flex-col gap-2 border-t border-border p-0 pt-3">
      {result.parts.map((part, index) => (
        <li key={part.key} className="flex flex-wrap items-center justify-between gap-2 text-body text-text">
          <span>{label(index)}</span>
          <ResultMark part={part} state={part.state} />
        </li>
      ))}
    </ul>
  );
}
