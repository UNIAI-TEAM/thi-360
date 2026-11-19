/**
 * Đếm chữ của bài Tự luận (BS 6), dùng chung cho màn làm bài, làm thử và màn chấm:
 * bỏ thẻ HTML, chuẩn hóa NFC, đổi khoảng trắng không ngắt thành khoảng trắng, tách theo
 * khoảng trắng; mỗi cụm có ít nhất một chữ cái hoặc chữ số là một chữ. Công thức
 * (`span.math-inline`, `span.math-block`) và ký hiệu đứng riêng không tính. Ranh giới khối
 * (đoạn, dòng, mục danh sách…) là khoảng trắng: `<p>a</p><p>b</p>` là hai chữ.
 */

const MATH_SPAN_RE = /<span\b[^>]*class="[^"]*\bmath-(?:inline|block)\b[^"]*"[^>]*>[\s\S]*?<\/span>/gi;
const BLOCK_END_RE = /<\/(?:p|div|li|h[1-6]|blockquote|tr|td|th|figcaption|ul|ol|table)>|<br\s*\/?>/gi;
const TAG_RE = /<[^>]*>/g;
const WORD_TOKEN_RE = /[\p{L}\p{N}]/u;
const ENTITIES: Record<string, string> = {
  "&nbsp;": " ",
  "&amp;": "&",
  "&lt;": "<",
  "&gt;": ">",
  "&quot;": '"',
  "&#39;": "'",
};

/** Chữ thuần dùng để đếm: bỏ thẻ và toàn bộ khối công thức, kể cả khi thẻ có nội dung lồng nhau. */
export function textForWordCount(html: string): string {
  const spaced = (html ?? "").replace(BLOCK_END_RE, (tag) => `${tag} `);
  let text: string;
  if (typeof DOMParser !== "undefined") {
    const doc = new DOMParser().parseFromString(`<div>${spaced}</div>`, "text/html");
    for (const el of [...doc.querySelectorAll(".math-inline, .math-block")]) el.remove();
    text = doc.body.textContent ?? "";
  } else {
    // Nhánh server (không có DOMParser): cắt khối công thức rồi bỏ mọi thẻ; đủ cho mã nguồn
    // do trình soạn phát ra (khối công thức không lồng `span` khác).
    text = spaced
      .replace(MATH_SPAN_RE, " ")
      .replace(TAG_RE, "")
      .replace(/&(?:nbsp|amp|lt|gt|quot|#39);/g, (entity) => ENTITIES[entity] ?? entity);
  }
  return text.normalize("NFC").replace(/\u00a0/g, " ");
}

export function countWords(html: string): number {
  const text = textForWordCount(html);
  let count = 0;
  for (const token of text.split(/\s+/)) {
    if (token && WORD_TOKEN_RE.test(token)) count += 1;
  }
  return count;
}
