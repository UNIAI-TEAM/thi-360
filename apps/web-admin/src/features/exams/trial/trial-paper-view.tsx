"use client";

import { Button, Card, cn, focusRing, StatusChip } from "@thi360/ui";
import { Question, type AnswerValue as KitAnswerValue, type EssayEditorProps } from "@thi360/ui/question";
import { RichTextView } from "@thi360/ui/rich-text";
import { AlertCircle, ArrowLeft, ChevronLeft, ChevronRight, Send } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useCallback, useMemo, useRef, useState } from "react";
import { DISPLAY_NUMBER_FORMAT } from "../display-number";
import { PreviewAudioList } from "../components/exam-preview-audio";
import {
  buildSetAnswerValue,
  isTrialLeafAnswered,
  listPaperLeafQuestions,
  sliceTrialPartSegments,
  toKitTrialQuestion,
  toKitTrialSet,
  toTrialAttachments,
} from "./mappers";
import { TrialEssayEditor } from "./trial-essay-editor";
import type {
  PreviewTrialBlock,
  PreviewTrialItem,
  PreviewTrialPaperResponse,
  PreviewTrialPart,
  PreviewTrialQuestion,
} from "./types";

function renderTrialEssayEditor(props: EssayEditorProps) {
  return <TrialEssayEditor {...props} />;
}

export interface TrialPaperViewProps {
  paper: PreviewTrialPaperResponse;
  answers: Readonly<Record<string, KitAnswerValue>>;
  onAnswerChange: (questionId: string, value: KitAnswerValue) => void;
  onSubmit: () => void;
  onRequestLeave: () => void;
  submitting: boolean;
  submitError: string | null;
  initialExpiresAt?: string | null;
}

export function TrialPaperView({
  paper,
  answers,
  onAnswerChange,
  onSubmit,
  onRequestLeave,
  submitting,
  submitError,
  initialExpiresAt,
}: TrialPaperViewProps) {
  const t = useTranslations("exams.trial");
  const tPreview = useTranslations("exams.preview");
  const format = useFormatter();

  const sortedParts = useMemo(
    () => [...(paper.parts ?? [])].sort((a, b) => (a.order ?? 0) - (b.order ?? 0)),
    [paper.parts],
  );
  const [selectedPartId, setSelectedPartId] = useState<string | null>(null);
  const activePart = useMemo(() => {
    if (sortedParts.length === 0) return null;
    if (selectedPartId) {
      const found = sortedParts.find((part) => (part.partId ?? "") === selectedPartId);
      if (found) return found;
    }
    return sortedParts[0]!;
  }, [sortedParts, selectedPartId]);

  const activePartIndex = useMemo(
    () => (activePart ? sortedParts.findIndex((p) => p.partId === activePart.partId) : -1),
    [sortedParts, activePart],
  );

  const leafDescriptors = useMemo(() => listPaperLeafQuestions(paper), [paper]);
  const leavesByPartId = useMemo(() => {
    const grouped = new Map<string, typeof leafDescriptors>();
    for (const leaf of leafDescriptors) {
      const bucket = grouped.get(leaf.partId) ?? [];
      bucket.push(leaf);
      grouped.set(leaf.partId, bucket);
    }
    return grouped;
  }, [leafDescriptors]);

  const answeredCount = useMemo(
    () => leafDescriptors.filter((leaf) => isTrialLeafAnswered(leaf.type, answers[leaf.questionId])).length,
    [leafDescriptors, answers],
  );

  const pendingScrollIdRef = useRef<string | null>(null);

  const scrollToQuestion = useCallback(
    (questionId: string, targetPartId: string, setQuestionId?: string) => {
      const scrollNow = () => {
        const directEl = document.getElementById(`trial-question-${questionId}`);
        const fallbackEl = setQuestionId
          ? document.getElementById(`trial-question-${setQuestionId}`)
          : null;
        const target = directEl ?? fallbackEl;
        if (target && typeof target.scrollIntoView === "function") {
          target.scrollIntoView({ behavior: "smooth", block: "start" });
        }
      };

      if (activePart && (activePart.partId ?? "") !== targetPartId) {
        pendingScrollIdRef.current = questionId;
        setSelectedPartId(targetPartId);
        requestAnimationFrame(() => {
          if (pendingScrollIdRef.current === questionId) {
            pendingScrollIdRef.current = null;
            scrollNow();
          }
        });
      } else {
        scrollNow();
      }
    },
    [activePart],
  );

  const expiresLabel = useMemo(() => {
    if (!initialExpiresAt) return null;
    const ms = Date.parse(initialExpiresAt);
    if (Number.isNaN(ms)) return null;
    return format.dateTime(new Date(ms), "dateTime");
  }, [initialExpiresAt, format]);

  return (
    <div className="@container flex flex-col gap-5">
      <header className="sticky top-0 z-10 -mx-4 border-b border-border bg-surface/95 px-4 py-3 backdrop-blur-xs">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex min-w-0 flex-wrap items-center gap-2.5">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              disabled={submitting}
              onClick={onRequestLeave}
            >
              <ArrowLeft aria-hidden />
              {t("backToPreview")}
            </Button>
            <h1 className="text-title font-bold text-text">{t("title")}</h1>
            <StatusChip tone="warning">{t("modeBadge")}</StatusChip>
            {paper.versionNo != null && (
              <StatusChip tone="neutral">{t("versionBadge", { version: paper.versionNo })}</StatusChip>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <span className="text-caption text-text-secondary tabular-nums">
              {t("progress", {
                answered: format.number(answeredCount),
                total: format.number(leafDescriptors.length),
              })}
            </span>
            <Button
              type="button"
              variant="primary"
              loading={submitting}
              onClick={onSubmit}
            >
              <Send aria-hidden />
              {t("submitButton")}
            </Button>
          </div>
        </div>
        <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-caption text-text-muted">
          <span>{t("untimedNotice")}</span>
          {paper.durationMinutes != null && (
            <span>{t("referenceDuration", { minutes: format.number(paper.durationMinutes) })}</span>
          )}
          {expiresLabel && <span>{t("retentionNotice", { time: expiresLabel })}</span>}
        </div>
      </header>

      {submitError && (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-danger/40 border-l-4 border-l-danger bg-danger-bg px-4 py-3 text-body text-danger"
        >
          <div className="flex items-start gap-2.5">
            <AlertCircle aria-hidden className="mt-0.5 size-5 shrink-0" />
            <span>{submitError}</span>
          </div>
          <Button type="button" variant="secondary" size="sm" onClick={onSubmit}>
            {t("retrySubmit")}
          </Button>
        </div>
      )}

      {sortedParts.length > 1 && (
        <nav aria-label={t("partNavAriaLabel")} className="flex flex-wrap gap-2">
          {sortedParts.map((part, idx) => {
            const pId = part.partId ?? `part-${idx}`;
            const isSelected = activePart?.partId === part.partId;
            return (
              <button
                key={pId}
                type="button"
                aria-pressed={isSelected}
                onClick={() => setSelectedPartId(pId)}
                className={cn(
                  "inline-flex min-h-10 items-center gap-2 rounded-lg border px-3.5 py-2 text-label transition-colors",
                  isSelected
                    ? "border-primary-500 bg-primary-bg font-semibold text-primary-text"
                    : "border-border bg-surface text-text-secondary hover:bg-subtle hover:text-text",
                  focusRing,
                )}
              >
                <span data-exam-content>{part.name || t("partFallback", { order: idx + 1 })}</span>
                {part.firstNumber != null && part.lastNumber != null && (
                  <span className="text-caption text-text-muted tabular-nums">
                    {t("partRange", {
                      first: format.number(part.firstNumber),
                      last: format.number(part.lastNumber),
                    })}
                  </span>
                )}
              </button>
            );
          })}
        </nav>
      )}

      <div className="grid items-start gap-5 @4xl:grid-cols-[minmax(0,1fr)_minmax(0,18.5rem)]">
        <div className="flex min-w-0 flex-col gap-5">
          {activePart ? (
            <TrialPartBody
              part={activePart}
              answers={answers}
              onAnswerChange={onAnswerChange}
              t={t}
              tPreview={tPreview}
            />
          ) : (
            <Card>
              <p className="text-body text-text-muted">{t("emptyPaper")}</p>
            </Card>
          )}

          {sortedParts.length > 1 && activePart && (
            <div className="flex items-center justify-between gap-3 border-t border-border pt-4">
              <Button
                type="button"
                variant="secondary"
                disabled={activePartIndex <= 0}
                onClick={() => {
                  const prev = sortedParts[activePartIndex - 1];
                  if (prev?.partId) setSelectedPartId(prev.partId);
                }}
              >
                <ChevronLeft aria-hidden />
                {t("previousPart")}
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={activePartIndex < 0 || activePartIndex >= sortedParts.length - 1}
                onClick={() => {
                  const next = sortedParts[activePartIndex + 1];
                  if (next?.partId) setSelectedPartId(next.partId);
                }}
              >
                {t("nextPart")}
                <ChevronRight aria-hidden />
              </Button>
            </div>
          )}
        </div>

        <aside aria-label={t("palette.ariaLabel")} className="@4xl:sticky @4xl:top-28">
          <Card className="flex flex-col gap-4">
            <div className="flex items-baseline justify-between gap-2">
              <h2 className="text-title font-semibold text-text">{t("palette.title")}</h2>
              <span className="text-caption text-text-muted tabular-nums">
                {t("palette.count", {
                  answered: format.number(answeredCount),
                  total: format.number(leafDescriptors.length),
                })}
              </span>
            </div>

            <div className="flex flex-col gap-3">
              {sortedParts.map((part, idx) => {
                const pId = part.partId ?? `part-${idx}`;
                const partLeaves = leavesByPartId.get(pId) ?? [];
                if (partLeaves.length === 0) return null;
                return (
                  <div key={pId} className="flex flex-col gap-2">
                    <p className="text-caption font-semibold text-text-secondary">
                      <span data-exam-content>{part.name || t("partFallback", { order: idx + 1 })}</span>
                    </p>
                    <div className="grid grid-cols-5 gap-1.5">
                      {partLeaves.map((leaf) => {
                        const answered = isTrialLeafAnswered(leaf.type, answers[leaf.questionId]);
                        const isCurrentPart = activePart?.partId === part.partId;
                        return (
                          <button
                            key={leaf.questionId}
                            type="button"
                            onClick={() => scrollToQuestion(leaf.questionId, pId, leaf.setQuestionId)}
                            aria-label={t(
                              answered ? "palette.itemAnswered" : "palette.itemUnanswered",
                              { number: format.number(leaf.number) },
                            )}
                            className={cn(
                              "flex h-9 items-center justify-center rounded-md border text-label tabular-nums transition-colors",
                              answered
                                ? "border-primary-500 bg-primary-bg font-semibold text-primary-text"
                                : "border-border bg-surface text-text hover:bg-subtle",
                              isCurrentPart && !answered && "border-border-strong",
                              focusRing,
                            )}
                          >
                            {format.number(leaf.number)}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}

function TrialPartBody({
  part,
  answers,
  onAnswerChange,
  t,
  tPreview,
}: {
  part: PreviewTrialPart;
  answers: Readonly<Record<string, KitAnswerValue>>;
  onAnswerChange: (questionId: string, value: KitAnswerValue) => void;
  t: ReturnType<typeof useTranslations<"exams.trial">>;
  tPreview: ReturnType<typeof useTranslations<"exams.preview">>;
}) {
  const format = useFormatter();
  const segments = useMemo(() => sliceTrialPartSegments(part), [part]);

  return (
    <section
      aria-labelledby={`trial-part-${part.partId}`}
      className="flex flex-col gap-5"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-border pb-2.5">
        <h2 id={`trial-part-${part.partId}`} className="text-h3 font-bold text-text">
          <span data-exam-content>{part.name}</span>
        </h2>
        <div className="flex flex-wrap items-center gap-2 text-caption text-text-muted tabular-nums">
          {part.firstNumber != null && part.lastNumber != null && (
            <span>
              {t("partRange", {
                first: format.number(part.firstNumber),
                last: format.number(part.lastNumber),
              })}
            </span>
          )}
          {part.points != null && (
            <span>
              · {t("partPoints", { points: format.number(part.points, DISPLAY_NUMBER_FORMAT) })}
            </span>
          )}
        </div>
      </div>

      {segments ? (
        segments.map((slice, idx) => (
          <div key={slice.segment.code ?? idx} className="flex flex-col gap-4">
            {slice.segment.name && (
              <div className="rounded-lg border border-border bg-subtle px-3.5 py-2.5">
                <h3 data-exam-content className="text-title font-semibold text-text">
                  {slice.segment.name}
                </h3>
              </div>
            )}
            {slice.blocks.map((block, bIdx) => (
              <TrialBlockView
                key={`${block.blockId ?? bIdx}-${idx}`}
                block={block}
                answers={answers}
                onAnswerChange={onAnswerChange}
                tPreview={tPreview}
              />
            ))}
          </div>
        ))
      ) : (
        (part.blocks ?? []).map((block, idx) => (
          <TrialBlockView
            key={block.blockId ?? idx}
            block={block}
            answers={answers}
            onAnswerChange={onAnswerChange}
            tPreview={tPreview}
          />
        ))
      )}
    </section>
  );
}

function TrialBlockView({
  block,
  answers,
  onAnswerChange,
  tPreview,
}: {
  block: PreviewTrialBlock;
  answers: Readonly<Record<string, KitAnswerValue>>;
  onAnswerChange: (questionId: string, value: KitAnswerValue) => void;
  tPreview: ReturnType<typeof useTranslations<"exams.preview">>;
}) {
  return (
    <div className="flex flex-col gap-4">
      {block.name && (
        <div className="text-label font-medium text-text-secondary">
          <span data-exam-content>{block.name}</span>
        </div>
      )}
      {(block.items ?? []).map((item, idx) => (
        <TrialItemView
          key={
            item.kind === "SET"
              ? item.set?.questionId ?? `set-${idx}`
              : item.question?.questionId ?? `q-${idx}`
          }
          item={item}
          answers={answers}
          onAnswerChange={onAnswerChange}
          tPreview={tPreview}
        />
      ))}
    </div>
  );
}

function TrialItemView({
  item,
  answers,
  onAnswerChange,
  tPreview,
}: {
  item: PreviewTrialItem;
  answers: Readonly<Record<string, KitAnswerValue>>;
  onAnswerChange: (questionId: string, value: KitAnswerValue) => void;
  tPreview: ReturnType<typeof useTranslations<"exams.preview">>;
}) {
  if (item.kind === "SET" && item.set) {
    const setItem = item.set;
    const setChildren = setItem.children ?? [];
    const { setQuestion, childQuestions } = toKitTrialSet(setItem);
    const setAttachments = toTrialAttachments(setItem.attachments);
    const childNumbers = setChildren.map((c) => c.number ?? 0).filter((n) => n > 0);
    const firstNo = childNumbers.length > 0 ? Math.min(...childNumbers) : 1;
    const lastNo = childNumbers.length > 0 ? Math.max(...childNumbers) : firstNo;

    const setValue = buildSetAnswerValue(setChildren, answers);

    return (
      <div
        id={`trial-question-${setItem.questionId ?? ""}`}
        className="scroll-mt-28 flex flex-col gap-3 rounded-xl border border-border bg-surface p-4"
      >
        {setAttachments.length > 0 && (
          <PreviewAudioList
            attachments={setAttachments}
            name={tPreview("audio.forSet", { first: firstNo, last: lastNo })}
          />
        )}
        {setChildren.map((child: PreviewTrialQuestion) => {
          const cAtt = toTrialAttachments(child.attachments);
          if (cAtt.length === 0) return null;
          return (
            <PreviewAudioList
              key={child.questionId}
              attachments={cAtt}
              name={tPreview("audio.forQuestion", { number: child.number ?? 1 })}
            />
          );
        })}
        <div data-exam-content>
          <Question
            question={setQuestion}
            mode="answer"
            headingLevel={3}
            value={setValue}
            renderEditor={renderTrialEssayEditor}
            onChange={(next) => {
              if (!next || typeof next !== "object" || !("children" in next)) return;
              const childrenMap = (next as { children?: Record<string, KitAnswerValue> }).children ?? {};
              for (const child of setChildren) {
                const cid = child.questionId;
                if (!cid) continue;
                const nextChildVal = childrenMap[cid];
                if (nextChildVal !== undefined && nextChildVal !== answers[cid]) {
                  onAnswerChange(cid, nextChildVal);
                }
              }
            }}
          >
            {childQuestions}
          </Question>
        </div>
      </div>
    );
  }

  if (item.kind === "QUESTION" && item.question) {
    const q = item.question;
    const qId = q.questionId ?? "";
    const kitQuestion = toKitTrialQuestion(q);
    const attachments = toTrialAttachments(q.attachments);

    return (
      <div
        id={`trial-question-${qId}`}
        className="scroll-mt-28 flex flex-col gap-3 rounded-xl border border-border bg-surface p-4"
      >
        {q.content && kitQuestion.body.type === "CLOZE" && (
          <div data-exam-content className="text-body text-text-secondary">
            <RichTextView html={q.content} />
          </div>
        )}
        {attachments.length > 0 && (
          <PreviewAudioList
            attachments={attachments}
            name={tPreview("audio.forQuestion", { number: q.number ?? 1 })}
          />
        )}
        <div data-exam-content>
          <Question
            question={kitQuestion}
            mode="answer"
            headingLevel={3}
            value={answers[qId] ?? null}
            renderEditor={renderTrialEssayEditor}
            onChange={(val) => onAnswerChange(qId, val)}
          />
        </div>
      </div>
    );
  }

  return null;
}
