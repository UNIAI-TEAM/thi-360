"use client";

import { Button, Card, ConfirmDialog, EmptyState, LiveStatus, notify, Skeleton } from "@thi360/ui";
import { ArrowRight, ClipboardCheck, Info, LayoutList, Save, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { focusExamQuotaWhenReady } from "../exam-overview";
import { focusMapidRuleWhenReady } from "../mapid-rules";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import { getErrorStatus } from "@/lib/api/query-client";
import { examQuotaBlockers, fillReviewCounts, rendersReviewPanel, saveBlockers } from "../allocation";
import { HsaExamContext, hsaLoadedBaselines, hsaNeedsNormalizing, isHsaExam, normalizeHsaParts, useHsaNormalizedDraft } from "../hsa-enforcement";
import { strandTable } from "../reference-distribution";
import { yearsNeedConfirmation } from "../check";
import { matchingTemplates } from "../create-exam-schema";
import { DISPLAY_NUMBER_FORMAT, roundForDisplay } from "../display-number";
import { examTabFromPathname, examTabHref } from "../editor-tabs";
import { addPoolSection } from "../coded-part";
import type { MatrixAxes } from "../matrix";
import { useExam, useExamStructure, useExamStructureSummary, useExamTemplates, useUpdateExamStructure } from "../queries";
import { useRunExamCheck } from "../review-queries";
import { ExamsApiError } from "../errors";
import type { ExamCheckResult, RunExamCheckRequest } from "../review-types";
import type { ExamDetail, ExamPart, ExamQuota, ExamSection, ExamStructure, ExamTemplate } from "../types";
import { BlockEditor } from "./block-editor";
import { blockCountsLocked } from "./block-form";
import type { TopicQuotaServerError } from "./topic-quota-table";
import { HsaNormalizedNote } from "../subject-forms/enforcement-control";
import {
  selectionScope,
  addBlock,
  addBranch,
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
  setPartPoints,
  type StructureSelection,
  updateBlock,
  updatePart,
  updateSection,
  validateDraft,
} from "./exam-structure-draft";
import { useMapidRuleText } from "./mapid-rules-table";
import { ExamOverviewPanel } from "./exam-overview-panel";
import { optionLabel, type StructureNames, useStructureIssueText } from "./exam-structure-issues";
import { describeStructureChanges } from "./exam-structure-changes";
import { ExamStructureLeaveDialog, type LeaveDestination } from "./exam-structure-leave-dialog";
import { ExamStructureList } from "./exam-structure-list";
import { ExamStructurePartPanel } from "./exam-structure-part-panel";
import { ExamStructureSubjectPanel } from "./exam-structure-subject-panel";
import { ExamStructureAlertsBanner, ExamStructureStatusStrip } from "./exam-structure-summary";
import { checkIssueTarget } from "./exam-structure-check-result";
import { StructureVersionPicker } from "./structure-version-picker";
import { SubjectChoiceNav } from "./subject-choice-nav";
import { PracticeReviewSubjectPanel } from "./practice-review-subject-panel";
import { ScoringExamples, StrandReference } from "./scoring-examples";
import { useUnsavedChangesGuard } from "./exam-structure-unsaved-guard";
import { formBlockingIssues, type BlockingIssue } from "../subject-forms/blocking";
import { SubjectFormScreen } from "../subject-forms/form-renderers";
import { FullFormNotice } from "../subject-forms/full-form-notice";
import { focusFormTarget, formTarget, type FormTarget } from "../subject-forms/navigation";
import { createRawInputStore } from "../subject-forms/raw-input-store";
import { pinnedForm, selectAllSubjectForms, type SubjectFormPins } from "../subject-forms/selection";

const SELECTION_PARAMS = { exam: "exam", part: "part", option: "option", section: "section", block: "block", rule: "rule", quota: "quota" } as const;

function readSelectionFromUrl(): StructureSelection | null {
  if (typeof window === "undefined") return null;
  const params = new URLSearchParams(window.location.search);
  const blockId = params.get(SELECTION_PARAMS.block);
  if (params.get(SELECTION_PARAMS.exam) === "1" || params.get(SELECTION_PARAMS.quota)) return { kind: "exam" };
  if (blockId) return { kind: "block", blockId };
  const sectionId = params.get(SELECTION_PARAMS.section);
  if (sectionId) return { kind: "section", sectionId };
  const partId = params.get(SELECTION_PARAMS.part);
  const optionId = params.get(SELECTION_PARAMS.option);
  if (partId && optionId) return { kind: "option", partId, optionId };
  return partId ? { kind: "part", partId } : null;
}

/** Ghi mục đang mở vào `?block=` / `?section=` / `?option=&part=` / `?part=` (giữ tham số xem trạng thái của màn) để tải lại hoặc gửi đường dẫn vẫn mở đúng chỗ. */
function writeSelectionToUrl(selection: StructureSelection): void {
  const url = new URL(window.location.href);
  url.searchParams.delete(SELECTION_PARAMS.exam);
  url.searchParams.delete(SELECTION_PARAMS.block);
  url.searchParams.delete(SELECTION_PARAMS.section);
  url.searchParams.delete(SELECTION_PARAMS.option);
  url.searchParams.delete(SELECTION_PARAMS.part);
  url.searchParams.delete(SELECTION_PARAMS.rule);
  url.searchParams.delete(SELECTION_PARAMS.quota);
  if (selection.kind === "exam") url.searchParams.set(SELECTION_PARAMS.exam, "1");
  else if (selection.kind === "block") url.searchParams.set(SELECTION_PARAMS.block, selection.blockId);
  else if (selection.kind === "section") url.searchParams.set(SELECTION_PARAMS.section, selection.sectionId);
  else {
    url.searchParams.set(SELECTION_PARAMS.part, selection.partId);
    if (selection.kind === "option") url.searchParams.set(SELECTION_PARAMS.option, selection.optionId);
  }
  window.history.replaceState(window.history.state, "", url);
}

export function ExamStructureView({ examId }: { examId: string }) {
  const t = useTranslations("exams.structure");
  const common = useTranslations("common");
  const structureQuery = useExamStructure(examId);
  const examQuery = useExam(examId);
  const referenceQuery = useContentReference();
  // Mẫu cấu trúc quyết định `fixedCounts` (khóa số câu) và bài ôn (màn rút gọn): chưa tải xong hoặc lỗi thì chưa cho sửa, kẻo
  // người dùng sửa số câu bị khóa hoặc thấy màn đầy đủ của bài ôn rồi bị máy chủ từ chối (R1-27).
  const templatesQuery = useExamTemplates();

  if (structureQuery.isPending || referenceQuery.isPending || examQuery.isPending || templatesQuery.isPending) {
    return (
      <div role="status" aria-label={common("loading")} className="grid grid-cols-1 gap-4 lg:grid-cols-[18.75rem_minmax(0,1fr)]">
        <Skeleton className="h-96" />
        <div className="flex flex-col gap-4">
          <Skeleton className="h-24" />
          <Skeleton className="h-96" />
        </div>
      </div>
    );
  }

  // Lần làm mới nền lỗi khi dữ liệu đã có trong bộ nhớ đệm không được gỡ trình sửa (mất bản nháp chưa lưu, R2-02).
  if (structureQuery.isError || referenceQuery.isError || examQuery.isError || (templatesQuery.isError && templatesQuery.data === undefined)) {
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
                void templatesQuery.refetch();
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
    <HsaExamContext.Provider value={isHsaExam(examQuery.data.kind)}>
      <StructureEditor
        exam={examQuery.data}
        structure={structureQuery.data}
        syncKey={structureQuery.dataUpdatedAt}
        reference={referenceQuery.data}
        templates={templatesQuery.data ?? []}
        template={templatesQuery.data?.find((item) => item.code === examQuery.data.templateCode) ?? null}
      />
    </HsaExamContext.Provider>
  );
}

function StructureEditor({
  exam,
  structure: loadedStructure,
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
  const reviewMode = exam.buildMode === "REVIEW";
  // H42, R-135: mẫu `fixedBlockCounts` khóa số câu của bài phạm vi `SUBJECT`; bài `PART` và bài ôn từ cùng mẫu vẫn sửa được.
  const rowsLocked = blockCountsLocked(template, exam.scope, reviewMode);
  const templateLocked = template?.fixedCounts === true || rowsLocked;
  // Bài ôn mới tạo có nhóm duy nhất chưa khai số câu: điền số câu bước 1 để màn, tổng và nội dung lưu cùng một số (V-01b).
  const structure = useMemo(
    () => (reviewMode ? { ...loadedStructure, parts: fillReviewCounts(loadedStructure.parts) } : loadedStructure),
    [loadedStructure, reviewMode],
  );
  const t = useTranslations("exams.structure");
  const commonT = useTranslations("exams.subjectForm.common");
  const examsT = useTranslations("exams");
  const format = useFormatter();
  const router = useRouter();
  const lookup = useReferenceLookup(reference);
  const mapidText = useMapidRuleText(reference);
  const save = useUpdateExamStructure(exam.id);
  const check = useRunExamCheck(exam.id);
  const summaryQuery = useExamStructureSummary(exam.id);
  const [checkResult, setCheckResult] = useState<ExamCheckResult | null>(null);

  const hsa = isHsaExam(exam.kind);
  const [draft, setDraft] = useState<ExamPart[]>(structure.parts);
  const [quotas, setQuotas] = useState<ExamQuota[]>(structure.examQuotas ?? []);
  useHsaNormalizedDraft(hsa, draft, setDraft, quotas, setQuotas);
  const [baseline, setBaseline] = useState(() => JSON.stringify(structure.parts));
  const [quotaBaseline, setQuotaBaseline] = useState(() => JSON.stringify(structure.examQuotas ?? []));
  const [syncedKey, setSyncedKey] = useState(syncKey);
  const draftJson = JSON.stringify(draft);
  const draftDirty = draftJson !== baseline || JSON.stringify(quotas) !== quotaBaseline;
  // Nhập thô của biểu mẫu theo môn (FTM R-15): giá trị đã gõ mà chưa ghi được vào bản nháp. Sống theo phiên của view, không vào `PUT`.
  const [rawInputs] = useState(createRawInputStore);
  const rawEntries = useSyncExternalStore(rawInputs.subscribe, rawInputs.getSnapshot, rawInputs.getSnapshot);
  /** Có thay đổi chưa lưu khi bản nháp khác bản đã nạp hoặc còn nhập thô; dùng cho mọi chỗ hỏi trước khi mất dữ liệu. `dirty` giữ nghĩa cũ (chỉ bản nháp) cho danh sách thay đổi. */
  const hasUnsavedChanges = draftDirty || rawEntries.length > 0;
  const dirty = draftDirty;
  // Dữ liệu mới từ máy chủ (sau khi lưu, hoặc lane khác làm mới) chỉ thay bản nháp khi không còn gì chưa lưu, kể cả nhập thô.
  if (syncKey !== syncedKey) {
    setSyncedKey(syncKey);
    if (!hasUnsavedChanges) {
      setDraft(structure.parts);
      setBaseline(JSON.stringify(structure.parts));
      setQuotas(structure.examQuotas ?? []);
      setQuotaBaseline(JSON.stringify(structure.examQuotas ?? []));
    }
  }
  const draftSnapshot = `${draftJson}\n${JSON.stringify(quotas)}`;
  const [topicFailure, setTopicFailure] = useState<{ code: TopicQuotaServerError; snapshot: string } | null>(null);
  const topicError = topicFailure && topicFailure.snapshot === draftSnapshot ? topicFailure.code : null;
  // Lỗi 422 `EXAM_QUOTA_SCOPE_INVALID` của chỉ tiêu cấp bài: hiện ở khối chỉ tiêu cấp bài khi danh sách còn như lúc lưu lỗi.
  const [quotaScopeFailure, setQuotaScopeFailure] = useState<string | null>(null);
  const quotaScopeError = quotaScopeFailure !== null && quotaScopeFailure === JSON.stringify(quotas);
  const baselineParts = useMemo(() => JSON.parse(baseline) as ExamPart[], [baseline]);
  // Bản đã lưu còn mức Tham khảo mà HSA không cho: ghi chú hiện cho tới khi lưu.
  const hsaNormalizing = useMemo(() => hsaNeedsNormalizing(hsa, baselineParts, quotaBaseline), [hsa, baselineParts, quotaBaseline]);

  // Biểu mẫu theo môn (FTM R-1, R-2): tính một lần khi mở và khi nạp mẫu (sau chuẩn hóa HSA-C), ghim trong phiên; sửa bản nháp không đổi.
  const selectionExam = useMemo(() => ({ kind: exam.kind, buildMode: exam.buildMode }), [exam.kind, exam.buildMode]);
  const pinForms = (parts: readonly ExamPart[]): SubjectFormPins =>
    selectAllSubjectForms(selectionExam, hsa ? normalizeHsaParts(parts) : parts);
  const [pinnedForms, setPinnedForms] = useState<SubjectFormPins>(() => pinForms(structure.parts));
  const [focusRequest, setFocusRequest] = useState<{ target: FormTarget; nonce: number } | null>(null);
  const formRegionRef = useRef<HTMLDivElement>(null);
  const focusNonce = useRef(0);
  /** Đổi mỗi lần bỏ thay đổi hoặc nạp mẫu để màn MT/DS dựng lại, không giữ chữ đang gõ của bản nháp đã bỏ. */
  const [formEpoch, setFormEpoch] = useState(0);

  const [selection, setSelection] = useState<StructureSelection | null>(() => {
    const fromUrl = readSelectionFromUrl();
    return fromUrl && selectionExists(structure.parts, fromUrl) ? fromUrl : defaultSelection(structure.parts);
  });
  // `?rule=` / `?quota=` (liên kết từ trang Kiểm tra và xem thử): cuộn tới quy tắc MAPID hoặc chỉ tiêu cấp bài khi mở trang.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const ruleId = params.get(SELECTION_PARAMS.rule);
    if (ruleId) void focusMapidRuleWhenReady(ruleId);
    const quotaId = params.get(SELECTION_PARAMS.quota);
    if (quotaId) void focusExamQuotaWhenReady(quotaId);
  }, []);
  const requested = selection && selectionExists(draft, selection) ? selection : defaultSelection(draft);
  const requestedBlock = requested?.kind === "block" ? findBlock(draft, requested.blockId) : null;
  const routedTarget = requestedBlock ? formTarget(pinnedForm(pinnedForms, requestedBlock.section.id), { sectionId: requestedBlock.section.id, blockId: requestedBlock.block.id }) : null;
  const compactTarget = routedTarget !== null && routedTarget.kind !== "LEGACY_BLOCK" ? routedTarget : null;
  const current: StructureSelection | null =
    requestedBlock && (compactTarget !== null || (reviewMode && rendersReviewPanel(requestedBlock.section, reviewMode)))
      ? { kind: "section", sectionId: requestedBlock.section.id }
      : requested;
  // Đặt focus vào ô (MT) hay dòng (DS) của đích sau khi môn vẽ xong (R-15): đích của `?block=`, bấm lỗi, bấm tóm tắt, hoặc của lỗi chặn lưu.
  const activeTarget = focusRequest?.target ?? compactTarget;
  const focusKey = activeTarget ? JSON.stringify({ target: activeTarget, nonce: focusRequest?.nonce ?? 0, current }) : "";
  useEffect(() => {
    if (!focusKey || !formRegionRef.current) return;
    const { target } = JSON.parse(focusKey) as { target: FormTarget };
    focusFormTarget(formRegionRef.current, target);
  }, [focusKey]);
  const [liveMessage, setLiveMessage] = useState("");
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const guard = useUnsavedChangesGuard(hasUnsavedChanges);
  const [pendingSelection, setPendingSelection] = useState<StructureSelection | null>(null);
  const [leaveError, setLeaveError] = useState<string | null>(null);
  // Dòng nhập theo tỷ lệ không ra chùm nguyên (R-142): lỗi báo tại dòng, bản nháp không đổi, nhưng không cho lưu cho đến khi sửa.
  const [rateInvalid, setRateInvalid] = useState(false);

  /** Mở mục không qua hộp thoại: sau chính thao tác của người dùng (thêm, xóa nhóm) hoặc sau khi đã lưu. */
  const selectNow = useCallback((next: StructureSelection) => {
    setSelection(next);
    setFocusRequest(null);
    writeSelectionToUrl(next);
  }, []);
  const select = (next: StructureSelection) => {
    if (hasUnsavedChanges && current && selectionScope(draft, next) !== selectionScope(draft, current)) {
      setLeaveError(null);
      setPendingSelection(next);
      return;
    }
    selectNow(next);
  };

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
    () => draftTotals(draft, { sharedDuration: shared ? exam.durationMinutes : null, combinations: structure.combinations ?? [] }),
    [draft, shared, exam.durationMinutes, structure.combinations],
  );
  const issueText = useStructureIssueText(draft, names);

  // Danh mục theo kỳ thi: quy tắc chấm mặc định, năng lực (V-ACT), tổ hợp và các môn ghép được khi sinh tổ hợp.
  const kind = reference.examKinds.find((item) => item.code === exam.kind);
  const kindCompetencies = useMemo(
    () => reference.competencies.filter((competency) => competency.examKindCodes.includes(exam.kind)),
    [reference, exam.kind],
  );
  const branchSubjects = reference.subjects;
  const yearsPending = yearsNeedConfirmation(exam);

  const announce = (message: string) => setLiveMessage(message);

  const handleAddBlock = (location: DraftLocation, section: ExamSection) => {
    const block = newBlock(t("newBlockName", { subject: lookup.subject(section.subjectCode) }));
    setDraft((parts) => addBlock(parts, location, block));
    selectNow({ kind: "block", blockId: block.id });
    announce(t("blockAdded", { name: block.name }));
  };

  const saveErrorText = (error: unknown) => {
    // 412: bài đã được người khác sửa (không tự nạp lại để không mất bản nháp); 422: dữ liệu sai kiểu.
    const status = getErrorStatus(error);
    return status === 412 ? t("saveConflict") : status === 422 ? t("saveInvalid") : t("saveError");
  };

  const goToIssue = (issue: BlockingIssue) => {
    const target = formTarget(pinnedForm(pinnedForms, issue.sectionId), { sectionId: issue.sectionId, blockId: issue.blockId, field: issue.field });
    selectNow(target?.kind === "LEGACY_BLOCK" ? { kind: "block", blockId: target.blockId } : { kind: "section", sectionId: issue.sectionId });
    if (target && target.kind !== "LEGACY_BLOCK") {
      focusNonce.current += 1;
      setFocusRequest({ target, nonce: focusNonce.current });
    }
  };
  const [pendingBlockedIssue, setPendingBlockedIssue] = useState<BlockingIssue | null>(null);
  const blockedByForm = (onFailed?: (message: string) => void): boolean => {
    const issue = formBlockingIssues({ exam: selectionExam, draft, pinnedForms, rawInputs: rawEntries })[0];
    if (!issue) return false;
    const message = commonT("blocked");
    if (onFailed) setPendingBlockedIssue(issue);
    else goToIssue(issue);
    if (!onFailed) notify.error(message);
    announce(message);
    onFailed?.(message);
    return true;
  };
  const discardAll = () => {
    setDraft(JSON.parse(baseline) as ExamPart[]);
    setQuotas(JSON.parse(quotaBaseline) as ExamQuota[]);
    setTopicFailure(null);
    // Bỏ thay đổi bỏ cả nhập thô và ghim lại biểu mẫu theo bản đã lưu (R-2, R-15): không còn lỗi chặn nào từ `id` cũ.
    rawInputs.clearAll();
    setFormEpoch((epoch) => epoch + 1);
    setPinnedForms(pinForms(JSON.parse(baseline) as ExamPart[]));
    setFocusRequest(null);
  };

  const runSave = (options: { onSaved?: () => void; onFailed?: (message: string) => void } = {}) => {
    if (blockedByForm(options.onFailed)) return;
    if (rateInvalid) {
      const message = t("rateBlocked");
      if (!options.onFailed) notify.error(message);
      announce(message);
      options.onFailed?.(message);
      return;
    }
    // Bảng phân bổ còn lỗi người dùng phải sửa (tổng tỷ lệ khác 100%, quá 8 chỉ tiêu): mở đúng chỗ, không gửi lên máy chủ.
    const blocker = saveBlockers(draft, axes)[0];
    const quotaBlocked = blocker ? false : examQuotaBlockers(quotas).length > 0;
    if (blocker || quotaBlocked) {
      // Quy tắc MAPID trỏ tới nhóm đã xóa: nêu câu của quy tắc và tên nhóm đã xóa, không đẩy người dùng sang bảng phân bổ (R1-F4).
      const orphanSection = blocker?.kind === "MAPID_GROUP" ? findSection(draft, blocker.sectionId)?.section : undefined;
      const orphan = orphanSection?.mapidRules?.find((rule) => rule.id === blocker?.ruleId);
      const message =
        orphanSection && orphan
          ? t("mapidGroupBlocked", {
              rule: mapidText.sentence(orphanSection, orphan),
              hasGroup: orphan.deletedGroupName ? "yes" : "no",
              group: orphan.deletedGroupName ?? "",
            })
          : t("allocationBlocked");
      if (!blocker) selectNow({ kind: "exam" });
      else selectNow(blocker.blockId ? { kind: "block", blockId: blocker.blockId } : { kind: "section", sectionId: blocker.sectionId });
      if (!options.onFailed) notify.error(message);
      announce(message);
      options.onFailed?.(message);
      return;
    }
    save.mutate(
      { parts: draft, examQuotas: quotas, expectedRevision: exam.revision },
      {
        onSuccess: () => {
          setBaseline(draftJson);
          setQuotaBaseline(JSON.stringify(quotas));
          setTopicFailure(null);
          // Lưu làm kết quả kiểm tra cũ hết hiệu lực: các môn đã đổi chuyển thành "Cần kiểm tra lại".
          setCheckResult(null);
          const message = exam.check.status === "UNCHECKED" ? t("saved") : t("savedRecheck");
          notify.success(message);
          announce(message);
          options.onSaved?.();
        },
        onError: (error) => {
          const message = saveErrorText(error);
          // 422 của khối chỉ tiêu chủ đề hiện ngay ở khối đó (H20), không chỉ ở thông báo nổi: mở nhóm, môn đang quá hạn mức.
          const topic = (error instanceof ExamsApiError ? error.fieldErrors : []).find((item) => /topicQuotas/.test(item.field));
          if (topic) {
            setTopicFailure({ code: topic.code === "PERCENT_OUT_OF_RANGE" ? "PERCENT_OUT_OF_RANGE" : "MAX_TOPIC_QUOTAS", snapshot: draftSnapshot });
            const over = saveBlockers(draft, axes).find((item) => item.kind === "TOPIC_MAX");
            if (over) selectNow(over.blockId ? { kind: "block", blockId: over.blockId } : { kind: "section", sectionId: over.sectionId });
          }
          if (error instanceof ExamsApiError && error.code === "EXAM_QUOTA_SCOPE_INVALID") {
            setQuotaScopeFailure(JSON.stringify(quotas));
            selectNow({ kind: "exam" });
          }
          // Có `onFailed` thì nơi gọi tự hiện lỗi (ví dụ `role="alert"` trong hộp thoại chuyển mục); không toast kẻo trùng.
          if (!options.onFailed) notify.error(message);
          announce(message);
          options.onFailed?.(message);
        },
      },
    );
  };

  /** Kiểm tra chạy trên bản đã lưu: còn thay đổi chưa lưu thì lưu trước, lưu lỗi thì không kiểm tra. */
  const runCheck = (request: Omit<RunExamCheckRequest, "examId">, after?: (result: ExamCheckResult) => void) => {
    if (blockedByForm()) return;
    const run = () =>
      check.mutate(request, {
        onSuccess: (result) => {
          setCheckResult(result);
          announce(t("check.done"));
          after?.(result);
        },
        onError: () => {
          notify.error(t("check.error"));
          announce(t("check.error"));
        },
      });
    if (dirty) runSave({ onSaved: run });
    else run();
  };

  /** "Tiếp tục": kiểm tra cả bài, rồi sang tab Kiểm tra nếu không có lỗi, hoặc dẫn tới lỗi đầu tiên. */
  const continueToReview = () =>
    runCheck({ scope: "EXAM" }, (result) => {
      const firstError = result.groups.flatMap((group) => group.issues).find((issue) => issue.severity === "ERROR");
      if (!firstError) {
        router.push(examTabHref(exam.id, "review"));
        return;
      }
      const target = checkIssueTarget(firstError);
      if (target) selectNow(target);
    });

  const leaveOpen = pendingSelection !== null || guard.pendingHref !== null;
  const stayHere = () => {
    setPendingSelection(null);
    setLeaveError(null);
    guard.stay();
    const issue = pendingBlockedIssue;
    setPendingBlockedIssue(null);
    if (issue) goToIssue(issue);
  };
  const proceedLeave = () => {
    const next = pendingSelection;
    setPendingSelection(null);
    setLeaveError(null);
    setPendingBlockedIssue(null);
    if (next) selectNow(next);
    else guard.leave();
  };
  const leaveAfterSave = () => runSave({ onSaved: proceedLeave, onFailed: setLeaveError });
  const leaveAfterDiscard = () => {
    discardAll();
    proceedLeave();
  };

  /** Tên môn hoặc phần của một mục để gọi trong hộp thoại chuyển mục; nhóm gọi theo tên nhóm. */
  const selectionName = (parts: readonly ExamPart[], target: StructureSelection, scope: boolean): string | null => {
    if (target.kind === "exam") return t("list.overview");
    if (target.kind === "part") return parts.find((part) => part.id === target.partId)?.name ?? null;
    if (target.kind === "option") {
      const option = parts.find((part) => part.id === target.partId)?.options.find((item) => item.id === target.optionId);
      return option ? optionLabel(option, names) : null;
    }
    if (target.kind === "section") {
      const found = findSection(parts, target.sectionId);
      return found ? lookup.subject(found.section.subjectCode) : null;
    }
    const found = findBlock(parts, target.blockId);
    if (!found) return null;
    return scope ? lookup.subject(found.section.subjectCode) : found.block.name || t("block.untitled");
  };
  const leaveItemName = leaveOpen && current ? selectionName(draft, current, true) : null;
  const leaveDestination: LeaveDestination = (() => {
    if (pendingSelection) return { kind: "item", name: selectionName(draft, pendingSelection, false) ?? "" };
    const href = guard.pendingHref;
    const base = `/exams/${encodeURIComponent(exam.id)}`;
    const path = href?.split(/[?#]/)[0] ?? "";
    if (path !== base && !path.startsWith(`${base}/`)) return { kind: "page" };
    const tab = examTabFromPathname(path);
    return tab === "general" ? { kind: "general" } : { kind: "tab", tab: examsT(`editor.tabs.${tab}`) };
  })();
  const leaveChanges = useMemo(
    () => (leaveOpen ? describeStructureChanges(JSON.parse(baseline) as ExamPart[], draft, lookup.subject) : []),
    [leaveOpen, baseline, draft, lookup],
  );

  const blockContext = current?.kind === "block" ? findBlock(draft, current.blockId) : null;
  const sectionContext = current?.kind === "section" ? findSection(draft, current.sectionId) : null;
  // Biểu mẫu ghim của môn đang mở (R-2): MT, DS vẽ bằng màn đã đăng ký; chưa có màn thì giữ MD, không để môn trống.
  const sectionForm = sectionContext ? pinnedForm(pinnedForms, sectionContext.section.id) : null;
  const formKind = sectionForm?.form === "MATRIX" || sectionForm?.form === "ROWS" ? sectionForm.form : null;
  const selectedPart =
    current?.kind === "part" || current?.kind === "option" ? draft.find((part) => part.id === current.partId) : null;
  const sectionStates = checkResult?.sectionStates ?? structure.sectionStates ?? null;
  const sourceVerified = (section: ExamSection): boolean => {
    if (sectionStates?.find((item) => item.sectionId === section.id)?.state !== "CHECKED") return false;
    const saved = findSection(baselineParts, section.id)?.section;
    return saved !== undefined && JSON.stringify(saved) === JSON.stringify(section);
  };
  const summaryLoad = summaryQuery.isPending ? "loading" : summaryQuery.data ? "ready" : "unavailable";
  const checkSectionId = sectionContext?.section.id ?? blockContext?.section.id ?? null;
  const checkRequest: Omit<RunExamCheckRequest, "examId"> | null = checkSectionId
    ? { scope: "SECTION", sectionId: checkSectionId }
    : selectedPart
      ? { scope: "PART", partId: selectedPart.id }
      : null;
  const checkAction = checkRequest ? (
    <Button type="button" variant="secondary" size="sm" loading={check.isPending || save.isPending} onClick={() => runCheck(checkRequest)}>
      <ClipboardCheck />
      <span>{checkRequest.scope === "SECTION" ? t("check.section") : t("check.part")}</span>
    </Button>
  ) : null;

  const hasVersionPicker =
    matchingTemplates(templates, {
      kind: exam.kind,
      scope: exam.scope,
      partCode: exam.partCode ?? "",
      subjectCode: exam.subjectCode ?? "",
    }).length >= 2;
  const showTopStrip = hsaNormalizing || hasVersionPicker || yearsPending || Boolean(template?.expertNote);
  const statusNote = exam.servingVersion !== null ? t("workingNote", { version: exam.servingVersion }) : t("draftNote");
  const effectiveSummary =
    reviewMode && JSON.stringify(loadedStructure.parts) !== JSON.stringify(structure.parts)
      ? null
      : (summaryQuery.data ?? null);
  const sectionStrandTable =
    sectionContext && !rendersReviewPanel(sectionContext.section, reviewMode)
      ? strandTable(sectionContext.section, quotas, exam.scope)
      : null;

  return (
    <div className="@container flex flex-col gap-4">
      <div data-testid="structure-workspace" className="overflow-clip rounded-lg border border-border bg-surface shadow-xs">
        <div
          data-testid="structure-toolbar"
          className="sticky top-0 z-20 flex flex-wrap items-center justify-between gap-3 border-b border-border bg-surface/95 px-4 py-3 backdrop-blur-xs sm:px-5"
        >
          <ExamStructureStatusStrip
            summary={effectiveSummary}
            load={summaryLoad}
            local={{ allocated: totals.allocated, total: totals.totalQuestions, missing: totals.missing }}
            statusNote={statusNote}
            hasUnsavedChanges={hasUnsavedChanges}
            unsavedLabel={t("unsavedChip")}
            issueCount={issues.length}
            canOpenOverview={!reviewMode && current?.kind !== "exam" && Boolean(effectiveSummary && effectiveSummary.rows.length > 0)}
            onOpenOverview={() => select({ kind: "exam" })}
          />
          <div className="flex flex-wrap items-center gap-2">
            <Button type="button" variant="secondary" size="sm" disabled={!hasUnsavedChanges || save.isPending} onClick={() => setConfirmDiscard(true)}>
              {t("discard")}
            </Button>
            <Button type="button" variant="secondary" size="sm" onClick={() => runSave()} loading={save.isPending} disabled={!hasUnsavedChanges}>
              <Save />
              <span>{t("save")}</span>
            </Button>
            <Button type="button" variant="secondary" size="sm" loading={check.isPending || save.isPending} onClick={() => runCheck({ scope: "EXAM" })}>
              <ClipboardCheck />
              <span>{t("check.all")}</span>
            </Button>
            <Button type="button" size="sm" loading={check.isPending || save.isPending} onClick={continueToReview}>
              <span>{t("check.continue")}</span>
              <ArrowRight />
            </Button>
          </div>
          <LiveStatus message={liveMessage} />
        </div>

        <ExamStructureAlertsBanner
          issues={issues}
          issueText={issueText}
          checkResult={checkResult}
          names={names}
          parts={draft}
          quotas={quotas}
          combinations={structure.combinations}
          onSelect={select}
        />

        {showTopStrip && (
          <div className="flex flex-col gap-2.5 border-b border-border bg-subtle/25 px-4 py-3 sm:px-5">
            {hsaNormalizing && <HsaNormalizedNote />}
            {hasVersionPicker && (
              <StructureVersionPicker
                exam={exam}
                templates={templates}
                dirty={hasUnsavedChanges}
                currentParts={draft}
                onAnnounce={announce}
                onApplied={(parts) => {
                  const loaded = hsaLoadedBaselines(hsa, JSON.stringify(parts), quotaBaseline);
                  setDraft(parts);
                  setBaseline(loaded.parts);
                  setQuotaBaseline(loaded.quotas);
                  setQuotas(JSON.parse(loaded.quotas) as ExamQuota[]);
                  setTopicFailure(null);
                  rawInputs.clearAll();
                  setFormEpoch((epoch) => epoch + 1);
                  setPinnedForms(pinForms(parts));
                  setFocusRequest(null);
                  setCheckResult(null);
                }}
              />
            )}
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
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-[17.5rem_minmax(0,1fr)]">
          <ExamStructureList
            parts={draft}
            combinations={structure.combinations ?? []}
            sectionStates={sectionStates}
            selection={current}
            issues={issues}
            names={names}
            onSelect={select}
            showOverview={!reviewMode}
          />
          <div ref={formRegionRef} className="flex min-w-0 flex-col bg-surface">
            {sectionContext?.option?.kind === "SUBJECT_CHOICE" && (
              <SubjectChoiceNav
                option={sectionContext.option}
                sectionId={sectionContext.section.id}
                names={names}
                onOpenOption={() =>
                  select({ kind: "option", partId: sectionContext.part.id, optionId: sectionContext.option!.id })
                }
                onOpenSection={(sectionId) => select({ kind: "section", sectionId })}
              />
            )}
            {current?.kind === "exam" ? (
              <ExamOverviewPanel
                examName={exam.name}
                summary={t("overviewSummary", {
                  count: roundForDisplay(totals.totalQuestions),
                  minutes: roundForDisplay(totals.totalMinutes),
                  shared: shared ? "yes" : "no",
                  points: format.number(totals.totalPoints, DISPLAY_NUMBER_FORMAT),
                })}
                parts={draft}
                quotas={quotas}
                onQuotas={setQuotas}
                names={names}
                labels={{ grade: lookup.grade, difficulty: lookup.difficulty }}
                scopeError={quotaScopeError}
                topicError={topicError}
                combinations={structure.combinations ?? []}
                structureSummary={effectiveSummary}
                summaryLoad={summaryLoad}
                dirty={dirty}
                onSelect={select}
              />
            ) : sectionContext && rendersReviewPanel(sectionContext.section, reviewMode) ? (
              <PracticeReviewSubjectPanel
                key={sectionContext.section.id}
                context={sectionContext}
                subjectName={lookup.subject(sectionContext.section.subjectCode)}
                axes={axes}
                labels={{ grade: lookup.grade, difficulty: lookup.difficulty }}
                reference={reference}
                onChange={(update) => setDraft((parts) => updateSection(parts, sectionContext.location, update))}
                step1Count={sectionContext.part.questionCount}
                examId={exam.id}
                minutes={roundForDisplay(totals.totalMinutes)}
                maxScore={exam.maxScore}
                headerAction={checkAction}
                topicError={topicError}
              />
            ) : sectionContext && formKind ? (
              <SubjectFormScreen
                kind={formKind}
                key={`${sectionContext.section.id}:${formEpoch}`}
                context={sectionContext}
                subjectName={lookup.subject(sectionContext.section.subjectCode)}
                axes={axes}
                labels={{ grade: lookup.grade, difficulty: lookup.difficulty }}
                reference={reference}
                onChange={(update) => setDraft((parts) => updateSection(parts, sectionContext.location, update))}
                headerAction={checkAction}
                examId={exam.id}
                templateLocked={templateLocked}
                sourceVerified={sourceVerified(sectionContext.section)}
                topicError={topicError}
                examQuotas={quotas}
                onExamQuotas={setQuotas}
                examKind={exam.kind}
                rawInputs={rawInputs}
                template={template}
                templates={templates}
                baselineParts={baselineParts}
              />
            ) : sectionContext ? (
              <>
                {sectionForm?.notice && (
                  <div className="border-b border-border bg-subtle/25 px-4 py-2.5 sm:px-6">
                    <FullFormNotice reason={sectionForm.reason} />
                  </div>
                )}
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
                  headerAction={checkAction}
                  examId={exam.id}
                  templateLocked={templateLocked}
                  sourceVerified={sourceVerified(sectionContext.section)}
                  topicError={topicError}
                  examQuotas={quotas}
                  onExamQuotas={setQuotas}
                  examKind={exam.kind}
                  rowsLocked={rowsLocked}
                  reviewMode={reviewMode}
                />
              </>
            ) : blockContext ? (
              <BlockEditor
                topicError={topicError}
                partTotal={blockContext.part.questionCount}
                rowsLocked={rowsLocked}
                template={template}
                commandCount={dirty ? null : (summaryQuery.data?.rows.find((row) => row.blockId === blockContext.block.id)?.commandCount ?? null)}
                onRateInvalid={setRateInvalid}
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
                examKind={exam.kind}
                templateCode={exam.templateCode}
                scoringDefaults={kind?.scoringDefaults ?? null}
                competencies={kindCompetencies}
                onChange={(block) => setDraft((parts) => updateBlock(parts, block.id, () => block))}
                onMove={(offset) => setDraft((parts) => moveBlock(parts, blockContext.block.id, offset))}
                onRemove={() => {
                  const name = blockContext.block.name;
                  setDraft((parts) => removeBlock(parts, blockContext.block.id));
                  selectNow({ kind: "part", partId: blockContext.part.id });
                  announce(t("blockRemoved", { name }));
                }}
                headerAction={checkAction}
              />
            ) : selectedPart ? (
              <ExamStructurePartPanel
                part={selectedPart}
                timing={exam.timing}
                sharedMinutes={exam.durationMinutes}
                names={names}
                structureCombinations={structure.combinations ?? []}
                axes={axes}
                labels={{ grade: lookup.grade, difficulty: lookup.difficulty }}
                examId={exam.id}
                templateLocked={templateLocked}
                rowsLocked={rowsLocked}
                reviewMode={reviewMode}
                sectionForm={(sectionId) => pinnedForm(pinnedForms, sectionId).form}
                onAddSubject={(subjectCode) => {
                  setDraft((parts) => addPoolSection(parts, selectedPart.id, subjectCode));
                  announce(t("subjectAdded", { subject: lookup.subject(subjectCode) }));
                }}
                showEnglish={
                  selectedPart.options.some((option) => option.kind === "SUBJECT" && option.code === ENGLISH_CODE) ||
                  (selectedPart.sections.length === 0 &&
                    !selectedPart.options.some((option) => option.kind === "BRANCH") &&
                    !template?.parts[selectedPart.order - 1]?.options?.length)
                }
                branchSubjects={branchSubjects}
                onPoints={(points) => setDraft((parts) => setPartPoints(parts, selectedPart.id, points))}
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
                onAddBlock={(optionId, section) => handleAddBlock({ partId: selectedPart.id, optionId, sectionId: section.id }, section)}
                onToggleEnglish={(enabled) => setDraft((parts) => setEnglishOption(parts, selectedPart.id, enabled))}
                onRemoveOption={(optionId) => {
                  const option = selectedPart.options.find((item) => item.id === optionId);
                  setDraft((parts) => removeOption(parts, selectedPart.id, optionId));
                  if (option) announce(t("combinationRemoved", { code: option.kind === "BRANCH" ? (option.name ?? option.code) : option.code }));
                }}
                onSelectBlock={(blockId) => select({ kind: "block", blockId })}
                onSelectSection={(sectionId) => select({ kind: "section", sectionId })}
                sectionStates={sectionStates}
                headerAction={checkAction}
                topicError={topicError}
              />
            ) : null}

            {sectionStrandTable && sectionContext && (
              <div className="border-t border-border px-4 py-5 sm:px-6">
                <StrandReference
                  table={sectionStrandTable}
                  subjectName={lookup.subject(sectionContext.section.subjectCode)}
                  difficultyName={lookup.difficulty}
                />
              </div>
            )}
            {current?.kind !== "exam" && !reviewMode && (
              <ScoringExamples kind={exam.kind} part={(sectionContext ?? blockContext)?.part ?? selectedPart ?? null} />
            )}
          </div>
        </div>
      </div>

      <ConfirmDialog
        open={confirmDiscard}
        onOpenChange={setConfirmDiscard}
        title={t("discardTitle")}
        description={t("discardDescription")}
        confirmLabel={t("discardConfirm")}
        onConfirm={() => {
          discardAll();
          setConfirmDiscard(false);
          announce(t("discarded"));
        }}
      />
      <ExamStructureLeaveDialog
        open={leaveOpen}
        saving={save.isPending}
        error={leaveError}
        itemName={leaveItemName}
        destination={leaveDestination}
        changes={leaveChanges}
        onSaveAndGo={leaveAfterSave}
        onDiscard={leaveAfterDiscard}
        onStay={stayHere}
      />
    </div>
  );
}
