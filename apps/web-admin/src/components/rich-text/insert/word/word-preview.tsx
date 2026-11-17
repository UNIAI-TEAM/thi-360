"use client";

import { cn } from "@thi360/ui";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { FormulaChip } from "./formula-chip";
import { NumberBadge } from "./number-badge";
import { FORMULA_ATTRIBUTE, RETYPE_ATTRIBUTE } from "./parse";
import type { ReviewItem } from "./session";

interface PreviewContext {
  itemOf: (id: string) => ReviewItem | undefined;
  numbers: ReadonlyMap<string, number>;
  t: ReturnType<typeof useTranslations>;
}

const MARK = "rounded-sm bg-warning-bg px-0.5 outline outline-1 outline-warning";

/** Chỗ giữ công thức: công thức đã đổi (chỗ tự sửa chờ xác nhận có nền cảnh báo), hoặc chỗ cần gõ lại. */
function renderPlaceholder(element: Element, context: PreviewContext): ReactNode {
  const id = element.getAttribute(FORMULA_ATTRIBUTE) ?? element.getAttribute(RETYPE_ATTRIBUTE) ?? "";
  const item = context.itemOf(id);
  if (!item) return null;
  const number = context.numbers.get(item.id);
  if (item.kind === "formula") {
    const pending = item.status === "pending";
    return (
      <span className={cn("mx-0.5", pending && MARK)} data-pending={pending ? "true" : undefined} title={pending ? context.t("mark") : undefined}>
        <FormulaChip latex={item.latex} />
        {pending && number !== undefined && <NumberBadge number={number} tone="warning" className="ms-1" />}
      </span>
    );
  }
  if (item.resolution?.type === "typed") {
    return (
      <span className="mx-0.5 rounded-sm bg-success-bg px-0.5">
        <FormulaChip latex={item.resolution.latex} />
      </span>
    );
  }
  if (item.resolution?.type === "removed") {
    return <span className="mx-0.5 text-caption text-text-muted line-through">{context.t("preview.formulaRemoved")}</span>;
  }
  return (
    <span className="mx-0.5 inline-flex items-center gap-1 rounded-full bg-danger-bg px-2 text-caption font-medium text-danger">
      {context.t("preview.formulaImage")}
      {number !== undefined && <NumberBadge number={number} tone="danger" />}
    </span>
  );
}

function renderNode(node: Node, key: number, context: PreviewContext): ReactNode {
  if (node.nodeType === Node.TEXT_NODE) return node.nodeValue;
  if (node.nodeType !== Node.ELEMENT_NODE) return null;
  const element = node as Element;
  if (element.hasAttribute(FORMULA_ATTRIBUTE) || element.hasAttribute(RETYPE_ATTRIBUTE)) {
    return <span key={key}>{renderPlaceholder(element, context)}</span>;
  }
  const children = renderChildren(element, context);
  switch (element.localName) {
    case "p":
      return <p key={key} className="m-0 min-w-0 break-words">{children}</p>;
    case "strong":
      return <strong key={key}>{children}</strong>;
    case "em":
      return <em key={key}>{children}</em>;
    case "u":
      return <u key={key}>{children}</u>;
    case "s":
      return <s key={key}>{children}</s>;
    case "sub":
      return <sub key={key}>{children}</sub>;
    case "sup":
      return <sup key={key}>{children}</sup>;
    case "br":
      return <br key={key} />;
    case "ul":
      return <ul key={key} className="m-0 list-disc ps-5">{children}</ul>;
    case "ol":
      return <ol key={key} className="m-0 list-decimal ps-5">{children}</ol>;
    case "li":
      return <li key={key}>{children}</li>;
    case "table":
      return (
        <div key={key} className="max-w-full overflow-x-auto">
          <table className="border-collapse text-body">{children}</table>
        </div>
      );
    case "tbody":
      return <tbody key={key}>{children}</tbody>;
    case "tr":
      return <tr key={key}>{children}</tr>;
    case "td":
    case "th": {
      const Cell = element.localName;
      return (
        <Cell
          key={key}
          colSpan={Number(element.getAttribute("colspan")) || undefined}
          rowSpan={Number(element.getAttribute("rowspan")) || undefined}
          className="border border-border px-2 py-1 text-start align-top"
        >
          {children}
        </Cell>
      );
    }
    default:
      return <span key={key}>{children}</span>;
  }
}

function renderChildren(element: Element, context: PreviewContext): ReactNode[] {
  return [...element.childNodes].map((child, index) => renderNode(child, index, context));
}

export interface WordBlocksProps {
  blocks: Element[];
  itemOf: PreviewContext["itemOf"];
  /** Số thứ tự của từng chỗ cần xem lại (cùng số với thẻ trong danh sách xem lại). */
  numbers: PreviewContext["numbers"];
}

/** Dựng các khối nội bộ của bản dán thành React (không `dangerouslySetInnerHTML`): chỉ thẻ trong danh sách cho phép. */
export function WordBlocks({ blocks, itemOf, numbers }: WordBlocksProps) {
  const t = useTranslations("richText.insertDialog.word");
  const context: PreviewContext = { itemOf, numbers, t };
  if (blocks.length === 0) return <p className="m-0 text-caption text-text-muted">{t("preview.empty")}</p>;
  return <div className="flex flex-col gap-1.5 text-body text-text">{blocks.map((block, index) => renderNode(block, index, context))}</div>;
}
