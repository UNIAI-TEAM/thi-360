import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { questionTabFromPathname } from "../components/question-detail-layout";
import { QuestionPreviewView } from "../preview/question-preview-view";
import { questionStore } from "../mock/store";
import type { QuestionDetail } from "../types";

vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/q-0001/preview",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

beforeEach(() => window.history.replaceState(null, "", "/"));
afterEach(() => {
  questionStore.reset();
  window.history.replaceState(null, "", "/");
});

const grade = () => screen.getByRole("button", { name: "Chấm thử" });

describe("xem trước câu hỏi", () => {
  it("tab Xem trước có đường dẫn riêng", () => {
    expect(questionTabFromPathname("/questions/q-1/preview")).toBe("preview");
  });

  it("chọn sai rồi đúng: chấm thử cho điểm cả câu và chỉ lộ đáp án sau khi chấm", async () => {
    renderWithProviders(<QuestionPreviewView questionId="q-0001" />);
    expect(await screen.findByRole("group", { name: "Xem trước trên trình duyệt máy tính" })).toBeInTheDocument();
    expect(screen.getByText(/Chọn, đổi hoặc bỏ trống đáp án/)).toBeInTheDocument();
    expect(screen.queryByText("Đáp án đúng")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("radio", { name: /^A\.\s*1$/ }));
    await userEvent.click(grade());
    expect(screen.getByText("Sai", { selector: "span" })).toBeInTheDocument();
    expect(screen.getByText("Đáp án đúng")).toBeInTheDocument();
    expect(screen.getByText("Bạn chọn sai")).toBeInTheDocument();
    expect(screen.getByText(/^0\s*\/\s*1/)).toBeInTheDocument();

    // Đổi đáp án thì kết quả cũ biến mất tới lần chấm sau.
    await userEvent.click(screen.getByRole("radio", { name: /^B\.\s*2/ }));
    expect(screen.queryByText("Đáp án đúng")).not.toBeInTheDocument();
    await userEvent.click(grade());
    expect(screen.getByText("Đúng hết")).toBeInTheDocument();
    expect(screen.getByText(/^1\s*\/\s*1/)).toBeInTheDocument();
  });

  it("bỏ trống: chấm thử báo Bỏ trống, 0 điểm", async () => {
    renderWithProviders(<QuestionPreviewView questionId="q-0001" />);
    await screen.findByRole("group", { name: "Xem trước trên trình duyệt máy tính" });
    await userEvent.click(grade());
    expect(screen.getAllByText("Bỏ trống").length).toBeGreaterThan(0);
    expect(screen.getByText(/^0\s*\/\s*1/)).toBeInTheDocument();
  });

  it("đổi giữa khung web và khung điện thoại", async () => {
    renderWithProviders(<QuestionPreviewView questionId="q-0001" />);
    const web = await screen.findByRole("group", { name: "Xem trước trên trình duyệt máy tính" });
    expect(web).toHaveAttribute("data-device", "web");
    await userEvent.click(screen.getByRole("tab", { name: "Điện thoại" }));
    const phone = await screen.findByRole("group", { name: "Xem trước trên màn hình điện thoại" });
    expect(phone).toHaveAttribute("data-device", "phone");
    expect(within(phone).getByText("Tính giới hạn lim (2n + 1)/(n + 3).")).toBeInTheDocument();
  });

  it("Đúng/Sai theo bảng: một ý đúng, một ý sai, một ý bỏ trống cho điểm theo số ý đúng", async () => {
    renderWithProviders(<QuestionPreviewView questionId="q-0005" />);
    await screen.findByRole("group", { name: "Xem trước trên trình duyệt máy tính" });
    const a = screen.getByRole("group", { name: "Ý a" });
    const b = screen.getByRole("group", { name: "Ý b" });
    await userEvent.click(within(a).getByRole("radio", { name: "Đúng" }));
    await userEvent.click(within(b).getByRole("radio", { name: "Đúng" }));
    await userEvent.click(grade());

    const panel = screen.getByRole("heading", { name: "Kết quả chấm thử" }).closest("div")!;
    expect(within(panel).getByText("Đúng một phần")).toBeInTheDocument();
    expect(within(panel).getByText(/^0,33\s*\/\s*1/)).toBeInTheDocument();
    const rows = within(panel).getAllByRole("listitem").map((item) => item.textContent);
    expect(rows[0]).toContain("Ý a");
    expect(rows[0]).toContain("Đúng");
    expect(rows[1]).toContain("Sai");
    expect(rows[2]).toContain("Bỏ trống");
  });

  it("Ghép đôi: điểm theo từng cặp đúng, lựa chọn đã dùng không chọn lại được khi mỗi lựa chọn dùng một lần", async () => {
    renderWithProviders(<QuestionPreviewView questionId="q-0009" />);
    await screen.findByRole("group", { name: "Xem trước trên trình duyệt máy tính" });
    expect(screen.getByText("Mỗi lựa chọn chỉ dùng một lần.")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("combobox", { name: "Lựa chọn ghép với mục 1" }));
    await userEvent.click(await screen.findByRole("option", { name: "1. sin x + C" }));
    await userEvent.click(screen.getByRole("combobox", { name: "Lựa chọn ghép với mục 2" }));
    expect(await screen.findByRole("option", { name: "1. sin x + C" })).toHaveAttribute("aria-disabled", "true");
    await userEvent.click(screen.getByRole("option", { name: "3. ln|x| + C" }));
    await userEvent.click(grade());

    expect(await screen.findByText(/^0,67\s*\/\s*2/)).toBeInTheDocument();
    expect(screen.getByText("Ghép đúng với: eˣ + C")).toBeInTheDocument();
  });

  it("Điền khuyết: chấm từng ô theo đáp án của chính ô", async () => {
    const base = questionStore.require("q-0004");
    const cloze: QuestionDetail = {
      ...base,
      id: "q-cloze",
      type: "CLOZE",
      points: 1.5,
      content: "",
      body: {
        type: "CLOZE",
        passage: "Thủ đô là [[b1]] của nước [[b2]].",
        blanks: [
          { id: "b1", answers: ["Hà Nội"], points: 1 },
          { id: "b2", answers: ["Việt Nam", "VN"], points: 0.5 },
        ],
      },
    };
    questionStore.insert(cloze);
    renderWithProviders(<QuestionPreviewView questionId="q-cloze" />);
    await screen.findByRole("group", { name: "Xem trước trên trình duyệt máy tính" });

    await userEvent.type(screen.getByRole("textbox", { name: "Ô 1" }), "  hà   nội ");
    await userEvent.type(screen.getByRole("textbox", { name: "Ô 2" }), "Lào");
    await userEvent.click(grade());

    const panel = screen.getByRole("heading", { name: "Kết quả chấm thử" }).closest("div")!;
    expect(within(panel).getByText("Đúng một phần")).toBeInTheDocument();
    expect(within(panel).getByText(/^1\s*\/\s*1,5/)).toBeInTheDocument();
    expect(screen.getByText("Đáp án chuẩn: Việt Nam / VN")).toBeInTheDocument();
  });

  it("Tự luận: không có điểm tự động, hiện Chờ chấm và bảng đối chiếu với hướng dẫn chấm và bài mẫu", async () => {
    renderWithProviders(<QuestionPreviewView questionId="q-8001" />);
    await screen.findByRole("group", { name: "Xem trước trên trình duyệt máy tính" });
    await userEvent.type(screen.getByRole("textbox", { name: "Bài làm" }), "Lòng biết ơn là gốc rễ");
    expect(screen.getByText(/^6 chữ/)).toBeInTheDocument();
    await userEvent.click(grade());

    const panel = screen.getByRole("heading", { name: "Kết quả chấm thử" }).closest("div")!;
    expect(within(panel).getByText("Chờ chấm", { selector: "span" })).toBeInTheDocument();
    expect(within(panel).getByText(/phần Tự luận do người chấm cho điểm/)).toBeInTheDocument();
    const compare = screen.getByRole("region", { name: "Đối chiếu với hướng dẫn chấm" });
    expect(within(compare).getByText("Lòng biết ơn là gốc rễ")).toBeInTheDocument();
    expect(within(compare).getByText(/Chấm theo các tiêu chí/)).toBeInTheDocument();
  });

  it("Bộ câu hỏi: điền đáp án đúng rồi chấm thử cho đúng hết, kèm kết quả từng câu con", async () => {
    renderWithProviders(<QuestionPreviewView questionId="q-0027" />);
    await screen.findByRole("group", { name: "Xem trước trên trình duyệt máy tính" });
    await userEvent.click(screen.getByRole("button", { name: "Điền đáp án đúng" }));
    await userEvent.click(grade());

    const panel = screen.getByRole("heading", { name: "Kết quả chấm thử" }).closest("div")!;
    expect(within(panel).getAllByText("Đúng hết")).toHaveLength(4);
    expect(within(panel).getByText(/^3\s*\/\s*3/)).toBeInTheDocument();
    expect(within(panel).getByText("Câu 3")).toBeInTheDocument();
  });

  it("Bộ câu hỏi chỉ có câu Tự luận đã điền: chấm thử báo Chờ chấm, không báo Sai", async () => {
    const essay = questionStore.require("q-8001");
    const set: QuestionDetail = {
      ...questionStore.require("q-0027"),
      id: "q-essay-set",
      points: essay.points,
      body: { type: "QUESTION_SET", passage: "Đọc đoạn trích.", childIds: [essay.id] },
      children: [essay],
    };
    questionStore.insert(set);
    renderWithProviders(<QuestionPreviewView questionId="q-essay-set" />);
    await screen.findByRole("group", { name: "Xem trước trên trình duyệt máy tính" });
    await userEvent.type(screen.getByRole("textbox", { name: "Bài làm" }), "Lòng biết ơn");
    await userEvent.click(grade());

    const panel = screen.getByRole("heading", { name: "Kết quả chấm thử" }).closest("div")!;
    expect(within(panel).getAllByText("Chờ chấm", { selector: "span" }).length).toBeGreaterThan(0);
    expect(within(panel).queryByText("Sai")).not.toBeInTheDocument();
  });

  it("tiếng Anh: không còn chữ tiếng Việt trong khung xem trước", async () => {
    renderWithProviders(<QuestionPreviewView questionId="q-0001" />, { locale: "en", messages: en });
    expect(await screen.findByRole("group", { name: "Preview in a desktop browser" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("radio", { name: /^A\.\s*1$/ }));
    await userEvent.click(screen.getByRole("button", { name: "Try grading" }));
    await waitFor(() => expect(screen.getByText("Wrong choice")).toBeInTheDocument());
    expect(screen.getByText("Trial grading result")).toBeInTheDocument();
    expect(screen.queryByText("Chấm thử")).not.toBeInTheDocument();
  });
});
