/** Biến `NEXT_PUBLIC_*` của web admin (xem `apps/web-admin/.env.example`). */
declare namespace NodeJS {
  interface ProcessEnv {
    /** Web học viên: nơi có trang đăng nhập. */
    NEXT_PUBLIC_THI360_WEB_URL?: string;
    /** `true` hoặc `false`: dữ liệu mẫu cho PBI-03, PBI-04 (Ngân hàng câu hỏi, Nhập câu hỏi, Bài thi). */
    NEXT_PUBLIC_THI360_CONTENT_MOCK?: string;
    /** `true` hoặc `false`: dữ liệu mẫu cho hàng chờ chấm `/grading`. */
    NEXT_PUBLIC_THI360_GRADING_MOCK?: string;
  }
}
