"use client";

import { Checkbox, IconButton, Input } from "@thi360/ui";
import { CircleMinus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import type { CatalogOption } from "@/features/content/types";

export interface BlockTagPickerProps {
  value: readonly string[];
  onChange: (codes: string[]) => void;
  options: readonly CatalogOption[];
  /** Tên của chỗ đặt nhãn, cho nhãn đọc màn hình ("nhóm", "chùm 2"). */
  scopeName: string;
  /** Mỏ neo của ô tìm (`data-field`), xem `block-form-fields.ts`. */
  anchor?: string;
}

/**
 * Chọn nhiều nhãn của nhóm hoặc của một chùm (H22): câu khớp khi có ít nhất một nhãn đã chọn. Ô tìm lọc theo mã và tên;
 * nhãn đã chọn hiện thành thẻ có nút bỏ. Nhãn đã lưu mà danh mục không còn vẫn hiện (theo mã) để bỏ được.
 */
export function BlockTagPicker({ value, onChange, options, scopeName, anchor }: BlockTagPickerProps) {
  const t = useTranslations("exams.structure.blockForm.tags");
  const listId = useId();
  const [query, setQuery] = useState("");
  const normalized = query.trim().toLocaleLowerCase();
  const shown = options.filter(
    (option) => normalized === "" || option.code.toLocaleLowerCase().includes(normalized) || option.name.toLocaleLowerCase().includes(normalized),
  );
  const nameOf = (code: string) => options.find((option) => option.code === code)?.name ?? code;
  const toggle = (code: string, checked: boolean) => onChange(checked ? [...value, code] : value.filter((item) => item !== code));

  return (
    <div className="flex flex-col gap-2">
      {value.length > 0 && (
        <ul className="flex flex-wrap gap-2" aria-label={t("selected", { name: scopeName })}>
          {value.map((code) => (
            <li
              key={code}
              className="inline-flex max-w-full items-center gap-1 rounded-full border border-primary-200 bg-primary-50 py-0.5 pr-0.5 pl-3 text-label font-medium text-primary-text"
            >
              <span className="min-w-0 wrap-anywhere">{nameOf(code)}</span>
              <IconButton
                icon={<CircleMinus />}
                className="size-8"
                aria-label={t("remove", { tag: nameOf(code), name: scopeName })}
                onClick={() => onChange(value.filter((item) => item !== code))}
              />
            </li>
          ))}
        </ul>
      )}
      <Input
        type="search"
        value={query}
        autoComplete="off"
        placeholder={t("search")}
        aria-label={t("searchLabel", { name: scopeName })}
        aria-controls={listId}
        data-field={anchor}
        onChange={(event) => setQuery(event.target.value)}
      />
      <ul id={listId} className="flex max-h-44 flex-col gap-0.5 overflow-auto rounded-md border border-border p-1">
        {shown.length === 0 && <li className="px-3 py-2 text-caption text-text-muted">{t("none")}</li>}
        {shown.map((option) => {
          const id = `${listId}-${option.code}`;
          return (
            <li key={option.code} className="flex min-h-10 items-center gap-2.5 rounded-sm px-3 hover:bg-subtle">
              <Checkbox id={id} checked={value.includes(option.code)} onCheckedChange={(checked) => toggle(option.code, checked === true)} />
              <label htmlFor={id} className="flex-1 cursor-pointer py-2 text-body text-text">
                {option.name}
              </label>
            </li>
          );
        })}
      </ul>
      <p className="text-caption text-text-muted">{t("matchAny")}</p>
    </div>
  );
}
