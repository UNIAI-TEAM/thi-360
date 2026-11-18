import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { ScoringExamples, StrandReference, vsatMatchingPoints, vsatTrueFalsePoints } from "../components/scoring-examples";
import { strandTable } from "../reference-distribution";
import type { ExamPart, ExamQuota, ExamSection, QuestionBlock, TopicQuota } from "../types";

/** FE-7 dòng 7, 8, 9: ví dụ chấm V-SAT, H-SCA và bảng mạch đối chiếu theo lệnh (R-140, spec 5.3). */

const block = (patch: Partial<QuestionBlock>): QuestionBlock => ({
  id: "b",
  name: "Dòng",
  questionType: "SINGLE_CHOICE",
  questionCount: 5,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: 6,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  ...patch,
});

const section = (blocks: QuestionBlock[], patch: Partial<ExamSection> = {}): ExamSection => ({
  id: "s",
  subjectCode: "TOAN",
  questionCount: 20,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks,
  ...patch,
});

const part = (blocks: QuestionBlock[]): ExamPart => ({
  id: "p",
  order: 1,
  name: "Phần",
  durationMinutes: null,
  questionCount: 20,
  questionCountOnPaper: 20,
  questionCountRequired: 20,
  participation: { mode: "ALL", choose: null },
  mode: "FIXED",
  sections: [section(blocks)],
  options: [],
});

describe("ví dụ chấm V-SAT (R-140)", () => {
  it("bảng điểm theo điểm tối đa: Đúng/Sai 0, 1, 2, 3, 6; Ghép đôi 0, 1,5, 3, 4,5, 6", () => {
    expect(vsatTrueFalsePoints(6)).toEqual([0, 1, 2, 3, 6]);
    expect(vsatMatchingPoints(6)).toEqual([0, 1.5, 3, 4.5, 6]);
  });

  it("dòng 7 · Phần có Đúng/Sai và Ghép đôi: 3/4 được 3 và 4,5; chọn 4/4 thì hai dạng bằng nhau", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ScoringExamples kind="VSAT" part={part([block({ questionType: "TRUE_FALSE" }), block({ id: "m", questionType: "MATCHING" })])} />);
    expect(screen.getByRole("status")).toHaveTextContent("Đúng 3/4: Đúng/Sai 3 điểm, Ghép đôi 4,5 điểm");
    expect(screen.getByText(/Ghép đôi tính 1,5 điểm mỗi cặp/)).toBeInTheDocument();
    const tf = screen.getByRole("region", { name: "Đúng/Sai · tối đa 6 điểm" });
    expect(within(tf).getByText("Đang xem").closest("tr")).toHaveTextContent("3/4 ý");
    expect(screen.getByRole("region", { name: "Ghép đôi · tối đa 6 điểm" })).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "4/4" }));
    expect(screen.getByRole("status")).toHaveTextContent("Đúng 4/4: Đúng/Sai 6 điểm, Ghép đôi 6 điểm");
    expect(screen.getByText("Ở mức này hai dạng được cùng số điểm.")).toBeInTheDocument();
  });

  it("Phần III chỉ có Ghép đôi: không hiện dòng so sánh với Đúng/Sai", () => {
    renderWithProviders(<ScoringExamples kind="VSAT" part={part([block({ questionType: "MATCHING" })])} />);
    expect(screen.getByRole("status")).toHaveTextContent("Đúng 3/4 cặp: 4,5 điểm");
    expect(screen.queryByRole("region", { name: /^Đúng\/Sai/ })).toBeNull();
    expect(screen.queryByText(/Ở mức này|Cùng số ý đúng/)).toBeNull();
  });

  it("N4 · chỉ có Ghép đôi: bộ chọn ghi Số cặp đúng; có Đúng/Sai thì giữ Số ý đúng trong câu; tiếng Anh Correct pairs", () => {
    const pairs = renderWithProviders(<ScoringExamples kind="VSAT" part={part([block({ questionType: "MATCHING" })])} />);
    expect(screen.getByRole("group", { name: "Số cặp đúng" })).toBeInTheDocument();
    expect(screen.queryByText("Số ý đúng trong câu")).toBeNull();
    pairs.unmount();
    const both = renderWithProviders(<ScoringExamples kind="VSAT" part={part([block({ questionType: "TRUE_FALSE" }), block({ id: "m", questionType: "MATCHING" })])} />);
    expect(screen.getByRole("group", { name: "Số ý đúng trong câu" })).toBeInTheDocument();
    both.unmount();
    renderWithProviders(<ScoringExamples kind="VSAT" part={part([block({ questionType: "MATCHING" })])} />, { locale: "en", messages: en });
    expect(screen.getByRole("group", { name: "Correct pairs" })).toBeInTheDocument();
  });

  it("dạng còn lại theo dạng có trong phần, kể cả câu con của chùm", () => {
    renderWithProviders(
      <ScoringExamples
        kind="VSAT"
        part={part([block({ questionType: "QUESTION_SET", setCount: 1, setSize: 6, childConditions: [{ from: 1, to: 6, nodeIds: [], answerType: "SINGLE_CHOICE" }] })])}
      />,
    );
    expect(screen.getByRole("cell", { name: "Đúng 2/3 câu trong chùm được 12/18" })).toBeInTheDocument();
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("tiếng Anh: không còn chữ tiếng Việt trong khối", () => {
    renderWithProviders(<ScoringExamples kind="VSAT" part={part([block({ questionType: "TRUE_FALSE" }), block({ id: "m", questionType: "MATCHING" })])} />, {
      locale: "en",
      messages: en,
    });
    expect(screen.getByRole("status")).toHaveTextContent("3/4 correct: True/False 3 points, Matching 4.5 points");
    expect(document.body.textContent).not.toMatch(/[ạảãàáâấầẩẫăắằẳẵặđêếềểễệôốồổỗộơớờởỡợưứừửữự]/i);
  });
});

describe("ví dụ chấm H-SCA (R-140)", () => {
  it("dòng 8 · đúng, một phần, sai, bỏ trống: nhiều đáp án 0,25 / 0,125 / 0 / 0, kèm chú thích luật chờ BA", () => {
    renderWithProviders(
      <ScoringExamples
        kind="HSCA"
        part={part([
          block({ questionType: "SINGLE_CHOICE", requiredPointsPerQuestion: 0.25 }),
          block({ id: "m", questionType: "MULTIPLE_CHOICE", requiredPointsPerQuestion: 0.25 }),
          block({ id: "s", questionType: "SHORT_ANSWER", requiredPointsPerQuestion: 0.25 }),
        ])}
      />,
    );
    const multiple = screen.getByRole("rowheader", { name: "Nhiều đáp án" }).closest("tr")!;
    expect(Array.from(multiple.querySelectorAll("td"), (cell) => cell.textContent)).toEqual(["0,25", "0,125", "0", "0"]);
    const single = screen.getByRole("rowheader", { name: "Một đáp án" }).closest("tr")!;
    expect(single).toHaveTextContent("Không áp dụng");
    expect(screen.getByRole("note")).toHaveTextContent("Luật chấm câu nhiều đáp án chờ BA xác nhận");
  });

  it("H-SCA Tiếng Anh, Ngữ văn chỉ có một đáp án: không có dòng Đúng một phần và không có chú thích BA", () => {
    renderWithProviders(<ScoringExamples kind="HSCA" part={part([block({ requiredPointsPerQuestion: 1 })])} />);
    expect(screen.queryByRole("rowheader", { name: "Nhiều đáp án" })).toBeNull();
    expect(screen.queryByRole("note")).toBeNull();
    expect(screen.getByRole("rowheader", { name: "Một đáp án" }).closest("tr")).toHaveTextContent("1");
  });

  it("R1-8 · khối chưa khai điểm mỗi câu: ô điểm hiện —, không tự đặt 1 điểm", () => {
    renderWithProviders(
      <ScoringExamples
        kind="HSCA"
        part={part([block({ questionType: "MULTIPLE_CHOICE", requiredPointsPerQuestion: null })])}
      />,
    );
    const row = screen.getByRole("rowheader", { name: "Nhiều đáp án" }).closest("tr")!;
    expect(Array.from(row.querySelectorAll("td"), (cell) => cell.textContent)).toEqual(["—", "—", "0", "0"]);
  });

  it("R1-6 · phần không có dạng nào có ví dụ: không có khung, tiêu đề hay mô tả", () => {
    const essayOnly = part([block({ questionType: "ESSAY" })]);
    expect(renderWithProviders(<ScoringExamples kind="VSAT" part={essayOnly} />).container).toBeEmptyDOMElement();
    const noChoice = part([block({ questionType: "MATCHING" })]);
    expect(renderWithProviders(<ScoringExamples kind="HSCA" part={noChoice} />).container).toBeEmptyDOMElement();
  });
});

describe("kỳ thi khác", () => {
  it("không hiện khối Ví dụ chấm", () => {
    const { container } = renderWithProviders(<ScoringExamples kind="HSA" part={part([block({})])} />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe("mạch đối chiếu theo lệnh V-SAT (R-139, spec 5.3)", () => {
  const strand = (id: string, name: string, count: number, patch: Partial<TopicQuota> = {}): TopicQuota => ({
    id,
    name,
    nodeIds: [],
    count,
    enforcement: "REFERENCE",
    basis: "PROMPT",
    ...patch,
  });
  const english = section([], {
    subjectCode: "ENGLISH",
    topicQuotas: [strand("t1", "Đọc hiểu", 42), strand("t2", "Giao tiếp qua văn bản", 4), strand("t3", "Ngữ pháp và từ vựng", 21)],
  });
  const difficulty: ExamQuota = {
    id: "q",
    name: "Độ khó theo lệnh",
    dimension: "DIFFICULTY",
    basis: "PROMPT",
    enforcement: "REFERENCE",
    buckets: [
      { codes: ["NB"], count: 21 },
      { codes: ["TH"], count: 39 },
      { codes: ["VD"], count: 7 },
    ],
  };

  it("dòng 9 · bài cả môn: đúng ba mạch của Tiếng Anh, tổng 67 lệnh, Chờ mapping, độ khó theo lệnh", () => {
    const table = strandTable(english, [difficulty], "SUBJECT");
    expect(table).toMatchObject({ total: 67 });
    renderWithProviders(<StrandReference table={table} subjectName="Tiếng Anh" difficultyName={(code) => ({ NB: "Nhận biết", TH: "Thông hiểu", VD: "Vận dụng" })[code] ?? code} />);
    const grid = screen.getByRole("table", { name: "Mạch đối chiếu của Tiếng Anh theo lệnh" });
    const rows = within(grid).getAllByRole("row").slice(1).map((row) => Array.from(row.querySelectorAll("th,td"), (cell) => cell.textContent));
    expect(rows).toEqual([
      ["Đọc hiểu", "42", "Chờ mapping"],
      ["Giao tiếp qua văn bản", "4", "Chờ mapping"],
      ["Ngữ pháp và từ vựng", "21", "Chờ mapping"],
      ["Tổng", "67", ""],
    ]);
    expect(screen.getByText("Mẫu số: Tiếng Anh cả môn · 67 lệnh")).toBeInTheDocument();
    expect(screen.getByText(/Độ khó theo lệnh: Nhận biết 21 · Thông hiểu 39 · Vận dụng 7\./)).toBeInTheDocument();
  });

  it("bài PART không có mạch theo lệnh: không vẽ gì, không lỗi, không khung rỗng", () => {
    expect(strandTable(english, [difficulty], "PART")).toBeNull();
    expect(strandTable(section([], { topicQuotas: [strand("t", "Chủ đề", 5, { basis: "QUESTION" })] }), [], "SUBJECT")).toBeNull();
    const { container } = renderWithProviders(<StrandReference table={null} subjectName="Tiếng Anh" difficultyName={(code) => code} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("mạch đã gắn MAPID hiện Đã gắn mã", () => {
    const mapped = section([], { topicQuotas: [strand("t", "Thơ", 3, { nodeIds: ["n1"] })] });
    renderWithProviders(<StrandReference table={strandTable(mapped, [], "SUBJECT")} subjectName="Ngữ văn" difficultyName={(code) => code} />);
    expect(screen.getByText("Đã gắn mã")).toBeInTheDocument();
  });
});
