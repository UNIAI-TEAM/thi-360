import type { ChoiceGroup } from "./types";

/**
 * Số câu từng môn của một cách chọn môn trong phương án `SUBJECT_CHOICE` (spec B10, D25).
 *
 * `groups` là nhóm của các môn đã chọn theo thứ tự môn trong cấu trúc; `countsByPick` là các số câu của phương án
 * (HSA: 17, 17, 16). Nhóm có nhiều môn được chọn hơn nhận số câu lớn nhất mỗi môn, môn của nhóm còn lại nhận số câu nhỏ
 * nhất. Cả ba môn cùng nhóm thì một môn nhận số câu nhỏ nhất: mỗi môn có thể là môn đó, nên trả về một cách chia cho từng
 * môn (HSA: 3). Kiểm tra cấu hình không biết trước môn nào nên chứng minh mọi cách.
 *
 * Môn chưa đặt nhóm (`null`) cả tổ hợp được coi là một nhóm chung như `ChoiceGroupAllocator` của máy chủ (R3-1): chia như
 * ba môn cùng nhóm. Lẫn môn `null` với môn có nhóm thì không chia được.
 *
 * Trả `[]` khi không chia được: sai số môn so với `countsByPick`, lẫn môn thiếu nhóm với môn có nhóm, hai nhóm bằng nhau,
 * hoặc số câu chia ra không trùng `countsByPick`.
 */
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
