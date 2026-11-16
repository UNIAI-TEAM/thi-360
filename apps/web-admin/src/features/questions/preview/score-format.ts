"use client";

import { useFormatter } from "next-intl";

/** Điểm hiển thị làm tròn nửa lên tới 2 chữ số ở bước cuối (spec mục 8), theo dấu thập phân của ngôn ngữ đang dùng. */
export function useScoreFormat(): (value: number) => string {
  const format = useFormatter();
  return (value) => format.number(value, { maximumFractionDigits: 2 });
}

/**
 * Điểm làm tròn nửa lên 2 chữ số (cùng quy tắc với `useScoreFormat`) dưới dạng số, để lời nhắn ICU chọn số ít/số nhiều
 * theo giá trị đã làm tròn (`toFixed` chọn số lớn hơn khi hòa, như `halfExpand`).
 */
export function roundScore(value: number): number {
  return Number(value.toFixed(2));
}
