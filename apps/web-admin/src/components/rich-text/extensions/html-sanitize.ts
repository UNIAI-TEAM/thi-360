import { Extension } from "@tiptap/core";
import { sanitizeQuestionHtml } from "../sanitize";

/**
 * Lọc HTML dán/thả vào soạn theo đúng danh sách cho phép của backend trước khi ProseMirror parse.
 * `transformPastedHTML` của TipTap cũng chạy cho HTML được thả (drop) — một điểm lọc duy nhất.
 */
export const HtmlSanitize = Extension.create<{ allowImages: boolean }>({
  name: "htmlSanitize",

  addOptions() {
    return { allowImages: true };
  },

  transformPastedHTML(html: string) {
    return sanitizeQuestionHtml(html, { allowImages: this.options.allowImages }).html;
  },
});
