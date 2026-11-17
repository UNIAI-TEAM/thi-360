"use client";

import type { ColumnDef } from "@tanstack/react-table";
import {
  Badge,
  Button,
  Card,
  Checkbox,
  cn,
  EmptyState,
  focusRing,
  FormField,
  Input,
  Label,
  ListCard,
  ListCardTag,
  ListCardTitleLink,
  LiveStatus,
  PageHeader,
  Pagination,
  Select,
  StatusChip,
  Table,
  type TableCards,
} from "@thi360/ui";
import { FileText, Plus, Search, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useId, useMemo, useState } from "react";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import {
  FILTER_FORM,
  FILTER_SEARCH,
  FILTER_SELECT,
  FILTER_TOGGLES,
} from "@/features/users/components/list-filters";
import { examTabHref } from "../editor-tabs";
import { useExamList } from "../queries";
import { EXAM_CHECK_TONE, EXAM_STATUS_TONE } from "../status";
import {
  EXAM_KINDS,
  type ExamCheckStatus,
  type ExamScope,
  type ExamKind,
  type ExamStatus,
  type ExamSummary,
  type ListExamsQuery,
} from "../types";
import { useKindLabel, usePartNames } from "./create-exam-fields";
import { ExamListRowActions } from "./exam-list-actions";

const ALL = "ALL";
const PAGE_SIZE_OPTIONS = [10, 20, 50];
const SCOPES: ExamScope[] = ["FULL", "PART", "SUBJECT"];
const STATUSES: ExamStatus[] = ["DRAFT", "PUBLISHED", "DISCONTINUED"];
const CHECK_STATUSES: ExamCheckStatus[] = ["UNCHECKED", "VALID", "INVALID", "NEEDS_RECHECK"];

/**
 * Cột phụ ẩn theo bề rộng khung chứa bảng (DESIGN.md mục 5.9): Cập nhật ẩn trước, rồi Hình thức, Lựa chọn, Kỳ thi.
 * Khung hẹp hơn 640px thì bảng thành danh sách thẻ (thẻ vẫn có Lựa chọn).
 */
const DROP_LOW_PRIORITY_COLUMNS = [
  "@container",
  "@max-[86rem]:[&_:is(th,td):has([data-col=updated])]:hidden",
  "@max-[78rem]:[&_:is(th,td):has([data-col=format])]:hidden",
  "@max-[72rem]:[&_:is(th,td):has([data-col=choice])]:hidden",
  "@max-[66rem]:[&_:is(th,td):has([data-col=kind])]:hidden",
].join(" ");

const CREATE_EXAM_HREF = "/exams/new";

/**
 * Danh sách bài thi (bản vẽ `BaiThi`; spec 7 kỳ thi): lọc theo bảy kỳ thi của danh mục và theo môn, cột Lựa chọn (tổ
 * hợp, nhánh hoặc môn), thời lượng ghi "chung" khi bài dùng thời lượng chung, huy hiệu "Có tự luận"; bảng/thẻ, sửa,
 * sao chép, ngừng bán, tạo bài thi.
 */
export function ExamListView() {
  const t = useTranslations("exams");
  const common = useTranslations("common");
  const format = useFormatter();
  const unpublishedId = useId();
  const referenceQuery = useContentReference();
  const lookup = useReferenceLookup(referenceQuery.data);
  const kindLabel = useKindLabel();
  const { partName: scopeName } = usePartNames();
  const kindCodes = useMemo(
    () => referenceQuery.data?.examKinds.map((kind) => kind.code) ?? [...EXAM_KINDS],
    [referenceQuery.data],
  );

  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [kind, setKind] = useState<ExamKind | typeof ALL>(ALL);
  const [subject, setSubject] = useState<string>(ALL);
  const [examScope, setExamScope] = useState<ExamScope | typeof ALL>(ALL);
  const [status, setStatus] = useState<ExamStatus | typeof ALL>(ALL);
  const [checkStatus, setCheckStatus] = useState<ExamCheckStatus | typeof ALL>(ALL);
  const [unpublishedOnly, setUnpublishedOnly] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [liveMessage, setLiveMessage] = useState("");

  useEffect(() => {
    const trimmed = searchInput.trim();
    if (trimmed === search) return;
    const timer = setTimeout(
      () => {
        setSearch(trimmed);
        setPage(1);
      },
      trimmed ? 300 : 0,
    );
    return () => clearTimeout(timer);
  }, [searchInput, search]);

  const query = useMemo<ListExamsQuery>(
    () => ({
      ...(search ? { q: search } : {}),
      ...(kind !== ALL ? { kind } : {}),
      ...(subject !== ALL ? { subjectCode: subject } : {}),
      ...(examScope !== ALL ? { scope: examScope } : {}),
      ...(status !== ALL ? { status } : {}),
      ...(checkStatus !== ALL ? { checkStatus } : {}),
      ...(unpublishedOnly ? { hasUnpublishedChanges: true } : {}),
      page: page - 1,
      size: pageSize,
    }),
    [search, kind, subject, examScope, status, checkStatus, unpublishedOnly, page, pageSize],
  );
  const examsQuery = useExamList(query);

  const hasActiveFilters = Boolean(
    search ||
      kind !== ALL ||
      subject !== ALL ||
      examScope !== ALL ||
      status !== ALL ||
      checkStatus !== ALL ||
      unpublishedOnly,
  );

  function resetFilters() {
    setSearchInput("");
    setSearch("");
    setKind(ALL);
    setSubject(ALL);
    setExamScope(ALL);
    setStatus(ALL);
    setCheckStatus(ALL);
    setUnpublishedOnly(false);
    setPage(1);
  }

  const option = (value: string, label: string) => ({ value, label });
  const allOption = option(ALL, t("list.filters.all"));

  const { structureText, updatedText, choiceText } = useMemo(
    () => ({
      /** Số câu và thời lượng: thời lượng chung ghi "chung", chưa có thời lượng ghi "—", còn lại ghi tổng thời lượng. */
      structureText: (exam: ExamSummary) => {
        const key =
          exam.durationMinutes <= 0
            ? "list.structureValueNoDuration"
            : exam.timing === "SHARED"
              ? "list.structureValueShared"
              : "list.structureValue";
        return t(key, { questions: exam.totalQuestions, minutes: exam.durationMinutes });
      },
      updatedText: (exam: ExamSummary) => format.dateTime(new Date(exam.updatedAt), "dateTime"),
      /**
       * Cột Lựa chọn: bài lẻ ghi phần/môn; bài khác ghi môn của bài (kỳ thi theo môn) và các lựa chọn của phần tự chọn
       * (Tiếng Anh, tổ hợp, nhánh). Không có gì thì `null`.
       */
      choiceText: (exam: ExamSummary): string | null => {
        if (exam.scope === "PART") return exam.partCode ? scopeName(exam.partCode) : null;
        const options = [
          ...(exam.englishOption ? [t("list.choice.english")] : []),
          ...(exam.combinationCount > 0 ? [t("list.choice.combinations", { count: exam.combinationCount })] : []),
          ...((exam.branchCount ?? 0) > 0 ? [t("list.choice.branches", { count: exam.branchCount ?? 0 })] : []),
        ].join(" + ");
        const subjectText = exam.subjectCode ? lookup.subject(exam.subjectCode) : "";
        return [subjectText, options].filter(Boolean).join(" · ") || null;
      },
    }),
    [t, format, scopeName, lookup],
  );

  const columns = useMemo<ColumnDef<ExamSummary>[]>(
    () => [
      {
        id: "exam",
        header: t("list.table.exam"),
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex min-w-48 flex-col">
            <Link
              href={examTabHref(row.original.id, "structure")}
              className={cn("rounded-sm font-semibold text-primary-text hover:underline", focusRing)}
            >
              <span className="line-clamp-2">{row.original.name}</span>
            </Link>
            <span className="text-caption wrap-anywhere text-text-muted">{row.original.code}</span>
          </div>
        ),
      },
      {
        id: "kind",
        header: () => <span data-col="kind">{t("list.table.kind")}</span>,
        enableSorting: false,
        cell: ({ row }) => <span data-col="kind">{kindLabel(row.original.kind)}</span>,
      },
      {
        id: "format",
        header: () => <span data-col="format">{t("list.table.format")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="format" className="whitespace-nowrap">
            {t(`scope.${row.original.scope}`)}
          </span>
        ),
      },
      {
        id: "choice",
        header: () => <span data-col="choice">{t("list.table.choice")}</span>,
        enableSorting: false,
        cell: ({ row }) => <ChoiceCell text={choiceText(row.original)} />,
      },
      {
        id: "structure",
        header: t("list.table.structure"),
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex flex-col items-start gap-1">
            <span className="whitespace-nowrap tabular-nums">{structureText(row.original)}</span>
            {row.original.hasEssay && <Badge>{t("list.essay")}</Badge>}
          </div>
        ),
      },
      {
        id: "status",
        header: t("list.table.status"),
        enableSorting: false,
        cell: ({ row }) => <ExamStatusChip exam={row.original} />,
      },
      {
        id: "draft",
        header: t("list.table.draft"),
        enableSorting: false,
        cell: ({ row }) =>
          row.original.hasUnpublishedChanges ? (
            <StatusChip tone="warning">{t("list.unpublished")}</StatusChip>
          ) : (
            <span className="text-text-muted">
              <span aria-hidden>—</span>
              <span className="sr-only">{t("list.noUnpublished")}</span>
            </span>
          ),
      },
      {
        id: "check",
        header: t("list.table.check"),
        enableSorting: false,
        cell: ({ row }) => <ExamCheckCell exam={row.original} />,
      },
      {
        id: "updated",
        header: () => <span data-col="updated">{t("list.table.updated")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="updated" className="whitespace-nowrap text-text-muted tabular-nums">
            {updatedText(row.original)}
          </span>
        ),
      },
      {
        id: "actions",
        header: () => <span className="block text-right">{t("list.table.actions")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex items-center justify-end gap-0.5 whitespace-nowrap">
            <ExamListRowActions exam={row.original} onAnnounce={setLiveMessage} />
          </div>
        ),
      },
    ],
    [t, structureText, updatedText, choiceText, kindLabel],
  );

  const cards = useMemo<TableCards<ExamSummary>>(
    () => ({
      render: (exam) => (
        <ListCard
          title={
            <ListCardTitleLink linkComponent={Link} href={examTabHref(exam.id, "structure")}>
              {exam.name}
            </ListCardTitleLink>
          }
          actions={<ExamListRowActions exam={exam} onAnnounce={setLiveMessage} />}
          caption={exam.code}
          status={<ExamStatusChip exam={exam} />}
          tags={
            <>
              <ListCardTag>{kindLabel(exam.kind)}</ListCardTag>
              <ListCardTag>{t(`scope.${exam.scope}`)}</ListCardTag>
              {exam.hasEssay && <ListCardTag>{t("list.essay")}</ListCardTag>}
              {exam.hasUnpublishedChanges && <StatusChip tone="warning">{t("list.unpublished")}</StatusChip>}
            </>
          }
          meta={[
            { label: t("list.table.choice"), value: choiceText(exam) ?? t("list.choice.none") },
            { label: t("list.table.structure"), value: structureText(exam) },
            { label: t("list.table.updated"), value: updatedText(exam) },
          ]}
        >
          <ExamCheckCell exam={exam} />
        </ListCard>
      ),
    }),
    [t, structureText, updatedText, choiceText, kindLabel],
  );

  const items = examsQuery.data?.content ?? [];
  const total = examsQuery.data?.totalElements ?? 0;
  const resultStatus =
    examsQuery.isLoading || examsQuery.isPlaceholderData || examsQuery.isError
      ? ""
      : t("list.resultCount", { count: total });

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title={t("list.title")}
        description={t("list.description")}
        actions={
          <Button asChild>
            <Link href={CREATE_EXAM_HREF}>
              <Plus />
              <span>{t("list.actions.create")}</span>
            </Link>
          </Button>
        }
      />

      <Card padding="md" className="@container">
        <search>
          <form aria-label={t("list.filters.ariaLabel")} onSubmit={(event) => event.preventDefault()} className={FILTER_FORM}>
            <FormField label={t("list.filters.search")} className={FILTER_SEARCH}>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  type="search"
                  name="q"
                  autoComplete="off"
                  spellCheck={false}
                  leadingIcon={<Search />}
                  value={searchInput}
                  onChange={(event) => setSearchInput(event.target.value)}
                  placeholder={t("list.filters.searchPlaceholder")}
                />
              )}
            </FormField>
            <FormField label={t("list.filters.kind")} className={cn(FILTER_SELECT, "@3xl:min-w-32")}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  value={kind}
                  onValueChange={(value) => {
                    setKind(value as ExamKind | typeof ALL);
                    setPage(1);
                  }}
                  options={[allOption, ...kindCodes.map((value) => option(value, kindLabel(value)))]}
                />
              )}
            </FormField>
            <FormField label={t("list.filters.subject")} className={cn(FILTER_SELECT, "@3xl:min-w-36")}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  value={subject}
                  onValueChange={(value) => {
                    setSubject(value);
                    setPage(1);
                  }}
                  options={[
                    allOption,
                    ...(referenceQuery.data?.subjects ?? []).map((item) => option(item.code, item.name)),
                  ]}
                />
              )}
            </FormField>
            <FormField label={t("list.filters.format")} className={cn(FILTER_SELECT, "@3xl:min-w-32")}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  value={examScope}
                  onValueChange={(value) => {
                    setExamScope(value as ExamScope | typeof ALL);
                    setPage(1);
                  }}
                  options={[allOption, ...SCOPES.map((value) => option(value, t(`scope.${value}`)))]}
                />
              )}
            </FormField>
            <FormField label={t("list.filters.status")} className={cn(FILTER_SELECT, "@3xl:min-w-36")}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  value={status}
                  onValueChange={(value) => {
                    setStatus(value as ExamStatus | typeof ALL);
                    setPage(1);
                  }}
                  options={[allOption, ...STATUSES.map((value) => option(value, t(`status.${value}`)))]}
                />
              )}
            </FormField>
            <FormField label={t("list.filters.check")} className={cn(FILTER_SELECT, "@3xl:min-w-40")}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  value={checkStatus}
                  onValueChange={(value) => {
                    setCheckStatus(value as ExamCheckStatus | typeof ALL);
                    setPage(1);
                  }}
                  options={[allOption, ...CHECK_STATUSES.map((value) => option(value, t(`checkStatus.${value}`)))]}
                />
              )}
            </FormField>
            <div className={FILTER_TOGGLES}>
              <div className="flex min-h-10 items-center gap-2">
                <Checkbox
                  id={unpublishedId}
                  checked={unpublishedOnly}
                  onCheckedChange={(checked) => {
                    setUnpublishedOnly(checked === true);
                    setPage(1);
                  }}
                />
                <Label htmlFor={unpublishedId} className="cursor-pointer font-normal text-text">
                  {t("list.filters.unpublishedOnly")}
                </Label>
              </div>
              {hasActiveFilters && (
                <Button type="button" variant="secondary" size="sm" onClick={resetFilters}>
                  {t("list.filters.clear")}
                </Button>
              )}
            </div>
          </form>
        </search>
      </Card>

      <Card padding="none" className="overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3">
          <p className="text-body font-medium text-text tabular-nums">
            {examsQuery.isLoading
              ? t("list.loadingCount")
              : examsQuery.isError
                ? t("list.errorCount")
                : t("list.resultCount", { count: total })}
          </p>
          <p className="text-caption text-text-muted">{t("list.hint")}</p>
          <LiveStatus message={liveMessage || resultStatus} />
        </div>

        <div className={DROP_LOW_PRIORITY_COLUMNS}>
          <Table
            aria-label={t("list.tableAriaLabel")}
            columns={columns}
            data={items}
            getRowId={(row) => row.id}
            loading={examsQuery.isLoading}
            loadingRows={pageSize}
            refreshing={examsQuery.isPlaceholderData}
            stickyColumns={{ start: true, end: true }}
            cards={cards}
            error={
              examsQuery.isError ? (
                <EmptyState
                  icon={<TriangleAlert />}
                  headingLevel={2}
                  title={t("list.errorTitle")}
                  description={t("list.errorDescription")}
                  action={
                    <Button variant="secondary" onClick={() => void examsQuery.refetch()}>
                      {common("retry")}
                    </Button>
                  }
                />
              ) : undefined
            }
            empty={
              hasActiveFilters ? (
                <EmptyState
                  icon={<FileText />}
                  headingLevel={2}
                  title={t("list.emptyFilteredTitle")}
                  description={t("list.emptyFilteredDescription")}
                  action={
                    <Button variant="secondary" onClick={resetFilters}>
                      {t("list.filters.clear")}
                    </Button>
                  }
                />
              ) : (
                <EmptyState
                  icon={<FileText />}
                  headingLevel={2}
                  title={t("list.emptyTitle")}
                  description={t("list.emptyDescription")}
                  action={
                    <Button asChild>
                      <Link href={CREATE_EXAM_HREF}>
                        <Plus />
                        <span>{t("list.actions.create")}</span>
                      </Link>
                    </Button>
                  }
                />
              )
            }
          />
        </div>

        {total > 0 && (
          <div className="border-t border-border px-4 py-3">
            <Pagination
              page={page}
              pageSize={pageSize}
              total={total}
              onPageChange={setPage}
              pageSizeOptions={PAGE_SIZE_OPTIONS}
              onPageSizeChange={(next) => {
                setPageSize(next);
                setPage(1);
              }}
            />
          </div>
        )}
      </Card>
    </div>
  );
}

/** Ô Lựa chọn; không có lựa chọn thì gạch ngang kèm chữ cho trình đọc màn hình. */
function ChoiceCell({ text }: { text: string | null }) {
  const t = useTranslations("exams.list");
  return (
    <span data-col="choice" className="block min-w-28">
      {text ?? (
        <span className="text-text-muted">
          <span aria-hidden>—</span>
          <span className="sr-only">{t("choice.none")}</span>
        </span>
      )}
    </span>
  );
}

function ExamStatusChip({ exam }: { exam: ExamSummary }) {
  const t = useTranslations("exams");
  return (
    <StatusChip tone={EXAM_STATUS_TONE[exam.status]}>
      {exam.status === "PUBLISHED" && exam.servingVersion !== null
        ? t("status.servingVersion", { version: exam.servingVersion })
        : t(`status.${exam.status}`)}
    </StatusChip>
  );
}

function ExamCheckCell({ exam }: { exam: ExamSummary }) {
  const t = useTranslations("exams");
  const { status, issueCount } = exam.check;
  // G3: cột Kiểm tra nằm sát cột Thao tác (ghim): đặt bề rộng tối thiểu để chip và ghi chú không bị cột ghim che.
  return (
    <div className="flex min-w-44 flex-col items-start gap-0.5 pr-2">
      <StatusChip tone={EXAM_CHECK_TONE[status]}>
        {status === "INVALID" ? t("checkStatus.issues", { count: issueCount }) : t(`checkStatus.${status}`)}
      </StatusChip>
      {status === "NEEDS_RECHECK" && <span className="text-caption text-text-muted">{t("list.recheckNote")}</span>}
    </div>
  );
}
