import { countFromPercent } from "./allocation";
import { isSetBlock } from "./matrix";
import type { QuestionType } from "@/features/content/types";
import type { ExamSection, MapidComparator, MapidMode, MapidRule, MapidRuleType, QuestionBlock, TopicQuota } from "./types";

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
 * độ khó hay nhóm. Quy tắc của máy chủ không có các trường đó (Q3), nên môn có chỉ tiêu như vậy giữ nguyên kiểu cũ.
 */
function convertibleQuota(quota: TopicQuota): boolean {
  return (
    quota.nodeIds.length > 0 &&
    quota.enforcement === "REQUIRED" &&
    (quota.basis === undefined || quota.basis === "QUESTION") &&
    !quota.gradeCodes?.length &&
    !quota.difficultyCodes?.length &&
    !quota.groupIds?.length
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
  const blockQuotas = section.blocks.flatMap((block) => block.topicQuotas ?? []);
  if (![...sectionQuotas, ...blockQuotas].every(convertibleQuota)) return section;

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
 * Số câu của một nhóm, mẫu số của quy tắc theo nhóm: chùm là `số chùm × số câu con`, nhóm câu đơn là số câu của nhóm. Nhóm "lấy
 * phần còn lại" (`questionCount` rỗng, không cỡ chùm) chưa biết: `null`, cùng cách máy chủ tính 0.
 */
export function groupUnits(block: QuestionBlock): number | null {
  if (isSetBlock(block) && block.setCount && block.setSize) return block.setCount * block.setSize;
  return block.questionCount;
}

export interface GroupSize {
  /** Chùm: số chùm và số câu con mỗi chùm; nhóm câu đơn: `null`. */
  sets: number | null;
  size: number | null;
  units: number | null;
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
    units: groupUnits(block),
    total: section.questionCount,
    type: block.questionType,
  };
}

/** Mẫu số của quy tắc: số câu của môn khi quy tắc cả môn, của nhóm khi quy tắc theo nhóm; `null` khi chưa biết hoặc nhóm không còn. */
export function ruleUnits(section: ExamSection, rule: Pick<MapidRule, "groupId">): number | null {
  if (!rule.groupId) return section.questionCount;
  const block = section.blocks.find((item) => item.id === rule.groupId);
  return block ? groupUnits(block) : null;
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
  type: MapidRuleType;
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
export function ruleNeedsNumber(type: MapidRuleType, withNumber: boolean): boolean {
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
  const base: MapidRule = { id: form.id, type: form.type, groupId: form.groupId, nodeIds: [...form.nodeIds] };
  if (!ruleNeedsNumber(form.type, form.withNumber)) return base;
  const withComparator: MapidRule = { ...base, comparator: form.comparator };
  if (form.entry === "COUNT") return { ...withComparator, count: form.count };
  return { ...withComparator, count: ruleCount(section, { ...withComparator, percent: form.percent }), percent: form.percent };
}

export type RuleIssue = "NO_NODES" | "GROUP_MISSING" | "ONLY_IN_GROUP_NEEDS_GROUP" | "PERCENT_RANGE";

/** Lỗi chặn nút Lưu của hộp thoại. Quy tắc của nhóm đã xóa báo `GROUP_MISSING` cho tới khi người dùng chọn lại phạm vi. */
export function ruleFormIssues(form: MapidRuleForm, section: ExamSection): RuleIssue[] {
  const issues: RuleIssue[] = [];
  if (form.groupId && !section.blocks.some((block) => block.id === form.groupId)) issues.push("GROUP_MISSING");
  if (form.nodeIds.length === 0) issues.push("NO_NODES");
  if (form.type === "ONLY_IN_GROUP" && !form.groupId) issues.push("ONLY_IN_GROUP_NEEDS_GROUP");
  if (ruleNeedsNumber(form.type, form.withNumber) && form.entry === "PERCENT" && (form.percent < 0 || form.percent > 100)) issues.push("PERCENT_RANGE");
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

/** Đưa focus tới dòng quy tắc; trả `false` khi màn không có dòng đó (ví dụ môn đang ở chế độ Toàn bài). */
export function focusMapidRule(ruleId: string, root: ParentNode = document): boolean {
  const target = root.querySelector<HTMLElement>(mapidRuleSelector(ruleId));
  if (!target) return false;
  target.scrollIntoView({ block: "center" });
  target.focus();
  return true;
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
