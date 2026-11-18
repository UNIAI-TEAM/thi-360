"use client";

import type { ColumnDef } from "@tanstack/react-table";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  IconButton,
  ListCard,
  LiveStatus,
  Skeleton,
  StatusChip,
  Table,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  type TableCards,
} from "@thi360/ui";
import { ArrowRight, ClipboardCheck, Eye, Info, LoaderCircle, RefreshCw, SearchCheck, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { createContext, type RefObject, useCallback, useContext, useMemo, useRef, useState } from "react";
import { EXTENDED_CHECK_TIME_LIMIT_SECONDS } from "../check";
import type { ExamReviewApiDeps } from "../review-api";
import { type DeepCheck, useDeepCheck, useExamCheck, useRunExamCheck } from "../review-queries";
import type {
  BlockCheck,
  CheckGroupResult,
  ExamCheckResult,
  PathCheck,
  PathCheckResult,
  PathSelectionCheck,
} from "../review-types";
import type { ExamDetail } from "../types";
import {
  BLOCK_RESULT_TONE,
  IconLink,
  PathResultChip,
  PathResultIcon,
  structureHref,
  useIssueText,
  useIssueWithPath,
  useLookup,
  usePathLabel,
} from "./exam-review-shared";
import { ExamReviewFindings } from "./exam-review-findings";

export interface ExamReviewCheckProps {
  exam: ExamDetail;
  /** Chuyển sang tab Đề xem trước và sinh đề cho đường này (`null`: giữ đường đang chọn). */
  onPreview: (pathId: string | null) => void;
  /** `false` khi tab bị che: kiểm tra kỹ ngừng hỏi kết quả, hỏi tiếp khi quay lại. */
  active?: boolean;
  deps?: ExamReviewApiDeps;
}

/**
 * Kiểm tra cấu hình (bản vẽ `BaiThiKiemTra`): kết quả theo từng đường lựa chọn, tám nhóm kiểm tra, chi tiết theo khối
 * của từng đường, liên kết tới chỗ cần sửa.
 */
export function ExamReviewCheck({ exam, onPreview, active = true, deps }: ExamReviewCheckProps) {
  const t = useTranslations("exams.review");
  const checkQuery = useExamCheck(exam.id, deps);
  const runCheck = useRunExamCheck(exam.id, deps);
  const [liveMessage, setLiveMessage] = useState("");
  const blocksRef = useRef<HTMLHeadingElement>(null);
  const [blockTab, setBlockTab] = useState<string | null>(null);

  const announceDone = (result: ExamCheckResult) => {
    const count = (value: PathCheckResult) => result.paths.filter((path) => path.result === value).length;
    setLiveMessage(
      t("live.done", { valid: count("VALID"), invalid: count("INVALID"), unconfirmed: count("UNCONFIRMED") }),
    );
  };
  const deep = useDeepCheck(exam.id, { active, deps, onDone: announceDone });
  // Kiểm tra kỹ đang chạy hay vừa lỗi thì thông báo theo trạng thái; còn lại là câu của lần chạy gần nhất.
  const announcement =
    deep.phase === "running" ? t("deep.live.running") : deep.phase === "failed" ? t("deep.live.failed") : liveMessage;

  function run() {
    setLiveMessage(t("live.running"));
    runCheck.mutate(
      {},
      { onSuccess: announceDone, onError: () => setLiveMessage(t("runError")) },
    );
  }

  function showOverlap(pathId: string) {
    setBlockTab(pathId);
    blocksRef.current?.scrollIntoView({ block: "start" });
    blocksRef.current?.focus();
  }

  let body;
  if (checkQuery.isPending) {
    body = (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-14" />
        <Skeleton className="h-16" />
        <Skeleton className="h-64" />
      </div>
    );
  } else if (checkQuery.isError) {
    body = (
      <Card>
        <EmptyState
          headingLevel={2}
          icon={<TriangleAlert />}
          title={t("errorTitle")}
          description={t("errorDescription")}
          action={
            <Button variant="secondary" onClick={() => void checkQuery.refetch()}>
              {t("retry")}
            </Button>
          }
        />
      </Card>
    );
  } else if (!checkQuery.data) {
    body = (
      <Card>
        <EmptyState
          headingLevel={2}
          icon={<ClipboardCheck />}
          title={t("emptyTitle")}
          description={t("emptyDescription")}
          action={
            <Button onClick={() => run()} loading={runCheck.isPending}>
              <ClipboardCheck />
              {t("run")}
            </Button>
          }
        />
        {runCheck.isError && <p className="text-center text-body text-danger">{t("runError")}</p>}
      </Card>
    );
  } else {
    body = (
      <CheckResultView
        exam={exam}
        result={checkQuery.data}
        running={runCheck.isPending}
        runFailed={runCheck.isError}
        deep={deep}
        onRun={run}
        onPreview={onPreview}
        onShowOverlap={showOverlap}
        blocksRef={blocksRef}
        blockTab={blockTab}
        onBlockTabChange={setBlockTab}
      />
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <LiveStatus message={announcement} />
      {body}
    </div>
  );
}

interface CheckResultViewProps {
  exam: ExamDetail;
  result: ExamCheckResult;
  running: boolean;
  runFailed: boolean;
  deep: DeepCheck;
  onRun: () => void;
  onPreview: (pathId: string | null) => void;
  onShowOverlap: (pathId: string) => void;
  blocksRef: RefObject<HTMLHeadingElement | null>;
  blockTab: string | null;
  onBlockTabChange: (pathId: string) => void;
}

function CheckResultView({
  exam,
  result,
  running,
  runFailed,
  deep,
  onRun,
  onPreview,
  onShowOverlap,
  blocksRef,
  blockTab,
  onBlockTabChange,
}: CheckResultViewProps) {
  const t = useTranslations("exams.review");
  const format = useFormatter();
  const stale = result.stale || exam.check.status === "NEEDS_RECHECK";
  const validCount = result.paths.filter((path) => path.result === "VALID").length;
  const first = result.paths[0];
  const deepRunning = deep.phase === "running";
  // Chưa xác nhận được ở cả đường lẫn từng cách chọn môn (đường Không hợp lệ vẫn có thể còn cách chọn chưa xác nhận).
  const hasUnconfirmed = result.paths.some(
    (path) => path.result === "UNCONFIRMED" || path.selections.some((selection) => selection.result === "UNCONFIRMED"),
  );

  return (
    <>
      <Card padding="md" className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <p className="flex items-start gap-2 text-body text-text-secondary">
            <RefreshCw aria-hidden className="mt-0.5 size-4 shrink-0" />
            <span>
              {t("lastRun", {
                time: format.dateTime(new Date(result.checkedAt), "dateTime"),
                seconds: result.durationSeconds,
                limit: result.timeLimitSeconds,
              })}
            </span>
          </p>
          {stale && (
            <p className="flex items-start gap-2 text-body text-warning">
              <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
              <span>{result.invalidReason ? t(`staleReason.${result.invalidReason}`) : t("stale")}</span>
            </p>
          )}
          {runFailed && <p className="text-body text-danger">{t("runError")}</p>}
          {hasUnconfirmed && !deepRunning && (
            <p className="flex items-start gap-2 text-body text-text-secondary">
              <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
              <span>{t("deep.hint", { seconds: EXTENDED_CHECK_TIME_LIMIT_SECONDS })}</span>
            </p>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          {hasUnconfirmed && (
            <Button
              variant="outline"
              onClick={deep.start}
              loading={deepRunning}
              disabled={running}
              aria-label={deepRunning ? t("deep.runningLabel") : undefined}
            >
              <SearchCheck />
              {deepRunning ? t("deep.runningLabel") : t("deep.start")}
            </Button>
          )}
          <Button variant="secondary" onClick={onRun} loading={running} disabled={deepRunning}>
            <RefreshCw />
            {t("rerun")}
          </Button>
        </div>
      </Card>
      <DeepCheckStatus deep={deep} />

      <dl aria-label={t("summary.label")} className="flex flex-wrap gap-x-6 gap-y-2 text-body">
        <SummaryItem
          label={t("summary.paths")}
          value={t("summary.pathsValue", {
            valid: validCount,
            total: result.paths.length,
          })}
        />
        <SummaryItem
          label={t("summary.questions")}
          value={
            result.printedQuestions !== (first?.totalQuestions ?? 0)
              ? t("summary.questionsPrinted", { count: first?.totalQuestions ?? 0, printed: result.printedQuestions })
              : format.number(first?.totalQuestions ?? 0)
          }
        />
        <SummaryItem
          label={t("summary.minutes")}
          value={t("summary.minutesValue", {
            count: first?.totalMinutes ?? 0,
            shared: exam.timing === "SHARED" ? "yes" : "no",
          })}
        />
        <SummaryItem
          label={t("summary.points")}
          value={
            result.maxScore !== null
              ? t("summary.pointsOfScale", {
                  points: format.number(result.totalPoints, { maximumFractionDigits: 2 }),
                  scale: format.number(result.maxScore, { maximumFractionDigits: 2 }),
                })
              : format.number(result.totalPoints, { maximumFractionDigits: 2 })
          }
        />
        <SummaryItem
          label={t("summary.issues")}
          value={result.issueCount ? t("summary.issuesValue", { count: result.issueCount }) : t("summary.none")}
        />
      </dl>

      <ExamReviewFindings exam={exam} result={result} />
      <PathsSection
        exam={exam}
        result={result}
        checking={running}
        deep={deep}
        onPreview={onPreview}
        onShowOverlap={onShowOverlap}
      />
      {result.paths
        .filter((path) => path.selections.length > 0)
        .map((path) => (
          <SelectionsSection key={path.pathId} exam={exam} path={path} />
        ))}
      <GroupsSection exam={exam} result={result} />
      <BlocksSection
        exam={exam}
        result={result}
        headingRef={blocksRef}
        value={blockTab}
        onValueChange={onBlockTabChange}
      />

      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="secondary" onClick={onRun} loading={running} disabled={deepRunning}>
          <RefreshCw />
          {t("rerun")}
        </Button>
        <Button variant="outline" onClick={() => onPreview(null)}>
          <Eye />
          {t("toPreview")}
        </Button>
      </div>
    </>
  );
}

function SummaryItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline gap-1.5">
      <dt className="text-text-muted">{label}</dt>
      <dd className="m-0 font-semibold text-text tabular-nums">{value}</dd>
    </div>
  );
}

/** Kiểm tra kỹ chạy nền: đang chạy (kết quả cũ còn hiện bên dưới tới khi xong) hoặc lỗi kèm thử lại. */
function DeepCheckStatus({ deep }: { deep: DeepCheck }) {
  const t = useTranslations("exams.review.deep");
  if (deep.phase === "idle") return null;
  if (deep.phase === "running") {
    return (
      <Card padding="md" className="flex items-start gap-3" aria-busy="true">
        <LoaderCircle
          aria-hidden
          className="mt-0.5 size-5 shrink-0 animate-spin text-text-secondary motion-reduce:animate-[spin_6s_linear_infinite]"
        />
        <div className="flex min-w-0 flex-col gap-0.5">
          <p className="font-semibold text-text">{t("running.title")}</p>
          <p className="text-body text-text-secondary">
            {t("running.description", { seconds: EXTENDED_CHECK_TIME_LIMIT_SECONDS })}
          </p>
        </div>
      </Card>
    );
  }
  return (
    <div className="flex flex-wrap items-start justify-between gap-3 rounded-lg border border-danger/30 bg-danger-bg p-4">
      <div className="flex min-w-0 items-start gap-3">
        <TriangleAlert aria-hidden className="mt-0.5 size-5 shrink-0 text-danger" />
        <div className="flex min-w-0 flex-col gap-0.5">
          <p className="font-semibold text-text">{t("failed.title")}</p>
          <p className="text-body text-text-secondary">
            {t(deep.errorCode === "CHECK_RUN_ABANDONED" ? "failed.abandoned" : "failed.generic")}
          </p>
        </div>
      </div>
      <Button variant="secondary" onClick={deep.start}>
        <RefreshCw />
        {t("failed.retry")}
      </Button>
    </div>
  );
}

/**
 * Kết quả từng cách chọn môn của đường có `SUBJECT_CHOICE` (spec 5.3, B10): mỗi bộ môn học viên có thể chọn một dòng
 * kèm số câu từng môn; bộ ba môn cùng nhóm có đủ ba cách chia. Quy tắc chia chỉ đọc.
 */
function SelectionsSection({ exam, path }: { exam: ExamDetail; path: PathCheck }) {
  const t = useTranslations("exams.review.selections");
  const tReview = useTranslations("exams.review");
  const lookup = useLookup();
  const issueText = useIssueText();
  const pathLabel = usePathLabel(exam.id);
  const choice = path.choices.find((item) => item.kind === "SUBJECT_CHOICE");
  const option = exam.parts
    .find((part) => part.id === choice?.partId)
    ?.options.find((item) => item.id === choice?.optionId);
  const counts = option?.countsByPick ?? [];
  const tally = (value: PathCheckResult) => path.selections.filter((selection) => selection.result === value).length;
  const titleId = `review-selections-${path.pathId}`;
  const label = pathLabel(path);

  const { columns, cards } = useMemo(() => {
    const picked = (selection: PathSelectionCheck) =>
      selection.subjectCodes.map((code, index) => `${lookup.subject(code)} ${selection.counts[index] ?? ""}`).join(" · ");
    const issueCell = (selection: PathSelectionCheck) => {
      const unique = [...new Map(selection.issues.map((issue) => [issue.key, issue])).values()];
      if (unique.length === 0) return <span className="text-text-muted">{tReview("noIssue")}</span>;
      return (
        <span className={selection.result === "INVALID" ? "text-danger" : "text-text-secondary"}>
          {issueText(unique[0]!)}
          {unique.length > 1 && <> {tReview("moreIssues", { count: unique.length - 1 })}</>}
        </span>
      );
    };
    const columns: ColumnDef<PathSelectionCheck>[] = [
      {
        id: "subjects",
        header: t("columns.subjects"),
        cell: ({ row }) => <span className="block min-w-56 font-semibold text-text tabular-nums">{picked(row.original)}</span>,
      },
      {
        id: "result",
        header: t("columns.result"),
        cell: ({ row }) => <PathResultChip result={row.original.result} />,
      },
      {
        id: "issue",
        header: t("columns.issue"),
        cell: ({ row }) => <div className="min-w-56">{issueCell(row.original)}</div>,
      },
    ];
    const cards: TableCards<PathSelectionCheck> = {
      render: (selection) => (
        <ListCard title={picked(selection)} status={<PathResultChip result={selection.result} />}>
          <p className="text-body">{issueCell(selection)}</p>
        </ListCard>
      ),
    };
    return { columns, cards };
  }, [t, tReview, lookup, issueText]);

  return (
    <section aria-labelledby={titleId} className="flex flex-col gap-3">
      <div>
        <h2 id={titleId} className="text-h3 font-bold text-text">
          {t("title", { path: label })}
        </h2>
        <p className="text-body text-text-muted">
          {t("summary", {
            total: path.selections.length,
            valid: tally("VALID"),
            invalid: tally("INVALID"),
            unconfirmed: tally("UNCONFIRMED"),
          })}
        </p>
        {option?.chooseSubjects && counts.length > 0 && (
          <p className="flex items-start gap-2 text-body text-text-muted">
            <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
            {t("rule", { choose: option.chooseSubjects, high: Math.max(...counts), low: Math.min(...counts) })}
          </p>
        )}
      </div>
      <Card padding="none" className="overflow-hidden">
        <Table
          aria-label={t("tableLabel", { path: label })}
          columns={columns}
          data={path.selections}
          getRowId={(selection) => `${selection.subjectCodes.join("+")}:${selection.counts.join("/")}`}
          empty={<EmptyState headingLevel={3} title={t("empty")} />}
          cards={cards}
        />
      </Card>
    </section>
  );
}

/** Dòng phụ của một đường: số câu, thời gian; đường Khoa học nêu số bộ môn và số trường hợp; tổ hợp nêu số câu từng môn. */
function usePathSub() {
  const t = useTranslations("exams.review");
  return useCallback(
    (path: PathCheck) => {
      const base = t("paths.sub", {
        questions: path.totalQuestions,
        minutes: path.totalMinutes,
      });
      if (path.selections.length > 0) {
        const picks = new Set(path.selections.map((selection) => selection.subjectCodes.join())).size;
        return `${base} · ${t("paths.subSelections", { picks, cases: path.selections.length })}`;
      }
      const optionIds = new Set(path.choices.map((choice) => choice.optionId));
      const perSection = new Map<string, number>();
      for (const block of path.blocks) {
        if (block.optionId && optionIds.has(block.optionId)) {
          perSection.set(block.sectionId, (perSection.get(block.sectionId) ?? 0) + block.required);
        }
      }
      const split = perSection.size > 1 ? [...perSection.values()].join("–") : "";
      return split ? `${base} · ${split}` : base;
    },
    [t],
  );
}

interface PathActionsValue {
  examId: string;
  deep: DeepCheck;
  /** `true` khi lần kiểm tra thường đang chạy: Kiểm tra kỹ tắt để hai lần chạy không ghi đè cùng khóa kết quả. */
  checking: boolean;
  onPreview: (pathId: string | null) => void;
  onShowOverlap: (pathId: string) => void;
}

const PathActionsContext = createContext<PathActionsValue | null>(null);

/** Thao tác theo kết quả của một đường: xem trước (hợp lệ), đến dòng ma trận (không hợp lệ), kiểm tra kỹ (chưa xác nhận). */
function PathActions({ path }: { path: PathCheck }) {
  const t = useTranslations("exams.review");
  const context = useContext(PathActionsContext);
  const pathLabel = usePathLabel(context?.examId);
  if (!context) return null;
  const { examId, deep, checking, onPreview, onShowOverlap } = context;
  const label = pathLabel(path);
  if (path.result === "VALID") {
    return (
      <IconButton
        icon={<Eye />}
        aria-label={t("paths.previewAria", { path: label })}
        tooltip={t("paths.previewTooltip")}
        onClick={() => onPreview(path.pathId)}
      />
    );
  }
  if (path.result === "INVALID") {
    const blockId = path.issues.find((issue) => issue.blockId)?.blockId ?? null;
    return (
      <Button asChild variant="link" size="sm">
        <Link href={structureHref(examId, blockId)} aria-label={t("paths.fixAria", { path: label })}>
          {t("paths.fix")}
          <ArrowRight />
        </Link>
      </Button>
    );
  }
  return (
    <div className="flex flex-col items-start gap-1">
      <Button
        variant="secondary"
        size="sm"
        onClick={deep.start}
        loading={deep.phase === "running"}
        disabled={checking}
        aria-label={
          deep.phase === "running" ? t("deep.runningLabel") : t("paths.deepAria", { path: label })
        }
      >
        {deep.phase === "running" ? t("deep.runningLabel") : t("deep.start")}
      </Button>
      <Button variant="link" size="sm" onClick={() => onShowOverlap(path.pathId)}>
        {t("paths.viewOverlap")}
      </Button>
    </div>
  );
}
function PathsSection({
  exam,
  result,
  checking,
  deep,
  onPreview,
  onShowOverlap,
}: {
  exam: ExamDetail;
  result: ExamCheckResult;
  checking: boolean;
  deep: DeepCheck;
  onPreview: (pathId: string | null) => void;
  onShowOverlap: (pathId: string) => void;
}) {
  const t = useTranslations("exams.review");
  const pathLabel = usePathLabel(exam.id);
  const pathSub = usePathSub();
  const issueText = useIssueText();
  const partsList = [...exam.parts]
    .sort((a, b) => a.order - b.order)
    .map((part) => part.order)
    .join(", ");
  // Thao tác đọc trạng thái chạy và các hàm gọi lại qua context, để cột của bảng giữ nguyên giữa các lần render:
  // bảng dựng lại ô mỗi khi định nghĩa cột đổi, nút đang được focus sẽ mất focus.
  const actionsValue = useMemo(
    () => ({ examId: exam.id, deep, checking, onPreview, onShowOverlap }),
    [exam.id, deep, checking, onPreview, onShowOverlap],
  );

  const { columns, cards } = useMemo(() => {
    const issueCell = (path: PathCheck) => {
      const unique = [...new Map(path.issues.map((issue) => [issue.key, issue])).values()];
      if (unique.length === 0) return <span className="text-text-muted">{t("noIssue")}</span>;
      return (
        <span className={path.result === "INVALID" ? "text-danger" : "text-text-secondary"}>
          {issueText(unique[0]!)}
          {unique.length > 1 && <> {t("moreIssues", { count: unique.length - 1 })}</>}
        </span>
      );
    };

    const columns: ColumnDef<PathCheck>[] = [
      {
        id: "path",
        header: t("paths.columns.path"),
        cell: ({ row }) => (
          <div className="flex min-w-48 flex-col">
            <span className="font-semibold text-text">{pathLabel(row.original)}</span>
            <span className="text-caption text-text-muted">{pathSub(row.original)}</span>
          </div>
        ),
      },
      {
        id: "parts",
        header: t("paths.columns.parts"),
        cell: () => <span className="whitespace-nowrap">{t("paths.partsValue", { list: partsList })}</span>,
      },
      {
        id: "result",
        header: t("paths.columns.result"),
        cell: ({ row }) => <PathResultChip result={row.original.result} />,
      },
      {
        id: "issue",
        header: t("paths.columns.issue"),
        cell: ({ row }) => <div className="min-w-56">{issueCell(row.original)}</div>,
      },
      {
        id: "actions",
        header: t("paths.columns.actions"),
        cell: ({ row }) => <PathActions path={row.original} />,
      },
    ];

    const cards: TableCards<PathCheck> = {
      render: (path) => (
        <ListCard
          title={pathLabel(path)}
          caption={pathSub(path)}
          status={<PathResultChip result={path.result} />}
          actions={path.result === "VALID" ? <PathActions path={path} /> : undefined}
        >
          <p className="text-body">{issueCell(path)}</p>
          {path.result !== "VALID" && (
            <div className="relative z-1">
              <PathActions path={path} />
            </div>
          )}
        </ListCard>
      ),
    };
    return { columns, cards };
  }, [t, pathLabel, pathSub, issueText, partsList]);

  return (
    <section aria-labelledby="review-paths-title" className="flex flex-col gap-3">
      <div>
        <h2 id="review-paths-title" className="text-h3 font-bold text-text">
          {t("paths.title")}
        </h2>
        <p className="text-body text-text-muted">{t("paths.description")}</p>
      </div>
      <Card padding="none" className="overflow-hidden">
        <PathActionsContext value={actionsValue}>
          <Table
            aria-label={t("paths.tableLabel", { count: result.paths.length })}
            columns={columns}
            data={result.paths}
            getRowId={(path) => path.pathId}
            empty={<EmptyState headingLevel={3} title={t("paths.empty")} />}
            cards={cards}
          />
        </PathActionsContext>
      </Card>
      <Legend />
    </section>
  );
}

function Legend() {
  const t = useTranslations("exams.review");
  const results: PathCheckResult[] = ["VALID", "INVALID", "UNCONFIRMED"];
  return (
    <Card padding="md" className="flex flex-col gap-2">
      <h3 className="text-label font-semibold text-text">{t("legend.title")}</h3>
      <ul className="flex flex-col gap-1.5">
        {results.map((value) => (
          <li key={value} className="flex items-start gap-2 text-body text-text-secondary">
            <PathResultIcon result={value} className="mt-0.5 size-4 shrink-0" />
            <span>
              <strong className="font-semibold text-text">{t(`result.${value}`)}:</strong> {t(`legend.${value}`)}
            </span>
          </li>
        ))}
      </ul>
      <p className="flex items-start gap-2 text-body text-text-muted">
        <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
        {t("legend.note")}
      </p>
    </Card>
  );
}

function GroupsSection({ exam, result }: { exam: ExamDetail; result: ExamCheckResult }) {
  const t = useTranslations("exams.review");
  const issueWithPath = useIssueWithPath(result.paths);
  const first = result.paths[0];

  const passText = (group: CheckGroupResult) =>
    t(`groups.pass.${group.group}`, {
      parts: result.partCount,
      paths: result.paths.length,
      questions: first?.totalQuestions ?? exam.totalQuestions,
      minutes: first?.totalMinutes ?? exam.durationMinutes,
      points: result.totalPoints,
      scale: exam.maxScore ?? 0,
      hasScale: exam.maxScore === null ? "no" : "yes",
    });

  return (
    <section aria-labelledby="review-groups-title" className="flex flex-col gap-3">
      <h2 id="review-groups-title" className="text-h3 font-bold text-text">
        {t("groups.title")}
      </h2>
      <ul className="@container grid grid-cols-[repeat(auto-fill,minmax(min(22rem,100%),1fr))] gap-3">
        {result.groups.map((group) => {
          const tone: PathCheckResult =
            group.status === "PASS" ? "VALID" : group.status === "FAIL" ? "INVALID" : "UNCONFIRMED";
          return (
            <li key={group.group} className="flex items-start gap-3 rounded-lg border border-border bg-surface p-4">
              <PathResultIcon result={tone} className="mt-0.5 size-[18px] shrink-0" />
              <div className="flex min-w-0 flex-col gap-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold text-text">{t(`groups.name.${group.group}`)}</span>
                  <StatusChip
                    tone={group.status === "PASS" ? "success" : group.status === "FAIL" ? "danger" : "warning"}
                  >
                    {group.status === "PASS" ? t("groups.passed") : t("groups.issues", { count: group.issues.length })}
                  </StatusChip>
                </div>
                <p className="text-body text-text-secondary">
                  {group.issues.length ? issueWithPath(group.issues[0]!) : passText(group)}
                </p>
                {group.issues.length > 1 && (
                  <p className="text-caption text-text-muted">{t("moreIssues", { count: group.issues.length - 1 })}</p>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function BlocksSection({
  exam,
  result,
  headingRef,
  value,
  onValueChange,
}: {
  exam: ExamDetail;
  result: ExamCheckResult;
  headingRef: RefObject<HTMLHeadingElement | null>;
  value: string | null;
  onValueChange: (pathId: string) => void;
}) {
  const t = useTranslations("exams.review");
  const pathLabel = usePathLabel(exam.id);
  const defaultTab = (result.paths.find((path) => path.result !== "VALID") ?? result.paths[0])?.pathId ?? "";
  const active = value && result.paths.some((path) => path.pathId === value) ? value : defaultTab;
  const tabLabel = (path: PathCheck) =>
    path.choices.length
      ? path.choices.map((choice) => (choice.kind === "COMBINATION" ? choice.code : choice.name)).join(" + ")
      : t("path.single");

  return (
    <section aria-labelledby="review-blocks-title" className="flex flex-col gap-3">
      <div>
        <h2
          id="review-blocks-title"
          ref={headingRef}
          tabIndex={-1}
          className="text-h3 font-bold text-text outline-hidden"
        >
          {t("blocks.title")}
        </h2>
        <p className="text-body text-text-muted">{t("blocks.description")}</p>
      </div>
      <Tabs value={active} onValueChange={onValueChange}>
        <TabsList aria-label={t("blocks.tabsLabel")}>
          {result.paths.map((path) => (
            <TabsTrigger key={path.pathId} value={path.pathId} className="gap-1.5">
              {path.result !== "VALID" && <PathResultIcon result={path.result} className="size-3.5 shrink-0" />}
              {tabLabel(path)}
              {path.result !== "VALID" && <span className="sr-only">{`: ${t(`result.${path.result}`)}`}</span>}
            </TabsTrigger>
          ))}
        </TabsList>
        {result.paths.map((path) => (
          <TabsContent key={path.pathId} value={path.pathId}>
            <BlockTable exam={exam} path={path} label={pathLabel(path)} />
          </TabsContent>
        ))}
      </Tabs>
    </section>
  );
}

function BlockTable({ exam, path, label }: { exam: ExamDetail; path: PathCheck; label: string }) {
  const t = useTranslations("exams.review");
  const format = useFormatter();
  const lookup = useLookup();
  const issueText = useIssueText();

  const { columns, cards } = useMemo(() => {
    const name = (block: BlockCheck) => `${lookup.subject(block.subjectCode)} · ${block.blockName}`;
    const required = (block: BlockCheck) =>
      block.setCount !== null && block.setSize !== null
        ? t("blocks.requiredSets", {
            questions: block.required,
            sets: block.setCount,
            size: block.setSize,
          })
        : format.number(block.required);
    const source = (block: BlockCheck) =>
      block.availableSets !== null
        ? t("blocks.sourceSets", { count: block.availableSets })
        : format.number(block.available);
    const issue = (block: BlockCheck) =>
      block.issues.length ? (
        <span className={block.result === "NOT_FOUND" ? "text-danger" : "text-text-secondary"}>
          {block.issues.map(issueText).join(" ")}
        </span>
      ) : (
        <span className="text-text-muted">{t("noIssue")}</span>
      );
    const location = (block: BlockCheck) =>
      block.result === "NOT_FOUND" ? (
        <Button asChild variant="link" size="sm">
          <Link href={structureHref(exam.id, block.blockId)} aria-label={t("blocks.fixAria", { block: name(block) })}>
            {t("paths.fix")}
            <ArrowRight />
          </Link>
        </Button>
      ) : (
        <IconLink
          href={structureHref(exam.id, block.blockId)}
          label={t("blocks.openAria", { block: name(block) })}
          tooltip={t("blocks.openTooltip")}
          icon={<ArrowRight />}
        />
      );
    const chip = (block: BlockCheck) => (
      <StatusChip tone={BLOCK_RESULT_TONE[block.result]}>{t(`blocks.result.${block.result}`)}</StatusChip>
    );

    const columns: ColumnDef<BlockCheck>[] = [
      {
        id: "block",
        header: t("blocks.columns.block"),
        cell: ({ row }) => (
          <span className="flex min-w-44 flex-col">
            <span className="text-caption text-text-muted">{t("blocks.part", { order: row.original.partOrder })}</span>
            <span className="font-semibold text-text">{name(row.original)}</span>
            {row.original.questionType === "ESSAY" && (
              <Badge className="mt-1 w-fit">{t("blocks.manualGrading")}</Badge>
            )}
          </span>
        ),
      },
      {
        id: "required",
        header: t("blocks.columns.required"),
        cell: ({ row }) => <span className="tabular-nums">{required(row.original)}</span>,
      },
      {
        id: "source",
        header: t("blocks.columns.source"),
        cell: ({ row }) => <span className="tabular-nums">{source(row.original)}</span>,
      },
      {
        id: "result",
        header: t("blocks.columns.result"),
        cell: ({ row }) => chip(row.original),
      },
      {
        id: "issue",
        header: t("blocks.columns.issue"),
        cell: ({ row }) => <div className="min-w-56">{issue(row.original)}</div>,
      },
      {
        id: "location",
        header: t("blocks.columns.location"),
        cell: ({ row }) => location(row.original),
      },
    ];

    const cards: TableCards<BlockCheck> = {
      render: (block) => (
        <ListCard
          title={name(block)}
          caption={t("blocks.part", { order: block.partOrder })}
          status={chip(block)}
          actions={block.result === "NOT_FOUND" ? undefined : location(block)}
          meta={[
            { label: t("blocks.columns.required"), value: required(block) },
            { label: t("blocks.columns.source"), value: source(block) },
          ]}
        >
          {block.issues.length > 0 && <p className="text-body">{issue(block)}</p>}
          {block.result === "NOT_FOUND" && <div className="relative z-1">{location(block)}</div>}
        </ListCard>
      ),
    };
    return { columns, cards };
  }, [t, format, lookup, issueText, exam.id]);

  return (
    <Card padding="none" className="overflow-hidden">
      <Table
        aria-label={t("blocks.tableLabel", { path: label })}
        columns={columns}
        data={path.blocks}
        getRowId={(block) => block.blockId}
        empty={<EmptyState headingLevel={3} title={t("blocks.empty")} />}
        cards={cards}
      />
    </Card>
  );
}
