import type { ContentReference } from "@/features/content/types";
import type { MatrixLabels } from "../components/block-matrix-editor";
import type { TopicQuotaServerError } from "../components/topic-quota-table";
import type { MatrixAxes } from "../matrix";
import type { ExamSection } from "../types";
import type { MatrixDefaultSnapshot } from "./matrix-form-model";
import type { SubjectFormEntry } from "./registry";

interface MatrixRawFields {
  /** Mục đang giữ ở ô (chữ đã gõ và mã lỗi), hoặc `null`. */
  get: (field: string) => { text: string; code: string } | null;
  /** Có mục nhập thô ở một ô có tên bắt đầu bằng `prefix` (ví dụ `GRADE:` cho mọi ô khối lớp): để hiện lại hàng mà người dùng đã gõ sai. */
  hasPrefix: (prefix: string) => boolean;
  set: (field: string, text: string, code: string) => void;
  clear: (field: string) => void;
  clearPrefix?: (prefix: string) => void;
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
  templateLocked: boolean;
  raw: MatrixRawFields;
  /** Lỗi 422 từ máy chủ của bảng chỉ tiêu chủ đề (`MAX_TOPIC_QUOTAS` / `PERCENT_OUT_OF_RANGE`). */
  topicError?: TopicQuotaServerError;
  /** Bản chụp bộ dòng thành phần mặc định của môn để khôi phục nhanh. */
  defaultSnapshot?: MatrixDefaultSnapshot | null;
}
export interface MatrixKindSlotOptions {
  placement?: "before-structure" | "after-structure";
  structureVariant?: "default" | "compact";
}

export type MatrixKindSlotOptionsResolver = MatrixKindSlotOptions | ((section: ExamSection) => MatrixKindSlotOptions);
