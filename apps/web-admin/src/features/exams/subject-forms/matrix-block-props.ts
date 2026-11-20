import type { ContentReference } from "@/features/content/types";
import type { MatrixLabels } from "../components/block-matrix-editor";
import type { TopicQuotaServerError } from "../components/topic-quota-table";
import type { MatrixAxes } from "../matrix";
import type { ExamSection } from "../types";
import type { SubjectFormEntry } from "./registry";

/**
 * Nhập thô của một ô của MT (FTM R-15): chữ đã gõ mà chưa ghi được vào bản nháp (số sai, tổng % chưa đủ 100, Trắc nghiệm nhỏ hơn câu
 * con). Mục nằm trong kho nhập thô của view theo `sectionId`, nên chặn lưu và hỏi trước khi rời trang dù màn đã đóng.
 */
export interface MatrixRawFields {
  /** Mục đang giữ ở ô (chữ đã gõ và mã lỗi), hoặc `null`. */
  get: (field: string) => { text: string; code: string } | null;
  /** Có mục nhập thô ở một ô có tên bắt đầu bằng `prefix` (ví dụ `GRADE:` cho mọi ô khối lớp): để hiện lại hàng mà người dùng đã gõ sai. */
  hasPrefix: (prefix: string) => boolean;
  set: (field: string, text: string, code: string) => void;
  clear: (field: string) => void;
}

/** Dữ liệu chung của các khối của MT (`matrix-form.tsx` dựng, các khối vẽ). */
export interface MatrixBlockProps {
  section: ExamSection;
  entry: SubjectFormEntry;
  kindCode: string;
  subjectName: string;
  /** Số câu của mục: mẫu số của tỷ lệ; `null` khi chưa biết. */
  total: number | null;
  axes: MatrixAxes;
  labels: MatrixLabels;
  reference: ContentReference;
  onChange: (update: (section: ExamSection) => ExamSection) => void;
  /** Mẫu khóa số câu của mục (H27, V-ACT FULL): số câu của nhóm câu đơn duy nhất chỉ đọc. */
  templateLocked: boolean;
  raw: MatrixRawFields;
  /** Lỗi 422 từ máy chủ của bảng chỉ tiêu chủ đề (`MAX_TOPIC_QUOTAS` / `PERCENT_OUT_OF_RANGE`). */
  topicError?: TopicQuotaServerError;
}
/**
 * Tuỳ chọn của chỗ gắn theo kỳ (`matrix-form.tsx`, `registerMatrixKindSlot`; chỗ gắn riêng theo kỳ của CR-2H).
 *
 * - `placement`: `"after-structure"` (mặc định, giữ đúng hành vi QDA, BCA, V-ACT: phần riêng nằm sau Cấu trúc câu, trước Khối lớp và
 *   Độ khó); `"before-structure"` vẽ phần riêng trước Cấu trúc câu, dưới thông báo tổng đổi (dành cho Mạch kiến thức của HSA Sử,
 *   Địa theo UNI-1706 §4-5).
 * - `structureVariant`: `"default"` giữ khối Cấu trúc câu hiện tại; `"compact"` cho bản gọn (câu đơn, chùm × câu con trên một khối
 *   gọn, không câu nhắc, các ô xếp ngang) — chỉ khi kỳ cần phần riêng đứng trước nó.
 */
export interface MatrixKindSlotOptions {
  placement?: "before-structure" | "after-structure";
  structureVariant?: "default" | "compact";
}

export type MatrixKindSlotOptionsResolver = MatrixKindSlotOptions | ((section: ExamSection) => MatrixKindSlotOptions);
