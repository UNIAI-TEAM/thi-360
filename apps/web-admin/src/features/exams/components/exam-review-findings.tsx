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
import { ArrowRight, CircleCheck, CircleX, Eye } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { Fragment, useId, useMemo, useState } from "react";
import {
  buildUnitRows,
  collectCheckIssues,
  findBlockContext,
  groupIssues,
  issueQuotaHref,
  issueRuleHref,
  issueStructureHref,
  splitBySeverity,
  worstSectionState,
  type IssueGrouping,
  type UnitRow,
} from "../check";
import { findExamQuotaForIssue } from "../exam-overview";
import { useExamStructure } from "../queries";
import type { CheckIssue, ExamCheckResult } from "../review-types";
import type { ExamDetail, ExamPart, ExamQuota, ExamSectionState, StructureCombination } from "../types";
import { examTabHref, withFromReview } from "../editor-tabs";
import { ExamQuotaDeviationHint, ExamQuotaScopePrefix } from "./exam-quota-deviation";
import { listingErrorCount, listingFieldHref, listingWarningCount } from "./exam-review-listing";
import { ExamName, labelBeforeName, richNodes, useIssueNode, useLookup } from "./exam-review-shared";
import { SECTION_STATE_TONES } from "./exam-structure-list";

export interface ExamReviewFindingsProps {
  exam: ExamDetail;
  result: ExamCheckResult;
  onPreview?: (pathId: string | null) => void;
}

export function ExamReviewFindings({ exam, result, onPreview }: ExamReviewFindingsProps) {
  const t = useTranslations("exams.review.findings");
  const tReview = useTranslations("exams.review");
  const tListing = useTranslations("exams.review.listing");
  const [grouping, setGrouping] = useState<IssueGrouping>("subject");
  const structure = useExamStructure(exam.id);
  const issues = useMemo(() => collectCheckIssues(result), [result]);
  const { errors, unconfirmed, warnings } = useMemo(() => splitBySeverity(issues), [issues]);
  const states = result.sectionStates ?? null;
  const checked = states?.filter((item) => item.state === "CHECKED").length ?? 0;
  const listingErrors = listingErrorCount(result.listingCheck);
  const listingWarnings = listingWarningCount(result.listingCheck);
  // Máy chủ không trả nhóm Thông tin bán: chưa biết có lỗi chặn hay không, nên không báo "Công bố được".
  const listingUnchecked = result.listingCheck == null;
  const stale = result.stale || exam.check.status === "NEEDS_RECHECK";
  const totalErrors = errors.length + listingErrors;
  const totalWarnings = warnings.length + listingWarnings;
  const blocked = stale || totalErrors > 0 || unconfirmed.length > 0 || listingUnchecked;

  const headline = stale
    ? t("staleHeadline")
    : errors.length > 0 && listingErrors > 0
      ? t("bothBlocked", { structure: errors.length, listing: listingErrors })
      : errors.length > 0
        ? t("blocked", { count: errors.length })
        : unconfirmed.length > 0
          ? t("unconfirmedHeadline", { count: unconfirmed.length })
          : listingErrors > 0
            ? t("structureValidListingBlocked", { count: listingErrors })
            : listingUnchecked
              ? t("listingUnconfirmed")
              : t("passed");

  return (
    <section aria-labelledby="review-findings-title" className="flex flex-col gap-4">
      <h2 id="review-findings-title" className="text-h3 font-bold text-text">
        {t("title")}
      </h2>

      <div
        role="status"
        className={`flex flex-wrap items-start justify-between gap-3 rounded-lg border p-4 ${blocked ? "border-danger/30 bg-danger-bg" : "border-success/30 bg-success-bg"}`}
      >
        <div className="flex min-w-0 flex-1 items-start gap-3">
          {blocked ? (
            <CircleX aria-hidden className="mt-0.5 size-5 shrink-0 text-danger" />
          ) : (
            <CircleCheck aria-hidden className="mt-0.5 size-5 shrink-0 text-success" />
          )}
          <div className="flex min-w-0 flex-col gap-0.5">
            <p className="font-semibold text-text">{headline}</p>
            <p className="text-body text-text-secondary">
              {totalWarnings > 0 ? t("warningsNote", { count: totalWarnings }) : t("noWarnings")}
              {issues.length > 0 && ` ${t("openHint")}`}
              {listingErrors > 0 && (
                <>
                  {" "}
                  <Link href={listingFieldHref(exam.id)} className="rounded-sm font-medium text-primary-text hover:underline">
                    {tListing("edit")}
                  </Link>
                </>
              )}
            </p>
          </div>
        </div>
        {onPreview && (
          <Button type="button" variant="outline" size="sm" onClick={() => onPreview(null)}>
            <Eye aria-hidden />
            {tReview("previewButton")}
          </Button>
        )}
      </div>

      <dl aria-label={t("countersLabel")} className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Counter label={t("counters.errors")} value={String(totalErrors)} tone={totalErrors > 0 ? "danger" : null} />
        <Counter label={t("counters.warnings")} value={String(totalWarnings)} tone={totalWarnings > 0 ? "warning" : null} />
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
              <IssueList
                exam={exam}
                issues={errors}
                grouping={by}
                severity="errors"
                parts={structure.data?.parts}
                quotas={structure.data?.examQuotas}
                combinations={structure.data?.combinations}
              />
              <IssueList
                exam={exam}
                issues={unconfirmed}
                grouping={by}
                severity="unconfirmed"
                parts={structure.data?.parts}
                quotas={structure.data?.examQuotas}
                combinations={structure.data?.combinations}
              />
              <IssueList
                exam={exam}
                issues={warnings}
                grouping={by}
                severity="warnings"
                parts={structure.data?.parts}
                quotas={structure.data?.examQuotas}
                combinations={structure.data?.combinations}
              />
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
  parts,
  quotas,
  combinations,
}: {
  exam: ExamDetail;
  issues: CheckIssue[];
  grouping: IssueGrouping;
  severity: "errors" | "unconfirmed" | "warnings";
  parts?: readonly ExamPart[];
  quotas?: readonly ExamQuota[];
  combinations?: readonly StructureCombination[];
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
                <IssueRow
                  key={`${group.key}:${issue.key}`}
                  exam={exam}
                  issue={issue}
                  parts={parts}
                  quotas={quotas}
                  combinations={combinations}
                />
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </div>
  );
}

function IssueRow({
  exam,
  issue,
  parts,
  quotas,
  combinations,
}: {
  exam: ExamDetail;
  issue: CheckIssue;
  parts?: readonly ExamPart[];
  quotas?: readonly ExamQuota[];
  combinations?: readonly StructureCombination[];
}) {
  const t = useTranslations("exams.review.findings");
  const tMapid = useTranslations("exams.mapidRules.issue");
  const tQuota = useTranslations("exams.examQuota");
  const issueNode = useIssueNode(exam, true);
  const lookup = useLookup();
  const openId = useId();
  const messageId = useId();
  const message = issueNode(issue);
  const isQuotaDeviation = issue.code === "EXAM_QUOTA_DEVIATION";
  const matchedQuota = isQuotaDeviation ? findExamQuotaForIssue(issue, quotas) : null;
  const effectiveParts = parts && parts.length > 0 ? parts : exam.parts;
  const ruleHref = issueRuleHref(exam.id, issue);
  const quotaHref = issueQuotaHref(exam.id, issue, quotas);
  const yearsHref = issue.code === "YEARS_UNCONFIRMED" ? `${examTabHref(exam.id, "general")}?field=yearsConfirmed` : null;
  const rawHref = yearsHref ?? ruleHref ?? quotaHref ?? issueStructureHref(exam.id, issue);
  const href = rawHref ? withFromReview(rawHref) : null;
  const where = findBlockContext(exam, issue.blockId);
  const partName = issue.partName ?? where?.part.name ?? exam.parts.find((part) => part.id === issue.partId)?.name ?? null;
  const blockName = issue.blockName ?? where?.block.name ?? null;
  // Tên phần và khối của mẫu là nội dung đề; tên môn theo danh mục ở ngoài vùng đó.
  const location = [
    partName ? <ExamName>{partName}</ExamName> : null,
    issue.subjectCode ? lookup.subject(issue.subjectCode) : null,
    blockName ? <ExamName>{blockName}</ExamName> : null,
  ].filter(Boolean);
  const affected = issue.affectedCodes ?? [];
  const tone = issue.severity === "ERROR" ? "border-danger/30" : "border-warning/30";
  return (
    <li className={`flex flex-col gap-1.5 rounded-lg border bg-surface p-3 ${tone}`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <p id={messageId} className="text-body text-text">
            {isQuotaDeviation && (
              <ExamQuotaScopePrefix
                quota={matchedQuota}
                parts={effectiveParts}
                combinations={combinations}
                subjectName={lookup.subject}
              />
            )}
            {message}
          </p>
          {isQuotaDeviation && (
            <ExamQuotaDeviationHint
              issue={issue}
              quota={matchedQuota}
              parts={effectiveParts}
              subjectName={lookup.subject}
              examId={exam.id}
            />
          )}
        </div>
        {href && (
          <Button asChild variant="link" size="sm">
            <Link
              id={openId}
              href={href}
              aria-label={
                ruleHref
                  ? labelBeforeName(tMapid, "goToRuleAria", "issue")
                  : quotaHref
                    ? labelBeforeName(tQuota, "goToQuotaAria", "issue")
                    : labelBeforeName(t, "openAria", "issue")
              }
              aria-labelledby={`${openId} ${messageId}`}
            >
              {ruleHref ? tMapid("goToRule") : quotaHref ? tQuota("goToQuota") : t("open")}
              <ArrowRight />
            </Link>
          </Button>
        )}
      </div>
      {location.length > 0 && (
        <p className="text-caption text-text-muted">
          {richNodes(t, "location", {
            where: location.map((part, index) => (
              <Fragment key={index}>
                {index > 0 && " · "}
                {part}
              </Fragment>
            )),
          })}
        </p>
      )}
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

/** Số lỗi hoặc cảnh báo của một dòng; 0 là dấu "–" trung tính, không tô màu cảnh báo cho dòng sạch. */
function CountCell({ value, tone, none }: { value: number; tone: string; none: string }) {
  return value > 0 ? (
    <span className={`block text-right tabular-nums ${tone}`}>{value}</span>
  ) : (
    <span className="block text-right text-text-muted">{none}</span>
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
    () => buildUnitRows(exam, combinations, states, issues),
    [exam, combinations, states, issues],
  );

  const { columns, cards } = useMemo(() => {
    const subjects = (row: UnitRow) => row.subjectCodes.map(lookup.subject).join(" → ");
    const status = (row: UnitRow) =>
      row.state ? <StatusChip tone={SECTION_STATE_TONES[row.state]}>{tState(row.state)}</StatusChip> : null;
    const name = (row: UnitRow) => (row.kind === "code" ? t("unitCode", { code: row.name }) : <ExamName>{row.name}</ExamName>);
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
        header: () => <span className="block text-right">{t("unitColumns.errors")}</span>,
        cell: ({ row }) => <CountCell value={row.original.errors} tone="text-danger" none={t("none")} />,
      },
      {
        id: "warnings",
        header: () => <span className="block text-right">{t("unitColumns.warnings")}</span>,
        cell: ({ row }) => <CountCell value={row.original.warnings} tone="text-warning" none={t("none")} />,
      },
    ];
    const cards: TableCards<UnitRow> = {
      render: (row) => (
        <ListCard
          title={<span className="line-clamp-2">{name(row)}</span>}
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
