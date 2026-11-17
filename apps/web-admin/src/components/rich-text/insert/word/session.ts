/**
 * Trạng thái xem lại của một lần "Dán từ Word" (spec 4.10): công thức đã đổi + tự sửa chờ xác nhận, công thức cần gõ
 * lại, ảnh chưa đưa vào. Mọi hàm thuần, không đụng DOM thật ngoài việc đọc cây khối; hộp thoại giữ một `WordReview` trong
 * state và thay nó sau mỗi thao tác.
 */
import { autoFix, type FixKind } from "./fixers";
import { materializeBlocks, normalizeEditorHtml, topLevelBlocks, type FormulaResolver } from "./materialize";
import { FORMULA_ATTRIBUTE, RETYPE_ATTRIBUTE } from "./parse";
import { keepWhole, splitByLabels } from "./split";
import type { OptionLabel, ParsedWord, SplitFields, WordImage } from "./types";

/** `ok`: không có chỗ nào cần sửa; `pending`: đã tự sửa, chờ xác nhận; `accepted`/`edited`: người soạn đã duyệt. */
export type FormulaStatus = "ok" | "pending" | "accepted" | "edited";

export interface ReviewFormula {
  kind: "formula";
  id: string;
  display: boolean;
  /** LaTeX thô từ OMML ("Sau khi đổi từ Word"). */
  raw: string;
  /** LaTeX hiện dùng khi điền vào biểu mẫu: bản đã tự sửa, hoặc bản người soạn sửa tay. */
  latex: string;
  fixes: FixKind[];
  status: FormulaStatus;
}

export type RetypeResolution = { type: "typed"; latex: string } | { type: "removed" };

export interface ReviewRetype {
  kind: "retype";
  id: string;
  cause: "image" | "unsupported" | "malformed";
  detail?: string;
  context: string;
  /** `null`: chưa xử lý ("Đưa vào biểu mẫu" bị tắt). */
  resolution: RetypeResolution | null;
}

export type ReviewItem = ReviewFormula | ReviewRetype;

export interface WordReview {
  /** HTML nội bộ của `parseWordHtml`. */
  html: string;
  items: ReviewItem[];
  images: WordImage[];
}

/** Một ô của biểu mẫu mà nội dung dán được tách vào. */
export type FieldRef = { field: "stem" } | { field: "option"; label: OptionLabel } | { field: "solution" };

export interface ReviewSummary {
  /** Công thức đã đổi đúng: không phải sửa, hoặc đã được duyệt. */
  converted: number;
  /** Đã tự sửa, chờ xác nhận. */
  pending: number;
  /** Cần gõ lại mà chưa xử lý (ảnh công thức, OMML ngoài tập con). */
  retype: number;
}

/** Bắt đầu xem lại: chạy bốn bộ tự sửa lên LaTeX thô của từng công thức. */
export function startReview(parsed: ParsedWord): WordReview {
  const items = parsed.formulas.map<ReviewItem>((formula) => {
    if (formula.kind === "retype") {
      return { kind: "retype", id: formula.id, cause: formula.cause, detail: formula.detail, context: formula.context, resolution: null };
    }
    const { latex, kinds } = autoFix(formula.latex);
    return {
      kind: "formula",
      id: formula.id,
      display: formula.display,
      raw: formula.latex,
      latex,
      fixes: kinds,
      status: kinds.length > 0 ? "pending" : "ok",
    };
  });
  return { html: parsed.html, items, images: parsed.images };
}

function updateItem(review: WordReview, id: string, change: (item: ReviewItem) => ReviewItem): WordReview {
  return { ...review, items: review.items.map((item) => (item.id === id ? change(item) : item)) };
}

/** "Chấp nhận": giữ bản đã tự sửa. */
export function acceptFix(review: WordReview, id: string): WordReview {
  return updateItem(review, id, (item) => (item.kind === "formula" && item.status === "pending" ? { ...item, status: "accepted" } : item));
}

/** "Chấp nhận mọi chỗ tự sửa". */
export function acceptAllFixes(review: WordReview): WordReview {
  return { ...review, items: review.items.map((item) => (item.kind === "formula" && item.status === "pending" ? { ...item, status: "accepted" } : item)) };
}

/** "Sửa tay": người soạn đặt LaTeX của chỗ này; coi như đã duyệt. */
export function editFormula(review: WordReview, id: string, latex: string): WordReview {
  return updateItem(review, id, (item) => (item.kind === "formula" ? { ...item, latex: latex.trim(), status: "edited" } : item));
}

/** "Gõ lại công thức": công thức mới do hộp công thức toán trả về. */
export function retypeFormula(review: WordReview, id: string, latex: string): WordReview {
  return updateItem(review, id, (item) => (item.kind === "retype" ? { ...item, resolution: { type: "typed", latex: latex.trim() } } : item));
}

/** "Bỏ": xóa chỗ này khỏi nội dung. */
export function removeFormula(review: WordReview, id: string): WordReview {
  return updateItem(review, id, (item) => (item.kind === "retype" ? { ...item, resolution: { type: "removed" } } : item));
}

export function summarize(review: WordReview): ReviewSummary {
  const summary: ReviewSummary = { converted: 0, pending: 0, retype: 0 };
  for (const item of review.items) {
    if (item.kind === "retype") {
      if (item.resolution === null) summary.retype += 1;
    } else if (item.status === "pending") {
      summary.pending += 1;
    } else {
      summary.converted += 1;
    }
  }
  return summary;
}

/** Còn công thức cần gõ lại chưa xử lý: "Đưa vào biểu mẫu" tắt. */
export function hasUnresolved(review: WordReview): boolean {
  return review.items.some((item) => item.kind === "retype" && item.resolution === null);
}

/** LaTeX của những chỗ tự sửa còn chờ xác nhận: được đánh dấu bằng decoration sau khi điền vào biểu mẫu. */
export function pendingLatexes(review: WordReview): string[] {
  return review.items.flatMap((item) => (item.kind === "formula" && item.status === "pending" ? [item.latex] : []));
}

/** Công thức dùng cho một chỗ giữ: bản hiện tại, bản gõ lại, hoặc `null` (bỏ / chưa xử lý). */
export function resolverOf(review: WordReview): FormulaResolver {
  const byId = new Map(review.items.map((item) => [item.id, item]));
  return (id) => {
    const item = byId.get(id);
    if (!item) return null;
    if (item.kind === "formula") return { latex: item.latex, display: item.display };
    return item.resolution?.type === "typed" ? { latex: item.resolution.latex, display: false } : null;
  };
}

/** Tách nội dung thành các ô (hoặc giữ nguyên một ô khi `split` tắt). */
export function splitReview(review: Pick<WordReview, "html">, split: boolean): SplitFields {
  const blocks = topLevelBlocks(review.html);
  return split ? splitByLabels(blocks) : keepWhole(blocks);
}

function hasPlaceholder(blocks: Element[], id: string): boolean {
  return blocks.some((block) => block.querySelector(`[${FORMULA_ATTRIBUTE}="${id}"], [${RETYPE_ATTRIBUTE}="${id}"]`) !== null);
}

/** Công thức `id` nằm ở ô nào sau khi tách. */
export function fieldOf(fields: SplitFields, id: string): FieldRef {
  if (hasPlaceholder(fields.solution, id)) return { field: "solution" };
  for (const option of fields.options) if (hasPlaceholder(option.blocks, id)) return { field: "option", label: option.label };
  return { field: "stem" };
}

export interface WordFields {
  /** HTML hợp đồng của đề bài (hoặc của toàn bộ nội dung khi không tách). */
  stem: string;
  options: { label: OptionLabel; html: string }[];
  solution: string;
  /** Có tìm thấy nhãn để tách. */
  matched: boolean;
}

function fieldHtml(blocks: Element[], resolve: FormulaResolver): string {
  const html = normalizeEditorHtml(materializeBlocks(blocks, resolve));
  return html === "<p></p>" ? "" : html;
}

/** HTML hợp đồng của từng ô, theo trạng thái xem lại hiện tại. */
export function buildFields(review: WordReview, split: boolean): WordFields {
  const resolve = resolverOf(review);
  const fields = splitReview(review, split);
  return {
    stem: fieldHtml(fields.stem, resolve),
    options: fields.options.map((option) => ({ label: option.label, html: fieldHtml(option.blocks, resolve) })),
    solution: fieldHtml(fields.solution, resolve),
    matched: fields.found,
  };
}
