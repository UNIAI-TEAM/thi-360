import type { components } from "@thi360/api-client";
import checkBcaRun from "./fixtures/check-bca-run.json";
import checkHsa001 from "./fixtures/check-hsa-f-001.json";
import checkHsa002Run from "./fixtures/check-hsa-f-002-run.json";
import checkThptqgTinRun from "./fixtures/check-thptqg-tin-run.json";
import previewBcaCa1 from "./fixtures/preview-bca-ca1.json";
import previewHsa000SciencePcg from "./fixtures/preview-hsa-f-000-science-pcg.json";
import previewHsa001En from "./fixtures/preview-hsa-f-001-en.json";
import previewHsa001ScienceInvalid from "./fixtures/preview-hsa-f-001-science-invalid.json";

type Schema = components["schemas"];

/**
 * Kết quả kiểm tra và đề xem trước dạng wire cho test màn Kiểm tra và Đề xem trước. Máy chủ mới là nơi tính (kiểm tra
 * cấu hình, sinh đề từ kho câu hỏi), client chỉ hiển thị, nên test nạp kết quả chụp sẵn thay vì tính lại. Mỗi tệp
 * trong `fixtures/` là kết quả của bài mẫu cùng tên (bỏ trường `null` như máy chủ), chụp từ bộ tính trong bộ nhớ
 * của nhánh mẫu lúc dựng test.
 */
export const HSA_001_CHECK = checkHsa001 as unknown as Schema["ExamCheckResult"];
export const HSA_002_RUN = checkHsa002Run as unknown as Schema["ExamCheckResult"];
export const BCA_RUN = checkBcaRun as unknown as Schema["ExamCheckResult"];
export const THPTQG_TIN_RUN = checkThptqgTinRun as unknown as Schema["ExamCheckResult"];

export const PREVIEW_HSA_001_EN = previewHsa001En as unknown as Schema["ExamPreviewResult"];
export const PREVIEW_HSA_001_SCIENCE_INVALID = previewHsa001ScienceInvalid as unknown as Schema["ExamPreviewResult"];
export const PREVIEW_HSA_000_SCIENCE_PCG = previewHsa000SciencePcg as unknown as Schema["ExamPreviewResult"];
export const PREVIEW_BCA_CA1 = previewBcaCa1 as unknown as Schema["ExamPreviewResult"];
