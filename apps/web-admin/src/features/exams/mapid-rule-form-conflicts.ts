import type { KnowledgeIndexEntry } from "@/features/content/knowledge-tree";
import { ruleCount, ruleUnits } from "./mapid-rules";
import type { ExamSection, MapidRule } from "./types";

type MapidRuleConflictCode =
  | "CAPACITY_EXCEEDED"
  | "EXACT_COUNTS_DIFFER"
  | "EXACT_BELOW_MINIMUM"
  | "SUBJECT_EXACT_BELOW_GROUP_MINIMUM"
  | "GROUP_MINIMUMS_ABOVE_SUBJECT_EXACT"
  | "ONLY_IN_GROUP_EXCLUDES_OTHER_GROUP"
  | "ONLY_IN_GROUP_OVERLAPS_OTHER_GROUP"
  | "ONLY_IN_GROUP_EXCLUDES_SOURCE"
  | "EXACT_ZERO_CONFLICTS_SOURCE";

type MapidRuleSubsumptionReason =
  | "EXACT_COVERS_PRESENT"
  | "AT_LEAST_COVERS_PRESENT"
  | "COVERS_PRESENT"
  | "EXACT_COVERS_RULE"
  | "AT_LEAST_COVERS_RULE"
  | "ONLY_IN_GROUP_COVERS_UNNUMBERED"
  | "RULE_COVERS_RULE";

type MapidRuleCheckResult =
  | { kind: "OK" }
  | {
      kind: "CONFLICT";
      code: MapidRuleConflictCode;
      /** Active existing rules involved in the conflict (excluding candidate.id), in declaration order. */
      relatedRules: MapidRule[];
      /** Whole-subject EXACT rule when code is GROUP_MINIMUMS_ABOVE_SUBJECT_EXACT. */
      subjectRule?: MapidRule;
      scopeGroupId: string | null;
      /** Group carrying the ONLY_IN_GROUP rule (or canonical first group when two ONLY_IN_GROUP rules overlap). */
      onlyGroupId?: string | null;
      /** Conflicting peer group in cross-group ONLY_IN_GROUP_* conflicts. */
      otherGroupId?: string | null;
      required?: number;
      available?: number;
      nodeIds: string[];
    }
  | {
      kind: "EQUIVALENT";
      relatedRules: MapidRule[];
    }
  | {
      kind: "SUBSUMED_BY";
      relatedRules: MapidRule[];
    }
  | {
      kind: "SUBSUMES";
      reason: MapidRuleSubsumptionReason;
      relatedRules: MapidRule[];
      count?: number;
    };

/** Returns true when `wideId` equals `narrowId` or is an ancestor of `narrowId` in `knowledgeIndex`. */
export function nodeCovers(
  wideId: string,
  narrowId: string,
  knowledgeIndex: ReadonlyMap<string, KnowledgeIndexEntry>,
): boolean {
  if (wideId === narrowId) return true;
  const entry = knowledgeIndex.get(narrowId);
  return entry ? entry.ancestors.some((ancestor) => ancestor.id === wideId) : false;
}

/** Returns true when every node in `narrowNodes` is covered by at least one node in `wideNodes`. */
export function nodesCover(
  wideNodes: readonly string[],
  narrowNodes: readonly string[],
  knowledgeIndex: ReadonlyMap<string, KnowledgeIndexEntry>,
): boolean {
  if (narrowNodes.length === 0 || wideNodes.length === 0) return false;
  return narrowNodes.every((narrowId) => wideNodes.some((wideId) => nodeCovers(wideId, narrowId, knowledgeIndex)));
}

/** Returns true when `a` and `b` cover each other (match the exact same set of questions). */
export function nodesEquivalent(
  a: readonly string[],
  b: readonly string[],
  knowledgeIndex: ReadonlyMap<string, KnowledgeIndexEntry>,
): boolean {
  return nodesCover(a, b, knowledgeIndex) && nodesCover(b, a, knowledgeIndex);
}

/** Returns true when some node in `a` and some node in `b` share an ancestor/descendant (or equal) relation. */
export function nodesOverlap(
  a: readonly string[],
  b: readonly string[],
  knowledgeIndex: ReadonlyMap<string, KnowledgeIndexEntry>,
): boolean {
  return a.some((x) => b.some((y) => nodeCovers(x, y, knowledgeIndex) || nodeCovers(y, x, knowledgeIndex)));
}

interface NormalizedRule {
  rule: MapidRule;
  groupId: string | null;
  nodeIds: string[];
  hasNumber: boolean;
  min: number;
  exact: number | null;
  units: number | null;
  nonEmpty: boolean;
}

function normalizeRule(rule: MapidRule, section: ExamSection): NormalizedRule {
  const groupId = rule.groupId ?? null;
  const hasNumber = rule.type === "COUNT" || (rule.type === "ONLY_IN_GROUP" && (rule.percent != null || rule.count != null));
  const min =
    rule.type === "PRESENT"
      ? 1
      : rule.type === "COUNT"
        ? ruleCount(section, rule)
        : rule.type === "ONLY_IN_GROUP"
          ? hasNumber
            ? ruleCount(section, rule)
            : 0
          : 0;
  const exact = hasNumber && rule.comparator === "EXACT" ? min : null;
  const units = groupId === null ? section.questionCount : ruleUnits(section, { groupId });
  const nonEmpty = units === null || units > 0;
  return { rule, groupId, nodeIds: rule.nodeIds, hasNumber, min, exact, units, nonEmpty };
}

const isCountOrPresent = (item: NormalizedRule): boolean => item.rule.type === "PRESENT" || item.rule.type === "COUNT";
const isMinCapable = (item: NormalizedRule): boolean => isCountOrPresent(item) || item.rule.type === "ONLY_IN_GROUP";

export function checkMapidRuleConflicts({
  candidate,
  section,
  knowledgeIndex,
}: {
  candidate: MapidRule;
  section: ExamSection;
  knowledgeIndex: ReadonlyMap<string, KnowledgeIndexEntry>;
}): MapidRuleCheckResult {
  const activeBlockIds = new Set(section.blocks.filter((block) => block.omitted !== true).map((block) => block.id));
  if (candidate.nodeIds.length === 0) return { kind: "OK" };
  if (candidate.groupId && !activeBlockIds.has(candidate.groupId)) return { kind: "OK" };
  if (candidate.type === "ONLY_IN_GROUP" && !candidate.groupId) return { kind: "OK" };

  const cand = normalizeRule(candidate, section);
  const existing = (section.mapidRules ?? [])
    .filter((rule) => rule.id !== candidate.id && rule.nodeIds.length > 0 && (!rule.groupId || activeBlockIds.has(rule.groupId)))
    .map((rule) => normalizeRule(rule, section));

  // 1a. Capacity exceeded
  if (cand.units !== null && cand.min > cand.units) {
    return {
      kind: "CONFLICT",
      code: "CAPACITY_EXCEEDED",
      relatedRules: [],
      scopeGroupId: cand.groupId,
      required: cand.min,
      available: cand.units,
      nodeIds: cand.nodeIds,
    };
  }
  if (section.questionCount !== null && cand.min > section.questionCount) {
    return {
      kind: "CONFLICT",
      code: "CAPACITY_EXCEEDED",
      relatedRules: [],
      scopeGroupId: null,
      required: cand.min,
      available: section.questionCount,
      nodeIds: cand.nodeIds,
    };
  }

  // 1b. Same scope EXACT vs EXACT / AT_LEAST / PRESENT (both directions)
  for (const e of existing) {
    if (e.groupId !== cand.groupId) continue;
    if (cand.exact !== null && e.min > cand.exact && nodesCover(cand.nodeIds, e.nodeIds, knowledgeIndex)) {
      return {
        kind: "CONFLICT",
        code: e.exact !== null ? "EXACT_COUNTS_DIFFER" : "EXACT_BELOW_MINIMUM",
        relatedRules: [e.rule],
        scopeGroupId: cand.groupId,
        required: e.min,
        available: cand.exact,
        nodeIds: cand.nodeIds,
      };
    }
    if (e.exact !== null && cand.min > e.exact && nodesCover(e.nodeIds, cand.nodeIds, knowledgeIndex)) {
      return {
        kind: "CONFLICT",
        code: cand.exact !== null ? "EXACT_COUNTS_DIFFER" : "EXACT_BELOW_MINIMUM",
        relatedRules: [e.rule],
        scopeGroupId: cand.groupId,
        required: cand.min,
        available: e.exact,
        nodeIds: cand.nodeIds,
      };
    }
  }

  // 1c. Subject EXACT vs single Group minimum (both directions)
  if (cand.groupId === null && cand.exact !== null) {
    for (const e of existing) {
      if (e.groupId !== null && e.min > cand.exact && nodesCover(cand.nodeIds, e.nodeIds, knowledgeIndex)) {
        return {
          kind: "CONFLICT",
          code: "SUBJECT_EXACT_BELOW_GROUP_MINIMUM",
          relatedRules: [e.rule],
          scopeGroupId: e.groupId,
          required: e.min,
          available: cand.exact,
          nodeIds: cand.nodeIds,
        };
      }
    }
  }
  if (cand.groupId !== null && cand.min > 0) {
    for (const e of existing) {
      if (e.groupId === null && e.exact !== null && cand.min > e.exact && nodesCover(e.nodeIds, cand.nodeIds, knowledgeIndex)) {
        return {
          kind: "CONFLICT",
          code: "SUBJECT_EXACT_BELOW_GROUP_MINIMUM",
          relatedRules: [e.rule],
          scopeGroupId: cand.groupId,
          required: cand.min,
          available: e.exact,
          nodeIds: cand.nodeIds,
        };
      }
    }
  }

  // 1d. Subject EXACT vs sum of DISTINCT Groups' minimums (order-independent)
  const allRules = [...existing, cand];
  for (const subjectRule of allRules) {
    if (subjectRule.groupId !== null || subjectRule.exact === null) continue;
    const bestByGroup = new Map<string, { maxMin: number; rules: NormalizedRule[] }>();
    for (const groupRule of allRules) {
      if (groupRule.groupId === null || groupRule.min <= 0 || !nodesCover(subjectRule.nodeIds, groupRule.nodeIds, knowledgeIndex)) {
        continue;
      }
      const entry = bestByGroup.get(groupRule.groupId);
      if (!entry) {
        bestByGroup.set(groupRule.groupId, { maxMin: groupRule.min, rules: [groupRule] });
      } else if (groupRule.min > entry.maxMin) {
        bestByGroup.set(groupRule.groupId, { maxMin: groupRule.min, rules: [groupRule] });
      } else if (groupRule.min === entry.maxMin) {
        entry.rules.push(groupRule);
      }
    }
    if (bestByGroup.size < 2) continue;
    let total = 0;
    for (const entry of bestByGroup.values()) total += entry.maxMin;
    const candidateContributes =
      cand.rule.id === subjectRule.rule.id ||
      Array.from(bestByGroup.values()).some((entry) => entry.rules.some((item) => item.rule.id === cand.rule.id));
    if (total > subjectRule.exact && candidateContributes) {
      const related: MapidRule[] = [];
      if (subjectRule.rule.id !== cand.rule.id) related.push(subjectRule.rule);
      for (const entry of bestByGroup.values()) {
        if (entry.rules.some((item) => item.rule.id === cand.rule.id)) continue;
        related.push(entry.rules[0]!.rule);
      }
      return {
        kind: "CONFLICT",
        code: "GROUP_MINIMUMS_ABOVE_SUBJECT_EXACT",
        relatedRules: related,
        subjectRule: subjectRule.rule,
        scopeGroupId: null,
        required: total,
        available: subjectRule.exact,
        nodeIds: subjectRule.nodeIds,
      };
    }
  }

  // 1e. ONLY_IN_GROUP in group A vs another group B (B !== A), both directions
  for (const e of existing) {
    if (
      cand.rule.type === "ONLY_IN_GROUP" &&
      e.rule.type === "ONLY_IN_GROUP" &&
      cand.groupId !== null &&
      e.groupId !== null &&
      cand.groupId !== e.groupId &&
      nodesOverlap(cand.nodeIds, e.nodeIds, knowledgeIndex)
    ) {
      const candCovers = e.min >= 1 && nodesCover(cand.nodeIds, e.nodeIds, knowledgeIndex);
      const eCovers = cand.min >= 1 && nodesCover(e.nodeIds, cand.nodeIds, knowledgeIndex);
      if (candCovers || eCovers) {
        const [only, other] =
          candCovers && eCovers
            ? e.min > cand.min || (e.min === cand.min && e.groupId.localeCompare(cand.groupId) > 0)
              ? [cand, e]
              : [e, cand]
            : candCovers
              ? [cand, e]
              : [e, cand];
        return {
          kind: "CONFLICT",
          code: "ONLY_IN_GROUP_EXCLUDES_OTHER_GROUP",
          relatedRules: [e.rule],
          scopeGroupId: other.groupId,
          onlyGroupId: only.groupId,
          otherGroupId: other.groupId,
          required: other.min,
          nodeIds: only.nodeIds,
        };
      }
      const candCoversE = nodesCover(cand.nodeIds, e.nodeIds, knowledgeIndex);
      const eCoversCand = nodesCover(e.nodeIds, cand.nodeIds, knowledgeIndex);
      const [first, second] =
        candCoversE !== eCoversCand
          ? candCoversE
            ? [cand, e]
            : [e, cand]
          : cand.groupId.localeCompare(e.groupId) <= 0
            ? [cand, e]
            : [e, cand];
      return {
        kind: "CONFLICT",
        code: "ONLY_IN_GROUP_OVERLAPS_OTHER_GROUP",
        relatedRules: [e.rule],
        scopeGroupId: second.groupId,
        onlyGroupId: first.groupId,
        otherGroupId: second.groupId,
        nodeIds: first.nodeIds,
      };
    }
    const pairs: Array<[NormalizedRule, NormalizedRule]> = [
      [cand, e],
      [e, cand],
    ];
    for (const [only, other] of pairs) {
      if (only.rule.type !== "ONLY_IN_GROUP" || only.groupId === null || other.groupId === null || other.groupId === only.groupId) {
        continue;
      }
      if (isMinCapable(other) && other.min >= 1 && nodesCover(only.nodeIds, other.nodeIds, knowledgeIndex)) {
        return {
          kind: "CONFLICT",
          code: "ONLY_IN_GROUP_EXCLUDES_OTHER_GROUP",
          relatedRules: [e.rule],
          scopeGroupId: other.groupId,
          onlyGroupId: only.groupId,
          otherGroupId: other.groupId,
          required: other.min,
          nodeIds: only.nodeIds,
        };
      }
      if (other.rule.type === "SOURCE" && other.nonEmpty && nodesCover(only.nodeIds, other.nodeIds, knowledgeIndex)) {
        return {
          kind: "CONFLICT",
          code: "ONLY_IN_GROUP_EXCLUDES_SOURCE",
          relatedRules: [e.rule],
          scopeGroupId: other.groupId,
          onlyGroupId: only.groupId,
          otherGroupId: other.groupId,
          nodeIds: other.nodeIds,
        };
      }
    }
  }

  // 1f. EXACT 0 vs SOURCE in same non-empty scope, Subject EXACT 0 vs non-empty Group SOURCE, or Subject SOURCE vs non-empty Group EXACT 0
  for (const e of existing) {
    const pairs: Array<[NormalizedRule, NormalizedRule]> = [
      [cand, e],
      [e, cand],
    ];
    for (const [zero, src] of pairs) {
      if (
        zero.exact === 0 &&
        src.rule.type === "SOURCE" &&
        ((zero.groupId === src.groupId && src.nonEmpty) ||
          (zero.groupId === null && src.nonEmpty) ||
          (src.groupId === null && zero.nonEmpty)) &&
        nodesCover(zero.nodeIds, src.nodeIds, knowledgeIndex)
      ) {
        return {
          kind: "CONFLICT",
          code: "EXACT_ZERO_CONFLICTS_SOURCE",
          relatedRules: [e.rule],
          scopeGroupId: zero.groupId ?? src.groupId,
          nodeIds: src.nodeIds,
        };
      }
    }
  }

  // 2. EQUIVALENT (same scope & equivalent nodes)
  const equivalents = existing.filter((e) => {
    if (e.groupId !== cand.groupId || !nodesEquivalent(cand.nodeIds, e.nodeIds, knowledgeIndex)) return false;
    if (isCountOrPresent(cand) && isCountOrPresent(e)) return cand.min === e.min && cand.exact === e.exact;
    if (cand.rule.type === "SOURCE" && e.rule.type === "SOURCE") return true;
    if (cand.rule.type === "ONLY_IN_GROUP" && e.rule.type === "ONLY_IN_GROUP") return cand.min === e.min && cand.exact === e.exact;
    return false;
  });
  if (equivalents.length > 0) {
    return { kind: "EQUIVALENT", relatedRules: equivalents.map((item) => item.rule) };
  }

  // 3. SUBSUMED_BY (existing stricter rule in same scope covers candidate)
  const subsumedBy = existing.filter((e) => {
    if (e.groupId !== cand.groupId) return false;
    if (isCountOrPresent(cand) && cand.exact === null && isMinCapable(e)) {
      return e.min >= cand.min && nodesCover(cand.nodeIds, e.nodeIds, knowledgeIndex);
    }
    if (
      cand.rule.type === "COUNT" &&
      cand.exact !== null &&
      e.rule.type === "ONLY_IN_GROUP" &&
      e.exact === cand.exact &&
      nodesEquivalent(cand.nodeIds, e.nodeIds, knowledgeIndex)
    ) {
      return true;
    }
    if (cand.rule.type === "ONLY_IN_GROUP" && cand.exact === null && e.rule.type === "ONLY_IN_GROUP") {
      return e.min >= cand.min && (e.hasNumber || e.min > cand.min) && nodesEquivalent(cand.nodeIds, e.nodeIds, knowledgeIndex);
    }
    return false;
  });
  if (subsumedBy.length > 0) {
    return { kind: "SUBSUMED_BY", relatedRules: subsumedBy.map((item) => item.rule) };
  }

  // 4. SUBSUMES (candidate is stricter than one or more existing weaker rules in same scope)
  const subsumes = existing.filter((e) => {
    if (e.groupId !== cand.groupId) return false;
    if (isCountOrPresent(e) && e.exact === null && isMinCapable(cand)) {
      return cand.min >= e.min && nodesCover(e.nodeIds, cand.nodeIds, knowledgeIndex);
    }
    if (
      e.rule.type === "COUNT" &&
      e.exact !== null &&
      cand.rule.type === "ONLY_IN_GROUP" &&
      cand.exact === e.exact &&
      nodesEquivalent(cand.nodeIds, e.nodeIds, knowledgeIndex)
    ) {
      return true;
    }
    if (e.rule.type === "ONLY_IN_GROUP" && e.exact === null && cand.rule.type === "ONLY_IN_GROUP") {
      return cand.min >= e.min && (cand.hasNumber || cand.min > e.min) && nodesEquivalent(e.nodeIds, cand.nodeIds, knowledgeIndex);
    }
    return false;
  });
  if (subsumes.length > 0) {
    const coversPresent = subsumes.some((item) => item.rule.type === "PRESENT");
    const onlyUnnumbered =
      cand.rule.type === "ONLY_IN_GROUP" && subsumes.every((item) => item.rule.type === "ONLY_IN_GROUP" && !item.hasNumber);
    let reason: MapidRuleSubsumptionReason;
    let count: number | undefined;
    if (coversPresent) {
      if (cand.exact !== null) {
        reason = "EXACT_COVERS_PRESENT";
        count = cand.exact;
      } else if (cand.rule.type === "COUNT" || (cand.rule.type === "ONLY_IN_GROUP" && cand.hasNumber)) {
        reason = "AT_LEAST_COVERS_PRESENT";
        count = cand.min;
      } else {
        reason = "COVERS_PRESENT";
      }
    } else if (onlyUnnumbered) {
      reason = "ONLY_IN_GROUP_COVERS_UNNUMBERED";
      count = cand.min;
    } else if (cand.exact !== null) {
      reason = "EXACT_COVERS_RULE";
      count = cand.exact;
    } else if (cand.rule.type === "COUNT" || (cand.rule.type === "ONLY_IN_GROUP" && cand.hasNumber)) {
      reason = "AT_LEAST_COVERS_RULE";
      count = cand.min;
    } else {
      reason = "RULE_COVERS_RULE";
    }
    return { kind: "SUBSUMES", reason, relatedRules: subsumes.map((item) => item.rule), ...(count !== undefined ? { count } : {}) };
  }

  return { kind: "OK" };
}
