"use client";

import type { ColumnDef } from "@tanstack/react-table";
import {
  Badge,
  Button,
  Card,
  ListCard,
  StatusChip,
  Table,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  type TableCards,
} from "@thi360/ui";
import { ArrowRight, CircleCheck, CircleX } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useMemo, useState } from "react";
import {
  buildUnitRows,
  collectCheckIssues,
  findBlockContext,
  groupIssues,
  issueStructureHref,
  splitBySeverity,
  worstSectionState,
  type IssueGrouping,
  type UnitRow,
} from "../check";
import { useExamStructure } from "../queries";
import type { CheckIssue, ExamCheckResult } from "../review-types";
import type { ExamDetail, ExamSectionState } from "../types";
import { useIssueText, useLookup } from "./exam-review-shared";
import { SECTION_STATE_TONES } from "./exam-structure-list";

export interface ExamReviewFindingsProps {
  exam: ExamDetail;
  result: ExamCheckResult;
}

/**
 * Lỗi và cảnh báo của lần kiểm tra (bản vẽ `KiemTra2B`, 2B 7.3 và 15.2): lỗi chặn công bố tách khỏi cảnh báo, gom theo
 * môn hoặc theo mã bài thi bị ảnh hưởng. Lời lỗi là chữ chính, mã kỹ thuật là chữ phụ; mỗi lỗi có liên kết mở đúng chỗ
 * ở bước Cấu trúc.
 */
export function ExamReviewFindings({ exam, result }: ExamReviewFindingsProps) {
  const t = useTranslations("exams.review.findings");
  const [grouping, setGrouping] = useState<IssueGrouping>("subject");
  const structure = useExamStructure(exam.id);
  const issues = useMemo(() => collectCheckIssues(result), [result]);
  const { errors, unconfirmed, warnings } = useMemo(() => splitBySeverity(issues), [issues]);
  const states = result.sectionStates ?? null;
  const checked = states?.filter((item) => item.state === "CHECKED").length ?? 0;
  const blocked = errors.length > 0 || unconfirmed.length > 0;

  return (
    <section aria-labelledby="review-findings-title" className="flex flex-col gap-4">
      <h2 id="review-findings-title" className="text-h3 font-bold text-text">
        {t("title")}
      </h2>

      <div
        role="status"
        className={`flex items-start gap-3 rounded-lg border p-4 ${blocked ? "border-danger/30 bg-danger-bg" : "border-success/30 bg-success-bg"}`}
      >
        {blocked ? (
          <CircleX aria-hidden className="mt-0.5 size-5 shrink-0 text-danger" />
        ) : (
          <CircleCheck aria-hidden className="mt-0.5 size-5 shrink-0 text-success" />
        )}
        <div className="flex min-w-0 flex-col gap-0.5">
          <p className="font-semibold text-text">
            {errors.length > 0
              ? t("blocked", { count: errors.length })
              : unconfirmed.length > 0
                ? t("unconfirmedHeadline", { count: unconfirmed.length })
                : t("passed")}
          </p>
          <p className="text-body text-text-secondary">
            {warnings.length > 0 ? t("warningsNote", { count: warnings.length }) : t("noWarnings")}
            {issues.length > 0 && ` ${t("openHint")}`}
          </p>
        </div>
      </div>

      <dl aria-label={t("countersLabel")} className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Counter label={t("counters.errors")} value={String(errors.length)} tone={errors.length > 0 ? "danger" : null} />
        <Counter label={t("counters.warnings")} value={String(warnings.length)} tone={warnings.length > 0 ? "warning" : null} />
        <Counter
          label={t("counters.unconfirmed")}
          value={String(unconfirmed.length)}
          tone={unconfirmed.length > 0 ? "warning" : null}
        />
        {states && (
          <Counter
            label={t("counters.sections")}
            value={t("counters.sectionsValue", { checked, total: states.length })}
            tone={null}
          />
        )}
      </dl>

      {issues.length > 0 && (
        <Tabs variant="segmented" value={grouping} onValueChange={(value) => setGrouping(value as IssueGrouping)}>
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-label font-semibold text-text-secondary" id="review-findings-grouping">
              {t("groupBy.label")}
            </span>
            <TabsList aria-labelledby="review-findings-grouping" className="max-w-xs">
              <TabsTrigger value="subject">{t("groupBy.subject")}</TabsTrigger>
              <TabsTrigger value="code">{t("groupBy.code")}</TabsTrigger>
            </TabsList>
          </div>
          {(["subject", "code"] as const).map((by) => (
            <TabsContent key={by} value={by} className="flex flex-col gap-5">
              <IssueList exam={exam} issues={errors} grouping={by} severity="errors" />
              <IssueList exam={exam} issues={unconfirmed} grouping={by} severity="unconfirmed" />
              <IssueList exam={exam} issues={warnings} grouping={by} severity="warnings" />
            </TabsContent>
          ))}
        </Tabs>
      )}

      {states && states.length > 0 && <SectionStates states={states} />}
      {states && (
        <UnitTable exam={exam} states={states} issues={issues} combinations={structure.data?.combinations ?? []} />
      )}
    </section>
  );
}

function Counter({ label, value, tone }: { label: string; value: string; tone: "danger" | "warning" | null }) {
  return (
    <div className="flex flex-col gap-0.5 rounded-lg border border-border bg-surface p-3">
      <dt className="text-caption text-text-muted">{label}</dt>
      <dd
        className={`m-0 text-h3 font-bold tabular-nums ${tone === "danger" ? "text-danger" : tone === "warning" ? "text-warning" : "text-text"}`}
      >
        {value}
      </dd>
    </div>
  );
}

function IssueList({
  exam,
  issues,
  grouping,
  severity,
}: {
  exam: ExamDetail;
  issues: CheckIssue[];
  grouping: IssueGrouping;
  severity: "errors" | "unconfirmed" | "warnings";
}) {
  const t = useTranslations("exams.review.findings");
  const lookup = useLookup();
  const groups = useMemo(() => groupIssues(issues, grouping), [issues, grouping]);
  if (issues.length === 0) return null;
  const headingId = `review-findings-${severity}-${grouping}`;
  const tone = severity === "errors" ? "text-danger" : "text-warning";
  return (
    <div className="flex flex-col gap-3" data-testid={`findings-${severity}`}>
      <h3 id={headingId} className={`text-label font-semibold ${tone}`}>
        {t(`${severity}Title`, { count: issues.length })}
      </h3>
      <ul aria-labelledby={headingId} className="flex flex-col gap-3">
        {groups.map((group) => (
          <li key={group.key} className="flex flex-col gap-2">
            <span className="text-label font-semibold text-text">
              {group.value === null
                ? t(grouping === "subject" ? "wholeExam" : "noCode")
                : grouping === "subject"
                  ? lookup.subject(group.value)
                  : t("codeGroup", { code: group.value })}
            </span>
            <ul className="flex flex-col gap-2">
              {group.issues.map((issue) => (
                <IssueRow key={`${group.key}:${issue.key}`} exam={exam} issue={issue} />
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </div>
  );
}

function IssueRow({ exam, issue }: { exam: ExamDetail; issue: CheckIssue }) {
  const t = useTranslations("exams.review.findings");
  const issueText = useIssueText(exam);
  const lookup = useLookup();
  const message = issueText(issue);
  const href = issueStructureHref(exam.id, issue);
  const where = findBlockContext(exam, issue.blockId);
  const location = [
    issue.partName ?? where?.part.name ?? exam.parts.find((part) => part.id === issue.partId)?.name ?? null,
    issue.subjectCode ? lookup.subject(issue.subjectCode) : null,
    issue.blockName ?? where?.block.name ?? null,
  ]
    .filter(Boolean)
    .join(" · ");
  const affected = issue.affectedCodes ?? [];
  const tone = issue.severity === "ERROR" ? "border-danger/30" : "border-warning/30";
  return (
    <li className={`flex flex-col gap-1.5 rounded-lg border bg-surface p-3 ${tone}`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <p className="text-body text-text">{message}</p>
          <p className="font-mono text-caption text-text-muted">{issue.code}</p>
        </div>
        {href && (
          <Button asChild variant="link" size="sm">
            <Link href={href} aria-label={t("openAria", { issue: message })}>
              {t("open")}
              <ArrowRight />
            </Link>
          </Button>
        )}
      </div>
      {location && <p className="text-caption text-text-muted">{t("location", { where: location })}</p>}
      {affected.length > 0 && (
        <p className="flex flex-wrap items-center gap-1.5 text-caption text-text-muted">
          <span>{t("affected")}</span>
          {affected.map((code) => (
            <Badge key={code}>{code}</Badge>
          ))}
        </p>
      )}
    </li>
  );
}

/** Trạng thái từng môn sau lần kiểm tra: một chip mỗi môn, trạng thái xấu nhất nếu môn nằm ở nhiều phần (H2). */
function SectionStates({ states }: { states: readonly ExamSectionState[] }) {
  const t = useTranslations("exams.review.findings");
  const tState = useTranslations("exams.structure.list.state");
  const lookup = useLookup();
  const subjects = [...new Set(states.map((item) => item.subjectCode ?? ""))];
  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-label font-semibold text-text">{t("unitsTitle")}</h3>
      <ul className="flex flex-wrap gap-2">
        {subjects.map((code) => {
          const state = worstSectionState(states.filter((item) => (item.subjectCode ?? "") === code));
          if (!state) return null;
          return (
            <li key={code} className="flex items-center gap-2 rounded-lg border border-border bg-surface px-3 py-1.5">
              <span className="text-body font-semibold text-text">{code ? lookup.subject(code) : t("wholeExam")}</span>
              <StatusChip tone={SECTION_STATE_TONES[state]}>{tState(state)}</StatusChip>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function UnitTable({
  exam,
  states,
  issues,
  combinations,
}: {
  exam: ExamDetail;
  states: readonly ExamSectionState[];
  issues: readonly CheckIssue[];
  combinations: Parameters<typeof buildUnitRows>[1];
}) {
  const t = useTranslations("exams.review.findings");
  const tState = useTranslations("exams.structure.list.state");
  const lookup = useLookup();
  const rows = useMemo(
    () => buildUnitRows(exam.parts, combinations, states, issues),
    [exam.parts, combinations, states, issues],
  );

  const { columns, cards } = useMemo(() => {
    const subjects = (row: UnitRow) => row.subjectCodes.map(lookup.subject).join(" → ");
    const status = (row: UnitRow) =>
      row.state ? <StatusChip tone={SECTION_STATE_TONES[row.state]}>{tState(row.state)}</StatusChip> : null;
    const name = (row: UnitRow) => (row.kind === "code" ? t("unitCode", { code: row.name }) : row.name);
    const columns: ColumnDef<UnitRow>[] = [
      {
        id: "name",
        header: t("unitColumns.name"),
        cell: ({ row }) => (
          <span className={`block min-w-32 font-semibold text-text ${row.original.kind === "code" ? "pl-4 font-normal" : ""}`}>
            {name(row.original)}
          </span>
        ),
      },
      { id: "subjects", header: t("unitColumns.subjects"), cell: ({ row }) => <span className="block min-w-48">{subjects(row.original)}</span> },
      { id: "state", header: t("unitColumns.state"), cell: ({ row }) => status(row.original) },
      {
        id: "errors",
        header: t("unitColumns.errors"),
        cell: ({ row }) => <span className="tabular-nums text-danger">{row.original.errors || t("none")}</span>,
      },
      {
        id: "warnings",
        header: t("unitColumns.warnings"),
        cell: ({ row }) => <span className="tabular-nums text-warning">{row.original.warnings || t("none")}</span>,
      },
    ];
    const cards: TableCards<UnitRow> = {
      render: (row) => (
        <ListCard
          title={name(row)}
          caption={subjects(row)}
          status={status(row)}
          meta={[
            { label: t("unitColumns.errors"), value: String(row.errors) },
            { label: t("unitColumns.warnings"), value: String(row.warnings) },
          ]}
        />
      ),
    };
    return { columns, cards };
  }, [t, tState, lookup]);

  if (rows.length === 0) return null;
  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-label font-semibold text-text">{t("partsTitle")}</h3>
      <Card padding="none" className="overflow-hidden">
        <Table
          aria-label={t("partsTitle")}
          columns={columns}
          data={rows}
          getRowId={(row) => row.key}
          empty={null}
          cards={cards}
        />
      </Card>
    </div>
  );
}
