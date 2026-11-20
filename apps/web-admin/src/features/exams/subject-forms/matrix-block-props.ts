import type { ContentReference } from "@/features/content/types";
import type { MatrixLabels } from "../components/block-matrix-editor";
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
}
