import { clsx, type ClassValue } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

// Cỡ chữ và bóng tự đặt tên trong tokens.css; không khai báo ở đây thì tailwind-merge
// coi `text-caption` là màu chữ và xóa nhầm `text-text-muted` đi kèm.
const twMerge = extendTailwindMerge({
  extend: {
    classGroups: {
      "font-size": [
        {
          text: [
            "display",
            "h1",
            "h2",
            "h3",
            "title",
            "body",
            "label",
            "field-label",
            "caption",
            "overline",
            "page-title",
          ],
        },
      ],
      shadow: [{ shadow: ["primary"] }],
    },
  },
});

/** Gộp class Tailwind, class viết sau thắng class viết trước. */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
