import type { components } from "@thi360/api-client";
import {
  type AttemptItemView,
  buildQuestionUnits,
  type QuestionDisplayUnit,
  toKitQuestion,
} from "../mappers";
import type { QuestionNavItem } from "../question-nav";

export type AttemptDisplaySegmentView = components["schemas"]["AttemptDisplaySegmentView"];

export interface SegmentedQuestionUnit extends QuestionDisplayUnit {
  segment: AttemptDisplaySegmentView | null;
  isSegmentStart: boolean;
  positionOffset: number;
}

export interface NavSegmentGroup {
  segment: AttemptDisplaySegmentView | null;
  items: QuestionNavItem[];
}

function sortSegments(
  displaySegments: readonly AttemptDisplaySegmentView[],
): AttemptDisplaySegmentView[] {
  return [...displaySegments].sort((a, b) => {
    const fromDiff = (a.fromPosition ?? Number.MAX_SAFE_INTEGER) - (b.fromPosition ?? Number.MAX_SAFE_INTEGER);
    if (fromDiff !== 0) return fromDiff;
    return (a.toPosition ?? Number.MAX_SAFE_INTEGER) - (b.toPosition ?? Number.MAX_SAFE_INTEGER);
  });
}

function segmentIdentity(segment: AttemptDisplaySegmentView | null, fallbackIdx = 0): string {
  if (!segment) return "__none__";
  return segment.code ?? `${segment.fromPosition ?? ""}-${segment.toPosition ?? ""}-${fallbackIdx}`;
}

function resolvePartPositionOffset(
  displayNos: readonly number[],
  sortedSegments: readonly AttemptDisplaySegmentView[],
): number {
  if (displayNos.length === 0 || sortedSegments.length === 0) return 0;
  const minDisplayNo = Math.min(...displayNos);
  const minSegFrom = sortedSegments[0]?.fromPosition ?? 1;
  if (minSegFrom === 1 && minDisplayNo > 1) {
    return minDisplayNo - 1;
  }
  return 0;
}

/** Finds the first display segment whose inclusive [fromPosition, toPosition] range contains `pos`. */
export function findSegmentForPosition(
  pos: number,
  sortedSegments: readonly AttemptDisplaySegmentView[],
): AttemptDisplaySegmentView | null {
  for (const seg of sortedSegments) {
    if (
      typeof seg.fromPosition === "number" &&
      typeof seg.toPosition === "number" &&
      pos >= seg.fromPosition &&
      pos <= seg.toPosition
    ) {
      return seg;
    }
  }
  return null;
}

/**
 * Builds question units for the attempt player, splitting any QUESTION_SET whose children span
 * multiple `displaySegments` so every segment retains the shared passage with only its own child items.
 */
export function buildSegmentedQuestionUnits(
  items: AttemptItemView[] | undefined,
  attemptId: string,
  displaySegments?: readonly AttemptDisplaySegmentView[],
): SegmentedQuestionUnit[] {
  const baseUnits = buildQuestionUnits(items, attemptId);
  if (!displaySegments || displaySegments.length === 0) {
    return baseUnits.map((unit) => ({
      ...unit,
      segment: null,
      isSegmentStart: false,
      positionOffset: 0,
    }));
  }

  const sortedSegments = sortSegments(displaySegments);
  const allLeafNos = baseUnits.flatMap((u) =>
    u.leafItems.map((leaf) => leaf.displayNo ?? u.displayStart),
  );
  const offset = resolvePartPositionOffset(allLeafNos, sortedSegments);

  const rawUnits: Array<Omit<SegmentedQuestionUnit, "isSegmentStart">> = [];

  for (const unit of baseUnits) {
    if (unit.kind === "LEAF") {
      const pos = (unit.parentItem.displayNo ?? unit.displayStart) - offset;
      rawUnits.push({
        ...unit,
        segment: findSegmentForPosition(pos, sortedSegments),
        positionOffset: offset,
      });
      continue;
    }

    const grouped = new Map<
      string,
      { segment: AttemptDisplaySegmentView | null; children: AttemptItemView[] }
    >();
    for (const child of unit.childItems) {
      const pos = (child.displayNo ?? unit.displayStart) - offset;
      const seg = findSegmentForPosition(pos, sortedSegments);
      const key = segmentIdentity(seg);
      const bucket = grouped.get(key);
      if (bucket) {
        bucket.children.push(child);
      } else {
        grouped.set(key, { segment: seg, children: [child] });
      }
    }

    if (grouped.size <= 1) {
      const only = grouped.values().next().value;
      rawUnits.push({
        ...unit,
        segment: only?.segment ?? null,
        positionOffset: offset,
      });
      continue;
    }

    let sliceIdx = 0;
    for (const { segment: seg, children: sliceChildren } of grouped.values()) {
      const sliceChildIds = sliceChildren.map((c) => c.itemId ?? "").filter(Boolean);
      const sliceChildKits = sliceChildren.map((c) => toKitQuestion(c, attemptId));
      const leafDisplays = sliceChildren.map((c) => c.displayNo ?? 0).filter((n) => n > 0);
      const displayStart = leafDisplays.length > 0 ? Math.min(...leafDisplays) : unit.displayStart;
      const displayEnd = leafDisplays.length > 0 ? Math.max(...leafDisplays) : displayStart;
      rawUnits.push({
        id: `${unit.id}::segment::${seg?.code ?? sliceIdx}`,
        kind: "QUESTION_SET",
        parentItem: unit.parentItem,
        childItems: sliceChildren,
        leafItems: sliceChildren,
        kitQuestion: toKitQuestion(unit.parentItem, attemptId, sliceChildIds),
        childKitQuestions: sliceChildKits,
        attachments: unit.attachments,
        displayStart,
        displayEnd,
        segment: seg,
        positionOffset: offset,
      });
      sliceIdx += 1;
    }
  }

  let prevSegKey: string | null = null;
  return rawUnits.map((unit) => {
    const segKey = unit.segment ? segmentIdentity(unit.segment) : null;
    const isSegmentStart = unit.segment !== null && segKey !== prevSegKey;
    prevSegKey = segKey;
    return {
      ...unit,
      isSegmentStart,
    };
  });
}

/**
 * Groups question nav items by their matching display segment while filtering out empty segments
 * and preserving any out-of-range items in a trailing fallback bucket.
 */
export function groupNavItemsBySegment(
  items: readonly QuestionNavItem[],
  displaySegments?: readonly AttemptDisplaySegmentView[],
  allPartItems?: readonly QuestionNavItem[],
): NavSegmentGroup[] {
  if (!displaySegments || displaySegments.length === 0) {
    return [];
  }

  const sortedSegments = sortSegments(displaySegments);
  const referenceItems = allPartItems ?? items;
  const offset = resolvePartPositionOffset(
    referenceItems.map((i) => i.displayNo),
    sortedSegments,
  );

  const buckets = new Map<string, NavSegmentGroup>();
  for (let i = 0; i < sortedSegments.length; i++) {
    const seg = sortedSegments[i]!;
    buckets.set(segmentIdentity(seg, i), { segment: seg, items: [] });
  }
  const ungrouped: QuestionNavItem[] = [];

  for (const item of items) {
    const pos = item.displayNo - offset;
    const matched = findSegmentForPosition(pos, sortedSegments);
    if (!matched) {
      ungrouped.push(item);
      continue;
    }
    const matchedIndex = sortedSegments.indexOf(matched);
    const key = segmentIdentity(matched, matchedIndex);
    buckets.get(key)?.items.push(item);
  }

  const groups: NavSegmentGroup[] = [];
  for (const group of buckets.values()) {
    if (group.items.length > 0) {
      groups.push(group);
    }
  }
  if (ungrouped.length > 0) {
    groups.push({ segment: null, items: ungrouped });
  }
  return groups;
}
