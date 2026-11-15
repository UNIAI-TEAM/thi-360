import { createMockStore } from "@/lib/mock/content-mock";
import type { ExamDetail, ExamTemplate } from "../types";
import fixture from "./exams.json";

/**
 * Kho bài thi mẫu dùng chung cho mọi màn của Bài thi (danh sách, soạn cấu trúc, kiểm tra, công bố).
 * Mọi thay đổi trong phiên đi qua kho này. Xóa khi nối backend.
 */
const data = fixture as { exams: ExamDetail[]; templates: ExamTemplate[] };

export const examStore = createMockStore<ExamDetail>(data.exams);

export const MOCK_EXAM_TEMPLATES: readonly ExamTemplate[] = data.templates;
