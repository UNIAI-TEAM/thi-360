import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { QuestionEditorView } from "@/features/questions/components/question-editor-view";
import { QuestionTypePicker } from "@/features/questions/components/question-type-picker";
import { isQuestionType } from "@/features/questions/editor-types";

export async function generateMetadata({
  searchParams,
}: {
  searchParams: Promise<{ type?: string | string[] }>;
}): Promise<Metadata> {
  const t = await getTranslations("questions.editor");
  const { type } = await searchParams;
  return { title: `${isQuestionType(typeof type === "string" ? type : null) ? t("createTitle") : t("typePicker.title")} · Thi 360` };
}

/** `/questions/new`: chọn dạng câu; `/questions/new?type=SINGLE_CHOICE`: biểu mẫu của dạng đó. */
export default async function QuestionCreatePage({
  searchParams,
}: {
  searchParams: Promise<{ type?: string | string[] }>;
}) {
  const { type } = await searchParams;
  const value = typeof type === "string" ? type : null;
  return isQuestionType(value) ? <QuestionEditorView key={value} mode="create" type={value} /> : <QuestionTypePicker />;
}
