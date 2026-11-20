import { countFromPercent } from "./allocation";
import { isSetBlock } from "./matrix";
import { effectiveScope } from "./subject-matrix";
import type { QuestionType } from "@/features/content/types";
import type {
  CountKnowledgeScope,
  ExamSection,
  KnowledgeScope,
  MapidComparator,
  MapidMode,
  MapidRule,
  MapidRuleType,
  QuestionBlock,
  TopicQuota,
} from "./types";

/**
 * Quy tắc MAPID của môn HSA (UNI-1704 v2, UNI-2758): dữ liệu và phép tính thuần của bảng "Quy tắc MAPID". Hình dạng dữ liệu khớp
 * record `MapidRule` của máy chủ (`mapidMode`, `mapidRules` trong cấu trúc); máy chủ biên dịch quy tắc thành phạm vi, chỉ tiêu,
 * giới hạn trên và loại trừ khi `mapidMode = MATRIX`.
 */

/** Số quy tắc tối đa của một môn (Q5); khớp `ExamStructureValidator.DEFAULT_MAX_MAPID_RULES`. */
export const MAX_MAPID_RULES = 20;

/**
 * Môn HSA dùng ma trận MAPID: Toán, Lý, Hóa, Sinh, Sử, Địa. Văn (`VAN`) và Tiếng Anh (`ANH`) giữ phạm vi kiểu cũ; môn nào không
 * có trong danh sách này cũng không có chế độ MAPID (Q4). Quyết định theo mã môn của `section.subjectCode`.
 */
const MATRIX_SUBJECT_CODES: Readonly<Record<string, true>> = { TOAN: true, LY: true, HOA: true, SINH: true, SU: true, DIA: true };

export function usesMapidMatrix(subjectCode: string): boolean {
  return Object.hasOwn(MATRIX_SUBJECT_CODES, subjectCode);
}

// ---------------------------------------------------------------- dữ liệu cũ

const hasNodes = (scope: { nodeIds: readonly string[] } | null | undefined): boolean => (scope?.nodeIds.length ?? 0) > 0;

/** Môn còn phạm vi MAPID hoặc chỉ tiêu chủ đề kiểu cũ, ở môn hoặc ở nhóm. */
export function hasLegacyMapid(section: ExamSection): boolean {
  return (
    hasNodes(section.knowledgeScope) ||
    (section.topicQuotas?.length ?? 0) > 0 ||
    section.blocks.some((block) => hasNodes(block.knowledgeScope) || (block.topicQuotas?.length ?? 0) > 0)
  );
}

/**
 * Chỉ tiêu kiểu cũ chuyển được sang quy tắc "ít nhất" mà không mất gì: có MAPID, mức Bắt buộc, đơn vị câu, không lọc khối lớp,
 * độ khó hay nhóm. Quy tắc của máy chủ không có các trường đó (Q3), nên môn có chỉ tiêu như vậy giữ nguyên kiểu cũ. Chỉ tiêu theo
 * tỷ lệ cần mẫu số (`units`: số câu của môn hoặc của nhóm): máy chủ tính `percent` ra số câu theo mẫu số và để `percent` thắng
 * `count`, nên khi chưa biết mẫu số (môn chọn môn có số câu theo `countsByPick`, nhóm lấy phần còn lại của môn chưa có số câu) quy
 * tắc mới ra 0 câu và chỉ tiêu bắt buộc đã lưu mất âm thầm (R1-F1); môn đó giữ kiểu cũ, nơi máy chủ dùng `count` đã lưu.
 */
function convertibleQuota(quota: TopicQuota, units: number | null): boolean {
  const byPercent = quota.percent !== undefined && quota.percent !== null;
  return (
    quota.nodeIds.length > 0 &&
    quota.enforcement === "REQUIRED" &&
    (quota.basis === undefined || quota.basis === "QUESTION") &&
    !quota.gradeCodes?.length &&
    !quota.difficultyCodes?.length &&
    !quota.groupIds?.length &&
    (!byPercent || units !== null)
  );
}

function quotaRule(quota: TopicQuota, id: string, groupId: string | null): MapidRule {
  return {
    id,
    type: "COUNT",
    groupId,
    nodeIds: [...quota.nodeIds],
    comparator: "AT_LEAST",
    count: quota.count,
    ...(quota.percent !== undefined && quota.percent !== null ? { percent: quota.percent } : {}),
  };
}

/** Phạm vi cũ sau khi bỏ MAPID: giữ lọc năng lực (máy chủ không đụng tới), còn lại bỏ; `null` khi không còn gì. */
function withoutNodes(scope: QuestionBlock["knowledgeScope"]): QuestionBlock["knowledgeScope"] {
  return scope?.competencyCodes?.length ? { nodeIds: [], competencyCodes: scope.competencyCodes } : null;
}

/**
 * Phạm vi MAPID cũ phải lọc như quy tắc SOURCE mới thì mới chuyển được, nếu không chuyển sẽ đổi nguồn của đề (R1-2, R1-3):
 * - Quy tắc SOURCE của máy chủ áp dụng VÀ (cả môn và nhóm cùng phải khớp), còn bản cũ để phạm vi riêng của nhóm thay phạm vi của
 *   môn: môn có MAPID ở cả môn và ở một nhóm giữ kiểu cũ.
 * - Phạm vi cũ của chùm lọc theo "mọi câu con thuộc hợp các MAPID" (`everyChildInScope`), hoặc lỏng hơn khi cờ tắt (chỉ cần một
 *   câu con hoặc cả bộ); SOURCE mới đòi mọi câu con cùng nằm dưới MỘT MAPID chung. Chỉ khi chùm có đúng một MAPID và đòi mọi câu
 *   con thì hai bên lọc như nhau. Phạm vi tính theo `effectiveScope`: chùm chưa chọn MAPID kế thừa phạm vi của môn.
 */
function sourcesUnchanged(section: ExamSection): boolean {
  if (hasNodes(section.knowledgeScope) && section.blocks.some((block) => hasNodes(block.knowledgeScope))) return false;
  return section.blocks.every((block) => {
    if (!isSetBlock(block)) return true;
    const scope = effectiveScope(block, section);
    return !scope || !hasNodes(scope) || (scope.nodeIds.length === 1 && scope.everyChildInScope === true);
  });
}

/**
 * Môn HSA dùng ma trận còn dữ liệu kiểu cũ thì chuyển sang quy tắc khi mở (UNI-1704 v2): phạm vi MAPID của môn thành quy tắc
 * SOURCE cả môn; chỉ tiêu chủ đề của môn thành COUNT "ít nhất" cả môn (số câu, tỷ lệ giữ nguyên); phạm vi riêng của nhóm thành
 * SOURCE nhóm; chỉ tiêu chủ đề của nhóm thành COUNT "ít nhất" của nhóm. Có quy tắc thì chế độ là MATRIX và các trường cũ bị
 * xóa (bản lưu kế tiếp ghi dạng mới). Không có gì để chuyển, hoặc chuyển sẽ mất dữ liệu (chỉ tiêu có lọc khối lớp, độ khó,
 * chưa gắn MAPID, mức Tham khảo, đơn vị lệnh hỏi hoặc điểm, quá 20 quy tắc): trả đúng `section` và môn giữ kiểu cũ. Môn đã có
 * `mapidMode` không đổi. Trả đúng tham chiếu cũ khi không đổi, để so bản nháp với bản đã lưu không báo thay đổi giả.
 */
export function convertLegacySection(section: ExamSection): ExamSection {
  if (section.mapidMode !== undefined || !usesMapidMatrix(section.subjectCode) || !hasLegacyMapid(section)) return section;
  const sectionQuotas = section.topicQuotas ?? [];
  const quotasConvert =
    sectionQuotas.every((quota) => convertibleQuota(quota, section.questionCount)) &&
    section.blocks.every((block) => (block.topicQuotas ?? []).every((quota) => convertibleQuota(quota, ruleUnits(section, { groupId: block.id }))));
  if (!quotasConvert || !sourcesUnchanged(section)) return section;

  const ids = new Set<string>();
  const unique = (base: string): string => {
    let id = base;
    for (let index = 2; ids.has(id); index += 1) id = `${base}-${index}`;
    ids.add(id);
    return id;
  };
  const rules: MapidRule[] = [];
  if (hasNodes(section.knowledgeScope)) {
    rules.push({ id: unique("legacy-source"), type: "SOURCE", groupId: null, nodeIds: [...section.knowledgeScope!.nodeIds] });
  }
  sectionQuotas.forEach((quota, index) => rules.push(quotaRule(quota, unique(`legacy-${quota.id || `q${index + 1}`}`), null)));
  for (const block of section.blocks) {
    if (hasNodes(block.knowledgeScope)) {
      rules.push({ id: unique(`legacy-source-${block.id}`), type: "SOURCE", groupId: block.id, nodeIds: [...block.knowledgeScope!.nodeIds] });
    }
    (block.topicQuotas ?? []).forEach((quota, index) => rules.push(quotaRule(quota, unique(`legacy-${quota.id || `${block.id}-q${index + 1}`}`), block.id)));
  }
  if (rules.length === 0 || rules.length > MAX_MAPID_RULES) return section;

  const converted: ExamSection = {
    ...section,
    knowledgeScope: withoutNodes(section.knowledgeScope),
    blocks: section.blocks.map((block) => {
      const cleared: QuestionBlock = { ...block, knowledgeScope: withoutNodes(block.knowledgeScope) };
      delete cleared.topicQuotas;
      return cleared;
    }),
    mapidMode: "MATRIX",
    mapidRules: rules,
  };
  delete converted.topicQuotas;
  return converted;
}

/**
 * Chế độ hiển thị của môn: `null` khi môn không dùng ma trận MAPID hoặc còn dữ liệu kiểu cũ không chuyển được (màn giữ phạm vi
 * và chỉ tiêu chủ đề kiểu cũ); môn chưa có `mapidMode` và không có dữ liệu cũ là `ALL`.
 */
export function mapidViewMode(section: ExamSection): MapidMode | null {
  if (!usesMapidMatrix(section.subjectCode)) return null;
  if (section.mapidMode !== undefined) return section.mapidMode;
  return hasLegacyMapid(section) ? null : "ALL";
}

// ---------------------------------------------------------------- phạm vi và số câu

/**
 * Số câu cố định của một nhóm: chùm là `số chùm × số câu con`, nhóm câu đơn là số câu của nhóm. Nhóm "lấy phần còn lại"
 * (`questionCount` rỗng, không phải chùm) không có số cố định: `null`; số của nó là `remainderUnits`.
 */
export function groupUnits(block: QuestionBlock): number | null {
  if (isSetBlock(block) && block.setCount && block.setSize) return block.setCount * block.setSize;
  return block.questionCount;
}

/** Nhóm câu đơn không khai số câu: máy chủ giao cho nó phần môn còn lại sau các nhóm khác (`Prep.prepare`). */
function takesRemainder(block: QuestionBlock): boolean {
  return !isSetBlock(block) && block.questionCount === null;
}

/**
 * Số câu của nhóm lấy phần còn lại, tính như máy chủ: số câu của môn trừ số câu của mọi nhóm có số cố định (chùm và nhóm câu đơn
 * đã khai số câu; các nhóm lấy phần còn lại khác không bị trừ, mỗi nhóm nhận đủ phần dư; nhóm đã bỏ `omitted` không phải nhóm nên
 * cũng không bị trừ, R1-F2). `null` khi môn chưa có số câu, một nhóm cố định chưa biết số câu (chùm chưa khai cỡ), hoặc các nhóm
 * khác đã vượt số câu của môn.
 */
function remainderUnits(section: Pick<ExamSection, "questionCount" | "blocks">, block: QuestionBlock): number | null {
  if (section.questionCount === null) return null;
  let fixed = 0;
  for (const other of section.blocks) {
    if (other.id === block.id || other.omitted === true || takesRemainder(other)) continue;
    const units = groupUnits(other);
    if (units === null) return null;
    fixed += units;
  }
  const rest = section.questionCount - fixed;
  return rest < 0 ? null : rest;
}

/** Số câu của một nhóm trong môn của nó: số cố định, hoặc phần còn lại với nhóm lấy phần còn lại. */
function unitsInSection(section: Pick<ExamSection, "questionCount" | "blocks">, block: QuestionBlock): number | null {
  return takesRemainder(block) ? remainderUnits(section, block) : groupUnits(block);
}

export interface GroupSize {
  /** Chùm: số chùm và số câu con mỗi chùm; nhóm câu đơn: `null`. */
  sets: number | null;
  size: number | null;
  units: number | null;
  /** Nhóm lấy phần còn lại: `units` là phần môn còn lại sau các nhóm khác (`null` khi chưa tính được). */
  remainder: boolean;
  /** Số câu của cả môn (mẫu số của cột "câu của bài"); `null` khi chưa biết. */
  total: number | null;
  type: QuestionType | null;
}

/** Cỡ hiển thị của nhóm trong hộp thoại, ví dụ "1 chùm × 3 câu con = 3/50 câu". */
export function groupSize(section: ExamSection, block: QuestionBlock): GroupSize {
  const set = isSetBlock(block) && block.setCount && block.setSize;
  return {
    sets: set ? block.setCount : null,
    size: set ? block.setSize : null,
    units: unitsInSection(section, block),
    remainder: takesRemainder(block),
    total: section.questionCount,
    type: block.questionType,
  };
}

/**
 * Mẫu số của quy tắc: số câu của môn khi quy tắc cả môn, của nhóm khi quy tắc theo nhóm (nhóm lấy phần còn lại: phần môn còn
 * lại sau các nhóm khác); `null` khi chưa biết hoặc nhóm không còn.
 */
export function ruleUnits(section: ExamSection, rule: Pick<MapidRule, "groupId">): number | null {
  if (!rule.groupId) return section.questionCount;
  const block = section.blocks.find((item) => item.id === rule.groupId);
  return block ? unitsInSection(section, block) : null;
}

/** Số câu của quy tắc: nhập theo tỷ lệ thì quy ra số câu theo mẫu số hiện tại (làm tròn nửa lên); không thì số đã nhập. */
export function ruleCount(section: ExamSection, rule: MapidRule): number {
  if (rule.percent !== undefined && rule.percent !== null) {
    const units = ruleUnits(section, rule);
    return units === null ? (rule.count ?? 0) : countFromPercent(rule.percent, units);
  }
  return rule.count ?? 0;
}

/**
 * Tính lại `count` của quy tắc nhập theo tỷ lệ khi số câu của môn hoặc của nhóm đổi (BA mục 5: đổi số câu thì tính lại chỉ tiêu
 * theo tỷ lệ). `percent` giữ nguyên. Trả đúng `section` khi không có gì đổi.
 */
export function recomputeMapidRules(section: ExamSection): ExamSection {
  const rules = section.mapidRules;
  if (!rules?.length) return section;
  let changed = false;
  const next = rules.map((rule) => {
    if (rule.percent === undefined || rule.percent === null) return rule;
    const count = ruleCount(section, rule);
    if (count === rule.count) return rule;
    changed = true;
    return { ...rule, count };
  });
  return changed ? { ...section, mapidRules: next } : section;
}

/** Chuẩn hóa một môn HSA khi nạp hoặc sửa: chuyển dữ liệu cũ, rồi tính lại số câu của quy tắc theo tỷ lệ. */
export function normalizeMapidSection(section: ExamSection): ExamSection {
  return usesMapidMatrix(section.subjectCode) ? recomputeMapidRules(convertLegacySection(section)) : section;
}

// ---------------------------------------------------------------- nhóm đã xóa

/** Quy tắc trỏ tới nhóm không còn trong môn: máy chủ từ chối `groupId` lạ nên phải sửa hoặc xóa trước khi lưu. */
export function deletedGroupRules(section: Pick<ExamSection, "mapidRules" | "blocks">): MapidRule[] {
  const ids = new Set(section.blocks.map((block) => block.id));
  return (section.mapidRules ?? []).filter((rule) => !!rule.groupId && !ids.has(rule.groupId));
}

// ---------------------------------------------------------------- câu mô tả

export type RuleSentenceKind =
  | "PRESENT"
  | "COUNT_EXACT"
  | "COUNT_AT_LEAST"
  | "ONLY_IN_GROUP"
  | "ONLY_IN_GROUP_EXACT"
  | "ONLY_IN_GROUP_AT_LEAST"
  | "SOURCE";

export interface RuleSentence {
  kind: RuleSentenceKind;
  scope: "SUBJECT" | "GROUP" | "DELETED";
  /** Nhóm của quy tắc; `null` khi quy tắc cả môn hoặc nhóm đã xóa. */
  group: QuestionBlock | null;
  /** Tên nhóm đã xóa còn ghi trên quy tắc (`MapidRule.deletedGroupName`) cho tới khi lưu; `null` khi không có. */
  deletedGroupName: string | null;
  /** Chỉ tiêu dạng "3/15" (số câu trên mẫu số), "3/15 (20%)" khi nhập theo tỷ lệ; "20%" khi chưa biết mẫu số; `null` khi quy tắc không có số. */
  amount: string | null;
}

function amountText(section: ExamSection, rule: MapidRule): string | null {
  const percent = rule.percent ?? null;
  if (percent === null && (rule.count === undefined || rule.count === null)) return null;
  const units = ruleUnits(section, rule);
  if (units === null) return percent === null ? String(rule.count) : `${percent}%`;
  const ratio = `${ruleCount(section, rule)}/${units}`;
  return percent === null ? ratio : `${ratio} (${percent}%)`;
}

function sentenceKind(rule: MapidRule): RuleSentenceKind {
  const suffix = rule.comparator === "EXACT" ? "EXACT" : "AT_LEAST";
  const numbered = rule.count !== undefined && rule.count !== null ? true : rule.percent !== undefined && rule.percent !== null;
  switch (rule.type) {
    case "PRESENT":
      return "PRESENT";
    case "SOURCE":
      return "SOURCE";
    case "COUNT":
      return `COUNT_${suffix}`;
    case "ONLY_IN_GROUP":
      return numbered ? `ONLY_IN_GROUP_${suffix}` : "ONLY_IN_GROUP";
  }
}

/** Dữ liệu cho câu của một quy tắc; chữ do màn dịch (khóa `exams.mapidRules.sentence.<kind>`). */
export function describeRule(section: ExamSection, rule: MapidRule): RuleSentence {
  const group = rule.groupId ? (section.blocks.find((block) => block.id === rule.groupId) ?? null) : null;
  return {
    kind: sentenceKind(rule),
    scope: !rule.groupId ? "SUBJECT" : group ? "GROUP" : "DELETED",
    group,
    deletedGroupName: !group && rule.groupId ? (rule.deletedGroupName ?? null) : null,
    amount: amountText(section, rule),
  };
}

export interface GroupRule {
  rule: MapidRule;
  /** OWN: quy tắc của chính nhóm; SUBJECT: giới hạn nguồn của cả môn, nhóm cũng phải theo; ELSEWHERE: MAPID chỉ được ở nhóm khác nên nhóm này loại ra. */
  role: "OWN" | "SUBJECT" | "ELSEWHERE";
}

/** Quy tắc có hiệu lực với nguồn của một nhóm, cho cột "Phạm vi nguồn" của bảng nhóm lấy câu. */
export function groupEffectiveRules(section: ExamSection, blockId: string): GroupRule[] {
  const exists = new Set(section.blocks.map((block) => block.id));
  const result: GroupRule[] = [];
  for (const rule of section.mapidRules ?? []) {
    if (rule.groupId === blockId) result.push({ rule, role: "OWN" });
    else if (!rule.groupId && rule.type === "SOURCE") result.push({ rule, role: "SUBJECT" });
    else if (rule.type === "ONLY_IN_GROUP" && rule.groupId && exists.has(rule.groupId)) result.push({ rule, role: "ELSEWHERE" });
  }
  return result;
}

// ---------------------------------------------------------------- biểu mẫu thêm, sửa quy tắc

export interface MapidRuleForm {
  id: string;
  type: MapidRuleType | null;
  /** `null`: cả môn. */
  groupId: string | null;
  nodeIds: string[];
  comparator: MapidComparator;
  /** ONLY_IN_GROUP chỉ có số khi người dùng bật "Kèm số lượng". */
  withNumber: boolean;
  entry: "COUNT" | "PERCENT";
  count: number;
  percent: number;
}

/** Quy tắc cần ô số: COUNT luôn; ONLY_IN_GROUP khi kèm số lượng; PRESENT và SOURCE không bao giờ. */
export function ruleNeedsNumber(type: MapidRuleType | null, withNumber: boolean): boolean {
  return type === "COUNT" || (type === "ONLY_IN_GROUP" && withNumber);
}

export function emptyRuleForm(id: string, groupId: string | null = null): MapidRuleForm {
  return { id, type: "PRESENT", groupId, nodeIds: [], comparator: "AT_LEAST", withNumber: false, entry: "COUNT", count: 1, percent: 10 };
}

export function formFromRule(rule: MapidRule): MapidRuleForm {
  const percent = rule.percent !== undefined && rule.percent !== null;
  return {
    id: rule.id,
    type: rule.type,
    groupId: rule.groupId ?? null,
    nodeIds: [...rule.nodeIds],
    comparator: rule.comparator ?? "AT_LEAST",
    withNumber: rule.count !== undefined && rule.count !== null ? true : percent,
    entry: percent ? "PERCENT" : "COUNT",
    count: rule.count ?? 1,
    percent: rule.percent ?? 10,
  };
}

/** Quy tắc lưu từ biểu mẫu: chỉ giữ trường mà loại quy tắc dùng; nhập theo tỷ lệ ghi cả `percent` và `count` đã quy đổi. */
export function ruleFromForm(form: MapidRuleForm, section: ExamSection): MapidRule {
  if (!form.type) {
    throw new Error("MapidRuleForm.type is required");
  }
  const base: MapidRule = { id: form.id, type: form.type, groupId: form.groupId, nodeIds: [...form.nodeIds] };
  if (!ruleNeedsNumber(form.type, form.withNumber)) return base;
  const withComparator: MapidRule = { ...base, comparator: form.comparator };
  if (form.entry === "COUNT") return { ...withComparator, count: form.count };
  return { ...withComparator, count: ruleCount(section, { ...withComparator, percent: form.percent }), percent: form.percent };
}

export type RuleIssue =
  | "TYPE_REQUIRED"
  | "NO_NODES"
  | "GROUP_MISSING"
  | "ONLY_IN_GROUP_NEEDS_GROUP"
  | "PERCENT_RANGE"
  | "PERCENT_DENOMINATOR_UNKNOWN"
  | "PERCENT_POOL"
  | "COUNT_ABOVE_GROUP"
  | "COUNT_ABOVE_TOTAL";

/**
 * Lỗi chặn nút Lưu của hộp thoại. Quy tắc của nhóm đã xóa báo `GROUP_MISSING` cho tới khi người dùng chọn lại phạm vi. Nhập theo tỷ lệ
 * mà chưa biết mẫu số (môn chưa có số câu, hoặc nhóm khác chưa khai cỡ nên không tính được phần còn lại) báo
 * `PERCENT_DENOMINATOR_UNKNOWN`: lưu lúc đó sẽ ghi 0 câu âm thầm.
 */
export function ruleFormIssues(form: MapidRuleForm, section: ExamSection): RuleIssue[] {
  const issues: RuleIssue[] = [];
  const groupGone = !!form.groupId && !section.blocks.some((block) => block.id === form.groupId);
  if (groupGone) issues.push("GROUP_MISSING");
  if (form.type === null) issues.push("TYPE_REQUIRED");
  if (form.nodeIds.length === 0) issues.push("NO_NODES");
  if (form.type === "ONLY_IN_GROUP" && !form.groupId) issues.push("ONLY_IN_GROUP_NEEDS_GROUP");
  const units = !groupGone ? ruleUnits(section, form) : null;
  if (ruleNeedsNumber(form.type, form.withNumber)) {
    let resolvedCount: number | null = null;
    if (form.entry === "PERCENT") {
      if (form.percent < 0 || form.percent > 100) issues.push("PERCENT_RANGE");
      else if (!groupGone && units === null) {
        // Môn tự chọn (HSA Khoa học): số câu theo `countsByPick`, người dùng không nhập được ở môn nên chỉ còn nhập theo số câu (R1-F10).
        issues.push(section.questionCount === null && section.choiceGroup !== null ? "PERCENT_POOL" : "PERCENT_DENOMINATOR_UNKNOWN");
      } else if (units !== null) {
        resolvedCount = countFromPercent(form.percent, units);
      }
    } else {
      resolvedCount = form.count;
    }
    if (resolvedCount !== null) {
      if (form.groupId !== null && !groupGone && units !== null && resolvedCount > units) {
        issues.push("COUNT_ABOVE_GROUP");
      }
      // Máy chủ từ chối cả bản lưu khi số câu của quy tắc vượt số câu của môn (`ExamStructureValidator.validateMapidRules`).
      if (section.questionCount !== null && resolvedCount > section.questionCount) {
        issues.push("COUNT_ABOVE_TOTAL");
      }
    }
  }
  return issues;
}

// ---------------------------------------------------------------- mỏ neo

/** Mỏ neo của dòng quy tắc (`id` và `data-field`): kết quả kiểm tra, xem thử cuộn tới đúng dòng bằng `focusMapidRule`. */
export function mapidRuleAnchor(ruleId: string): string {
  return `mapid-rule-${ruleId}`;
}

export function mapidRuleSelector(ruleId: string): string {
  return `[data-field="${CSS.escape(mapidRuleAnchor(ruleId))}"]`;
}

const highlightTimers = new WeakMap<HTMLElement, number>();

/** Đưa focus tới dòng quy tắc; trả `false` khi màn không có dòng đó (ví dụ môn đang ở chế độ Toàn bài). */
export function focusMapidRule(ruleId: string, root: ParentNode = document): boolean {
  const target = root.querySelector<HTMLElement>(mapidRuleSelector(ruleId));
  if (!target) return false;
  target.scrollIntoView({ block: "center" });
  target.focus();
  if (!target.hasAttribute("data-mapid-focus")) {
    target.setAttribute("data-mapid-focus", "");
    target.addEventListener("blur", () => target.removeAttribute("data-mapid-focus"), { once: true });
  }
  target.setAttribute("data-mapid-highlight", "");
  window.clearTimeout(highlightTimers.get(target));
  highlightTimers.set(target, window.setTimeout(() => {
    target.removeAttribute("data-mapid-highlight");
    highlightTimers.delete(target);
  }, 3000));
  return true;
}

/**
 * Từ vấn đề của kiểm tra, xem thử sang dòng quy tắc: màn môn dựng sau khi mở môn, nên chờ tối đa `frames` khung hình cho tới khi
 * dòng xuất hiện rồi cuộn tới và đưa tiêu điểm vào. Trả `false` khi không có dòng (môn ở chế độ Toàn bài, quy tắc đã xóa).
 */
export function focusMapidRuleWhenReady(ruleId: string, root: ParentNode = document, frames = 30): Promise<boolean> {
  const { promise, resolve } = Promise.withResolvers<boolean>();
  let left = frames;
  const look = () => {
    if (focusMapidRule(ruleId, root)) resolve(true);
    else if (--left <= 0) resolve(false);
    else requestAnimationFrame(look);
  };
  requestAnimationFrame(look);
  return promise;
}

/**
 * Từ màn nhóm sang bảng "Quy tắc MAPID" của môn: màn môn dựng sau khi chuyển, nên chờ tối đa `frames` khung hình cho tới khi
 * bảng xuất hiện rồi cuộn tới và đưa tiêu điểm vào. Trả `false` khi không có bảng (môn ở chế độ Toàn bài).
 */
export function revealMapidRulesTable(root: ParentNode = document, frames = 30): Promise<boolean> {
  const { promise, resolve } = Promise.withResolvers<boolean>();
  let left = frames;
  const look = () => {
    const table = root.querySelector<HTMLElement>('[data-testid="mapid-rules-table"]');
    if (table) {
      table.scrollIntoView({ block: "start" });
      table.focus();
      resolve(true);
    } else if (--left <= 0) {
      resolve(false);
    } else {
      requestAnimationFrame(look);
    }
  };
  requestAnimationFrame(look);
  return promise;
}

// ---------------------------------------------------------------- phạm vi đếm nguồn

const unique = (items: readonly string[]): string[] => [...new Set(items)];

/** Phạm vi chỉ còn lọc năng lực (MAPID cũ bị bỏ khi môn có `mapidMode`); `null` khi không còn gì. */
function competencyOnly(legacy: KnowledgeScope | null): CountKnowledgeScope | null {
  return legacy ? { nodeIds: [], competencyCodes: legacy.competencyCodes ?? [] } : null;
}

/**
 * Phạm vi để đếm nguồn của một nhóm, khớp cách máy chủ biên dịch khi sinh đề (`MapidRuleCompiler`): môn có `mapidMode` thì MAPID
 * cũ của môn và nhóm bị bỏ; ở MATRIX mỗi quy tắc "Chỉ lấy từ" là MỘT nhóm MAPID (`nodeGroups`: trong một quy tắc là HOẶC), câu
 * phải khớp mọi nhóm áp dụng, tức quy tắc của cả môn và của chính nhóm (VÀ). Chùm còn đòi mọi câu con cùng một MAPID của từng nhóm.
 * `excludedNodeIds` là MAPID mà quy tắc "Chỉ xuất hiện ở nhóm này" của nhóm khác loại khỏi nhóm này. Môn chưa có `mapidMode` (kỳ
 * thi khác, Văn, Anh) giữ phạm vi kiểu cũ.
 */
export function groupCountScope(block: QuestionBlock, section: ExamSection): CountKnowledgeScope | null {
  if (section.mapidMode === undefined) return effectiveScope(block, section);
  const rules = section.mapidMode === "MATRIX" ? (section.mapidRules ?? []) : [];
  const sourceGroups = (groupId: string | null) =>
    rules
      .filter((rule) => rule.type === "SOURCE" && (rule.groupId ?? null) === groupId)
      .map((rule) => unique(rule.nodeIds))
      .filter((nodes) => nodes.length > 0);
  const own = sourceGroups(block.id);
  const nodeGroups = [...sourceGroups(null), ...own];
  const inherited = effectiveScope({ knowledgeScope: competencyOnly(block.knowledgeScope) }, { knowledgeScope: competencyOnly(section.knowledgeScope) });
  const scope: CountKnowledgeScope | null =
    nodeGroups.length > 0
      ? {
          nodeIds: [],
          competencyCodes: inherited?.competencyCodes ?? [],
          nodeGroups,
          ...(isSetBlock(block) ? { everyChildInScope: true, everyChildSameNode: true } : {}),
        }
      : inherited;
  const groups = new Set(section.blocks.map((item) => item.id));
  const excluded = unique(
    rules
      .filter((rule) => rule.type === "ONLY_IN_GROUP" && rule.groupId && rule.groupId !== block.id && groups.has(rule.groupId))
      .flatMap((rule) => rule.nodeIds),
  );
  return excluded.length > 0 ? { ...(scope ?? { nodeIds: [] }), excludedNodeIds: excluded } : scope;
}
