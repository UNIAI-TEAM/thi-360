import type { PersonRef } from "@/features/content/types";
import { createMockStore } from "@/lib/mock/content-mock";
import type { QuestionDetail } from "../types";
import fixture from "./questions.json";

/**
 * Kho câu hỏi mẫu dùng chung cho mọi màn của Ngân hàng câu hỏi (danh sách, soạn câu, nhập từ tệp, chọn câu cho
 * bài thi). Mọi thay đổi trong phiên đi qua kho này để các màn thấy cùng một dữ liệu. Xóa khi nối backend.
 */
const data = fixture as { people: PersonRef[]; questions: QuestionDetail[] };

export const questionStore = createMockStore<QuestionDetail>(data.questions);

export const MOCK_QUESTION_AUTHORS: readonly PersonRef[] = data.people;

/** Người đang đăng nhập trong dữ liệu mẫu (tác giả của câu tạo mới). */
export const MOCK_CURRENT_AUTHOR: PersonRef = data.people[0]!;
