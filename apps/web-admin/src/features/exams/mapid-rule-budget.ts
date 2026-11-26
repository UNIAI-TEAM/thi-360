import type { KnowledgeIndexEntry } from "@/features/content/knowledge-tree";
import { nodesCover, ruleDemand } from "./mapid-rule-form-conflicts";
import { ruleCount, ruleUnits } from "./mapid-rules";
import type { ExamSection, MapidRule } from "./types";

/**
 * Quy tắc MAPID "ngân sách lồng nhau" (quyết định 2026-11-26, `UNI-2824`): số của toàn môn vẫn là tổng cả đề, mỗi nhóm
 * lấy một phần. Với MAPID X, phần đã chia = tổng mức tối thiểu của các nhóm có quy tắc trên X hoặc MAPID con của X
 * (trong một nhóm lấy mức lớn nhất, như `MapidRuleConflicts` ở máy chủ); phần còn lại dành cho các vị trí ngẫu nhiên.
 */

type KnowledgeIndex = ReadonlyMap<string, KnowledgeIndexEntry>;

export interface GroupShare {
  groupId: string;
  need: number;
}

const numbered = (rule: MapidRule): boolean =>
  rule.type === "COUNT" || (rule.type === "ONLY_IN_GROUP" && (rule.count != null || rule.percent != null));

/** Số "đúng N" của quy tắc; `null` khi quy tắc không phải "đúng". */
function exactCount(section: ExamSection, rule: MapidRule): number | null {
  return numbered(rule) && rule.comparator === "EXACT" ? ruleCount(section, rule) : null;
}

function activeGroupIds(section: ExamSection): Set<string> {
  return new Set(section.blocks.filter((block) => block.omitted !== true).map((block) => block.id));
}

/** Quy tắc có MAPID, ở toàn môn hoặc ở nhóm còn hoạt động. */
function activeRules(section: ExamSection, rules: readonly MapidRule[]): MapidRule[] {
  const groups = activeGroupIds(section);
  return rules.filter((rule) => rule.nodeIds.length > 0 && (!rule.groupId || groups.has(rule.groupId)));
}

/** Phần mỗi nhóm đã lấy trong MAPID `nodeIds`, theo thứ tự nhóm ở cấu trúc; bỏ nhóm không cần câu nào. */
export function groupSharesUnder(
  section: ExamSection,
  rules: readonly MapidRule[],
  nodeIds: readonly string[],
  knowledgeIndex: KnowledgeIndex,
  exceptGroupId: string | null = null,
): GroupShare[] {
  const active = activeRules(section, rules);
  const shares: GroupShare[] = [];
  for (const block of section.blocks) {
    if (block.omitted === true || block.id === exceptGroupId) continue;
    let need = 0;
    for (const rule of active) {
      if (rule.groupId !== block.id || !nodesCover(nodeIds, rule.nodeIds, knowledgeIndex)) continue;
      need = Math.max(need, ruleDemand(section, rule));
    }
    if (need > 0) shares.push({ groupId: block.id, need });
  }
  return shares;
}

const total = (shares: readonly GroupShare[]): number => shares.reduce((sum, share) => sum + share.need, 0);

export interface OnlyGroupCap {
  /** Quy tắc "chỉ xuất hiện ở nhóm" bao MAPID của quy tắc toàn môn. */
  rule: MapidRule;
  groupId: string;
  units: number;
}

/** Nhóm nhỏ nhất có quy tắc "chỉ xuất hiện ở nhóm" bao `nodeIds`: toàn môn không lấy quá số câu của nhóm đó (điểm 5). */
function onlyGroupCap(
  section: ExamSection,
  rules: readonly MapidRule[],
  nodeIds: readonly string[],
  knowledgeIndex: KnowledgeIndex,
): OnlyGroupCap | null {
  let best: OnlyGroupCap | null = null;
  for (const rule of activeRules(section, rules)) {
    if (rule.type !== "ONLY_IN_GROUP" || !rule.groupId || !nodesCover(rule.nodeIds, nodeIds, knowledgeIndex)) continue;
    const units = ruleUnits(section, rule);
    if (units === null) continue;
    if (best === null || units < best.units) best = { rule, groupId: rule.groupId, units };
  }
  return best;
}

export interface SubjectBudget {
  shares: GroupShare[];
  /** Tổng phần các nhóm đã lấy. */
  allocated: number;
  onlyGroup: OnlyGroupCap | null;
}

/** Ngân sách của một quy tắc toàn môn: các nhóm đã lấy bao nhiêu câu thuộc MAPID của nó. Không tính chính quy tắc đó. */
export function subjectBudget(section: ExamSection, candidate: MapidRule, knowledgeIndex: KnowledgeIndex): SubjectBudget | null {
  if (candidate.groupId || candidate.nodeIds.length === 0) return null;
  const others = (section.mapidRules ?? []).filter((rule) => rule.id !== candidate.id);
  const shares = groupSharesUnder(section, others, candidate.nodeIds, knowledgeIndex);
  return { shares, allocated: total(shares), onlyGroup: onlyGroupCap(section, others, candidate.nodeIds, knowledgeIndex) };
}

export interface GroupBudget {
  /** Quy tắc toàn môn "đúng N" chặt nhất bao MAPID của quy tắc nhóm. */
  subjectRule: MapidRule;
  exact: number;
  /** Phần các nhóm khác đã lấy trong MAPID của `subjectRule`. */
  others: GroupShare[];
  /** Số câu nhóm này còn lấy được: `exact` trừ phần các nhóm khác (có thể âm khi các nhóm khác đã vượt). */
  remaining: number;
}

/** Ngân sách của một quy tắc nhóm: phần toàn môn còn lại sau các nhóm khác, theo quy tắc toàn môn "đúng N" chặt nhất. */
export function groupBudget(section: ExamSection, candidate: MapidRule, knowledgeIndex: KnowledgeIndex): GroupBudget | null {
  if (!candidate.groupId || candidate.nodeIds.length === 0) return null;
  const others = (section.mapidRules ?? []).filter((rule) => rule.id !== candidate.id);
  let best: GroupBudget | null = null;
  for (const subjectRule of activeRules(section, others)) {
    if (subjectRule.groupId) continue;
    const exact = exactCount(section, subjectRule);
    if (exact === null || !nodesCover(subjectRule.nodeIds, candidate.nodeIds, knowledgeIndex)) continue;
    const shares = groupSharesUnder(section, others, subjectRule.nodeIds, knowledgeIndex, candidate.groupId);
    const remaining = exact - total(shares);
    if (best === null || remaining < best.remaining) best = { subjectRule, exact, others: shares, remaining };
  }
  return best;
}

/** Hợp các nút của quy tắc nguồn toàn môn; `null` khi môn chưa có nguồn (nhóm chọn được mọi MAPID). */
export function subjectSourceNodes(rules: readonly MapidRule[], exceptRuleId: string | null = null): string[] | null {
  const nodes = rules.filter((rule) => !rule.groupId && rule.type === "SOURCE" && rule.id !== exceptRuleId).flatMap((rule) => rule.nodeIds);
  return nodes.length > 0 ? [...new Set(nodes)] : null;
}

/** MAPID mà nhóm `groupId` không được chọn vì quy tắc "chỉ xuất hiện ở nhóm" của nhóm khác (điểm 5). */
export function onlyElsewhereNodes(section: ExamSection, groupId: string, exceptRuleId: string | null = null): string[] {
  const groups = activeGroupIds(section);
  const nodes = (section.mapidRules ?? [])
    .filter((rule) => rule.type === "ONLY_IN_GROUP" && rule.id !== exceptRuleId && rule.groupId && rule.groupId !== groupId && groups.has(rule.groupId))
    .flatMap((rule) => rule.nodeIds);
  return [...new Set(nodes)];
}

/** Quy tắc nhóm có MAPID nằm ngoài nguồn toàn môn `sourceNodes`. */
export function groupRulesOutsideSource(
  section: ExamSection,
  rules: readonly MapidRule[],
  sourceNodes: readonly string[] | null,
  knowledgeIndex: KnowledgeIndex,
): MapidRule[] {
  if (!sourceNodes) return [];
  return activeRules(section, rules).filter((rule) => !!rule.groupId && !nodesCover(sourceNodes, rule.nodeIds, knowledgeIndex));
}

/** Quy tắc nhóm rơi ra ngoài khi lưu `candidate` (quy tắc nguồn toàn môn) thay cho bản hiện có: phải hỏi trước (điểm 4). */
export function groupRulesLeftOutside(section: ExamSection, candidate: MapidRule, knowledgeIndex: KnowledgeIndex): MapidRule[] {
  if (candidate.groupId || candidate.type !== "SOURCE") return [];
  const rules = section.mapidRules ?? [];
  const before = new Set(groupRulesOutsideSource(section, rules, subjectSourceNodes(rules), knowledgeIndex).map((rule) => rule.id));
  const next = [...rules.filter((rule) => rule.id !== candidate.id), candidate];
  return groupRulesOutsideSource(section, next, subjectSourceNodes(next), knowledgeIndex).filter((rule) => !before.has(rule.id));
}

/** Cảnh báo trên dòng quy tắc khi số không còn khớp (sau khi sửa cấu trúc hoặc dữ liệu cũ); vẫn lưu được (điểm 6). */
export type RuleWarning =
  | { code: "SUBJECT_BELOW_GROUPS"; allocated: number; shares: GroupShare[]; fix: number | null }
  | { code: "GROUP_ABOVE_SUBJECT"; subjectRule: MapidRule; remaining: number; fix: number | null }
  | { code: "ABOVE_UNITS"; units: number; fix: number }
  | { code: "ABOVE_ONLY_GROUP"; groupId: string; units: number; fix: number }
  | { code: "OUTSIDE_SUBJECT_SOURCE"; sources: MapidRule[] };

export function mapidRuleWarnings(section: ExamSection, knowledgeIndex: KnowledgeIndex): Map<string, RuleWarning[]> {
  const rules = section.mapidRules ?? [];
  const warnings = new Map<string, RuleWarning[]>();
  const push = (rule: MapidRule, warning: RuleWarning) => warnings.set(rule.id, [...(warnings.get(rule.id) ?? []), warning]);
  const sourceNodes = subjectSourceNodes(rules);
  const outside = new Set(groupRulesOutsideSource(section, rules, sourceNodes, knowledgeIndex).map((rule) => rule.id));
  const sources = rules.filter((rule) => !rule.groupId && rule.type === "SOURCE");

  for (const rule of activeRules(section, rules)) {
    if (outside.has(rule.id)) push(rule, { code: "OUTSIDE_SUBJECT_SOURCE", sources });
    if (!numbered(rule)) {
      if (rule.type === "PRESENT" && rule.groupId) {
        const budget = groupBudget(section, rule, knowledgeIndex);
        if (budget && budget.remaining < 1) push(rule, { code: "GROUP_ABOVE_SUBJECT", subjectRule: budget.subjectRule, remaining: Math.max(0, budget.remaining), fix: null });
      }
      continue;
    }
    const count = ruleCount(section, rule);
    const units = ruleUnits(section, rule);
    const floor = rule.comparator === "EXACT" ? 0 : 1;
    if (units !== null && count > units) {
      push(rule, { code: "ABOVE_UNITS", units, fix: units });
      continue;
    }
    if (!rule.groupId) {
      const budget = subjectBudget(section, rule, knowledgeIndex);
      if (!budget) continue;
      const cap = budget.onlyGroup;
      if (cap && count > cap.units) push(rule, { code: "ABOVE_ONLY_GROUP", groupId: cap.groupId, units: cap.units, fix: cap.units });
      if (rule.comparator === "EXACT" && count < budget.allocated) {
        const ceiling = Math.min(section.questionCount ?? Number.POSITIVE_INFINITY, cap?.units ?? Number.POSITIVE_INFINITY);
        push(rule, { code: "SUBJECT_BELOW_GROUPS", allocated: budget.allocated, shares: budget.shares, fix: budget.allocated <= ceiling ? budget.allocated : null });
      }
      continue;
    }
    const budget = groupBudget(section, rule, knowledgeIndex);
    if (budget && count > budget.remaining) {
      const remaining = Math.max(0, budget.remaining);
      push(rule, { code: "GROUP_ABOVE_SUBJECT", subjectRule: budget.subjectRule, remaining, fix: remaining >= floor ? remaining : null });
    }
  }
  return warnings;
}

/** Áp nút sửa nhanh: đổi số câu của quy tắc, bỏ tỷ lệ (số mới là số câu cố định). */
export function withRuleCount(rule: MapidRule, count: number): MapidRule {
  const next: MapidRule = { ...rule, count };
  delete next.percent;
  return next;
}
