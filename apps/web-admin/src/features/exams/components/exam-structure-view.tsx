"use client";

import { Button, Card, ConfirmDialog, EmptyState, LiveStatus, notify, Skeleton, StatusChip } from "@thi360/ui";
import { ClipboardCheck, Info, LayoutList, Save, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useCallback, useMemo, useState } from "react";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import { getErrorStatus } from "@/lib/api/query-client";
import { yearsNeedConfirmation } from "../check";
import { examTabHref } from "../editor-tabs";
import { combinationsForKind } from "../create-exam-schema";
import type { MatrixAxes } from "../matrix";
import { useExam, useExamStructure, useExamTemplates, useUpdateExamStructure } from "../queries";
import type { ExamDetail, ExamPart, ExamSection, ExamStructure, ExamTemplate } from "../types";
import { BlockEditor } from "./block-editor";
import {
  addBlock,
  addBranch,
  attachCombination,
  confirmOption,
  defaultSelection,
  ENGLISH_CODE,
  findBlock,
  type DraftLocation,
  draftTotals,
  findSection,
  moveBlock,
  newBlock,
  removeBlock,
  removeOption,
  selectionExists,
  setEnglishOption,
  setPartChoose,
  setPartOnPaper,
  setPartPoints,
  setPartQuestionCount,
  setSectionCount,
  type StructureSelection,
  updateBlock,
  updatePart,
  updateSection,
  validateDraft,
} from "./exam-structure-draft";
import { issueTarget, optionLabel, type StructureNames, useStructureIssueText } from "./exam-structure-issues";
import { ExamStructureList } from "./exam-structure-list";
import { ExamStructurePartPanel } from "./exam-structure-part-panel";
import { ExamStructureSubjectPanel } from "./exam-structure-subject-panel";
import { StructureVersionPicker } from "./structure-version-picker";
import { UnsavedChangesDialog, useUnsavedChangesGuard } from "./exam-structure-unsaved-guard";

const SELECTION_PARAMS = { part: "part", section: "section", block: "block" } as const;

function readSelectionFromUrl(): StructureSelection | null {
  if (typeof window === "undefined") return null;
  const params = new URLSearchParams(window.location.search);
  const blockId = params.get(SELECTION_PARAMS.block);
  if (blockId) return { kind: "block", blockId };
  const sectionId = params.get(SELECTION_PARAMS.section);
  if (sectionId) return { kind: "section", sectionId };
  const partId = params.get(SELECTION_PARAMS.part);
  return partId ? { kind: "part", partId } : null;
}

/** Ghi mục đang mở vào `?block=` / `?section=` / `?part=` (giữ tham số xem trạng thái của màn) để tải lại hoặc gửi đường dẫn vẫn mở đúng chỗ. */
function writeSelectionToUrl(selection: StructureSelection): void {
  const url = new URL(window.location.href);
  url.searchParams.delete(SELECTION_PARAMS.block);
  url.searchParams.delete(SELECTION_PARAMS.section);
  url.searchParams.delete(SELECTION_PARAMS.part);
  if (selection.kind === "block") url.searchParams.set(SELECTION_PARAMS.block, selection.blockId);
  else if (selection.kind === "section") url.searchParams.set(SELECTION_PARAMS.section, selection.sectionId);
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
      templates={templatesQuery.data ?? []}
      template={templatesQuery.data?.find((item) => item.code === examQuery.data.templateCode) ?? null}
    />
  );
}

function StructureEditor({
  exam,
  structure,
  syncKey,
  reference,
  templates,
  template,
}: {
  exam: ExamDetail;
  structure: ExamStructure;
  syncKey: number;
  reference: ContentReference;
  templates: readonly ExamTemplate[];
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
  const issues = useMemo(() => validateDraft(draft, axes, { timing: exam.timing }), [draft, axes, exam.timing]);
  const totals = useMemo(
    () => draftTotals(draft, { sharedDuration: shared ? exam.durationMinutes : null }),
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
  const branchSubjects = reference.subjects;
  const yearsPending = yearsNeedConfirmation(exam);

  const announce = (message: string) => setLiveMessage(message);

  const handleAddBlock = (location: DraftLocation, section: ExamSection) => {
    const block = newBlock(t("newBlockName", { subject: lookup.subject(section.subjectCode) }));
    setDraft((parts) => addBlock(parts, location, block));
    select({ kind: "block", blockId: block.id });
    announce(t("blockAdded", { name: block.name }));
  };

  const runSave = () => {
    save.mutate(
      { parts: draft, expectedRevision: exam.revision },
      {
        onSuccess: () => {
          setBaseline(JSON.stringify(draft));
          const message = exam.check.status === "UNCHECKED" ? t("saved") : t("savedRecheck");
          notify.success(message);
          announce(message);
        },
        onError: (error) => {
          // 412: bài đã được người khác sửa (không tự nạp lại để không mất bản nháp); 422: dữ liệu sai kiểu.
          const status = getErrorStatus(error);
          const message = status === 412 ? t("saveConflict") : status === 422 ? t("saveInvalid") : t("saveError");
          notify.error(message);
          announce(message);
        },
      },
    );
  };

  const blockContext = current?.kind === "block" ? findBlock(draft, current.blockId) : null;
  const sectionContext = current?.kind === "section" ? findSection(draft, current.sectionId) : null;
  const selectedPart =
    current?.kind === "part" || current?.kind === "option" ? draft.find((part) => part.id === current.partId) : null;

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
        <StructureVersionPicker
          exam={exam}
          templates={templates}
          dirty={dirty}
          onAnnounce={announce}
          onApplied={(parts) => {
            setDraft(parts);
            setBaseline(JSON.stringify(parts));
          }}
        />
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
        <ExamStructureList
          parts={draft}
          combinations={structure.combinations ?? []}
          sectionStates={structure.sectionStates ?? null}
          selection={current}
          issues={issues}
          names={names}
          onSelect={select}
        />
        <div className="min-w-0">
          {sectionContext ? (
            <ExamStructureSubjectPanel
              key={sectionContext.section.id}
              context={sectionContext}
              subjectName={lookup.subject(sectionContext.section.subjectCode)}
              axes={axes}
              labels={{ grade: lookup.grade, difficulty: lookup.difficulty }}
              reference={reference}
              onChange={(update) => setDraft((parts) => updateSection(parts, sectionContext.location, update))}
              onSelectBlock={(blockId) => select({ kind: "block", blockId })}
              onAddBlock={() => handleAddBlock(sectionContext.location, sectionContext.section)}
            />
          ) : blockContext ? (
            <BlockEditor
              key={blockContext.block.id}
              block={blockContext.block}
              section={blockContext.section}
              onOpenSection={() => select({ kind: "section", sectionId: blockContext.section.id })}
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
              branchSubjects={branchSubjects}
              onPoints={(points) => setDraft((parts) => setPartPoints(parts, selectedPart.id, points))}
              onOnPaper={(count) =>
                setDraft((parts) => updatePart(parts, selectedPart.id, (part) => setPartOnPaper(part, count)))
              }
              onChoose={(choose) =>
                setDraft((parts) => updatePart(parts, selectedPart.id, (part) => setPartChoose(part, choose)))
              }
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
              onAddBlock={(optionId, section) => handleAddBlock({ partId: selectedPart.id, optionId, sectionId: section.id }, section)}
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
              onSelectSection={(sectionId) => select({ kind: "section", sectionId })}
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
