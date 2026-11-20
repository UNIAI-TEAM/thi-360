import { registerSubjectFormRenderer } from "./form-renderers";
import { RowListForm } from "./row-list-form";

/**
 * Đăng ký màn Danh sách dòng cho dạng ROWS khi nạp module (plan B1: CR-3 đăng ký DS). `registry.ts` nạp tệp này, nên mọi nơi tra bảng
 * đăng ký (khung, selection, blocking) đều đã có màn; không sửa `form-renderers.ts` của CR-1.
 */
registerSubjectFormRenderer("ROWS", RowListForm);
