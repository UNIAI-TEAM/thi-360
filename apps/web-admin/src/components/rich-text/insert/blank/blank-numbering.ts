import type { Node as PMNode } from "@tiptap/pm/model";
import { BLANK_NODE } from "../../extensions/names";

/**
 * Numbered blank `span.blank[data-blank="n"]` (spec 3.2): `data-blank` = n points at the n-th sub-question in its
 * current order (3.5). Stored HTML is not guaranteed to match the canonical form of the template byte for byte
 * (attribute order, whitespace, single quotes, extra classes), so tags are read by attribute rather than compared as
 * strings; only a blank whose number changes is rewritten to the canonical form
 * `<span class="blank" data-blank="n"></span>`, every other byte of the HTML stays as is.
 */
const SPAN_ELEMENT = /<span\b((?:[^>"']|"[^"]*"|'[^']*')*)>[^<]*<\/span>/gi;
const ATTRIBUTE = /([^\s"'<>/=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g;

const blankSpan = (number: number) => `<span class="blank" data-blank="${number}"></span>`;

/** `data-blank` number of a `<span …>` tag with class `blank` and a positive-integer `data-blank`; `null` if it is not a blank. */
function blankNumberOfAttributes(attributes: string): number | null {
  let isBlank = false;
  let number: number | null = null;
  for (const [, name, quoted, single, bare] of attributes.matchAll(ATTRIBUTE)) {
    const value = quoted ?? single ?? bare ?? "";
    const key = name!.toLowerCase();
    if (key === "class") isBlank = value.split(/\s+/).includes("blank");
    else if (key === "data-blank" && /^\s*\d+\s*$/.test(value)) number = Number(value);
  }
  return isBlank ? number : null;
}

/** Replaces each blank with `replace(number, original tag)`; other tags stay as is. */
function mapBlanks(html: string, replace: (number: number, span: string) => string): string {
  return html.replace(SPAN_ELEMENT, (span, attributes: string) => {
    const number = blankNumberOfAttributes(attributes);
    return number === null ? span : replace(number, span);
  });
}

/** `data-blank` numbers of the blanks in the HTML, in order of appearance. */
export function blankNumbersOfHtml(html: string): number[] {
  const numbers: number[] = [];
  mapBlanks(html, (number, span) => {
    numbers.push(number);
    return span;
  });
  return numbers;
}

/** `data-blank` numbers of the blanks in the editor document, skipping the node at `exceptPos`. */
export function blankNumbersOfDoc(doc: PMNode, exceptPos?: number): number[] {
  const numbers: number[] = [];
  doc.descendants((node, pos) => {
    if (node.type.name === BLANK_NODE && pos !== exceptPos && typeof node.attrs.blank === "number") numbers.push(node.attrs.blank);
    return true;
  });
  return numbers;
}

/** First sub-question (numbered from 1) among `childCount` that has no blank yet; `null` when none is left. */
export function firstChildWithoutBlank(childCount: number, usedNumbers: readonly number[]): number | null {
  const used = new Set(usedNumbers);
  for (let number = 1; number <= childCount; number += 1) {
    if (!used.has(number)) return number;
  }
  return null;
}

/**
 * Reorders sub-questions: the one at index `from` (0-based) moves to `to`; its blank follows it, and the blanks of the
 * sub-questions it displaces are renumbered (spec 3.5).
 */
export function renumberBlanksAfterMove(html: string, from: number, to: number): string {
  return mapBlanks(html, (number, span) => {
    const position = number - 1;
    let next = position;
    if (position === from) next = to;
    else if (from < to && position > from && position <= to) next = position - 1;
    else if (from > to && position >= to && position < from) next = position + 1;
    return next === position ? span : blankSpan(next + 1);
  });
}

/**
 * Removes the sub-question at index `removed` (0-based): its blank is deleted and larger numbers drop by 1 (spec 3.5).
 * Only the blank is removed; the text around it stays as is.
 */
export function renumberBlanksAfterRemove(html: string, removed: number): string {
  return mapBlanks(html, (number, span) => {
    if (number === removed + 1) return "";
    return number > removed + 1 ? blankSpan(number - 1) : span;
  });
}
