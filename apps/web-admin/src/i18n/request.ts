import { getRequestConfig } from "next-intl/server";

/** Đợt 1 chỉ có tiếng Việt; chuỗi hiển thị nằm ở `src/messages/vi.json`. */
export default getRequestConfig(async () => ({
  locale: "vi",
  messages: (await import("../messages/vi.json")).default,
  timeZone: "Asia/Ho_Chi_Minh",
}));
