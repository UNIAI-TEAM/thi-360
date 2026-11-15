"use client";

import type { QuestionType } from "@/features/content/types";
import { BlanksEditor } from "./question-editor-blanks";
import { ChoiceOptionsEditor } from "./question-editor-choice";
import { DragDropEditor } from "./question-editor-drag";
import type { BodyPath, ContentPath } from "./question-editor-shared";
import { StatementsEditor } from "./question-editor-statements";

/** Khối soạn đáp án theo dạng câu; dùng cho câu đơn và cho từng câu con của câu chùm. */
export function QuestionBodyEditor({
  type,
  prefix,
  contentPath,
  contentFieldId,
}: {
  type: QuestionType;
  prefix: BodyPath;
  contentPath: ContentPath;
  contentFieldId: string;
}) {
  switch (type) {
    case "SINGLE_CHOICE":
      return <ChoiceOptionsEditor prefix={prefix} multiple={false} />;
    case "MULTIPLE_CHOICE":
      return <ChoiceOptionsEditor prefix={prefix} multiple />;
    case "TRUE_FALSE":
      return <StatementsEditor prefix={prefix} />;
    case "FILL_IN":
      return <BlanksEditor prefix={prefix} contentPath={contentPath} contentFieldId={contentFieldId} />;
    case "DRAG_DROP":
      return <DragDropEditor prefix={prefix} />;
    case "QUESTION_SET":
      return null;
  }
}
