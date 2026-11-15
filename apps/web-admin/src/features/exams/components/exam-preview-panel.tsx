"use client";

import {
  Button,
  Card,
  EmptyState,
  FormField,
  LiveStatus,
  Select,
  Skeleton,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@thi360/ui";
import { ArrowRight, CircleHelp, CircleX, Eye, Info, RefreshCw, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { enumeratePaths } from "../check";
import { useExamVersion, useGeneratePreview } from "../review-queries";
import type { ExamCheckResult, ExamPathRef, PathCheckResult, PreviewResult } from "../review-types";
import type { ExamDetail } from "../types";
import { ExamPreviewAllocation } from "./exam-preview-allocation";
import { ExamPreviewPaper } from "./exam-preview-paper";
import { structureHref, useChoiceNameResolver, useIssueText, usePathLabel } from "./exam-review-shared";

export interface ExamPreviewPanelProps {
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

/**
 * Sinh đề xem trước (bản vẽ `BaiThiXemTruoc`): chọn đường lựa chọn, sinh bằng cùng quy tắc với lượt thi thật, xem đề
 * đã sinh và bảng đối chiếu phân bổ. Đề xem trước không trừ lượt, không tạo kết quả học tập.
 */
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
  const format = useFormatter();
  const pathLabel = usePathLabel();
  const nameOf = useChoiceNameResolver();
  const versionQuery = useExamVersion(exam.id, version);
  const generate = useGeneratePreview(exam.id);
  const [liveMessage, setLiveMessage] = useState("");
  const [run, setRun] = useState(0);
  const [tab, setTab] = useState<"paper" | "allocation">("paper");

  const options = useMemo<PathOption[]>(() => {
    if (version !== null) {
      return versionQuery.data
        ? enumeratePaths(versionQuery.data.parts, nameOf).map((path) => ({ path, result: null }))
        : [];
    }
    if (check) return check.paths.map((path) => ({ path, result: path.result }));
    return enumeratePaths(exam.parts, nameOf).map((path) => ({ path, result: null }));
  }, [version, versionQuery.data, check, exam.parts, nameOf]);

  const [selected, setSelected] = useState<string | null>(initialPathId);
  const fallback = (options.find((option) => option.result === null || option.result === "VALID") ?? options[0])?.path.pathId;
  const pathId = selected && options.some((option) => option.path.pathId === selected) ? selected : (fallback ?? null);

  function start(nextPathId: string | null = pathId) {
    if (!nextPathId) return;
    const nextRun = run + 1;
    setRun(nextRun);
    setTab("paper");
    setLiveMessage(t("live.generating"));
    generate.mutate(
      { pathId: nextPathId, seed: nextRun, version },
      {
        onSuccess: (result) => {
          setLiveMessage(
            result.status === "GENERATED"
              ? t("live.generated", { count: result.paper.totalQuestions })
              : t("live.failed", { path: pathLabel(result.path) }),
          );
        },
        onError: () => setLiveMessage(t("error")),
      },
    );
  }

  // Mở từ bảng kết quả kiểm tra ("Sinh đề xem trước cho …"): sinh ngay một lần khi đã biết danh sách đường.
  const autoStarted = useRef(false);
  useEffect(() => {
    if (!autoGenerate || autoStarted.current || !pathId || options.length === 0) return;
    autoStarted.current = true;
    start(pathId);
  });

  const optionLabel = (option: PathOption) => {
    const label = pathLabel(option.path);
    if (option.result === "INVALID") return t("pathOptionInvalid", { path: label });
    if (option.result === "UNCONFIRMED") return t("pathOptionUnconfirmed", { path: label });
    return label;
  };

  const data = generate.data;
  const runStatus = data
    ? t(data.status === "GENERATED" ? "runStatus" : "runStatusFailed", {
        run,
        time: format.dateTime(new Date(data.generatedAt), "dateTime"),
      })
    : "";

  let body;
  if (version !== null && versionQuery.isPending) {
    body = <Skeleton className="h-64" />;
  } else if (version !== null && versionQuery.isError) {
    body = (
      <Card>
        <EmptyState headingLevel={3} icon={<TriangleAlert />} title={t("versionErrorTitle")} />
      </Card>
    );
  } else if (options.length === 0) {
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
  } else if (generate.isError) {
    body = (
      <Card>
        <EmptyState
          headingLevel={3}
          icon={<TriangleAlert />}
          title={t("errorTitle")}
          description={t("error")}
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
    body = <PreviewFailure exam={exam} result={data} onShowCheck={onShowCheck} />;
  } else {
    body = (
      <Tabs variant="segmented" value={tab} onValueChange={(value) => setTab(value as "paper" | "allocation")}>
        <TabsList aria-label={t("tabs.label")} className="max-w-md">
          <TabsTrigger value="paper">{t("tabs.paper")}</TabsTrigger>
          <TabsTrigger value="allocation">{t("tabs.allocation")}</TabsTrigger>
        </TabsList>
        <TabsContent value="paper">
          <ExamPreviewPaper paper={data.paper} />
        </TabsContent>
        <TabsContent value="allocation">
          <ExamPreviewAllocation paper={data.paper} />
        </TabsContent>
      </Tabs>
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
        <div className="flex flex-wrap items-end gap-3">
          <FormField label={t("pathLabel")} className="min-w-0 flex-[1_1_18rem]">
            {(controlProps) => (
              <Select
                {...controlProps}
                value={pathId ?? undefined}
                onValueChange={setSelected}
                disabled={options.length === 0}
                options={options.map((option) => ({ value: option.path.pathId, label: optionLabel(option) }))}
              />
            )}
          </FormField>
          <Button onClick={() => start()} loading={generate.isPending} disabled={!pathId}>
            {data ? <RefreshCw /> : <Eye />}
            {data ? t("regenerate") : t("generate")}
          </Button>
        </div>
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
  onShowCheck,
}: {
  exam: ExamDetail;
  result: Extract<PreviewResult, { status: "FAILED" }>;
  onShowCheck: () => void;
}) {
  const t = useTranslations("exams.preview");
  const pathLabel = usePathLabel();
  const issueText = useIssueText();
  const invalid = result.reason === "INVALID";
  const fixBlock = result.issues.find((issue) => issue.blockId)?.blockId ?? null;
  const Icon = invalid ? CircleX : CircleHelp;
  return (
    <div
      className={`flex items-start gap-3 rounded-lg border p-4 ${invalid ? "border-danger/30 bg-danger-bg" : "border-warning/30 bg-warning-bg"}`}
    >
      <Icon aria-hidden className={`mt-0.5 size-5 shrink-0 ${invalid ? "text-danger" : "text-warning"}`} />
      <div className="flex min-w-0 flex-col gap-1.5">
        <h3 className="text-title font-semibold text-text">
          {t(invalid ? "failInvalidTitle" : "failUnconfirmedTitle", { path: pathLabel(result.path) })}
        </h3>
        <ul className="flex flex-col gap-1 text-body text-text">
          {result.issues.map((issue) => (
            <li key={issue.key}>{issueText(issue)}</li>
          ))}
        </ul>
        <p className="text-body text-text-secondary">{t("failNote")}</p>
        <div>
          {invalid ? (
            <Button asChild variant="link" size="sm">
              <Link href={structureHref(exam.id, fixBlock)}>
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
        </div>
      </div>
    </div>
  );
}
