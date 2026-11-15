"use client";

import { Button, StatusPage } from "@thi360/ui";
import { readPreferenceCookies } from "@thi360/web-kit/preferences";
import { ServerCrash } from "lucide-react";
import { useSyncExternalStore } from "react";
import { GLOBAL_ERROR_MESSAGES } from "@/messages/global-error";
import "./globals.css";

const subscribeNever = () => () => {};
const readBrowserCookies = () => document.cookie;
const readNoCookies = () => "";

/**
 * Trang lỗi toàn cục: hiện khi chính layout gốc lỗi, nên tự dựng `<html>`, `<body>` và nạp `globals.css`.
 * Không có ngữ cảnh next-intl hay cấu hình giao diện: ngôn ngữ và chế độ sáng/tối đọc từ cookie của người dùng
 * (máy chủ dựng bản mặc định tiếng Việt, chế độ sáng), màu dùng bộ mặc định trong token.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const cookieString = useSyncExternalStore(subscribeNever, readBrowserCookies, readNoCookies);
  const { locale, theme } = readPreferenceCookies(cookieString);
  const t = GLOBAL_ERROR_MESSAGES[locale];

  return (
    <html lang={locale} data-theme={theme ?? "light"}>
      <body className="font-sans antialiased">
        <title>{t.title}</title>
        <StatusPage
          icon={ServerCrash}
          eyebrow={t.eyebrow}
          title={t.title}
          description={t.description}
          meta={error.digest ? t.traceLabel.replace("{traceId}", error.digest) : undefined}
          actions={
            <>
              <Button size="lg" onClick={reset}>
                {t.retry}
              </Button>
              {/* Tải lại hẳn trang chủ: bộ định tuyến phía trình duyệt có thể đã hỏng cùng layout gốc. */}
              <Button variant="secondary" size="lg" onClick={() => window.location.assign("/")}>
                {t.backHome}
              </Button>
            </>
          }
        />
      </body>
    </html>
  );
}
