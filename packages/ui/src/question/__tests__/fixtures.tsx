import { useState, type ReactNode } from "react";
import { Question, type QuestionProps } from "../question";
import type { AnswerValue, KitQuestion, QuestionReview } from "../types";

export const single: KitQuestion = {
  id: "q-single",
  displayNo: 1,
  points: 1,
  stem: "<p>Thủ đô của Việt Nam là gì?</p>",
  body: {
    type: "SINGLE_CHOICE",
    options: [
      { id: "o1", content: "<p>Hà Nội</p>", correct: true },
      { id: "o2", content: "<p>Huế</p>" },
      { id: "o3", content: "<p>Đà Nẵng</p>" },
      { id: "o4", content: "<p>Cần Thơ</p>" },
    ],
  },
};

export const multiple: KitQuestion = {
  id: "q-multi",
  displayNo: 2,
  points: 1,
  stem: "<p>Những số nào là số nguyên tố?</p>",
  body: {
    type: "MULTIPLE_CHOICE",
    scoringRule: "ALL_OR_NOTHING",
    options: [
      { id: "m1", content: "<p>2</p>", correct: true },
      { id: "m2", content: "<p>4</p>" },
      { id: "m3", content: "<p>7</p>", correct: true },
    ],
  },
};

export const shortAnswer: KitQuestion = {
  id: "q-short",
  displayNo: 3,
  points: 1,
  stem: "<p>Tính 7 x 6.</p>",
  body: { type: "SHORT_ANSWER", answers: ["42"], inputHint: "Số nguyên", answerFormat: "INTEGER" },
};

export const cloze: KitQuestion = {
  id: "q-cloze",
  displayNo: 4,
  points: 2,
  body: {
    type: "CLOZE",
    passage: "<p>Nước sôi ở [[b1]] độ C và đóng băng ở [[b2]] độ C.</p>",
    blanks: [
      { id: "b1", answers: ["100"], points: 1 },
      { id: "b2", answers: ["0"], points: 1 },
    ],
  },
};

export const trueFalse: KitQuestion = {
  id: "q-tf",
  displayNo: 6,
  points: 1,
  stem: "<p>Đúng hay sai?</p>",
  body: {
    type: "TRUE_FALSE",
    scoringMethod: "PER_STATEMENT",
    statements: [
      { id: "s1", content: "<p>Hai đường thẳng song song thì đồng phẳng.</p>", answer: true, points: 0.25 },
      { id: "s2", content: "<p>Hai đường thẳng chéo nhau thì có điểm chung.</p>", answer: false, points: 0.25 },
    ],
  },
};

export const matching: KitQuestion = {
  id: "q-match",
  displayNo: 7,
  points: 1,
  stem: "<p>Ghép đôi.</p>",
  body: {
    type: "MATCHING",
    left: [
      { id: "l1", content: "<p>Pa (pascal)</p>" },
      { id: "l2", content: "<p>Joule</p>" },
    ],
    right: [
      { id: "r1", content: "<p>Áp suất</p>" },
      { id: "r2", content: "<p>Năng lượng</p>" },
      { id: "r3", content: "<p>Công suất</p>" },
    ],
    pairs: [
      { leftId: "l1", rightId: "r1" },
      { leftId: "l2", rightId: "r2" },
    ],
    reuseRight: false,
  },
};

export const essay: KitQuestion = {
  id: "q-essay",
  displayNo: 8,
  points: 25,
  stem: "<p>Phân tích hình tượng người lính.</p>",
  body: { type: "ESSAY", wordLimit: 5 },
};

export const setChildA: KitQuestion = { ...single, id: "q-set-a", displayNo: 9 };
export const setChildB: KitQuestion = { ...shortAnswer, id: "q-set-b", displayNo: 10 };

export const questionSet: KitQuestion = {
  id: "q-set",
  displayNo: 9,
  points: 2,
  body: {
    type: "QUESTION_SET",
    passage: "<p>Ngữ liệu chung của bộ câu hỏi.</p>",
    childIds: ["q-set-a", "q-set-b"],
  },
};

export const all: { question: KitQuestion; value: AnswerValue }[] = [
  { question: single, value: { optionId: "o1" } },
  { question: multiple, value: { optionIds: ["m1", "m3"] } },
  { question: shortAnswer, value: { text: "42" } },
  { question: cloze, value: { blanks: { b1: "100", b2: "0" } } },
  { question: trueFalse, value: { statements: { s1: true, s2: false } } },
  { question: matching, value: { pairs: { l1: "r1", l2: "r2" } } },
  { question: essay, value: { html: "<p>Người lính trẻ.</p>", wordCount: 3 } },
];

/** Bọc `Question` với trạng thái của nơi gọi, như màn làm bài thật: mỗi thay đổi được báo ra `onValue`. */
export function Live({
  question,
  initial = null,
  onValue,
  mode = "answer",
  review,
  children,
  ...rest
}: {
  question: KitQuestion;
  initial?: AnswerValue | null;
  onValue?: (value: AnswerValue) => void;
  mode?: "answer" | "review";
  review?: QuestionReview | null;
  children?: KitQuestion[];
} & Partial<Omit<QuestionProps, "question" | "mode" | "value" | "onChange" | "review" | "children">>): ReactNode {
  const [value, setValue] = useState<AnswerValue | null>(initial);
  return (
    <Question
      {...rest}
      question={question}
      mode={mode}
      value={value}
      onChange={(next) => {
        setValue(next);
        onValue?.(next);
      }}
      review={review}
      children={children}
    />
  );
}
