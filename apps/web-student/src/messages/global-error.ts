import type { AppLocale } from "@thi360/web-kit/preferences";

/**
 * Chuỗi của trang lỗi toàn cục (`app/global-error.tsx`). Trang này thay thế cả layout gốc nên không có ngữ cảnh
 * next-intl; chuỗi phải nằm sẵn trong gói mã phía trình duyệt. Chỉ giữ vài chuỗi ở đây thay vì nhập cả `vi.json` và
 * `en.json` vào gói đó. Nội dung phải trùng với `errorPage.*` và `common.*` của hai tệp dịch (có kiểm thử so khớp).
 */
export interface GlobalErrorMessages {
  eyebrow: string;
  title: string;
  description: string;
  traceLabel: string;
  retry: string;
  backHome: string;
}

export const GLOBAL_ERROR_MESSAGES: Record<AppLocale, GlobalErrorMessages> = {
  vi: {
    eyebrow: "Lỗi hệ thống",
    title: "Đã có lỗi máy chủ",
    description: "Đã xảy ra lỗi không mong muốn khi xử lý yêu cầu. Vui lòng thử lại sau.",
    traceLabel: "Khi cần hỗ trợ, gửi kèm mã này: {traceId}",
    retry: "Thử lại",
    backHome: "Về trang chủ",
  },
  en: {
    eyebrow: "System error",
    title: "Server error",
    description: "An unexpected error occurred while processing your request. Please try again later.",
    traceLabel: "If you contact support, include this code: {traceId}",
    retry: "Retry",
    backHome: "Back to home",
  },
};
