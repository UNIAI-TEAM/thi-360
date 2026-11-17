/**
 * Menu `/` mở bảng mục chèn (spec 4.1, ED-1). Tệp rỗng do ED-0b đăng ký sẵn trong trình soạn để
 * ED-1 chỉ sửa file này, không đụng `rich-text-editor.tsx`.
 */
import { Extension } from "@tiptap/core";

export const SlashMenu = Extension.create({ name: "slashMenu" });
