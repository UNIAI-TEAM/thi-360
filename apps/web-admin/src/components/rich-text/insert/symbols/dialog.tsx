"use client";

import { SESSION_QUERY_KEY } from "@/features/session/api";
import type { Session } from "@/features/session/types";
import { useQueryClient } from "@tanstack/react-query";
import { Button, Input } from "@thi360/ui";
import { Search } from "lucide-react";
import { useTranslations } from "next-intl";
import { Fragment, useId, useMemo, useState, type ReactNode } from "react";
import type { InsertDialogProps } from "../registry";
import { InsertDialogShell } from "../shared/insert-dialog-shell";
import { loadRecent, pushRecent } from "../shared/recent-store";
import { SymbolGrid, type SymbolGridItem } from "../shared/symbol-grid";
import { insertSymbol } from "./insert-symbol";
import { matchesQuery } from "./search";
import { SYMBOL_GROUP_IDS, symbolById, symbolText, SYMBOLS, type SymbolDef, type SymbolGroupId } from "./symbol-data";
import { WideSymbolGrid } from "./wide-symbol-grid";

const RECENT_SCOPE = "symbols";

function glyphOf(entry: SymbolDef): ReactNode {
  return entry.parts.map((part, index) => {
    if (typeof part === "string") return <Fragment key={index}>{part}</Fragment>;
    return "sub" in part ? <sub key={index}>{part.sub}</sub> : <sup key={index}>{part.sup}</sup>;
  });
}

interface SymbolGroupView {
  id: "recent" | SymbolGroupId;
  entries: SymbolDef[];
}

/**
 * Hộp "Ký hiệu và đơn vị" (spec 4.4, bản vẽ `SoanThaoKyHieu`): lưới `SymbolGrid` theo nhóm và ô tìm theo tên tiếng Việt
 * lẫn tiếng Anh. Bấm một ký hiệu chèn chữ Unicode thường (đơn vị, sinh học dùng `sub`/`sup` thật) vào con trỏ và hộp
 * không đóng; nút Đóng ở chân hộp.
 */
export default function SymbolsInsertDialog({ editor, open, onOpenChange }: InsertDialogProps) {
  const t = useTranslations("richText.insertDialog.symbols");
  const searchId = useId();
  const [query, setQuery] = useState("");
  const queryClient = useQueryClient();
  // Phiên đã nạp ở khung ứng dụng; không có (test, trang rời) thì không có "Dùng gần đây".
  const userId = queryClient.getQueryData<Session>(SESSION_QUERY_KEY)?.id ?? null;
  const [recentIds, setRecentIds] = useState<string[]>(() => (userId ? loadRecent(userId, RECENT_SCOPE) : []));

  const searching = query.trim() !== "";
  const groups = useMemo<SymbolGroupView[]>(() => {
    const recentEntries = recentIds.flatMap((id) => symbolById(id) ?? []);
    const all: SymbolGroupView[] = [
      { id: "recent", entries: recentEntries },
      ...SYMBOL_GROUP_IDS.map((id) => ({ id, entries: SYMBOLS.filter((entry) => entry.group === id) })),
    ];
    if (!searching) return all.filter((group) => group.entries.length > 0);
    // Đang tìm: bỏ "Dùng gần đây" (trùng với các nhóm), chỉ giữ ký hiệu có tên hoặc chữ khớp, ở cả hai ngôn ngữ.
    return all
      .filter((group) => group.id !== "recent")
      .map((group) => ({
        ...group,
        entries: group.entries.filter((entry) =>
          matchesQuery(query, [t(`names.${entry.id}`), t(`aliases.${entry.id}`), symbolText(entry)]),
        ),
      }))
      .filter((group) => group.entries.length > 0);
  }, [recentIds, query, searching, t]);

  function pick(entry: SymbolDef) {
    insertSymbol(editor, entry);
    if (userId) {
      pushRecent(userId, RECENT_SCOPE, entry.id);
      setRecentIds(loadRecent(userId, RECENT_SCOPE));
    }
  }

  return (
    <InsertDialogShell
      open={open}
      onOpenChange={onOpenChange}
      title={t("title")}
      description={t("description")}
      hideFooter
    >
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <label htmlFor={searchId} className="sr-only">
            {t("searchLabel")}
          </label>
          <Input
            id={searchId}
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t("searchPlaceholder")}
            leadingIcon={<Search />}
            autoComplete="off"
            autoFocus
          />
        </div>

        <div className="flex max-h-[min(55vh,26rem)] flex-col gap-4 overflow-y-auto pr-1">
          {groups.length === 0 && (
            <p role="status" className="text-body text-text-muted">
              {t("noResults")}
            </p>
          )}
          {groups.map((group) => {
            const items: SymbolGridItem[] = group.entries.map((entry) => ({
              glyph: glyphOf(entry),
              label: t(`names.${entry.id}`),
            }));
            // Đơn vị dài (`mol/L`, `W/m²`) tràn ô 40px của SymbolGrid: nhóm có đơn vị dùng lưới rộng theo chữ.
            const Grid = group.entries.some((entry) => entry.group === "units") ? WideSymbolGrid : SymbolGrid;
            return (
              <section key={group.id} className="flex flex-col gap-1.5">
                <h3 className="text-field-label text-text">{t(`groups.${group.id}`)}</h3>
                <Grid
                  aria-label={t(`groups.${group.id}`)}
                  items={items}
                  onPick={(_, index) => pick(group.entries[index]!)}
                />
              </section>
            );
          })}
        </div>

        <div className="flex items-center justify-between gap-3 border-t border-border pt-4">
          <p className="text-caption text-text-muted">{t("hint")}</p>
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            {t("close")}
          </Button>
        </div>
      </div>
    </InsertDialogShell>
  );
}
