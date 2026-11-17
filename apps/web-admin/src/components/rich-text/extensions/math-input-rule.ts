/**
 * Input rule `$…$` → công thức cùng dòng, `$$…$$` → khối, `\$` giữ ký tự (spec 4.1, ED-1). Tệp rỗng
 * do ED-0b đăng ký sẵn trong trình soạn để ED-1 chỉ sửa file này.
 */
import { Extension } from "@tiptap/core";

export const MathInputRule = Extension.create({ name: "mathInputRule" });
