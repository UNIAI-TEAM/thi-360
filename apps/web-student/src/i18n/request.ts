import { getRequestConfig } from "next-intl/server";
import messages from "../messages/vi.json";

export default getRequestConfig(async () => ({
  locale: "vi",
  messages,
  timeZone: "Asia/Ho_Chi_Minh",
}));
