"use client";

import { Button, Card, ConfirmDialog, EmptyState, LiveStatus, notify, Skeleton, StatusChip } from "@thi360/ui";
import { ClipboardCheck, Info, LayoutList, Save, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useCallback, useMemo, useState } from "react";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import { yearsNeedConfirmation } from "../check";
import { examTabHref } from "../editor-tabs";
import { combinationsForKind } from "../create-exam-schema";
import { type MatrixAxes, structureTotals, validateStructure } from "../matrix";
import { useExam, useExamStructure, useExamTemplates, useUpdateExamStructure } from "../queries";
import type { ExamDetail, ExamPart, ExamSection, ExamStructure, ExamTemplate } from "../types";
import { BlockEditor } from "./block-editor";
import {
  addBlock,
  addBranch,
  attachCombination,
  attachGenerated,
  confirmOption,
  defaultSelection,
  ENGLISH_CODE,
  findBlock,
  moveBlock,
  newBlock,
  removeBlock,
  removeOption,
  selectionExists,
  setEnglishOption,
  setPartPoints,
  setPartQuestionCount,
  setSectionCount,
  type StructureSelection,
  updateBlock,
  updatePart,
} from "./exam-structure-draft";
import { issueTarget, optionLabel, type StructureNames, useStructureIssueText } from "./exam-structure-issues";
import { ExamStructureOutline } from "./exam-structure-outline";
import { ExamStructurePartPanel } from "./exam-structure-part-panel";
import { UnsavedChangesDialog, useUnsavedChangesGuard } from "./exam-structure-unsaved-guard";

const SELECTION_PARAMS = { part: "part", block: "block" } as const;

function readSelectionFromUrl(): StructureSelection | null {
  if (typeof window === "undefined") return null;
  const params = new URLSearchParams(window.location.search);
  const blockId = params.get(SELECTION_PARAMS.block);
  if (blockId) return { kind: "block", blockId };
  const partId = params.get(SELECTION_PARAMS.part);
  return partId ? { kind: "part", partId } : null;
}

/** Ghi mục đang mở vào `?block=` / `?part=` (giữ `?mockState=`) để tải lại hoặc gửi đường dẫn vẫn mở đúng chỗ. */
function writeSelectionToUrl(selection: StructureSelection): void {
  const url = new URL(window.location.href);
  url.searchParams.delete(SELECTION_PARAMS.block);
  url.searchParams.delete(SELECTION_PARAMS.part);
  if (selection.kind === "block") url.searchParams.set(SELECTION_PARAMS.block, selection.blockId);
  else url.searchParams.set(SELECTION_PARAMS.part, selection.partId);
  window.history.replaceState(window.history.state, "", url);
}

/**
 * Trang Cấu trúc và ma trận (bản vẽ `BaiThiCauTruc`, `BaiThiToHop`). Đọc bản làm việc của cấu trúc, sửa trên bản nháp,
 * lưu bằng nút Lưu (`updateExamStructure`); còn thay đổi chưa lưu thì hỏi lại trước khi rời trang.
 */
export function ExamStructureView({ examId }: { examId: string }) {
  const t = useTranslations("exams.structure");
  const common = useTranslations("common");
  const structureQuery = useExamStructure(examId);
  const examQuery = useExam(examId);
  const referenceQuery = useContentReference();
  // Mẫu cấu trúc chỉ để hiện ghi chú chuyên môn và biết phần tự chọn có nhánh Tiếng Anh; không chặn trang khi lỗi.
  const templatesQuery = useExamTemplates();

  if (structureQuery.isPending || referenceQuery.isPending || examQuery.isPending) {
    return (
      <div role="status" aria-label={common("loading")} className="grid grid-cols-1 gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]">
        <Skeleton className="h-96" />
        <div className="flex flex-col gap-4">
          <Skeleton className="h-24" />
          <Skeleton className="h-96" />
        </div>
      </div>
    );
  }

  if (structureQuery.isError || referenceQuery.isError || examQuery.isError) {
    return (
      <Card>
        <EmptyState
          icon={<TriangleAlert />}
          headingLevel={2}
          title={t("errorTitle")}
          description={t("errorDescription")}
          action={
            <Button
              variant="secondary"
              onClick={() => {
                void structureQuery.refetch();
                void referenceQuery.refetch();
                void examQuery.refetch();
              }}
            >
              {common("retry")}
            </Button>
          }
        />
      </Card>
    );
  }

  if (structureQuery.data.parts.length === 0) {
    return (
      <Card>
        <EmptyState
          icon={<LayoutList />}
          headingLevel={2}
          title={t("emptyTitle")}
          description={t("emptyDescription")}
          action={
            <Button asChild variant="secondary">
              <Link href={examTabHref(examId, "general")}>{t("emptyAction")}</Link>
            </Button>
          }
        />
      </Card>
    );
  }

  return (
    <StructureEditor
      exam={examQuery.data}
      structure={structureQuery.data}
      syncKey={structureQuery.dataUpdatedAt}
      reference={referenceQuery.data}
      template={templatesQuery.data?.find((item) => item.code === examQuery.data.templateCode) ?? null}
    />
  );
}

function StructureEditor({
  exam,
  structure,
  syncKey,
  reference,
  template,
}: {
  exam: ExamDetail;
  structure: ExamStructure;
  syncKey: number;
  reference: ContentReference;
  template: ExamTemplate | null;
}) {
  const t = useTranslations("exams.structure");
  const format = useFormatter();
  const lookup = useReferenceLookup(reference);
  const save = useUpdateExamStructure(exam.id);

  const [draft, setDraft] = useState<ExamPart[]>(structure.parts);
  const [baseline, setBaseline] = useState(() => JSON.stringify(structure.parts));
  const [syncedKey, setSyncedKey] = useState(syncKey);
  const dirty = JSON.stringify(draft) !== baseline;
  // Dữ liệu mới từ máy chủ (sau khi lưu, hoặc lane khác làm mới) chỉ thay bản nháp khi không còn gì chưa lưu.
  if (syncKey !== syncedKey) {
    setSyncedKey(syncKey);
    if (!dirty) {
      setDraft(structure.parts);
      setBaseline(JSON.stringify(structure.parts));
    }
  }

  const [selection, setSelection] = useState<StructureSelection | null>(() => {
    const fromUrl = readSelectionFromUrl();
    return fromUrl && selectionExists(structure.parts, fromUrl) ? fromUrl : defaultSelection(structure.parts);
  });
  const current = selection && selectionExists(draft, selection) ? selection : defaultSelection(draft);
  const [liveMessage, setLiveMessage] = useState("");
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const guard = useUnsavedChangesGuard(dirty);

  const select = useCallback((next: StructureSelection) => {
    setSelection(next);
    writeSelectionToUrl(next);
  }, []);

  const axes = useMemo<MatrixAxes>(
    () => ({
      grades: reference.grades.map((grade) => grade.code),
      difficulties: reference.difficulties.map((difficulty) => difficulty.code),
    }),
    [reference],
  );
  const names = useMemo<StructureNames>(
    () => ({
      subject: lookup.subject,
      combination: (code) =>
        reference.combinations.find((combination) => combination.code === code)?.subjectCodes.map(lookup.subject).join(" – ") ??
        code,
    }),
    [lookup, reference],
  );
  const shared = exam.timing === "SHARED";
  const issues = useMemo(() => validateStructure(draft, axes, { timing: exam.timing }), [draft, axes, exam.timing]);
  const totals = useMemo(
    () => structureTotals(draft, { sharedDuration: shared ? exam.durationMinutes : null }),
    [draft, shared, exam.durationMinutes],
  );
  const issueText = useStructureIssueText(draft, names);

  // Danh mục theo kỳ thi: quy tắc chấm mặc định, năng lực (V-ACT), tổ hợp và các môn ghép được khi sinh tổ hợp.
  const kind = reference.examKinds.find((item) => item.code === exam.kind);
  const kindCompetencies = useMemo(
    () => reference.competencies.filter((competency) => competency.examKindCodes.includes(exam.kind)),
    [reference, exam.kind],
  );
  const kindCombinations = useMemo(() => combinationsForKind(reference.combinations, exam.kind), [reference, exam.kind]);
  const subjectPool = useMemo(
    () =>
      reference.subjects
        .map((subject) => subject.code)
        .filter((code) => kindCombinations.some((combination) => combination.subjectCodes.includes(code))),
    [reference, kindCombinations],
  );
  const defaultCombinationSize = Math.max(0, ...kindCombinations.map((combination) => combination.subjectCodes.length));
  const branchSubjects = reference.subjects;
  const yearsPending = yearsNeedConfirmation(exam);

  const announce = (message: string) => setLiveMessage(message);

  const handleAddBlock = (partId: string, optionId: string | null, section: ExamSection) => {
    const block = newBlock(t("newBlockName", { subject: lookup.subject(section.subjectCode) }));
    setDraft((parts) => addBlock(parts, { partId, optionId, sectionId: section.id }, block));
    select({ kind: "block", blockId: block.id });
    announce(t("blockAdded", { name: block.name }));
  };

  const runSave = () => {
    save.mutate(
      { parts: draft },
      {
        onSuccess: () => {
          setBaseline(JSON.stringify(draft));
          const message = exam.check.status === "UNCHECKED" ? t("saved") : t("savedRecheck");
          notify.success(message);
          announce(message);
        },
        onError: () => {
          notify.error(t("saveError"));
          announce(t("saveError"));
        },
      },
    );
  };

  const blockContext = current?.kind === "block" ? findBlock(draft, current.blockId) : null;
  const selectedPart = current?.kind === "part" ? draft.find((part) => part.id === current.partId) : null;

  return (
    <div className="@container flex flex-col gap-4">
      <Card padding="md" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <dl aria-label={t("summary.label")} className="flex flex-wrap gap-x-6 gap-y-2 text-body">
            <SummaryItem
              label={t("summary.questions")}
              value={
                totals.printedQuestions !== totals.totalQuestions
                  ? t("summary.questionsPrinted", { count: totals.totalQuestions, printed: totals.printedQuestions })
                  : format.number(totals.totalQuestions)
              }
            />
            <SummaryItem
              label={t("summary.duration")}
              value={
                shared
                  ? t("summary.sharedMinutes", { count: totals.totalMinutes })
                  : t("summary.minutes", { count: totals.totalMinutes })
              }
            />
            <SummaryItem
              label={t("summary.points")}
              value={
                exam.maxScore !== null
                  ? t("summary.pointsOfScale", {
                      points: format.number(totals.totalPoints, { maximumFractionDigits: 2 }),
                      scale: format.number(exam.maxScore, { maximumFractionDigits: 2 }),
                    })
                  : format.number(totals.totalPoints, { maximumFractionDigits: 2 })
              }
              warn={exam.maxScore !== null && Math.abs(totals.totalPoints - exam.maxScore) > 1e-6}
            />
            <SummaryItem
              label={t("summary.allocated")}
              value={t("summary.allocatedValue", { allocated: totals.allocated, missing: totals.missing })}
            />
            <SummaryItem label={t("summary.issues")} value={format.number(issues.length)} warn={issues.length > 0} />
          </dl>
          <div className="flex flex-wrap items-center gap-2">
            {dirty && <StatusChip tone="warning">{t("unsavedChip")}</StatusChip>}
            <Button
              type="button"
              variant="secondary"
              disabled={!dirty || save.isPending}
              onClick={() => setConfirmDiscard(true)}
            >
              {t("discard")}
            </Button>
            <Button asChild variant="secondary">
              <Link href={examTabHref(exam.id, "review")}>
                <ClipboardCheck />
                <span>{t("checkLink")}</span>
              </Link>
            </Button>
            <Button type="button" onClick={runSave} loading={save.isPending} disabled={!dirty}>
              <Save />
              <span>{t("save")}</span>
            </Button>
          </div>
        </div>
        <p className="text-caption text-text-muted">
          {exam.servingVersion !== null ? t("workingNote", { version: exam.servingVersion }) : t("draftNote")}
        </p>
        {yearsPending && (
          <p className="flex flex-wrap items-start gap-x-2 gap-y-1 rounded-md border border-warning/40 bg-warning-bg px-3 py-2 text-body text-text">
            <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
            <span className="min-w-0 flex-1">
              {t("yearsPending", { structure: exam.structureYear ?? 0, matrix: exam.matrixYear ?? 0 })}
            </span>
            <Button asChild variant="link" size="sm">
              <Link href={examTabHref(exam.id, "general")}>{t("yearsLink")}</Link>
            </Button>
          </p>
        )}
        {template?.expertNote && (
          <p className="flex items-start gap-2 text-body text-text-secondary">
            <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
            <span>{t("expertNote", { note: template.expertNote })}</span>
          </p>
        )}
        {issues.length > 0 && (
          <details className="rounded-md border border-warning/40 bg-warning-bg px-3 py-2">
            <summary className="cursor-pointer text-body font-medium text-warning">
              {t("issuesSummary", { count: issues.length })}
            </summary>
            <ul className="mt-2 flex flex-col gap-1.5">
              {issues.map((issue, index) => (
                <li key={index} className="flex flex-wrap items-start justify-between gap-2 text-body text-text">
                  <span className="min-w-0 flex-1">{issueText(issue)}</span>
                  <Button type="button" variant="link" size="sm" onClick={() => select(issueTarget(issue))}>
                    {t("issueGo")}
                  </Button>
                </li>
              ))}
            </ul>
          </details>
        )}
        <LiveStatus message={liveMessage} />
      </Card>

      <div className="grid grid-cols-1 items-start gap-4 @5xl:grid-cols-[18rem_minmax(0,1fr)]">
        <ExamStructureOutline
          parts={draft}
          selection={current}
          issues={issues}
          names={names}
          onSelect={select}
          onAddBlock={handleAddBlock}
        />
        <div className="min-w-0">
          {blockContext ? (
            <BlockEditor
              key={blockContext.block.id}
              block={blockContext.block}
              subjectCode={blockContext.section.subjectCode}
              locationText={t("blockLocation", {
                part: blockContext.part.name,
                option: blockContext.option ? optionLabel(blockContext.option, names) : "",
                subject: lookup.subject(blockContext.section.subjectCode),
                position: blockContext.position,
                total: blockContext.section.blocks.length,
                hasOption: blockContext.option ? "yes" : "no",
              })}
              position={blockContext.position}
              siblingCount={blockContext.section.blocks.length}
              axes={axes}
              labels={{ grade: lookup.grade, difficulty: lookup.difficulty }}
              reference={reference}
              scoringDefaults={kind?.scoringDefaults ?? null}
              competencies={kindCompetencies}
              onChange={(block) => setDraft((parts) => updateBlock(parts, block.id, () => block))}
              onMove={(offset) => setDraft((parts) => moveBlock(parts, blockContext.block.id, offset))}
              onRemove={() => {
                const name = blockContext.block.name;
                setDraft((parts) => removeBlock(parts, blockContext.block.id));
                select({ kind: "part", partId: blockContext.part.id });
                announce(t("blockRemoved", { name }));
              }}
            />
          ) : selectedPart ? (
            <ExamStructurePartPanel
              part={selectedPart}
              timing={exam.timing}
              sharedMinutes={exam.durationMinutes}
              names={names}
              combinations={kindCombinations}
              showEnglish={
                selectedPart.options.some((option) => option.kind === "SUBJECT" && option.code === ENGLISH_CODE) ||
                (selectedPart.sections.length === 0 &&
                  !selectedPart.options.some((option) => option.kind === "BRANCH") &&
                  !template?.parts[selectedPart.order - 1]?.options?.length)
              }
              subjectPool={subjectPool}
              defaultCombinationSize={defaultCombinationSize}
              branchSubjects={branchSubjects}
              onPoints={(points) => setDraft((parts) => setPartPoints(parts, selectedPart.id, points))}
              onGenerate={(combinations) => {
                setDraft((parts) => attachGenerated(parts, selectedPart.id, combinations, lookup.subject));
                announce(t("combinationsGenerated", { count: combinations.length }));
              }}
              onAddBranch={(branch) => {
                setDraft((parts) => addBranch(parts, selectedPart.id, branch));
                announce(t("branchAdded", { name: branch.name.trim() }));
              }}
              onConfirm={(optionId) => {
                const option = selectedPart.options.find((item) => item.id === optionId);
                setDraft((parts) => confirmOption(parts, selectedPart.id, optionId));
                if (option) announce(t("optionConfirmed", { code: option.kind === "BRANCH" ? (option.name ?? option.code) : option.code }));
              }}
              issues={issues.filter((issue) =>
                ("partId" in issue ? issue.partId : issue.location.partId) === selectedPart.id,
              )}
              issueText={issueText}
              onChange={(update) => setDraft((parts) => updatePart(parts, selectedPart.id, update))}
              onQuestionCount={(count) =>
                setDraft((parts) => updatePart(parts, selectedPart.id, (part) => setPartQuestionCount(part, count)))
              }
              onSectionCount={(optionId, sectionId, questionCount) =>
                setDraft((parts) => setSectionCount(parts, { partId: selectedPart.id, optionId, sectionId }, questionCount))
              }
              onAddBlock={(optionId, section) => handleAddBlock(selectedPart.id, optionId, section)}
              onToggleEnglish={(enabled) => setDraft((parts) => setEnglishOption(parts, selectedPart.id, enabled))}
              onAttachCombination={(combination) => {
                setDraft((parts) => attachCombination(parts, selectedPart.id, combination));
                announce(t("combinationAttached", { code: combination.code }));
              }}
              onRemoveOption={(optionId) => {
                const option = selectedPart.options.find((item) => item.id === optionId);
                setDraft((parts) => removeOption(parts, selectedPart.id, optionId));
                if (option) announce(t("combinationRemoved", { code: option.kind === "BRANCH" ? (option.name ?? option.code) : option.code }));
              }}
              onSelectBlock={(blockId) => select({ kind: "block", blockId })}
            />
          ) : null}
        </div>
      </div>

      <ConfirmDialog
        open={confirmDiscard}
        onOpenChange={setConfirmDiscard}
        title={t("discardTitle")}
        description={t("discardDescription")}
        confirmLabel={t("discardConfirm")}
        onConfirm={() => {
          setDraft(JSON.parse(baseline) as ExamPart[]);
          setConfirmDiscard(false);
          announce(t("discarded"));
        }}
      />
      <UnsavedChangesDialog guard={guard} />
    </div>
  );
}

function SummaryItem({ label, value, warn = false }: { label: string; value: string; warn?: boolean }) {
  return (
    <div className="flex items-baseline gap-1.5">
      <dt className="text-text-muted">{label}</dt>
      <dd className={warn ? "m-0 font-semibold text-warning" : "m-0 font-semibold text-text"}>{value}</dd>
    </div>
  );
}
