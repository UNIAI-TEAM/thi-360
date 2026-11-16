import examsFixture from "../mock/exams.json";
import type { ExamDetail, ExamTemplate } from "../types";

/**
 * Bài thi và mẫu cấu trúc mẫu dùng làm dữ liệu cho test (ExamsBackend dựng bài từ đây; test logic đọc cấu trúc mẫu).
 * Đây là chỗ DUY NHẤT test nhập `mock/exams.json`: khi QA bỏ lớp mẫu, chuyển tệp JSON sang `__tests__/fixtures/` và sửa
 * một dòng nhập này.
 */
export const EXAMS_FIXTURE = examsFixture as unknown as {
  exams: ExamDetail[];
  templates: ExamTemplate[];
  partNames: Record<string, string>;
};

export const FIXTURE_TEMPLATES: readonly ExamTemplate[] = EXAMS_FIXTURE.templates;
