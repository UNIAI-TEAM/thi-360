"use client";

import { cn, Input, Label } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { ratePercent } from "../allocation";
import { formatNumberText, parseNumberText, parsePercentText } from "../components/block-number-input";
import { LOCKED_FIELD } from "../components/locked-field";

interface MatrixCellProps {
  /** Nhãn của ô (tên khối lớp, độ khó hay dòng của Cấu trúc câu). */
  label: string;
  /** `data-form-field` của ô: đích của lỗi và của `navigation.ts`. */
  field: string;
  /** Số đang lưu của ô; `null`: ô để trống. */
  stored: number | null;
  /** Số câu quy ra của ô: khi lưu theo %, các ô của một hàng quy chung bằng phần dư lớn nhất. */
  count: number | null;
  /** Đơn vị của giá trị đang lưu (`PERCENT`: `stored` là %). */
  unit: "COUNT" | "PERCENT";
  /** Mẫu số của tỷ lệ (số câu của mục); `null` khi chưa biết. */
  total: number | null;
  /** Hai cột Số câu / Tỷ lệ (V-ACT, R-9); không thì một ô số câu kèm % chỉ đọc bên cạnh. */
  twoColumns: boolean;
  /** Ô chỉ đọc (mẫu khóa số câu của mục, H27). */
  readOnly?: boolean;
  /** Giá trị hợp lệ vừa nhập: `unit` là cột đã gõ, `value` `null` khi xóa ô. */
  onEdit: (edit: { unit: "COUNT" | "PERCENT"; value: number | null }) => void;
  /** Báo chữ chưa ghi được (`null`: ô đã hợp lệ, xóa mục nhập thô). Mã `invalidPercent` cho cột Tỷ lệ, `invalidNumber` cho cột Số câu. */
  onRaw: (raw: { text: string; code: string } | null) => void;
  /** Mục nhập thô đang giữ ở ô (kho nhập thô của view, R-15): ô mở lại hiện đúng chữ đã gõ và lỗi, không hiện lại số đã lưu. */
  raw?: { text: string; code: string } | null;
}

/**
 * Một ô số của MT: ô của khối lớp, độ khó hay dòng Cấu trúc câu. Ô trống là ô chưa khai (không ghi), "0" là ô khai 0. Hai cột Số câu /
 * Tỷ lệ (V-ACT, R-9): gõ ở cột nào thì ghi theo đơn vị đó và cột kia hiện số quy đổi (cột Tỷ lệ chưa gõ hiện % tính từ số làm gợi ý);
 * các kỳ khác một ô số câu kèm % chỉ đọc bên cạnh. Số không hợp lệ không vào bản nháp mà báo qua `onRaw`.
 */
export function MatrixCell({ label, field, stored, count, unit, total, twoColumns, readOnly = false, onEdit, onRaw, raw = null }: MatrixCellProps) {
  const t = useTranslations("exams.subjectForm.matrix.distribution");
  const tCommon = useTranslations("exams.subjectForm.common.countPercent");
  const id = useId();
  const shownCount = stored === null ? null : unit === "COUNT" ? stored : count;
  const shownPercent = stored === null ? null : unit === "PERCENT" ? stored : total ? ratePercent(stored, total) : null;
  const rawIsPercent = raw?.code === "invalidPercent";
  const [countText, setCountText] = useState(() => (raw && !rawIsPercent ? raw.text : formatNumberText(shownCount)));
  const [percentText, setPercentText] = useState(() => (raw && rawIsPercent ? raw.text : formatNumberText(unit === "PERCENT" ? shownPercent : null)));
  const [last, setLast] = useState({ count: shownCount, percent: shownPercent });
  const [invalid, setInvalid] = useState(raw !== null);
  // Giá trị đổi từ bên ngoài (đổi tổng, hoàn tác, ô khác quy đổi lại): hiện giá trị mới thay cho chữ cũ.
  if (last.count !== shownCount || last.percent !== shownPercent) {
    setLast({ count: shownCount, percent: shownPercent });
    if (parseNumberText(countText, false) !== (shownCount ?? 0) || (countText.trim() === "") !== (shownCount === null)) setCountText(formatNumberText(shownCount));
    if (twoColumns && (percentText.trim() === "") !== (shownPercent === null || unit === "COUNT")) setPercentText(formatNumberText(unit === "PERCENT" ? shownPercent : null));
  }

  const accept = (edit: { unit: "COUNT" | "PERCENT"; value: number | null }) => {
    setInvalid(false);
    onRaw(null);
    onEdit(edit);
  };
  const reject = (text: string, code: "invalidNumber" | "invalidPercent") => {
    setInvalid(true);
    onRaw({ text, code });
  };
  const changeCount = (text: string) => {
    setCountText(text);
    setPercentText("");
    if (text.trim() === "") return accept({ unit: "COUNT", value: null });
    const parsed = parseNumberText(text, false);
    if (parsed === null) return reject(text, "invalidNumber");
    accept({ unit: "COUNT", value: parsed });
  };
  const changePercent = (text: string) => {
    setPercentText(text);
    if (text.trim() === "") return accept({ unit: "PERCENT", value: null });
    const parsed = parsePercentText(text);
    if (parsed === null) return reject(text, "invalidPercent");
    accept({ unit: "PERCENT", value: parsed });
  };

  const errorId = `${id}-error`;

  return (
    <div data-form-field={field} className="flex min-w-0 flex-col gap-1">
      <Label htmlFor={`${id}-count`}>{label}</Label>
      <span className="flex flex-wrap items-end gap-2">
        <span className="flex flex-col gap-1">
          {twoColumns && <span className="text-caption text-text-muted">{tCommon("count")}</span>}
          <Input
            id={`${id}-count`}
            aria-invalid={invalid || undefined}
            aria-describedby={invalid ? errorId : undefined}
            readOnly={readOnly}
            aria-readonly={readOnly || undefined}
            inputMode="numeric"
            autoComplete="off"
            value={countText}
            onChange={(event) => changeCount(event.target.value)}
            className={cn("w-20 text-right tabular-nums", readOnly && LOCKED_FIELD)}
          />
        </span>
        {twoColumns ? (
          <span className="flex flex-col gap-1">
            <Label htmlFor={`${id}-percent`} className="text-caption font-normal text-text-muted">
              {tCommon("percent")}
            </Label>
            <Input
              id={`${id}-percent`}
              aria-invalid={invalid || undefined}
              aria-describedby={invalid ? errorId : undefined}
              aria-label={t("percentAria", { label })}
              readOnly={readOnly}
              aria-readonly={readOnly || undefined}
              inputMode="decimal"
              autoComplete="off"
              value={percentText}
              placeholder={unit === "COUNT" && shownPercent !== null ? formatNumberText(shownPercent) : undefined}
              onChange={(event) => changePercent(event.target.value)}
              className={cn("w-20 text-right tabular-nums", readOnly && LOCKED_FIELD)}
            />
          </span>
        ) : (
          shownPercent !== null && <span className="pb-2 text-caption text-text-muted tabular-nums">{tCommon("share", { percent: shownPercent })}</span>
        )}
      </span>
      {invalid && (
        <p id={errorId} role="alert" className="text-caption text-danger">
          {tCommon("invalidNumber")}
        </p>
      )}
    </div>
  );
}
