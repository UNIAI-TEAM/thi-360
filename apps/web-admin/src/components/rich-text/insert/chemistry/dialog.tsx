"use client";

import { SESSION_QUERY_KEY } from "@/features/session/api";
import type { Session } from "@/features/session/types";
import { useQueryClient } from "@tanstack/react-query";
import { cn, focusRing, Input } from "@thi360/ui";
import { ChevronRight } from "lucide-react";
import katex from "katex";
import "katex/contrib/mhchem";
import { useId, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { DisplayToggle } from "../math/display-toggle";
import type { InsertDialogProps } from "../registry";
import { InsertDialogShell } from "../shared/insert-dialog-shell";
import { loadRecent, pushRecent } from "../shared/recent-store";
import { SymbolGrid } from "../shared/symbol-grid";
import { arrowGlyph, ceLatex, findArrows, setArrowConditions, type CeArrow } from "./ce";
import { FormulaSpan } from "./formula-span";
import { insertChemistry, readChemistryTarget } from "./insert-formulas";

const RECENT_SCOPE = "chemistry";

type FieldKey = "source" | "after";

type ConditionField = "top" | "bottom";

/** Chữ đang gõ trong một ô điều kiện: hiện đúng chữ đó tới khi rời ô, kể cả khi chuỗi gõ nhanh đọc lại khác đi. */
interface ConditionDraft {
  index: number;
  field: ConditionField;
  value: string;
}

/** Một nút của bảng nút: đoạn chèn vào ô, số ký tự lùi con trỏ về sau khi chèn (ví dụ `[]` đặt con trỏ giữa hai ngoặc). */
interface ButtonSpec {
  id: string;
  glyph: ReactNode;
  text: string;
  field?: FieldKey;
  caretBack?: number;
}

/** LaTeX có dựng được bằng KaTeX + mhchem không. Chuỗi rỗng coi là hợp lệ (chưa nhập). */
function renders(latex: string, displayMode: boolean): boolean {
  if (!latex) return true;
  try {
    katex.renderToString(latex, { displayMode, throwOnError: true });
    return true;
  } catch {
    return false;
  }
}

const ARROW_BUTTONS: ButtonSpec[] = [
  { id: "arrowSingle", glyph: "→", text: " -> " },
  { id: "arrowReversible", glyph: "⇌", text: " <=> " },
  { id: "arrowBack", glyph: "←", text: " <- " },
  { id: "gas", glyph: "↑", text: " ^" },
  { id: "precipitate", glyph: "↓", text: " v" },
];

const STATE_BUTTONS: ButtonSpec[] = [
  { id: "stateSolid", glyph: "(s)", text: "(s)" },
  { id: "stateLiquid", glyph: "(l)", text: "(l)" },
  { id: "stateGas", glyph: "(g)", text: "(g)" },
  { id: "stateAqueous", glyph: "(aq)", text: "(aq)" },
];

const CHARGE_BUTTONS: ButtonSpec[] = [
  { id: "chargePlus", glyph: "+", text: "^+" },
  { id: "chargeMinus", glyph: "−", text: "^-" },
  { id: "chargeTwoPlus", glyph: "2+", text: "^2+" },
  { id: "chargeTwoMinus", glyph: "2−", text: "^2-" },
  { id: "chargeThreePlus", glyph: "3+", text: "^3+" },
  { id: "chargeThreeMinus", glyph: "3−", text: "^3-" },
];

const OTHER_BUTTONS: ButtonSpec[] = [
  {
    id: "enthalpy",
    glyph: (
      <>
        Δ<sub>r</sub>
        <i>H</i>°
      </>
    ),
    // Không thuộc cú pháp mhchem: ΔrH° là LaTeX của "Ghi sau phương trình" (công thức riêng), nên nút ghi vào ô đó.
    text: "\\Delta_r H^\\circ_{298} = ",
    field: "after",
  },
  { id: "concentration", glyph: "[ ]", text: "[]", caretBack: 1 },
  { id: "doubleBond", glyph: "=", text: "=" },
  { id: "tripleBond", glyph: "≡", text: "#" },
  {
    id: "isotope",
    glyph: (
      <>
        <sup>A</sup>
        <sub>Z</sub>X
      </>
    ),
    text: "^{A}_{Z}X",
  },
];

/** Ví dụ của phần "Xem thêm cách gõ": mã gõ nhanh và kết quả dựng bằng cùng đường dựng với xem trước. */
const GUIDE_EXAMPLES: { id: string; code: string }[] = [
  { id: "arrowSingle", code: "A -> B" },
  { id: "arrowReversible", code: "N2 + 3H2 <=> 2NH3" },
  { id: "arrowBack", code: "A <- B" },
  { id: "gas", code: "CaCO3 -> CaO + CO2 ^" },
  { id: "precipitate", code: "Ba^2+ + SO4^2- -> BaSO4 v" },
  { id: "conditions", code: "A ->[t°, xt][p] B" },
  { id: "coefficient", code: "2H2 + O2 -> 2H2O" },
  { id: "parentheses", code: "Ca(OH)2" },
  { id: "hydrate", code: "CuSO4*5H2O" },
  { id: "states", code: "NaCl(s) -> NaCl(aq)" },
];

/**
 * Hộp "Công thức hóa học" (spec 4.3, bản vẽ `SoanThaoHoaHoc`): ô "Gõ nhanh" là nội dung `\ce{…}` của mhchem, nút chèn
 * đoạn mhchem tại con trỏ. Mỗi mũi tên trong chuỗi có một cặp ô "Trên/Dưới mũi tên" đồng bộ hai chiều với nhóm
 * `->[trên][dưới]` của chính nó trong chuỗi. "Ghi sau phương trình" là một công thức cùng dòng riêng đặt ngay sau
 * phương trình. Xem trước dựng bằng `renderMath`. Mở lại: công thức `\ce{…}` đang chọn về lại ô Gõ nhanh, điều kiện của
 * từng mũi tên hiện ở hàng của nó; "Ghi sau" không lấy lại vì nó là công thức riêng.
 */
export default function ChemistryInsertDialog({ editor, open, onOpenChange }: InsertDialogProps) {
  const t = useTranslations("richText.insertDialog.chemistry");
  const target = useMemo(() => readChemistryTarget(editor), [editor]);
  const [source, setSource] = useState(target?.body ?? "");
  const [draft, setDraft] = useState<ConditionDraft | null>(null);
  const [after, setAfter] = useState("");
  const [display, setDisplay] = useState(target?.display ?? false);

  const sourceId = useId();
  const conditionsId = useId();
  const afterId = useId();
  const equationErrorId = useId();
  const noteErrorId = useId();
  const sourceRef = useRef<HTMLInputElement>(null);
  const afterRef = useRef<HTMLInputElement>(null);
  const pendingCaret = useRef<{ field: FieldKey; caret: number } | null>(null);

  const queryClient = useQueryClient();
  // Phiên đã nạp ở khung ứng dụng; không có (test, trang rời) thì không có "Dùng gần đây".
  const userId = queryClient.getQueryData<Session>(SESSION_QUERY_KEY)?.id ?? null;
  const [recent, setRecent] = useState<string[]>(() => (userId ? loadRecent(userId, RECENT_SCOPE) : []));

  const body = source.trim();
  const arrows = findArrows(source);
  const equation = body ? ceLatex(body) : "";
  const note = after.trim();
  const equationOk = useMemo(() => renders(equation, display), [equation, display]);
  const noteOk = useMemo(() => renders(note, false), [note]);
  const canInsert = body !== "" && equationOk && noteOk;

  // Sau khi nút chèn đoạn vào ô, trả focus và đặt con trỏ ngay sau đoạn vừa chèn (cuối ô khi không lùi).
  useLayoutEffect(() => {
    const pending = pendingCaret.current;
    if (!pending) return;
    pendingCaret.current = null;
    const input = (pending.field === "source" ? sourceRef : afterRef).current;
    input?.focus();
    input?.setSelectionRange(pending.caret, pending.caret);
  });

  function insertText({ text, field = "source", caretBack = 0 }: ButtonSpec) {
    const input = (field === "source" ? sourceRef : afterRef).current;
    const value = field === "source" ? source : after;
    const start = input?.selectionStart ?? value.length;
    const end = input?.selectionEnd ?? start;
    const next = value.slice(0, start) + text + value.slice(end);
    (field === "source" ? setSource : setAfter)(next);
    pendingCaret.current = { field, caret: start + text.length - caretBack };
  }

  function conditionValue(arrow: CeArrow, index: number, field: ConditionField): string {
    return draft && draft.index === index && draft.field === field ? draft.value : arrow[field];
  }

  function changeCondition(index: number, field: ConditionField, value: string) {
    const arrow = arrows[index];
    if (!arrow) return;
    const top = field === "top" ? value : conditionValue(arrow, index, "top");
    const bottom = field === "bottom" ? value : conditionValue(arrow, index, "bottom");
    setSource(setArrowConditions(source, index, top, bottom));
    setDraft({ index, field, value });
  }

  function renderConditionInput(index: number, field: ConditionField, naming: { id: string } | { labelledBy: string }) {
    const arrow = arrows[index];
    return (
      <Input
        id={"id" in naming ? naming.id : undefined}
        aria-labelledby={"labelledBy" in naming ? naming.labelledBy : undefined}
        value={arrow ? conditionValue(arrow, index, field) : ""}
        onChange={(event) => changeCondition(index, field, event.target.value)}
        onBlur={() => setDraft(null)}
        disabled={!arrow}
        placeholder={arrow ? t(field === "top" ? "topPlaceholder" : "bottomPlaceholder") : t("conditions.noArrow")}
        dir="ltr"
        autoComplete="off"
        spellCheck={false}
      />
    );
  }

  function renderConditions() {
    // 0 hoặc 1 mũi tên: một cặp ô như trước (0 mũi tên thì khóa, gợi ý gõ mũi tên trước).
    if (arrows.length <= 1) {
      return (
        <>
          <div className="flex flex-col gap-1.5">
            <label htmlFor={`${conditionsId}-0-top`} className="text-field-label text-text">
              {t("topLabel")}
            </label>
            {renderConditionInput(0, "top", { id: `${conditionsId}-0-top` })}
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor={`${conditionsId}-0-bottom`} className="text-field-label text-text">
              {t("bottomLabel")}
            </label>
            {renderConditionInput(0, "bottom", { id: `${conditionsId}-0-bottom` })}
          </div>
        </>
      );
    }
    // Nhiều mũi tên: một hàng mỗi mũi tên theo thứ tự; tên ô = "Mũi tên n (→)" + tên cột.
    const topHeadId = `${conditionsId}-top`;
    const bottomHeadId = `${conditionsId}-bottom`;
    return (
      <div
        role="group"
        aria-label={t("conditions.title")}
        className="grid grid-cols-[auto_minmax(0,1fr)_minmax(0,1fr)] items-center gap-x-3 gap-y-2 sm:col-span-2"
      >
        <span aria-hidden />
        <span id={topHeadId} className="text-field-label text-text">
          {t("topLabel")}
        </span>
        <span id={bottomHeadId} className="text-field-label text-text">
          {t("bottomLabel")}
        </span>
        {arrows.map((arrow, index) => {
          const rowId = `${conditionsId}-${index}`;
          return (
            <div key={index} className="contents">
              <span id={rowId} className="text-body whitespace-nowrap text-text-secondary">
                {t("conditions.arrowLabel", { index: index + 1, arrow: arrowGlyph(arrow.arrow) })}
              </span>
              {renderConditionInput(index, "top", { labelledBy: `${rowId} ${topHeadId}` })}
              {renderConditionInput(index, "bottom", { labelledBy: `${rowId} ${bottomHeadId}` })}
            </div>
          );
        })}
      </div>
    );
  }

  function submit() {
    if (!canInsert) return;
    insertChemistry(editor, { equation, note, display }, target);
    if (userId) {
      pushRecent(userId, RECENT_SCOPE, body);
      setRecent(loadRecent(userId, RECENT_SCOPE));
    }
    onOpenChange(false);
  }

  function renderGroup(groupId: string, specs: ButtonSpec[]) {
    return (
      <div key={groupId} className="flex flex-col gap-1.5">
        <span className="text-caption font-medium text-text-muted">{t(`groups.${groupId}`)}</span>
        <SymbolGrid
          aria-label={t(`groups.${groupId}`)}
          items={specs.map((spec) => ({ glyph: spec.glyph, label: t(`buttons.${spec.id}`) }))}
          onPick={(_, index) => insertText(specs[index]!)}
        />
      </div>
    );
  }

  return (
    <InsertDialogShell
      open={open}
      onOpenChange={onOpenChange}
      title={t("title")}
      description={t("description")}
      submitLabel={target ? t("update") : t("insert")}
      submitDisabled={!canInsert}
      onSubmit={submit}
    >
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <label htmlFor={sourceId} className="text-field-label text-text">
            {t("quickLabel")}
          </label>
          <Input
            id={sourceId}
            ref={sourceRef}
            value={source}
            onChange={(event) => setSource(event.target.value)}
            dir="ltr"
            autoFocus
            autoComplete="off"
            spellCheck={false}
            aria-invalid={!equationOk}
            aria-describedby={equationOk ? undefined : equationErrorId}
            className="font-mono"
          />
          {!equationOk && (
            <p id={equationErrorId} role="alert" className="text-caption text-danger">
              {t("errors.equation")}
            </p>
          )}
        </div>

        <div className="flex flex-wrap gap-x-6 gap-y-3">
          {renderGroup("arrows", ARROW_BUTTONS)}
          {renderGroup("states", STATE_BUTTONS)}
          {renderGroup("charges", CHARGE_BUTTONS)}
          {renderGroup("other", OTHER_BUTTONS)}
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          {renderConditions()}
          <div className="flex flex-col gap-1.5">
            <label htmlFor={afterId} className="text-field-label text-text">
              {t("afterLabel")}
            </label>
            <Input
              id={afterId}
              ref={afterRef}
              value={after}
              onChange={(event) => setAfter(event.target.value)}
              placeholder={t("afterPlaceholder")}
              dir="ltr"
              autoComplete="off"
              spellCheck={false}
              aria-invalid={!noteOk}
              aria-describedby={noteOk ? undefined : noteErrorId}
              className="font-mono"
            />
            {!noteOk && (
              <p id={noteErrorId} role="alert" className="text-caption text-danger">
                {t("errors.note")}
              </p>
            )}
          </div>
        </div>

        <DisplayToggle
          display={display}
          onChange={setDisplay}
          label={t("displayLabel")}
          inlineLabel={t("inline")}
          blockLabel={t("block")}
        />

        <section aria-label={t("previewLabel")} className="flex flex-col gap-1.5">
          <span className="text-field-label text-text">{t("previewLabel")}</span>
          <div
            data-testid="chemistry-preview"
            className="flex min-h-14 flex-wrap items-center gap-x-2 rounded-xl border border-border bg-subtle px-4 py-3 text-body text-text"
          >
            {equation && equationOk ? (
              <FormulaSpan latex={equation} display={display} />
            ) : (
              <span className="text-text-placeholder">{t("previewEmpty")}</span>
            )}
            {note && noteOk && <FormulaSpan latex={note} />}
          </div>
        </section>

        <div className="grid gap-4 lg:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <span className="text-field-label text-text">{t("rules.title")}</span>
            <ul className="list-disc space-y-1 pl-5 text-caption text-text-muted">
              <li>
                {t("rules.subscriptLead")} <code className="font-mono text-text">H2SO4</code> {t("rules.becomes")} H
                <sub>2</sub>SO<sub>4</sub>
              </li>
              <li>
                {t("rules.chargeLead")} <code className="font-mono text-text">Fe^3+</code> {t("rules.becomes")} Fe
                <sup>3+</sup>
              </li>
              <li>
                {t("rules.arrowLead")} <code className="font-mono text-text">-&gt;</code> {t("rules.arrowOr")}{" "}
                <code className="font-mono text-text">&lt;=&gt;</code> {t("rules.arrowTail")}
              </li>
              <li>
                {t("rules.capitalsLead")} <code className="font-mono text-text">C2H5OH</code> {t("rules.capitalsNot")}{" "}
                <code className="font-mono text-text">c2h5oh</code>
              </li>
            </ul>
          </div>
          {recent.length > 0 && (
            <div className="flex flex-col gap-1.5">
              <span className="text-field-label text-text">{t("recentLabel")}</span>
              <div className="flex flex-wrap gap-2">
                {recent.map((recentBody) => (
                  <button
                    key={recentBody}
                    type="button"
                    aria-label={recentBody}
                    onClick={() => setSource(recentBody)}
                    className={cn(
                      "inline-flex min-h-10 items-center rounded-md border border-border bg-surface px-3 text-body text-text",
                      "hover:border-border-strong hover:bg-subtle",
                      focusRing,
                    )}
                  >
                    <FormulaSpan latex={ceLatex(recentBody)} />
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        <details data-testid="chemistry-guide" className="group rounded-md border border-border">
          <summary
            className={cn(
              "flex cursor-pointer list-none items-center gap-2 rounded-md px-4 py-2 text-field-label text-text",
              focusRing,
            )}
          >
            <ChevronRight aria-hidden className="size-4 transition-transform group-open:rotate-90 motion-reduce:transition-none" />
            {t("rules.more")}
          </summary>
          <ul className="grid gap-x-6 gap-y-2 border-t border-border px-4 py-3 text-caption text-text-muted md:grid-cols-2">
            {GUIDE_EXAMPLES.map(({ id, code }) => (
              <li key={id} className="flex flex-wrap items-center gap-x-1.5 gap-y-1">
                <span>{t(`rules.guide.${id}`)}</span>
                <code className="font-mono text-text">{code}</code>
                <span>{t("rules.becomes")}</span>
                <FormulaSpan latex={ceLatex(code)} className="text-body text-text" />
              </li>
            ))}
          </ul>
        </details>

        <p className="text-caption text-text-muted">{t("structureNote")}</p>
      </div>
    </InsertDialogShell>
  );
}
