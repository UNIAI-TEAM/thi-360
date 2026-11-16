import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { questionStore } from "@/features/questions/mock/store";
import { renderWithProviders } from "@/test/render";
import { ExamReviewView } from "../components/exam-review-view";
import { ExamVersionsView } from "../components/exam-versions-view";
import { reviewStore } from "../mock/review-store";
import { examStore } from "../mock/store";
import { generatePreview } from "../review-api";

const navigation = vi.hoisted(() => ({ search: "" }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/exams/ex-bca-f-001/review",
  useSearchParams: () => new URLSearchParams(navigation.search),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

const BCA = "ex-bca-f-001";
const THPTQG = "ex-thptqg-tin-001";

/** Mẫu BCA cố ý có một câu tự luận chưa có tiêu chí chấm; bổ sung để đường CA1 sinh được đề. */
function addMissingRubric() {
  questionStore.update("q-7002", (question) =>
    question.body.type === "ESSAY"
      ? { ...question, body: { ...question.body, rubric: [{ id: "r1", description: "Nội dung nghị luận", points: 25 }] } }
      : question,
  );
}

beforeEach(() => {
  examStore.reset();
  questionStore.reset();
  reviewStore.reset();
  navigation.search = "";
  window.history.replaceState(null, "", "/");
});

describe("đề xem trước theo bảy kỳ thi", () => {
  it("BCA: điểm từng phần, thời lượng chung, khối tự luận chấm tay kèm tiêu chí chấm", async () => {
    addMissingRubric();
    const result = await generatePreview({ examId: BCA, pathId: "opt-b3-1", seed: 1 });
    if (result.status !== "GENERATED") throw new Error(`expected a paper, got ${result.status}`);
    const { paper } = result;
    expect(paper).toMatchObject({ totalQuestions: 81, totalPoints: 100, totalMinutes: 180, sharedDuration: true, hasManualGrading: true });
    expect(paper.parts.map((part) => [part.points, part.maxPoints])).toEqual([
      [25, 25],
      [60, 60],
      [15, 15],
    ]);
    expect(paper.parts.every((part) => part.durationMinutes === null)).toBe(true);
    const essay = paper.parts[0]!.blocks[0]!;
    expect(essay.manualGrading).toBe(true);
    const item = essay.items[0]!;
    if (item.kind !== "QUESTION") throw new Error("essay block must hold single questions");
    expect(item.question.rubric).not.toBeNull();
  });

  it("BCA: câu tự luận thiếu tiêu chí chấm thì không sinh đề", async () => {
    const result = await generatePreview({ examId: BCA, pathId: "opt-b3-1", seed: 1 });
    expect(result.status).toBe("FAILED");
    if (result.status === "FAILED") expect(result.issues.map((issue) => issue.code)).toContain("RUBRIC_MISSING");
  });

  it("THPTQG Tin học: đường Khoa học máy tính có 28 câu, 10 điểm", async () => {
    const result = await generatePreview({ examId: THPTQG, pathId: "opt-t2-cs", seed: 3 });
    if (result.status !== "GENERATED") throw new Error(`expected a paper, got ${result.status}`);
    expect(result.paper).toMatchObject({ totalQuestions: 28, totalPoints: 10, totalMinutes: 50, hasManualGrading: false });
    expect(result.paper.parts.map((part) => part.points)).toEqual([6, 4]);
  });

  it("màn Đề xem trước của BCA hiện điểm phần và nhãn Chấm tay", async () => {
    addMissingRubric();
    const user = userEvent.setup();
    navigation.search = "tab=preview&path=opt-b3-1";
    renderWithProviders(<ExamReviewView examId={BCA} />);
    const preview = await waitFor(() => screen.getByRole("tabpanel", { name: "Đề xem trước" }));
    await user.click(await within(preview).findByRole("button", { name: "Sinh đề xem trước" }));
    expect(await within(preview).findByText("81 câu · 100 điểm · 180 phút, chung cả bài")).toBeInTheDocument();
    expect(within(preview).getByText(/Đề có câu tự luận chấm tay/)).toBeInTheDocument();
    expect(within(preview).getByRole("heading", { name: /Phần 1 · Tự luận Ngữ văn.*25 \/ 25 điểm/ })).toBeInTheDocument();
    expect(within(preview).getAllByText("Chấm tay").length).toBeGreaterThan(0);
    expect(within(preview).getByText("Tiêu chí chấm")).toBeInTheDocument();
  });
});

describe("kết quả kiểm tra theo bảy kỳ thi", () => {
  it("THPTQG Tin học: số câu làm và in, thời lượng chung, điểm trên thang; cảnh báo không chặn", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExamReviewView examId={THPTQG} />);
    await user.click(await screen.findByRole("button", { name: "Kiểm tra cấu hình" }));
    expect(await screen.findByText("28 câu làm (30 câu in trên đề)")).toBeInTheDocument();
    expect(screen.getByText("50 phút, chung cả bài")).toBeInTheDocument();
    expect(screen.getByText("10 / 10 điểm")).toBeInTheDocument();
    expect(examStore.require(THPTQG).check.status).toBe("VALID");
    expect(screen.getAllByText(/quy tắc chấm khác quy tắc mong đợi/).length).toBeGreaterThan(0);
  });

  it("BCA: thiếu tiêu chí chấm nêu mã câu nguồn; khối tự luận có nhãn Chấm tay", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExamReviewView examId={BCA} />);
    await user.click(await screen.findByRole("button", { name: "Kiểm tra cấu hình" }));
    expect((await screen.findAllByText(/chưa có tiêu chí chấm \(CH-007002\)/)).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Chấm tay").length).toBeGreaterThan(0);
  });
});

describe("hộp thoại công bố", () => {
  it("đường không công bố được nêu vấn đề chặn đầu tiên", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExamVersionsView examId="ex-hsa-f-001" />);
    await user.click(await screen.findByRole("button", { name: "Công bố" }));
    const dialog = await screen.findByRole("dialog");
    const row = within(dialog).getByRole("checkbox", { name: /^KH-2/ }).parentElement!;
    expect(within(row).getByText(/cần 3 câu/)).toBeInTheDocument();
  });
});
