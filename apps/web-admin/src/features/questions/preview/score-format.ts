"use client";

import { useFormatter } from "next-intl";

/** Điểm hiển thị làm tròn nửa lên tới 2 chữ số ở bước cuối (spec mục 8), theo dấu thập phân của ngôn ngữ đang dùng. */
export function useScoreFormat(): (value: number) => string {
  const format = useFormatter();
  return (value) => format.number(value, { maximumFractionDigits: 2 });
}
