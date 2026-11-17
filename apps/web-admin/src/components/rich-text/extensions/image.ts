import { Image } from "@tiptap/extension-image";

/** Ảnh trong nội dung: `img` thường của TipTap cộng `data-asset-id` (backend viết lại `src` từ id). */
export const RichTextImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      dataAssetId: {
        default: null,
        parseHTML: (element: HTMLElement) => element.getAttribute("data-asset-id"),
        renderHTML: (attributes: Record<string, unknown>) =>
          attributes.dataAssetId ? { "data-asset-id": attributes.dataAssetId as string } : {},
      },
    };
  },
});
