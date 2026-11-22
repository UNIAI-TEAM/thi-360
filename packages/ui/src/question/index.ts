/**
 * `@thi360/ui/question` — thành phần câu hỏi của đợt 3 (plan FS-4, LT 8, BE 4.3):
 * 8 dạng câu, chế độ `answer` (làm bài) và `review` (xem lại), đáp án đúng hình
 * `answer` của `attempt_item` (LT 4). Mỗi nội dung đề được đánh dấu `data-exam-content`.
 */
export { Question, type QuestionProps } from "./question";
export { SingleChoiceQuestion, MultipleChoiceQuestion } from "./choice-body";
export { ShortAnswerQuestion } from "./short-answer";
export {
  AttemptAudioPlayer,
  AudioPlayer,
  type AttemptAudioPlayerProps,
} from "./audio-player/audio-player";
export {
  createTrialAudioAdapter,
  type CreateTrialAudioAdapterOptions,
} from "./audio-player/trial-adapter";
export {
  AudioAdapterError,
  type AudioAdapterErrorCode,
  type AudioAdapterErrorOptions,
  type AudioClosedReason,
  type AudioEndReason,
  type AudioPlayAdapter,
  type AudioPlaybackSnapshot,
  type AudioPlayState,
} from "./audio-player/types";
export { SymbolInputBar, type SymbolInputBarProps } from "./symbol-input/symbol-input-bar";
export {
  DEFAULT_ION_SYMBOLS,
  insertTextAtCursor,
  shouldShowSymbolInput,
  type SymbolGroup,
  type SymbolKey,
} from "./symbol-input/symbols";
export { ClozeQuestion, ClozePassage } from "./cloze";
export { TrueFalseQuestion } from "./true-false";
export { MatchingQuestion } from "./matching";
export { EssayQuestion, type EssayEditorProps } from "./essay";
export { QuestionSet, type QuestionSetProps, type ChildQuestionProps } from "./question-set";
export {
  QuestionFrame,
  OutcomeChip,
  OutcomeGlyph,
  ReviewPoints,
  toggleButtonClass,
} from "./question-frame";
export type { KitQuestionProps } from "./shared";
export { resolveReview, childReview, answersVisible } from "./shared";
export { countWords, textForWordCount } from "./count-words";
export {
  optionLetter,
  matchLetter,
  statementLetter,
  orderById,
  htmlToText,
  plainTextToHtml,
  htmlToPlainText,
  formatScore,
} from "./helpers";
export type {
  AnswerFormat,
  AnswerValue,
  ChoiceOption,
  ClozeAnswer,
  ClozeBlank,
  EssayAnswer,
  EssayCriterion,
  EssayGrading,
  KitQuestion,
  KitQuestionOf,
  MatchingAnswer,
  MatchingItem,
  MatchingPair,
  MultipleChoiceAnswer,
  MultipleChoiceRule,
  PartResult,
  QuestionBody,
  QuestionMode,
  QuestionOutcome,
  QuestionReview,
  QuestionSetAnswer,
  QuestionType,
  ShortAnswerAnswer,
  SingleChoiceAnswer,
  TrueFalseAnswer,
  TrueFalseMethod,
  TrueFalseStatement,
} from "./types";
