"use client";

import { Button, Card, cn, focusRing, StatusChip } from "@thi360/ui";
import { TiptapEssayEditor, type EssayEditorLabels } from "@thi360/ui/essay-editor";
import {
  Question,
  QuestionSet,
  type AnswerValue as KitAnswerValue,
  type AudioClosedReason,
  type ChildQuestionProps,
  type EssayEditorProps,
  type KitQuestionOf,
  type QuestionBody as KitQuestionBody,
} from "@thi360/ui/question";
import { AlertCircle, ArrowLeft, ChevronLeft, ChevronRight, Send } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { DISPLAY_NUMBER_FORMAT } from "../display-number";
import {
  buildSetAnswerValue,
  isTrialLeafAnswered,
  listPaperLeafQuestions,
  sliceTrialPartSegments,
  toKitTrialQuestion,
  toKitTrialSet,
  trialPartKey,
} from "./mappers";
import {
  filterTrialAudioAttachments,
  TrialAudioList,
  useTrialAudioRegistry,
  type TrialAudioRegistry,
} from "./trial-audio";
import type {
  PreviewTrialBlock,
  PreviewTrialItem,
  PreviewTrialPaperResponse,
  PreviewTrialPart,
  PreviewTrialQuestion,
} from "./types";

function LocalizedEssayEditor(props: EssayEditorProps) {
  const t = useTranslations("exams.trial.editor");
  const labels = useMemo<EssayEditorLabels>(
    () => ({
      toolbarLabel: t("toolbarLabel"),
      bold: t("bold"),
      italic: t("italic"),
      bulletList: t("bulletList"),
      orderedList: t("orderedList"),
      undo: t("undo"),
      redo: t("redo"),
    }),
    [t],
  );
  return <TiptapEssayEditor {...props} labels={labels} />;
}

function renderLocalizedEssayEditor(props: EssayEditorProps) {
  return <LocalizedEssayEditor {...props} />;
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
  focusHeadingKey?: number;
  audioRegistry?: TrialAudioRegistry;
  audioClosedReason?: AudioClosedReason | null;
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
  focusHeadingKey = 0,
  audioRegistry,
  audioClosedReason = null,
}: TrialPaperViewProps) {
  const t = useTranslations("exams.trial");
  const format = useFormatter();
  const headingRef = useRef<HTMLHeadingElement>(null);
  const { registry: fallbackAudioRegistry } = useTrialAudioRegistry(
    audioRegistry ? undefined : paper,
    focusHeadingKey,
  );
  const resolvedAudioRegistry = audioRegistry ?? fallbackAudioRegistry;
  const stopAllAudio = resolvedAudioRegistry.stopAll;

  useEffect(() => {
    if (focusHeadingKey > 0) {
      headingRef.current?.focus();
    }
  }, [focusHeadingKey]);

  const sortedParts = useMemo(
    () => [...(paper.parts ?? [])].sort((a, b) => (a.order ?? 0) - (b.order ?? 0)),
    [paper.parts],
  );
  const [selectedPartId, setSelectedPartId] = useState<string | null>(null);
  const activePartIndex = useMemo(() => {
    if (sortedParts.length === 0) return -1;
    if (selectedPartId) {
      const foundIndex = sortedParts.findIndex((part, idx) => trialPartKey(part, idx) === selectedPartId);
      if (foundIndex >= 0) return foundIndex;
    }
    return 0;
  }, [sortedParts, selectedPartId]);
  const activePart = activePartIndex >= 0 ? sortedParts[activePartIndex]! : null;
  const activePartKey = activePart ? trialPartKey(activePart, activePartIndex) : null;

  const prevPartKeyRef = useRef<string | null>(activePartKey);
  useEffect(() => {
    if (prevPartKeyRef.current !== null && prevPartKeyRef.current !== activePartKey) {
      stopAllAudio();
    }
    prevPartKeyRef.current = activePartKey;
  }, [activePartKey, stopAllAudio]);

  const prevSubmittingRef = useRef(false);
  useEffect(() => {
    if (submitting && !prevSubmittingRef.current) {
      stopAllAudio();
    }
    prevSubmittingRef.current = submitting;
  }, [submitting, stopAllAudio]);

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

  const pendingScrollRef = useRef<{
    questionId: string;
    targetPartKey: string;
    setQuestionId?: string;
  } | null>(null);

  const performScroll = useCallback((questionId: string, setQuestionId?: string) => {
    const directEl = document.getElementById(`trial-question-${questionId}`);
    const fallbackEl = setQuestionId
      ? document.getElementById(`trial-question-${setQuestionId}`)
      : null;
    const target = directEl ?? fallbackEl;
    if (target && typeof target.scrollIntoView === "function") {
      const reduceMotion =
        typeof window !== "undefined" &&
        window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
      target.scrollIntoView({
        behavior: reduceMotion ? "auto" : "smooth",
        block: "start",
      });
    }
  }, []);

  useEffect(() => {
    const pending = pendingScrollRef.current;
    if (!pending || activePartKey !== pending.targetPartKey) return;
    pendingScrollRef.current = null;
    performScroll(pending.questionId, pending.setQuestionId);
  }, [activePartKey, performScroll]);

  const scrollToQuestion = useCallback(
    (questionId: string, targetPartKey: string, setQuestionId?: string) => {
      if (activePartKey && activePartKey !== targetPartKey) {
        pendingScrollRef.current = { questionId, targetPartKey, setQuestionId };
        setSelectedPartId(targetPartKey);
      } else {
        pendingScrollRef.current = null;
        performScroll(questionId, setQuestionId);
      }
    },
    [activePartKey, performScroll],
  );

  const expiresLabel = useMemo(() => {
    if (!initialExpiresAt) return null;
    const ms = Date.parse(initialExpiresAt);
    if (Number.isNaN(ms)) return null;
    return format.dateTime(new Date(ms), "dateTime");
  }, [initialExpiresAt, format]);

  return (
    <div className="@container flex flex-col gap-5">
      <header className="sticky top-0 z-10 -mx-4 border-b border-border bg-surface/95 px-4 py-3 backdrop-blur-xs sm:-mx-6 sm:px-6">
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
            <h1
              ref={headingRef}
              tabIndex={-1}
              className={cn("rounded-sm text-title font-bold text-text", focusRing)}
            >
              {t("title")}
            </h1>
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
            const pId = trialPartKey(part, idx);
            const isSelected = activePartKey === pId;
            return (
              <button
                key={pId}
                type="button"
                aria-pressed={isSelected}
                onClick={() => {
                  pendingScrollRef.current = null;
                  setSelectedPartId(pId);
                }}
                className={cn(
                  "relative inline-flex min-h-10 pointer-coarse:min-h-11 items-center gap-2 rounded-lg border px-3.5 py-2 text-label font-medium transition-colors motion-reduce:transition-none",
                  isSelected
                    ? "border-primary-600 bg-primary-50 text-primary-text after:pointer-events-none after:absolute after:inset-x-2.5 after:bottom-1 after:border-b-2 after:border-current"
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
          {activePart && activePartKey ? (
            <TrialPartBody
              part={activePart}
              partKey={activePartKey}
              answers={answers}
              onAnswerChange={onAnswerChange}
              audioRegistry={resolvedAudioRegistry}
              audioDisabled={submitting}
              audioClosedReason={audioClosedReason}
              t={t}
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
                  pendingScrollRef.current = null;
                  const prevIdx = activePartIndex - 1;
                  const prev = sortedParts[prevIdx];
                  if (prev) setSelectedPartId(trialPartKey(prev, prevIdx));
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
                  pendingScrollRef.current = null;
                  const nextIdx = activePartIndex + 1;
                  const next = sortedParts[nextIdx];
                  if (next) setSelectedPartId(trialPartKey(next, nextIdx));
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
                const pId = trialPartKey(part, idx);
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
                        const isCurrentPart = activePartKey === pId;
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
                              "flex h-9 items-center justify-center rounded-md border text-label tabular-nums transition-colors motion-reduce:transition-none pointer-coarse:min-h-11 pointer-coarse:min-w-11",
                              answered
                                ? "border-primary-600 bg-primary-50 font-semibold text-primary-text"
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
  partKey,
  answers,
  onAnswerChange,
  audioRegistry,
  audioDisabled,
  audioClosedReason,
  t,
}: {
  part: PreviewTrialPart;
  partKey: string;
  answers: Readonly<Record<string, KitAnswerValue>>;
  onAnswerChange: (questionId: string, value: KitAnswerValue) => void;
  audioRegistry: TrialAudioRegistry;
  audioDisabled: boolean;
  audioClosedReason: AudioClosedReason | null;
  t: ReturnType<typeof useTranslations<"exams.trial">>;
}) {
  const format = useFormatter();
  const segments = useMemo(() => sliceTrialPartSegments(part), [part]);

  return (
    <section
      aria-labelledby={`trial-part-${partKey}`}
      className="flex flex-col gap-5"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-border pb-2.5">
        <h2 id={`trial-part-${partKey}`} className="text-h3 font-bold text-text">
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
                ownerPrefix={`${partKey}:seg-${slice.segment.code ?? idx}:${block.blockId ?? bIdx}`}
                answers={answers}
                onAnswerChange={onAnswerChange}
                audioRegistry={audioRegistry}
                audioDisabled={audioDisabled}
                audioClosedReason={audioClosedReason}
                t={t}
              />
            ))}
          </div>
        ))
      ) : (
        (part.blocks ?? []).map((block, idx) => (
          <TrialBlockView
            key={block.blockId ?? idx}
            block={block}
            ownerPrefix={`${partKey}:noseg:${block.blockId ?? idx}`}
            answers={answers}
            onAnswerChange={onAnswerChange}
            audioRegistry={audioRegistry}
            audioDisabled={audioDisabled}
            audioClosedReason={audioClosedReason}
            t={t}
          />
        ))
      )}
    </section>
  );
}

function TrialBlockView({
  block,
  ownerPrefix,
  answers,
  onAnswerChange,
  audioRegistry,
  audioDisabled,
  audioClosedReason,
  t,
}: {
  block: PreviewTrialBlock;
  ownerPrefix: string;
  answers: Readonly<Record<string, KitAnswerValue>>;
  onAnswerChange: (questionId: string, value: KitAnswerValue) => void;
  audioRegistry: TrialAudioRegistry;
  audioDisabled: boolean;
  audioClosedReason: AudioClosedReason | null;
  t: ReturnType<typeof useTranslations<"exams.trial">>;
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
          ownerPrefix={`${ownerPrefix}:item-${idx}`}
          answers={answers}
          onAnswerChange={onAnswerChange}
          audioRegistry={audioRegistry}
          audioDisabled={audioDisabled}
          audioClosedReason={audioClosedReason}
          t={t}
        />
      ))}
    </div>
  );
}

function TrialItemView({
  item,
  ownerPrefix,
  answers,
  onAnswerChange,
  audioRegistry,
  audioDisabled,
  audioClosedReason,
  t,
}: {
  item: PreviewTrialItem;
  ownerPrefix: string;
  answers: Readonly<Record<string, KitAnswerValue>>;
  onAnswerChange: (questionId: string, value: KitAnswerValue) => void;
  audioRegistry: TrialAudioRegistry;
  audioDisabled: boolean;
  audioClosedReason: AudioClosedReason | null;
  t: ReturnType<typeof useTranslations<"exams.trial">>;
}) {
  if (item.kind === "SET" && item.set) {
    const setItem = item.set;
    const setChildren = setItem.children ?? [];
    const { setQuestion, childQuestions } = toKitTrialSet(setItem);
    const childById = new Map<string, PreviewTrialQuestion>();
    for (const child of setChildren) {
      if (child.questionId) childById.set(child.questionId, child);
    }

    const setNumbers = setChildren
      .map((child) => child.number)
      .filter((num): num is number => typeof num === "number" && num > 0);
    const setFirst = setNumbers.length > 0 ? Math.min(...setNumbers) : null;
    const setLast = setNumbers.length > 0 ? Math.max(...setNumbers) : null;
    const setRangeName =
      setFirst !== null && setLast !== null
        ? setFirst === setLast
          ? t("audio.forQuestion", { number: setFirst })
          : t("audio.forSet", { first: setFirst, last: setLast })
        : t("audio.setFallback");

    const setValue = buildSetAnswerValue(setChildren, answers);

    return (
      <div
        id={`trial-question-${setItem.questionId ?? ""}`}
        className="scroll-mt-28 flex flex-col gap-3 rounded-xl border border-border bg-surface p-4"
      >
        <TrialAudioList
          attachments={setItem.attachments}
          registry={audioRegistry}
          ownerKey={`${ownerPrefix}:set-${setItem.questionId ?? "0"}`}
          contextLabel={t("audio.setLabel", { name: setRangeName })}
          groupLabel={t("audio.setGroup", { name: setRangeName })}
          disabled={audioDisabled}
          closedReason={audioClosedReason}
        />
        <QuestionSet
          question={setQuestion as KitQuestionOf<Extract<KitQuestionBody, { type: "QUESTION_SET" }>>}
          mode="answer"
          headingLevel={3}
          value={setValue}
          onChange={(next) => {
            const childrenMap = next?.children ?? {};
            for (const child of setChildren) {
              const cid = child.questionId;
              if (!cid) continue;
              const nextChildVal = childrenMap[cid];
              if (nextChildVal !== undefined && nextChildVal !== answers[cid]) {
                onAnswerChange(cid, nextChildVal);
              }
            }
          }}
          renderQuestion={(childProps: ChildQuestionProps) => {
            const child = childById.get(childProps.question.id);
            const hasChildAudio = filterTrialAudioAttachments(child?.attachments).length > 0;
            const childNumber = child?.number ?? childProps.question.displayNo;
            const childName = t("audio.forQuestion", { number: childNumber });
            const childQuestionEl = (
              <Question
                question={childProps.question}
                mode={childProps.mode}
                value={childProps.value}
                onChange={childProps.onChange}
                review={childProps.review}
                headingLevel={childProps.headingLevel}
                renderEditor={renderLocalizedEssayEditor}
              />
            );
            if (!hasChildAudio) return childQuestionEl;
            return (
              <div className="flex flex-col gap-3">
                <TrialAudioList
                  attachments={child?.attachments}
                  registry={audioRegistry}
                  ownerKey={`${ownerPrefix}:child-${childProps.question.id}`}
                  contextLabel={t("audio.questionLabel", { name: childName })}
                  groupLabel={t("audio.group", { name: childName })}
                  disabled={audioDisabled}
                  closedReason={audioClosedReason}
                />
                {childQuestionEl}
              </div>
            );
          }}
        >
          {childQuestions}
        </QuestionSet>
      </div>
    );
  }

  if (item.kind === "QUESTION" && item.question) {
    const q = item.question;
    const qId = q.questionId ?? "";
    const kitQuestion = toKitTrialQuestion(q);
    const qNumber = q.number ?? kitQuestion.displayNo;
    const qName = t("audio.forQuestion", { number: qNumber });

    return (
      <div
        id={`trial-question-${qId}`}
        className="scroll-mt-28 flex flex-col gap-3 rounded-xl border border-border bg-surface p-4"
      >
        <TrialAudioList
          attachments={q.attachments}
          registry={audioRegistry}
          ownerKey={`${ownerPrefix}:q-${qId}`}
          contextLabel={t("audio.questionLabel", { name: qName })}
          groupLabel={t("audio.group", { name: qName })}
          disabled={audioDisabled}
          closedReason={audioClosedReason}
        />
        <Question
          question={kitQuestion}
          mode="answer"
          headingLevel={3}
          value={answers[qId] ?? null}
          renderEditor={renderLocalizedEssayEditor}
          onChange={(val) => onAnswerChange(qId, val)}
        />
      </div>
    );
  }

  return null;
}
