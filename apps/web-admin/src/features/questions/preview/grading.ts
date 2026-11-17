import type { QuestionDetail } from "../types";

/**
 * Chấm thử trong xem trước câu hỏi (UniNote PBI-03 mục 8.5, spec mục 8): cùng quy tắc chấm của lượt thi, chạy trong
 * trình duyệt trên bản câu đang xem. Hàm thuần, không đụng kho dữ liệu mẫu.
 */

/** Bài làm thử theo dạng câu. */
export type PreviewAnswer =
  | { type: "SINGLE_CHOICE"; selected: string | null }
  | { type: "MULTIPLE_CHOICE"; selected: string[] }
  | { type: "SHORT_ANSWER"; text: string }
  /** Giá trị theo id ô trống. */
  | { type: "CLOZE"; values: Record<string, string> }
  /** `null`: bỏ trống (không tính là đúng). */
  | { type: "TRUE_FALSE"; values: Record<string, boolean | null> }
  /** Id mục trái -> id lựa chọn phải; `null`: bỏ trống. */
  | { type: "MATCHING"; picks: Record<string, string | null> }
  | { type: "QUESTION_SET"; children: Record<string, PreviewAnswer> }
  | { type: "ESSAY"; text: string };

export type PartState = "correct" | "wrong" | "blank";

/** Kết quả một ý (ô trống, mệnh đề, cặp ghép). Điểm `null` khi dạng chấm không chia điểm theo ý (bảng theo số ý đúng). */
export interface PartResult {
  /** Id ô trống, mệnh đề hoặc mục trái. */
  key: string;
  state: PartState;
  earned: number | null;
  max: number | null;
}

/** `manual`: câu chỉ chấm tay (Tự luận), chưa có điểm. */
export type QuestionResultState = PartState | "partial" | "manual";

export interface QuestionResult {
  state: QuestionResultState;
  /** Điểm đạt trên phần chấm tự động. */
  earned: number;
  max: number;
  /** Có phần chấm tay: điểm đạt chưa gồm phần đó. */
  hasManualPart: boolean;
  parts: PartResult[];
  /** Kết quả từng câu con theo id (Bộ câu hỏi). */
  children: Record<string, QuestionResult>;
}

/** Chuẩn hóa khi khớp đáp án (spec mục 8, B7): bỏ khoảng trắng thừa, NFC, không phân biệt hoa thường. */
export function normalizeAnswer(value: string): string {
  return value.normalize("NFC").trim().replace(/\s+/g, " ").toLocaleLowerCase("vi");
}

/** Cắt nhiễu dấu phẩy động; việc làm tròn hai chữ số để cho bước hiển thị. */
function clean(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}

function sum(values: number[]): number {
  return clean(values.reduce((total, value) => total + value, 0));
}

function matchesAny(value: string, accepted: readonly string[]): boolean {
  const normalized = normalizeAnswer(value);
  return normalized !== "" && accepted.some((answer) => normalizeAnswer(answer) === normalized);
}

export function emptyAnswer(question: QuestionDetail): PreviewAnswer {
  const body = question.body;
  switch (body.type) {
    case "SINGLE_CHOICE":
      return { type: "SINGLE_CHOICE", selected: null };
    case "MULTIPLE_CHOICE":
      return { type: "MULTIPLE_CHOICE", selected: [] };
    case "SHORT_ANSWER":
      return { type: "SHORT_ANSWER", text: "" };
    case "CLOZE":
      return { type: "CLOZE", values: Object.fromEntries(body.blanks.map((blank) => [blank.id, ""])) };
    case "TRUE_FALSE":
      return { type: "TRUE_FALSE", values: Object.fromEntries(body.statements.map((item) => [item.id, null])) };
    case "MATCHING":
      return { type: "MATCHING", picks: Object.fromEntries(body.left.map((item) => [item.id, null])) };
    case "QUESTION_SET":
      return {
        type: "QUESTION_SET",
        children: Object.fromEntries(question.children.map((child) => [child.id, emptyAnswer(child)])),
      };
    case "ESSAY":
      return { type: "ESSAY", text: "" };
  }
}

/** Bài làm đúng hoàn toàn (nút "Điền đáp án đúng"); Tự luận điền bài mẫu nếu có. */
export function correctAnswer(question: QuestionDetail): PreviewAnswer {
  const body = question.body;
  switch (body.type) {
    case "SINGLE_CHOICE":
      return { type: "SINGLE_CHOICE", selected: body.options.find((option) => option.correct)?.id ?? null };
    case "MULTIPLE_CHOICE":
      return { type: "MULTIPLE_CHOICE", selected: body.options.filter((option) => option.correct).map((option) => option.id) };
    case "SHORT_ANSWER":
      return { type: "SHORT_ANSWER", text: body.answers[0] ?? "" };
    case "CLOZE":
      return { type: "CLOZE", values: Object.fromEntries(body.blanks.map((blank) => [blank.id, blank.answers[0] ?? ""])) };
    case "TRUE_FALSE":
      return { type: "TRUE_FALSE", values: Object.fromEntries(body.statements.map((item) => [item.id, item.answer])) };
    case "MATCHING":
      return {
        type: "MATCHING",
        picks: Object.fromEntries(
          body.left.map((item) => [item.id, body.pairs.find((pair) => pair.leftId === item.id)?.rightId ?? null]),
        ),
      };
    case "QUESTION_SET":
      return {
        type: "QUESTION_SET",
        children: Object.fromEntries(question.children.map((child) => [child.id, correctAnswer(child)])),
      };
    case "ESSAY":
      return { type: "ESSAY", text: body.sampleAnswer ?? "" };
  }
}

function overallState(earned: number, max: number, answered: boolean): QuestionResultState {
  if (!answered) return "blank";
  if (max > 0 && earned >= max) return "correct";
  return earned > 0 ? "partial" : "wrong";
}

function emptyResult(max: number, state: QuestionResultState, extra: Partial<QuestionResult> = {}): QuestionResult {
  return { state, earned: 0, max, hasManualPart: false, parts: [], children: {}, ...extra };
}

/** Chấm một câu theo bảng điểm của spec mục 8. Bài làm khác dạng với câu thì coi như bỏ trống. */
export function gradePreview(question: QuestionDetail, answer: PreviewAnswer): QuestionResult {
  const body = question.body;
  const max = question.points;

  if (body.type === "SINGLE_CHOICE") {
    const selected = answer.type === "SINGLE_CHOICE" ? answer.selected : null;
    if (selected === null) return emptyResult(max, "blank");
    const right = body.options.find((option) => option.id === selected)?.correct === true;
    return emptyResult(max, right ? "correct" : "wrong", { earned: right ? max : 0 });
  }

  if (body.type === "MULTIPLE_CHOICE") {
    const selected = answer.type === "MULTIPLE_CHOICE" ? answer.selected : [];
    if (selected.length === 0) return emptyResult(max, "blank");
    const correctIds = body.options.filter((option) => option.correct).map((option) => option.id);
    const wrongPicked = selected.filter((id) => !correctIds.includes(id)).length;
    const correctPicked = selected.filter((id) => correctIds.includes(id)).length;
    const earned =
      body.scoringRule === "PARTIAL"
        ? wrongPicked > 0 || correctIds.length === 0
          ? 0
          : clean((max * correctPicked) / correctIds.length)
        : wrongPicked === 0 && correctPicked === correctIds.length
          ? max
          : 0;
    return emptyResult(max, overallState(earned, max, true), { earned });
  }

  if (body.type === "SHORT_ANSWER") {
    const text = answer.type === "SHORT_ANSWER" ? answer.text : "";
    if (text.trim() === "") return emptyResult(max, "blank");
    const right = matchesAny(text, body.answers);
    return emptyResult(max, right ? "correct" : "wrong", { earned: right ? max : 0 });
  }

  if (body.type === "CLOZE") {
    const values = answer.type === "CLOZE" ? answer.values : {};
    const parts = body.blanks.map<PartResult>((blank) => {
      const value = values[blank.id] ?? "";
      if (value.trim() === "") return { key: blank.id, state: "blank", earned: 0, max: blank.points };
      const right = matchesAny(value, blank.answers);
      return { key: blank.id, state: right ? "correct" : "wrong", earned: right ? blank.points : 0, max: blank.points };
    });
    const total = sum(body.blanks.map((blank) => blank.points));
    const earned = sum(parts.map((part) => part.earned ?? 0));
    return emptyResult(total, overallState(earned, total, parts.some((part) => part.state !== "blank")), {
      earned,
      parts,
    });
  }

  if (body.type === "TRUE_FALSE") {
    const values = answer.type === "TRUE_FALSE" ? answer.values : {};
    const perStatement = body.scoringMethod === "PER_STATEMENT";
    const parts = body.statements.map<PartResult>((statement) => {
      const value = values[statement.id] ?? null;
      const state: PartState = value === null ? "blank" : value === statement.answer ? "correct" : "wrong";
      const statementMax = perStatement ? (statement.points ?? 0) : null;
      return {
        key: statement.id,
        state,
        earned: statementMax === null ? null : state === "correct" ? statementMax : 0,
        max: statementMax,
      };
    });
    const answered = parts.some((part) => part.state !== "blank");
    const correctCount = parts.filter((part) => part.state === "correct").length;
    const earned = perStatement
      ? sum(parts.map((part) => part.earned ?? 0))
      : correctCount === 0
        ? 0
        : (body.scoreTable[correctCount - 1] ?? 0);
    return emptyResult(max, overallState(earned, max, answered), { earned, parts });
  }

  if (body.type === "MATCHING") {
    const picks = answer.type === "MATCHING" ? answer.picks : {};
    const parts = body.left.map<PartResult>((item) => {
      const pair = body.pairs.find((candidate) => candidate.leftId === item.id);
      const pairMax = pair?.points ?? 0;
      const pick = picks[item.id] ?? null;
      if (pick === null) return { key: item.id, state: "blank", earned: 0, max: pairMax };
      const right = pair !== undefined && pair.rightId === pick;
      return { key: item.id, state: right ? "correct" : "wrong", earned: right ? pairMax : 0, max: pairMax };
    });
    const earned = sum(parts.map((part) => part.earned ?? 0));
    return emptyResult(max, overallState(earned, max, parts.some((part) => part.state !== "blank")), { earned, parts });
  }

  if (body.type === "QUESTION_SET") {
    const answers = answer.type === "QUESTION_SET" ? answer.children : {};
    const children = Object.fromEntries(
      question.children.map((child) => [child.id, gradePreview(child, answers[child.id] ?? emptyAnswer(child))]),
    );
    const results = Object.values(children);
    const total = sum(results.map((result) => result.max));
    const earned = sum(results.map((result) => result.earned));
    const answered = results.some((result) => result.state !== "blank");
    const hasManualPart = results.some((result) => result.hasManualPart || result.state === "manual");
    const state = overallState(earned, total, answered);
    // Phần Tự luận chưa chấm không phải phần sai: chỉ khi có câu con tự chấm bị sai mới báo Sai; không thì chờ chấm.
    const autoWrong = results.some((result) => result.state === "wrong");
    const setState: QuestionResultState = !hasManualPart
      ? state
      : state === "correct"
        ? "partial"
        : state === "wrong" && !autoWrong
          ? "manual"
          : state;
    return emptyResult(total, setState, {
      earned,
      hasManualPart,
      children,
    });
  }

  // Tự luận: chấm tay theo hướng dẫn chấm, không có điểm tự động.
  const essay = answer.type === "ESSAY" ? answer.text : "";
  return emptyResult(max, essay.trim() === "" ? "blank" : "manual", { hasManualPart: true });
}

/** Số chữ của bài làm Tự luận (tách theo khoảng trắng). */
export function countWords(text: string): number {
  const trimmed = text.trim();
  return trimmed === "" ? 0 : trimmed.split(/\s+/).length;
}
