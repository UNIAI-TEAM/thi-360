import { getMarkRange } from "@tiptap/core";
import type { Node as PMNode } from "@tiptap/pm/model";
import type { EditorState } from "@tiptap/pm/state";
import { UNDERLINE_LABEL_MARK } from "../../extensions/names";

/** Nhãn hợp lệ của `u[data-label]` (spec 3.3): một chữ A–H. */
export const UNDERLINE_LABELS = ["A", "B", "C", "D", "E", "F", "G", "H"] as const;

/** Nhãn kế tiếp chưa dùng (A, B, C, …); `null` khi đã dùng hết A–H. */
export function nextUnderlineLabel(used: Iterable<string>): string | null {
  const taken = new Set(used);
  return UNDERLINE_LABELS.find((label) => !taken.has(label)) ?? null;
}

/** Một cụm gạch chân đánh nhãn trong nội dung: nhãn và chữ thuần của cụm. */
export interface UnderlinePhrase {
  label: string;
  text: string;
}

/**
 * Các cụm `u[data-label]` của một chuỗi HTML, theo thứ tự xuất hiện; chữ thuần (bỏ định dạng). Dùng cho phương án
 * tự lấy theo cụm gạch chân (spec 4.8).
 */
export function underlinePhrasesOfHtml(html: string): UnderlinePhrase[] {
  if (!html.includes("data-label")) return [];
  const doc = new DOMParser().parseFromString(html, "text/html");
  return [...doc.querySelectorAll("u[data-label]")].map((element) => ({
    label: element.getAttribute("data-label")?.trim() ?? "",
    text: (element.textContent ?? "").trim(),
  }));
}

/** Cụm gạch chân đánh nhãn trong vùng chọn hiện tại của trình soạn. */
export interface LabelPhraseSelection {
  /** Nhãn của cụm đang chọn; `null` khi vùng chọn chưa phải cụm đánh nhãn (chèn mới). */
  label: string | null;
  /** Phạm vi cụm: cả cụm đã đánh nhãn, hoặc vùng chọn khi chèn mới. */
  from: number;
  to: number;
  /** Chữ thuần của cụm. */
  text: string;
  /** Nhãn mà các cụm khác (ngoài phạm vi này) đang dùng. */
  usedByOthers: string[];
  /** Phạm vi nằm trọn trong một đoạn (mỗi cụm một phần tử `u`, spec 3.3: nhãn không lặp). */
  singleBlock: boolean;
}

/** Nhãn của mọi cụm gạch chân trong tài liệu, không tính phạm vi `[from, to]`. */
function labelsOutside(doc: PMNode, from: number, to: number): string[] {
  const labels: string[] = [];
  doc.descendants((node, pos) => {
    if (!node.isInline) return true;
    if (pos >= from && pos + node.nodeSize <= to) return false;
    for (const mark of node.marks) {
      if (mark.type.name === UNDERLINE_LABEL_MARK && typeof mark.attrs.label === "string") labels.push(mark.attrs.label);
    }
    return false;
  });
  return labels;
}

/**
 * Phân tích vùng chọn của trình soạn cho hộp và khung "Cụm đang chọn": có mark đánh nhãn trong vùng chọn (hoặc ở con
 * trỏ) thì là cụm đó, mở rộng ra cả cụm; không thì là vùng chọn thường để chèn nhãn mới.
 */
export function readLabelSelection(state: EditorState): LabelPhraseSelection {
  const { doc, selection, schema } = state;
  const markType = schema.marks[UNDERLINE_LABEL_MARK];
  let from = selection.from;
  let to = selection.to;
  let label: string | null = null;
  if (markType) {
    let anchor = -1;
    let found: string | null = null;
    if (selection.empty) {
      const mark = selection.$from.marks().find((candidate) => candidate.type === markType);
      if (mark) {
        anchor = selection.from;
        found = String(mark.attrs.label ?? "");
      }
    } else {
      doc.nodesBetween(selection.from, selection.to, (node, pos) => {
        if (anchor !== -1) return false;
        const mark = node.marks.find((candidate) => candidate.type === markType);
        if (mark) {
          anchor = Math.max(pos, selection.from);
          found = String(mark.attrs.label ?? "");
        }
        return anchor === -1;
      });
    }
    if (anchor !== -1 && found !== null) {
      const range = getMarkRange(doc.resolve(anchor), markType, { label: found });
      if (range) {
        from = range.from;
        to = range.to;
        label = found;
      }
    }
  }
  return {
    label,
    from,
    to,
    text: doc.textBetween(from, to, " "),
    usedByOthers: labelsOutside(doc, from, to),
    singleBlock: from === to || doc.resolve(from).sameParent(doc.resolve(to)),
  };
}
