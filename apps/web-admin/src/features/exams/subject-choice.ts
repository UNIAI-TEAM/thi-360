import type { ChoiceGroup } from "./types";

/**
 * Số câu từng môn của một cách chọn môn trong phương án `SUBJECT_CHOICE` (spec B10, D25). Đây là MỘT hàm riêng chứa
 * quy tắc chia, để đổi được khi BA chốt phần còn là đề xuất (ba môn cùng nhóm tự nhiên).
 *
 * `groups` là nhóm của các môn đã chọn theo thứ tự môn trong cấu trúc; `countsByPick` là các số câu của phương án
 * (HSA: 17, 17, 16). Nhóm có nhiều môn được chọn hơn nhận số câu lớn nhất mỗi môn, môn của nhóm còn lại nhận số câu nhỏ
 * nhất. Cả ba môn cùng nhóm thì một môn nhận số câu nhỏ nhất: mỗi môn có thể là môn đó, nên trả về một cách chia cho từng
 * môn (HSA: 3). Lần sinh đề chọn một cách theo hạt giống (`pickAllocation`); kiểm tra cấu hình không biết trước môn nào
 * nên chứng minh mọi cách.
 *
 * Trả `[]` khi không chia được: sai số môn so với `countsByPick`, có môn thiếu nhóm, hai nhóm bằng nhau, hoặc số câu chia
 * ra không trùng `countsByPick`.
 */
export function allocateSubjectCounts(
  groups: readonly (ChoiceGroup | null)[],
  countsByPick: readonly number[],
): number[][] {
  const picked = groups.length;
  if (picked === 0 || countsByPick.length !== picked || groups.some((group) => group === null)) return [];
  const high = Math.max(...countsByPick);
  const low = Math.min(...countsByPick);
  const natural = groups.filter((group) => group === "NATURAL").length;
  const social = picked - natural;

  const allocations: number[][] =
    natural === 0 || social === 0
      ? groups.map((_, lowIndex) => groups.map((__, index) => (index === lowIndex ? low : high)))
      : natural === social
        ? []
        : [groups.map((group) => ((natural > social ? group === "NATURAL" : group === "SOCIAL") ? high : low))];

  const expected = [...countsByPick].sort((a, b) => a - b).join();
  return allocations.filter((counts) => [...counts].sort((a, b) => a - b).join() === expected);
}

/** Chọn một cách chia theo hạt giống của lần sinh đề: `random` là số ngẫu nhiên có hạt giống trong [0, 1). */
export function pickAllocation(allocations: readonly number[][], random: () => number): number[] | null {
  if (allocations.length === 0) return null;
  return allocations[Math.min(allocations.length - 1, Math.floor(random() * allocations.length))]!;
}
