import type { QuestionDetail } from "@/features/questions/types";
import { createMockStore } from "@/lib/mock/content-mock";
import fixture from "./questions.json";
import examsFixture from "./questions-exams.json";

/**
 * Kho câu hỏi mẫu của hàng chờ chấm `/grading`: đề, tiêu chí chấm và đoạn đọc hiểu của câu tự luận
 * (đợt chấm điểm sẽ nối API thật và xóa kho này). Đọc bởi lớp dữ liệu mẫu của grading.
 */
const data = fixture as { questions: QuestionDetail[] };

export const questionStore = createMockStore<QuestionDetail>([...data.questions, ...(examsFixture.questions as QuestionDetail[])]);
