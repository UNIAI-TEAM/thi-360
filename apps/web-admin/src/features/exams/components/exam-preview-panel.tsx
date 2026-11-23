"use client";

import { useMutation } from "@tanstack/react-query";
import {
  Button,
  Card,
  cn,
  EmptyState,
  focusRing,
  LiveStatus,
  Select,
  Skeleton,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@thi360/ui";
import { AlertCircle, ArrowRight, CircleHelp, CircleX, Clock, Eye, Info, ListOrdered, Play, RefreshCw, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { type ReactNode, useEffect, useId, useMemo, useRef, useState } from "react";
import { enumeratePaths, issueRuleHref, issueStructureHref } from "../check";
import { errorCodeOf } from "../errors";
import { extendPreviewForTrial, trialPageHref } from "../trial/api";
import { usePreviewMinutesLeft } from "../preview-retention";
import { useExamVersion, useGeneratePreview } from "../review-queries";
import { changedPartIds, examNeedsPathChoice, listPreviewSections } from "../review-scope";
import type { CheckIssue, ExamCheckResult, ExamPathRef, PathCheckResult, PreviewResult, PreviewTarget } from "../review-types";
import type { ExamDetail, ExamPartOption } from "../types";
import { ExamContentText } from "./exam-content-text";
import { ExamPreviewAllocation } from "./exam-preview-allocation";
import { ExamPreviewComplianceBanner } from "./exam-preview-compliance";
import { ExamPreviewPaper } from "./exam-preview-paper";
import { ExamPreviewSubjects } from "./exam-preview-subjects";
import { PreviewScopeCard } from "./exam-preview-scope-card";
import { ExamName, richNodes, useChoiceNameResolver, useIssueText, useLookup, usePathLabel, usePathNode } from "./exam-review-shared";

interface ExamPreviewPanelProps {
  exam: ExamDetail;
  /** Kết quả kiểm tra gần nhất (trạng thái từng đường); `null` khi chưa kiểm tra. */
  check: ExamCheckResult | null;
  initialPathId: string | null;
  /** Sinh đề ngay khi mở (bấm "Sinh đề xem trước" ở bảng kết quả kiểm tra). */
  autoGenerate: boolean;
  /** Sinh theo một phiên bản đã công bố; `null` là bản làm việc. */
  version: number | null;
  onClearVersion: () => void;
  onShowCheck: () => void;
}

interface PathOption {
  path: ExamPathRef;
  result: PathCheckResult | null;
}

type ScopeKind = PreviewTarget["kind"];

const PREVIEW_RETENTION_MINUTES = 30;

export function ExamPreviewPanel({
  exam,
  check,
  initialPathId,
  autoGenerate,
  version,
  onClearVersion,
  onShowCheck,
}: ExamPreviewPanelProps) {
  const t = useTranslations("exams.preview");
  const tIssue = useTranslations("exams.review.issue");
  const format = useFormatter();
  const lookup = useLookup();
  const pathLabel = usePathLabel(exam.id);
  const pathNode = usePathNode(exam.id);
  const nameOf = useChoiceNameResolver();
  const versionQuery = useExamVersion(exam.id, version);
  const generate = useGeneratePreview(exam.id);
  const scopeName = useId();
  const trialUnavailableId = useId();
  const [liveMessage, setLiveMessage] = useState("");
  const [run, setRun] = useState(0);
  const [tab, setTab] = useState<"paper" | "allocation">("paper");
  const [original, setOriginal] = useState(false);
  const [resultLabel, setResultLabel] = useState("");
  const [trialExpires, setTrialExpires] = useState<{ previewId: string; expiresAt: string } | null>(null);
  const [trialErrorCode, setTrialErrorCode] = useState<string | null>(null);
  const [blockedTrialHref, setBlockedTrialHref] = useState<string | null>(null);
  const activePreviewIdRef = useRef<string | null>(null);
  const tryButtonRef = useRef<HTMLButtonElement>(null);

  const extendTrial = useMutation({
    networkMode: "always",
    mutationFn: (previewId: string) =>
      extendPreviewForTrial({ examId: exam.id, previewId }),
    onSuccess: (res, previewId) => {
      if (activePreviewIdRef.current !== previewId) return;
      setTrialErrorCode(null);
      if (res.expiresAt) {
        setTrialExpires({ previewId, expiresAt: res.expiresAt });
      }
      const href = trialPageHref(exam.id, previewId, res.expiresAt ?? null);
      let popup: Window | null = null;
      try {
        popup = window.open(href, "_blank");
      } catch {
        popup = null;
      }
      if (!popup || popup.closed) {
        setBlockedTrialHref(href);
        setLiveMessage(t("trial.popupBlocked"));
        return;
      }
      try {
        popup.opener = null;
      } catch {
        // Ignore if browser restricts setting opener on child window
      }
      setBlockedTrialHref(null);
      setLiveMessage(t("trial.liveOpened"));
    },
    onError: (error, previewId) => {
      if (activePreviewIdRef.current !== previewId) return;
      const code = errorCodeOf(error) ?? "GENERIC";
      setTrialErrorCode(code);
      setLiveMessage(code === "PREVIEW_EXPIRED" ? t("trial.expiredError") : t("trial.genericError"));
    },
  });

  const parts = version !== null ? versionQuery.data?.parts : exam.parts;
  const sectionOptions = useMemo(() => listPreviewSections(parts ?? []), [parts]);
  const choiceRequired = examNeedsPathChoice(parts ?? []);

  const options = useMemo<PathOption[]>(() => {
    if (version !== null) {
      return versionQuery.data
        ? enumeratePaths(versionQuery.data.parts, nameOf).map((path) => ({ path, result: null }))
        : [];
    }
    if (check) return check.paths.map((path) => ({ path, result: path.result }));
    return enumeratePaths(exam.parts, nameOf).map((path) => ({ path, result: null }));
  }, [version, versionQuery.data, check, exam.parts, nameOf]);

  // Phạm vi: bài có phần chọn thì sinh theo một đường (hoặc một môn), "Cả bài" chỉ dùng cho bài không có lựa chọn.
  const [pickedKind, setPickedKind] = useState<ScopeKind | null>(null);
  const wanted: ScopeKind = pickedKind ?? "PATH";
  const kind: ScopeKind =
    wanted === "SECTION" && sectionOptions.length > 0 ? "SECTION" : choiceRequired ? "PATH" : "EXAM";

  const [selected, setSelected] = useState<string | null>(initialPathId);
  const fallback = (options.find((option) => option.result === null || option.result === "VALID") ?? options[0])?.path.pathId;
  const pathId = selected && options.some((option) => option.path.pathId === selected) ? selected : (fallback ?? null);
  const selectedPath = options.find((option) => option.path.pathId === pathId)?.path ?? null;

  function subjectChoiceOf(candidatePathId: string | null): ExamPartOption | null {
    const path = options.find((option) => option.path.pathId === candidatePathId)?.path;
    const choice = path?.choices.find((item) => item.kind === "SUBJECT_CHOICE");
    return parts?.find((part) => part.id === choice?.partId)?.options.find((item) => item.id === choice?.optionId) ?? null;
  }

  // Môn đã chọn theo từng phương án; chưa chọn thì xem các môn đầu tiên của phương án theo thứ tự cấu trúc.
  const [pickedSubjects, setPickedSubjects] = useState<{ optionId: string; codes: string[] } | null>(null);
  function subjectsOf(option: ExamPartOption | null): string[] | undefined {
    if (!option) return undefined;
    if (pickedSubjects?.optionId === option.id) return pickedSubjects.codes;
    return option.sections.slice(0, option.chooseSubjects ?? 0).map((section) => section.subjectCode);
  }
  function generateErrorText(error: unknown): string {
    return errorCodeOf(error) === "PREVIEW_SUBJECTS_INVALID" ? tIssue("PREVIEW_SUBJECTS_INVALID") : t("error");
  }
  const subjectOption = kind === "PATH" ? subjectChoiceOf(pathId) : null;
  const subjectCodes = subjectsOf(subjectOption);
  const subjectsIncomplete = subjectOption !== null && subjectCodes?.length !== subjectOption.chooseSubjects;

  // Một môn: môn của SUBJECT_CHOICE chọn 17 hoặc 16 câu.
  const [pickedSection, setPickedSection] = useState<string | null>(null);
  const section = sectionOptions.find((item) => item.sectionId === pickedSection) ?? sectionOptions[0] ?? null;
  const [pickedCount, setPickedCount] = useState<number | null>(null);
  const sectionCount = section?.counts ? (section.counts.find((count) => count === pickedCount) ?? section.counts[0]!) : null;

  function currentTarget(): PreviewTarget | null {
    if (kind === "SECTION") {
      if (!section) return null;
      return {
        kind: "SECTION",
        sectionId: section.sectionId,
        ...(sectionCount !== null ? { sectionQuestionCount: sectionCount } : {}),
      };
    }
    if (kind === "PATH") {
      if (!pathId) return null;
      return { kind: "PATH", pathId, ...(subjectCodes ? { subjectCodes } : {}) };
    }
    return { kind: "EXAM" };
  }
  const target = currentTarget();
  const canGenerate = target !== null && !subjectsIncomplete;

  function labelOf(result: PreviewResult, used: PreviewTarget): string {
    if (used.kind === "PATH") return pathLabel(result.path);
    if (used.kind === "SECTION") {
      const picked = sectionOptions.find((item) => item.sectionId === used.sectionId);
      const name = picked ? lookup.subject(picked.subjectCode) : "";
      return used.sectionQuestionCount !== undefined ? t("scope.sectionWithCount", { name, count: used.sectionQuestionCount }) : name;
    }
    return t("scope.kind.EXAM");
  }

  /** Sinh đề mới (không gửi `basePreviewId`) hoặc, khi có `keep`, đổi đường trên đề thử cũ và chỉ sinh lại các phần đã đổi. */
  function start(keep?: { basePreviewId: string; regeneratePartIds: string[]; seed: number }) {
    const used = currentTarget();
    if (!used) return;
    const nextRun = run + 1;
    setRun(nextRun);
    setTab("paper");
    setOriginal(false);
    setTrialErrorCode(null);
    setBlockedTrialHref(null);
    activePreviewIdRef.current = null;
    setLiveMessage(t("live.generating"));
    generate.mutate(
      {
        target: used,
        seed: keep?.seed ?? nextRun,
        version,
        ...(keep ? { basePreviewId: keep.basePreviewId, regeneratePartIds: keep.regeneratePartIds } : {}),
      },
      {
        onSuccess: (result) => {
          activePreviewIdRef.current = result.status === "GENERATED" ? (result.previewId ?? null) : null;
          setResultLabel(labelOf(result, used));
          setLiveMessage(
            result.status === "GENERATED"
              ? t("live.generated", { count: result.paper.totalQuestions })
              : t("live.failed", { path: labelOf(result, used) }),
          );
        },
        onError: (error) =>
          setLiveMessage(errorCodeOf(error) === "PREVIEW_EXPIRED" ? t("expired.title") : generateErrorText(error)),
      },
    );
  }

  // Mở từ bảng kết quả kiểm tra ("Sinh đề xem trước cho …"): sinh ngay một lần khi đã biết danh sách đường.
  const autoStarted = useRef(false);
  useEffect(() => {
    if (!autoGenerate || autoStarted.current || !canGenerate || options.length === 0) return;
    autoStarted.current = true;
    start();
  });

  /** Tên đường trong ô chọn: tên lựa chọn của mẫu nằm trong `ExamName`, chữ "(không sinh được)" ở ngoài. */
  const optionLabel = (option: PathOption): ReactNode => {
    const label = pathNode(option.path);
    if (option.result === "INVALID") return richNodes(t, "pathOptionInvalid", { path: label });
    if (option.result === "UNCONFIRMED") return richNodes(t, "pathOptionUnconfirmed", { path: label });
    return label;
  };
  /** Cùng nhãn nhưng là chữ thuần, để Radix gõ tìm trong danh sách (`SelectOption.textValue`). */
  const optionText = (option: PathOption): string => {
    const label = pathLabel(option.path);
    if (option.result === "INVALID") return t("pathOptionInvalid", { path: label });
    if (option.result === "UNCONFIRMED") return t("pathOptionUnconfirmed", { path: label });
    return label;
  };

  const data = generate.data;
  const shown = data?.status === "GENERATED" ? data : null;
  useEffect(() => {
    activePreviewIdRef.current = !generate.isPending && shown?.previewId ? shown.previewId : null;
    return () => {
      activePreviewIdRef.current = null;
    };
  }, [generate.isPending, shown?.previewId]);
  const extendedExpiresAt =
    shown?.previewId && trialExpires?.previewId === shown.previewId
      ? trialExpires.expiresAt
      : null;
  const effectiveExpiresAt = extendedExpiresAt ?? data?.expiresAt ?? null;
  const minutesLeft = usePreviewMinutesLeft(
    effectiveExpiresAt,
    extendedExpiresAt ? undefined : PREVIEW_RETENTION_MINUTES,
  );
  const expired = generate.isError && errorCodeOf(generate.error) === "PREVIEW_EXPIRED";
  const runStatus = data
    ? t(data.status === "GENERATED" ? "runStatus" : "runStatusFailed", {
        run,
        time: format.dateTime(new Date(data.generatedAt), "dateTime"),
      })
    : "";

  // Đổi mã trên cùng đề thử (R-15): chỉ các phần có lựa chọn khác nhau giữa hai đường phải sinh lại.
  const keepIds = shown && shown.previewId && kind === "PATH" && selectedPath ? changedPartIds(shown.path, selectedPath) : [];
  const canKeep = shown !== null && shown.previewId !== null && keepIds.length > 0 && canGenerate;
  const regeneratedParts = shown ? shown.paper.parts.filter((part) => keepIds.includes(part.partId)) : [];
  const keptParts = shown ? shown.paper.parts.filter((part) => !keepIds.includes(part.partId)) : [];
  const range = (items: typeof keptParts) =>
    items.length ? { first: Math.min(...items.map((part) => part.firstNumber)), last: Math.max(...items.map((part) => part.lastNumber)) } : null;
  const keptRange = range(keptParts);
  const regeneratedRange = range(regeneratedParts);

  let body;
  if (version !== null && versionQuery.isPending) {
    body = <Skeleton className="h-64" />;
  } else if (version !== null && versionQuery.isError) {
    body = (
      <Card>
        <EmptyState headingLevel={3} icon={<TriangleAlert />} title={t("versionErrorTitle")} />
      </Card>
    );
  } else if (options.length === 0 && sectionOptions.length === 0) {
    body = (
      <Card>
        <EmptyState headingLevel={3} icon={<Eye />} title={t("noPathsTitle")} description={t("noPathsDescription")} />
      </Card>
    );
  } else if (generate.isPending) {
    body = (
      <div className="flex flex-col gap-3" aria-busy="true">
        <Skeleton className="h-10 w-72 max-w-full" />
        <Skeleton className="h-40" />
        <Skeleton className="h-40" />
      </div>
    );
  } else if (expired) {
    body = (
      <Card>
        <EmptyState
          headingLevel={3}
          icon={<Clock />}
          title={t("expired.title")}
          description={t("expired.description", { minutes: PREVIEW_RETENTION_MINUTES })}
          action={
            <Button onClick={() => start()} disabled={!canGenerate}>
              <RefreshCw />
              {t("regenerate")}
            </Button>
          }
        />
      </Card>
    );
  } else if (generate.isError) {
    body = (
      <Card>
        <EmptyState
          headingLevel={3}
          icon={<TriangleAlert />}
          title={t("errorTitle")}
          description={generateErrorText(generate.error)}
          action={
            <Button variant="secondary" onClick={() => start()}>
              {t("retry")}
            </Button>
          }
        />
      </Card>
    );
  } else if (!data) {
    body = (
      <Card>
        <EmptyState headingLevel={3} icon={<Eye />} title={t("idleTitle")} description={t("idleDescription")} />
      </Card>
    );
  } else if (data.status === "FAILED") {
    body = <PreviewFailure exam={exam} result={data} label={resultLabel} onShowCheck={onShowCheck} />;
  } else {
    const canStartTrial = data.previewId !== null;
    const isExtendingCurrent = extendTrial.isPending && extendTrial.variables === data.previewId;
    body = (
      <div className="flex flex-col gap-4">
        <Card padding="md" className="flex flex-col gap-3">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="flex min-w-0 flex-col gap-0.5">
              <h3 className="text-title font-semibold text-text">{t("resultTitle", { label: resultLabel })}</h3>
              <p className="text-body text-text-secondary">
                {t("resultMeta", {
                  questions: data.paper.totalQuestions,
                  seed: data.seed,
                  retention: extendedExpiresAt
                    ? t("retentionExtended", {
                        time: format.dateTime(new Date(extendedExpiresAt), "dateTime"),
                        state: minutesLeft === null ? "unknown" : minutesLeft === 0 ? "expired" : "left",
                        left: minutesLeft ?? 0,
                      })
                    : t("retention", {
                        minutes: PREVIEW_RETENTION_MINUTES,
                        state: minutesLeft === null ? "unknown" : minutesLeft === 0 ? "expired" : "left",
                        left: minutesLeft ?? 0,
                      }),
                })}
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="secondary" size="sm" onClick={() => setOriginal((value) => !value)}>
                <ListOrdered />
                {original ? t("order.showShuffled") : t("order.showOriginal")}
              </Button>
              <Button
                ref={tryButtonRef}
                type="button"
                variant="primary"
                size="sm"
                disabled={!canStartTrial}
                loading={isExtendingCurrent}
                aria-describedby={!canStartTrial ? trialUnavailableId : undefined}
                onClick={() => {
                  if (!data.previewId || isExtendingCurrent) return;
                  setBlockedTrialHref(null);
                  setTrialErrorCode(null);
                  setLiveMessage("");
                  extendTrial.mutate(data.previewId);
                }}
              >
                <Play aria-hidden />
                {t("trial.button")}
              </Button>
            </div>
          </div>
          {!canStartTrial && (
            <p id={trialUnavailableId} className="text-caption text-text-muted">
              {t("trial.unavailable")}
            </p>
          )}
          {blockedTrialHref && (
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-warning/40 bg-warning-bg px-3 py-2 text-caption text-text">
              <span className="flex items-center gap-2">
                <Info aria-hidden className="size-4 shrink-0 text-warning" />
                <span>{t("trial.popupBlocked")}</span>
              </span>
              <a
                href={blockedTrialHref}
                target="_blank"
                rel="noopener noreferrer"
                onClick={() => {
                  setBlockedTrialHref(null);
                  tryButtonRef.current?.focus();
                }}
                className={cn(
                  "inline-flex min-h-8 items-center rounded-sm font-semibold text-primary-text underline pointer-coarse:min-h-11",
                  focusRing,
                )}
              >
                {t("trial.popupBlockedLink")}
              </a>
            </div>
          )}
          {trialErrorCode && (
            <div
              role="alert"
              className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger-bg px-3 py-2 text-caption text-danger"
            >
              <AlertCircle aria-hidden className="mt-0.5 size-4 shrink-0" />
              <span>
                {trialErrorCode === "PREVIEW_EXPIRED" ? t("trial.expiredError") : t("trial.genericError")}
              </span>
            </div>
          )}
        </Card>
        <Tabs variant="segmented" value={tab} onValueChange={(value) => setTab(value as "paper" | "allocation")}>
          <TabsList aria-label={t("tabs.label")} className="max-w-md">
            <TabsTrigger value="paper">{t("tabs.paper")}</TabsTrigger>
            <TabsTrigger value="allocation">{t("tabs.allocation")}</TabsTrigger>
          </TabsList>
          <TabsContent value="paper" className="flex flex-col gap-6">
            <ExamPreviewComplianceBanner
              paper={data.paper}
              compliance={data.compliance}
              examId={exam.id}
              onOpenAllocation={() => setTab("allocation")}
            />
            <ExamPreviewPaper paper={data.paper} original={original} />
          </TabsContent>
          <TabsContent value="allocation">
            <ExamPreviewAllocation
              paper={data.paper}
              compliance={data.compliance}
              dropped={data.droppedQuestions}
              examId={exam.id}
              original={original}
            />
          </TabsContent>
        </Tabs>
      </div>
    );
  }

  return (
    <section aria-labelledby="preview-title" className="flex flex-col gap-4">
      <h2 id="preview-title" className="sr-only">
        {t("title")}
      </h2>
      <LiveStatus message={liveMessage} />
      <p className="flex items-start gap-2.5 rounded-lg border border-border bg-subtle px-3.5 py-2.5 text-body text-text-secondary">
        <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
        <span>
          <strong className="font-semibold text-text">{t("noteTitle")}</strong> {t("note")}
        </span>
      </p>
      {version !== null && (
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-surface px-3.5 py-2.5">
          <p className="text-body text-text">{t("versionNote", { version })}</p>
          <Button variant="link" size="sm" onClick={onClearVersion}>
            {t("backToWorking")}
          </Button>
        </div>
      )}

      <Card padding="md" className="flex flex-col gap-3">
        <fieldset className="m-0 grid min-w-0 grid-cols-[repeat(auto-fit,minmax(min(16.25rem,100%),1fr))] gap-2.5 border-0 p-0">
          <legend className="sr-only">{t("scope.label")}</legend>
          {sectionOptions.length > 0 && (
            <PreviewScopeCard name={scopeName} value="SECTION" label={t("scope.kind.SECTION")} checked={kind === "SECTION"} onSelect={() => setPickedKind("SECTION")}>
              {section && (
                <Select
                  aria-label={t("scope.section")}
                  value={section.sectionId}
                  onValueChange={(value) => {
                    setPickedKind("SECTION");
                    setPickedSection(value);
                  }}
                  options={sectionOptions.map((item) => ({
                    value: item.sectionId,
                    label: richNodes(t, "scope.sectionOption", { subject: lookup.subject(item.subjectCode), part: <ExamName>{item.partName}</ExamName> }),
                    textValue: t("scope.sectionOption", { subject: lookup.subject(item.subjectCode), part: item.partName }),
                  }))}
                />
              )}
              {kind === "SECTION" && section?.counts && section.counts.length > 1 && sectionCount !== null && (
                <Select
                  aria-label={t("scope.count")}
                  value={String(sectionCount)}
                  onValueChange={(value) => setPickedCount(Number(value))}
                  options={section.counts.map((count) => ({ value: String(count), label: t("scope.countOption", { count, full: count === section.counts![0] ? "yes" : "no" }) }))}
                />
              )}
            </PreviewScopeCard>
          )}
          {choiceRequired && (
            <PreviewScopeCard name={scopeName} value="PATH" label={t("scope.kind.PATH")} checked={kind === "PATH"} onSelect={() => setPickedKind("PATH")}>
              <Select
                aria-label={t("pathLabel")}
                value={pathId ?? undefined}
                onValueChange={(value) => {
                  setPickedKind("PATH");
                  setSelected(value);
                }}
                disabled={options.length === 0}
                options={options.map((option) => ({ value: option.path.pathId, label: optionLabel(option), textValue: optionText(option) }))}
              />
            </PreviewScopeCard>
          )}
          <PreviewScopeCard name={scopeName} value="EXAM" label={t("scope.kind.EXAM")} checked={kind === "EXAM"} disabled={choiceRequired} onSelect={() => setPickedKind("EXAM")}>
            {choiceRequired && <span className="text-caption">{t("scope.examBlocked")}</span>}
          </PreviewScopeCard>
        </fieldset>
        <div className="flex flex-wrap items-center gap-2">
          <Button onClick={() => start()} loading={generate.isPending} disabled={!canGenerate}>
            {data ? <RefreshCw /> : <Eye />}
            {data ? t("regenerate") : t("generate")}
          </Button>
          {canKeep && shown?.previewId && (
            <Button
              variant="secondary"
              onClick={() => start({ basePreviewId: shown.previewId!, regeneratePartIds: keepIds, seed: shown.seed })}
              loading={generate.isPending}
            >
              {richNodes(t, "keep.change", { parts: <ExamName>{regeneratedParts.map((part) => part.name).join(", ")}</ExamName> })}
            </Button>
          )}
        </div>
        {subjectOption && (
          <ExamPreviewSubjects
            option={subjectOption}
            value={subjectCodes ?? []}
            onChange={(codes) => setPickedSubjects({ optionId: subjectOption.id, codes })}
          />
        )}
        {canKeep && shown && selectedPath && keptRange && regeneratedRange && (
          <p className="flex items-start gap-2 text-body text-text-secondary">
            <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
            {richNodes(t, "keep.note", {
              from: pathNode(shown.path),
              to: pathNode(selectedPath),
              kept: <ExamName>{keptParts.map((part) => part.name).join(", ")}</ExamName>,
              keptFirst: keptRange.first,
              keptLast: keptRange.last,
              regenerated: <ExamName>{regeneratedParts.map((part) => part.name).join(", ")}</ExamName>,
              regeneratedFirst: regeneratedRange.first,
              regeneratedLast: regeneratedRange.last,
            })}
          </p>
        )}
        {runStatus && <p className="text-caption text-text-muted">{runStatus}</p>}
      </Card>

      {body}

      <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-body text-text-muted">
        <Info aria-hidden className="size-4 shrink-0" />
        <span>{t("footer")}</span>
        <Button variant="link" size="sm" onClick={onShowCheck}>
          {t("footerLink")}
        </Button>
      </p>
    </section>
  );
}

function PreviewFailure({
  exam,
  result,
  label,
  onShowCheck,
}: {
  exam: ExamDetail;
  result: Extract<PreviewResult, { status: "FAILED" }>;
  label: string;
  onShowCheck: () => void;
}) {
  const t = useTranslations("exams.preview");
  const unavailableId = useId();
  const issueText = useIssueText(exam);
  const issueContent = useIssueText(exam, true);
  const invalid = result.reason === "INVALID";
  const fixHref = result.issues.map((issue) => issueStructureHref(exam.id, issue)).find((href) => href !== null) ?? null;
  const Icon = invalid ? CircleX : CircleHelp;
  return (
    <div
      className={`flex items-start gap-3 rounded-lg border p-4 ${invalid ? "border-danger/30 bg-danger-bg" : "border-warning/30 bg-warning-bg"}`}
    >
      <Icon aria-hidden className={`mt-0.5 size-5 shrink-0 ${invalid ? "text-danger" : "text-warning"}`} />
      <div className="flex min-w-0 flex-col gap-1.5">
        <h3 className="text-title font-semibold text-text">
          {t(invalid ? "failInvalidTitle" : "failUnconfirmedTitle", { path: label })}
        </h3>
        <ul className="flex flex-col gap-1 text-body text-text">
          {result.issues.map((issue) => (
            <PreviewIssueItem key={issue.key} examId={exam.id} issue={issue} text={issueText(issue)} content={issueContent(issue)} />
          ))}
        </ul>
        <p className="text-body text-text-secondary">{t("failNote")}</p>
        <div className="flex flex-wrap items-center gap-3">
          {invalid ? (
            <Button asChild variant="link" size="sm">
              <Link href={fixHref ?? `/exams/${encodeURIComponent(exam.id)}/structure`}>
                {t("failFixLink")}
                <ArrowRight />
              </Link>
            </Button>
          ) : (
            <Button variant="link" size="sm" onClick={onShowCheck}>
              {t("failCheckLink")}
              <ArrowRight />
            </Button>
          )}
          <Button
            type="button"
            variant="secondary"
            size="sm"
            disabled
            aria-describedby={unavailableId}
          >
            <Play aria-hidden />
            {t("trial.button")}
          </Button>
        </div>
        <p id={unavailableId} className="text-caption text-text-muted">
          {t("trial.unavailable")}
        </p>
      </div>
    </div>
  );
}

/** Một vấn đề của lần sinh thử; vấn đề nêu quy tắc MAPID có thêm liên kết tới dòng quy tắc ở trang Cấu trúc. */
export function PreviewIssueItem({ examId, issue, text, content = text }: { examId: string; issue: CheckIssue; text: string; content?: string }) {
  const tMapid = useTranslations("exams.mapidRules.issue");
  const href = issueRuleHref(examId, issue);
  return (
    <li className="flex flex-wrap items-baseline gap-x-2">
      <span><ExamContentText text={content} /></span>
      {href && (
        <Button asChild variant="link" size="sm">
          <Link href={href} aria-label={tMapid("goToRuleAria", { issue: text })}>
            {tMapid("goToRule")}
          </Link>
        </Button>
      )}
    </li>
  );
}
