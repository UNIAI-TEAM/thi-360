"use client";

import { SESSION_QUERY_KEY } from "@/features/session/api";
import type { Session } from "@/features/session/types";
import { useQueryClient } from "@tanstack/react-query";
import { cn, focusRing, Input } from "@thi360/ui";
import katex from "katex";
import { useId, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import "../../math-render";
import type { InsertDialogProps } from "../registry";
import { InsertDialogShell } from "../shared/insert-dialog-shell";
import { loadRecent, pushRecent } from "../shared/recent-store";
import { SymbolGrid } from "../shared/symbol-grid";
import { ceLatex, composeCe, splitArrowConditions } from "./ce";
import { DisplayToggle } from "./display-toggle";
import { FormulaSpan } from "./formula-span";
import { insertChemistry, readChemistryTarget } from "./insert-formulas";

const RECENT_SCOPE = "chemistry";

type FieldKey = "source" | "after";

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

/**
 * Hộp "Công thức hóa học" (spec 4.3, bản vẽ `SoanThaoHoaHoc`): ô "Gõ nhanh" là nội dung `\ce{…}` của mhchem, nút chèn
 * đoạn mhchem tại con trỏ, "Trên/Dưới mũi tên" ghép vào mũi tên đầu tiên, "Ghi sau phương trình" là một công thức
 * cùng dòng riêng đặt ngay sau phương trình. Xem trước dựng bằng `renderMath`. Mở lại: công thức `\ce{…}` đang chọn
 * được tách về ba ô; "Ghi sau" không lấy lại vì nó là công thức riêng.
 */
export default function ChemistryInsertDialog({ editor, open, onOpenChange }: InsertDialogProps) {
  const t = useTranslations("richText.insertDialog.chemistry");
  const target = useMemo(() => readChemistryTarget(editor), [editor]);
  const [source, setSource] = useState(target?.parts.source ?? "");
  const [top, setTop] = useState(target?.parts.top ?? "");
  const [bottom, setBottom] = useState(target?.parts.bottom ?? "");
  const [after, setAfter] = useState("");
  const [display, setDisplay] = useState(target?.display ?? false);

  const sourceId = useId();
  const topId = useId();
  const bottomId = useId();
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

  const body = composeCe(source, top, bottom);
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

  function loadRecentEquation(recentBody: string) {
    const parts = splitArrowConditions(recentBody);
    setSource(parts.source);
    setTop(parts.top);
    setBottom(parts.bottom);
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
          <div className="flex flex-col gap-1.5">
            <label htmlFor={topId} className="text-field-label text-text">
              {t("topLabel")}
            </label>
            <Input
              id={topId}
              value={top}
              onChange={(event) => setTop(event.target.value)}
              placeholder={t("topPlaceholder")}
              dir="ltr"
              autoComplete="off"
              spellCheck={false}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor={bottomId} className="text-field-label text-text">
              {t("bottomLabel")}
            </label>
            <Input
              id={bottomId}
              value={bottom}
              onChange={(event) => setBottom(event.target.value)}
              placeholder={t("bottomPlaceholder")}
              dir="ltr"
              autoComplete="off"
              spellCheck={false}
            />
          </div>
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
                    onClick={() => loadRecentEquation(recentBody)}
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

        <p className="text-caption text-text-muted">{t("structureNote")}</p>
      </div>
    </InsertDialogShell>
  );
}
