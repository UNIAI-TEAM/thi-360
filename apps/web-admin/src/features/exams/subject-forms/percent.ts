import { apportionByPercent } from "../matrix";

/** Quy đổi cả một bảng chia theo một chiều: các dòng cùng chia chung `total` câu. */
export function percentsToCounts(percents: readonly number[], total: number): number[] {
  return apportionByPercent(percents, total).results;
}

/** Quy đổi một dòng đứng riêng (dòng DS, dòng chỉ tiêu chủ đề, dòng bảng mạch) trên mẫu số riêng của nó. */
export function percentToCount(percent: number, total: number): number {
  return apportionByPercent([percent], total).results[0] ?? 0;
}

/** Bảng chia cả mục theo một chiều chỉ quy ra số khi tổng đúng 100%. */
export function percentsAreComplete(percents: readonly number[]): boolean {
  return Math.abs(percents.reduce((sum, value) => sum + value, 0) - 100) < 1e-6;
}

export function wholeSetCount(count: number, setSize: number): { ok: true; sets: number } | { ok: false } {
  if (!Number.isInteger(count) || !Number.isInteger(setSize) || setSize <= 0 || count % setSize !== 0) return { ok: false };
  return { ok: true, sets: count / setSize };
}
