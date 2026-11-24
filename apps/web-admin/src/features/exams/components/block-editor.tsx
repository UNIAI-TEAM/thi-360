"use client";

import { Badge, Button, Checkbox, cn, ConfirmDialog, FormField, IconButton, Input, Select, StatusChip } from "@thi360/ui";
import { ArrowDown, ArrowUp, ChevronRight, Lock, Trash2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useEffect, useEffectEvent, useId, useState, type ReactNode } from "react";
import {
  QUESTION_TYPES,
  type CompetencyOption,
  type ContentReference,
  type QuestionType,
  type ScoringDefaults,
} from "@/features/content/types";
import { applyTopicTotal, countFromPercent, ratePercent } from "../allocation";
import type { MatrixAxes } from "../matrix";
import { roundForDisplay } from "../display-number";
import { blockPointsEach, emptyMatrix, isFlexibleSet, isSetBlock, validateBlock } from "../matrix";
import { isHsaExam, useHsaExam } from "../hsa-enforcement";
import { mapidViewMode } from "../mapid-rules";
import { useAvailableQuestions } from "../queries";
import type { ExamSection, ExamTemplate, KnowledgeScope, QuestionBlock } from "../types";
import { applyBlockPatch, droppedSlotIndexes, rateSetMismatch, resizeSets, switchSetMode, withEveryChildDefault } from "./block-form";
import { blockFieldAnchor, focusBlockField, type BlockField } from "./block-form-fields";
import { blockFieldFromUrl, clearBlockFieldFromUrl, consumeBlockFieldRequest, useBlockFieldRequest } from "./block-field-focus";
import { BlockConditions } from "./block-conditions";
import { BlockMatrixEditor, type MatrixLabels } from "./block-matrix-editor";
import { ExamName, labelAfterName, labelBeforeName, richNodes } from "./exam-review-shared";
import { BlockNumberInput, parseNumberText } from "./block-number-input";
import { ChoiceCardGroup } from "./choice-card-group";
import { AudioChip, blockAvailableInput, blockShare, sectionAudio } from "./block-row-metrics";
import { BlockScoringEditor, expectedScoringFor } from "./block-scoring-editor";
import { BlockSetConditions } from "./block-set-conditions";
import { LOCKED_FIELD } from "./locked-field";
import { BlockSwitches } from "./block-switches";
import { KnowledgeScopeField } from "./knowledge-scope-field";
import { BlockMapidRules } from "./block-mapid-rules";
import { TopicQuotaElsewhereAlert } from "./topic-quota-elsewhere-alert";
import { TopicQuotaTable, type TopicQuotaServerError } from "./topic-quota-table";

/** Giá trị của ô chọn dạng trả lời cho nhóm lấy theo chỉ tiêu dạng trả lời của môn (`questionType = null`). */
const OPEN_TYPE = "__OPEN__";
/** Ô chọn "không lọc" của dạng câu con. */
const ANY = "__ANY__";
/** Loại câu không phải nhóm chùm: dạng của câu con (D19) và dạng của nhóm câu đơn. */
const PLAIN_TYPES = QUESTION_TYPES.filter((type) => type !== "QUESTION_SET" && type !== "CLOZE");
/** Loại câu của nhóm chùm. */
const SET_TYPES: readonly QuestionType[] = ["QUESTION_SET", "CLOZE"];
/** SP2E và SPT gọi phạm vi theo chủ đề là "Theo mạch kiến thức". */
const STRAND_KINDS: readonly string[] = ["SP2E", "SPT"];
/** Mẫu cấu trúc bật sẵn ô "Mọi câu con thuộc chủ đề" (R-120): bài ôn V-ACT theo chủ đề. */
const EVERY_CHILD_DEFAULT_TEMPLATES: readonly string[] = ["VACT_REVIEW"];

interface BlockEditorProps {
  block: QuestionBlock;
  /** Môn chứa nhóm: nhóm không đặt phạm vi MAPID thì dùng phạm vi của môn. */
  section: ExamSection;
  subjectCode: string;
  /** "Phần 1 · Toán · nhóm 2 / 2". */
  locationText: string;
  position: number;
  siblingCount: number;
  axes: MatrixAxes;
  labels: MatrixLabels;
  reference: ContentReference | undefined;
  /** Mã kỳ thi của bài; chỉ để chọn tên "Theo chủ đề" hay "Theo mạch kiến thức". */
  examKind?: string;
  /** Mã mẫu cấu trúc của bài; bài từ `VACT_REVIEW` bật sẵn "Mọi câu con thuộc chủ đề" khi nhóm chùm có MAPID. */
  templateCode?: string | null;
  /** Quy tắc chấm mặc định của kỳ thi, nạp khi nhóm đổi sang Đúng/Sai hoặc Nhiều đáp án. */
  scoringDefaults: ScoringDefaults | null;
  /** Năng lực của kỳ thi (V-ACT); rỗng: không có lọc và chiều năng lực. */
  competencies: readonly CompetencyOption[];
  onChange: (block: QuestionBlock) => void;
  onMove: (offset: -1 | 1) => void;
  onRemove: () => void;
  /** Về màn môn chứa nhóm. */
  onOpenSection: () => void;
  /** Nút nằm ở đầu vùng chi tiết (bản vẽ `BaiThiBuoc2`: "Kiểm tra môn"). */
  headerAction?: ReactNode;
  /** Lỗi máy chủ ở khối chỉ tiêu chủ đề của nhóm (H20). */
  topicError?: TopicQuotaServerError;
  /** Tổng câu của phần chứa nhóm: mẫu số của "Tỷ lệ trong phần" (R-142); `null`: chưa biết. */
  partTotal?: number | null;
  /** Mẫu khóa dòng (H42, R-135): số lượng, cách lấy và dạng chỉ đọc; nguồn câu, phạm vi và chỉ tiêu vẫn sửa được. */
  rowsLocked?: boolean;
  /** Mẫu của bài; khi mẫu khóa nhóm đã lược (H43), nhóm là bản sao nhóm đã lược của mẫu thì không bật lại được. */
  template?: ExamTemplate | null;
  /** Số lệnh hỏi của nhóm do máy chủ tính (`structure/summary`); `null`: chưa có hoặc bản nháp đã đổi so với bản lưu. */
  commandCount?: number | null;
  /** Dòng nhập theo tỷ lệ đang sai (chùm không ra số nguyên): màn Cấu trúc không cho lưu cho đến khi sửa. */
  onRateInvalid?: (invalid: boolean) => void;
}

/**
 * Một nhóm lấy câu của môn (2B 3.3, 15.3; bản vẽ `NhomChum2B`, `NhomCauDon2B`): chọn cách lấy (câu đơn hoặc câu chùm),
 * loại câu hỏi (không còn dạng bài, K11), số lượng, phạm vi kiến thức "Toàn môn" hoặc "Theo chủ đề", rồi các điều kiện
 * thêm bằng menu "Thêm điều kiện" (nhãn, hình dạng câu, định dạng đáp án, điểm mỗi câu). Nhóm chùm có thêm điều kiện
 * riêng từng chùm và điều kiện theo vị trí câu con. Mỗi trường có mỏ neo `data-field` (xem `block-form-fields.ts`).
 * Ma trận lớp, độ khó của riêng nhóm là mục mở thêm, mặc định đóng; câu của nhóm vẫn tính vào chỉ tiêu của môn.
 */
export function BlockEditor({
  block,
  section,
  subjectCode,
  locationText,
  position,
  siblingCount,
  axes,
  labels,
  reference,
  examKind,
  templateCode,
  scoringDefaults,
  competencies,
  onChange,
  onMove,
  onRemove,
  onOpenSection,
  headerAction,
  topicError = null,
  partTotal = null,
  rowsLocked = false,
  template = null,
  commandCount = null,
  onRateInvalid,
}: BlockEditorProps) {
  const t = useTranslations("exams.structure.block");
  const tForm = useTranslations("exams.structure.blockForm");
  const tContent = useTranslations("content");
  const titleId = useId();
  const nameId = useId();
  const [confirmRemove, setConfirmRemove] = useState(false);
  const [mapidRequest, setMapidRequest] = useState(0);
  const [droppedSlots, setDroppedSlots] = useState<number[]>([]);
  const lockId = useId();
  const [entryMode, setEntryMode] = useState<"COUNT" | "RATE">("COUNT");
  // Nhập theo tỷ lệ: số câu do tỷ lệ quyết định nên các ô số câu chỉ đọc; mẫu khóa dòng thì mọi ô số lượng chỉ đọc và có lý do.
  const countReadOnly = rowsLocked || entryMode === "RATE";
  const lockProps = (controlProps: { "aria-describedby"?: string }) =>
    countReadOnly
      ? {
          readOnly: true,
          "aria-readonly": true as const,
          className: LOCKED_FIELD,
          ...(rowsLocked ? { "aria-describedby": [controlProps["aria-describedby"], lockId].filter(Boolean).join(" ") } : {}),
        }
      : {};
  const anchor = (field: BlockField) => blockFieldAnchor(block.id, field);
  // Môn HSA dùng ma trận MAPID: phạm vi và chỉ tiêu chủ đề của nhóm không còn dùng, thay bằng quy tắc của môn (cùng điều kiện với màn môn).
  const hsaContext = useHsaExam();
  const mapidMode = hsaContext || isHsaExam(examKind) ? mapidViewMode(section) : null;

  // Bấm lỗi: tiêu điểm vào đúng ô của lỗi, từ vùng tổng hợp (yêu cầu trong bộ nhớ) hoặc từ tab Kiểm tra (`?block=&field=`).
  const fieldRequest = useBlockFieldRequest(block.id);
  useEffect(() => {
    const field = fieldRequest?.field ?? blockFieldFromUrl(window.location.search, block.id);
    if (!field) return;
    // Đợi một khung hình để ô của nhóm vừa mở vẽ xong rồi mới đặt tiêu điểm.
    const frame = requestAnimationFrame(() => {
      focusBlockField(block.id, field);
      if (fieldRequest) consumeBlockFieldRequest();
      clearBlockFieldFromUrl();
    });
    return () => cancelAnimationFrame(frame);
  }, [block.id, fieldRequest]);

  const isSet = isSetBlock(block);
  const flexible = isFlexibleSet(block);
  const everyChildDefault = templateCode != null && EVERY_CHILD_DEFAULT_TEMPLATES.includes(templateCode);
  const remainder = block.questionCount === null;
  // Môn chỉ có một nhóm lấy phần còn lại (R1-5): nhóm khác đã giữ thì ô này khóa, trừ khi chính nhóm này đang giữ.
  const remainderTaken = !remainder && section.blocks.some((other) => other.id !== block.id && other.questionCount === null);
  const questionCount = block.questionCount ?? 0;
  const matrix = block.matrix ?? emptyMatrix();
  const editableMatrix = { ...block, matrix, questionCount };

  // Nguồn đếm theo phạm vi có hiệu lực, dạng trả lời và điều kiện audio của môn (H38, R-130): cùng bộ lọc với bảng nhóm của môn.
  const availableInput = blockAvailableInput(block, section, subjectCode);
  const availableQuery = useAvailableQuestions(availableInput);
  const applyRateCount = (count: number) => {
    if (isSet && !flexible) setSets(count / (block.setSize ?? 1), block.setSize ?? 1);
    else update({ questionCount: count });
  };
  const availableState = availableQuery.isError ? "error" : availableQuery.data ? "ready" : "loading";

  const issues = validateBlock(block, axes);
  const countError = issues.some((issue) => issue.code === "COUNT_NOT_POSITIVE") ? t("errors.countPositive") : undefined;
  const setIssue = issues.find((issue) => issue.code === "SET_SIZE_MISMATCH");

  // Tổng của nhóm đổi thì số câu của chỉ tiêu chủ đề nhập theo tỷ lệ tính lại ngay theo tổng mới (R1-03): bảng nằm cạnh ô số
  // câu nên không cần hỏi như ở môn (R-122 dòng 6), và `count` gửi đi luôn khớp số bảng đang hiện (H30).
  // Giá trị bật sẵn của bài ôn là dữ liệu, không chỉ hiển thị: mọi thay đổi của nhóm đi qua đây.
  const update = (patch: Partial<QuestionBlock>) => {
    const patched = applyBlockPatch(block, patch);
    const percentRows = patched.topicQuotas?.some((quota) => quota.percent !== undefined && quota.percent !== null) ?? false;
    const next =
      patched.questionCount !== null && patched.questionCount !== block.questionCount && percentRows
        ? { ...patched, topicQuotas: applyTopicTotal(patched.topicQuotas ?? [], patched.questionCount) }
        : patched;
    onChange(everyChildDefault ? withEveryChildDefault(next) : next);
  };
  // Nhóm đã lưu từ trước chưa có cờ: ghi `true` một lần khi mở nhóm để lưu và đếm nguồn dùng giá trị đang hiện.
  const normaliseOnOpen = useEffectEvent(() => {
    const next = everyChildDefault ? withEveryChildDefault(block) : block;
    if (next !== block) onChange(next);
  });
  useEffect(() => {
    normaliseOnOpen();
  }, [block.id]);
  const setType = (questionType: QuestionType | null) => {
    // Đổi dạng thì quy tắc chấm mong đợi theo dạng mới (mặc định của kỳ thi).
    const expectedScoring = expectedScoringFor(questionType, scoringDefaults, blockPointsEach(block));
    if (questionType === "QUESTION_SET" || questionType === "CLOZE") {
      // Chùm cỡ linh hoạt chỉ có ở Bộ câu hỏi: đổi sang Điền khuyết thì thành một chùm cỡ bằng tổng câu con.
      if (flexible && questionType === "QUESTION_SET") {
        update({ questionType, childAnswerType: null, expectedScoring });
        setDroppedSlots([]);
        return;
      }
      const setSize = Math.max(isSet ? (block.setSize ?? questionCount) : questionCount, 1);
      const setCount = isSet ? Math.max(block.setCount ?? 1, 1) : 1;
      update({ questionType, setCount, setSize, questionCount: setCount * setSize, childAnswerType: null, expectedScoring });
    } else {
      update({
        questionType,
        setCount: null,
        setSize: null,
        childAnswerType: null,
        questionCount: remainder ? null : Math.max(questionCount, 1),
        expectedScoring,
      });
    }
    setDroppedSlots([]);
  };
  const setSets = (setCount: number, setSize: number) => {
    const { patch, dropped } = resizeSets(block, setCount, setSize);
    update(patch);
    setDroppedSlots(dropped);
  };
  const setSetMode = (mode: "total" | "sets") => {
    if ((mode === "total") === flexible) return;
    update(switchSetMode(block, mode));
    // Sang "chỉ nhập tổng" bỏ điều kiện riêng từng chùm (máy chủ từ chối `slots` không có số chùm): báo như khi đổi số chùm.
    setDroppedSlots(mode === "total" ? droppedSlotIndexes(block.slots, 0) : []);
  };
  // Cờ nằm trong `knowledgeScope` của nhóm: hết MAPID riêng (bỏ chip cuối, "Toàn môn", áp dụng 0 mục) thì `KnowledgeScopeField` bỏ cờ và nhóm kế thừa môn (`null`); `reconcileBlock` bỏ cờ khi thành câu đơn.
  const setEveryChild = (checked: boolean) => {
    const scope: KnowledgeScope = { ...(block.knowledgeScope ?? { nodeIds: [] }) };
    delete scope.everyChildInScope;
    update({ knowledgeScope: checked || everyChildDefault ? { ...scope, everyChildInScope: checked } : scope });
  };

  // Năng lực chọn được: năng lực của môn của nhóm (V-ACT gắn năng lực theo môn); môn không có năng lực riêng thì mọi năng lực.
  const subjectCompetencies = competencies.filter((competency) => competency.subjectCode === subjectCode);
  const competencyChoices = subjectCompetencies.length ? subjectCompetencies : competencies;
  const selectedCompetencies = block.knowledgeScope?.competencyCodes ?? [];
  const competencyName = (code: string) => competencies.find((item) => item.code === code)?.name ?? code;
  const setCompetencies = (codes: string[]) => {
    // Lọc năng lực sống trong `knowledgeScope.competencyCodes` nhưng tách khỏi kế thừa MAPID (R1-4): chọn năng lực
    // trên nhóm chưa chọn nút nào vẫn kế thừa phạm vi của môn (`effectiveScope`); bỏ hết mã và chưa có nút riêng thì
    // quy về `null` cho gọn.
    const knowledgeScope =
      codes.length === 0 && (block.knowledgeScope?.nodeIds.length ?? 0) === 0
        ? null
        : { nodeIds: [], ...block.knowledgeScope, competencyCodes: codes };
    // Chiều năng lực của ma trận đi theo năng lực đã chọn (giữ giá trị của năng lực còn lại).
    const byCompetency = matrix.byCompetency?.length
      ? (codes.length ? codes : competencyChoices.map((item) => item.code)).map((code) => ({
          code,
          value: matrix.byCompetency?.find((item) => item.code === code)?.value ?? 0,
        }))
      : matrix.byCompetency;
    update({ knowledgeScope, matrix: block.matrix ? { ...matrix, byCompetency } : block.matrix });
  };
  const matrixCompetencies = (selectedCompetencies.length ? selectedCompetencies : competencyChoices.map((item) => item.code)).map(
    (code) => ({ code, name: competencyName(code) }),
  );

  // Dòng tổng của nhóm (bản vẽ `NhomChumLinhHoat2B`): số câu, số ý (Đúng/Sai) và điểm tối đa = số câu × điểm mỗi câu.
  const maxPoints = roundForDisplay(questionCount * blockPointsEach(block));
  const statements = block.itemShape?.statements ?? 0;
  const summaryText =
    isSet && questionCount > 0
      ? flexible
        ? tForm("summary.flexible", { count: questionCount, points: maxPoints })
        : tForm("summary.sets", { sets: block.setCount ?? 0, size: block.setSize ?? 0, count: questionCount, points: maxPoints })
      : block.questionType === "TRUE_FALSE" && questionCount > 0 && statements > 0
        ? tForm("summary.trueFalse", { count: questionCount, statements: questionCount * statements, points: maxPoints })
        : null;

  const typeLabel = (type: QuestionType) => tContent(`questionType.${type}`);
  const range = block.positionRange;
  const blockNameNode = block.name ? <ExamName>{block.name}</ExamName> : t("untitled");

  return (
    <div className="flex flex-col gap-5 p-4 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-border pb-4">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h2 id={titleId} className="flex flex-wrap items-center gap-2 text-h3 font-bold wrap-break-word text-text">
            <span aria-hidden className="h-5 w-1 shrink-0 rounded-full bg-primary-600" />
            {richNodes(t, "title", { name: <span id={nameId}>{blockNameNode}</span> })}
            {range && <Badge className="font-medium">{tForm("positionRange", { from: range.from, to: range.to, same: range.from === range.to ? "yes" : "no" })}</Badge>}
            {block.questionType === "ESSAY" && <Badge className="font-medium">{t("manualGrading")}</Badge>}
            {block.omitted === true && <Badge className="font-medium">{tForm("omittedBadge")}</Badge>}
          </h2>
          <p className="pl-3 text-caption text-text-muted">{locationText}</p>
          {block.templateGroupCode && <p className="pl-3 text-caption text-text-muted">{t("fromTemplate")}</p>}
          <Button type="button" variant="link" size="sm" className="w-fit pl-3 pr-0" onClick={onOpenSection}>
            {t("backToSection")}
          </Button>
        </div>
        <div className="flex flex-wrap items-center justify-end gap-2">
          {headerAction}
          <div className="flex items-center gap-0.5">
            <BlockIconAction
              icon={<ArrowUp />}
              labelKey="moveUp"
              nameNode={blockNameNode}
              nameId={nameId}
              disabled={position <= 1}
              onClick={() => onMove(-1)}
            />
            <BlockIconAction
              icon={<ArrowDown />}
              labelKey="moveDown"
              nameNode={blockNameNode}
              nameId={nameId}
              disabled={position >= siblingCount}
              onClick={() => onMove(1)}
            />
            <BlockIconAction
              icon={<Trash2 />}
              labelKey="remove"
              nameNode={blockNameNode}
              nameId={nameId}
              disabled={rowsLocked}
              tone="danger"
              onClick={() => setConfirmRemove(true)}
            />
          </div>
        </div>
      </div>

      <div className="grid grid-cols-[repeat(auto-fill,minmax(min(14rem,100%),1fr))] gap-4" aria-labelledby={titleId} role="group">
        <FormField label={t("name")} required error={block.name.trim() ? undefined : t("errors.nameRequired")}>
          {(controlProps) => (
            <Input
              {...controlProps}
              data-field={anchor("name")}
              value={block.name}
              autoComplete="off"
              maxLength={200}
              onChange={(event) => update({ name: event.target.value })}
            />
          )}
        </FormField>
        <ChoiceCardGroup
          variant="pill"
          legend={tForm("mode.legend")}
          value={isSet ? "set" : "single"}
          disabled={block.answerTypeLocked || rowsLocked}
          anchor={anchor("mode")}
          options={[
            { value: "single", label: tForm("mode.single") },
            { value: "set", label: tForm("mode.set") },
          ]}
          onChange={(mode) => {
            if (mode === "set" && !isSet) setType("QUESTION_SET");
            if (mode === "single" && isSet) setType("SINGLE_CHOICE");
          }}
        />
        {block.answerTypeLocked || rowsLocked ? (
          <FormField label={t("questionType")} hint={t("typeLocked")}>
            {(controlProps) => (
              <div className="relative">
                <Input
                  {...controlProps}
                  data-field={anchor("questionType")}
                  readOnly
                  value={block.questionType ? typeLabel(block.questionType) : t("typeOpen")}
                  className="pr-9"
                />
                <Lock aria-hidden className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-text-muted" />
              </div>
            )}
          </FormField>
        ) : (
          <FormField label={t("questionType")} required hint={block.questionType === null ? t("typeOpenHint") : undefined}>
            {(controlProps) => (
              <Select
                {...controlProps}
                data-field={anchor("questionType")}
                value={block.questionType ?? OPEN_TYPE}
                onValueChange={(value) => setType(value === OPEN_TYPE ? null : (value as QuestionType))}
                options={[
                  ...(isSet ? [] : [{ value: OPEN_TYPE, label: t("typeOpen") }]),
                  ...(isSet ? SET_TYPES : PLAIN_TYPES).map((type) => ({ value: type, label: typeLabel(type) })),
                ]}
              />
            )}
          </FormField>
        )}
        {isSet ? (
          <>
            {block.questionType === "QUESTION_SET" && (
              <ChoiceCardGroup
          variant="pill"
                legend={tForm("setMode.legend")}
                disabled={rowsLocked}
                value={flexible ? "total" : "sets"}
                anchor={anchor("setMode")}
                options={[
                  { value: "total", label: tForm("setMode.total"), hint: tForm("setMode.totalHint") },
                  { value: "sets", label: tForm("setMode.sets"), hint: tForm("setMode.setsHint") },
                ]}
                onChange={setSetMode}
              />
            )}
            {flexible ? (
              <FormField label={tForm("setMode.totalField")} required hint={tForm("setMode.totalFieldHint")}>
                {(controlProps) => (
                  <BlockNumberInput
                    {...controlProps}
                    {...lockProps(controlProps)}
                    data-field={anchor("questionCount")}
                    value={questionCount}
                    onValueChange={(value) => update({ questionCount: value })}
                  />
                )}
              </FormField>
            ) : (
              <>
                <FormField label={t("setCount")} required>
                  {(controlProps) => (
                    <BlockNumberInput
                      {...controlProps}
                      {...lockProps(controlProps)}
                      data-field={anchor("setCount")}
                      value={block.setCount ?? 0}
                      onValueChange={(value) => setSets(value, block.setSize ?? 0)}
                    />
                  )}
                </FormField>
                <FormField label={t("setSize")} required hint={t("setHint", { count: questionCount })}>
                  {(controlProps) => (
                    <BlockNumberInput
                      {...controlProps}
                      {...lockProps(controlProps)}
                      data-field={anchor("setSize")}
                      value={block.setSize ?? 0}
                      onValueChange={(value) => setSets(block.setCount ?? 0, value)}
                    />
                  )}
                </FormField>
              </>
            )}
            {block.questionType === "QUESTION_SET" && (
              <FormField label={t("childAnswerType")} hint={t("childAnswerTypeHint")}>
                {(controlProps) => (
                  <Select
                    {...controlProps}
                    data-field={anchor("childAnswerType")}
                    value={block.childAnswerType ?? ANY}
                    onValueChange={(value) => update({ childAnswerType: value === ANY ? null : (value as QuestionType) })}
                    options={[
                      { value: ANY, label: t("childAnswerTypeAny") },
                      ...PLAIN_TYPES.map((type) => ({ value: type, label: typeLabel(type) })),
                    ]}
                  />
                )}
              </FormField>
            )}
          </>
        ) : (
          <FormField
            label={t("questionCount")}
            required={!remainder}
            hint={remainder ? t("remainderHint") : remainderTaken ? t("remainderTaken") : t("questionCountHint")}
            error={countError}
          >
            {(controlProps) => (
              <div className="flex flex-col gap-2">
                <BlockNumberInput
                  {...controlProps}
                  {...lockProps(controlProps)}
                  data-field={anchor("questionCount")}
                  value={questionCount}
                  disabled={remainder}
                  onValueChange={(value) => update({ questionCount: value })}
                />
                <div className="flex items-center gap-2">
                  <Checkbox
                    id={`${titleId}-remainder`}
                    checked={remainder}
                    disabled={remainderTaken || rowsLocked}
                    onCheckedChange={(checked) => update({ questionCount: checked === true ? null : 1 })}
                  />
                  <label htmlFor={`${titleId}-remainder`} className={cn("text-body text-text", remainderTaken ? "cursor-not-allowed opacity-50" : "cursor-pointer")}>
                    {t("remainder")}
                  </label>
                </div>
              </div>
            )}
          </FormField>
        )}
      </div>
      <BlockRowInfo
        block={block}
        section={section}
        partTotal={partTotal}
        rowsLocked={rowsLocked}
        lockId={lockId}
        available={availableQuery.data}
        entryMode={entryMode}
        onEntryMode={setEntryMode}
        onApplyCount={applyRateCount}
        onRateInvalid={onRateInvalid}
      />
      {isSet && countError && <p className="text-caption text-danger">{countError}</p>}
      {setIssue && setIssue.code === "SET_SIZE_MISMATCH" && (
        <p className="text-caption text-danger">{t("errors.setMismatch", { expected: setIssue.expected })}</p>
      )}
      {isSet && <p className="text-caption text-text-muted">{flexible ? tForm("setMode.note") : t("setWholeNote")}</p>}
      {summaryText && <p className="text-label font-semibold text-text tabular-nums">{summaryText}</p>}
      {droppedSlots.length > 0 && (
        <p role="status" className="rounded-md border border-info/30 bg-info-bg px-3 py-2 text-body text-info">
          {flexible
            ? tForm("slots.droppedToTotal", { list: droppedSlots.join(", ") })
            : tForm("slots.dropped", { sets: block.setCount ?? 0, list: droppedSlots.join(", ") })}
        </p>
      )}

      <BlockSwitches block={block} onChange={update} template={template} />

      {mapidMode !== null ? (
        <BlockMapidRules section={section} blockId={block.id} mode={mapidMode} reference={reference} onOpenSection={onOpenSection} anchor={anchor("scope")} />
      ) : (
        <KnowledgeScopeField
          value={block.knowledgeScope}
          onChange={(knowledgeScope) => update({ knowledgeScope })}
          inherited={{ scope: section.knowledgeScope }}
          reference={reference}
          subjectCode={subjectCode}
          name={block.name}
          anchor={anchor("scope")}
          strand={examKind !== undefined && STRAND_KINDS.includes(examKind)}
          openRequest={mapidRequest}
          everyChild={
            isSet
              ? {
                  checked: block.knowledgeScope?.everyChildInScope ?? false,
                  onChange: setEveryChild,
                  anchor: anchor("everyChildInScope"),
                }
              : undefined
          }
        />
      )}

      <BlockConditions
        block={block}
        reference={reference}
        onChange={update}
        onAddMapid={() => setMapidRequest((count) => count + 1)}
        mapidManaged={mapidMode !== null}
      />

      {isSet && <BlockSetConditions block={block} reference={reference} subjectCode={subjectCode} onChange={update} commandCount={commandCount} locked={rowsLocked} />}

      {competencies.length > 0 && (
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-field-label font-medium text-text-secondary">{t("competencies")}</legend>
          <div className="flex flex-wrap gap-x-5 gap-y-2">
            {competencyChoices.map((competency) => {
              const id = `${block.id}-competency-${competency.code}`;
              return (
                <div key={competency.code} className="flex items-center gap-2">
                  <Checkbox
                    id={id}
                    checked={selectedCompetencies.includes(competency.code)}
                    onCheckedChange={(checked) =>
                      setCompetencies(
                        checked === true
                          ? [...selectedCompetencies, competency.code]
                          : selectedCompetencies.filter((code) => code !== competency.code),
                      )
                    }
                  />
                  <label htmlFor={id} className="cursor-pointer text-body text-text">
                    {competency.name}
                  </label>
                </div>
              );
            })}
          </div>
          <p className="text-caption text-text-muted">{t("competenciesHint")}</p>
        </fieldset>
      )}

      <BlockScoringEditor
        block={block}
        defaults={scoringDefaults}
        onChange={(expectedScoring) => update({ expectedScoring })}
      />

      <details className="group rounded-md border border-border">
        <summary className="flex cursor-pointer list-none items-center gap-2 px-4 py-3 text-label font-semibold text-text">
          <ChevronRight aria-hidden className="size-4 transition-transform group-open:rotate-90 motion-reduce:transition-none" />
          {t("ownMatrix")}
          <span className="text-caption font-normal text-text-muted">
            {block.matrix === null ? t("ownMatrixNone") : t("ownMatrixSet")}
          </span>
        </summary>
        <div className="flex flex-col gap-3 border-t border-border p-4">
          <p className="text-caption text-text-muted">{t("ownMatrixHint")}</p>
          {remainder ? (
            <p className="text-body text-text-secondary">{t("ownMatrixNeedsCount")}</p>
          ) : (
            <>
              <BlockMatrixEditor
                key={block.id}
                block={editableMatrix}
                axes={axes}
                labels={labels}
                competencies={competencies.length ? matrixCompetencies : []}
                onChange={(next) => update({ matrix: next })}
                available={availableQuery.data}
                availableState={availableState}
                declared={block.matrix !== null}
              />
              {block.matrix !== null && (
                <Button type="button" variant="secondary" size="sm" className="w-fit" onClick={() => update({ matrix: null })}>
                  {t("ownMatrixRemove")}
                </Button>
              )}
            </>
          )}
        </div>
      </details>

      {/* Nhóm lấy phần còn lại không có bảng chỉ tiêu chủ đề: lỗi 422 của nó hiện như cảnh báo chung (R3-01). */}
      {mapidMode !== null ? null : !remainder && reference ? (
        <TopicQuotaTable
          id={block.id}
          quotas={block.topicQuotas ?? []}
          onChange={(topicQuotas) => update({ topicQuotas })}
          total={questionCount}
          levelName={block.name || t("untitled")}
          subjectCode={subjectCode}
          reference={reference}
          axes={axes}
          labels={labels}
          serverError={topicError}
        />
      ) : (
        <TopicQuotaElsewhereAlert error={topicError} />
      )}

      <ConfirmDialog
        open={confirmRemove}
        onOpenChange={setConfirmRemove}
        title={t("removeTitle", { name: block.name })}
        description={t("removeDescription", { count: questionCount })}
        confirmLabel={t("removeConfirm")}
        onConfirm={() => {
          setConfirmRemove(false);
          onRemove();
        }}
      />
    </div>
  );
}

/** Nút icon đầu thẻ nhóm: tách nhãn giao diện khỏi tên nhóm (`ExamName`) qua `aria-labelledby` (DESIGN.md mục 8). */
function BlockIconAction({
  icon,
  labelKey,
  nameNode,
  nameId,
  disabled,
  tone,
  onClick,
}: {
  icon: ReactNode;
  labelKey: "moveUp" | "moveDown" | "remove";
  nameNode: ReactNode;
  nameId: string;
  disabled?: boolean;
  tone?: "danger";
  onClick: () => void;
}) {
  const t = useTranslations("exams.structure.block");
  const buttonId = useId();
  const afterId = useId();
  const before = labelBeforeName(t, labelKey, "name");
  const after = labelAfterName(t, labelKey, "name");
  return (
    <>
      <IconButton
        id={buttonId}
        icon={icon}
        tone={tone}
        aria-label={before}
        aria-labelledby={[buttonId, nameId, after ? afterId : null].filter(Boolean).join(" ")}
        tooltip={richNodes(t, labelKey, { name: nameNode })}
        disabled={disabled}
        onClick={onClick}
      />
      {after && (
        <span id={afterId} hidden>
          {after}
        </span>
      )}
    </>
  );
}

interface BlockRowInfoProps {
  block: QuestionBlock;
  section: ExamSection;
  partTotal: number | null;
  rowsLocked: boolean;
  lockId: string;
  available: { total: number } | undefined;
  entryMode: "COUNT" | "RATE";
  onEntryMode: (mode: "COUNT" | "RATE") => void;
  onApplyCount: (count: number) => void;
  onRateInvalid?: (invalid: boolean) => void;
}

/**
 * Thông tin của dòng lấy câu (bản vẽ `NhomAudio2B`, R-130, R-135, R-142): nhãn audio theo phần (không đổi ở từng dòng),
 * nguồn khả dụng theo audio, tổng câu và tỷ lệ trong phần, nhập theo tỷ lệ (chùm phải ra số nguyên chùm), lý do khóa dòng.
 */
function BlockRowInfo({ block, section, partTotal, rowsLocked, lockId, available, entryMode, onEntryMode, onApplyCount, onRateInvalid }: BlockRowInfoProps) {
  const t = useTranslations("exams.structure.blockForm.rows");
  const format = useFormatter();
  const rateId = useId();
  const errorId = useId();
  const count = block.questionCount;
  const isSet = isSetBlock(block);
  const flexible = isFlexibleSet(block);
  const setSize = block.setSize ?? 0;
  const audio = sectionAudio(section);
  const total = partTotal !== null && partTotal > 0 ? partTotal : null;
  const share = blockShare(count, partTotal);
  // Tỷ lệ cần số câu của nhóm; chùm khai số chùm × cỡ chùm cần cỡ chùm để ra số nguyên chùm.
  const canRate = total !== null && !rowsLocked && count !== null && (!isSet || flexible || setSize >= 1);
  const rateMode = canRate && entryMode === "RATE";
  const [rateText, setRateText] = useState("");

  const parsed = rateMode ? parseNumberText(rateText, true) : 0;
  const derived = parsed === null || total === null ? 0 : countFromPercent(parsed, total);
  const needsWholeSets = isSet && !flexible;
  const mismatch = rateMode && parsed !== null && needsWholeSets ? rateSetMismatch(derived, setSize) : null;
  const rate = rateText.trim();
  let error: string | null = null;
  if (rateMode) {
    if (parsed === null) error = t("rate.errors.format");
    else if (mismatch) {
      error = t(mismatch.low === 0 ? "rate.errors.setsFromZero" : "rate.errors.sets", { rate, count: mismatch.count, size: setSize, low: mismatch.low, high: mismatch.high });
    } else if (derived === 0) error = t("rate.errors.zero");
  }
  const invalid = error !== null;
  const report = useEffectEvent((next: boolean) => onRateInvalid?.(next));
  useEffect(() => {
    report(invalid);
    return () => report(false);
  }, [invalid]);

  const pickMode = (mode: "COUNT" | "RATE") => {
    onEntryMode(mode);
    if (mode === "RATE" && total !== null) setRateText(String(ratePercent(count ?? 0, total) ?? 0).replace(".", ","));
  };
  const changeRate = (text: string) => {
    setRateText(text);
    const value = parseNumberText(text, true);
    if (value === null || total === null) return;
    const next = countFromPercent(value, total);
    if (next <= 0 || (needsWholeSets && rateSetMismatch(next, setSize))) return;
    if (next !== count) onApplyCount(next);
  };

  if (!audio && !rowsLocked && share === null && !canRate) return null;
  return (
    <div className="flex flex-col gap-3">
      {audio && (
        <div className="flex flex-wrap items-center gap-2">
          <AudioChip audio={audio} />
          <span className="text-caption text-text-muted">{t("audioNote")}</span>
        </div>
      )}
      {rowsLocked && (
        <div className="flex flex-col gap-1.5">
          <span className="inline-flex items-center gap-1.5">
            <Lock aria-hidden className="size-4 text-text-muted" />
            <StatusChip tone="neutral">{t("locked")}</StatusChip>
          </span>
          <p id={lockId} className="text-body text-text-secondary">
            {t("lockedReason")}
          </p>
          <p className="text-caption text-text-muted">{t("lockedHint")}</p>
        </div>
      )}
      {share !== null && count !== null && (
        <p className="text-label font-semibold text-text tabular-nums">
          {t("share", { count, rate: format.number(share, { maximumFractionDigits: 1 }) })}
        </p>
      )}
      {audio && available && (
        <p className="text-body text-text-secondary tabular-nums">{t("available", { count: available.total, audio })}</p>
      )}
      {canRate && (
        <ChoiceCardGroup
          variant="pill"
          legend={t("entry.legend")}
          value={rateMode ? "RATE" : "COUNT"}
          onChange={pickMode}
          options={[
            { value: "COUNT", label: t("entry.count") },
            { value: "RATE", label: t("entry.rate") },
          ]}
        />
      )}
      {rateMode && (
        <div className="flex max-w-xs flex-col gap-1.5">
          <label htmlFor={rateId} className="text-field-label font-medium text-text-secondary">
            {t("rate.label")}
          </label>
          <Input
            id={rateId}
            inputMode="decimal"
            autoComplete="off"
            value={rateText}
            aria-invalid={invalid || undefined}
            aria-describedby={error ? errorId : undefined}
            className="text-right tabular-nums"
            onChange={(event) => changeRate(event.target.value)}
          />
          {!error && <p className="text-caption text-text-muted tabular-nums">{t("rate.result", { count: derived })}</p>}
        </div>
      )}
      {error && (
        <p id={errorId} role="alert" className="rounded-md border border-danger/40 bg-danger-bg px-3 py-2 text-body text-text-secondary">
          <strong className="font-semibold text-danger">{t(isSet && !flexible && mismatch ? "rate.errors.setsTitle" : "rate.errors.title")}</strong> {error}
        </p>
      )}
    </div>
  );
}
