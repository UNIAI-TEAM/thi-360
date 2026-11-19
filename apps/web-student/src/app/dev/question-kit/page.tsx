import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { QuestionKitDemo } from "./question-kit-demo";

export const metadata: Metadata = { title: "Question kit" };

/** Trang thử của nhà phát triển: bản production trả 404. Đi theo luật cần đăng nhập của `proxy.ts`. */
export default function QuestionKitPage() {
  if (process.env.NODE_ENV === "production") notFound();
  return <QuestionKitDemo />;
}
