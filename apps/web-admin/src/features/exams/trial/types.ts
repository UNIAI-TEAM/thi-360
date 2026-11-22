import type { components } from "@thi360/api-client";

export type PreviewTrialPaperResponse = components["schemas"]["PreviewTrialPaperResponse"];
export type PreviewTrialPart = components["schemas"]["PreviewTrialPart"];
export type PreviewTrialBlock = components["schemas"]["PreviewTrialBlock"];
export type PreviewTrialItem = components["schemas"]["PreviewTrialItem"];
export type PreviewTrialQuestion = components["schemas"]["PreviewTrialQuestion"];
export type PreviewTrialQuestionSet = components["schemas"]["PreviewTrialQuestionSet"];
export type PreviewTrialSegment = components["schemas"]["PreviewTrialSegment"];
export type PreviewTrialAttachment = components["schemas"]["PreviewTrialAttachment"];
export type PreviewTrialResponse = components["schemas"]["PreviewTrialResponse"];

export type PreviewGradeRequest = components["schemas"]["PreviewGradeRequest"];
export type PreviewGradeResponse = components["schemas"]["PreviewGradeResponse"];
export type PreviewGradedItem = components["schemas"]["PreviewGradedItem"];
export type ReviewGradingDetail = components["schemas"]["ReviewGradingDetail"];
export type AttemptResultUnitStats = components["schemas"]["AttemptResultUnitStats"];

export type PreviewTrialAnswerValue = NonNullable<PreviewGradeRequest["answers"]>[string];
export type PreviewTrialOutcome = NonNullable<PreviewGradedItem["outcome"]>;
export type PreviewTrialQuestionType = NonNullable<PreviewTrialQuestion["type"]>;
export type PreviewTrialAnswerMap = Record<string, PreviewTrialAnswerValue>;
