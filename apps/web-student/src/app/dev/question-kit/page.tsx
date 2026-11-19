import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import type { Metadata } from "next";
import { getLocale } from "next-intl/server";
import { notFound } from "next/navigation";
import { QuestionKitDemo } from "./question-kit-demo";
import { KIT_CHROME } from "./samples";

/** Tiêu đề tab theo ngôn ngữ giao diện (từ `KIT_CHROME`), để phép kiểm lẫn ngôn ngữ đọc được `document.title`. */
export async function generateMetadata(): Promise<Metadata> {
  return { title: KIT_CHROME[normalizeUiLocale(await getLocale())].title };
}

/** Trang thử của nhà phát triển: bản production trả 404. Đi theo luật cần đăng nhập của `proxy.ts`. */
export default function QuestionKitPage() {
  if (process.env.NODE_ENV === "production") notFound();
  return <QuestionKitDemo />;
}
