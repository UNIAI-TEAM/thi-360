"use client";

import { Button, Card, ConfirmDialog, EmptyState, LiveStatus, notify, Skeleton, StatusChip } from "@thi360/ui";
import { ArrowRight, ClipboardCheck, Info, LayoutList, Save, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { focusMapidRuleWhenReady } from "../mapid-rules";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import { getErrorStatus } from "@/lib/api/query-client";
import { examQuotaBlockers, fillReviewCounts, rendersReviewPanel, saveBlockers } from "../allocation";
import { HsaExamContext, hsaLoadedBaselines, hsaNeedsNormalizing, isHsaExam, useHsaNormalizedDraft } from "../hsa-enforcement";
import { strandTable } from "../reference-distribution";
import { yearsNeedConfirmation } from "../check";
import { DISPLAY_NUMBER_FORMAT, roundForDisplay } from "../display-number";
import { examTabFromPathname, examTabHref } from "../editor-tabs";
import { addPoolSection } from "../coded-part";
import type { MatrixAxes } from "../matrix";
import { useExam, useExamStructure, useExamStructureSummary, useExamTemplates, useUpdateExamStructure } from "../queries";
import { useRunExamCheck } from "../review-queries";
import { ExamsApiError } from "../review-api";
import type { ExamCheckResult, RunExamCheckRequest } from "../review-types";
import type { ExamDetail, ExamPart, ExamQuota, ExamSection, ExamStructure, ExamTemplate } from "../types";
import { BlockEditor } from "./block-editor";
import { blockCountsLocked } from "./block-form";
import type { TopicQuotaServerError } from "./topic-quota-table";
import { HsaNormalizedNote } from "./enforcement-toggle";
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
import { ExamEditorActions } from "./exam-editor-actions";
import { useMapidRuleText } from "./mapid-rules-table";
import { ExamOverviewPanel } from "./exam-overview-panel";
import { optionLabel, type StructureNames, useStructureIssueText } from "./exam-structure-issues";
import { describeStructureChanges } from "./exam-structure-changes";
import { ExamStructureLeaveDialog, type LeaveDestination } from "./exam-structure-leave-dialog";
import { ExamStructureList } from "./exam-structure-list";
import { ExamStructurePartPanel } from "./exam-structure-part-panel";
import { ExamStructureSubjectPanel } from "./exam-structure-subject-panel";
import { ExamStructureSummaryRegion } from "./exam-structure-summary";
import { checkIssueTarget } from "./exam-structure-check-result";
import { StructureVersionPicker } from "./structure-version-picker";
import { SubjectChoiceNav } from "./subject-choice-nav";
import { ReviewSubjectPanel } from "./review-subject-panel";
import { ScoringExamples, StrandReference } from "./scoring-examples";
import { useUnsavedChangesGuard } from "./exam-structure-unsaved-guard";

const SELECTION_PARAMS = { exam: "exam", part: "part", option: "option", section: "section", block: "block", rule: "rule" } as const;

function readSelectionFromUrl(): StructureSelection | null {
  if (typeof window === "undefined") return null;
  const params = new URLSearchParams(window.location.search);
  const blockId = params.get(SELECTION_PARAMS.block);
  if (params.get(SELECTION_PARAMS.exam)) return { kind: "exam" };
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
  if (selection.kind === "exam") url.searchParams.set(SELECTION_PARAMS.exam, "1");
  else if (selection.kind === "block") url.searchParams.set(SELECTION_PARAMS.block, selection.blockId);
  else if (selection.kind === "section") url.searchParams.set(SELECTION_PARAMS.section, selection.sectionId);
  else {
    url.searchParams.set(SELECTION_PARAMS.part, selection.partId);
    if (selection.kind === "option") url.searchParams.set(SELECTION_PARAMS.option, selection.optionId);
  }
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
  /** Bài ôn (R-22): màn rút gọn, một phần, một môn, một nhóm. */
  const reviewMode = template?.buildMode === "REVIEW";
  // H42, R-135: mẫu `fixedBlockCounts` khóa số câu của bài phạm vi `SUBJECT`; bài `PART` và bài ôn từ cùng mẫu vẫn sửa được.
  const rowsLocked = blockCountsLocked(template, exam.scope, reviewMode);
  const templateLocked = template?.fixedCounts === true || rowsLocked;
  // Bài ôn mới tạo có nhóm duy nhất chưa khai số câu: điền số câu bước 1 để màn, tổng và nội dung lưu cùng một số (V-01b).
  const structure = useMemo(
    () => (reviewMode ? { ...loadedStructure, parts: fillReviewCounts(loadedStructure.parts) } : loadedStructure),
    [loadedStructure, reviewMode],
  );
  const t = useTranslations("exams.structure");
  const examsT = useTranslations("exams");
  const format = useFormatter();
  const router = useRouter();
  const lookup = useReferenceLookup(reference);
  const mapidText = useMapidRuleText(reference);
  const save = useUpdateExamStructure(exam.id);
  const check = useRunExamCheck(exam.id);
  const summaryQuery = useExamStructureSummary(exam.id);
  const [checkResult, setCheckResult] = useState<ExamCheckResult | null>(null);

  // Kỳ thi HSA sinh đề luôn theo ma trận (UNI-1701): bản nháp luôn ở mức cho phép, bản đã lưu giữ nguyên để màn báo "có
  // thay đổi chưa lưu" cho tới khi lưu.
  const hsa = isHsaExam(exam.kind);
  const [draft, setDraft] = useState<ExamPart[]>(structure.parts);
  const [quotas, setQuotas] = useState<ExamQuota[]>(structure.examQuotas ?? []);
  useHsaNormalizedDraft(hsa, draft, setDraft, quotas, setQuotas);
  const [baseline, setBaseline] = useState(() => JSON.stringify(structure.parts));
  const [quotaBaseline, setQuotaBaseline] = useState(() => JSON.stringify(structure.examQuotas ?? []));
  const [syncedKey, setSyncedKey] = useState(syncKey);
  const draftJson = JSON.stringify(draft);
  const dirty = draftJson !== baseline || JSON.stringify(quotas) !== quotaBaseline;
  // Dữ liệu mới từ máy chủ (sau khi lưu, hoặc lane khác làm mới) chỉ thay bản nháp khi không còn gì chưa lưu.
  if (syncKey !== syncedKey) {
    setSyncedKey(syncKey);
    if (!dirty) {
      setDraft(structure.parts);
      setBaseline(JSON.stringify(structure.parts));
      setQuotas(structure.examQuotas ?? []);
      setQuotaBaseline(JSON.stringify(structure.examQuotas ?? []));
    }
  }
  // Lỗi 422 của khối chỉ tiêu chủ đề (H20) chỉ hiện khi bản nháp (cả chỉ tiêu cấp bài) còn đúng như lúc lưu lỗi; sửa gì thì lỗi này
  // không còn đúng.
  const draftSnapshot = `${draftJson}\n${JSON.stringify(quotas)}`;
  const [topicFailure, setTopicFailure] = useState<{ code: TopicQuotaServerError; snapshot: string } | null>(null);
  const topicError = topicFailure && topicFailure.snapshot === draftSnapshot ? topicFailure.code : null;
  // Lỗi 422 `EXAM_QUOTA_SCOPE_INVALID` của chỉ tiêu cấp bài: hiện ở khối chỉ tiêu cấp bài khi danh sách còn như lúc lưu lỗi.
  const [quotaScopeFailure, setQuotaScopeFailure] = useState<string | null>(null);
  const quotaScopeError = quotaScopeFailure !== null && quotaScopeFailure === JSON.stringify(quotas);
  // Tổng số câu của mục đã xác nhận sau khi đổi (R-121): bảng nhập theo tỷ lệ chờ xác nhận khi tổng khác số này.
  const [confirmedCounts, setConfirmedCounts] = useState<Record<string, number>>({});
  const baselineParts = useMemo(() => JSON.parse(baseline) as ExamPart[], [baseline]);
  // Bản đã lưu còn mức Tham khảo mà HSA không cho: ghi chú hiện cho tới khi lưu.
  const hsaNormalizing = useMemo(() => hsaNeedsNormalizing(hsa, baselineParts, quotaBaseline), [hsa, baselineParts, quotaBaseline]);

  const [selection, setSelection] = useState<StructureSelection | null>(() => {
    const fromUrl = readSelectionFromUrl();
    return fromUrl && selectionExists(structure.parts, fromUrl) ? fromUrl : defaultSelection(structure.parts);
  });
  // `?rule=` (liên kết "Đi tới quy tắc" của kiểm tra và xem thử): cuộn tới dòng quy tắc MAPID một lần khi mở trang.
  useEffect(() => {
    const ruleId = new URLSearchParams(window.location.search).get(SELECTION_PARAMS.rule);
    if (ruleId) void focusMapidRuleWhenReady(ruleId);
  }, []);
  const requested = selection && selectionExists(draft, selection) ? selection : defaultSelection(draft);
  // Bài ôn (UNI-2752): chỗ sửa của khối duy nhất là màn bài ôn. `?block=`, "Đến chỗ sửa" và lỗi kiểm tra trỏ tới khối đó đều mở
  // môn, không mở màn nhóm đầy đủ (Tên nhóm, Cách lấy, Ma trận riêng...) mà bài ôn rút gọn để ẩn.
  const reviewBlock = reviewMode && requested?.kind === "block" ? findBlock(draft, requested.blockId) : null;
  const current: StructureSelection | null =
    reviewBlock && rendersReviewPanel(reviewBlock.section, reviewMode) ? { kind: "section", sectionId: reviewBlock.section.id } : requested;
  const [liveMessage, setLiveMessage] = useState("");
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const guard = useUnsavedChangesGuard(dirty);
  const [pendingSelection, setPendingSelection] = useState<StructureSelection | null>(null);
  const [leaveError, setLeaveError] = useState<string | null>(null);
  // Dòng nhập theo tỷ lệ không ra chùm nguyên (R-142): lỗi báo tại dòng, bản nháp không đổi, nhưng không cho lưu cho đến khi sửa.
  const [rateInvalid, setRateInvalid] = useState(false);

  /** Mở mục không qua hộp thoại: sau chính thao tác của người dùng (thêm, xóa nhóm) hoặc sau khi đã lưu. */
  const selectNow = useCallback((next: StructureSelection) => {
    setSelection(next);
    writeSelectionToUrl(next);
  }, []);
  /** Chuyển sang môn hoặc phần khác khi còn thay đổi chưa lưu thì hỏi bằng hộp thoại ba nút (R-05). */
  const select = (next: StructureSelection) => {
    if (dirty && current && selectionScope(draft, next) !== selectionScope(draft, current)) {
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

  const discardAll = () => {
    setDraft(JSON.parse(baseline) as ExamPart[]);
    setQuotas(JSON.parse(quotaBaseline) as ExamQuota[]);
    setConfirmedCounts({});
    setTopicFailure(null);
  };

  const runSave = (options: { onSaved?: () => void; onFailed?: (message: string) => void } = {}) => {
    if (rateInvalid) {
      const message = t("rateBlocked");
      if (!options.onFailed) notify.error(message);
      announce(message);
      options.onFailed?.(message);
      return;
    }
    // Bảng phân bổ còn lỗi người dùng phải sửa (tổng tỷ lệ khác 100%, quá 8 chỉ tiêu): mở đúng chỗ, không gửi lên máy chủ.
    const blocker = saveBlockers(draft, axes, (sectionId) => confirmedCounts[sectionId] ?? savedCountOf(sectionId), reviewMode)[0];
    const quotaBlocked = blocker ? false : examQuotaBlockers(quotas).length > 0;
    if (blocker || quotaBlocked) {
      const found = blocker?.kind === "TOTAL_CHANGE" ? findSection(draft, blocker.sectionId) : null;
      // Quy tắc MAPID trỏ tới nhóm đã xóa: nêu câu của quy tắc và tên nhóm đã xóa, không đẩy người dùng sang bảng phân bổ (R1-F4).
      const orphanSection = blocker?.kind === "MAPID_GROUP" ? findSection(draft, blocker.sectionId)?.section : undefined;
      const orphan = orphanSection?.mapidRules?.find((rule) => rule.id === blocker?.ruleId);
      const message = found
        ? t("totalChangeBlocked", { subject: lookup.subject(found.section.subjectCode) })
        : orphanSection && orphan
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
  };
  const proceedLeave = () => {
    const next = pendingSelection;
    setPendingSelection(null);
    setLeaveError(null);
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
  const selectedPart =
    current?.kind === "part" || current?.kind === "option" ? draft.find((part) => part.id === current.partId) : null;
  const sectionStates = checkResult?.sectionStates ?? structure.sectionStates ?? null;
  /** Số câu của mục ở bản đã lưu (mốc để phát hiện tổng đổi, R-121). */
  const savedCountOf = (sectionId: string): number | null => findSection(baselineParts, sectionId)?.section.questionCount ?? null;
  /** "Đủ nguồn theo ma trận" chỉ khi môn đã kiểm tra đạt và chưa sửa gì kể từ bản đã lưu (R-122 dòng 8). */
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

  return (
    <div className="@container flex flex-col gap-4">
      {hsaNormalizing && <HsaNormalizedNote />}
      <ExamEditorActions>
        {dirty && <StatusChip tone="warning">{t("unsavedChip")}</StatusChip>}
        <Button type="button" variant="secondary" disabled={!dirty || save.isPending} onClick={() => setConfirmDiscard(true)}>
          {t("discard")}
        </Button>
        <Button type="button" variant="secondary" onClick={() => runSave()} loading={save.isPending} disabled={!dirty}>
          <Save />
          <span>{t("save")}</span>
        </Button>
        <Button type="button" variant="secondary" loading={check.isPending || save.isPending} onClick={() => runCheck({ scope: "EXAM" })}>
          <ClipboardCheck />
          <span>{t("check.all")}</span>
        </Button>
        <Button type="button" loading={check.isPending || save.isPending} onClick={continueToReview}>
          <span>{t("check.continue")}</span>
          <ArrowRight />
        </Button>
      </ExamEditorActions>
      <Card padding="md" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <dl aria-label={t("summary.label")} className="flex flex-wrap gap-x-6 gap-y-2 text-body">
            <SummaryItem label={t("summary.kind")} value={kind?.name ?? exam.kind} />
            <SummaryItem label={t("summary.scope")} value={examsT(`scope.${exam.scope}`)} />
            <SummaryItem
              label={t("summary.questions")}
              value={
                totals.printedQuestions !== totals.totalQuestions
                  ? t("summary.questionsPrinted", { count: roundForDisplay(totals.totalQuestions), printed: roundForDisplay(totals.printedQuestions) })
                  : format.number(totals.totalQuestions, DISPLAY_NUMBER_FORMAT)
              }
            />
            <SummaryItem
              label={t("summary.duration")}
              value={
                shared
                  ? t("summary.sharedMinutes", { count: roundForDisplay(totals.totalMinutes) })
                  : t("summary.minutes", { count: roundForDisplay(totals.totalMinutes) })
              }
            />
            {reviewMode ? (
              // Bài ôn: tổng điểm theo ngân hàng câu nên không so với thang; chỉ nêu thang khi có.
              exam.maxScore !== null && (
                <SummaryItem label={t("summary.scale")} value={t("summary.scaleValue", { scale: exam.maxScore })} />
              )
            ) : (
              <SummaryItem
                label={t("summary.points")}
                value={
                  exam.maxScore !== null
                    ? t("summary.pointsOfScale", {
                        points: format.number(totals.totalPoints, DISPLAY_NUMBER_FORMAT),
                        scale: format.number(exam.maxScore, DISPLAY_NUMBER_FORMAT),
                      })
                    : format.number(totals.totalPoints, DISPLAY_NUMBER_FORMAT)
                }
                warn={exam.maxScore !== null && Math.abs(totals.totalPoints - exam.maxScore) > 1e-6}
              />
            )}
          </dl>
          <Button asChild variant="secondary">
            <Link href={examTabHref(exam.id, "general")}>{t("summary.editOverview")}</Link>
          </Button>
        </div>
        <p className="text-caption text-text-muted">
          {exam.servingVersion !== null ? t("workingNote", { version: exam.servingVersion }) : t("draftNote")}
        </p>
        <StructureVersionPicker
          exam={exam}
          templates={templates}
          dirty={dirty}
          currentParts={draft}
          onAnnounce={announce}
          onApplied={(parts) => {
            // Bài HSA: bản đã lưu tạm của lần nạp này đã chuyển mức, để bản nháp sạch và lần tải lại kế tiếp thay được (R2-2).
            const loaded = hsaLoadedBaselines(hsa, JSON.stringify(parts), quotaBaseline);
            setDraft(parts);
            setBaseline(loaded.parts);
            setQuotaBaseline(loaded.quotas);
            // Chỉ tiêu cấp bài đang sửa thuộc mẫu cũ: bỏ để lần tải lại cấu trúc kế tiếp đưa chỉ tiêu của mẫu mới vào, không
            // để lần lưu sau ghi đè `examQuotas` của mẫu mới (R1-10).
            setQuotas(JSON.parse(loaded.quotas) as ExamQuota[]);
            setConfirmedCounts({});
            setTopicFailure(null);
            // Phiên bản mới làm kết quả kiểm tra cũ hết hiệu lực: huy hiệu trạng thái tính lại từ đầu.
            setCheckResult(null);
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
        <LiveStatus message={liveMessage} />
      </Card>

      <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-[18.75rem_minmax(0,1fr)]">
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
        <div className="flex min-w-0 flex-col gap-4">
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
            />
          ) : sectionContext && rendersReviewPanel(sectionContext.section, reviewMode) ? (
            <ReviewSubjectPanel
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
          ) : sectionContext ? (
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
              confirmedCount={confirmedCounts[sectionContext.section.id] ?? savedCountOf(sectionContext.section.id)}
              onConfirmCount={(count) => setConfirmedCounts((counts) => ({ ...counts, [sectionContext.section.id]: count }))}
              examQuotas={quotas}
              onExamQuotas={setQuotas}
              examKind={exam.kind}
            />
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
          {sectionContext && !rendersReviewPanel(sectionContext.section, reviewMode) && (
            <StrandReference
              table={strandTable(sectionContext.section, quotas, exam.scope)}
              subjectName={lookup.subject(sectionContext.section.subjectCode)}
              difficultyName={lookup.difficulty}
            />
          )}
          {current?.kind !== "exam" && !reviewMode && (
            <ScoringExamples kind={exam.kind} part={(sectionContext ?? blockContext)?.part ?? selectedPart ?? null} />
          )}
          <ExamStructureSummaryRegion
            summary={reviewMode && JSON.stringify(loadedStructure.parts) !== JSON.stringify(structure.parts) ? null : (summaryQuery.data ?? null)}
            load={summaryLoad}
            dirty={dirty}
            local={{ allocated: totals.allocated, total: totals.totalQuestions, missing: totals.missing }}
            issues={issues}
            issueText={issueText}
            checkResult={checkResult}
            names={names}
            parts={draft}
            onSelect={select}
          />
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

function SummaryItem({ label, value, warn = false }: { label: string; value: string; warn?: boolean }) {
  return (
    <div className="flex items-baseline gap-1.5">
      <dt className="text-text-muted">{label}</dt>
      <dd className={warn ? "m-0 font-semibold text-warning" : "m-0 font-semibold text-text"}>{value}</dd>
    </div>
  );
}
