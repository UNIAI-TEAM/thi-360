import examsFixture from "./fixtures/exams.json";
import type { ExamDetail, ExamTemplate } from "../types";

/**
 * Bài thi và mẫu cấu trúc mẫu dùng làm dữ liệu cho test (ExamsBackend dựng bài từ đây; test logic đọc cấu trúc mẫu).
 * Đây là chỗ DUY NHẤT test nhập `fixtures/exams.json` (N2-3, QA: JSON đã chuyển từ `mock/` khi xóa lớp mẫu).
 */
export const EXAMS_FIXTURE = examsFixture as unknown as {
  exams: ExamDetail[];
  templates: ExamTemplate[];
  partNames: Record<string, string>;
};

export const FIXTURE_TEMPLATES: readonly ExamTemplate[] = EXAMS_FIXTURE.templates;
