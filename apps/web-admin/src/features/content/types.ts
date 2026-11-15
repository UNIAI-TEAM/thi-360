/**
 * Kiểu dữ liệu dùng chung của Ngân hàng câu hỏi và Bài thi (đợt 2, dữ liệu mẫu).
 *
 * Mã môn, khối, độ khó, nhãn là mã giá trị của danh mục dùng chung (PBI-02); khi nối backend lấy từ API lựa chọn của
 * danh mục. Cây kiến thức là danh mục dạng cây "Cấu trúc kiến thức"; `code` là MAPID.
 */

/** Sáu dạng câu theo bản vẽ (BA ghi bốn dạng; đang chờ BA chốt, xem UNI-2590). */
export const QUESTION_TYPES = [
  "SINGLE_CHOICE",
  "MULTIPLE_CHOICE",
  "TRUE_FALSE",
  "FILL_IN",
  "DRAG_DROP",
  "QUESTION_SET",
] as const;
export type QuestionType = (typeof QUESTION_TYPES)[number];

/** Trạng thái câu hỏi theo UniNote PBI-03 mục 9: Nháp, Sẵn sàng, Ngừng sử dụng. */
export const QUESTION_STATUSES = ["DRAFT", "READY", "RETIRED"] as const;
export type QuestionStatus = (typeof QUESTION_STATUSES)[number];

export interface CatalogOption {
  code: string;
  name: string;
}

/** Môn › Chương › Bài › Kiến thức; câu hỏi chỉ gắn được ở cấp Kiến thức. */
export type KnowledgeLevel = "SUBJECT" | "CHAPTER" | "LESSON" | "KNOWLEDGE";

export interface KnowledgeNode {
  id: string;
  /** MAPID, duy nhất trong cây. */
  code: string;
  name: string;
  level: KnowledgeLevel;
  subjectCode: string;
  /** Lớp của nút từ cấp Chương trở xuống; nút môn để `null`. */
  gradeCode: string | null;
  active: boolean;
  children: KnowledgeNode[];
}

export interface ContentReference {
  subjects: CatalogOption[];
  grades: CatalogOption[];
  difficulties: CatalogOption[];
  tags: CatalogOption[];
  /** Mã tổ hợp (danh mục kiểu Tổ hợp), dùng cho Phần 3 của bài thi. */
  combinations: CombinationOption[];
  /** Mỗi phần tử là một môn (nút gốc). */
  knowledgeTree: KnowledgeNode[];
}

export interface CombinationOption {
  code: string;
  name: string;
  subjectCodes: string[];
}

/** Kiến thức gắn với câu hỏi; đúng một mục `primary` khi có ít nhất một mục. */
export interface KnowledgeRef {
  nodeId: string;
  code: string;
  name: string;
  primary: boolean;
}

export interface PersonRef {
  id: string;
  name: string;
}
