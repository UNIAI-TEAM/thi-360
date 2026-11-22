import type { components } from "@thi360/api-client";
import type { MyExamCardData } from "./api";

export type MyExamBundleRef = components["schemas"]["MyExamBundleRef"];

export interface BundleCrossReference {
  examCode: string;
  examName: string;
  primaryBundle: MyExamBundleRef;
}

export interface MyExamBundleGroup {
  bundle: MyExamBundleRef;
  cards: MyExamCardData[];
  crossReferences: BundleCrossReference[];
}

export interface GroupedMyExams {
  bundleGroups: MyExamBundleGroup[];
  standaloneCards: MyExamCardData[];
}

/**
 * Nhóm các thẻ bài đã mua trên `/home` theo bộ đề (TM 16.4, AC-14):
 * - Thẻ không thuộc bộ nào nằm ở `standaloneCards`.
 * - Thẻ thuộc một hoặc nhiều bộ được đặt thẻ đầy đủ đúng một lần trong bộ đầu tiên (`bundles[0]`,
 *   bộ mua gần nhất theo CM-6); các bộ còn lại nhận dòng dẫn chiếu tới bộ chính.
 */
export function groupMyExams(ownedCards: readonly MyExamCardData[]): GroupedMyExams {
  const groupMap = new Map<string, MyExamBundleGroup>();
  const standaloneCards: MyExamCardData[] = [];

  const ensureGroup = (ref: MyExamBundleRef): MyExamBundleGroup => {
    const existing = groupMap.get(ref.bundleId);
    if (existing) return existing;
    const created: MyExamBundleGroup = { bundle: ref, cards: [], crossReferences: [] };
    groupMap.set(ref.bundleId, created);
    return created;
  };

  for (const card of ownedCards) {
    const bundles = card.bundles;
    if (!bundles || bundles.length === 0) {
      standaloneCards.push(card);
      continue;
    }

    const primaryBundle = bundles[0]!;
    ensureGroup(primaryBundle).cards.push(card);

    const seenBundleIds = new Set<string>([primaryBundle.bundleId]);
    for (const secondaryBundle of bundles.slice(1)) {
      if (seenBundleIds.has(secondaryBundle.bundleId)) continue;
      seenBundleIds.add(secondaryBundle.bundleId);
      ensureGroup(secondaryBundle).crossReferences.push({
        examCode: card.code,
        examName: card.name,
        primaryBundle,
      });
    }
  }

  return {
    bundleGroups: Array.from(groupMap.values()),
    standaloneCards,
  };
}
