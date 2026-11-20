package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kết quả ghi nhận một câu trả lời cuối cùng khi hoàn thành phần hoặc nộp bài")
public enum FinalAnswerStatus {
    SAVED,
    STALE,
    REJECTED
}
