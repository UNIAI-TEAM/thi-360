"use client";

import { Button } from "@thi360/ui";
import { CircleAlert, CircleCheck, Scale } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { samePoints } from "../editor-form";

/**
 * Dòng "Tổng điểm các ý / cặp: x / y" của câu Đúng/Sai (cộng điểm từng ý) và Ghép đôi: tổng điểm thành phần phải bằng
 * điểm của câu (spec 4.3). `points` là `NaN` khi ô Điểm của câu chưa hợp lệ: chỉ hiện tổng. Nút "Chia đều" chia lại
 * điểm của câu cho các thành phần.
 */
export function PointsTotalLine({
  total,
  points,
  count,
  onEven,
}: {
  total: number;
  points: number;
  count: number;
  onEven: () => void;
}) {
  const t = useTranslations("questions.editor.pointsTotal");
  const format = useFormatter();
  const known = Number.isFinite(points) && points > 0;
  const matches = known && samePoints(total, points);
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="flex items-center gap-1.5 text-caption font-medium text-text-secondary">
        {known &&
          (matches ? (
            <CircleCheck aria-hidden className="size-4 text-success" />
          ) : (
            <CircleAlert aria-hidden className="size-4 text-warning" />
          ))}
        {known
          ? t(matches ? "matches" : "differs", { total: format.number(total), points: format.number(points) })
          : t("totalOnly", { total: format.number(total) })}
      </p>
      <Button type="button" variant="outline" size="sm" disabled={count === 0 || !known} onClick={onEven}>
        <Scale aria-hidden />
        {t("even")}
      </Button>
    </div>
  );
}
