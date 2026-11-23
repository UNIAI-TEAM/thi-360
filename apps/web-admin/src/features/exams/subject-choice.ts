import type { ChoiceGroup } from "./types";

export function allocateSubjectCounts(
  groups: readonly (ChoiceGroup | null)[],
  countsByPick: readonly number[],
): number[][] {
  const picked = groups.length;
  const ungrouped = groups.filter((group) => group === null).length;
  if (picked === 0 || countsByPick.length !== picked || (ungrouped > 0 && ungrouped < picked)) return [];
  const high = Math.max(...countsByPick);
  const low = Math.min(...countsByPick);
  const natural = groups.filter((group) => group === "NATURAL").length;
  const social = picked - ungrouped - natural;

  const allocations: number[][] =
    natural === 0 || social === 0
      ? groups.map((_, lowIndex) => groups.map((__, index) => (index === lowIndex ? low : high)))
      : natural === social
        ? []
        : [groups.map((group) => ((natural > social ? group === "NATURAL" : group === "SOCIAL") ? high : low))];

  const expected = [...countsByPick].sort((a, b) => a - b).join();
  return allocations.filter((counts) => [...counts].sort((a, b) => a - b).join() === expected);
}
